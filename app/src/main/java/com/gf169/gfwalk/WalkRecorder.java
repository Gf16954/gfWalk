package com.gf169.gfwalk;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

public class WalkRecorder extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    static final String TAG = "gfWalkRecorder";

    static WalkRecorder self;

    volatile Location lastGoodLocation = null;  // Последняя полученная от provider'a не ошибочная точка
    Location prevGoodLocation;
    volatile int curLocationIsOK; // Последняя полученная точка свежая и хорошая

    int locationRequestInterval;
    final int locationRequestInterval2 = 1;  // Начальный
    final int locationRequestInterval3 = 3;  // После плохой точки или долгого отсутствия

    boolean mockLocation = false; // Ниже переустановлю: если под эмулятором - true

    int callNumber = -1;
    Thread timerThread;
    Thread mockerThread;
    Thread waitingThread;

    GoogleApiClient googleApiClient = null;
    Geocoder geocoder;
    Clock clock;
    NotificationManager notificationManager;
    NotificationCompat.Builder notifyBuilder;
    int notifyID = 1;
    int locationRequestPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;
    final float ACCURACY_FACTOR = 0.5F;  // TODO: Уточнить !
    final int waitingThreadTimeout = 10;
    int activityRecognitionInterval = 5; // сек !!!
    ARState arStateLast;
    ARState arStateMean = new ARState((ActivityRecognitionResult) null);
    PendingIntent locationPendigIntent, arPendingIntent;
    float batteryCapacity = -1f; // microampere-hours

    SharedPreferences walkSettings;

    // Эти переменные update'ятся в разных thread'ах, поэтому volatile
    volatile boolean locationServiceIsReady = false;
    volatile String totalStr;
    volatile Location lastPointLocation = null;  // Последняя точка маршрута. Служит и флагом начала/продолжения прогулки
    volatile long lastPointTime = 0;
    volatile int walkId;
    volatile long startTime;  // Время начала прогулки
    volatile Location endLocation; // Последней точки последнего продолжения
    volatile long walkDuration; // в миллисекундах
    volatile long deltaDuration = 0; //  Начальная duration-durationNetto (duration с учетом приостановок)
    volatile float walkLength; // в метрах
    volatile float deltaLength;
    volatile int lastPointNumber; // в прогулке
    volatile int lastAFNumber; // в прогулке
    volatile long lastPointId = 0;
    volatile Vector<Long> lastAFIds = new Vector<>(Walk.AFKIND_MAX + 1); // По видам артефаков
    volatile boolean screenIsOn = true;

    int iOnLocationChanged = -1;

    int nLocationNotOk;
    int nLocationNotOkMax = 5;  // Число полученных плохих точек, после которых не проверяем следующую
    float nStale = 2.5f; // Число интервалов после последней точки, после чего она считается протухшей

    BroadcastReceiver broadcastReceiver;
    PowerManager.WakeLock wakeLock;

    static boolean arIsPresent = false; // Есть датчикИ
