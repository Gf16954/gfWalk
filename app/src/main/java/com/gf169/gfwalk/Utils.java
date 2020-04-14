/**
 * Created by gf on 04.07.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
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
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public class Utils {
    static final String TAG = "gfUtils";

    static Activity curActivity;
    static DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
    static final double IMPOSSIBLE_ALTITUDE = -11111;

    static void copyFile(String src, String dst) {
        Utils.logD(TAG, "copyFile " + src + " " + dst);

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(new File(src));
            fos = new FileOutputStream(dst);
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
        } catch (Exception e) {
            e.printStackTrace();
            Utils.logD(TAG, "copyFile ERROR");
        } finally {
            try {
                fos.close();
                fis.close();
            } catch (Exception e) {
            }
            ;
        }
    }

    static boolean sleep(int milliseconds, boolean interruptable) {
        long endTime = SystemClock.elapsedRealtime() + milliseconds;
        while (endTime > SystemClock.elapsedRealtime()) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                if (interruptable) {
                    return true; // interrupted
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
        String s = String.format("%.6f%s%.6f%s%.0f%s%.0f%s%.6f%s%.0f",
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

    public static float getDistance(LatLng location, LatLng location2) {
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
            return curActivity.getResources().getString(R.string.no_address); // Timed out waiting for response from server
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

    public static String lengthStr(float length, float lengthNetto) { // 11.5km
        String s = String.format("%.3f", length / 1000);
        if (lengthNetto > 0) {
            String s2 = String.format("%.3f", lengthNetto / 1000);
            s = s.equals(s2) ? s : s + "(" + s2 + ")";
        }
        return s + " km";
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

    public static String altitudeStr(double alt, double altIni) { // 150 m (+5)
        return String.format("%.0f", alt) + " m" +
                (altIni == Utils.IMPOSSIBLE_ALTITUDE || altIni == alt ?
                        "" : String.format(" (%+.0f)", alt - altIni));
    }

    public static String accuracyStr(double accuracy) { // 150 m(+5)
        return "±" + String.format("%.0f", accuracy) + " m";
    }

    public static String fullInfStr(Location location, double altIni) {
        return
                String.format("%.6f%s%.6f",  // 6-ой знак - 0.1 м
                        location.getLatitude(), "° ", location.getLongitude()) + "°"
                        + (location.hasAltitude() ?
                        " " + Utils.altitudeStr(location.getAltitude(), altIni) : "")
                        + (location.hasAccuracy() ?
                        " " + Utils.accuracyStr(location.getAccuracy()) : "")
                        + (location.hasSpeed() ?
                        " " + String.format("%.1f", location.getSpeed() * 3.6) + " km/h" : "")
                        + (location.hasBearing() ?
                        " " + String.format("%.0f", location.getBearing()) + "°" : "")
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
        Cursor cursor = curActivity.getContentResolver().query(
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
        Cursor cursor = curActivity.getContentResolver().query(
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

    static String createDirIfNotExists(String path) {
        File file = new File(Environment.getExternalStorageDirectory(), path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Utils.logD(TAG, "Could not create folder " + file.getName());
                return null;
            }
        }
        return file.getPath();
    }

    static String padr(String s, int n, char c) {
        StringBuilder r = new StringBuilder(s);
        for (int i = r.length(); i < n; i++) {
            r = r.append(c);
        }
        return r.toString();
    }

    static int dpyToPx(int dpy) {
        return (int) metrics.ydpi * dpy / 160;
    }

    static <T extends Object> T nvl(T x, T y) {
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

    static SharedPreferences restartFlags;

    static void raiseRestartActivityFlag(String activityName, boolean on) {
        if (restartFlags == null) {
            restartFlags = curActivity.getSharedPreferences("RestartFlags", Activity.MODE_PRIVATE);
        }
        restartFlags.edit().putBoolean(activityName, on).commit();
    }

    static boolean restartActivityIfFlagIsRaised(Activity activity) {
        if (restartFlags == null) {
            restartFlags = curActivity.getSharedPreferences("RestartFlags", Activity.MODE_PRIVATE);
        }
        if (restartFlags.getBoolean(activity.getLocalClassName(), false)) {
            restartFlags.edit().putBoolean(activity.getLocalClassName(), false).commit();
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

    public static void logD(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg);
        }
    }

    static Thread runX(final Runnable runnable, final Object object, final int maxSeconds) {
        // Если object свободен, захватывает его и запускает runnanble, если нет - дожидается в течение maxSeconds
        // Object не должен быть autoboxed !!!
        Thread thread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        synchronized (object) {
                            curActivity.runOnUiThread(runnable);
                            try {
                                Thread.sleep(
                                        maxSeconds >= 0 ? maxSeconds * 1000 : 60 * 60 * 24 * 1000);
                            } catch (Exception e) {
                                return;
                            }
                        }
                    }
                }, "gfRunX");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static final List<String> neededPermissions = new LinkedList<String>();

    static int getNotGrantedPermissions() {  // Не даденные опасные permissions в neededPermissions
        if (Build.VERSION.SDK_INT < 23) return 0;
        try {
            // Scan manifest for dangerous permissions not already granted
            PackageManager packageManager = curActivity.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(curActivity.getPackageName(),
                    PackageManager.GET_PERMISSIONS);
            neededPermissions.clear();
            for (String permission : packageInfo.requestedPermissions) {
                PermissionInfo permissionInfo = packageManager.getPermissionInfo(permission, PackageManager.GET_META_DATA);
//                if (permissionInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS)
                if ((permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) !=
                        PermissionInfo.PROTECTION_DANGEROUS &&
                     (permissionInfo.protectionLevel & PermissionInfo.PROTECTION_FLAG_PRE23) != 0
                ) continue;
                if (curActivity.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
                    continue;
                neededPermissions.add(permission);
            }
        } catch (Exception error) {
            Log.e(TAG, String.format("Unable to query for permission: %s", error.getMessage()));
            return -1;
        }
        return neededPermissions.size();
    }

    static public class RequestFragment extends Fragment {
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
                    curActivity.getFragmentManager().beginTransaction();
            fragmentTransaction.remove(this);
            fragmentTransaction.commit();

            if (getNotGrantedPermissions() > 0) { // Не дал
                Toast.makeText(curActivity, getResources().getString(R.string.goodbye),
                        Toast.LENGTH_LONG).show();
                curActivity.finish();
            }
            return;
        }
    }

    ;

    static void grantMeAllDangerousPermissions() {
        if (getNotGrantedPermissions() > 0) {
            FragmentTransaction fragmentTransaction =
                    curActivity.getFragmentManager().beginTransaction();
            RequestFragment requestFragment = new RequestFragment();
            fragmentTransaction.add(0, requestFragment);
            fragmentTransaction.commit();
        }
    }

    static void setUncaughtExceptionHandler(final Runnable runnable) {
        if (runnable == null) {  // TODO: Культурно восстановить старый
            Thread.setDefaultUncaughtExceptionHandler(null);
        } else {
            final Thread.UncaughtExceptionHandler oldHandler =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(
                                Thread paramThread,
                                Throwable paramThrowable
                        ) {
                            //Do your own error handling here
                            runnable.run();

                            if (oldHandler != null)
                                oldHandler.uncaughtException(
                                        paramThread,
                                        paramThrowable
                                ); //Delegates to Android's error handling - обычный диалог
                            else
                                System.exit(2); //Prevents the service/app from freezing
                        }
                    });
        }
    }

}