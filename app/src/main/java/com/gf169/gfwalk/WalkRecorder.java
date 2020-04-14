package com.gf169.gfwalk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Geocoder;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Vector;
import java.util.regex.Pattern;

public class WalkRecorder extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    static final String TAG="gfWalkRecorder";

    static boolean isWorking;
    static long curSessionStartTime; // По здешним часам
    static volatile Location lastGoodLocation=null;  // Последняя полученная от provider'a
        // не ошибочная точка. static чтобы была видна из mapActivity
    static volatile int curLocationIsOK; // Последняя полученная точка свежая и хорошая

    boolean mockLocation=false; // Ниже переустановлю: если под эмулятором - true

    int callNumber=-1;
    volatile Boolean isFirstCall=true;
    Thread timerThread;
    Thread mockerThread;
    Thread waitingThread;

    volatile GoogleApiClient googleApiClient=null;
    volatile boolean locationServiceIsReady=false;
    volatile boolean isLastPoint;

    Intent savedIntent;

    Location lastPointLocation=null;  // Последняя точка маршрута. Служит и флагом начала и продолжения прогулки
    long lastPointTime=0;
    Vector<Long> lastAFIds=new Vector<>(Walk.AFKIND_MAX+1); // По видам артефаков

    long startTime;  // Время начала прогулки
    Location endLocation; // Последней точки последнего продолжения
    long walkDuration; // в миллисекундах
    long deltaDuration=0; //  Начальная duration-durationNetto (duration с учетом приостановок)
    float walkLength; // в метрах
    float deltaLength;

    int lastPointNumber; // в прогулке
    int lastAFNumber; // в прогулке
    long lastPointId=0;
    int iOnLocationChanged=-1;
    volatile long[] nextPointTime={0L};
    NotificationManager notificationManager;
    NotificationCompat.Builder notifyBuilder;
    int notifyID=1;
    Geocoder geocoder;

    Clock clock;

    int locationRequestInterval=3; // Будет взят из настроек
    int locationRequestFastestInterval=1;
    int locationRequestIntervalIni=1;
    int locationRequestPriority=LocationRequest.PRIORITY_HIGH_ACCURACY;
    final float ACCURACY_FACTOR=0.5F;  // TODO: Уточнить !
    int maxPossibleSpeed=1000; // км/час
    int waitingThreadTimeout = 2000;

    int activityRecognitionIntervalK=1; // * locationRequestInterval !!!
    ARState curARState;
    PendingIntent arPendingIntent;
    boolean isStill;

    SharedPreferences walkSettings;

    class ARState { //ActivityRecognitionState
        DetectedActivity activity;
        String name="";
        int conf=0;
        int maxPossibleSpeed=-1;

        ARState(ActivityRecognitionResult result) {
            String s="",s2;
            int v;
            for (DetectedActivity activity : result.getProbableActivities()) {
                s2="?"+activity.getType();
                v=-1;
                switch(activity.getType()) {
                    case DetectedActivity.IN_VEHICLE:
                        s2="on_vehicle";
                        v=Integer.valueOf(
                                walkSettings.getString("recording_max_possible_speed", "1000"));
                        break;
                    case DetectedActivity.ON_BICYCLE:
                        s2="on_bicycle";
                        v=60;
                        break;
                    case DetectedActivity.ON_FOOT:
                        s2="on_foot";
                        break;
                    case DetectedActivity.RUNNING:
                        s2="running";
                        v=25;
                        break;
                    case DetectedActivity.WALKING:
                        s2="walking";
                        v=10;
                        break;
                    case DetectedActivity.STILL:
                        s2="still";
                        v=0;
                        break;
                    case DetectedActivity.UNKNOWN:
                        s2="unknown";
                        break;
                    case DetectedActivity.TILTING:
                        s2="tilting";
                        break;
                }
                s+=s2+"-"+activity.getConfidence()+"% ";
                if (v>=0 && activity.getConfidence()>conf) {
                    conf=activity.getConfidence();
                    this.activity=activity;
                    name=s2;
                    maxPossibleSpeed=v;
                }
            }
            Utils.logD(TAG, "ARState: "+s+" -> "+name+" "+maxPossibleSpeed+"km/h");
        }
    }
    @Override
    public void onCreate() {
        Utils.logD(TAG, "onCreate");

        Utils.setUncaughtExceptionHandler(  // Чтобы при отвале все культурно убивало
            new Runnable() {
                @Override
                public void run() {
                    stop(5);
                }
            }
        );
        isWorking=true;
        lastGoodLocation=null;

//        AirbrakeNotifier.register(this,
//                "f17762b5ea71e1af3bcf37ba0cb2a67c",
//                "", false);

/* Требует     <uses-permission android:name="android.permission.READ_PHONE_STATE" />
        TelephonyManager telephonyManager=(TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String IMEI=telephonyManager.getDeviceId();
        mockLocation=IMEI!=null && !IMEI.isEmpty() && IMEI.replace("0", "").isEmpty(); // У эмулятора куча нулей
*/
        mockLocation=Build.MODEL.contains("Android SDK built for x86");
        walkSettings=
                SettingsActivity.getCurrentWalkSettings(WalkRecorder.this, -1);

        googleApiClient=new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        if (mockLocation) {
            startMocking();
        } else {
            googleApiClient.connect();
        }
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            CharSequence name = "gfWalk";
//            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            String channelId="gfWalk";
            NotificationChannel channel = new NotificationChannel(channelId,
                    "gfWalk", NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null,null);
//            String description = "gfWalk";
//            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            notifyBuilder = new NotificationCompat.Builder(this,channelId);
        } else {
            notifyBuilder = new NotificationCompat.Builder(this);
        }
        startForeground(notifyID, notifyBuilder
                .setSmallIcon(R.drawable.ic_recording_1)
                .setContentTitle(getString(R.string.notification_recording))
                .setContentText(getResources().getString(R.string.waiting_for_GPS))
//                .setCategory(NotificationCompat.CATEGORY_ALARM)
//                .setOngoing(false)  // Никак крест не появляется :(
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
        notificationManager=(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        geocoder=new Geocoder(this, Locale.getDefault());
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.logD(TAG, "onStartCommand "+intent);

        final Location lastGoodLocation2=lastGoodLocation;
        if (ActivityRecognitionResult.hasResult(intent)) {
            ARState s=new ARState(ActivityRecognitionResult.extractResult(intent));
            if (s.maxPossibleSpeed>=0) { // Не tilt...
                curARState = s;
                maxPossibleSpeed = Math.max(maxPossibleSpeed, curARState.maxPossibleSpeed); // max !!!
            }
            return Service.START_STICKY_COMPATIBILITY;
        }
        if (intent==null) {  // Чтобы не отваливалось после отвала MapActivity. Не нужно?
            stop(1);
            return Service.START_STICKY_COMPATIBILITY;
        }
        Bundle extras=intent.getExtras();
        final boolean isLastPoint=extras.getBoolean("isLastPoint"); // Локальная !  Больше точек не будет, надо убить сервис
        this.isLastPoint=isLastPoint; // А это для TimerThread
        final int walkId=extras.getInt("walkId");
        final String debugInfo=extras.getString("debugInfo","");
        final int afKind=extras.getInt("afKind"); // Может быть передан артефакт - если SPEECH или TEXT
        final String afUri=extras.getString("afUri");
        final String afFilePath=extras.getString("afFilePath");

        if (isLastPoint) {
            stop(2);  // Все, больше звонки не принимаем
        }
        if (!locationServiceIsReady) {
            if (isLastPoint) {
                stop(6);
                return Service.START_STICKY_COMPATIBILITY;
            } else {
                if (savedIntent==null) {  // Самый первый вызов
                    this.savedIntent = intent; // Чтобы повторить первый вызов когда появится GPS

                    waitingThread = new Thread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    toast(getResources().getString(R.string.waiting_for_GPS),
                                            Toast.LENGTH_SHORT);
                                    boolean interrupted = false;
                                    while (!interrupted && !locationServiceIsReady) {
                                        interrupted = Utils.sleep(waitingThreadTimeout, true);

                                        if (!locationServiceIsReady) {
                                            toast(getResources().getString(R.string.waiting_for_GPS),
                                                    Toast.LENGTH_SHORT);
                                        }
                                    }
                                }
                            }
                            , "gfWaitingForGPS");
                    waitingThread.start();
                }
                return Service.START_STICKY_COMPATIBILITY;
            }
        }
        callNumber++;

        if (isFirstCall) {  // Должно быть в main thread
            walkSettings =
                    SettingsActivity.getCurrentWalkSettings(WalkRecorder.this, walkId);
        }

        final String threadName="gfWalkRecorder #"+callNumber;
        Utils.logD(TAG, "Thread "+threadName+" is starting...");
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        Utils.logD(TAG, "Thread "+threadName+"(?) started");

                        addPoint(lastGoodLocation2, walkId, debugInfo, afKind, afUri, afFilePath, isLastPoint);

                        if (isLastPoint) {
                            Utils.logD(TAG, "Thread "+threadName+"(?): last point is "+
                                    "added, service is stopping and thread is too");
                            return;
                        }
                        synchronized (isFirstCall) {
                            if (!isFirstCall) {
                                Utils.logD(TAG, "Thread "+threadName+"(?) is ending");
                                return;
                            }
                            isFirstCall=false;
                        }
                        Utils.logD(TAG, "Thread "+threadName+"(?) is staying resident");
                        timerThread=Thread.currentThread(); // Здесь !!!

                        int iNotifyIcon=0;
                        boolean interrupted=false;
                        String totalStr="";
                        Location prevLocation=new Location("");
                        while (!interrupted) {
                            while (!interrupted && SystemClock.elapsedRealtime()<nextPointTime[0]) {
                                Location lastGoodLocation2=lastGoodLocation;
                                if (lastGoodLocation2==null) {  // Конец - onDestroy убил lastGoodLocation
                                    return;
                                }
                                if (lastGoodLocation2.getTime()==prevLocation.getTime()) {
                                    // Не было onLocationChange - в метро
                                    if (System.currentTimeMillis()-prevLocation.getTime()>
                                        Math.max(locationRequestInterval,locationRequestFastestInterval)*1000*2) {
                                        if (curLocationIsOK>=0) {
                                            curLocationIsOK = -1;  // Пропал GPS и остальное - ничего не получаем
                                            drawCurPosMarker();
                                        }
                                    }
                                } else {
                                    prevLocation.set(lastGoodLocation2);
                                }
                                iNotifyIcon=++iNotifyIcon%2;
                                if (curLocationIsOK>=0) {
                                    notifyBuilder
                                            .setSmallIcon(iNotifyIcon == 0 ?
                                                    R.drawable.ic_recording_1 :
                                                    R.drawable.ic_recording_2)
                                            .setContentText(totalStr);
                                } else {
                                    notifyBuilder
                                            .setSmallIcon(iNotifyIcon==0 ?
                                                    R.drawable.ic_recording_1 :
                                                    R.drawable.ic_recording_3)
                                            .setContentText(getResources().
                                                    getString(R.string.location_not_defined));  // На Galaxy не пишет по-русски !
                                }
                                notificationManager.notify(notifyID, notifyBuilder.build());

                                interrupted=Utils.sleep(1000, true);
                            }
                            Location lastGoodLocation2=lastGoodLocation;
                            if (interrupted || WalkRecorder.this.isLastPoint || lastGoodLocation2==null) {
                                Utils.logD(TAG, "Resident thread: thread is ending - "+
                                        (interrupted ? "interrupted" : "isLastPoint"));
                                return;
                            }
                            totalStr = Utils.nvl(
                                    addPoint(lastGoodLocation2,walkId,"from timer",0,null,null,false),
                                    totalStr);
                        }
                        Utils.logD(TAG, "Resident thread is interrupted");
                    }
                },threadName).start();
        return Service.START_STICKY_COMPATIBILITY;
    }
    @Override
    public void onDestroy() {
        stop(0); // Вдруг снаружи кто-то сделает stopService
        Utils.logD(TAG, "onDestroy");
    }
    void stop(int callNumber) {
        Utils.logD(TAG, "stop "+callNumber);

        Utils.setUncaughtExceptionHandler(null);

        if (googleApiClient!=null) {
            if (googleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
                if (arPendingIntent!=null) {
                    ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient,
                            arPendingIntent);
                }
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
        isWorking=false;
        lastGoodLocation=null;

        stopSelf();
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
        requestLocationUpdates(locationRequestIntervalIni); // Начальные, для определения состояния
    }
    public void requestLocationUpdates(int interval) {
        LocationRequest locationRequest=LocationRequest.create();
        locationRequest.setInterval(interval * 1000);
        locationRequest.setFastestInterval(locationRequestFastestInterval * 1000);
        locationRequest.setPriority(locationRequestPriority);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this);
    }
    public void requestActivityRecognition(int interval) {
        arPendingIntent=PendingIntent.getService(
                this, 3, new Intent(this, WalkRecorder.class), PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                googleApiClient, interval * 1000, arPendingIntent);

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
    public void onLocationChanged(Location location) {
        iOnLocationChanged++;
        Utils.logD(TAG, "onLocationChanged #"+iOnLocationChanged+": "+
                Utils.timeStr2(System.currentTimeMillis())+" "+
                Utils.loc2Str(location)+" "+(curARState==null ? "" : curARState.name));

        if (lastGoodLocation==null) {// Самая первая - может быть старье
            lastGoodLocation=new Location("");
            lastGoodLocation.set(location);
            return;
        }
        int ii=0;
        isStill&= locationServiceIsReady &  // Пока Location service не заработал, на AR не обращаем внимания
                curARState!=null && curARState.name.equals("still");
        if (isStill) {
            long t=location.getTime();
            location.set(lastGoodLocation);
            location.setTime(t);
            ii=4;
            curLocationIsOK=1;
            maxPossibleSpeed=0;
        } else {
            if (locationServiceIsReady || // Первые 2 точки должны быть разные
                    !Utils.loc2LatLng(location).equals(Utils.loc2LatLng(lastGoodLocation))) {
                ii = 1;
                if (location.getAccuracy() <  // Точность определения положения приемлема
                        Integer.valueOf(walkSettings.getString("recording_min_location_accuracy", "50"))) {
                    ii = 2;
                    if (Utils.getDistance(
                                Utils.loc2LatLng(location),Utils.loc2LatLng(lastGoodLocation)) <=
                        (location.getTime() - lastGoodLocation.getTime())*maxPossibleSpeed / 3600) {
                        ii = 3;
                        curLocationIsOK=1;
                        isStill=true;
                        maxPossibleSpeed= curARState==null ?
                            Integer.valueOf(
                                        walkSettings.getString("recording_max_possible_speed", "1000"))
                            : curARState.maxPossibleSpeed;
                    }
                }
            }
            if (ii<3) {
                curLocationIsOK=0;  // Плохая :(
                isStill=false;
            }
        }
        Utils.logD(TAG, "onLocationChanged #"+iOnLocationChanged+": OK="+ii);

        if (curLocationIsOK>0) { // Все ОК, полноценная точка

            lastGoodLocation.set(location);

            if (!locationServiceIsReady) {  // В начале - убиваем пробный request, запускаем боевой
                if (!mockLocation) {
//                    LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
                    locationRequestInterval=Integer.valueOf(
                            walkSettings.getString("recording_location_request_interval", "10"));
                    requestLocationUpdates(locationRequestInterval);
                    requestActivityRecognition(locationRequestInterval*activityRecognitionIntervalK);
                }
                locationServiceIsReady = true; // Можно начинать запись прогулки
                clock = new Clock(location); // Запускаем часы
                curSessionStartTime=clock.getTime();
                if (savedIntent != null) {
                    startService(savedIntent);  // Повторяем самый первый вызов из MapActivity
                } else {
                    return;
                }
            }
        }

        drawCurPosMarker(); // Рисуем в любом случае

        if (locationServiceIsReady && !mockLocation) {  // Если изменилась настройка
            int i = Integer.valueOf(
                    walkSettings.getString("recording_location_request_interval", "10"));
            if (i != locationRequestInterval) {
                locationRequestInterval = i;
//                LocationServices.FusedLocationApi.
//                        removeLocationUpdates(googleApiClient, WalkRecorder.this);
                requestLocationUpdates(locationRequestInterval);
            }
        }
    }
    void drawCurPosMarker() {
        Intent intent=new Intent("DoInUIThread")// Делаем дела в интерфейсе
                .putExtra("action", "drawCurPosMarker")  // Нарисуй маркер текущего положения
                ;
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    synchronized String addPoint(  // Возвращает totalStr прогулки; выполняется в НЕглавной Thread !!!
            Location location,
            int walkId, String debugInfo,
            int afKind, String afUri, String afFilePath, boolean isLastPoint) {
        Utils.logD(TAG, "addPoint: "+debugInfo+" "+Utils.loc2Str(location));

        if (location==null || // Вышли еще не успев получить
                clock==null) {  // Нажата кнопка Назад до появления первой точки
            return "Walk failed";  // Никуда не идет
        }
        setNextPointTime();  // Сразу устанвливаем или отодвигаем

        long t=clock.getTime();

        if (lastPointLocation==null) { // Самая первая точка в этом run'e сервиса
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
                    DB.KEY_POINTWALKID+"="+walkId, null, null, null, DB.KEY_POINTID+" desc");
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
        // Не протухла ли location ?
        if (System.currentTimeMillis()-location.getTime()>1000*Integer.valueOf(
                        walkSettings.getString("recording_max_seconds_between_points","60"))
                && !isLastPoint) {  // Последнюю всегда считаем хорошей
            return getResources().getString(R.string.location_not_defined); // Положения не знаем - точку не добавляем !!!
        }
        ContentValues values=new ContentValues();
        String pointAddress="";
        boolean pointIsAdded=
                lastPointLocation==null || // Первую всегда добавляем
                Utils.getDistance(Utils.loc2LatLng(location),Utils.loc2LatLng(lastPointLocation))>
                Math.max(
                    Integer.valueOf(walkSettings.getString("recording_min_meters_between_points", "10")),
                    (location.getAccuracy()+lastPointLocation.getAccuracy())*ACCURACY_FACTOR);
        if (!pointIsAdded) {
            values.put(DB.KEY_POINTTIMEEND, t);
            DB.db.update(DB.TABLE_POINTS, values, DB.KEY_POINTID+"="+lastPointId, null);
            Utils.logD(TAG, "Last point is updated - location didn't change. "+debugInfo);
        } else {
            lastPointNumber++;
            values.put(DB.KEY_POINTWALKID, walkId);
            values.put(DB.KEY_POINTFLAGRESUME, lastPointLocation==null);
            values.put(DB.KEY_POINTTIME, t);
            values.put(DB.KEY_POINTLOCATION, Utils.loc2Str(location));
            values.put(DB.KEY_POINTDEBUGINFO, "#"+lastPointNumber+" "+debugInfo);
            pointAddress=Utils.getAddress(geocoder, Utils.loc2LatLng(location));
            values.put(DB.KEY_POINTADDRESS, pointAddress);
            lastPointId=DB.db.insert(DB.TABLE_POINTS, null, values);
            Utils.logD(TAG, "A point is added: #"+lastPointNumber+" "+debugInfo);
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
            for (int afKind2 : new int[]{Walk.AFKIND_PHOTO, Walk.AFKIND_VIDEO, Walk.AFKIND_SPEECH}) {
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
            String[] what={
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATA,
            };
            for (int afKind2 : new int[]{Walk.AFKIND_PHOTO, Walk.AFKIND_VIDEO, Walk.AFKIND_SPEECH}) {
                Uri contentUri=
                        afKind2==Walk.AFKIND_PHOTO ?
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                                afKind2==Walk.AFKIND_VIDEO ?
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String where=MediaStore.MediaColumns._ID+">"+lastAFIds.get(afKind2);
                Cursor cursor=getContentResolver().query(
                        contentUri, what, where, null,
                        MediaStore.MediaColumns._ID);
                boolean flagFirst=true;
                while (flagFirst && cursor!=null && cursor.moveToFirst() ||
                        !flagFirst && cursor.moveToNext()) {
                    flagFirst=false;

                    afUri=Uri.withAppendedPath(contentUri,
                            ""+cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))).toString();
                    afFilePath=cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
                    if (!isOurArtefact(afKind2,afFilePath)) {
                        continue;
                    }
                    long pointId=lastPointId;
                    int pointNumber=lastPointNumber;
                    long timeAdded=cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED))
                            *1000;
                    if (pointIsAdded &&
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
                    Utils.logD(TAG, "An artefact is saved (2): #" + lastAFNumber + ", point #" + pointNumber +
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
//  Начало прогулки - время нажатия на + !!!
//                startTime=lastPointTime;  // Будет показываться в списке - надо ли update'ить ?
//                values.put(DB.KEY_STARTTIME, startTime);
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
        } else if (pointIsAdded){
            walkLength=walkLength+Utils.getDistance(
                    Utils.loc2LatLng(location),Utils.loc2LatLng(lastPointLocation));
        }
        walkDuration=lastPointTime-startTime;

        values.put(DB.KEY_DURATION, walkDuration);
        values.put(DB.KEY_DURATIONNETTO, walkDuration-deltaDuration);
        values.put(DB.KEY_LENGTH, walkLength);
        values.put(DB.KEY_LENGTHNETTO, walkLength-deltaLength);
        DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID+"="+walkId, null);

        LocalBroadcastManager.getInstance(this).sendBroadcast(  // Нарисует добавленную точку
                new Intent("DoInUIThread")
                        .putExtra("action", "loadAndDraw"));
        if (pointIsAdded) {
            lastPointLocation=new Location(location);
        }
        return Walk.formTotalStr(  // Строка для notification
                walkSettings,
                walkDuration,walkLength,
                walkDuration-deltaDuration,walkLength-deltaLength,
                walkDuration,walkLength,
                walkDuration-deltaDuration,walkLength-deltaLength,
                null);
    }
    void setNextPointTime() {
        if (mockLocation) {
            nextPointTime[0]=SystemClock.elapsedRealtime()+5000;
        } else {
            nextPointTime[0] = SystemClock.elapsedRealtime()+
                    1000*Integer.valueOf(
                            walkSettings.getString("recording_max_seconds_between_points","60"));
        }
    }
    class Clock {  // Все времена в программе по этим часам !
        long timeDeviceStart; // Время включения device'a по часам location

        Clock(Location location) {
            timeDeviceStart=location.getTime()-SystemClock.elapsedRealtime();
        }
        long getTime() {
            return timeDeviceStart+SystemClock.elapsedRealtime();
        }
    }
    static int getAfKind(String fileName) {  // Оставлено на всякий случай
        if (fileName==null) return 0;
        String ext="";
        if (fileName.lastIndexOf(".")>=0) {
            ext=fileName.substring(fileName.lastIndexOf(".")+1);
        }
        if (ext.equalsIgnoreCase("jpg")) return Walk.AFKIND_PHOTO;
        if (ext.equalsIgnoreCase("mp4")) return Walk.AFKIND_VIDEO;
        if (ext.equalsIgnoreCase("m4a") || ext.equalsIgnoreCase("3gpp")) return Walk.AFKIND_SPEECH;
        if (ext.equalsIgnoreCase("txt")) return Walk.AFKIND_TEXT;
        return 0;
    }
    void startMocking() {
        final Location[] prevLocation={new Location("")};
        final String threadName="gfMocker";
        mockerThread=new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        Random randomGenerator=new Random();
                        Utils.logD(WalkRecorder.TAG, "Thread "+threadName+" started");
                        Location location=new Location("mock provider");
                        long timeStart=System.currentTimeMillis();
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
                                    location.setLatitude(55.75222 + r * k);
                                    location.setLongitude(37.61556 - r * k);
                                    k=1e-6;
                                } else {
                                    location.setLatitude(location.getLatitude()+vLat *
                                            (t - location.getTime()) * 1e-3 +
                                            randomGenerator.nextInt(100) * k);
                                    location.setLongitude(location.getLongitude()+vLng *
                                            (t - location.getTime()) * 1e-3 +
                                            randomGenerator.nextInt(100) * k);
                                }
                                if (prevLocation[0]!=null) {
                                    location.setBearing(prevLocation[0].bearingTo(location));
                                }
                            }
                            location.setTime(t);
                            prevLocation[0].set(location);

                            if (true) { //!Utils.isBetween(t-timeStart,45000,120000)) { // Симулируем отсутствие сигнала
                                onLocationChanged(location);  // !!!
                            }
                            interrupted=Utils.sleep(3000, true);
                        }
                        Utils.logD(TAG, "Thread "+threadName+" is interrupted");
                    }
                },threadName);
        mockerThread.start();
    }
    void toast(String text, int duration) { // Просит того, кто может показать - кто впереди
        Intent intent=new Intent("DoInUIThread")
                .putExtra("action", "toast")
                .putExtra("text", text)
                .putExtra("duration", duration);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    static Pattern pattern;
    static final String BAD_PLACES=".*(download|screenshots|viber|whatsapp|skype|GroupMe|Line|WeChat|Kakao|MessageMe|Kik|Tango|Cubie|Facebook|Hike|Hangouts|Maaii|FaceTime|BBM|Rounds|Snapchat|Nimbuzz|ChatON|Voxer|Hipchat).*";
    boolean isOurArtefact(int afKind, String afFilePath) {//}, Long dateTaken) { // Чтобы не попали посторонние, например, пришедшие по Vyber'e
        Utils.logD(TAG, "isOurArtefact "+afFilePath);

        Long t;
        if (pattern==null) {  // При первом обращении
            pattern=Pattern.compile(BAD_PLACES,Pattern.CASE_INSENSITIVE);
        }
        if (pattern.matcher(afFilePath).matches()) {
            return false;
        }
        try { // jpg: 2016:02:23 22:15:43
            ExifInterface exif = new ExifInterface(afFilePath);
            t=Utils.dateTime2Long(exif.getAttribute(ExifInterface.TAG_DATETIME));
            Utils.logD(TAG, "isOurArtefact - EXIF: " +
                    exif.getAttribute(ExifInterface.TAG_DATETIME)+
                    " lastPointTime: "+Utils.time2Str(lastPointTime));
            if (t>0 && t<lastPointTime) {
                Utils.logD(TAG,"isOurArtefact - false !!!");
                return false;
            }
        } catch (Exception e) {
        }
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
/*
    String dirPhoto= Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM).getPath();
    if (!afFilePath.startsWith(dirPhoto)) {

        Utils.logD(TAG, "addPoint: "+afFilePath+" "+dirPhoto);
        Utils.logD(TAG, "isOurArtefact: "+afFilePath);
        Utils.logD(TAG, "isOurArtefact: "+
                Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM).getPath());
        Utils.logD(TAG, "isOurArtefact: "+
                Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC).getPath());
        Utils.logD(TAG, "isOurArtefact: "+
                Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES).getPath());
*/
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