/*
To conserve battery, activity reporting may stop when the device is 'STILL' for an extended period of time.
It will resume once the device moves again. This only happens on devices that support the Sensor.TYPE_SIGNIFICANT_MOTION hardware.
Beginning in API 21, activities may be received less frequently than the detectionIntervalMillis parameter
if the device is in power save mode and the screen is off.
 */
    static class ARState { //ActivityRecognitionState
        static int[] types = {DetectedActivity.STILL /*3*/, DetectedActivity.WALKING /*7*/, // По возрастанию скорости
                DetectedActivity.RUNNING /*8*/, DetectedActivity.ON_BICYCLE /*1*/,
                DetectedActivity.IN_VEHICLE /*0*/, DetectedActivity.UNKNOWN} /*4*/;
        static String[] names = {"still", "walking", "running", "on bicycle", "in vehicle", "unknown"};
        // tilting 5 и on foot 2 выкинуты
        static float[] maxSpeeds = {0.1f, 8, 20, 25, 250, 250}; // км/час

        int type; //=DetectedActivity.UNKNOWN;
        String name; //="unknown";
        float maxPossibleSpeed; //=1000000;
        int[] confs = {0, 0, 0, 0, 0, 0};

        float maxSpeedFromMap; // На интервале от предыдущей точки
        int typePrev;
        Location locationPrev;

        Boolean isStill;

        private void setProps(int i) {
            type = types[i];
            name = names[i];
            maxPossibleSpeed = maxSpeeds[i];
        }

        static float getMaxSpeed(int type) {
            for (int i = 0; i < types.length; i++) {
                if (type == types[i]) {
                    return maxSpeeds[i];
                }
            }
            return maxSpeeds[types.length - 1];
        }

        static String getName(int type) {
            for (int i = 0; i < types.length; i++) {
                if (type == types[i]) {
                    return names[i];
                }
            }
            return "unknown";
        }

        static int getType(float speed) {
            for (int i = 0; i < types.length; i++) {
                if (speed < maxSpeeds[i]) {
                    return types[i];
                }
            }
            return DetectedActivity.UNKNOWN;
        }

        void reset(int typePrev, Location locationPrev) {
            setProps(types.length - 1);  // unknown
            confs = new int[]{0, 0, 0, 0, 0, 0};
            maxSpeedFromMap = 0;   // На интервале от предыдущей точки
            this.typePrev = typePrev;
            this.locationPrev = locationPrev != null ? new Location(locationPrev) : null;
            isStill = arIsPresent ? true : null;
        }

        ARState(ActivityRecognitionResult result) {
            StringBuilder s = new StringBuilder("ARState new: ");

            if (result == null) {
                reset(DetectedActivity.UNKNOWN, null);
                Utils.logD(TAG, s + "-> " + name);
                return;
            }

            int j = -1;
            int conf = 0;
            for (DetectedActivity activity : result.getProbableActivities()) {
                int activityType = activity.getType();
                s.append(activityType).append("-").append(activity.getConfidence()).append("% ");

                if (activityType == DetectedActivity.TILTING || activityType == DetectedActivity.ON_FOOT
                    || activityType == DetectedActivity.UNKNOWN)
                    continue;
                for (int i = 0; i < types.length; i++) {
                    if (types[i] == activity.getType()) {
                        confs[i] = activity.getConfidence();
                        if (confs[i] > conf) {
                            j = i;
                            conf = confs[i];
                        }
                    }
                }
            }
            if (j >= 0) {
                setProps(j);
            } else {
                reset(DetectedActivity.UNKNOWN, null);
            }

            Utils.logD(TAG, s + "-> " + name);

            isStill = name.equals("still");
        }

        ARState(ARState arState) {     // =clone
            this.type = arState.type;
            this.name = arState.name;
            this.maxPossibleSpeed = arState.maxPossibleSpeed;
            this.maxSpeedFromMap = arState.maxSpeedFromMap;
            this.typePrev = arState.typePrev;
            this.locationPrev = arState.locationPrev != null ? new Location(arState.locationPrev) : null;
            this.isStill = arState.isStill;
        }

        void add(ARState arState) {
            for (int i = 0; i < types.length; i++) {
                if (arState.confs[i] < 45) continue; // !!! Пропускаем 40, 10, 10, 10...
                confs[i] += arState.confs[i];
            }
            StringBuilder s = new StringBuilder("ARState add: ");
            int j = -1;
            int conf = 0;
            for (int i = 0; i < types.length; i++) {
                s.append(types[i]).append("-").append(confs[i]).append("% ");
                if (confs[i] > conf) {
                    j = i;
                    conf = confs[i];
                }
            }
            if (j >= 0) {
                setProps(j);
            }

            if (isStill == null) {
                isStill = name.equals("still");
            } else {
                isStill &= name.equals("still");
            }

            Utils.logD(TAG, s + "-> " + name + "   isStill=" +isStill);
        }

        void infereType(Location locationLast) {
            String s = "ARState infereType: " + type;
            if (type == DetectedActivity.UNKNOWN || type == DetectedActivity.STILL) {
                type = typePrev; // Предыдущей точки
                s += " -> " + type;
            }
            // Если на самом деле двигались быстрее, чем возможно при этом типе, определяем тип по фактической скорости
            float speed = 0;
/*
            speed = Math.max(locationLast.getSpeed(),
                    locationPrev == null ? 0 : locationPrev.getSpeed());
            speed = Math.max(speed, maxSpeedFromMap); // m/s
*/
            if (locationPrev != null) {
                float speed2 =  // Средня скорость
                        Utils.getDistance(Utils.loc2LatLng(locationLast), Utils.loc2LatLng(locationPrev)) /
                                ((locationLast.getTime() - locationPrev.getTime()) / 1000f);
                if (!(speed2 != speed2)) speed = Math.max(speed, speed2); // Не NaN
            }
            speed = speed * 3.6f; // km/h

            if (type == DetectedActivity.STILL || type == DetectedActivity.UNKNOWN ||
                    speed > getMaxSpeed(type)) {
                type = getType(speed);
                s += " -> " + speed + "km/h -> " + type;
            }
            Utils.logD(TAG, s);
        }

        static void setAbsolutelyMaxSpeed(int speed) {
            maxSpeeds[maxSpeeds.length - 2] = speed;
            maxSpeeds[maxSpeeds.length - 1] = speed;
        }
    }

    @Override
    public void onCreate() {
        Utils.logD(TAG, "onCreate");
/* Перенесено в MapActivity
        Utils.setDefaultUncaughtExceptionHandler(  // Чтобы при отвале все культурно убивало
                () -> stop(5));
*/
    }

    void startForeground() {
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "gfWalk";
            NotificationChannel channel = new NotificationChannel(channelId,
                    "gfWalk", NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
            notifyBuilder = new NotificationCompat.Builder(this, channelId);
        } else {
            notifyBuilder = new NotificationCompat.Builder(this);
        }
        super.startForeground(notifyID, notifyBuilder
                .setSmallIcon(R.drawable.ic_recording_1)
                .setContentTitle(getString(R.string.notification_recording))
                .setContentText(getResources().getString(R.string.waiting_for_GPS))
                .setPriority(Notification.PRIORITY_MAX)  // Чтобы как можно левее
                .addAction(new NotificationCompat.Action.Builder(
                        R.drawable.ic_stop_recording,
                        getString(R.string.notification_stop_recording),
                        PendingIntent.getBroadcast(
                                this, 1,
                                new Intent(MapActivity.GLOBAL_INTENT_FILTER)
//                                        .setClass(this, MapActivity.class)  !!! С этим не доходит
                                        .putExtra("action", "stopRecording"),
                                PendingIntent.FLAG_UPDATE_CURRENT)
                ).build())
                .setContentIntent(
                        PendingIntent.getActivity(
                                this, 2,
                                (new Intent(this, MapActivity.class))
                                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                .build());

        startTimerThread();

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    Utils.logD(TAG, "Screen is turned off");
                    screenIsOn = false;
                    timerThread.interrupt();  // Экономим электроэнергию
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    Utils.logD(TAG, "Screen is turned on");
                    screenIsOn = true;
                    if (locationServiceIsReady) { // Добавляем точку, чтобы сразу нарисовал все ненарисованное
                        addPoint(null, "Screen is turned on",
                                0, null, null, false);
                    }
                    startTimerThread();
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(broadcastReceiver, filter);

        walkSettings =
                SettingsActivity.getCurrentWalkSettings(WalkRecorder.this, -1);  // Глобальные
        geocoder = new Geocoder(this, Locale.getDefault());

        mockLocation = Utils.isEmulator();
        if (mockLocation) {
            mockerThread = startMocking(this::onLocationChanged, "gfMockerThread", 10000,
                    Utils.str2Loc("55.75222 37.61556")); // Москва, Кремль
        } else {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addApi(ActivityRecognition.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            googleApiClient.connect();

            waitingThread = new Thread(() -> {  // Ждем GPS
                boolean interrupted = false;
                while (!interrupted && !locationServiceIsReady) {
                    interrupted = Utils.sleep(waitingThreadTimeout * 1000, true);
                    if (!locationServiceIsReady) {
                        Utils.toast(this,
                                getResources().getString(R.string.waiting_for_GPS), Toast.LENGTH_LONG);
                    }
                }
            }, "gfWaitingForGPS");
            waitingThread.start();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":wakeLock");
        wakeLock.acquire();
    }

    void startTimerThread() {
        new Thread(() ->  {
            Utils.logD(TAG, "Timer thread is started");
            timerThread = Thread.currentThread();

            // Update'им notification (идущие ноги)
            int iNotifyIcon = 0;
            Location prevLocation = new Location("");

            boolean interrupted = false;
            while (!interrupted) {
                // Utils.logD(TAG, "Timer thread is working");

                if (lastGoodLocation == null) continue;
                if (lastGoodLocation.getTime() == prevLocation.getTime()) {
                    if (System.currentTimeMillis() - prevLocation.getTime() > // Долго не было onLocationChange - в метро
                            locationRequestInterval * 1000 * nStale) {
                        if (curLocationIsOK >= 0) {
                            curLocationIsOK = -1;  // Пропал GPS и остальное - ничего не получаем
                        }
                    }
                } else {
                    prevLocation.set(lastGoodLocation);
                }
                iNotifyIcon = ++iNotifyIcon % 2;
                notifyBuilder
                        .setSmallIcon(iNotifyIcon == 0 ?
                                R.drawable.ic_recording_1 :
                                curLocationIsOK >= 0 || !BuildConfig.BUILD_TYPE.equals("debug") ?
                                        R.drawable.ic_recording_2 : R.drawable.ic_recording_3)
                        .setContentText(totalStr);
                notificationManager.notify(notifyID, notifyBuilder.build());

                interrupted = Utils.sleep(1000, true);
            }
            Utils.logD(TAG, "Timer thread is stopped");
        },"gfTimerThread").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {  // Main thread
        callNumber++;
        Utils.logD(TAG, "onStartCommand call "  + callNumber);

        if (intent == null) { // ?
            Utils.logD(TAG, "onStartCommand: intent == null");
            stop(1);
            return Service.START_STICKY;
        }

        // Вызов из MapActivity
        Bundle extras = intent.getExtras();
            // Начни запись прогулки
        int walkId2 = extras != null ? extras.getInt("walkId") : 0;
        if (walkId2 > 0) {
            Utils.logD(TAG, "onStartCommand: walkId=" + walkId2);
            getWalkInf(walkId2);

            self = this;  // Работает !!!
            startForeground();  // Реальный запуск здесь и только здесь - единственный законный способ!

            return Service.START_STICKY;
        }

        if (self == null) {  // Ни на что не реагируем
            StringBuilder s = new StringBuilder("\n");
            for (CharSequence c: intent.getExtras().keySet()) s.append(c).append(" ");
            Utils.logD(TAG, "onStartCommand: skipping premature call"  + s);
            return Service.START_STICKY;
        }

            // Передает скорость - для участия в определении typeActivity
        float speedFromMap = extras != null ? extras.getFloat("speedFromMap", -1) : -1;
        if (speedFromMap >= 0) {
            arStateMean.maxSpeedFromMap = Math.max(arStateMean.maxSpeedFromMap, speedFromMap);
            Utils.logD(TAG, "onStartCommand: maxSpeedFromMap="
                    + speedFromMap + " " + arStateMean.maxSpeedFromMap);
            return Service.START_STICKY;
        }

        // Вызов отсюда же - ActivityRecognition
        if (ActivityRecognitionResult.hasResult(intent)) {
            arIsPresent = true;

            arStateLast = new ARState(ActivityRecognitionResult.extractResult(intent));
            arStateMean.add(arStateLast);
            Utils.logD(TAG, "onStartCommand: ARState type=" + arStateLast.type + " " + arStateMean.type);
            return Service.START_STICKY;
        }

        StringBuilder s = new StringBuilder("\n");
        for (CharSequence c: intent.getExtras().keySet()) s.append(c).append(" ");
        Utils.logD(TAG, "onStartCommand: unknown call"  + s);
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        stop(0); // Вдруг снаружи кто-то сделает stopService
        Utils.logD(TAG, "onDestroy");
    }

    void stop(int callNumber) {
        Utils.logD(TAG, "stop "+callNumber);

        Utils.setDefaultUncaughtExceptionHandler(null);

        self = null;

        if (googleApiClient!=null) {
            if (googleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient,
                        locationPendigIntent);
                if (arPendingIntent!=null)
                    ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient,
                            arPendingIntent);
                googleApiClient.disconnect();
            }
            googleApiClient=null;
        }
        if (timerThread!=null) {
            timerThread.interrupt();  // Если попадет во время sleep, убьет, если нет - самоубьется
        }
        if (mockerThread!=null) {
            mockerThread.interrupt();
        }
        if (waitingThread!=null) {
            waitingThread.interrupt();
        }
        if (notificationManager!=null) {
            notificationManager.cancel(notifyID);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {} // Еще не зарегистрирован

        lastGoodLocation=null;
        stopSelf();

//        switchLogcatRecorder(false, walkSettings, this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onConnected(Bundle connectionHint) { // Google Api client
        Utils.logD(TAG, "onConnected");

        if (googleApiClient==null) { // 2 раза было ! Между onCreate\Connect и onConnected сервис убит
//            toast("onConnected: googleApiClient==null !!!", Toast.LENGTH_LONG);
            return;
        }
        locationRequestInterval=locationRequestInterval2;
        requestLocationUpdates(locationRequestInterval,locationRequestInterval); // Начальные, для определения состояния
    }

    void getWalkInf(int walkId) {
        Utils.logD(TAG, "getWalkInf " + walkId);

        this.walkId=walkId;
        walkSettings =
                SettingsActivity.getCurrentWalkSettings(WalkRecorder.this, walkId);

        DB.dbInit(this);
        Cursor cursor=DB.db.query(DB.TABLE_WALKS, new String[]{
                        DB.KEY_LENGTH, DB.KEY_DURATION, DB.KEY_LENGTHNETTO, DB.KEY_DURATIONNETTO,
                        DB.KEY_STARTTIME},
                DB.KEY_ID+"="+walkId, null, null, null, null);
        cursor.moveToFirst();
        walkDuration=cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION));
        walkLength=cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTH));
        deltaDuration=cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION))-
                cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATIONNETTO));
        deltaLength=cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTH))-
                cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTHNETTO));
        startTime=cursor.getLong(cursor.getColumnIndex(DB.KEY_STARTTIME));

        cursor=DB.db.query(DB.TABLE_POINTS, new String[]{DB.KEY_POINTLOCATION},
                DB.KEY_POINTWALKID+"="+walkId, null, null, null,
                DB.KEY_POINTID+" desc");
        lastPointNumber=cursor.getCount()-1;
        if (cursor.moveToFirst()) {
            endLocation=Utils.str2Loc(
                    cursor.getString(cursor.getColumnIndex(DB.KEY_POINTLOCATION)));
        }

        cursor=DB.db.query(DB.TABLE_AFS, null,
                DB.KEY_AFWALKID+"="+walkId, null, null, null, null);
        lastAFNumber=cursor.getCount()-1;

        cursor.close();
    }

    public void requestActivityRecognition(int interval) {
        arPendingIntent=PendingIntent.getService(
                this, 3, new Intent(this, WalkRecorder.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                googleApiClient, interval * 1000, arPendingIntent);  // Нету с listener'ом, только pendingIntent:(
    }

    @SuppressLint("MissingPermission")
    public void requestLocationUpdates(int interval, int minInterval) {
        Utils.logD(TAG, "requestLocationUpdates " + interval + " " + minInterval);
        LocationRequest locationRequest=LocationRequest.create();
        locationRequest.setInterval(interval * 1000);
        locationRequest.setFastestInterval(minInterval * 1000);
        locationRequest.setPriority(locationRequestPriority);
        locationPendigIntent=PendingIntent.getService(
                this, 4, new Intent(this, WalkRecorder.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        LocationServices.FusedLocationApi.requestLocationUpdates(
//                googleApiClient, locationRequest, locationPendigIntent); // -> onStartCommand
                googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {  // Никогда не видел
        Utils.logD(TAG, "onConnectionFailed, result: "+result.toString());

        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("DoInUIThread")
                        .putExtra("action", "showNotConnectedDialog")  // Выдаст диалог "Установите новую версию"
                        .putExtra("resultCode", result.getErrorCode())
                        .putExtra("result", result.toString()));
        stop(4);
    }

    @Override
    public void onConnectionSuspended(int cause) {  // Сам восстановит
        Utils.logD(TAG, "onConnectionSuspended, cause=" + cause);
    }

    @Override
    public void onLocationChanged(Location location) {  // Main thread
        if (self == null) return;

        iOnLocationChanged++;
        Utils.logD(TAG, "onLocationChanged #"+iOnLocationChanged+": " +
                new SimpleDateFormat("HH:mm:ss.sss").format(location.getTime()) + " " +
                Utils.loc2Str(location)+" "+(arStateLast==null ? "" : arStateLast.name) + " " + arStateMean.isStill);

        if (lastGoodLocation==null) {// Самая первая
            lastGoodLocation=new Location(location);
            prevGoodLocation=new Location(location);
            return;
        }
        // Location хорошая?
        int iOk=0;
        if (locationServiceIsReady  // Пока Location service не заработал, на AR не обращаем внимания
                && arStateMean.isStill == Boolean.TRUE) {  // Стоял весь интервал
            long t=location.getTime();
            location.set(lastGoodLocation); // Копируем предыдущую
            location.setTime(t);
            iOk=4;

        } else {

            iOk = testLocation(location);

            if (iOk<3) {
                nLocationNotOk++;
                if (nLocationNotOk > nLocationNotOkMax) {
                    iOk = 7;
                }
            }
        }
        curLocationIsOK = iOk <3? 0 : 1;
        Utils.logD(TAG, "onLocationChanged #"+iOnLocationChanged+": OK=" + iOk + " " + nLocationNotOk);

        if (curLocationIsOK > 0) { // Все ОК, полноценная точка
            prevGoodLocation.set(lastGoodLocation);
            lastGoodLocation.set(location);
            nLocationNotOk = 0;

            if (locationServiceIsReady) { // Добавляем точку
                addPoint(null, "onLocationChanged",  // null - добавится lastGoodLocation
                            0, null, null, false);

            } else { // Начало записи прогулки
                clock = new Clock(location); // Запускаем часы

                if (!mockLocation) {
                    requestActivityRecognition(activityRecognitionInterval);
                }

                addPoint(null, "enterResumeMode",  // Первая точка
                        0, null, null, false);

                locationServiceIsReady = true;
            }

            if (!mockLocation) {
                int i = Integer.parseInt(  // Восстанавливаем нормальный интервал
                        walkSettings.getString("recording_max_seconds_between_points", "60"));
                if (i != locationRequestInterval) {
                    locationRequestInterval = i;
                    requestLocationUpdates(locationRequestInterval, locationRequestInterval);
                }
            }

        } else if (!mockLocation) {  // Хорошей точки не получили!
            if (locationRequestInterval != locationRequestInterval3) {  // !!!
                locationRequestInterval = locationRequestInterval3;
                requestLocationUpdates(locationRequestInterval, locationRequestInterval); // Учащаем
            }
        }

        if (locationServiceIsReady && !mockLocation) {  // Если изменилась настройка
            ARState.setAbsolutelyMaxSpeed(Integer.parseInt(
                    walkSettings.getString("recording_max_possible_speed", "250")));
        }
    }

    int testLocation(Location location) {
        int r = 0;
        if (locationServiceIsReady ||
            !Utils.loc2LatLng(location).equals(Utils.loc2LatLng(lastGoodLocation))) {  // Первые 2 точки должны быть разные
            r = 1;

            float x = location.getAccuracy();
            float y = Integer.parseInt(walkSettings.getString("recording_min_location_accuracy", "50"));
            String s = " Test 2 (accuracy) " + x + " < " + y;
            if (x < y) { // Точность определения положения приемлема
                r = 2;
            };
            Utils.logD(TAG, "onLocationChanged #"+iOnLocationChanged +
                s + " " + (r == 2 ? "Ok" : "NOT Ok"));
            if (r == 2) {
                if (arStateLast == null) {
                    s = "Test 3 arStateLast == null";
                    r = 5;
                } else if (System.currentTimeMillis()-location.getTime()> // Не протухла ли location ?
                    Integer.parseInt(walkSettings.getString("recording_max_seconds_between_points", "60"))
                        * 1000 * nStale) {
                    s = "Test 3 - too much time after previous point";
                    r = 6;
                } else {
                    x = Utils.getDistance(Utils.loc2LatLng(location), Utils.loc2LatLng(lastGoodLocation));
                    y = arStateLast.maxPossibleSpeed / 3.6f;  // m/s
                    float z = arStateMean.maxPossibleSpeed / 3.6f;
                    long i = (location.getTime() - lastGoodLocation.getTime()) / 1000;
                    s = "Test 3 (erronious jump) " + x + " <= max(" + y + "," + z + ") * " + i;
                    if (x <= Math.max(y, z) * i) {
                        r = 3;
                    }
                }
                Utils.logD(TAG, "onLocationChanged #"+iOnLocationChanged +
                    " " + s + " " + "  r=" + r + (r >= 3 ? " Ok" : " NOT Ok"));
            }
        }
        return r;
    }

    void addPoint(
            Location location, String debugInfo,
            int afKind, String afUri, String afFilePath, boolean isLastPoint) {

        if (!locationServiceIsReady) {
            if (isLastPoint) { // Еще не добавили первую точку, нажали back
                stop(6);
            }
            return;
        }

        if (location != null &&  // Вызов из MapActivity, еще не проверена - проверяем
                testLocation(location) >= 3) {
            prevGoodLocation.set(lastGoodLocation);
            lastGoodLocation.set(location);
        }

        boolean pointIsAllreadyPresent =  // Новой точки не будет
                Utils.getDistance(Utils.loc2LatLng(lastGoodLocation), Utils.loc2LatLng(prevGoodLocation)) <=
                    Math.max(Integer.parseInt(walkSettings.getString("recording_min_meters_between_points", "10")),
                        (lastGoodLocation.getAccuracy() + prevGoodLocation.getAccuracy()) * ACCURACY_FACTOR);
/*
In statistical terms, it is assumed that location errors are random with a normal distribution,
so the 68% confidence circle represents one standard deviation.
*/
        arStateMean.infereType(lastGoodLocation);
        final ARState arState2 = new ARState(arStateMean);
        // Инициализируем на следующий интервал
        if (pointIsAllreadyPresent) {
            arStateMean.reset(arStateMean.typePrev, lastGoodLocation);
        } else {
            arStateMean.reset(arStateMean.type, lastGoodLocation);
        }
        final Location location2 = lastGoodLocation;
        new Thread(() -> {  // Безымянная
            totalStr = addPoint2(location2, debugInfo, afKind, afUri, afFilePath, isLastPoint, arState2,
                    pointIsAllreadyPresent);
        }).start();
    }

    private synchronized String addPoint2(  // Возвращает totalStr прогулки; выполняется в НЕглавной Thread !!!
            Location location, String debugInfo,
            int afKind, String afUri, String afFilePath, boolean isLastPoint, ARState arState,
                                            boolean pointIsAllreadyPresent) {
        Utils.logD(TAG, "addPoint2 start: "+debugInfo+" "+Utils.loc2Str(location));

        long t=clock.getTime();

        ContentValues values=new ContentValues();
        String pointAddress="";

        boolean toAdd = lastPointLocation==null || !pointIsAllreadyPresent;
        if (toAdd) {
            lastPointNumber++;
            values.put(DB.KEY_POINTWALKID, walkId);
            values.put(DB.KEY_POINTFLAGRESUME, lastPointLocation==null);
            values.put(DB.KEY_POINTTIME, t);
            values.put(DB.KEY_POINTTIMEEND, t);
            values.put(DB.KEY_POINTLOCATION, Utils.loc2Str(location));
            values.put(DB.KEY_POINTDEBUGINFO, "#"+lastPointNumber+" "+debugInfo);
            pointAddress=Utils.getAddress(geocoder, Utils.loc2LatLng(location));
            values.put(DB.KEY_POINTADDRESS, pointAddress);
            values.put(DB.KEY_POINTACTIVITYTYPE, arState.type); // Как сюда попали - для цвета линии

            if (BuildConfig.BUILD_TYPE.equals("debug")) {
                BatteryManager batteryManager = (BatteryManager) this.getSystemService(Context.BATTERY_SERVICE);
                if (batteryCapacity < 0) { // Первый раз
                    batteryCapacity = 100f * // microampere-hours
                            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) /
                            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                }
                float batteryCharge =  // %
                        batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) /
                                batteryCapacity * 100;
                values.put(DB.KEY_POINTBATTERYCHARGE, batteryCharge);
            }

            lastPointId=DB.db.insert(DB.TABLE_POINTS, null, values);
            Utils.logD(TAG, "A point is added: #"+lastPointNumber+" "+debugInfo);

        } else {
            values.put(DB.KEY_POINTTIMEEND, t);
            DB.db.update(DB.TABLE_POINTS, values, DB.KEY_POINTID + "=" + lastPointId, null);
            Utils.logD(TAG, "Last point is updated - location didn't change. "+debugInfo);
        }

        // Сохраняем артефакты
        if (afKind>0) {  // Передан артефакт
            values.clear();
            values.put(DB.KEY_AFWALKID, walkId);
            values.put(DB.KEY_AFPOINTID, lastPointId);
            values.put(DB.KEY_AFPOINTNUMBER, lastPointNumber); // В прогулке
            values.put(DB.KEY_AFTIME, t);
            values.put(DB.KEY_AFKIND, afKind);
            values.put(DB.KEY_AFURI, afUri);
            values.put(DB.KEY_AFFILEPATH, afFilePath);
            values.put(DB.KEY_AFDELETED, false);

            DB.db.insert(DB.TABLE_AFS, null, values);

            lastAFNumber++;
            Utils.logD(TAG, "An artefact is saved (1): #"+lastAFNumber+", point #"+lastPointNumber+
                    " "+afKind+" "+afUri+" "+afFilePath);
        }
        // Фото, видео и звук
        if (lastPointLocation==null) { // Запоминаем последние Id'ы по видам
            for (int i=0; i<=Walk.AFKIND_MAX; i++) lastAFIds.add(0L);
            for (int afKind2 : new int[] {Walk.AFKIND_PHOTO, Walk.AFKIND_VIDEO, Walk.AFKIND_SPEECH}) {
                Uri contentUri=
                        afKind2==Walk.AFKIND_PHOTO ?
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                                afKind2==Walk.AFKIND_VIDEO ?
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                                        afKind2==Walk.AFKIND_SPEECH ?
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI : null;
                if (contentUri!=null) {
                    Cursor cursor=getContentResolver().query(
                            contentUri, new String[]{MediaStore.MediaColumns._ID}, null, null,
                            MediaStore.MediaColumns._ID+" desc");
                    if (cursor.moveToFirst()) {
                        lastAFIds.set(afKind2,
                                cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
                    }
                    cursor.close();
                }
            }
        } else {     // Прицепляем артефакты, появившиеся после прошлого раза
/*
            String[] what={
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, // Build.VERSION.SDK_INT > Q
            };
*/
            for (int afKind2 : new int[]{Walk.AFKIND_PHOTO, Walk.AFKIND_VIDEO, Walk.AFKIND_SPEECH}) {
                Uri contentUri=
                        afKind2==Walk.AFKIND_PHOTO ?
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                                afKind2==Walk.AFKIND_VIDEO ?
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String where=MediaStore.MediaColumns._ID + ">?";
                String[] args = {"" + lastAFIds.get(afKind2)};
/*
                Cursor cursor;
                try {
                    cursor=getContentResolver().query(
                            contentUri, what, where, args,
                            MediaStore.MediaColumns._ID);
                } catch (SQLiteException e) {  // В Android 9 в MediaStore.Audio.Media нет MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                                                // И, вероятно, в остальных в младших версиях
                    String[] what2={
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DATE_ADDED,
                            MediaStore.MediaColumns.DATA,
                    };
                    cursor=getContentResolver().query(
                            contentUri, what2, where, args,
                            MediaStore.MediaColumns._ID);
                }
*/
                Cursor cursor = getContentResolver().query(
                            contentUri, null, where, args,  // Все поля
                            MediaStore.MediaColumns._ID);
                boolean flagFirst=true;
                while (flagFirst && cursor!=null && cursor.moveToFirst() ||
                        !flagFirst && cursor.moveToNext()) {
                    flagFirst=false;

                    afUri=Uri.withAppendedPath(contentUri,
                            ""+cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))).toString();
                    afFilePath=cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
                    Utils.logD(TAG,"Artefact is being saved " + afFilePath);

                    String bucket;
                    int i = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
                    if (i >= 0) {
                        bucket = cursor.getString(i);
                    } else {
                        bucket = Utils.getNthPiece(afFilePath, -2, "\\/");  // Каталог последнего уровня
                    }
                    if (!isOurArtefact(afKind2, afFilePath, bucket)) {
                        continue;
                    }

                    long pointId=lastPointId;
                    int pointNumber=lastPointNumber;
                    long timeAdded=cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED))
                            *1000;
                    if (toAdd &&
                            timeAdded-lastPointTime< // Если ближе к предыдущей,
                            t-timeAdded) { // прицепляем к ней
                        pointId=lastPointId-1;
                        pointNumber=lastPointNumber-1;
                    }

                    lastAFNumber++;
                    values.clear();
                    values.put(DB.KEY_AFWALKID, walkId);
                    values.put(DB.KEY_AFPOINTID, pointId);
                    values.put(DB.KEY_AFPOINTNUMBER, pointNumber);
                    values.put(DB.KEY_AFTIME, timeAdded);
                    values.put(DB.KEY_AFKIND, afKind2);
                    values.put(DB.KEY_AFURI, afUri);
                    values.put(DB.KEY_AFFILEPATH, afFilePath);
                    values.put(DB.KEY_AFDELETED, false);
                    DB.db.insert(DB.TABLE_AFS, null, values);

                    lastAFIds.set(afKind2,
                            cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
                    Utils.logD(TAG, "The artefact is saved (2): #" + lastAFNumber + ", point #" + pointNumber +
                            " " + afKind2 + " " + afUri + " " + afFilePath+" "+new Date(timeAdded));
                }
                cursor.close();
            }
        }
        lastPointTime=t;

        // Теперь update'им прогулку
        values.clear();
        if (lastPointLocation==null) {
            if (walkDuration==0) {  // Самое начало прогулки
                walkDuration=deltaDuration=System.currentTimeMillis()-startTime; // Времена по одним часам !!!
                startTime=startTime+lastPointTime-System.currentTimeMillis();  // По часам location
                values.put(DB.KEY_STARTTIME, startTime);
                if (!pointAddress.equals("")) { // Адрес первой точки - место начала прогулки
                    values.put(DB.KEY_STARTPLACE, pointAddress);
                }
            } else {  // Начало продолжения
                deltaDuration+=lastPointTime-startTime-walkDuration;
                if (endLocation!=null) {  // На всякий ...
                    walkLength+=Utils.getDistance(Utils.loc2LatLng(location),
                            Utils.loc2LatLng(endLocation));
                    deltaLength+=Utils.getDistance(Utils.loc2LatLng(location),
                            Utils.loc2LatLng(endLocation));
                }
            }
        } else if (toAdd){
            walkLength=walkLength+Utils.getDistance(
                    Utils.loc2LatLng(location),Utils.loc2LatLng(lastPointLocation));
        }
        walkDuration=lastPointTime-startTime;

        values.put(DB.KEY_DURATION, walkDuration);
        values.put(DB.KEY_DURATIONNETTO, walkDuration-deltaDuration);
        values.put(DB.KEY_LENGTH, walkLength);
        values.put(DB.KEY_LENGTHNETTO, walkLength-deltaLength);
        DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID+"="+walkId, null);

        if (screenIsOn) {  // Нарисует все когда включится экран - экономим электроэнергию
            LocalBroadcastManager.getInstance(this).sendBroadcast(  // Нарисует добавленную точку
                    new Intent("DoInUIThread")
                            .putExtra("action", "loadAndDraw"));
        }

        if (toAdd) {
            lastPointLocation=new Location(location);  // Только здесь
        }

        Utils.logD(TAG, "addPoint2 stop: "+debugInfo);

        if (isLastPoint) {  // Конец прогулки
/*
            if (locationServiceIsReady) {
                stop(2);
            } else {      // В mapActivity нажали назад не дождавшись появления первой точки
                stop(6);
            }
*/
            stop(2);
            return null;
        } else {
            return Walk.formTotalStr(  // Строка для notification
                    walkSettings,
                    walkDuration, walkLength,
                    walkDuration - deltaDuration, walkLength - deltaLength,
                    walkDuration, walkLength,
                    walkDuration - deltaDuration, walkLength - deltaLength,
                    null);
        }
    }

    static Thread startMocking(
            Utils.Consumer<Location> onLocationChanged, String threadName, int interval,
            Location locationStart) {
        Utils.logD(TAG, "startMocking");

        final Location[] prevLocation={new Location("")};
        Thread mockerThread=new Thread(()->{
            Random randomGenerator=new Random();
            Utils.logD(WalkRecorder.TAG, "Thread "+threadName+" started");
            Location location=new Location("mock provider");
            boolean interrupted=false;
            double vLat=1e-5; // 1e-4 гр/сек = 13 м/сек = 47 км/час
            double vLng=1e-5;
            double k=1e-5;
            while (!interrupted) {
                long t=System.currentTimeMillis();
                if (false) {//!Utils.isBetween(t-timeStart,15000,30000) { // Симулируем плохой сигнал
                    location.set(prevLocation[0]);
                } else {
                    if (location.getLatitude()==0) {
                        int r=randomGenerator.nextInt(100);
                        location.setLatitude(locationStart.getLatitude() + r * k);
                        location.setLongitude(locationStart.getLongitude() - r * k);
                        k=1e-10 * interval;
                    } else {
                        location.setLatitude(location.getLatitude() +
                                vLat * (t - location.getTime()) * 1e-3 +
                                randomGenerator.nextInt(100) * k);
                        location.setLongitude(location.getLongitude() +
                                vLng * (t - location.getTime()) * 1e-3 +
                                randomGenerator.nextInt(100) * k);
                    }
                    if (prevLocation[0]!=null) {
                        location.setBearing(prevLocation[0].bearingTo(location));
                    }
                }
                location.setTime(t);
                prevLocation[0].set(location);

                if (true) { //!Utils.isBetween(t-timeStart,45000,120000)) { // Симулируем отсутствие сигнала
    //                                onLocationChanged(location);  // !!!
                    Utils.logD(TAG, "Thread "+threadName+": emulated location " + location);
                    onLocationChanged.accept(location);  // !!!
                }
                interrupted=Utils.sleep(interval, true);
            }
            Utils.logD(TAG, "Thread "+threadName+" is interrupted");
        },threadName);
        mockerThread.start();
        return mockerThread;
    }

    static Pattern pattern;
    static final String BAD_PLACES=".*(download|screenshots|viber|whatsapp|skype|GroupMe|Line|WeChat|Kakao|MessageMe|Kik|Tango|Cubie|Facebook|Hike|Hangouts|Maaii|FaceTime|BBM|Rounds|Snapchat|Nimbuzz|ChatON|Voxer|Hipchat|Telegram|Instagram|Zoom).*";
    boolean isOurArtefact(int afKind, String afFilePath, String bucket) { // Чтобы не попали посторонние, например, пришедшие по Vyber'e
        Utils.logD(TAG, "isOurArtefact "+afFilePath);

        if ("Camera".equals(bucket) || MapActivity.DIR_AUDIO.equals(bucket)) {
            Utils.logD(TAG, "isOurArtefact bucket=" + bucket + " -> true");
            return true;
        }

        Long t;
        if (pattern==null) {  // При первом обращении
            pattern=Pattern.compile(BAD_PLACES,Pattern.CASE_INSENSITIVE);
        }
        if (pattern.matcher(afFilePath).matches()) {
            Utils.logD(TAG, "isOurArtefact filepath -> false");
            return false;
        }
        try { // jpg: 2016:02:23 22:15:43
            ExifInterface exif = new ExifInterface(afFilePath);
            t=Utils.dateTime2Long(exif.getAttribute(ExifInterface.TAG_DATETIME));
            Utils.logD(TAG, "isOurArtefact - EXIF: " +
                    exif.getAttribute(ExifInterface.TAG_DATETIME)+
                    " lastPointTime: "+Utils.time2Str(lastPointTime));
            if (t>0 && t<lastPointTime) {
                Utils.logD(TAG,"isOurArtefact time -> false");
                return false;
            }
        } catch (Exception e) {
        }

        Utils.logD(TAG, "isOurArtefact -> true");
        return true;
// Добавить: дата из имени файла
/* Бессмысленно - дата файла всегда равна дате его появления на устройстве
        try {
            Long t=new File(afFilePath).lastModified()
            if (t<lastPointTime) {
                return false;
            }
        } catch (Exception e) { // Успели убить
            return false;
        }
*/
/* Тоже бессмысленно - dateTaken=дате файла
        if (afKind==Walk.AFKIND_PHOTO || afKind==Walk.AFKIND_VIDEO) {
            if (dateTaken < lastPointTime) {
                return false;
            }
        }
*/
/* И опять бессмысленно: дает дату включения девайса
        try { // Video и Audio: 2016 02 25
            MediaMetadataRetriever mediaMetadataRetriever= new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(afFilePath);
            Utils.logD(TAG, "isOurArtefact - METADATA_KEY_DATE: " + mediaMetadataRetriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DATE));
            t=Utils.dateTime2Long2(mediaMetadataRetriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DATE));
Utils.logD(TAG, "isOurArtefact: "+t+" "+lastPointTime);
            if (t>0 && t+24*3600*1000<lastPointTime) {
                return false;
            }
        } catch (Exception e) {
        }
*/
    }

    static class Clock {  // Все времена в программе по этим часам !
        long timeDeviceStart; // Время включения device'a по часам location в миллисекундах

        Clock(Location location) {
            timeDeviceStart = location.getTime() - SystemClock.elapsedRealtime();
        }

        long getTime() {
            return timeDeviceStart + SystemClock.elapsedRealtime();
        }
    }

    static void switchLogcatRecorder(boolean on, SharedPreferences settings, Context context) {
        if (settings.getBoolean("developer_start_logcat_recorder_application", false)) {
            String s = settings.getString("developer_logcat_recorder_application", null);
            if (s != null) {
                Utils.logD(TAG, "switchLogcatRecorder "+on);
                try {
                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(s);
                    if (on) {
                    } else { // Не работает, и не надо
                        if (s.equals("com.dp.logcatapp")) {
                            s = s +".activities.MainActivity" + "_stop_recording_extra";
                            intent.putExtra(s,true);
                        }
                    };
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(
                            context, s + "is not installed",
                            Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    static void switchDevelopersLog(boolean on, SharedPreferences settings) {
        if (settings.getBoolean("developer_write_developers_log", false)) {
            if (on) {
                Utils.startDevelopersLog();
            } else {
                Utils.stopDevelopersLog();
            }
        }
    }
}
/*
During the Google IO presentation, a chart was presented showing effect of different priorities
of the recognition algorithm as tested multiple times on Galaxy Nexus.

Priority	Typical location update interval	Battery drain per hour (%)	Accuracy
HIGH_ACCURACY	5 seconds	7.25%	~10 meters
BALANCED_POWER	20 seconds	0.6%	~40 meters
NO_POWER	N/A	small	~1 mile
*/