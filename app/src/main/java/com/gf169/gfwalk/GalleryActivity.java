package com.gf169.gfwalk;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.MediaStore;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Vector;

public class GalleryActivity extends AppCompatActivity {
    static final String TAG = "gfGalleryActivity";

    ArrayList<String> afParcels;
    ArrayList<String> pointStrs;
    ArrayList<Integer> afNumbers;
    Vector<Bitmap>[] bitmaps;

    Walk walk;
    int walkId;
    int mode;  // MapActivity
    int afNumber;
    int iconPosition;
    String deletedAFNumbers;
    String pointsToRedraw;
    boolean showComments;
    boolean isLongClick;

    Gallery gallery;
    ImageView bigPicture;
    TextView textPanel;
    TextView commentField;

    int bigPictureHeight=0;
    int curPosition;
    boolean fileHasGone=false;
    Menu optionsMenu;
    String comment;
    ContentValues values = new ContentValues();
    String calledFrom;

    boolean isSelectionMode;
    ArrayList<Boolean> markedItems;
    int markedCount;

    boolean isOnClickExecuted;

    static final int WRITE_TEXT_REQUEST_CODE=2;
    static final int SETTINGS_REQUEST_CODE=9;
    static final int NUMBER_OF_BITMAPS_FORMED_IN_ADVANCE = 2;
    static final DisplayMetrics METRICS=Resources.getSystem().getDisplayMetrics();

    SharedPreferences walkSettings;

    Bitmap bitmapNull = BitmapFactory.decodeResource(MyApplication.appContext.getResources(),
            R.drawable.bmp_null);
    Bitmap bitmapNull2 = BitmapFactory.decodeResource(MyApplication.appContext.getResources(),
            R.drawable.bmp_null);
//int m2 = 0; Тест - OutOfMemory

    @Override
    protected void onNewIntent(Intent intent) { // Повторный вход - из MapActivity
        Utils.logD(TAG, "onNewIntent");
        super.onNewIntent(intent);

        calledFrom=intent.getStringExtra("calledFrom");
        afNumber=intent.getExtras().getInt("afInGalleryNumber", 0);

        if (intent.getExtras().getInt("afSize", walk.AFs.size())>walk.AFs.size()) {// Появились новые точкм - active mode
            show(intent.getExtras());  // Появились новые артефакты - полностью перерисовываем
        } else {
            mode=intent.getExtras().getInt("mode", -1);
            for (int i=0; i<afNumbers.size(); i=i+1) {
                if (afNumbers.get(i)==afNumber) {
                    onGalleryItemClick(null, i, 0,
                            intent.getExtras().getBoolean("isLongClick", false));
                    return;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gallery);

        MyMarker.curActivity=this;
        Utils.raiseRestartActivityFlag(this.getLocalClassName(), false);

        Bundle extras=savedInstanceState;
        if (extras==null) {
            extras=getIntent().getExtras();
        }
        calledFrom=extras.getString("calledFrom");

        if (show(extras)) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    broadcastReceiver, new IntentFilter("ToGalleryActivity"));
        }
    }

