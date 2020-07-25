/**
 * Created by gf on 04.07.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;

public class Utils {
    static final String TAG = "gfUtils";

    static Context appContext;
    static DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
    static final double IMPOSSIBLE_ALTITUDE = -11111;

    static void ini(Context appContext) {
        Utils.appContext = appContext;
    }

    static File copyFile(String src, String dst) {
        Utils.logD(TAG, "copyFile " + src + " " + dst);

        try (FileInputStream fis = new FileInputStream(new File(src));
             FileOutputStream fos = new FileOutputStream(dst)) {
            while (true) {
                int i = fis.read();
                if (i != -1) {
                    fos.write(i);
                } else {
                    break;
                }
            }
            fos.flush();
            Utils.logD(TAG, "copyFile OK");
            return new File(dst);
        } catch (Exception e) {
            e.printStackTrace();
            Utils.logD(TAG, "copyFile ERROR " + src + "->" + dst);
            return null;
        }
    }

    static boolean sleep(int milliseconds, boolean interruptable) {
        if (interruptable && Thread.interrupted()) {  // Надо?
            return true;
        }

        long endTime = SystemClock.elapsedRealtime() + milliseconds;
        while (endTime > SystemClock.elapsedRealtime()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                if (interruptable) {
                    return true; // Разбудили
                }
            }
        }
        return false;  // Проспал сколько было сказано
    }

    /*
        public static LatLng str2LatLng(String str) { // Из LatLng.toString
            if (str=="") {
                return null;
            }
            int i = str.indexOf(",");
            String lat = str.substring(0, i).trim();
            lat = lat.substring(lat.indexOf("(") + 1);
            String lng = str.substring(i + 1).trim();
            lng = lng.substring(0, lng.length() - 1);
            return new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
        }
    */
    public static String loc2Str(Location loc) {
        if (loc == null) {
            return "";
        }
        String s = String.format("%.6f%s%.6f%s%.0f%s%.0f%s%.1f%s%.0f",
                loc.getLatitude(), " ", loc.getLongitude(), " ",
                loc.hasAltitude() ? loc.getAltitude() : IMPOSSIBLE_ALTITUDE, " ",
                loc.hasAccuracy() ? loc.getAccuracy() : -1, " ",
                loc.hasSpeed() ? loc.getSpeed() : -1, " ",
                loc.hasBearing() ? loc.getBearing() : -1);
        return s;
    }

    public static Location str2Loc(String str) {
        if (str == null) {
            return null;
        }
        Location loc = new Location("");

        String[] tokens = str.split(" ");
        loc.setLatitude(Double.parseDouble(tokens[0].replace(",", ".")));
        loc.setLongitude(Double.parseDouble(tokens[1].replace(",", ".")));
        double d = tokens.length > 2 ? Double.parseDouble(tokens[2].replace(",", ".")) : IMPOSSIBLE_ALTITUDE;
        if (d > -1000) {
            loc.setAltitude(d);
        }
        d = tokens.length > 3 ? Double.parseDouble(tokens[3].replace(",", ".")) : -1;
        if (d >= 0) {
            loc.setAccuracy((float) d);
        }
        d = tokens.length > 4 ? Double.parseDouble(tokens[4].replace(",", ".")) : -1;
        if (d >= 0) {
            loc.setSpeed((float) d);
        }
        d = tokens.length > 5 ? Double.parseDouble(tokens[5].replace(",", ".")) : -1;
        if (d >= 0) {
            loc.setBearing((float) d);
        }
        return loc;
    }

    public static LatLng loc2LatLng(Location location) {
        if (location == null) {
            return null;
        }
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    public static float getDistance(LatLng location, LatLng location2) {  // в метрах
        float[] res = new float[1];
        Location.distanceBetween(
                location.latitude, location.longitude,
                location2.latitude, location2.longitude,
                res);
        return ((float) Math.round(1000 * res[0])) / 1000;  // До целых миллиметров
    }

    public static String getAddress(Geocoder geocoder, LatLng location) {
        try {
            List<Address> addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1);
            if (addresses != null) {
                if (addresses.size() > 0) {
//                    return nvl(addresses.get(0).getFeatureName(),  -- getFeatureName без улицы :(
//                            addresses.get(0).getAddressLine(0));
                    return addresses.get(0).getAddressLine(0);
                } else {
                    return "NoAddress 1";  // Ни разу не видел
                }
            } else {
                return "NoAddress 2";  // Ни разу не видел
            }
        } catch (IOException e) {  // Возможно, нет интернета
            return appContext.getResources().getString(R.string.no_address); // Timed out waiting for response from server
        }
    }

    public static String dateStr(long milliSeconds, DateFormat dateFormat) {
        return dateFormat.format(new Date(milliSeconds));
    }

    public static String timeStr(long milliSeconds, TimeZone timeZone) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(timeZone);
        return sdf.format(new Date(milliSeconds));
    }

    public static String timeStr2(long milliSeconds) {
        return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(milliSeconds));
    }

    public static String durationStr(long duration, long durationNetto) { // 2:03
        long hours = duration / 3600000;
        long minutes = (duration - hours * 3600000) / 60000;
        String s = String.format("%d%s%02d", hours, ":", minutes);
        if (durationNetto > 0) {
            hours = durationNetto / 3600000;
            minutes = (durationNetto - hours * 3600000) / 60000;
            String s2 = String.format("%d%s%02d", hours, ":", minutes);
            s = s.equals(s2) ? s : s + "(" + s2 + ")";
        }
        return s;
    }

    public static String durationStr2(long duration) { // 2:03:33
        duration = duration / 1000;
        long hours = duration / 3600;
        long minutes = (duration - hours * 3600) / 60;
        long seconds = duration - hours * 3600 - minutes * 60;
        String s = String.format("%d%s%02d%s%02d", hours, ":", minutes, ":", seconds);
        return s;
    }

    public static String lengthStr(float length, float lengthNetto) { // 11.5km
        String s = String.format("%.3f", length / 1000);
        if (lengthNetto > 0) {
            String s2 = String.format("%.3f", lengthNetto / 1000);
            s = s.equals(s2) ? s : s + "(" + s2 + ")";
        }
        return s + "km";
    }

    public static String speedStr(long duration, float length,
                                  long durationNetto, float lengthNetto) {
        if (duration == 0) {
            return "";
        }
        String s = String.format("%.1f", length / duration * 3600);
        if (durationNetto > 0) {
            String s2 = String.format("%.1f", lengthNetto / durationNetto * 3600);
            s = s.equals(s2) ? s : s + "(" + s2 + ")";
        }
        return s + " km/h";
    }

    public static String altitudeStr(double alt, double altPrev, double altIni) { // 150 m (+5,-10)
        String s=String.format("%.0f", alt) + "m";
        String s2=
                (altPrev == Utils.IMPOSSIBLE_ALTITUDE /*|| altPrev == alt*/ ?
                "(?" : String.format("(%+.0f", alt - altPrev))+
                (altIni == Utils.IMPOSSIBLE_ALTITUDE /*|| altIni == alt*/ ?
                        ",?)" : String.format(",%+.0f)", alt - altIni));
        if (s2.equals("(?,?)")) return s;
        else return s+s2;
    }

    public static String accuracyStr(double accuracy) { // 150 m(+5)
        return "±" + String.format("%.0f", accuracy) + "m";
    }

    public static String locationFullInfStr(Location location, double altPrev, double altIni) {
        return
                String.format("%.6f%s%.6f",  // 6-ой знак - 0.1 м
                        location.getLatitude(), "° ", location.getLongitude()) + "°"
                        + " " + (location.hasAltitude() ?
                        Utils.altitudeStr(location.getAltitude(), altPrev, altIni) : "?m")
                        + " " + (location.hasAccuracy() ?
                        Utils.accuracyStr(location.getAccuracy()) : "±?")
                        + " " + (location.hasSpeed() ?
                        String.format("%.1f", location.getSpeed() * 3.6) : "?") + "km/h"
                        + " " + (location.hasBearing() ?
                        String.format("%.0f", location.getBearing()) : "?") + "°"
                ;
    }

    public static Long dateTime2Long(String str) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
        try {
            Date date = dateTimeFormat.parse(str);
            return date.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static Long dateTime2Long2(String str) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy MM dd");
        try {
            Date date = dateTimeFormat.parse(str);
            return date.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static String time2Str(Long t) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss.mmm");
        return dateTimeFormat.format(new Date(t));
    }

    public static Uri getImageContentUri(String filePath) {
        Cursor cursor = appContext.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.DATA + "=? ",
                new String[]{filePath}, null);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
        } else {
            return null;
        }
    }

    public static Uri getVideoContentUri(String filePath) {
        Cursor cursor = appContext.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Video.Media._ID},
                MediaStore.Video.Media.DATA + "=? ",
                new String[]{filePath}, null);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id);
        } else {
            return null;
        }
    }

    static String createDirIfNotExists(String directory, String path) {
        // File file = new File(Environment.getExternalStorageDirectory(), path);
        File file = new File(directory, path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Utils.logD(TAG, "Could not create folder " + file.getName());
                return null;
            }
        }
        return file.getPath();
    }

    static String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf(".");
        if (i>=0) return fileName.substring(i + 1);
        return "";
    }

    static String padr(String s, int n, char c) {
        StringBuilder r = new StringBuilder(s);
        for (int i = r.length(); i < n; i++) {
            r = r.append(c);
        }
        return r.toString();
    }

    static int dpyToPx(int dpy) {
        float ydpi = metrics.ydpi;  // The exact physical pixels per inch of the screen in the Y dimension
        if (ydpi < 10) ydpi *= 160;  // Дырка в virual device nexus 5X Android 10
        return (int) ydpi * dpy / 160;
    }

    static <T> T nvl(T x, T y) {
        return x == null ? y : x;
    }

    static boolean set(StringBuilder x, String y) { // Для присвоения в if'ах
        x.replace(0,x.length(),y);
        return true;
    }

    static int byteSizeOf(Bitmap data) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            return data.getRowBytes() * data.getHeight();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return data.getByteCount();
        } else {
            return data.getAllocationByteCount();
        }
    }

    static void restartActivity(Activity activity) {
        Utils.logD(TAG, "restartActivity " + activity.getLocalClassName());
        activity.recreate();
    }

    private static SharedPreferences restartFlags;

    static void raiseRestartActivityFlag(String activityName, boolean on) {   // "MainActivity"
        if (restartFlags == null) {
            restartFlags = appContext.getSharedPreferences("RestartFlags", Activity.MODE_PRIVATE);
        }

        restartFlags.edit().putBoolean(activityName, on).commit();
    }

    static boolean restartActivityIfFlagIsRaised(Activity activity) {
        if (restartFlags == null) {
            restartFlags = appContext.getSharedPreferences("RestartFlags", Activity.MODE_PRIVATE);
        }
        String activityName = getNthPiece(activity.getLocalClassName(), -1, "\\.");
        if (restartFlags.getBoolean(activityName, false)) {
            restartFlags.edit().putBoolean(activityName, false).commit();
            restartActivity(activity);
            return true;
        }
        return false;
    }

    public static boolean isBetween(float a, float b, float c) {
        return b <= a && c >= a;
    }

    public static boolean isBetween(int a, int b, int c) {
        return b <= a && c >= a;
    }

    public static class IntegerX {   // mutable
        int value;

        public IntegerX(int value) {
            this.value=value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class FloatX {
        float value;

        public FloatX(float value) {
            this.value=value;
        }

        public float getValue() {
            return value;
        }

        public void setValue(float value) {
            this.value = value;
        }
    }

    static OutputStreamWriter logWriter;
    static void startDevelopersLog() {
        String dir = Utils.createDirIfNotExists(appContext.getExternalFilesDir(null).getAbsolutePath(), "Logs");
        String logFilePath = dir + "/" +
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
            /* "_" + android.os.Process.myPid() +*/ ".txt";
        Utils.logD(TAG, "Starting developer's log - " + logFilePath);
        try {
            logWriter=new OutputStreamWriter(
                    new FileOutputStream(new File(logFilePath)));
        } catch (java.io.IOException e) {
            String s = "Could not create developer's log " + logFilePath;
            Log.e(TAG, s);
            Toast.makeText(appContext, s,
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
            logWriter = null;
        }
    }

    static void stopDevelopersLog() {
        logD(TAG, "Stopping developer's log");

        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                String s = "Could not close developer's log";
                Log.e(TAG, s);
                Toast.makeText(appContext, s, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
            logWriter = null;
        }
    }

    public static void logD(String tag, String msg) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.d(tag, msg);

            if (logWriter != null) {
                String s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) +
                        " " + tag + " " + msg;
                try {
                    logWriter.write(s + "\n");
                    logWriter.flush();
                } catch (Exception e) {
                    s = "Could not write a line to developer's log";
                    Log.e(TAG, s);
                    Toast.makeText(appContext, s,
                            Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        }
    }

    static Thread runX(final Runnable runnable, final Object object, final int maxSeconds,
                       Activity activity) {
        // Если object свободен, захватывает его и запускает runnanble в main thread, если нет - дожидается в течение maxSeconds
        // Object не должен быть autoboxed !!!
        Thread thread = new Thread(
                () -> {
                    synchronized (object) {
                        activity.runOnUiThread(runnable);
                        try {
                            Thread.sleep(
                                    maxSeconds >= 0 ? maxSeconds * 1000 : 60 * 60 * 24 * 1000);
                        } catch (Exception e) {
                        }
                    }
                }, "gfRunX");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static private final List<String> neededPermissions = new LinkedList<>();
    static private int getNotGrantedPermissions() {  // Не даденные опасные permissions в neededPermissions
        if (Build.VERSION.SDK_INT < 23) return 0;
        try {
            // Scan manifest for dangerous permissions not already granted
            PackageManager packageManager = appContext.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(appContext.getPackageName(),
                    PackageManager.GET_PERMISSIONS);
            neededPermissions.clear();
            for (String permission : packageInfo.requestedPermissions) {
                try {
                    PermissionInfo permissionInfo = packageManager.getPermissionInfo(permission, PackageManager.GET_META_DATA);
                    if ((permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) !=
                            PermissionInfo.PROTECTION_DANGEROUS &&
                         (permissionInfo.protectionLevel & PermissionInfo.PROTECTION_FLAG_PRE23) != 0
                    ) continue;
                    if (appContext.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
                        continue;
                    neededPermissions.add(permission);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, String.format("Unable to query for permission: %s", e.getMessage()));
                    continue; // Такого еще нет в этом Android'e
                }
            }
        } catch (Exception error) {
            // Не может быть
        }
        return neededPermissions.size();
    }

    static public class RequestFragment extends Fragment {
        Activity activity;

        @Override
        public void onStart() {
            super.onStart();
            if (Build.VERSION.SDK_INT < 23) return;
            requestPermissions(neededPermissions.toArray(new String[0]), 12345);
        }

        @Override
        public void onRequestPermissionsResult(
                int requestCode, String[] permissions, int[] grantResults) {
            if (requestCode != 12345) return;
            FragmentTransaction fragmentTransaction =
                    activity.getFragmentManager().beginTransaction();
            fragmentTransaction.remove(this);
            fragmentTransaction.commit();

            if (getNotGrantedPermissions() > 0) { // Не дал
                Toast.makeText(appContext, getResources().getString(R.string.goodbye),
                        Toast.LENGTH_LONG).show();
                activity.finish();
            }
        }
    }

    static void grantMeAllDangerousPermissions(Activity activity) {
        if (getNotGrantedPermissions() > 0) {
            FragmentTransaction fragmentTransaction =
                    activity.getFragmentManager().beginTransaction();
            RequestFragment requestFragment = new RequestFragment();
            requestFragment.activity = activity;
            fragmentTransaction.add(0, requestFragment);
            fragmentTransaction.commit();
        }
    }

    static void setDefaultUncaughtExceptionHandler(final Runnable runnable) {  // static - for all threads!
        if (runnable == null) {  // TODO: Культурно восстановить старый
            Thread.setDefaultUncaughtExceptionHandler(null);
        } else {
            final Thread.UncaughtExceptionHandler oldHandler =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    (paramThread, paramThrowable) -> {
                        //Do your own error handling here
                        runnable.run();

                        if (oldHandler != null)
                            oldHandler.uncaughtException(
                                    paramThread,
                                    paramThrowable
                            ); //Delegates to Android's error handling - обычный диалог
                        else
                            System.exit(2); //Prevents the service/app from freezing
                    });
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

    public interface Consumer<T> {  // Родной требует SDK>=24
        void accept(T t);
    }

    private static Boolean isEmulator;
    static boolean isEmulator() { // Работает с Android Studio virtual devices, но не с BlueStacks:(
/*      TelephonyManager telephonyManager=(TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String IMEI=telephonyManager.getDeviceId();
        return = IMEI!=null && !IMEI.isEmpty() && IMEI.replace("0", "").isEmpty(); // У эмулятора куча нулей
 Требует     <uses-permission android:name="android.permission.READ_PHONE_STATE" /> */
        if (isEmulator == null) {
            isEmulator = Build.MODEL.startsWith("Android SDK built for x86") ||
                Build.MODEL.startsWith("sdk_gphone_x86") ||  // API 30
                Build.MODEL.equals("AOSP on IA Emulator") ||
                "marlin".equals(Build.DEVICE);  // BlueStacks
        }
        return isEmulator.booleanValue();
    }

    static String getDeviceId() {
        return Settings.Secure.getString(appContext.getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    static void FALogEvent(String eventName, String paramName, String paramValue) {
        logD("FALogEvent",eventName+" "+ paramName + "=" + paramValue);
// Сам пишет:  D/FA: Logging event ...  Не пишет!
        Bundle bundle = new Bundle();
        bundle.putString(paramName, paramValue);
        FirebaseAnalytics.getInstance(appContext).logEvent(eventName, bundle);
    }

    static String getNthPiece(String s, int n, String d) {
        //  [ ] \ / ^ $ . | ? * + ( ) { } экранировать \\
        if (s == null) return null;
        String[] a = s.split(d);
        try {
            if (n>=0) return a[n];
            return a[a.length + n];
        } catch (Exception e) {
            return null;
        }
    }

    static <T> int addToList (List<T> v, T e) {
        for (int i = 0; i < v.size() ; i++) {
            if (v.get(i) == null) {
                v.set(i, e);
                return i;
            }
        }
        v.add(e);
        return v.size() - 1;
    }
}