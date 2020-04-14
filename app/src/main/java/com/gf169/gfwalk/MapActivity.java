package com.gf169.gfwalk;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;

import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapActivity extends AppCompatActivity implements
        OnMapReadyCallback,GoogleMap.OnMapLongClickListener
        ,GoogleMap.OnMapLoadedCallback, GoogleMap.OnMarkerClickListener,
        View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener
//        ,GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener
{
    static final String TAG = "gfMapActivity";

    public static final int MODE_PASSIVE = 0; // Показ прогулки
    public static final int MODE_RESUME = 1; // Переход к записи
    public static final int MODE_ACTIVE = 2; // Запись - после нарисования первой новой точки

    static final int WRITE_TEXT_REQUEST_CODE = 2;

    public static final String GLOBAL_INTENT_FILTER = "com.gf.gfwalk";

    boolean mapIsVisible = false;

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
    long lastSaveTime;

    AtomicBoolean animationIsRunning = new AtomicBoolean(false);
    Vector<Thread> animateCameraWaitingThreads = new Vector<>(10, 10);

    Location curLocation;
    Location oldLocation;
    boolean curLocationIsBad;
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
    boolean showInfoWindow;
    Circle accuracyCircle;
    boolean showAccuracyCircle;
    boolean showAFs;
    int mapType = GoogleMap.MAP_TYPE_NORMAL;

    Vector<Marker> otherMarkers = new Vector<>(1);
    static final int MARKER_POINT_IN_GALLERY = 0;  // Точка, на которой фокус в галерее
    static final int MO_POINT_IN_GALLERY = 14;

    SharedPreferences walkSettings;
    Compas compas;
    static final int MY_POSITION_BUTTON_ID = 102;
    static final int COMPAS_VIEW_ID = 5;

    private boolean whereAmIIsOn=false;
    private GoogleApiClient googleApiClient = null;  // Для режима WhereAmI

    private boolean liveMapIsOn=false;
/*
    @Override
    public void onMyLocationClick(@NonNull Location location) {
//            Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onMyLocationButtonClick() {
        whereAmI();
        return true;  // Перехватили, стандартный курсор не покажет!
    }    @Override
*/

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
        Utils.logD(TAG, "onCreate");
        super.onCreate(savedInstanceState);

//        AirbrakeNotifier.register(this,
//                "f17762b5ea71e1af3bcf37ba0cb2a67c",
//                "", false);

        MyMarker.curActivity = this;
        Utils.curActivity = this;
        Utils.raiseRestartActivityFlag(this.getLocalClassName(), false);

        if (savedInstanceState == null) {  // Первый запуск
            savedInstanceState = getIntent().getExtras();
            if (savedInstanceState == null) {  // Надо, может быть !
                finish();
                return;
            }
            mode = savedInstanceState.getInt("mode");
        } else {
            mode = WalkRecorder.isWorking ?
                    savedInstanceState.getInt("mode") :
                    MODE_PASSIVE; // Прекратили запись из notification'a
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
        for (int i = 0; i < curPosMarkers.capacity(); i++) {
            curPosMarkers.add(null);
        }
        for (int i = 0; i < otherMarkers.capacity(); i++) {
            otherMarkers.add(null);
        }
        mapType = savedInstanceState.getInt("mapType", GoogleMap.MAP_TYPE_NORMAL);
        lastSaveTime = savedInstanceState.getLong("lastSaveTime");
        textFilePath = savedInstanceState.getString("textFilePath");

        whereAmIIsOn = savedInstanceState.getBoolean("whereAmIIsOn"); // Показ текущего положения в passive mode
        if (whereAmIIsOn) {
            curLocation=savedInstanceState.getParcelable("curLocation");
            curLocationIsBad=false;
        } else {
            curLocation = WalkRecorder.lastGoodLocation;
            curLocationIsBad = WalkRecorder.curLocationIsOK <= 0;
        }

        liveMapIsOn = savedInstanceState.getBoolean("liveMapIsOn"); // Показ текущего положения в passive mode

        walkSettings = SettingsActivity.getCurrentWalkSettings(this, walkId);
/*
        boolean b=walkSettings.getBoolean("map_cursor_pulsations_per_second",true);
        MyMarker.turnAnimationOnOff(MO_CUR_POS_BAD_POSITION,b);
        MyMarker.turnAnimationOnOff(MO_CUR_POS_POSITION_ONLY,b);
        MyMarker.turnAnimationOnOff(MO_CUR_POS_WITH_DIRECTION_1,b);
        MyMarker.turnAnimationOnOff(MO_CUR_POS_WITH_DIRECTION_2,b);
*/

        setContentView(R.layout.activity_map);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMapAsync(this);

        findViewById(R.id.map_curtain).setOnTouchListener( // Для блокирования touch events во впремя анимации
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return animationIsRunning.get(); // false - pass on the touch to the map or shadow layer.
                    }
                });
    }

    @Override
    protected void onPause() {
        Utils.logD(TAG, "onPause");
        super.onPause();

        mapIsVisible = false;
/*
        if (compas !=null) {
            compas.turnOn(false);
        }
*/
    }

    @Override
    protected void onStop() {
        Utils.logD(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Utils.logD(TAG, "onDestroy");
        super.onDestroy();

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
            unregisterReceiver(broadcastReceiver2);
        } catch (Exception e) {
        }
        if (mode != MODE_PASSIVE && isFinishing()) {  // Нажата Back или Up или в программе Finish()
            Toast.makeText(MapActivity.this, getResources().getString(R.string.walk_recording_ended),
                    Toast.LENGTH_SHORT).show();
            addPoint(0, null, null, "onDestroy", true); // Команда WalkRecorder'у: добавь и умри
        }
        for (int i = 0; i < curPosMarkers.size(); i++) {
            MyMarker.killMarker(curPosMarkers, i);
        }
        for (int i = 0; i < otherMarkers.size(); i++) {
            MyMarker.killMarker(otherMarkers, i);
        }
        for (int i = 0; i < MyMarker.workMarkers.size(); i++) {
            MyMarker.killMarker(MyMarker.workMarkers, i);
        }
        for (int i = 0; i < animateCameraWaitingThreads.size(); i++) {
            if (animateCameraWaitingThreads.get(i) != null) {
                animateCameraWaitingThreads.get(i).interrupt();
            }
        }
        if (googleApiClient!=null && googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        }
    }

    @Override
    protected void onStart() {
        Utils.logD(TAG, "onStart");
        super.onStart();

        returnIfHasGone();
    }

    @Override
    protected void onRestart() {
        Utils.logD(TAG, "onRestart");
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
        Utils.logD(TAG, "onResume " + lastAction);
        super.onResume();

        if (lastAction == R.id.action_photo) {
            addPoint(0, null, null, "onResume (PHOTO)", false); // Чтобы картинка сразу появилась на карте
        } else if (lastAction == R.id.action_video) {
            addPoint(0, null, null, "onResume (VIDEO)", false);
        } else if (lastAction == R.id.action_speech) {
            addPoint(0, null, null, "onResume (SPEECH)", false);
        }
        lastAction = 0;

        mapIsVisible = true;

/*
        if (mode==MODE_ACTIVE) {
            if (compas ==null) {
                compas =new Compas(this, );
            }
            compas.turnOn(true);
        }
*/
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

        // Рисуем my location button
        View v = getSupportFragmentManager().findFragmentById(R.id.map).getView().
                findViewById(COMPAS_VIEW_ID);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
        ViewGroup p = (ViewGroup) v.getParent();
        ImageView v2 = new ImageView(this);
        RelativeLayout.LayoutParams lp2 = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        lp2.setMargins(p.getWidth() - v.getRight(), lp.topMargin, lp.leftMargin, p.getHeight() - v.getBottom());
        v2.setImageDrawable(curLocation == null ?
                getResources().getDrawable(R.drawable.my_position_button_passive) :
                liveMapIsOn ? getResources().getDrawable(R.drawable.my_position_button_live) :
                        getResources().getDrawable(R.drawable.my_position_button_active));
        v2.setId(MY_POSITION_BUTTON_ID);
        v2.setClickable(true);
        v2.setOnClickListener(this);
        p.addView(v2, lp2);

        walk = new Walk(this);
        walk.loadAndDraw(true); // Загрузка из базы и рисование - асинхронно, в отдельной thread
        setTitle(getResources().getString(R.string.walk_header) + walkId);
        map.setOnMapLongClickListener(this);
        map.setOnMapLoadedCallback(this);  // Здесь установим масштаб и фокус
        map.setOnMarkerClickListener(this);
        if (mode == MODE_RESUME) {
            enterResumeMode();  // Начинаем запись прогулки
        } else if (mode == MODE_ACTIVE) {
            returnIfHasGone();
        } else if (mode == MODE_PASSIVE) {
            if (whereAmIIsOn) {
                switchWhereAmI(true);
            }
        }
//        map.setMyLocationEnabled(true);
//        map.setOnMyLocationButtonClickListener(this);
//        map.setOnMyLocationClickListener(this);

    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // Вызывается между onPause и onStop
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
        savedInstanceState.putBoolean("showAccuracyCircle", showAccuracyCircle);
        savedInstanceState.putBoolean("showAFs", showAFs);
        savedInstanceState.putLong("lastSaveTime", lastSaveTime = System.currentTimeMillis());
        savedInstanceState.putInt("mapType", mapType);
        savedInstanceState.putString("textFilePath", textFilePath);
        savedInstanceState.putParcelable("curLocation", curLocation);
        savedInstanceState.putBoolean("whereAmIIsOn", whereAmIIsOn);
        savedInstanceState.putBoolean("liveMapIsOn", liveMapIsOn);
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
            case R.id.action_pause:
                enterPassiveMode();
                return true;
            case R.id.action_resume:
                enterResumeMode();
                return true;
            case R.id.action_gallery:
                afInGalleryNumber = showGallery(this, walk, this, null, afInGalleryNumber,
                        mode, false);
                return true;
            case R.id.action_where_am_i:
                switchWhereAmI(!whereAmIIsOn);
                updateOptionsMenu();
                return true;
            case R.id.action_live_map:
                switchLiveMap(!liveMapIsOn);
                updateOptionsMenu();
                return true;
            case R.id.action_show_entire_route:
                showEntireRoute();
                return true;
            case R.id.action_show_afs:
                showAFs = !showAFs;
                recreate();
                return true;
            case R.id.action_show_accuracy_circle:
                showAccuracyCircle = !showAccuracyCircle;
                drawAccuracyCircle();
                updateOptionsMenu();
                return true;
            case R.id.action_map_type_satelite:
                map.setMapType(
                        mapType = mapType == GoogleMap.MAP_TYPE_HYBRID ?
                                GoogleMap.MAP_TYPE_NORMAL : GoogleMap.MAP_TYPE_HYBRID);
                return true;
            case R.id.action_map_type_terrain:
                map.setMapType(
                        mapType = mapType == GoogleMap.MAP_TYPE_TERRAIN ?
                                GoogleMap.MAP_TYPE_NORMAL : GoogleMap.MAP_TYPE_TERRAIN);
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
    public void onMapLoaded() { // Не раньше, чем будет показана !!!
        Utils.logD(TAG, "onMapLoaded");

        walk.mapIsLoaded = true; // Просто понимаем флаг - loadAndDraw увидит и вернет сюда просьбу установить zoom
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case WRITE_TEXT_REQUEST_CODE:
                if (true) { //resultCode==Activity.RESULT_OK  { // Редактор всегда дает 0, а не Activity.RESULT_OK=-1) {
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
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        Utils.logD(TAG, "onSupportNavigateUp");

        finish();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("ToGalleryActivity").putExtra("action", "finish"));
        return true;
    }

    @Override
    public void onBackPressed() {  // После повторного входа по Back выкидывает на рабочий стол - лечение
        Utils.logD(TAG, "onBackPressed");

        finish();
        if (!calledFrom.equals("MainActivity")) {
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

    void enterResumeMode() { // До момента нарисования первой новой точки
        Utils.logD(TAG, "enterResumeMode " + new Date());
        mode = MODE_RESUME;
        updateOptionsMenu();
        invalidateOptionsMenu(); // перерисует action bar

        addPoint(0, null, null, "enterResumeMode", false); // Старт сервиса walkRecorder !!!
    }

    void enterActiveMode() { // Вызывается из Walk после нарисования первой новой точки (Resume -> Active)
        Utils.logD(TAG, "enterActiveMode");

        if (mode == MODE_ACTIVE) { // На всякий...
            return;
        }
        mode = MODE_ACTIVE;
        updateOptionsMenu();
        invalidateOptionsMenu(); // перерисует action bar
        setZoomAndFocus(2);
        MainActivity.pleaseDo = MainActivity.pleaseDo + " refresh selected item";

        ImageView v = getSupportFragmentManager().findFragmentById(R.id.map).getView().
                findViewById(MY_POSITION_BUTTON_ID);
        v.setImageDrawable(liveMapIsOn ?
                getResources().getDrawable(R.drawable.my_position_button_live) :
                getResources().getDrawable(R.drawable.my_position_button_active));
        v.invalidate();
    }

    private void enterPassiveMode() {
        Utils.logD(TAG, "enterPassiveMode");

        modeInitial = mode;
        mode = MODE_PASSIVE;
        addPoint(0, null, null, "enterPassiveMode", true); // Добавь точку и умри
        updateOptionsMenu();
        invalidateOptionsMenu();
        for (int i = 0; i < curPosMarkers.capacity(); i++) {
            if (curPosMarkers.get(i) != null) { // Успел нарисовать
                MyMarker.killMarker(curPosMarkers, i);
            }
        }
        curPosMarker = null;
        curPosMarkerIndex = -1;
        showAccuracyCircle = false;
        drawAccuracyCircle();
        curLocation = null;
        whereAmIIsOn=false;

        ImageView v = getSupportFragmentManager().findFragmentById(R.id.map).getView().
                findViewById(MY_POSITION_BUTTON_ID);
        v.setImageDrawable(getResources().getDrawable(R.drawable.my_position_button_passive));
        v.invalidate();
    }

    private void updateOptionsMenu() {
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_photo).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_speech).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_text).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_video).setVisible(mode == MODE_ACTIVE);
            optionsMenu.findItem(R.id.action_pause).setVisible(mode != MODE_PASSIVE);
            optionsMenu.findItem(R.id.action_resume).setVisible(mode == MODE_PASSIVE &&
                    modeInitial != MODE_PASSIVE);  // ПРИостановленная новая прогулка
            optionsMenu.findItem(R.id.action_gallery).setVisible(mode != MODE_RESUME &&
                    pointsAreLoaded);
            optionsMenu.findItem(R.id.action_where_am_i).setVisible(mode == MODE_PASSIVE)
                    .setTitle(whereAmIIsOn ?
                            getResources().getString(R.string.action_turn_whereami_off) :
                            getResources().getString(R.string.action_turn_whereami_on));
            optionsMenu.findItem(R.id.action_live_map).setVisible(mode == MODE_ACTIVE || whereAmIIsOn)
                    .setTitle(liveMapIsOn ?
                            getResources().getString(R.string.action_turn_Live_map_off) :
                            getResources().getString(R.string.action_turn_live_map_on));
            optionsMenu.findItem(R.id.action_show_entire_route).setVisible(mode != MODE_RESUME);
            optionsMenu.findItem(R.id.action_show_accuracy_circle).setVisible(mode == MODE_ACTIVE)
                    .setTitle(showAccuracyCircle ?
                            getResources().getString(R.string.action_hide_accuracy_circle) :
                            getResources().getString(R.string.action_show_accuracy_circle));
            optionsMenu.findItem(R.id.action_show_afs)
                    .setTitle(showAFs ?
                            getResources().getString(R.string.action_hide_afs) :
                            getResources().getString(R.string.action_show_afs));
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
        try {
            startActivity(intent); // !!!
            addPoint(0, null, null, "startActivity RECORD_SOUND_ACTION", false);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getResources().getString(R.string.no_sound_recording_program),
                    Toast.LENGTH_LONG).show();
        }
    }

    void actionText() {
        Utils.logD(TAG, "actionText");

        String dir = Utils.createDirIfNotExists("TEXT");
        if (dir == null) {
            Toast.makeText(this,
                    String.format(
                            getResources().getString(R.string.format_could_not_create_directory),
                            "TEXT", Environment.getExternalStorageDirectory()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        textFilePath = dir + "/gfwalk" +
                (new SimpleDateFormat("_yyyyMMdd_HHmmss")).format(new Date()) + ".txt";
        try {
            new File(textFilePath).createNewFile();
        } catch (java.io.IOException e) {
            Toast.makeText(this,
                    String.format(
                            getResources().getString(R.string.format_could_not_create_file),
                            textFilePath),
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
        Intent intent = new Intent(this, WalkRecorder.class);
        intent.putExtra("walkId", walkId)
                .putExtra("debugInfo", debugInfo).putExtra("isLastPoint", isLastPoint);
        if (afKind > 0) {
            intent.putExtra("afKind", afKind)
                    .putExtra("afUri", afUri.toString()).putExtra("afFilePath", afFilePath);
        }
        startService(intent);
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
            Toast.makeText(activity, activity.getResources().getString(R.string.nothing_to_show),
                    Toast.LENGTH_SHORT).show();
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

            if (action.equals("showNotConnectedDialog")) { // GoogleApiClient не подключтлся к сервису
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
            } else if (action.equals("loadAndDraw")) {  // WalkRecoder создал новую точку (записал в базу) - рисуем ее
                walk.loadAndDraw(false);
            } else if (action.equals("drawCurPosMarker")) {
                curLocation = WalkRecorder.lastGoodLocation;
                curLocationIsBad = WalkRecorder.curLocationIsOK <= 0;
                drawCurPosMarker(curLocationIsBad);
            } else if (action.equals("setZoomAndFocus")) { // Из Walk
                setZoomAndFocus(1);
                if (curLocation != null) {
                    drawCurPosMarker(curLocationIsBad);
                }
                if (updateFromGalleryIntent != null) {
                    updateFromGallery(updateFromGalleryIntent);
                    updateFromGalleryIntent = null;
                }
//                drawPointInGalleryMarker(afInGalleryNumber);  // Точки уже загружены. Но артефакты еще нет!
                // Перенесено в Walk
                pointsAreLoaded = true;
                updateOptionsMenu();
            } else if (action.equals("toast")) {
                Toast.makeText(MapActivity.this,
                        intent.getStringExtra("text"),
                        intent.getIntExtra("duration", Toast.LENGTH_SHORT) == Toast.LENGTH_SHORT ?
                                Toast.LENGTH_SHORT : Toast.LENGTH_SHORT).show();
            } else if (action.equals("updateFromGallery")) {
                updateFromGallery(intent);
            } else if (action.equals("finish")) { // from Gallery when pressed Up
                finish();
                Intent intent2 = new Intent(context, MainActivity.class);  // В некотрых случаях вываливается в рабочий стол
                intent2.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // На всякий случай
                startActivity(intent2);
            } else if (action.equals("restartActivityIfFlagIsRaised")) {
                if (Utils.restartActivityIfFlagIsRaised(MapActivity.this)) {
                    return;
                }
            }
        }
    };

    void setZoomAndFocus(int callNumber) {  // 1 - Walk попросил после того, как загрузил старые точки
        // и MapIsLoaded, 2 -  из enterActiveMode'a, вызванного Walk'ом после того, как он нарисовал первую новую точку
        Utils.logD(TAG, "setZoomAndFocus " + callNumber + " " + zoomAndFocusAreSet);

        if (zoomAndFocusAreSet) {   // Не первый create
            if (callNumber == 1) {
                returnIfHasGone();
            } else if (callNumber == 2) {
                if (walk.points.size() > 0) { // Последнюю точку в фокус
                    animateCamera(CameraUpdateFactory.
                            newLatLng(Utils.loc2LatLng(walk.points.get(walk.points.size() - 1).location)));
                }
            }
            Utils.logD(TAG, "setZoomAndFocus 1");
            return;
        }
        if (walk.points.size() == 0) {
            Utils.logD(TAG, "setZoomAndFocus 2");
            return;
        } else if (walk.points.size() == 1) {// || mode != MODE_PASSIVE) { // Самое начало прогулки
            animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(
                    Utils.loc2LatLng(walk.points.get(walk.points.size() - 1).location), zoom0, 0, 0)));
            Utils.logD(TAG, "setZoomAndFocus 3");
        } else {
            showEntireRoute();
            Utils.logD(TAG, "setZoomAndFocus 4");
        }
//        zoomAndFocusAreSet = true;  Перенесено в animateCamera
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
                    null, null, null, -1, false, true);
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

            if (action.equals("stopRecording")) { // Из notification WalkRecorder
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
        if (mode == MODE_ACTIVE) {
            if (curPosMarker != null) {
                builder.include(curPosMarker.getPosition());
            }
        }
        LatLngBounds bounds;
        try {
            bounds = builder.build();
        } catch (Exception e) {
            return; // Ни одной точки нет
        }
        int padding = 50; // offset from edges of the map in pixels
        animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));  // Zoom и Focus. Animate иногда зависает :(
    }

    void drawCurPosMarker(boolean isBad) {
        Utils.logD(TAG, "drawCurPosMarker");

        if (!mapIsVisible) return;

        if (mode == MODE_PASSIVE && !whereAmIIsOn // Может быть
                || curLocation == null) { // На всякий...
            return;
        }
        boolean isInfoWindowShown = curPosMarker != null && curPosMarker.isInfoWindowShown();

        int curPosMarkerIndexNew = isBad ? MARKER_BAD_POSITION : getKindOfCurPosMarker();
        String title = Utils.fullInfStr(curLocation, walk.initialAltitude);
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
                    true, true);
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
/*
        if (marker.equals(curPosMarker)) {
            // 0 - ничего, 1 - info window, 2 - info window + accuracy circle, 3 - accuracy circle
            showAccuracyCircle=(curPosMarkerViewKind+1) % 4;

            if (curPosMarkerViewKind>0 && curPosMarkerViewKind<3) {
                curPosMarker.showInfoWindow();
            } else {
                curPosMarker.hideInfoWindow();
            }
            drawAccuracyCircle();
            return  true;
        }
*/
        return false;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == MY_POSITION_BUTTON_ID) {
            if (curLocation != null) {
                meToTheCenter();
            } else {
                showEntireRoute();
            }
        }
    }

    void returnIfHasGone() {  // Если я ушел за экран, возвращаем
        Utils.logD(TAG, "returnIfHasGone");

        StringBuilder s = new StringBuilder("");
        if (map == null || walk == null || mode == MODE_PASSIVE
                || !walk.mapIsLoaded && Utils.set(s, "!walk.mapIsLoaded")
                || curLocation == null && walk.points.size() == 0 &&
                Utils.set(s, "curLocation == null && walk.points.size()==0")
                || System.currentTimeMillis() - lastSaveTime < 5000 && // Не переворот
                Utils.set(s, "System.currentTimeMillis() - lastSaveTime < 5000")
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
                new Runnable() {
                    public void run() {
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
                                        zoomAndFocusAreSet = true;
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
                    }
                }, animationIsRunning, -1);
        animateCameraWaitingThreads.add(thread[0]);
        Utils.logD(TAG, "animateCamera: start " + thread[0].getId());
    }

    int getKindOfCurPosMarker() {
        // float speed = walkSettings.getFloat("recording_min_possible_speed",0.0f);  ClassCastException!!!
        float minSpeed = Float.parseFloat(walkSettings.getString("recording_min_possible_speed", "0"));
        if (minSpeed != 0 && (!curLocation.hasSpeed() || curLocation.getSpeed() < minSpeed) && turnCompasOn(true)) {
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

    void switchWhereAmI(boolean on) {
        Utils.logD(TAG, "switchWhereAmI "+on);

        whereAmIIsOn=on;

        curLocation = null;
        if (on) {
            if (googleApiClient==null) {
                googleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
 //                       .enableAutoManage(this, this)
                        .build();
            }
            if (googleApiClient.isConnected()) {
                onConnected(null);
            } else {
                googleApiClient.connect();
            }
        } else {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
//            googleApiClient = null;
            MyMarker.killMarker(curPosMarkers, 0);
            curPosMarkerIndex = -1;
        }
        ImageView v = getSupportFragmentManager().findFragmentById(R.id.map).getView().
                findViewById(MY_POSITION_BUTTON_ID);
        v.setImageDrawable(!on ?
                getResources().getDrawable(R.drawable.my_position_button_passive) :
                liveMapIsOn ? getResources().getDrawable(R.drawable.my_position_button_live) :
                getResources().getDrawable(R.drawable.my_position_button_active));
    }

    @Override
    public void onConnected(Bundle connectionHint) { // Google Api client
        Utils.logD(TAG, "onConnectedQQ");

        if (googleApiClient == null) { // 2 раза было !
            return;
        }

        int interval = 1;
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(interval * 1000);
        locationRequest.setFastestInterval(interval * 1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this);
    }

    public void onConnectionFailed(ConnectionResult result) {  // Никогда не видел
        Utils.logD(TAG, "onConnectionFailed, result: " + result.toString());

        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("DoInUIThread")
                        .putExtra("action", "showNotConnectedDialog")  // Выдаст диалог "Установите новую версию"
                        .putExtra("resultCode", result.getErrorCode())
                        .putExtra("result", result.toString()));
    }

    @Override
    public void onConnectionSuspended(int cause) {  // Сам восстановит
        Utils.logD(TAG, "onConnectionSuspended, cause=" + cause);
    }

    @Override
    public void onLocationChanged(Location location) {
        Utils.logD(TAG, "onLocationChanged "+location);

        boolean isFirst= curLocation==null; // Первое после включения
        if (isFirst) {
            curLocation = new Location("");
        }
        curLocation.set(location);
        drawCurPosMarker(false);
        if (isFirst) {
            meToTheCenter();
        }
    }
    void switchLiveMap(boolean on) {
        Utils.logD(TAG, "switchLiveMap " + on);

        liveMapIsOn=on;
        rotateMap(on);
        ImageView v = getSupportFragmentManager().findFragmentById(R.id.map).getView().
                findViewById(MY_POSITION_BUTTON_ID);
        v.setImageDrawable(liveMapIsOn ?
                getResources().getDrawable(R.drawable.my_position_button_live) :
                getResources().getDrawable(R.drawable.my_position_button_active));
        v.invalidate();
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
}