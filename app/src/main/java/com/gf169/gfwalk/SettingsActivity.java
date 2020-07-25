package com.gf169.gfwalk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

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
    static PreferenceFragmentCompat preferenceFragment;
    static SettingsActivity curActivity;
    SharedPreferences defaultSettings;

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            // Выполняется не в New, а в getFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).commit();
            Utils.logD(TAG, "MyPreferenceFragment onCreate");
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(preferencesFileName);
            addPreferencesFromResource(R.xml.preferences);

            updateAllSummeries();

            findPreference("recording_max_possible_speed").setEnabled(walkId >= 0); // !!!
            SensorManager sensorManager =
                    (SensorManager) curActivity.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
                findPreference("map_min_possible_speed").setEnabled(false);
            }
            findPreference("misc_skin_color").setEnabled(walkId<0);
            findPreference("developer_options").setEnabled(walkId<0);  // Вся группа

            if (!Utils.isEmulator() &&
                !(" "+curSettings.getString("developer_devices","")+" ").contains(
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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        }

        void updateAllSummeries() {
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {  // Категории
                PreferenceGroup preferenceGroup = (PreferenceGroup) getPreferenceScreen().getPreference(i);
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                    curActivity.updateSummary(preferenceGroup.getPreference(j).getKey());
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate");

        SharedPreferences globalSettings = SettingsActivity.getCurrentWalkSettings(this, -1);
        setTheme(MainActivity.getThemeX(globalSettings));
        super.onCreate(savedInstanceState);

        curActivity=this;

        if (savedInstanceState==null) {
            savedInstanceState=getIntent().getExtras();
            if (savedInstanceState==null) {  // Надо, может быть !
                finish();
                return;
            }
        }

        walkId=savedInstanceState.getInt("walkId");
        preferencesFileName=savedInstanceState.getString("preferencesFileName");
        curSettings=getSharedPreferences(preferencesFileName, MODE_PRIVATE);
        oldSettingsMap=curSettings.getAll();

        String s=walkId<0 ? getResources().getString(R.string.global_settings_header) :
                getResources().getString(R.string.walk_settings_header)+walkId;
        setTitle(s);

        preferenceFragment=new MyPreferenceFragment();
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).commit();
    }

    @Override
    public void onBackPressed() {
        Utils.logD(TAG, "onBackPressed");
        super.onBackPressed();

        newSettingsMap = curSettings.getAll();
        String key;

        if (walkId<0) { // Глобальная настройка, вызван из MainActivity
            key = "misc_skin_color";
            if (!newSettingsMap.get(key).equals(oldSettingsMap.get(key))) {
                Utils.raiseRestartActivityFlag("MainActivity", true);
            }
            return;
        }
        // Настройка прогулки - вытаскиваем изменившиеся в результате редактирования
        // настройки и сохраняем в walkSettings
        Iterator iterator = newSettingsMap.keySet().iterator();
//        SharedPreferences globalSettings =
//                getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE); // default
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences walkSettings = getSharedPreferences("Walk" + walkId, MODE_PRIVATE);
        SharedPreferences.Editor editor = walkSettings.edit();
        while (iterator.hasNext()) {
            key = (String) iterator.next();
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

//        SharedPreferences globalSettings=
//                context.getSharedPreferences(context.getPackageName()+"_preferences",MODE_PRIVATE); // default
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);  // Незаполненные!
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
    }

    void updateSummary(String key) {
        Preference pref = preferenceFragment.findPreference(key);

        if (defaultSettings == null) {
            String filename = "fngrtjhkgtr"; // Файл не создается
            defaultSettings = getSharedPreferences(filename, MODE_PRIVATE);
            PreferenceManager.setDefaultValues(this, filename, MODE_PRIVATE,
                R.xml.preferences, true);   // true!
        }
        if (pref instanceof EditTextPreference || pref instanceof ListPreference) {
            String s2 = "", s3, s4 = "";
            if (pref instanceof EditTextPreference) {
                s2 = defaultSettings.getString(key, null);  // default
                s4 = "" + ((EditTextPreference) pref).getText();
            } else if (pref instanceof ListPreference) {
                s2 = defaultSettings.getString(pref.getKey(), null);  // default VALUE
                s4 = "" + ((ListPreference) pref).getEntry();  // ENTRY
                if (s2.equals(((ListPreference) pref).getValue())) {
                    s2 = s4;
                } else {
                    CharSequence[] as = ((ListPreference) pref).getEntryValues();
                    for (int i = 0; i < as.length ; i++) {
                        if (as[i].equals(s2)) {
                            s2 = "" + ((ListPreference) pref).getEntries()[i];
                        }
                    }
                }
            }
            s3 = "" + pref.getSummary();
            String wordSet = getResources().getString(R.string.word_set);
            String wordDefault = getResources().getString(R.string.word_default);
            if (!s3.equals("")) {
                int i = s3.indexOf(wordDefault);
                if (i >= 0) {
                    s3 = s3.substring(0, i);
                } else if (i < 0) {
                    s3 += ". ";
                }
            }
            s3 += wordDefault + " " + s2;
            if (s4.equals("null") || s4.equals(s2)) {
                pref.setSummary(s3);
            } else {
                pref.setSummary(Html.fromHtml(s3+", <b>" +
                    getResources().getString(R.string.word_set) + " " + s4 +"</b>"));
            }
        }
    }
}
