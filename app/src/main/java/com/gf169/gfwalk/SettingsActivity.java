package com.gf169.gfwalk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Iterator;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String TAG="gfSettingsActivity";

    static int walkId;
    static String preferencesFileName;
    static SharedPreferences curSettings;  // То, что редактируется
    static Map<String, ?> oldSettingsMap;
    Map<String, ?> newSettingsMap;
    static PreferenceFragment preferenceFragment;
    static Activity curActivity;

    public static class MyPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            // Выполняется не в New, а в getFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).commit();
            Utils.logD(TAG, "MyPreferenceFragment onCreate");
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(preferencesFileName);
            addPreferencesFromResource(R.xml.preferences);

            findPreference("recording_max_possible_speed").setEnabled(walkId >= 0); // !!!
            SensorManager sensorManager =
                    (SensorManager) curActivity.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
                findPreference("map_min_possible_speed").setEnabled(false);
            }
            findPreference("developer_options").setEnabled(walkId<0);  // Вся группа

            if (!(" "+curSettings.getString("developer_devices","")+" ").contains(
                    " "+Utils.getDeviceId()+" ") &&
                    !getResources().getString(R.string.debug_devices_ids).contains(Utils.getDeviceId())) {
                PreferenceScreen rootPreferences = (PreferenceScreen) findPreference("root_preferences");
                ((PreferenceCategory) rootPreferences.findPreference("developer_options")).removeAll();
                rootPreferences.removePreference(findPreference("developer_options"));
            } else {
                findPreference("developer_devices").setEnabled(false);
            }

            setRetainInstance(true); // !!!
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        curActivity=this;

        if (savedInstanceState==null) {
            savedInstanceState=getIntent().getExtras();
            if (savedInstanceState==null) {  // Надо, может быть !
                finish();
                return;
            }
        }
        int screenSize=(getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK);
        if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
                screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        walkId=savedInstanceState.getInt("walkId");
        preferencesFileName=savedInstanceState.getString("preferencesFileName");
        curSettings=getSharedPreferences(preferencesFileName, MODE_PRIVATE);
        oldSettingsMap=curSettings.getAll();

        String s=walkId<0 ? getResources().getString(R.string.global_settings_header) :
                getResources().getString(R.string.walk_settings_header)+walkId;
        setTitle(s);

        preferenceFragment=new MyPreferenceFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).commit();
    }

    @Override
    public void onBackPressed() {
        Utils.logD(TAG, "onBackPressed");

        if (walkId<0) { // Глобальная настройка
            super.onBackPressed();
            return;
        }
        // Настройка прогулки - вытаскиваем изменившиеся в результате редактирования
        // настройки и сохраняем в walkSettings
        newSettingsMap = curSettings.getAll();
        Iterator iterator = newSettingsMap.keySet().iterator();
        SharedPreferences globalSettings =
                getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE); // default
        SharedPreferences walkSettings = getSharedPreferences("Walk" + walkId, MODE_PRIVATE);
        SharedPreferences.Editor editor = walkSettings.edit();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            Object value = newSettingsMap.get(key);
            if (value.getClass().getName().contains("String") &&
                    (!value.equals(globalSettings.getString(key, null)) ||
                            walkSettings.contains(key))) {
                editor.putString(key, (String) value);
            } else if (value.getClass().getName().contains("Boolean") &&
                    (!value.equals(globalSettings.getBoolean(key, false)) ||
                            walkSettings.contains(key))) {
                editor.putBoolean(key, (Boolean) value);
            }
            // Если изменились параметры Activity, заказываем ее рестарт
            if (!value.equals(oldSettingsMap.get(key))) {
                if (key.startsWith("map")) {
                    Utils.raiseRestartActivityFlag("MapActivity",true);
                } else if (key.startsWith("gallery")) {
                    Utils.raiseRestartActivityFlag("GalleryActivity",true);
                }
            }
        }
        editor.commit();

        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // Вызывается между onPause и onStop
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putInt("walkId", walkId);
        savedInstanceState.putString("preferencesFileName", preferencesFileName);
    }

    @Override
    public void onResume() {
        super.onResume();

        curSettings.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        curSettings.unregisterOnSharedPreferenceChangeListener(this);
    }

    static SharedPreferences getCurrentWalkSettings(Context context, int walkId) {
        // currentSettings = globalSettings + walkSettings (сверху)
        // Оттуда будут браться реальные настройки и их будем редактироать в SettingsActivity
        Utils.logD(TAG, "getCurrentSettings");

        SharedPreferences globalSettings=
                context.getSharedPreferences(context.getPackageName()+"_preferences",MODE_PRIVATE); // default
        PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
        if (walkId<0) {
            return globalSettings;
        }
        Map<String, ?> globalSettingsMap=globalSettings.getAll();
        SharedPreferences walkSettings=context.getSharedPreferences("Walk" + walkId, MODE_PRIVATE);
        Map<String, ?> walkSettingsMap=walkSettings.getAll();
        SharedPreferences currentWalkSettings=context.getSharedPreferences("CurrentWalk", MODE_PRIVATE);
        SharedPreferences.Editor editor=currentWalkSettings.edit();

        for (String key:globalSettingsMap.keySet()) {
            Object value;
            if (walkSettingsMap.containsKey(key)) {
                value=walkSettingsMap.get(key);
            } else {
                value=globalSettingsMap.get(key);
            }
            Utils.logD(TAG, "getCurrentSettings "+key+" "+value);
            if (value.getClass().getName().contains("String")) {
                editor.putString(key,(String) value);
            } else if (value.getClass().getName().contains("Boolean")) {
                editor.putBoolean(key, (Boolean) value);
            }
        }
        editor.commit();

        return currentWalkSettings;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateSummary(key);
//        pref.setSummary(Html.fromHtml("<font color='#145A14'>" +"qq"  + "</font>")); Не ест :(
    }

    static void updateSummary(String key) {
        Preference pref = preferenceFragment.findPreference(key);

        if (pref instanceof EditTextPreference) {
            String[] s=((String) pref.getSummary()).split(
                    curActivity.getResources().getString(R.string.default_word));
            String s3=s[0];
            if (s.length>1) { // В summary есть "По умолчанию" - должно быть всегда !
                String s2 = s[1].split(",")[0].trim();  // Значение по умолчанию. Ф-ции getDefaultValue нет :(
                s3+=curActivity.getResources().getString(R.string.default_word)+" "+s2;
                if (!curSettings.getString(key,"").equals(s2)) {
                    s3+=curActivity.getResources().getString(R.string.set_word) +
                            " "+curSettings.getString(key, "");
                }
            }
            pref.setSummary(s3);
        }
    }
}
