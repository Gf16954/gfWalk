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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
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

public class GalleryActivity extends AppCompatActivity {
    static final String TAG = "gfGalleryActivity";

    ArrayList<String> afParcels = new ArrayList<>();
    ArrayList<String> pointStrs=new ArrayList<>();
    ArrayList<Integer> afNumbers=new ArrayList<>();

    Walk walk;
    int walkId;
    int mode;
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
    Bitmap bitmap;
    Menu optionsMenu;
    String comment;
    ContentValues values = new ContentValues();
    String calledFrom;

    static final int WRITE_TEXT_REQUEST_CODE=2;
    static final int SETTINGS_REQUEST_CODE=9;

    final static DisplayMetrics METRICS=Resources.getSystem().getDisplayMetrics();

    SharedPreferences walkSettings;

    @Override
    protected void onNewIntent(Intent intent) { // Повторный вход - из MapActivity
        Utils.logD(TAG, "onNewIntent");

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

//        AirbrakeNotifier.register(this,
//                "f17762b5ea71e1af3bcf37ba0cb2a67c",
//                "", false);

        MyMarker.curActivity=this;
        Utils.curActivity=this;
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
        deletedAFNumbers=extras.getString("deletedAFNumbers", "");
        pointsToRedraw=extras.getString("pointsToRedraw", "");
        showComments=extras.getBoolean("showComments", true);
        isLongClick=extras.getBoolean("isLongClick", false);

        walk=new Walk(this,walkId);
        afNumbers.clear();
        for (int i=0; i<afParcels.size(); i++) {
            walk.AFs.add(walk.new AF(afParcels.get(i))); // !!! walk.new AF
            if (!walk.AFs.get(i).deleted) {
                afNumbers.add(i);
                if (i==afNumber) {
                    curPosition=afNumbers.size()-1;
                }
            }
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
        bigPicture=(ImageView) findViewById(R.id.imageviewPicture);
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

        textPanel=(TextView) findViewById(R.id.textviewText);  // Event'ы обрабатывает bigPicture

        commentField=(TextView) findViewById(R.id.textviewComment);
        commentField.setVisibility(showComments ? View.VISIBLE : View.INVISIBLE);

        gallery=(Gallery) findViewById(R.id.gallerySnapshotList);
        gallery.getLayoutParams().height=METRICS.heightPixels*
                Integer.valueOf(walkSettings.getString("gallery_ribbon_height","10"))/100;
        gallery.setAdapter(new ImageAdapter());
        gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                onGalleryItemClick(v, position, 0, true);
            }
        });
        ViewTreeObserver vto = bigPicture.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                bigPicture.getViewTreeObserver().removeOnPreDrawListener(this);
                bigPictureHeight=bigPicture.getMeasuredHeight();
                // Только здесь известна высота !
                onGalleryItemClick(null, curPosition, 0, isLongClick);
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
            drawAF(imageView, afNumbers.get(position), parent.getLayoutParams().height, 0,
                    position==curPosition);
            imageView.setTag(position);
            return imageView;
        }
    }
    public void onGalleryItemClick(View v, int position, int direction, boolean toPlay) {
        if (walk.AFs.get(afNumbers.get(position)).deleted) {
            return;
        }
        if (curPosition!=position) {
            saveComment();
            View view=gallery.findViewWithTag(curPosition);
            if (view!=null) { // Картинка на экране - стираем рамку
                drawAF((ImageView)view, afNumbers.get(curPosition), 0, 0, false);
            }
            curPosition=position;
            afNumber=afNumbers.get(curPosition);
            showComment();
            updateOptionsMenu();
        }
        gallery.setSelection(curPosition);
        setTitle(pointStrs.get(walk.AFs.get(afNumber).pointNumber));
        drawAF(bigPicture, afNumber, 0, direction, false);

        if (v==null) { // Swipe
            v = gallery.findViewWithTag(curPosition);
        }
        if (v!=null) {
            drawAF((ImageView) v, afNumber, 0, 0, true); // Добавляем рамку
        }
        if (toPlay &&// Видео и речь сразу показываем
                (walk.AFs.get(afNumber).kind==Walk.AFKIND_VIDEO ||
                        walk.AFs.get(afNumber).kind==Walk.AFKIND_SPEECH)) {
            viewAF(walk.AFs.get(afNumber));
        }
    }
    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        saveComment();
        savedInstanceState.putInt("walkId", walkId);
        savedInstanceState.putInt("mode", mode);
        savedInstanceState.putInt("afInGalleryNumber", afNumber);
        if (!deletedAFNumbers.equals("")) {
            afParcels.clear();
            for (int k=0; k<walk.AFs.size(); k++) {
                afParcels.add(walk.AFs.get(k).toString());
            }
        }
        savedInstanceState.putStringArrayList("afParcels", afParcels);
        savedInstanceState.putStringArrayList("pointStrs", pointStrs);
        savedInstanceState.putInt("curPosition", curPosition);
        savedInstanceState.putInt("iconPosition", iconPosition);
        savedInstanceState.putString("deletedAFNumbers", deletedAFNumbers);
        savedInstanceState.putString("pointsToRedraw", pointsToRedraw);
        savedInstanceState.putBoolean("showComments", showComments);
        savedInstanceState.putString("calledFrom", calledFrom);
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
            case R.id.action_make_walk_icon:
                setWalkIcon(curPosition);
                break;
            case R.id.action_info:
                MainActivity.walkInfo(this, walkId, mode);
                return true;
            case R.id.action_show_comments:
                showComments=!showComments;
                commentField.setVisibility(showComments ? View.VISIBLE : View.INVISIBLE);
                updateOptionsMenu();
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

        if (bitmap!=null) {
            bitmap.recycle();
        }
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
        switch (requestCode) {
            case WRITE_TEXT_REQUEST_CODE:
                drawAF(bigPicture, afNumber, 0, 0, false);
                break;
            case SETTINGS_REQUEST_CODE:
                break;
        }
    }
    @Override
    public boolean onSupportNavigateUp() {  // Шаг влево, шаг вправо - побег.
                                            // У Main'a android:launchMode="singleTop"
        Utils.logD(TAG, "onSupportNavigateUp");

        finish();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("DoInUIThread") // to MapActivity
                        .putExtra("action", "finish"));
        return true;
    }
    @Override
    public void onBackPressed() {  // После повторного входа по Back выкидывает на рабочий стол
        Utils.logD(TAG, "onBackPressed");

        finish();
        if (!calledFrom.equals("MainActivity")) {
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

            if (action.equals("finish")) { // from MapActivity when pressed Up
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
            optionsMenu.findItem(R.id.action_show_comments).setTitle(showComments ?
                    getResources().getString(R.string.action_hide_comments) :
                    getResources().getString(R.string.action_show_comments));
        }
    }
    void drawAF(ImageView imageView, int afNumber, int height, int direction, boolean withBorder) {
        if (height<=0) height=imageView.getHeight();
        Utils.logD(TAG, "Drawing "+(imageView==bigPicture ? "large" : "small") +
                " picture #"+afNumber+", height - "+height);

        Walk.AF af=walk.AFs.get(afNumber);

        bitmap=null;
        if (imageView==bigPicture) {
            textPanel.setText(null);
            if (!af.deleted && af.kind==Walk.AFKIND_TEXT) {
                String s="", s2;
                try {
                    BufferedReader reader=new BufferedReader(new InputStreamReader(
                            new FileInputStream(new File(af.filePath))));
                    while ((s2=reader.readLine())!=null) s+=s2+"\n";
                    reader.close();
                    textPanel.setText(s);
                    imageView.setImageBitmap(null);
                    return;
                } catch (Exception e) {
/*
                    Toast.makeText(this,   // И пойдет дальше - нарисует иконку с карандашем
                            String.format(
                                    getResources().getString(R.string.format_could_not_read_from_file),
                                    af.filePath), Toast.LENGTH_SHORT).show();
*/
                }
            } else if (af.kind==Walk.AFKIND_SPEECH) {  // Без рамки
                bitmap=MyMarker.decodeBitmap(getResources(), R.drawable.ic_point_speech2,
                        0, null, null, height,false);
            }
        }
/* TODO: Сделать так ! Перевернуть. И Out of memory ! Зачем ? Быстрее !? И прижать можно кверху (matrix)
// http://stackoverflow.com/questions/6075363/android-image-view-matrix-scale-translate
// http://stackoverflow.com/questions/3647993/android-bitmaps-loaded-from-gallery-are-rotated-in-imageview
        if (isBig && af.kind==Walk.AFKIND_PHOTO) {
            imageView.setImageURI(Uri.parse(af.uri));
            return;
        }
*/
        if (!af.deleted && (af.kind==Walk.AFKIND_PHOTO || af.kind==Walk.AFKIND_VIDEO)) {
            bitmap=MyMarker.decodeBitmap(null, 0,
                    af.kind, af.uri, af.filePath, height, imageView==bigPicture); // isBig !!!
        }
        if (bitmap==null) {
            bitmap=MyMarker.decodeBitmap(getResources(), MyMarker.mmoA[af.kind].iconResources[0],
                    0, null, null, height,false);
        }
            if (!af.deleted && new File(walk.AFs.get(afNumber).filePath).exists()) {
            if (af.kind==Walk.AFKIND_VIDEO) {
                if (imageView!=bigPicture) {
                    bitmap=MyMarker.addOverlayOnBitmap(bitmap, this.getResources(), R.drawable.ic_action_play);
                }
            }
            if (imageView==bigPicture) {
                fileHasGone=false;
            } else if (iconPosition>=0 && afNumber==afNumbers.get(iconPosition)) {
                bitmap=MyMarker.addTextOnIcon(bitmap,getResources().getString(R.string.af_walk_icon_sign),
                        4, Color.RED);
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
        if (withBorder) {
            bitmap=MyMarker.addOverlayOnBitmap(bitmap, getResources(), R.drawable.ic_gallery_border);
        }
        if (direction!=0) {
            switchImage(imageView, bitmap, direction);
        } else {
            imageView.setImageBitmap(bitmap);
        }
    }
    void switchImage(final ImageView imageView, final Bitmap bitmap, int dir) {
        final Animation anim_out = AnimationUtils.loadAnimation(this,
                dir>0 ? R.anim.slide_out_left : R.anim.slide_out_right);
        final Animation anim_in  = AnimationUtils.loadAnimation(this,
                dir>0 ? R.anim.slide_in_right : R.anim.slide_in_left);
        anim_out.setAnimationListener(new Animation.AnimationListener()
        {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation)
            {
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
            intent.setData(Uri.parse(af.uri));
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
        ImageSpan span = new ImageSpan(this,R.drawable.hint_write2,
                ImageSpan.ALIGN_BOTTOM);
        SpannableStringBuilder ssb = new SpannableStringBuilder("              ");
        ssb.setSpan(span,0,1,Spannable.SPAN_INCLUSIVE_INCLUSIVE );
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
        if (iconPosition>=0 && afNumber==afNumbers.get(iconPosition)) {
            Toast.makeText(this,getResources().getString(R.string.warning_icon_delete),
                    Toast.LENGTH_LONG).show();
        }
        PopupMenu popup=new PopupMenu(this,gallery);
        popup.getMenuInflater()
                .inflate(R.menu.gallery_activity_delete_modes, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                deleteAF2(item.getItemId()==R.id.mode2);
                return true;
            }
        });
        popup.show();
    }
    void deleteAF2(boolean toDeleteFile) {
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
        drawAF(bigPicture, afNumber, 0, 0, false);
        drawAF((ImageView) gallery.findViewWithTag(curPosition), afNumber, 0, 0, true);

        if (iconPosition>=0 && afNumber==afNumbers.get(iconPosition)) {
            iconPosition=-1;
            setWalkIcon(iconPosition);  // Записываем в базу
        }
        int deletedAFNumber=afNumber;
        if (!toNextAF(1) && !toNextAF(-1)) {
            Toast.makeText(this, getResources().getString(R.string.nothing_to_show),
                    Toast.LENGTH_SHORT).show();
            afNumber=-1;
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent("DoInUIThread")
                        .putExtra("action", "updateFromGallery")
                        .putExtra("deletedAFInGalleryNumber", deletedAFNumber)
                        .putExtra("afInGalleryNumber", afNumber));
        MainActivity.pleaseDo=MainActivity.pleaseDo+" refresh selected item";
        if (afNumber<0) {
            finish();
        }
    }
    void setWalkIcon(int position) {
        View view;
        if (iconPosition>=0 && // Стираем хвездочку
                (view=gallery.findViewWithTag(iconPosition))!=null) { // картинка на экране
            int i=iconPosition;
            iconPosition=-1;
            drawAF((ImageView)view, afNumbers.get(i), 0, 0, false);
        }
        iconPosition=position;
        if (iconPosition>=0) { // Рисуем хвездочку
            drawAF((ImageView) gallery.findViewWithTag(iconPosition),
                    afNumbers.get(iconPosition), 0, 0, true);
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
}
