package com.gf169.gfwalk;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapActivity extends AppCompatActivity implements
        OnMapReadyCallback,GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMapLoadedCallback, GoogleMap.OnMarkerClickListener,
        GoogleMap.OnInfoWindowClickListener,
        View.OnClickListener {
//        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
//        GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener,

    private static final String TAG = "gfMapActivity";

    static final int MODE_PASSIVE = 0; // Показ прогулки
    static final int MODE_RESUME = 1; // Переход к записи
    static final int MODE_ACTIVE = 2; // Запись - после нарисования первой новой точки

    static final int WRITE_TEXT_REQUEST_CODE = 2;
    static final int RECORD_SPEECH_REQUEST_CODE = 3;

    static final String GLOBAL_INTENT_FILTER = "com.gf.gfwalk";

    static final String DIR_TEXT = "Text";
    static final String DIR_AUDIO = "gfWalk";  // Появится в разделе Files

    int walkId = -1;
    int mode;  // Входной
    int modeInitial;

    GoogleMap map; // Might be null if Google Play services APK is not available.
    Walk walk;
    Menu optionsMenu;

    float zoom0 = 17F; // Начальный для ActiveMode
    boolean zoomAndFocusAreSet = false;
    String textFilePath;  // Файл текстового редактора
    int lastAction = 0;
    int afInGalleryNumber;
    Intent updateFromGalleryIntent;
    boolean pointsAreLoaded; // walk загрузил и нарисовал все точки из базы
    String calledFrom;

    AtomicBoolean animationIsRunning = new AtomicBoolean(false);
    Vector<Thread> animateCameraWaitingThreads = new Vector<>(10, 10);

    Location curLocation;
    Location curLocation2;

    Vector<Marker> curPosMarkers = new Vector<>(4); // Маркеры текущего положения
    static final int MARKER_POSITION_ONLY = 0;  // Индекс в массиве curPosMarkers
    static final int MARKER_WITH_DIRECTION_1 = 1; // Со стрелкой скорости - если известно направление движения
    static final int MARKER_WITH_DIRECTION_2 = 3; // Со стрелкой направления девайса
    static final int MARKER_BAD_POSITION = 2;  // Наоборот, этот маркер обозначает последнее достоверное положение :)

    static final int MO_CUR_POS_POSITION_ONLY = 11;  // Параметры маркера - индекс в массиве MyMarker.mmoA
    static final int MO_CUR_POS_WITH_DIRECTION_1 = 12;
    static final int MO_CUR_POS_WITH_DIRECTION_2 = 18;
    static final int MO_CUR_POS_BAD_POSITION = 13;
    Marker curPosMarker;
    int curPosMarkerIndex = -1;

    Vector<Marker> otherMarkers = new Vector<>(1);
    static final int MARKER_POINT_IN_GALLERY = 0;  // Точка, на которой фокус в галерее
    static final int MO_POINT_IN_GALLERY = 14;

    Vector<Marker> allMarkers=new Vector<>();

    boolean mapIsVisible = false;
    boolean showInfoWindow;  // маркера текущего положения
    int markerWithInfoWindow;  // маркера точки маршрута - getPosition.hashCode()
    Circle accuracyCircle;
    boolean showAccuracyCircle;
    boolean showAFs;
    int mapType = GoogleMap.MAP_TYPE_NORMAL;
    private boolean whereAmIIsOn=false;
    private boolean liveMapIsOn=false;
    boolean returnIfHasGone = false;

    Compas compas;
    ImageView myPositionButton;
    ImageView entireRouteButton;
    static final int COMPAS_VIEW_ID = 5;

    long lastBackPressedTime=0;

    SharedPreferences walkSettings;

    float minPossibleSpeed=-1;
    int currentLocationRequestInterval=-1;
    float cursorPulsationsPerSecond=-1;

    Thread mockerThread;
    Handler handler;

    static LocationListener locationListener;
    static GoogleApiClient googleApiClient;
    static MapActivity curActivity;

    long timeStartSoundRecorder;

    protected void onNewIntent(Intent intent) { // Повторный вход из Gallery
        Utils.logD(TAG, "onNewIntent");
        super.onNewIntent(intent);

        calledFrom = intent.getStringExtra("calledFrom");
        if (walk != null) {
            updateFromGallery(intent);
        } else { // После переворота - еще не было onMapReady
            updateFromGalleryIntent = intent;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate " + this);
        super.onCreate(savedInstanceState);
/* Mitigation of Google bug 24.4.2020
        SharedPreferences googleBug = getSharedPreferences("google_bug", Context.MODE_PRIVATE);
        if (!googleBug.contains("fixed")) {
            File corruptedZoomTables = new File(getFilesDir(), "ZoomTables.data");
            corruptedZoomTables.delete();
            googleBug.edit().putBoolean("fixed", true).apply();
        }
*/
        Utils.setDefaultUncaughtExceptionHandler(() -> {  // Чтобы при отвале все культурно убивало
            Log.e(TAG, "Uncaught Exception");
            if (WalkRecorder.self != null) {
                WalkRecorder.self.stop(6);
            }
            if (locationListener != null) {  // Надо?
                switchWhereAmI(false, false);
                finish();
            }
            Utils.setDefaultUncaughtExceptionHandler(null);
        });

        if (curActivity == null) {  // При старте из notification создается левый экземпляр
            MyMarker.curActivity = this;
            MyMarker.mapActivity = this;
            curActivity = this;
        }

        Utils.raiseRestartActivityFlag(this.getLocalClassName(), false);

        if (savedInstanceState == null) {  // Первый запуск
            savedInstanceState = getIntent().getExtras();
            if (savedInstanceState == null) {  // Надо, может быть !
                finish();
                return;
            }
            mode = savedInstanceState.getInt("mode");  // Новая/продолжение - MODE_RESUME, старая - MODE_PASSIVE
        } else {
            mode = savedInstanceState.getInt("mode");
        }

        modeInitial = savedInstanceState.getInt("modeInitial", mode);
        walkId = savedInstanceState.getInt("walkId", -1);
        zoomAndFocusAreSet = savedInstanceState.getBoolean("zoomAndFocusAreSet");
        afInGalleryNumber = savedInstanceState.getInt("afInGalleryNumber", -1);
        showInfoWindow = savedInstanceState.getBoolean("showInfoWindow");
        showAccuracyCircle = savedInstanceState.getBoolean("showAccuracyCircle");
        showAFs = savedInstanceState.getBoolean("showAFs", true);
        lastAction = savedInstanceState.getInt("lastAction");
        calledFrom = savedInstanceState.getString("calledFrom");
        for (int i = 0; i < curPosMarkers.capacity(); i++) curPosMarkers.add(null);
        for (int i = 0; i < otherMarkers.capacity(); i++) otherMarkers.add(null);
        markerWithInfoWindow = savedInstanceState.getInt("markerWithInfoWindow");
        mapType = savedInstanceState.getInt("mapType", GoogleMap.MAP_TYPE_NORMAL);
        textFilePath = savedInstanceState.getString("textFilePath");
        if (Utils.isEmulator())
            curLocation2 =savedInstanceState.getParcelable("curLocation2");
        liveMapIsOn = savedInstanceState.getBoolean("liveMapIsOn");
        returnIfHasGone = savedInstanceState.getBoolean("returnIfHasGone");
        walkSettings = SettingsActivity.getCurrentWalkSettings(this, walkId);

        whereAmIIsOn = /* mode != MODE_PASSIVE || */
                savedInstanceState.getBoolean("whereAmIIsOn"); // Показывать текущее положение

        setContentView(R.layout.activity_map);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMapAsync(this);
        findViewById(R.id.map_curtain).setOnTouchListener( // Для блокирования touch events во время анимации
                (view, event) -> {return animationIsRunning.get();}); // false - pass on the touch to the map or shadow layer.
    }

    @Override
    protected void onPause() {
        Utils.logD(TAG, "onPause " + this);
        super.onPause();

/*
        // Запоминаем, может, собрались в настройку
        showAddressInMarkers = walkSettings.getBoolean("map_show_address_in_markers", false);
        showDetailsInMarkers = walkSettings.getBoolean("map_show_details_in_markers", false);
        showNettoVauesInMarkers = walkSettings.getBoolean("map_show_netto_values_in_markers", false);
*/

        mapIsVisible = false;

        if (!isChangingConfigurations()) { // Не переворот, НЕ будет сразу же рестартован
            switchWhereAmI(false, true);  // Отключаем получение onLocationChange, а флог сохраняем
            returnIfHasGone = whereAmIIsOn;
        } else {
            returnIfHasGone = false;  // При перевороте не возвращаем
        }
    }

    @Override
    protected void onStop() {
        Utils.logD(TAG, "onStop " + this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Utils.logD(TAG, "onDestroy " + this);
        super.onDestroy();

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
            unregisterReceiver(broadcastReceiver2);
        } catch (Exception e) {
        }
        for (int i = 0; i < animateCameraWaitingThreads.size(); i++) {
            if (animateCameraWaitingThreads.get(i) != null) {
                animateCameraWaitingThreads.get(i).interrupt();
            }
        }
        if (mockerThread!=null) {
            mockerThread.interrupt();
        }
        if (this == curActivity) {  // При старте из notification создается левый экземпляр
            MyMarker.curActivity = null;
            MyMarker.mapActivity = null;
            curActivity = null;
            if (isFinishing() && WalkRecorder.self != null) {
                WalkRecorder.self.stop(7);
            }
        }
    }

    @Override
    protected void onStart() {
        Utils.logD(TAG, "onStart " + this);
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Utils.logD(TAG, "onRestart " + this);
        super.onRestart();
/* Откуда-то берется лишний onPause
        if (Utils.restartActivityIfFlagIsRaised(MapActivity.this)) {
            return;
        }
*/
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("DoInUIThread")
                        .putExtra("action", "restartActivityIfFlagIsRaised"));
    }

    @Override
    protected void onResume() {
        Utils.logD(TAG, "onResume " + this + lastAction);
        super.onResume();

        if (lastAction == R.id.action_photo) {
            addPoint(0, null, null, "onResume (PHOTO)", false); // Чтобы картинка сразу появилась на карте
        } else if (lastAction == R.id.action_video) {
            addPoint(0, null, null, "onResume (VIDEO)", false);
        } else if (lastAction == R.id.action_speech) {
            addPoint(0, null, null, "onResume (SPEECH)", false);
        }
        lastAction = 0;

        // Возможно, ходили в настройку, поменяли
        minPossibleSpeed = Float.parseFloat(walkSettings.getString("map_min_possible_speed", "2.4"));
        int i = currentLocationRequestInterval;
        currentLocationRequestInterval=
                Integer.parseInt(walkSettings.getString("map_current_location_request_interval", "3"));
        if (i!=currentLocationRequestInterval && googleApiClient!=null) {
            requestLocationUpdates();
        }
        cursorPulsationsPerSecond = Float.parseFloat(walkSettings.getString("map_cursor_pulsations_per_second", "1"));

/*
        if (showAddressInMarkers != null && (
                showAddressInMarkers != walkSettings.getBoolean("map_show_address_in_markers", false) ||
                showDetailsInMarkers != walkSettings.getBoolean("map_show_details_in_markers", false) ||
                showNettoVauesInMarkers != walkSettings.getBoolean("map_show_netto_values_in_markers", false))) {
            recreate();  // Чтобы сразу подействовали изменения
        }
*/

        mapIsVisible = true;

        if (whereAmIIsOn) {
            switchWhereAmI(true, false);  // Запускаем слежение
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {  //  Появилась карта
        Utils.logD(TAG, "onMapReady " + map);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver, new IntentFilter("DoInUIThread"));
        registerReceiver(broadcastReceiver2, new IntentFilter(GLOBAL_INTENT_FILTER));

//        UiSettings uiSettings = map.getUiSettings ();
//        uiSettings.setMyLocationButtonEnabled(true);

        this.map = map;
        map.setMapType(mapType);
        map.setBuildingsEnabled(true); // Не работает?
        map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {  // multyline snippet
            @Override
            public View getInfoContents(Marker arg0) {
                return null;
            }
            @Override
            public View getInfoWindow(Marker marker) {
                LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
                View view = inflater.inflate(R.layout.map_infowindow, null, false);
                ((TextView) view.findViewById(R.id.marker_title)).setText(marker.getTitle());
                ((TextView) view.findViewById(R.id.marker_snippet)).setText(marker.getSnippet());

                return view;
            }
        });

        walk = new Walk(this);
        walk.loadAndDraw(true); // Загрузка из базы и рисование - асинхронно, в отдельной thread
        setTitle(getResources().getString(R.string.walk_header) + walkId);
        map.setOnMapLongClickListener(this);
        map.setOnMapLoadedCallback(this);  // Здесь установим масштаб и фокус
        map.setOnMarkerClickListener(this);
        map.setOnInfoWindowClickListener(this);
    }

    @Override
    public void onMapLoaded() { // Не раньше, чем будет показана !!!
        Utils.logD(TAG, "onMapLoaded");
/* Callback interface for when the map has finished rendering. This occurs after all tiles required
to render the map have been fetched, and all labeling is complete. This event will not fire if the
map never loads due to connectivity issues, or if the map is continuously changing and never completes
loading due to the user constantly interacting with the map.
*/
        addButtons();

        if (mode == MODE_RESUME) {
            enterResumeMode();  // Начинаем запись прогулки
        }

        walk.mapIsLoaded = true; // Просто понимаем флаг - loadAndDraw увидит и вернет сюда просьбу установить zoom
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // Вызывается после onStop
        Utils.logD(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt("walkId", walkId);
        savedInstanceState.putInt("mode", mode);
        savedInstanceState.putInt("modeInitial", modeInitial);
        savedInstanceState.putBoolean("zoomAndFocusAreSet", zoomAndFocusAreSet);
        savedInstanceState.putInt("afInGalleryNumber", afInGalleryNumber);
        savedInstanceState.putString("calledFrom", calledFrom);
        savedInstanceState.putInt("lastAction", lastAction);
        savedInstanceState.putBoolean("showInfoWindow", curPosMarker != null &&
                curPosMarker.isInfoWindowShown());
        for (Marker marker: allMarkers) {
            if (marker!=null && marker.isInfoWindowShown()) {
                savedInstanceState.putInt("markerWithInfoWindow",
                        marker.getSnippet() == null ? 0 : marker.getPosition().hashCode());
            }
        }
        savedInstanceState.putBoolean("showAccuracyCircle", showAccuracyCircle);
        savedInstanceState.putBoolean("showAFs", showAFs);
        savedInstanceState.putInt("mapType", mapType);
        savedInstanceState.putString("textFilePath", textFilePath);
        if (Utils.isEmulator())
            savedInstanceState.putParcelable("curLocation2", curLocation2);
        savedInstanceState.putBoolean("whereAmIIsOn", whereAmIIsOn);
        savedInstanceState.putBoolean("liveMapIsOn", liveMapIsOn);
        savedInstanceState.putBoolean("returnIfHasGone", returnIfHasGone);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Utils.logD(TAG, "onCreateOptionsMenu");

        if (walkId < 0) {  // Надо !
            return true;
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.map_activity_actions, menu);
        optionsMenu = menu;
        updateOptionsMenu();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Utils.logD(TAG, "onOptionsItemSelected " + item.getTitle());

        switch (lastAction = item.getItemId()) {
            case R.id.action_photo:
                actionPhoto();
                break;
            case R.id.action_speech:
                actionSpeech();
                break;
            case R.id.action_text:
                actionText();
                break;
            case R.id.action_video:
                actionVideo();
                break;
/* 2 раза back
            case R.id.action_pause:
                enterPassiveMode();
                return true;
*/
            case R.id.action_resume:
                enterResumeMode();
                return true;
            case R.id.action_gallery:
                afInGalleryNumber = showGallery(this, walk, this, null, afInGalleryNumber,
                        mode, false);
                return true;
            case R.id.action_where_am_i:
                switchWhereAmI(!whereAmIIsOn, false);  // Там обратит
                item.setChecked(whereAmIIsOn);
                return true;
            case R.id.action_live_map:
                liveMapIsOn=!liveMapIsOn;
                switchLiveMap(liveMapIsOn);
                item.setChecked(!liveMapIsOn);
                return true;
            case R.id.action_show_afs:
                showAFs = !showAFs;
                recreate();
                item.setChecked(showAFs);
                return true;
            case R.id.action_show_accuracy_circle:
                showAccuracyCircle = !showAccuracyCircle;
                drawAccuracyCircle();
//                updateOptionsMenu();
                item.setChecked(showAccuracyCircle);
                return true;
            case R.id.action_map_type_satelite:
                map.setMapType(
                        mapType = mapType == GoogleMap.MAP_TYPE_HYBRID ?  // Satellite maps with a transparent layer of major streets
                                GoogleMap.MAP_TYPE_NORMAL : GoogleMap.MAP_TYPE_HYBRID);
                item.setChecked(mapType==GoogleMap.MAP_TYPE_HYBRID);
                if (mapType==GoogleMap.MAP_TYPE_HYBRID) {
                    optionsMenu.findItem(R.id.action_map_type_terrain).setChecked(false);
                }
                return true;
            case R.id.action_map_type_terrain:
                map.setMapType(
                        mapType = mapType == GoogleMap.MAP_TYPE_TERRAIN ?  // Рельеф
                                GoogleMap.MAP_TYPE_NORMAL : GoogleMap.MAP_TYPE_TERRAIN);
                item.setChecked(mapType==GoogleMap.MAP_TYPE_TERRAIN);
                if (mapType==GoogleMap.MAP_TYPE_TERRAIN) {
                    optionsMenu.findItem(R.id.action_map_type_satelite).setChecked(false);
                }
                return true;
            case R.id.action_info:
                MainActivity.walkInfo(this, walkId, mode);
                return true;
            case R.id.action_settings:
                MainActivity.settings(this, walkId);
                break;
            case R.id.action_help:
                MainActivity.showHelp(this);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapLongClick(LatLng location) {
        if (pointsAreLoaded) {
            Utils.logD(TAG, "Going into gallery");
            afInGalleryNumber = showGallery(this, walk, this, location, afInGalleryNumber,
                    mode, true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Utils.logD(TAG, "onActivityResult " + requestCode + " " + resultCode);
        super.onActivityResult(requestCode, resultCode, intent);

        switch (requestCode) {
            case WRITE_TEXT_REQUEST_CODE:
                if (true) { //resultCode==Activity.RESULT_OK  { // Редактор всегда дает 0, а не Activity.RESULT_OK=-1
                    File file = new File(textFilePath);
                    if (file.exists() && file.length() > 0) {
                        Utils.logD(TAG, "Text file " + textFilePath + " is written");
                        addPoint(Walk.AFKIND_TEXT, Uri.parse("file://" + textFilePath), textFilePath,
                                "onActivityResult WRITE_TEXT_REQUEST_CODE", false);
                    } else {
                        file.delete();
                    }
                }
                break;

            case RECORD_SPEECH_REQUEST_CODE:  // sound recorder
                String dir = "";
                if ("xiaomi".equals(Build.BRAND)) {
                    dir = Environment.getExternalStorageDirectory().getPath() + "/MIUI/sound_recorder";
                }
/* Не работает
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .setDataAndNormalize(Uri.fromFile(new File(dir))));
*/
                processNewAudioRecords(dir, timeStartSoundRecorder, System.currentTimeMillis());
                break;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        Utils.logD(TAG, "onSupportNavigateUp");

        if (mode!=MODE_PASSIVE) {
            if (requestDoubleClick()) return false;
            handler.removeCallbacksAndMessages(null);
            enterPassiveMode();
            return false;
        }

        finish();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("ToGalleryActivity").putExtra("action", "finish"));
        return true;
    }

    @Override
    public void onBackPressed() {
        Utils.logD(TAG, "onBackPressed");

        if (mode!=MODE_PASSIVE) {
            if (requestDoubleClick()) return;
            handler.removeCallbacksAndMessages(null);
            enterPassiveMode();
            return;
        }

        finish();
        if (!calledFrom.endsWith("MainActivity")) {   // После повторного входа по Back выкидывает на рабочий стол - лечение
            Intent intent = new Intent(this, GalleryActivity.class);
            intent.putExtra("afSize", walk.AFs.size())
                    .putExtra("calledFrom", "MainActivity"); // Чтобы вернулся в нее
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private boolean requestDoubleClick() {
        int dt=500;  // Интервал двойного тюка
        if (lastBackPressedTime == 0 || System.currentTimeMillis() - lastBackPressedTime > dt) {
            (handler=new Handler(Looper.getMainLooper())).postDelayed(() -> {
                if (!isFinishing())
                    Toast.makeText(this, getResources().getString(R.string.press_back_once_more),
                            Toast.LENGTH_SHORT).show();
            },dt+10);
            lastBackPressedTime=System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void addButtons() {
        Utils.logD(TAG, "addButtons");

        entireRouteButton = new ImageView(this);
        entireRouteButton.setImageDrawable(getResources().getDrawable(R.drawable.btn_entire_route));
        entireRouteButton.setClickable(true);
        entireRouteButton.setOnClickListener(this);
        View v = getSupportFragmentManager().findFragmentById(R.id.map).getView().
                findViewById(COMPAS_VIEW_ID);  // Симметрично компасу
        ViewGroup p = (ViewGroup) v.getParent();
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(p.getWidth() - v.getRight(), v.getTop(),
                v.getLeft(), p.getHeight() - v.getBottom());
        p.addView(entireRouteButton, lp); // Последующее изменение lp отражается на view!

        myPositionButton=new ImageView(this);
        myPositionButton.setImageDrawable(getResources().getDrawable(R.drawable.btn_my_position));
        myPositionButton.setClickable(true);
        myPositionButton.setOnClickListener(this);
        myPositionButton.setVisibility(View.INVISIBLE);
        RelativeLayout.LayoutParams lp2 = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        lp2.setMargins(p.getWidth() - 2*v.getRight(), v.getTop(),
                2*v.getLeft() + v.getWidth(), p.getHeight() - v.getBottom());
        p.addView(myPositionButton, lp2);

        if (mode==MODE_ACTIVE) myPositionButton.setVisibility(View.VISIBLE);
    }

    void enterResumeMode() { // До момента нарисования первой новой точки
        Utils.logD(TAG, "enterResumeMode");

        mode = MODE_RESUME;
        switchWhereAmI(true, false);
        updateOptionsMenu();

        Intent intent = new Intent(this, WalkRecorder.class)
                .putExtra("walkId", walkId);
        startService(intent);
    }

    void enterActiveMode() { // Вызывается из Walk после нарисования первой новой точки (Resume -> Active)
        Utils.logD(TAG, "enterActiveMode");

        mode = MODE_ACTIVE;

        updateOptionsMenu();
        MainActivity.pleaseDo = MainActivity.pleaseDo + " refresh selected item";
    }

    private void enterPassiveMode() {
        Utils.logD(TAG, "enterPassiveMode");

        modeInitial = mode;
        mode = MODE_PASSIVE;

        addPoint(0, null, null, "enterPassiveMode", true); // Добавь точку и умри

        updateOptionsMenu();
        for (int i = 0; i < curPosMarkers.capacity(); i++) {
            if (curPosMarkers.get(i) != null) { // Успел нарисовать
                MyMarker.killMarker(curPosMarkers, i);
            }
        }

        curPosMarker = null;
        curPosMarkerIndex = -1;

        showAccuracyCircle = false;
        drawAccuracyCircle();

        switchWhereAmI(false, false); // Там curLocation = null

        myPositionButton.setVisibility(View.INVISIBLE);

        if (modeInitial==MODE_ACTIVE) {
            Toast.makeText(MapActivity.this, getResources().getString(R.string.walk_recording_ended),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateOptionsMenu() {
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_photo).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_speech).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_text).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_video).setVisible(mode == MODE_ACTIVE);
//            optionsMenu.findItem(R.id.action_pause).setVisible(mode != MODE_PASSIVE);
            optionsMenu.findItem(R.id.action_resume).setVisible(mode == MODE_PASSIVE &&
                    modeInitial != MODE_PASSIVE);  // ПРИостановленная новая прогулка
            optionsMenu.findItem(R.id.action_gallery).setVisible(mode != MODE_RESUME &&
                    pointsAreLoaded);
            optionsMenu.findItem(R.id.action_where_am_i).setVisible(mode == MODE_PASSIVE)
                    .setChecked(whereAmIIsOn);
            optionsMenu.findItem(R.id.action_live_map).setVisible(mode == MODE_ACTIVE || whereAmIIsOn)
                    .setChecked(liveMapIsOn);
            optionsMenu.findItem(R.id.action_show_accuracy_circle).setVisible(mode == MODE_ACTIVE)
                    .setChecked(showAccuracyCircle);
            optionsMenu.findItem(R.id.action_show_afs)
                    .setChecked(showAFs);
            optionsMenu.findItem(R.id.action_map_type_terrain)
                    .setChecked(mapType==GoogleMap.MAP_TYPE_TERRAIN);
            optionsMenu.findItem(R.id.action_map_type_satelite)
                    .setChecked(mapType==GoogleMap.MAP_TYPE_HYBRID);

            invalidateOptionsMenu();
        }
    }

    void actionPhoto() {
        Utils.logD(TAG, "actionPhoto");

        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA); // Так и только так !
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Чтобы забыла про камеру
        startActivity(intent);
        addPoint(0, null, null, "startActivity INTENT_ACTION_STILL_IMAGE_CAMERA", false);
    }

    void actionSpeech() {
        Utils.logD(TAG, "actionSpeech");

        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        PackageManager pm = this.getPackageManager();
        final ResolveInfo mInfo = pm.resolveActivity(intent, 0);
        try {
            if ("xiaomi".equals(Build.BRAND) &&  // Не регистрирует файлы в Content provider'e - сами регистрируем
                    "com.android.soundrecorder.SoundRecorder".equals(  // И не дает себя заменить, так что это на всякий случай
                            intent.resolveActivity(getPackageManager()).getClassName())) {
                timeStartSoundRecorder = System.currentTimeMillis();
                startActivityForResult(intent, RECORD_SPEECH_REQUEST_CODE);
            } else {
                startActivity(intent); // !!!
            }

            addPoint(0, null, null, "startActivity RECORD_SOUND_ACTION", false);

        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getResources().getString(R.string.no_sound_recording_program),
                    Toast.LENGTH_LONG).show();
        }
    }

    void actionText() {
        Utils.logD(TAG, "actionText");

        String dir = Utils.createDirIfNotExists(getFilesDir().getAbsolutePath(), DIR_TEXT); // Internal
        if (dir == null) {
            Toast.makeText(this,
                    String.format(
                            getResources().getString(R.string.format_could_not_create_directory),
                            "Text", getFilesDir().getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        textFilePath = dir + "/" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
        try {
            new File(textFilePath).createNewFile();
        } catch (java.io.IOException e) {
            Toast.makeText(this, String.format(getResources().getString(R.string.format_could_not_create_file), textFilePath),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, EditTextActivity.class);
        intent.putExtra("textFilePath", textFilePath);
        startActivityForResult(intent, WRITE_TEXT_REQUEST_CODE);
    }

    void actionVideo() {
        Utils.logD(TAG, "actionVideo");

        Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA); // Так и только так !
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Чтобы забыла про камеру
        startActivity(intent);
        addPoint(0, null, null, "startActivity INTENT_ACTION_VIDEO_CAMERA", false);
    }

    private void addPoint(
            int afKind,
            Uri afUri,
            String afFilePath,
            String debugInfo,
            boolean isLastPoint) {
/*
        Intent intent = new Intent(this, WalkRecorder.class)
                .putExtra("walkId", walkId)
                .putExtra("location", curLocation)
                .putExtra("debugInfo", debugInfo).putExtra("isLastPoint", isLastPoint);
        if (afKind > 0) {
            intent.putExtra("afKind", afKind)
                .putExtra("afUri", afUri.toString()).putExtra("afFilePath", afFilePath);
        }
        startService(intent);
*/
        WalkRecorder.self.addPoint(curLocation, debugInfo, afKind,
                afUri == null ? null : afUri.toString(), afFilePath, isLastPoint);
    }

    static int showGallery(Activity activity, Walk walk,
                           MapActivity mapActivity, LatLng location,
                           int afInGalleryNumber, // -1 - первая, -2 - которая иконка прогулки (вызов из MainActivity)
                           int mode, boolean isLongClick) {
        ArrayList<String> afParcels = new ArrayList<>();
        ArrayList<String> pointStrs = new ArrayList<>();
        float d1 = 1e10F;
        float d2;
        int lastPointNumber = -1;
        int pointNumber = -1;
        int afInGalleryNumber2 = -1;

        for (int k = 0; k < walk.AFs.size(); k++) {
            afParcels.add(walk.AFs.get(k).toString());
            if (walk.AFs.get(k).deleted) {
                continue;
            }
            if (afInGalleryNumber2 < 0) {
                afInGalleryNumber2 = k; // Первый неудаленный
            }
            pointNumber = walk.AFs.get(k).pointNumber;
            if (pointNumber > lastPointNumber) {
                for (int j = lastPointNumber + 1; j < pointNumber; j++) {
                    pointStrs.add(null);  // Передаем толькво описание точек, у которых есть артефакты
                }
                pointStrs.add(MainActivity.secondStr(walk.timeZone.getID(),
                        walk.points.get(pointNumber).time, walk.points.get(pointNumber).address));
            }
            if (location != null) { // Ищем ближайшую точку к кликнутом месту
                d2 = Utils.getDistance(location,
                        Utils.loc2LatLng(walk.points.get(pointNumber).location));
                if (d2 < d1) {
                    d1 = d2;
                    afInGalleryNumber = k;
                }
            }
            lastPointNumber = pointNumber;
        }
        if (pointNumber < 0) {
            Toast.makeText(activity, activity.getResources().getString(R.string.nothing_to_show), Toast.LENGTH_SHORT).show();
            return -1;
        }
        afInGalleryNumber = afInGalleryNumber >= 0 && !walk.AFs.get(afInGalleryNumber).deleted ||
                afInGalleryNumber == -2 ?
                afInGalleryNumber : afInGalleryNumber2;
        if (mapActivity != null) {
            mapActivity.drawPointInGalleryMarker(afInGalleryNumber);
        }
        Intent intent = new Intent(activity, GalleryActivity.class);
        intent.putExtra("walkId", walk.walkId).
                putExtra("afInGalleryNumber", afInGalleryNumber).
                putStringArrayListExtra("afParcels", afParcels).
                putStringArrayListExtra("pointStrs", pointStrs).
                putExtra("afSize", walk.AFs.size()).
                putExtra("mode", mode).
                putExtra("isLongClick", isLongClick).
                putExtra("calledFrom", activity.getLocalClassName());
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);

        return afInGalleryNumber;
    }

    void showGallery2() { // Отсюда через Инфо
        afInGalleryNumber = showGallery(this, walk, this, null, afInGalleryNumber,
                mode, false);
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("action");
            Utils.logD(TAG, "Got a local message - " + action);

            switch (action) {
                case "showNotConnectedDialog":  // GoogleApiClient не подключтлся к сервису
                    int resultCode = intent.getIntExtra("resultCode", 0);
                    if (resultCode == ConnectionResult.SERVICE_MISSING ||
                            resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ||
                            resultCode == ConnectionResult.SERVICE_DISABLED) {
                        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, MapActivity.this, 1);
                        dialog.show();
                    } else {
                        String result = intent.getStringExtra("result");
                        Toast.makeText(context,
                                "Couldn't connect to Google service, result: " + result,
                                Toast.LENGTH_LONG).show();
                    }
                    finish();

                    break;
                case "loadAndDraw":   // WalkRecoder создал новую точку (записал в базу) - рисуем ее
                    walk.loadAndDraw(false);

                    break;
                case "onLocationChanged":   // Из gfMockerThread2
                    onLocationChanged(Utils.str2Loc(intent.getStringExtra("location")));

                    break;
                case "setZoomAndFocus":  // Из Walk по окончании загрузки старой прогулки

                    setZoomAndFocus();

                    if (updateFromGalleryIntent != null) {
                        updateFromGallery(updateFromGalleryIntent);
                        updateFromGalleryIntent = null;
                    }
                    pointsAreLoaded = true;
                    updateOptionsMenu();

                    break;
                case "toast":
                    Toast.makeText(MapActivity.this,
                            intent.getStringExtra("text"),
                            intent.getIntExtra("duration", Toast.LENGTH_SHORT) == Toast.LENGTH_SHORT ?
                                    Toast.LENGTH_SHORT : Toast.LENGTH_SHORT).show();

                    break;
                case "updateFromGallery":
                    updateFromGallery(intent);

                    break;
                case "finish":  // from Gallery when pressed Up
                    finish();
                    Intent intent2 = new Intent(context, MainActivity.class);  // В некотрых случаях вываливается в рабочий стол

                    intent2.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // На всякий случай

                    startActivity(intent2);

                    break;
                case "restartActivityIfFlagIsRaised":
                    if (Utils.restartActivityIfFlagIsRaised(MapActivity.this)) {
                        return;
                    }
                    break;
            }
        }
    };

    void setZoomAndFocus() {  // Несколько вызовов, выполняется только первый
        Utils.logD(TAG, "setZoomAndFocus " + zoomAndFocusAreSet);

        if (zoomAndFocusAreSet || !walk.mapIsLoaded) return;

        int iRes = 0;
        if (curLocation != null) {
            iRes = 1;
            animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(
                    Utils.loc2LatLng(curLocation), zoom0, 0, 0)));
        } else if (walk.points.size() > 0) {
            showEntireRoute();
            iRes = 2;
        }
        zoomAndFocusAreSet = iRes>0;

        Utils.logD(TAG, "setZoomAndFocus " + iRes);
    }

    void drawPointInGalleryMarker(int afInGalleryNumber) {
        if (!showAFs) {
            return;
        }
        if (afInGalleryNumber < 0) {
            if (otherMarkers.get(MARKER_POINT_IN_GALLERY) != null) {
                otherMarkers.get(MARKER_POINT_IN_GALLERY).setVisible(false);
            }
            return;
        }
        int pointInGalleryNumber = walk.AFs.get(afInGalleryNumber).pointNumber;
        if (otherMarkers.get(MARKER_POINT_IN_GALLERY) == null) {
            MyMarker.drawMarker(map,
                    otherMarkers, MARKER_POINT_IN_GALLERY,
                    MO_POINT_IN_GALLERY, new Location(""), "", "",
                    null, null, null, -1, 0,false, true);
        }
        Marker marker = otherMarkers.get(MARKER_POINT_IN_GALLERY);
        marker.setTitle("В галерее");
        marker.setPosition(Utils.loc2LatLng(
                walk.points.get(pointInGalleryNumber).location));
        marker.setVisible(true);
    }

    BroadcastReceiver broadcastReceiver2 = new BroadcastReceiver() {   // Глобальный
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("action");
            Utils.logD(TAG, "Got a global message - " + action);

            if ("stopRecording".equals(action)) { // Из notification WalkRecorder
                enterPassiveMode();
            }
        }
    };

    void updateFromGallery(Intent intent) {
        Utils.logD(TAG, "updateFromGallery");

        final int deletedAFInGalleryNumber = intent.getIntExtra("deletedAFInGalleryNumber", -1);
        if (deletedAFInGalleryNumber >= 0) {
            walk.AFs.get(deletedAFInGalleryNumber).deleted = true;
            new Thread(
                    new Runnable() {
                        public void run() {
                            int i = walk.AFs.get(deletedAFInGalleryNumber).pointNumber;
                            walk.drawAFMarkers2(
                                    walk.points.get(i),
                                    walk.AFs.get(deletedAFInGalleryNumber).kind, true);
                        }
                    }, "gfDrawAFMarkers").start();
        }
        int i = afInGalleryNumber;  // Точку в галерее в центр
        i = i >= 0 && i < walk.AFs.size() ? walk.AFs.get(i).pointNumber : -1;
        afInGalleryNumber = intent.getIntExtra("afInGalleryNumber", -1);
        int j = afInGalleryNumber >= 0 ? walk.AFs.get(afInGalleryNumber).pointNumber : -1;
        if (mode == MODE_PASSIVE && j >= 0 && j != i) {
            animateCamera(CameraUpdateFactory.newLatLng(
                    Utils.loc2LatLng(walk.points.get(j).location)));
        }
        drawPointInGalleryMarker(afInGalleryNumber);
    }

    void meToTheCenter() {
        Utils.logD(TAG, "meToTheCenter");

        LatLng latLng = null;
        if (curLocation != null) {
            latLng = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());
        }
//        if (latLng == null && walk.points.size() > 0) {
//            latLng = Utils.loc2LatLng(walk.points.get(walk.points.size() - 1).location);
//        }
        if (latLng != null) {
            animateCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    void showEntireRoute() {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (int i = 0; i < walk.points.size(); i++) {
            builder.include(Utils.loc2LatLng(walk.points.get(i).location));
        }
        if (curPosMarker != null) {
            builder.include(curPosMarker.getPosition());
        }
        LatLngBounds bounds;
        try {
            bounds = builder.build();
        } catch (Exception e) {
            return; // Ни одной точки нет. Или Error using newLatLngBounds(LatLngBounds, int): Map size can't be 0. Most likely, layout has not yet occured for the map view.  Either wait until layout has occurred or use newLatLngBounds(LatLngBounds, int, int, int) which allows you to specify the map's dimensions.
        }
        int padding = 50; // offset from edges of the map in pixels
        animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));  // Zoom и Focus. Animate иногда зависает :(
    }

    void drawCurPosMarker() {
        Utils.logD(TAG, "drawCurPosMarker");

        if (!mapIsVisible) return;

        if (!whereAmIIsOn // Может быть
                || curLocation == null) { // На всякий...
            return;
        }

        boolean isInfoWindowShown = curPosMarker != null && curPosMarker.isInfoWindowShown();

        String title = Utils.locationFullInfStr(curLocation,
                walk.points.size() > 0 && walk.points.get(walk.points.size()-1).location.hasAltitude() ?
                    walk.points.get(walk.points.size()-1).location.getAltitude() : Utils.IMPOSSIBLE_ALTITUDE,
                walk.initialAltitude);
        int curPosMarkerIndexNew = getKindOfCurPosMarker();
        if (curPosMarkerIndexNew != curPosMarkerIndex) {
            curPosMarkerIndex = curPosMarkerIndexNew;
            int markerKind =
                    curPosMarkerIndex == MARKER_BAD_POSITION ? MO_CUR_POS_BAD_POSITION :
                            curPosMarkerIndex == MARKER_POSITION_ONLY ? MO_CUR_POS_POSITION_ONLY :
                                    curPosMarkerIndex == MARKER_WITH_DIRECTION_1 ? MO_CUR_POS_WITH_DIRECTION_1 :
                                            MO_CUR_POS_WITH_DIRECTION_2;
            MyMarker.drawMarker(map, curPosMarkers, 0, markerKind,  // 0! Потому что одновременно может быть только 1
                    curLocation, title, null,
                    null, null, null,
                    curPosMarkerIndex == MARKER_WITH_DIRECTION_1 ? curLocation.getBearing() :
                            curPosMarkerIndex == MARKER_WITH_DIRECTION_2 ? compas.getAzimuth() : -1,
                    cursorPulsationsPerSecond,true, true);
            curPosMarker = curPosMarkers.get(0);
        } else {
            curPosMarker.setTitle(title);
            curPosMarker.setPosition(Utils.loc2LatLng(curLocation));
            if (curPosMarkerIndex == MARKER_WITH_DIRECTION_1) {
                curPosMarker.setRotation(curLocation.getBearing());
            } else if (curPosMarkerIndex == MARKER_WITH_DIRECTION_2) {
                curPosMarker.setRotation(compas.getAzimuth());
            }
        }
        if (showInfoWindow || isInfoWindowShown) {
            showInfoWindow = false; // Только самый первый раз
            markerWithInfoWindow=0;
            curPosMarker.showInfoWindow();  // Refresh'им
        }
        drawAccuracyCircle();

        if (liveMapIsOn) {
            rotateMap(true);
        }
    }

    void drawAccuracyCircle() {
        if (accuracyCircle != null) {
            accuracyCircle.remove();
        }
        if (!showAccuracyCircle) {
            return;
        }
        if (curLocation != null && curLocation.hasAccuracy()) {
            accuracyCircle = map.addCircle(
                    new CircleOptions()
                            .center(Utils.loc2LatLng(curLocation))
                            .radius(curLocation.getAccuracy())
                            .strokeWidth(0)
                            .fillColor(Color.argb(20, 0, 0, 255)));
        }
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        if (marker != curPosMarker) {
            markerWithInfoWindow = marker.getSnippet() == null ? 0 : marker.getPosition().hashCode();
        }
        return false;
    }

    @Override
    public void onInfoWindowClick (Marker marker) {
        marker.hideInfoWindow();
    }

    @Override
    public void onClick(View v) {
        if (v == myPositionButton) {
            if (curLocation != null) {
                meToTheCenter();
            }
        } else if (v == entireRouteButton) {
            showEntireRoute();
        }
    }

    void returnIfHasGone() {  // Если я ушел за экран, возвращаем. Не работает с mock location
        Utils.logD(TAG, "returnIfHasGone");

        StringBuilder s = new StringBuilder();
        if (map == null || walk == null // || mode == MODE_PASSIVE
                || !walk.mapIsLoaded && Utils.set(s, "!walk.mapIsLoaded")
                || curLocation == null &&
                Utils.set(s, "curLocation == null")
        ) {
            Utils.logD(TAG, "returnIfHasGone 1: " + s);
            return;
        }
        Utils.logD(TAG, "returnIfHasGone 2: " + map.getCameraPosition().zoom);
        Location location = curLocation != null ?
                curLocation : walk.points.get(walk.points.size() - 1).location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        Point point = map.getProjection().toScreenLocation(latLng);
        if (!Utils.isBetween(point.x, 0, findViewById(R.id.map).getWidth()) ||
                !Utils.isBetween(point.y, 0, findViewById(R.id.map).getHeight())) {
            animateCamera(CameraUpdateFactory.newLatLng(latLng));
            Utils.logD(TAG, "returnIfHasGone 3: returned");
        }
    }

    void animateCamera(final CameraUpdate cameraUpdate) {  // Не две одновременно
        Utils.logD(TAG, "animateCamera");

        final Thread[] thread = {null};
        thread[0] = Utils.runX(
                () -> {
                    Utils.logD(TAG, "animateCamera: real start " + thread[0].getId());
                    map.animateCamera(cameraUpdate,
                            new GoogleMap.CancelableCallback() {
                                @Override
                                public void onFinish() {
                                    Utils.logD(TAG, "animateCamera: stop1 " + thread[0].getId());
                                    thread[0].interrupt();
                                    animationIsRunning.set(false);
                                    animateCameraWaitingThreads.set(
                                            animateCameraWaitingThreads.indexOf(thread[0]), null);
//                                    zoomAndFocusAreSet = true;
                                }

                                @Override
                                public void onCancel() {
                                    Utils.logD(TAG, "animateCamera: stop2 " + thread[0].getId());
                                    thread[0].interrupt();
                                    animationIsRunning.set(false);
                                    animateCameraWaitingThreads.set(
                                            animateCameraWaitingThreads.indexOf(thread[0]), null);
                                }
                            }
                    );
                    animationIsRunning.set(true);
                }, animationIsRunning, -1, this);
        animateCameraWaitingThreads.add(thread[0]);
        Utils.logD(TAG, "animateCamera: start " + thread[0].getId());
    }

    int getKindOfCurPosMarker() {
        if (minPossibleSpeed != 0 && (!curLocation.hasSpeed()
                || curLocation.getSpeed() < minPossibleSpeed)
                && turnCompasOn(true)) {
            return MARKER_WITH_DIRECTION_2;
        } else if (curLocation.hasBearing()) {
            turnCompasOn(false);
            return MARKER_WITH_DIRECTION_1;
        }
        turnCompasOn(false);
        return MARKER_POSITION_ONLY;
    }

    boolean turnCompasOn(boolean on) {
        if (on) {
            if (compas == null) {
                compas = new Compas(this);
            }
            return compas.turnOn(true);
        } else {
            if (compas != null) {
                return compas.turnOn(false);
            }
            return true;
        }
    }

    void switchWhereAmI(boolean on, boolean preserveFlag) {
        Utils.logD(TAG, "switchWhereAmI "+on);

        // if (on == whereAmIIsOn) return;
        if (!preserveFlag)  whereAmIIsOn=on;

        if (on) {
            if (Utils.isEmulator()) {
                mockerThread = WalkRecorder.startMocking((location)-> LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent("DoInUIThread")
                                .putExtra("action", "onLocationChanged")
                                .putExtra("location", Utils.loc2Str(location))),
                        "gfMockerThread2", 3000,
                        Utils.nvl(curLocation2, Utils.str2Loc("55.75222 37.61556"))); // Москва, Кремль
                return;
            }
            if (locationListener == null) {
                // Постоянный, не пресоздается при первороте
                locationListener = MapActivity::onLocationChanged;
                // Тоже
                googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle connectionHint) { // Google Api client
                                curActivity.onConnected(connectionHint);
                            }
                            @Override
                            public void onConnectionSuspended(int cause) {  // Сам восстановит
                                Utils.logD(TAG, "onConnectionSuspended, cause=" + cause);
                            }
                        })
                        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                            public void onConnectionFailed(ConnectionResult result) {  // Никогда не видел
                                Utils.logD(TAG, "onConnectionFailed, result: " + result);

                                LocalBroadcastManager.getInstance(curActivity).sendBroadcast(
                                        new Intent("DoInUIThread")
                                                .putExtra("action", "showNotConnectedDialog")  // Выдаст диалог "Установите новую версию"
                                                .putExtra("resultCode", result.getErrorCode())
                                                .putExtra("result", result.toString()));
                            }
                        })
                //                       .enableAutoManage(this, this)
                        .build();
            }
            if (googleApiClient.isConnected()) {
                onConnected(null);
            } else {
                googleApiClient.connect();
            }

        } else {
            MyMarker.killMarker(curPosMarkers, 0);
            curPosMarkerIndex = -1;
            if (myPositionButton != null)
                myPositionButton.setVisibility(View.INVISIBLE);
            if (mockerThread!=null) {
                mockerThread.interrupt();
                mockerThread = null;
                curLocation2 = new Location(curLocation);
            } else {
                if (googleApiClient != null && googleApiClient.isConnected()) {
                    LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationListener);
                }
            }
            curLocation = null;
        }
    }

    public void onConnected(Bundle connectionHint) {
        Utils.logD(TAG, "onConnected");
        requestLocationUpdates();
    }

    static void onLocationChanged(Location location)    {
        Utils.logD(TAG, "onLocationChanged");

        if (curActivity == null || curActivity.walk == null || !curActivity.walk.mapIsLoaded
            || curActivity.animationIsRunning.get()) return;

        boolean isFirst = curActivity.curLocation==null; // Первое после включения
        if (isFirst) {
            curActivity.curLocation = new Location("");
        }
        curActivity.curLocation.set(location);

        if (curActivity.myPositionButton!=null) curActivity.myPositionButton.setVisibility(View.VISIBLE);
        curActivity.drawCurPosMarker();

        if (isFirst) {
            if (!curActivity.zoomAndFocusAreSet) {
                curActivity.setZoomAndFocus();
            } else {
                if (curActivity.returnIfHasGone) {
                    curActivity.returnIfHasGone();
                }
            }
        }

        if (curActivity.mode == MODE_ACTIVE) {
            Intent intent = new Intent(curActivity.getApplicationContext(), WalkRecorder.class);
            intent.putExtra("speedFromMap", location.getSpeed());
            curActivity.startService(intent);
        }
    }

    private void requestLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(currentLocationRequestInterval * 1000);
        locationRequest.setFastestInterval(currentLocationRequestInterval * 1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleApiClient, locationListener);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, locationListener);
    }

    void switchLiveMap(boolean on) {
        Utils.logD(TAG, "switchLiveMap " + on);
        rotateMap(on);
    }

    void rotateMap(boolean on) {
        if (curPosMarkerIndex == MARKER_WITH_DIRECTION_1 || curPosMarkerIndex == MARKER_WITH_DIRECTION_2) {
            CameraPosition pos = map.getCameraPosition();
            if (on) { // стрелка маркера смотрит вверх
                pos = CameraPosition.builder(pos)
                        .target(Utils.loc2LatLng(curLocation))
                        .bearing(curPosMarkerIndex == MARKER_WITH_DIRECTION_1 ? curLocation.getBearing() :
                                curPosMarkerIndex == MARKER_WITH_DIRECTION_2 ? compas.getAzimuth() : 0)
                        .build();
            } else {  // Возвращаем, верх - север
                pos = CameraPosition.builder(pos)
                        .bearing(0)
                        .build();
            }
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
    }

    void processNewAudioRecords(String sDir, long tStart, long tStop) {
        File dir = new File(sDir);
        try {
            for (File file : dir.listFiles(
                    (file) -> {return file.lastModified() >= tStart && file.lastModified() <= tStop;})) {
    //            addPoint(Walk.AFKIND_SPEECH, Uri.fromFile(file), file.getAbsolutePath(), // При просмотре будет FileUriExposedException
    //                    "onActivityResult RECORD_SPEECH_REQUEST_CODE", false);
                addToMediaProvider(file);
            }
            ;
        } catch (Exception e) {
        }
    }

    void addToMediaProvider(File file) {
        Utils.logD(TAG, "addToMediaProvider " + file.getAbsolutePath());

//        String file2Path =  Utils.createDirIfNotExists(getFilesDir().getAbsolutePath(),
        String file2Path =  Utils.createDirIfNotExists(getExternalFilesDir(null).getAbsolutePath(),
                DIR_AUDIO) +  // Именно External, иначе снаружи не увидят - FileNotFoundException
                "/" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +
                "." + Utils.getFileExtension(file.getName());
// Internal - /data/data/com.gf169.gfwalk.debug/files/Audio/20200528_223728.mp3
// External - /storage/emulated/0/Android/data/com.gf169.gfwalk.debug/files/Audio/20200529_151339.mp3
//            /storage/emulated/0 = /sdcard - link
        File file2 = Utils.copyFile(file.getAbsolutePath(), file2Path);
        if (file2 == null) {
            Toast.makeText(
                    this, "Failed to copy " + file.getAbsolutePath() + " into " + file2Path,
                    Toast.LENGTH_LONG)
                    .show();
            return;
        }
//        file.setReadable(true, false); // И так читается

        ContentValues values = new ContentValues(1);
//        values.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath()) ;  // Не видит!
        values.put(MediaStore.Audio.Media.DATA, file2Path) ;
        Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Toast.makeText(this, "Failed to insert " + file2Path + " into MediaStore",
                    Toast.LENGTH_LONG).show();
        }
    }
}