    boolean show(Bundle extras) {
        walkId=extras.getInt("walkId", -1);
        if (walkId<0) {  // Надо !
            finish();
            return false;
        }

        walkSettings = SettingsActivity.getCurrentWalkSettings(this, walkId);

        mode=extras.getInt("mode", -1);
        afNumber=extras.getInt("afInGalleryNumber", 0);
        afParcels=extras.getStringArrayList("afParcels");
        pointStrs=extras.getStringArrayList("pointStrs");
        iconPosition=extras.getInt("iconPosition", -2);
        pointsToRedraw=extras.getString("pointsToRedraw", "");
        showComments=extras.getBoolean("showComments", true);
        isLongClick=extras.getBoolean("isLongClick", false);

        walk=new Walk(this,walkId);
        afNumbers = new ArrayList<>(afParcels.size());
        bitmaps = new Vector[2];
        bitmaps[0] = new Vector<>(afParcels.size()); // small
        bitmaps[1] = new Vector<>(afParcels.size()); // big
        for (int i=0; i<afParcels.size(); i++) {
            walk.AFs.add(walk.new AF(afParcels.get(i))); // !!! walk.new AF
            if (!walk.AFs.get(i).deleted) {  // Не удаленные!!!
                afNumbers.add(i);  // Этот массив пойдет в адаптер. Содержимое - номера AF в прогулке
                if (i==afNumber) {
                    curPosition=afNumbers.size()-1;
                }
                bitmaps[0].add(null);
                bitmaps[1].add(null);
            }
        }

        isSelectionMode = extras.getBoolean("isSelectionMode", false);
        if (isSelectionMode) {
            switchSelectionMode(true, false, extras.getString("markedItems", null));
        }

        if (iconPosition==-2) { // Не передан
            DB.dbInit(this);
            Cursor cursor=DB.db.query(DB.TABLE_WALKS, new String[]{DB.KEY_ICONAFID},
                    DB.KEY_ID+"="+walkId, null, null, null, null);
            cursor.moveToFirst();
            long i=cursor.getLong(cursor.getColumnIndex(DB.KEY_ICONAFID));
            for (int j=0; j<afNumbers.size(); j++) {
                if (walk.AFs.get(afNumbers.get(j)).afId==i) {
                    iconPosition=j;
                    if (afNumber==-2) {
                        curPosition=j;
                        afNumber=afNumbers.get(j);
                    }
                    break;
                }
            }
            cursor.close();
        }

        if (afNumber<0) {  // На всякий...
            curPosition=0;
            afNumber=afNumbers.get(0);
        }

        bigPicture=findViewById(R.id.imageviewBigPicture);
        bigPicture.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public boolean onSwipeLeft() {
                toNextAF(1);
                return true;
            }
            @Override
            public boolean onSwipeRight() {
                toNextAF(-1);
                return true;
            }
            @Override
            public boolean onClick(){
                viewAF(walk.AFs.get(afNumber));
                return true;
            }
        });

        textPanel = findViewById(R.id.textviewText);  // Event'ы обрабатывает bigPicture
        commentField = findViewById(R.id.textviewComment);
        commentField.setVisibility(showComments ? View.VISIBLE : View.INVISIBLE);

        gallery = findViewById(R.id.gallerySnapshotList);
        gallery.getLayoutParams().height=METRICS.heightPixels *
                Integer.parseInt(walkSettings.getString("gallery_ribbon_height","10")) / 100;
        gallery.setAdapter(new ImageAdapter());
        gallery.setOnItemClickListener((parent, view, position, id) -> {
            if (isSelectionMode) {
                setMarked((ImageView) view, !markedItems.get(position));
            } else {
                onGalleryItemClick(view, position, 0, true);
                        // walk.AFs.get(afNumbers.get(position)).kind == Walk.AFKIND_SPEECH);
            }
        });
        gallery.setOnItemLongClickListener((parent, view, position, id) -> {
            if (isSelectionMode) { // В selectionMode long click работает как обычный
                onGalleryItemClick(view, position, 0, false);
                return true;
            } else {
                return onGalleryItemLongClick(view);
            }
        });
        gallery.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onGalleryItemSelected(view, position);
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
//        gallery.setCallbackOnUnselectedItemClick();
        gallery.setSelection(curPosition);

        ViewTreeObserver vto = bigPicture.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                bigPicture.getViewTreeObserver().removeOnPreDrawListener(this);
                bigPictureHeight=bigPicture.getMeasuredHeight();
                // Только здесь известна высота !
                onGalleryItemClick(null, curPosition, 0, false); //isLongClick);  // longClick в карте
                isLongClick=false; // Имеет смысл только в первом запуске show
                return true;
            }
        });

        showComment();

        return true;
    }

    public class ImageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return afNumbers.size();
        }
        @Override
        public Object getItem(int position) {
            return position; // ???
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ImageView imageView = new ImageView(GalleryActivity.this);
            imageView.setTag(position); // position в адаптере
            drawAF(imageView, afNumbers.get(position), parent.getLayoutParams().height, 0,
                    position == curPosition, isSelectionMode ? markedItems.get(position) : false);
           return imageView;
        }
    }

    void onGalleryItemClick(View v, int position, int direction, boolean toPlay) { // Не в Selection mode
        Utils.logD(TAG, "onGalleryItemClick " + position + " " + direction + " " + v);

        isOnClickExecuted = true;

        if (walk.AFs.get(afNumbers.get(position)).deleted) {
            return;
        }
        if (curPosition!=position) {
            saveComment();
            ImageView view=gallery.findViewWithTag(curPosition);
            if (view!=null) { // Если картинка на экране, убираем рамку
                addTempOverlay(view, R.drawable.overlay_border_default);
            }
            curPosition=position;
            afNumber=afNumbers.get(curPosition);
            showComment();
            updateOptionsMenu();
        }
        gallery.setSelection(curPosition);
        Walk.AF af = walk.AFs.get(afNumber);
        setTitle(pointStrs.get(af.pointNumber));
        drawAF(bigPicture, afNumber, 0, direction, false, false);

        if (v==null) { // Swipe большой картинки
            v = gallery.findViewWithTag(curPosition);
        }
        if (v!=null) {
            addTempOverlay((ImageView) v, R.drawable.overlay_border_selected);
        }
        if (toPlay && // Видео и речь сразу показываем
                (af.kind==Walk.AFKIND_VIDEO || af.kind==Walk.AFKIND_SPEECH)) {
            viewAF(af);
        }
    }

    void onGalleryItemSelected(View v, int position) {  // Вызывается когда item переезжает в середину, если по клику - после onClick
/* Нет, не будем
        Utils.logD(TAG, "onGalleryItemSelected " + position);

        if (!isOnClickExecuted) {
            onGalleryItemClick(v, position, 0, false);
        }
        isOnClickExecuted = false;
*/
    }

    boolean onGalleryItemLongClick(View view) {  // Не в Selection mode
        Utils.logD(TAG, "onGalleryLongClick");

        switchSelectionMode(true, true, null);
        // setMarked((ImageView) view, true);

        return true;
    }

    void switchSelectionMode(boolean on, boolean withRedraw, String selectedItemsStr) {
        if (on == isSelectionMode)
            return;

        View v = findViewById(R.id.markedItemsCount);
        v.setVisibility(on ? View.VISIBLE : View.GONE);
        v.getRootView().invalidate();

        if (on) {
            markedItems = new ArrayList<>(afNumbers.size());
            markedCount = 0;
            if (selectedItemsStr != null) {
                for (String s2 : selectedItemsStr.split(" ")) {
                    markedItems.add(s2.startsWith("1"));
                    markedCount += markedItems.get(markedItems.size() - 1) ? 1 : 0;
                }
            } else {
                for (int i = markedItems.size(); i < afNumbers.size(); i++)
                    markedItems.add(false);
            }
            drawMarkedCount(false);
        }
        isSelectionMode = on;
        if (withRedraw)
            redrawGallery();
    }

    void setMarked(ImageView v, boolean isMarked) {
        int position = (Integer) v.getTag();
        if (isMarked == markedItems.get(position) ||
            walk.AFs.get(afNumbers.get(position)).deleted) {
            return;
        }
        markedCount += isMarked ? 1 : -1;
        markedItems.set(position, isMarked);
        drawMark(v, isMarked);
        drawMarkedCount(false);
    }

    void drawMark(ImageView v, boolean isMarked) {
        addTempOverlay(v, isMarked == false ? R.drawable.overlay_marked_false : R.drawable.overlay_marked_true);
    }

    void addTempOverlay(ImageView v, int DrawableResId) {
        Bitmap b = ((BitmapDrawable) v.getDrawable()).getBitmap();
        b = b.copy(Bitmap.Config.ARGB_8888, true);  // Сохраняем исходную
        b = MyMarker.addOverlayOnBitmap(b, this.getResources(), DrawableResId);
        v.setImageBitmap(b);
    }

    void drawMarkedCount(boolean isWarningNeeded) {
        TextView v = (TextView) findViewById(R.id.markedItemsCount);
        String s = String.format(getResources().getString(R.string.format_afs_marked_count), markedCount);
        int i = Color.BLACK;
        if (isWarningNeeded) {
            s += "   " + getResources().getString(R.string.format_afs_marked_deletion_warning);
            i = Color.RED;
        }
        v.setTextColor(i);
        v.setText(s);
    }

    void redrawGallery() {
        Utils.logD(TAG, "redrawGallery " + isSelectionMode);

        for (int i = 0; i < ((ViewGroup) gallery).getChildCount(); i++) {
            ImageView v = (ImageView) gallery.getChildAt(i);
            int position = (Integer) v.getTag();

            if (isSelectionMode) {
                if (!walk.AFs.get(afNumbers.get(position)).deleted) {
                    drawMark(v, false);
                }
            } else {
                drawAF(v, afNumbers.get(position),
                        0, 0, position == curPosition, false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        saveComment();
        savedInstanceState.putInt("walkId", walkId);
        savedInstanceState.putInt("mode", mode);
        savedInstanceState.putInt("afInGalleryNumber", afNumber);
        if (deletedAFNumbers != null) {  // Что-то удалили - udate'им .deleted
            afParcels = new ArrayList<>(walk.AFs.size());
            for (int k=0; k<walk.AFs.size(); k++) {
                afParcels.add(walk.AFs.get(k).toString());
            }
        }
        savedInstanceState.putStringArrayList("afParcels", afParcels);
        savedInstanceState.putStringArrayList("pointStrs", pointStrs);
        savedInstanceState.putInt("curPosition", curPosition);
        savedInstanceState.putInt("iconPosition", iconPosition);
        savedInstanceState.putString("pointsToRedraw", pointsToRedraw);
        savedInstanceState.putBoolean("showComments", showComments);
        savedInstanceState.putString("calledFrom", calledFrom);

        savedInstanceState.putBoolean("isSelectionMode", isSelectionMode);
        if (isSelectionMode) {
            StringBuilder s = new StringBuilder();
            for (boolean b : markedItems) s.append((b ? "1 " : "0 "));
            savedInstanceState.putString("markedItems", s.toString());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Utils.logD(TAG, "onCreateOptionsMenu");

        if (walkId<0) {  // Надо !
            return true;
        }
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.gallery_activity_actions, menu);
        optionsMenu=menu;
        updateOptionsMenu();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Utils.logD(TAG, "onOptionsItemSelected "+item.getTitle());

        switch (item.getItemId()) {
            case R.id.action_map:
                showWalkOnMap();
                break;
            case R.id.action_delete_af:
                deleteAF();
                break;
            case R.id.action_share_af:
                shareAF();
                break;
            case R.id.action_make_walk_icon:
                setWalkIcon(curPosition);
                break;
            case R.id.action_info:
                MainActivity.walkInfo(this, walkId, mode);
                return true;
            case R.id.action_show_comments:
                showComments=!showComments;
                commentField.setVisibility(showComments ? View.VISIBLE : View.INVISIBLE);
                item.setChecked(showComments);
                break;
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
    protected void onDestroy() {
        Utils.logD(TAG, "onDestroy");
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void finish() {
        Utils.logD(TAG, "finish " + afNumber);

        saveComment();
        if (afNumber>=0) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent("DoInUIThread")
                            .putExtra("action", "updateFromGallery")
                            .putExtra("afInGalleryNumber", afNumber));
        }
        super.finish();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case WRITE_TEXT_REQUEST_CODE:
                drawAF(bigPicture, afNumber, 0, 0, false, false);
                break;
            case SETTINGS_REQUEST_CODE:
                break;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {  // Шаг влево, шаг вправо - побег.
                                            // У Main'a android:launchMode="singleTop"
        Utils.logD(TAG, "onSupportNavigateUp");

        if (isSelectionMode) {
            switchSelectionMode(false, true, null);
            return true;
        }

        finish();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("DoInUIThread") // to MapActivity
                        .putExtra("action", "finish"));
        return true;
    }

    @Override
    public void onBackPressed() {  // После повторного входа по Back выкидывает на рабочий стол
        Utils.logD(TAG, "onBackPressed");

        if (isSelectionMode) {
            switchSelectionMode(false, true, null);
            return;
        }

        finish();
        if (!calledFrom.endsWith("MainActivity")) {
            Intent intent=new Intent(this, MapActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("afInGalleryNumber", afNumber)
                    .putExtra("calledFrom", "MainActivity"); // Чтобы вернулся в нее
            startActivity(intent);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }


    private BroadcastReceiver broadcastReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getStringExtra("action");
            Utils.logD(TAG, "Got a local message - "+action);

            if ("finish".equals(action)) { // from MapActivity when pressed Up
                finish();
                Intent intent2=new Intent(context,MainActivity.class);  // В некотрых случаях вываливается в рабочий стол
                intent2.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // На всякий случай
                startActivity(intent2);
            }
        }
    };

    void updateOptionsMenu() {
        if (optionsMenu!=null) {
            optionsMenu.findItem(R.id.action_show_comments)
                    .setChecked(showComments);
        }
    }

    void drawAF(ImageView imageView, int afNumber, int height, int direction,
                boolean isSelected, boolean isMarked) {
        Utils.logD(TAG, "drawAF " + imageView.getTag() + " " + afNumber + " " + height + " "
                + direction + " " + isSelected + " " + isMarked);

        if (height<=0) height=imageView.getHeight();
        Utils.logD(TAG, "Drawing "+(imageView==bigPicture ? "big" : "small") +
                " picture #"+afNumber+", height - "+height);

        Walk.AF af=walk.AFs.get(afNumber);

        if (imageView==bigPicture) {
            textPanel.setText(null);
            if (!af.deleted && af.kind==Walk.AFKIND_TEXT) {
                StringBuilder s = new StringBuilder();
                String s2;
                try {
                    BufferedReader reader=new BufferedReader(new InputStreamReader(
                            new FileInputStream(new File(af.filePath))));
                    while ((s2=reader.readLine())!=null) s.append(s2).append("\n");
                    reader.close();
                    textPanel.setText(s);
                    imageView.setImageBitmap(null);
                    return;
                } catch (Exception e)  {// И пойдет дальше - нарисует иконку с карандашем
                }
            }
        }

        int iKind = imageView==bigPicture ? 1 : 0;
        int position = afNumbers.indexOf(afNumber);
        if (bitmaps[iKind].get(position) == null) {
            formBitmap(af, height, imageView==bigPicture, position);
        } else {
            Utils.logD(TAG, "drawAF bitmap is " +
                    (bitmaps[iKind].get(position) == bitmapNull ? "beeing formed" : "formed before"));
            while (bitmaps[iKind].get(position) == bitmapNull) {} // Ждем пока нарисует
        }
        Bitmap bitmap = bitmaps[iKind].get(position);
        Utils.logD(TAG, "drawAF bitmap is ready - " + bitmap);

        int m = 0;
        int k = 1;
        for (int j = position + k; k < afNumbers.size(); j = position + k) {
            // Заранее формируем - по очереди в обе стороны
            k = k > 0 ? -k : -k + 1;
            if (j<0 || j >= afNumbers.size()) {
                continue;
            }

            int afNumber2 = afNumbers.get(j);
            if (!walk.AFs.get(afNumber2).deleted && bitmaps[iKind].get(j) == null) {
                int height2 = height;
                int j2 = j;
                new Thread(() -> {
                    formBitmap(walk.AFs.get(afNumber2), height2,
                            imageView == bigPicture, j2);
                }, "formBitmap" + afNumber2).start();
                if (bitmaps[iKind].get(j2) == bitmapNull) {
                    bitmaps[iKind].set(j2, null);
                    break;
                }

                if (m++ > NUMBER_OF_BITMAPS_FORMED_IN_ADVANCE) break;  // ToDo
            }
        }

        if (!af.deleted && new File(walk.AFs.get(afNumber).filePath).exists()) {
            if (af.kind==Walk.AFKIND_VIDEO) {
                if (imageView!=bigPicture) {
                    String s = getPlayDuration(af);
                    if (s != null) {
                        bitmap = MyMarker.addTextOnIcon(bitmap, s, 0, -1);
                    } else {
                        bitmap = MyMarker.addOverlayOnBitmap(bitmap, this.getResources(), R.drawable.overlay_play);
                    }
                }
            }
            if (af.kind==Walk.AFKIND_SPEECH) {
                if (imageView!=bigPicture) {
                    String s = getPlayDuration(af);
                    if (s != null) {
                        bitmap = MyMarker.addTextOnIcon(bitmap, s, 0, Color.WHITE);
                    }
                }
            }
            if (imageView==bigPicture) {
                fileHasGone=false;
            } else if (iconPosition>=0 && afNumber==afNumbers.get(iconPosition)) {
                bitmap=MyMarker.addTextOnIcon(bitmap,getResources().getString(R.string.af_walk_icon_sign),
                        4, Color.GREEN);
            }
        } else {
            bitmap=MyMarker.addTextOnIcon(bitmap,
                    af.deleted ? getResources().getString(R.string.af_deleted) :
                            getResources().getString(R.string.af_has_gone),
                    0, Color.RED);
            if (imageView==bigPicture) {
                fileHasGone=true;
                MainActivity.pleaseDo = MainActivity.pleaseDo + " refresh selected item"; // Чтобы пересчитал артефакты
            }
        }

        if (direction!=0) {
            Utils.logD(TAG, "drawAF animation started");
            switchImage(imageView, bitmap, direction);
        } else {
            imageView.setImageBitmap(bitmap);
        }

        if (imageView!=bigPicture) {
            addTempOverlay(imageView,
                isSelected ? R.drawable.overlay_border_selected : R.drawable.overlay_border_default);
            if (isSelectionMode && !af.deleted) {
                addTempOverlay(imageView,
                    isMarked ? R.drawable.overlay_marked_true : R.drawable.overlay_marked_false);
            }
        }

        Utils.logD(TAG, "drawAF ended");
    }

    void formBitmap(Walk.AF af, int height, boolean isBigPicture, int position) {
        Utils.logD(TAG, "formBitmap start " + isBigPicture + " " + position);

        int iKind = isBigPicture ? 1 : 0;
        if (bitmaps[iKind].get(position) != null) {  // На всякий ...
            return;
        }
        bitmaps[iKind].set(position, bitmapNull);  // Признак - в процессе формирования

        Bitmap bitmap = null;
        try {
            if (af.kind == Walk.AFKIND_SPEECH && isBigPicture) {
                bitmap = MyMarker.decodeBitmap(getResources(), R.drawable.ic_point_speech2,   // Без рамки
                        0, null, null, height, false);
            } else if ((af.kind==Walk.AFKIND_PHOTO || af.kind==Walk.AFKIND_VIDEO) && !af.deleted) {
                bitmap=MyMarker.decodeBitmap(null, 0,
                        af.kind, af.uri, af.filePath, height, isBigPicture);
            }
            if (bitmap==null) {
                bitmap=MyMarker.decodeBitmap(getResources(), MyMarker.mmoA[af.kind].iconResources[0],
                        0, null, null, height,false);
            }
/*
m2++;
Utils.logD(TAG, "qqqq "+ (isBigPicture ? 1 : 0) + " " + position + " " + m2);
if (m2 % 15 == 0) {
    m2++;
    m2 += 1/0;
}
*/
        } catch(OutOfMemoryError e) {
//        } catch(Exception e) {
            for (int i = 0; i < bitmaps[0].size(); i++) {
                for (int j = 0; j <2 ; j++) {
                    Bitmap b = bitmaps[j].get(i);
                    if (b == bitmapNull) continue;
                    if (b == null) continue;
                    if (gallery.findViewWithTag(i) != null) continue; // Фигурирет ни в каком-то view
                    if (j == 1 && ((BitmapDrawable) bigPicture.getDrawable()).getBitmap() == b) continue;

                    bitmaps[j].set(i, null);
                    b.recycle();
//Utils.logD(TAG, "qqqq2 "+ i + " " + j);
                }
            }
            formBitmap(af, height, isBigPicture, position); // Еще раз
        } catch(Exception e) { // ???
            bitmap = bitmapNull2;
        }

        bitmaps[iKind].set(position, bitmap);
        Utils.logD(TAG, "formBitmap stop  " + isBigPicture + " " + position);
    }

    void switchImage(final ImageView imageView, final Bitmap bitmap, int dir) {
        final Animation anim_out = AnimationUtils.loadAnimation(this,
                dir>0 ? R.anim.slide_out_left : R.anim.slide_out_right);
        final Animation anim_in  = AnimationUtils.loadAnimation(this,
                dir>0 ? R.anim.slide_in_right : R.anim.slide_in_left);
        anim_out.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) {
                imageView.setImageBitmap(bitmap);
                anim_in.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationRepeat(Animation animation) {}
                    @Override public void onAnimationEnd(Animation animation) {}
                });
                imageView.startAnimation(anim_in);
            }
        });
        imageView.startAnimation(anim_out);
        // Utils.logD(TAG, "qqqq "+anim_in.getDuration() + " " + anim_out.getDuration());
        // config_shortAnimTime - 200
    }

    void viewAF(Walk.AF af) {
        Utils.logD(TAG, "viewAF "+af);

        if (fileHasGone) return;

        Intent intent=new Intent();
        if (af.kind==Walk.AFKIND_TEXT) {
//            intent.setAction(android.content.Intent.ACTION_EDIT);
//            intent.setDataAndType(Uri.parse(af.uri),"text/plain");
            intent = new Intent(this, EditTextActivity.class);
            intent.putExtra("textFilePath", af.filePath);
            startActivityForResult(intent, MapActivity.WRITE_TEXT_REQUEST_CODE);
        } else {
            intent.setAction(android.content.Intent.ACTION_VIEW);
            Uri uri = Uri.parse(af.uri);
            intent.setDataAndType(uri, getContentResolver().getType(uri));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }
    }

    void showComment() {
        DB.dbInit(this);
        Cursor cursor = DB.db.query(DB.TABLE_AFS, new String[]{DB.KEY_AFCOMMENT},
                DB.KEY_AFID+"="+walk.AFs.get(afNumber).afId,
                null, null, null,null);
        cursor.moveToFirst();
        comment=cursor.getString(cursor.getColumnIndex(DB.KEY_AFCOMMENT));
        cursor.close();
        comment=Utils.nvl(comment, "");
        commentField.setText(comment);


        ImageSpan span = new ImageSpan(this,R.drawable.hint_write,
                ImageSpan.ALIGN_BOTTOM);
        SpannableStringBuilder ssb = new SpannableStringBuilder("   ");  // !!!
        ssb.setSpan(span,0,1, Spannable.SPAN_INCLUSIVE_INCLUSIVE );
        commentField.setHint(ssb);


    }

    void saveComment() {
        if (comment!=null && !comment.equals(commentField.getText().toString())) {
            values.put(DB.KEY_AFCOMMENT, (comment=commentField.getText().toString()));
            DB.db.update(DB.TABLE_AFS, values,
                    DB.KEY_AFID+"="+walk.AFs.get(afNumber).afId, null);
        }
        if (commentField!=null) {
            commentField.clearFocus();
        }
    }

    boolean toNextAF(int dir) {
        for (int i=curPosition+dir;
             i>=0 && i<afNumbers.size(); i=i+dir) {
            if (!walk.AFs.get(afNumbers.get(i)).deleted) {
                onGalleryItemClick(null, i, dir, false);
                return true;
            }
        }
        return false;
    }

    void deleteAF() {
        if (isSelectionMode && markedCount==0)
            return;

        PopupMenu popup=new PopupMenu(this,gallery);
        popup.getMenuInflater()
                .inflate(R.menu.gallery_activity_delete_modes, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                deleteAF2(item.getItemId()==R.id.mode2);
                return true;
            }
        });
        popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
            public void onDismiss(PopupMenu popup) {
                if (isSelectionMode && markedCount>1) {
                    drawMarkedCount(false);
                }
            }
        });
        popup.show();

        if (isSelectionMode && markedCount>1) {
            drawMarkedCount(true);
        }
    }

    void deleteAF2(boolean toDeleteFile) {
        deletedAFNumbers = "";

        if (isSelectionMode) {
            for (int i=0; i<markedItems.size(); i++) {
                if (markedItems.get(i)) {
                    deleteAF3(afNumbers.get(i), toDeleteFile);
                    deletedAFNumbers += afNumbers.get(i) + " ";
                }
            }
            switchSelectionMode(false, true, null);
        } else {
            deleteAF3(afNumber, toDeleteFile);
            drawAF((ImageView) gallery.findViewWithTag(curPosition), afNumber,
                    0, 0, true, true);
            drawAF(bigPicture, afNumber, 0, 0, false, false);
            deletedAFNumbers = afNumber + "";
        }

        if (iconPosition>=0 && walk.AFs.get(afNumbers.get(iconPosition)).deleted) {
            iconPosition=-1;
            setWalkIcon(iconPosition);  // Записываем в базу
        }
        if (walk.AFs.get(afNumbers.get(curPosition)).deleted) {
            if (!toNextAF(1) && !toNextAF(-1)) {
                Toast.makeText(this, getResources().getString(R.string.nothing_to_show),
                        Toast.LENGTH_SHORT).show();
                afNumber = -1;
            }
        }
                // Update'им карту
        String deletedAFPointNumbers=" ";
        for (String s : deletedAFNumbers.split(" ")) {
            int afNumber = Integer.parseInt(s);

            String s2 = walk.AFs.get(afNumber).pointNumber + ":" +
                        walk.AFs.get(afNumber).kind + " ";
            if (!deletedAFPointNumbers.contains(" " + s2))
                deletedAFPointNumbers += s2;
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("DoInUIThread")
                    .putExtra("action", "updateFromGallery")
                    .putExtra("deletedAFNumbers", deletedAFNumbers)
                    .putExtra("deletedAFPointNumbers", deletedAFPointNumbers.substring(1))
                    .putExtra("afInGalleryNumber", afNumber)
                    );

        MainActivity.pleaseDo=MainActivity.pleaseDo+" refresh selected item";
        if (afNumber<0) { // Все удалили
            finish();
        }
    }

    void deleteAF3(int afNumber, boolean toDeleteFile) {
        if (toDeleteFile) {
            new File(walk.AFs.get(afNumber).filePath).delete();
            if (walk.AFs.get(afNumber).kind!=Walk.AFKIND_TEXT) {
                getContentResolver().delete(Uri.parse(walk.AFs.get(afNumber).uri),null,null);
            }
        }
        ContentValues values=new ContentValues();
        values.put(DB.KEY_AFDELETED, true);
        DB.db.update(DB.TABLE_AFS, values, DB.KEY_AFID+"="+walk.AFs.get(afNumber).afId, null);

        walk.AFs.get(afNumber).deleted=true;

        if (toDeleteFile) { // Нарисует фотоаппарат со словом Удален, иначе только добавит слово
            int i = afNumbers.indexOf(afNumber);
            bitmaps[0].set(i, null);
            bitmaps[1].set(i, null);
        }

        if (iconPosition>=0 && afNumber==afNumbers.get(iconPosition)) {
            Toast.makeText(this,getResources().getString(R.string.warning_icon_delete),
                    Toast.LENGTH_LONG).show();
        }
    }

    void shareAF() {
        Intent sendIntent = new Intent();
        if (isSelectionMode) {
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Uri> uris = new ArrayList<>();
            for (int i=0; i<markedItems.size(); i++) {
                if (markedItems.get(i)) {
                    uris.add(Uri.parse(walk.AFs.get(afNumbers.get(i)).uri));
                }
            }
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris);
//            sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.walk_header) + walkId);
        } else {
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(walk.AFs.get(afNumber).uri));
        }
        sendIntent.setType("*/*");
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);

        switchSelectionMode(false, true, null);
    }

    void setWalkIcon(int position) {
        View view;
        if (iconPosition>=0 && // Стираем слово
                (view=gallery.findViewWithTag(iconPosition))!=null) { // картинка на экране
            int i=iconPosition;
            iconPosition=-1;
            drawAF((ImageView)view, afNumbers.get(i), 0, 0, false, false);
        }
        iconPosition=position;
        if (iconPosition>=0) { // Пишем слово
            drawAF((ImageView) gallery.findViewWithTag(iconPosition),
                    afNumbers.get(iconPosition), 0, 0, true, false);
        }
        ContentValues values=new ContentValues();
        values.put(DB.KEY_ICONAFID,
                iconPosition<0 ? -1 : walk.AFs.get(afNumbers.get(iconPosition)).afId);
        values.put(DB.KEY_ICON,"");  // Признак необходимости сформировать заново
                DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID + "=" + walkId, null);
        MainActivity.pleaseDo=MainActivity.pleaseDo+" refresh selected item";
    }

    void showWalkOnMap() {
        DB.dbInit(this);
        Cursor cursor=DB.db.query(DB.TABLE_WALKS, new String[]{DB.KEY_TIMEZONE},
                DB.KEY_ID+"="+walkId, null, null, null, null);
        cursor.moveToFirst();
        String timeZoneId=cursor.getString(cursor.getColumnIndex(DB.KEY_TIMEZONE));
        cursor.close();
        MainActivity.showWalkOnMap(this, walkId, MapActivity.MODE_PASSIVE,
                timeZoneId, false, afNumber);
    }

    @Override
    protected void onResume() {
        Utils.logD(TAG, "onResume");
        super.onResume();

        Utils.restartActivityIfFlagIsRaised(this);
    }

    String getPlayDuration(Walk.AF af) {
        long i = 0;
        Cursor cursor = getContentResolver().query(
                Uri.parse(af.uri),
                af.kind==Walk.AFKIND_VIDEO ?
                        new String[]{MediaStore.Video.VideoColumns.DURATION} :
                        new String[]{MediaStore.Audio.AudioColumns.DURATION},
                null, null, null);
        if (cursor.moveToNext()) {
            i = cursor.getInt(0);
        }
        cursor.close();

        if (af.kind == Walk.AFKIND_SPEECH && i == 0) {
            MediaPlayer mp = MediaPlayer.create(this, Uri.parse(af.uri));
            i = mp.getDuration();
            mp.release();
        }
        if (i > 0) return Utils.durationStr2(i);
        return null;
    }
}
