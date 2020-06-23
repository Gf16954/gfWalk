/**
 * Created by gf on 18.04.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

public class Walk {
    static final String TAG = "gfWalk";

    static final int MO_DEFAULT = 0;  // Стрелка у каждой точки

    static final int AFKIND_PHOTO = 1;
    static final int AFKIND_SPEECH= 2;
    static final int AFKIND_TEXT = 3;
    static final int AFKIND_VIDEO = 4;
    static final int AFKIND_MIN = 1;
    static final int AFKIND_MAX = 4;

    static final int MO_END_ACTIVE = 5;
    static final int MO_END_PASSIVE = 6;
    static final int MO_START = 7;
    static final int MO_RESUME = 8;
    static final int MO_END_INTERMEDIATE = 15; // Конец непоследнего куска
    static final int MO_DEFAULT_PASSIVE = 16; // Стрелка перед продолжением
    static final int MO_DEFAULT_WITH_AFS = 17; // Стрела точки с артефактами

    DateFormat dateFormat=DateFormat.getDateInstance(); // Только дата, default style
    SimpleDateFormat timeFormat=new SimpleDateFormat();
    TimeZone timeZone;

    MapActivity mapActivity;
    SharedPreferences walkSettings;

    int walkId;
    long startTime;  // Время начала прогулки
    double initialAltitude=Utils.IMPOSSIBLE_ALTITUDE;  // Высота первой точки

    Vector<Marker> lastMarkers=new Vector<>(3); // Последние нарисованные маркеры каждого вида
    static final int MARKER_RESUME=0;
    static final int MARKER_DEFAULT=1;
    static final int MARKER_END=2;

    volatile boolean mapIsLoaded=false;

    Geocoder geocoder;
    GoogleMap map; // Будет предано из MapActivity - здесь будет рисование

    class Point {
        long pointId; // Id записи в базе
        boolean flagResume; // Начало продолжения или всей прогулки
        Long time; // Время прибытия в точку по абсолютному времени (мс), см. WalkRecorder.Clock
        Long timeEnd; // Время убытия
        Location location;
        String debugInfo;
        String address;
        int activityType; // На чем сюда приехали (DetectedActivity.IN_VEHICLE, ON_FOOT, и т.п.)
        float batteryCharge; // %

                // Дальше все рабочее, в базу не идет
        Point pointBefore; // Предыдущая в массиве points
        float length; // в метрах
        float lengthNetto; // с учетом приостановок записи
        long duration; // в миллисекундах
        long durationNetto;

        String totalStr;   // Продолжительность, длина и пр. - в snippet маркера
        String markerTitle;
        Vector<Marker> markers=new Vector<>(AFKIND_MAX-AFKIND_MIN+1); // По видам артефаков

        void load(Cursor cursor, Point pointBefore) {
            pointId=cursor.getLong(cursor.getColumnIndex(DB.KEY_POINTID));
            flagResume=cursor.getInt(cursor.getColumnIndex(DB.KEY_POINTFLAGRESUME))>0;
            time=cursor.getLong(cursor.getColumnIndex(DB.KEY_POINTTIME)); // Прибытие в точку
            timeEnd=cursor.getLong(cursor.getColumnIndex(DB.KEY_POINTTIMEEND));  // Убытие
            location=Utils.str2Loc(cursor.getString(cursor.getColumnIndex(DB.KEY_POINTLOCATION)));
            debugInfo=cursor.getString(cursor.getColumnIndex(DB.KEY_POINTDEBUGINFO));
            address=cursor.getString(cursor.getColumnIndex(DB.KEY_POINTADDRESS));
            String s=cursor.getString(cursor.getColumnIndex(DB.KEY_POINTACTIVITYTYPE));
            activityType=s==null ? -1 : Integer.parseInt(s);
            s=cursor.getString(cursor.getColumnIndex(DB.KEY_POINTBATTERYCHARGE));
            batteryCharge=s==null ? 0 : Float.parseFloat(s);

            this.pointBefore=pointBefore;

            if (pointBefore==null) { // Самая первая точка
                duration = time-startTime; // !!!
                durationNetto = 0;
                length=lengthNetto=0;
            } else {
                duration=time-startTime;  // До прибытия в точку
                length=pointBefore.length+Utils.getDistance(
                        Utils.loc2LatLng(location), Utils.loc2LatLng(pointBefore.location));
                if (flagResume) {
                    durationNetto=pointBefore.durationNetto;
                    lengthNetto=pointBefore.lengthNetto;
                } else {
                    durationNetto=pointBefore.durationNetto+duration-pointBefore.duration;
                    lengthNetto=pointBefore.lengthNetto+length-pointBefore.length;
                }
            }

            if (initialAltitude==Utils.IMPOSSIBLE_ALTITUDE && location.hasAltitude()) {
                initialAltitude = location.getAltitude();
            }
            float batteryDischargeRate = -1;
            if (BuildConfig.BUILD_TYPE.equals("debug") && pointBefore != null)
                        batteryDischargeRate = (pointBefore.batteryCharge - batteryCharge) /
                                (time-pointBefore.time)*1000*3600;  // %/час
            totalStr=formTotalStr(walkSettings,
                    duration, length, durationNetto, lengthNetto,
                    flagResume ? duration : pointBefore.duration +
                            (pointBefore.timeEnd > 0 ? pointBefore.timeEnd : pointBefore.time)
                            - pointBefore.time,  // Конец пребывания
                    flagResume ? length : pointBefore.length,
                    0,0,
                    Utils.locationFullInfStr(location,
                            pointBefore!=null && pointBefore.location.hasAltitude() ?
                                pointBefore.location.getAltitude() : location.getAltitude(),
                            initialAltitude)
                    + " "+WalkRecorder.ARState.getName(activityType)
                    + (batteryDischargeRate >= 0 ? " "+String.format("%.1f",batteryDischargeRate)+"%/h" : "")
                    + (BuildConfig.BUILD_TYPE.equals("debug") ? " "+debugInfo : ""));
            markerTitle=formMarkerTitle();
        }

        String formMarkerTitle() {
            return timeStr(time, timeEnd, flagResume)
                + (walkSettings.getBoolean("map_show_address_in_markers", false) ?
                    " "+address : "");
        }
    }
    ArrayList<Point> points = new ArrayList<>();

    public class AF { // Артефакт (фото, запись речи, текст)
        long afId;
        long pointId;
        int pointNumber; // В прогулке
        Long time;
        int kind;
        String uri;
        String filePath;
        String comment;
        boolean deleted;

        String d="\t";

        AF(Cursor cursor) {
            afId=cursor.getLong(cursor.getColumnIndex(DB.KEY_AFID));
            pointId=cursor.getLong(cursor.getColumnIndex(DB.KEY_AFPOINTID));
            pointNumber=cursor.getInt(cursor.getColumnIndex(DB.KEY_AFPOINTNUMBER));
            time=cursor.getLong(cursor.getColumnIndex(DB.KEY_AFTIME));
            kind=cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND));
            uri=cursor.getString(cursor.getColumnIndex(DB.KEY_AFURI));
            filePath=cursor.getString(cursor.getColumnIndex(DB.KEY_AFFILEPATH));
            comment=cursor.getString(cursor.getColumnIndex(DB.KEY_AFCOMMENT));
            deleted=cursor.getInt(cursor.getColumnIndex(DB.KEY_AFDELETED))==1;
        }

        AF(String parcel) {
            String[] af=parcel.split(d);
            afId=Long.parseLong(af[0]);
            pointId=Long.parseLong(af[1]);
            pointNumber=Integer.parseInt(af[2]);
            time=Long.parseLong(af[3]);
            kind=Integer.parseInt(af[4]);
            uri=af[5];
            filePath=af[6];
            comment=af[7];
            deleted=Boolean.parseBoolean(af[8]);
        }

        @Override
        public String toString() {
            return ""+afId+d+pointId+d+pointNumber+d+time+d+kind+d+uri+d+filePath+d+comment+d+deleted;
        }
    }

    ArrayList<AF> AFs = new ArrayList<>();

    Walk(MapActivity mapActivity) {
        this.mapActivity = mapActivity;
        this.walkId = mapActivity.walkId;
        this.map = mapActivity.map;
        walkSettings=mapActivity.walkSettings;

        for (int i=0; i<lastMarkers.capacity(); i++) {
            lastMarkers.add(null);
        }

        geocoder = new Geocoder(mapActivity,Locale.getDefault());
        timeFormat = new SimpleDateFormat("HH:mm");
        timeZone=getTimeZone(mapActivity);
        timeFormat.setTimeZone(timeZone);
        dateFormat.setTimeZone(timeZone);

        Cursor cursor;
        cursor = DB.db.query(DB.TABLE_WALKS, new String[]{DB.KEY_STARTTIME},
                DB.KEY_ID + "=" + walkId,
                null, null, null, null);
        cursor.moveToFirst();
        startTime=cursor.getLong(cursor.getColumnIndex(DB.KEY_STARTTIME));
        cursor.close();
    }

    Walk(Activity activity, int walkId) {  // Используется в Main'e и Gallery
        this.walkId=walkId;
        walkSettings=SettingsActivity.getCurrentWalkSettings(activity, walkId);
        if (activity.getLocalClassName().endsWith("GalleryActivity")) {
            return;
        }
        timeZone=getTimeZone(activity);
        timeFormat.setTimeZone(timeZone);
        dateFormat.setTimeZone(timeZone);

//        Cursor dbCursor = DB.db.query(DB.TABLE_POINTS, null, null, null, null, null, null);
//        String[] columnNames = dbCursor.getColumnNames();

        Cursor cursor;
        cursor = DB.db.query(DB.TABLE_POINTS, new String[]{
                        DB.KEY_POINTID, DB.KEY_POINTFLAGRESUME, DB.KEY_POINTTIME, DB.KEY_POINTTIMEEND,
                        DB.KEY_POINTLOCATION, DB.KEY_POINTDEBUGINFO, DB.KEY_POINTADDRESS,
                        DB.KEY_POINTACTIVITYTYPE,DB.KEY_POINTBATTERYCHARGE},
                DB.KEY_POINTWALKID + "=" + walkId,
                null, null, null,
                DB.KEY_POINTID);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Point point=new Point();
                point.load(cursor, points.size()>0 ? points.get(points.size()-1) : null);
                points.add(point);
            } while (cursor.moveToNext());
        }
        cursor = DB.db.query(DB.TABLE_AFS, new String[]{
                DB.KEY_AFID, DB.KEY_AFPOINTNUMBER, DB.KEY_AFPOINTID, DB.KEY_AFTIME,
                DB.KEY_AFKIND, DB.KEY_AFURI, DB.KEY_AFFILEPATH, DB.KEY_AFCOMMENT, DB.KEY_AFDELETED},
                DB.KEY_AFWALKID + "=" + walkId,
                null, null, null,
                DB.KEY_AFPOINTID+","+DB.KEY_AFTIME);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                AFs.add(new AF(cursor));
                int i=AFs.size()-1;
                if (!AFs.get(i).deleted && AFs.get(i).filePath!=null &&
                        !(new File(AFs.get(i).filePath).exists())) { // А вдруг удалили ?
                    AFs.get(i).deleted = true;
                    ContentValues values = new ContentValues();
                    values.put(DB.KEY_AFDELETED, true);
                    DB.db.update(DB.TABLE_AFS, values, DB.KEY_AFID + "=" + AFs.get(i).afId, null);
                    MainActivity.pleaseDo = MainActivity.pleaseDo + " refresh selected item";
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    TimeZone getTimeZone(Activity activity) {
        DB.dbInit(activity);
        Cursor cursor = DB.db.query(DB.TABLE_WALKS, new String[]{DB.KEY_TIMEZONE},
                DB.KEY_ID + "=" + walkId,
                null, null, null, null);
        cursor.moveToFirst();
        TimeZone timeZone=TimeZone.getTimeZone(cursor.getString(cursor.getColumnIndex(DB.KEY_TIMEZONE)));
        cursor.close();
        return  timeZone;
    }

    static int create() {
        ContentValues newValues=new ContentValues();
        newValues.put(DB.KEY_STARTTIME, System.currentTimeMillis());
        newValues.put(DB.KEY_TIMEZONE, TimeZone.getDefault().getID());
        newValues.put(DB.KEY_COMMENT, "");
        newValues.put(DB.KEY_ICONAFID, -1);   // Еще не назначена
        int i = (int) DB.db.insert(DB.TABLE_WALKS, null, newValues);
        Utils.logD(TAG, "Walk #"+i+" created");
        return i;
    }

    void loadAndDraw(final boolean isFirstCall) {  // Загружаем и рисуем появившиеся в базе точки
        String threadName="gfLoadAndDraw #" + points.size();
        Utils.logD(TAG, "Thread "+threadName+" is starting...");
        new Thread(() -> {loadAndDraw2(isFirstCall);}, threadName).start();
    }

    synchronized private void loadAndDraw2(boolean isFirstCall) {  // isFirstCall - первый вызов из MapActivity
        Utils.logD(TAG, "loadAndDraw2");

        boolean toGetAddress = true;
        ContentValues values = new ContentValues();
        DB.dbInit(mapActivity);

        int lastPointInd = points.size()-1;  // В массиве points - последняя загруженная
        int pointInd=lastPointInd;  // Сюда будем писать
        String s="";
        if (lastPointInd>=0) {
            s=" AND "+DB.KEY_POINTID+">="+points.get(lastPointInd).pointId; // Начиная с последней !
        } else {
            pointInd=0;
        }
        Cursor cursor = DB.db.query(DB.TABLE_POINTS, null,  // Все поля
                DB.KEY_POINTWALKID + "=" + walkId + s,
                null, null, null,
                DB.KEY_POINTID);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Point point;
                if (pointInd>lastPointInd) {  // Новая точка
                    point=new Point();
                    for (int i=0; i<AFKIND_MAX-AFKIND_MIN+1; i++) {
                        point.markers.add(null);
                    }
                } else {
                    point=points.get(pointInd);
                }
                point.load(cursor, pointInd==0 ? null : points.get(pointInd-1));

                if (point.address.contains(
                        mapActivity.getResources().getString(R.string.no_address))) {  // Пытаемся повторно определить
                    if (isFirstCall & toGetAddress) {
                        point.address = Utils.getAddress(geocoder, Utils.loc2LatLng(point.location));
                        if (point.address.contains(
                                mapActivity.getResources().getString(R.string.no_address))) { // Адрес так и не получили
                            toGetAddress = false; // Больше в этот раз не пытаемся
                        } else {
                            values.clear();
                            values.put(DB.KEY_POINTADDRESS, point.address);
                            DB.db.update(DB.TABLE_POINTS, values,
                                    DB.KEY_POINTID + "=" + point.pointId, null);
                            if (points.size() == 0) { // Адрес первой точки - место начала прогулки
                                values.clear();
                                values.put(DB.KEY_STARTPLACE, point.address);
                                DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID + "=" + walkId, null);
                                MainActivity.pleaseDo=MainActivity.pleaseDo + " refresh selected item";
                            }
                        }
                    }
                }
                if (pointInd==lastPointInd) {
                    points.set(pointInd, point);
                } else {
                    points.add(point);
                }
                pointInd++;
                Utils.logD(TAG, "A point is loaded: #"+(points.size()-1)+" "+
                        point.time+" "+point.timeEnd+" "+point.address+" "+point.debugInfo+" "+point.activityType);
            } while (cursor.moveToNext());
        }
        if (isFirstCall) { // Точки загружены, можно установить Zoom - дожидаемся пока загрузится карта и просим
            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            while (!mapIsLoaded) {
                                if (Utils.sleep(1000,true)) {  //TODO: предохраниться от зависания и убийства UIThread
                                    return;
                                }
                            }
                            Intent intent = new Intent("DoInUIThread");
                            intent.putExtra("action", "setZoomAndFocus");
                            LocalBroadcastManager.getInstance(mapActivity).sendBroadcast(intent);
                        }
                    }, "gfWaitForMapIsLoaded").start();
        }
        for (int k = Math.max(lastPointInd,0); k < points.size(); k++) { // Рисуем загруженные точки, начиная, если надо, с последней уже нарисованной

            drawOnePoint(k, k==lastPointInd, k==points.size()-1);

            if (mapActivity.mode==MapActivity.MODE_RESUME && // Первую НОВУЮ точку нарисовали - оживляем Map
//                    points.get(k).flagResume && points.get(k).time>=WalkRecorder.curSessionStartTime) {
                    points.get(k).flagResume && !isFirstCall) {
                mapActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mapActivity.enterActiveMode();
                    }
                });
            }
        }
        if (isFirstCall) { // Все артефакты загружены
            mapActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mapActivity.drawPointInGalleryMarker(mapActivity.afInGalleryNumber);
                }
            });
        }
        Utils.logD(TAG, "loadAndDraw2 ended");
    }

    private void drawOnePoint(int iPoint, boolean isLastOld, boolean isLastNew) {
        Utils.logD(TAG, "A point is beeing drawn: #"+iPoint);

        int lastAFInd = AFs.size()-1;
        String s = "";
/*
        if (lastAFInd >= 0 &&
                AFs.get(lastAFInd).pointNumber==iPoint) { // Предохранение от сдвига времени
            s = " AND " + DB.KEY_AFTIME + ">" + AFs.get(lastAFInd).time;
        }
*/
        Cursor cursor=DB.db.query(DB.TABLE_AFS, new String[]{  // Загружаем артефакты этой точки в конец массива AFs
                        DB.KEY_AFID, DB.KEY_AFPOINTNUMBER, DB.KEY_AFPOINTID, DB.KEY_AFTIME,
                        DB.KEY_AFKIND, DB.KEY_AFURI, DB.KEY_AFFILEPATH, DB.KEY_AFCOMMENT,
                        DB.KEY_AFDELETED},
                DB.KEY_AFPOINTID + "=" + points.get(iPoint).pointId +
                /* " AND +DB.KEY_AFDELETED+ "=0" + */ s,
                null, null, null,
                DB.KEY_AFTIME); // Order !!! Увы, время не абсолютное, а из файла, при сдвиге времени могут сразу не показаться, но потом все равно покажутся
        boolean isWithAFs=false;
        if (cursor!=null && cursor.moveToFirst()) {
            isWithAFs=true;
            do {
                if ((lastAFInd < 0 ||
                        AFs.get(lastAFInd).pointNumber!=iPoint) || // Предохранение от сдвига времени
                        cursor.getLong(cursor.getColumnIndex(DB.KEY_AFTIME))>AFs.get(lastAFInd).time) {
                    AF af = new AF(cursor);
                    AFs.add(af);
                    Utils.logD(TAG, "An artifact is loaded: #" + (AFs.size() - 1) +
                            " " + af.time + " " + af.kind + " " + af.uri + " " + af.filePath +
                            (af.deleted ? " deleted" : ""));
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        boolean dontRedrawAFMarkers=false;
        if (AFs.size()==lastAFInd+1) { // Ничего не добавилось - перерисовывать картинки не нужно
            if (isLastOld && !isLastNew) { // Время пребывания не изменилось - вообще ничего не надо
                return;
            } else {
                dontRedrawAFMarkers=true;
            }
        }
        Point prevPoint=iPoint>0 ? points.get(iPoint-1) : null; // Предыдущая в маршруте
        drawOnePoint2(points.get(iPoint), prevPoint, isLastNew, isLastOld,
                dontRedrawAFMarkers,isWithAFs);
    }

    private void drawOnePoint2(final Point point, final Point prevPoint,
                       final boolean isLastNew, final boolean isLastOld,
                       boolean dontRedrawAFMarkers, boolean isWithAFs) {
        // Маркер ПРЕДЫДУЩЕЙ ПО МАРШРУТУ точки - если последнюю рисуем впервые (не перерисовываем)
        if (prevPoint!=null && !isLastOld) {
            if (point.flagResume) {  // Начало продолжения - рисуем маркер конца в предыдущей точке
                MyMarker.drawMarker(map,  // Конечный маркер у точки прерывания
                        MyMarker.workMarkers, -1,
                        MO_END_INTERMEDIATE, // Маркер промежуточного конца
                        prevPoint.location,
                        prevPoint.markerTitle,
                        prevPoint.totalStr,
                        null, null, null, -1, 0, true, false);
            }
        }
        // Маркер последней точки
        if (prevPoint!=null) {  // default'ный маркер (стрелка) - у всех кроме первой
            Float bearing=360+prevPoint.location.bearingTo(point.location);
            MyMarker.drawMarker(map,
                    lastMarkers, MARKER_DEFAULT,
                    isWithAFs ? MO_DEFAULT_WITH_AFS :
                            point.flagResume ? MO_DEFAULT_PASSIVE : MO_DEFAULT,
                    point.location,
                    point.markerTitle,
                    point.totalStr,
                    null, null, null, bearing, 0,true, isLastOld);
        }
        if (point.flagResume) { // Маркер начала или продолжения
            MyMarker.drawMarker(map,
                    lastMarkers, MARKER_RESUME,
                    prevPoint==null ? MO_START : MO_RESUME,
                    point.location,
                    point.markerTitle,
                    point.totalStr,
                    null, null, null, -1, 0,true, isLastOld);
        }
        if (isLastNew) {   // Конечный маркер у новой последней точки
            MyMarker.drawMarker(map,
                    lastMarkers, MARKER_END,
                    mapActivity.mode==MapActivity.MODE_ACTIVE ? MO_END_ACTIVE : MO_END_PASSIVE,
                    point.location,
                    point.markerTitle,
                    point.totalStr,
                    null, null, null, -1, 0, true, true);
        }
        // Отрезок
        if (prevPoint!=null && !isLastOld) {
            mapActivity.runOnUiThread(new Runnable() {  // Отрезок
                public void run() {
                    map.addPolyline(new PolylineOptions()
                            .add(Utils.loc2LatLng(point.location), Utils.loc2LatLng(prevPoint.location))
                            .width(Utils.dpyToPx(mapActivity.getResources().getInteger(R.integer.line_width)))
                            .endCap(new RoundCap())
                            .pattern(getLinePattern(point))
                            .color(getLineColor(point))
                            .zIndex(-100));
                }
            });
        }
        // Теперь маркеры артефактов - с картинками
        if (mapActivity.showAFs && !dontRedrawAFMarkers) {
            drawAFMarkers(point);
        }
    }

    private int getLineColor(Point point) {
        if (point.flagResume) return mapActivity.getResources().getColor(R.color.line_pause);

        switch (point.activityType) {
            case DetectedActivity.IN_VEHICLE:
                return mapActivity.getResources().getColor(R.color.line_inVehicle);
            case DetectedActivity.ON_BICYCLE:
                return mapActivity.getResources().getColor(R.color.line_onBicycle);
            case DetectedActivity.RUNNING:
                return mapActivity.getResources().getColor(R.color.line_running);
            case DetectedActivity.WALKING:
                return mapActivity.getResources().getColor(R.color.line_walking);
        }

        return mapActivity.getResources().getColor(R.color.line_unknown);
    }

    private List<PatternItem> getLinePattern(Point point) {
//        PatternItem[] patterns={new Dot()};
        int w=Utils.dpyToPx(mapActivity.getResources().getInteger(R.integer.line_width));
        PatternItem[] patterns={new Dash(w*2), new Gap(w)};
        return point.flagResume ? Arrays.asList(patterns) : null;
    }

    private void drawAFMarkers(Point point) {
        for (int markerKind=AFKIND_MIN; markerKind<=AFKIND_MAX; markerKind++) {
            drawAFMarkers2(point, markerKind, false);
        }
    }

    void drawAFMarkers2(final Point point, int markerKind, boolean clearIfEmpty) {
        final int markerIndex=markerKind-AFKIND_MIN;

        int numberOfAFs=0;
        String uri=null, filePath=null;
        for (int i=AFs.size()-1; i>=0; i--) {  // Берем первый артефакт
            if (AFs.get(i).pointId>point.pointId) {
                continue;
            } else if (AFs.get(i).pointId<point.pointId) {
                break;
            } else if (AFs.get(i).deleted) {
                continue;
            } else if (AFs.get(i).filePath!=null &&
                        !(new File(AFs.get(i).filePath).exists())) { // А вдруг удалили ?
                AFs.get(i).deleted=true;
                ContentValues values=new ContentValues();
                values.put(DB.KEY_AFDELETED, true);
                DB.db.update(DB.TABLE_AFS, values, DB.KEY_AFID+"="+AFs.get(i).afId, null);
                MainActivity.pleaseDo=MainActivity.pleaseDo + " refresh selected item";
                continue;
            }
            if (AFs.get(i).kind==markerKind) {
                numberOfAFs++;
                uri=AFs.get(i).uri;
                filePath=AFs.get(i).filePath;
            }
        }
        if (numberOfAFs>0) {
            MyMarker.drawMarker(map,
                    point.markers,
                    markerIndex,
                    markerKind,
                    point.location,
                    point.markerTitle,
                    point.totalStr,
                    uri, filePath,
                    numberOfAFs == 1 ? null : "" + numberOfAFs,
                    -1, 0, true, true);
        } else if (clearIfEmpty && point.markers.get(markerIndex)!=null) {
            MyMarker.killMarker(point.markers,markerIndex);
        }
    }

    static String formTotalStr(SharedPreferences settings,
                               long duration, float length,
                               long durationNetto, float lengthNetto,
                               long durationPointBefore, float lengthPointBefore,
                               long durationPointBeforeNetto, float lengthPointBeforeNetto,
                               String tail) {
        return formTotalStr(
                settings.getBoolean("map_show_details_in_markers", false),
                settings.getBoolean("map_show_netto_values_in_markers", false),
                duration, length, durationNetto, lengthNetto,
                durationPointBefore, lengthPointBefore, durationPointBeforeNetto, lengthPointBeforeNetto,
                tail);
    }

    private static String formTotalStr(boolean showDetailsInMarkers, boolean showNettoVauesInMarkers,
                               long duration, float length,
                               long durationNetto, float lengthNetto,
                               long durationPointBefore, float lengthPointBefore,
                               long durationPointBeforeNetto, float lengthPointBeforeNetto,
                               String tail) {
        Utils.logD(TAG, "formTotalStr: " + duration + " " + length + " " + durationPointBefore + " " + lengthPointBeforeNetto);

        String s=Utils.durationStr(duration, showNettoVauesInMarkers ? durationNetto : 0)
                +" "+Utils.lengthStr(length, showNettoVauesInMarkers ? lengthNetto : 0);
        if (duration!=durationPointBefore) {
            s +=" "+Utils.speedStr(duration-durationPointBefore,length-lengthPointBefore,
                    showNettoVauesInMarkers ? durationNetto-durationPointBeforeNetto : 0,
                    showNettoVauesInMarkers ? lengthNetto-lengthPointBeforeNetto : 0);
        }
        if (showDetailsInMarkers & tail!=null) {
            s=s+" \n"+tail;
        }
        return s;
    }

    private String timeStr(long milliSeconds, long milliSeconds2, boolean withFirstDate) {
        String s=timeFormat.format(new Date(milliSeconds));
        if (milliSeconds2>0) {
            String s2=timeFormat.format(new Date(milliSeconds2));
            if (!s2.equals(s)) {
                String s3=dateFormat.format(new Date(milliSeconds2));
                if (!s3.equals(dateFormat.format(new Date(milliSeconds)))) { // Сменился день
                    s+=" - "+s3+" "+s2;  // Перед вторым временем - дата
                } else {
                    s+=" - "+s2;
                }
            }
        }
        if (withFirstDate) {
            String s3=dateFormat.format(new Date(milliSeconds));
            s=s3+" "+s;
        }
        return s;
    }
}
