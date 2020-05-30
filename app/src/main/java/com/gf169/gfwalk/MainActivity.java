package com.gf169.gfwalk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class MainActivity extends Activity {
    static final String TAG="gfMainActivity";

    static SimpleDateFormat dateFormat=new SimpleDateFormat();
    static TimeZone timeZone;

    static final String FILTER_STR_NOT_IN_BIN ="ifnull("+DB.KEY_DELETED+",0)=0";
    static final String FILTER_STR_IN_BIN ="ifnull("+DB.KEY_DELETED+",0)=1";
    String filterStr=FILTER_STR_NOT_IN_BIN;
    Bundle filterParms;

    ListView walklist;
    int walkId;
    int itemCount;
    int selectedPos=-1;
    View selectedView;
    Bitmap[] updatedIcons;
    boolean[] updatedIcons2;
    String[] updatedComments;
    String[] updatedStartPlaces;
    int firstVisiblePos;
    Menu optionsMenu;

    static final int SETTINGS_REQUEST_CODE=9;
    static String pleaseDo="";  // Просьбы других activity к этой сделать что-нибудь

    static Context appContext;
    SharedPreferences globalSettings;

    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate " + this);
        super.onCreate(savedInstanceState);

        appContext = getApplicationContext();
        Utils.ini(appContext);
        MyMarker.curActivity=this;
        WalkFilterDialogFragment.mainActivity=this;

        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            filterParms=savedInstanceState.getBundle("filterParms");
            filterStr=savedInstanceState.getString("filterStr");
            walkId=savedInstanceState.getInt("walkId");
            selectedPos=savedInstanceState.getInt("selectedPos");
            firstVisiblePos=savedInstanceState.getInt("firstVisiblePos");
        }

        globalSettings=SettingsActivity.getCurrentWalkSettings(this, -1);

        Utils.grantMeAllDangerousPermissions(this);
        DB.dbInit(this);
        condenseAFs();
        makeWalklist();
    }
    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Utils.logD(TAG, "onSaveInstanceState");

            super.onSaveInstanceState(savedInstanceState);
            savedInstanceState.putBundle("filterParms", filterParms);
            savedInstanceState.putString("filterStr", filterStr);
            savedInstanceState.putInt("walkId", walkId);
            savedInstanceState.putInt("selectedPos", selectedPos);
            savedInstanceState.putInt("firstVisiblePos", firstVisiblePos);
    }
    void makeWalklist() {
        Utils.logD(TAG, "makeWalklist");

        int[] arrayViewIDs;
        String[] arrayColumns;
        SimpleCursorAdapter adapter;

        walklist=(ListView) findViewById(R.id.listViewWalks);

        arrayViewIDs=new int[]{R.id.imageViewIcon, R.id.buttonWalkId,
                R.id.textViewString1, R.id.textViewString2, R.id.textViewString3};
        arrayColumns=new String[]{DB.KEY_ICON, DB.KEY_ID,
                DB.KEY_STARTTIME, DB.KEY_COMMENT, DB.KEY_STARTPLACE,
                DB.KEY_DURATION, DB.KEY_LENGTH, DB.KEY_TIMEZONE, DB.KEY_ICONAFID,
                DB.KEY_DELETED
        };
        final Cursor cursor=DB.db.query(DB.TABLE_WALKS, arrayColumns,
                filterStr, null, null, null, DB.KEY_ID+" DESC");
        adapter=new SimpleCursorAdapter(this, R.layout.listviewitem_walk, cursor,
                arrayColumns, arrayViewIDs, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);  // Асинхронный

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(final View view, final Cursor cursor, int columnIndex) {
                final int position=cursor.getPosition();
                String s;
                if (columnIndex==cursor.getColumnIndex(DB.KEY_ICON)) {
                    Utils.logD(TAG, "setViewValue "+position+" "+
                            cursor.getLong(cursor.getColumnIndex(DB.KEY_ID))+" "+view.getParent());
                    byte[] s2=null;
                    if (updatedIcons2[position]) {
                        ((ImageView) view).setImageBitmap(updatedIcons[position]);
                    } else {
                        s2=cursor.getBlob(columnIndex);
                        if (s2==null) { // Еще не сформирована
                            updatedIcons[position] =
                                formIcon(cursor.getLong(cursor.getColumnIndex(DB.KEY_ID)),
                                         cursor.getLong(cursor.getColumnIndex(DB.KEY_ICONAFID)));
                            updatedIcons2[position] = true; // Правильная иконка в updatedIcons
                            ((ImageView) view).setImageBitmap(updatedIcons[position]);
                        } else if (s2.length<=1) { // Артефактов нет - живет без иконки
                            ((ImageView) view).setImageDrawable(null);
                        } else { // Уже сформирована, в т.ч. пустая (если нет артефактов) - массив из одного символа 0
                            ((ImageView) view).setImageBitmap(
                                    BitmapFactory.decodeByteArray(s2, 0, s2.length));
                        }
                    }
                    View v=(View) view.getParent();
                    v.setTag(R.id.tag_walkIsDeleted,
                            cursor.getInt(cursor.getColumnIndex(DB.KEY_DELETED))==1 ? "*" : null);
                    v.setTag(R.id.tag_walkIsSelected,
                            position == selectedPos ? "*" : null);
                    paintItem(v, position == selectedPos);

                    if (updatedIcons[position]!=null | (s2!=null && s2.length>1)) {
                                                        // Если скобок нет, отваливается при s2=null
                        view.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                selectItem(position, (View) view.getParent());
                                MapActivity.showGallery(MainActivity.this,
                                        new Walk(MainActivity.this, walkId),
                                        null, null, -2, MapActivity.MODE_PASSIVE, false);
                            }
                        });
                    } else {
                        view.setClickable(false);  // Нужно!
                    };
                    return true;
                } else if (columnIndex==cursor.getColumnIndex(DB.KEY_ID)) {  // Кнопка с номером прогулки
                    s="#"+cursor.getLong(columnIndex);
                    ((Button) view).setText(s);
                    view.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            selectItem(position, (View) view.getParent());
                            firstVisiblePos=walklist.getFirstVisiblePosition();  // >-1 - признак ухода в диалог
                            walkInfo(MainActivity.this, walkId, MapActivity.MODE_PASSIVE); // В диалог
                        }
                    });
                    return true;
                } else if (columnIndex==cursor.getColumnIndex(DB.KEY_STARTTIME)) {
                    timeZone=TimeZone.getTimeZone(cursor.getString(
                            cursor.getColumnIndex(DB.KEY_TIMEZONE)));
                    dateFormat.setTimeZone(timeZone);
                    s=Utils.dateStr(cursor.getLong(columnIndex), dateFormat);
                    ((TextView) view).setText(s.split(" ")[0]);  // 14.11.15
                    return true;
                } else if (columnIndex==cursor.getColumnIndex(DB.KEY_COMMENT)) {
                    if ((s=updatedComments[position])==null) {
                        s=cursor.getString(columnIndex);
                        if (s.isEmpty()) {  // Продолжительность и длина - 2:13 5.456km
                            s=formDesc(cursor);
                        }
                    }
                    ((TextView) view).setText(s);
                    return true;
                } else if (columnIndex==cursor.getColumnIndex(DB.KEY_STARTPLACE)) {
                    ((TextView) view).setText(secondStr(
                            cursor.getString(cursor.getColumnIndex(DB.KEY_TIMEZONE)),
                            cursor.getLong(cursor.getColumnIndex(DB.KEY_STARTTIME)),
                            Utils.nvl(updatedStartPlaces[position],
                                    cursor.getString(cursor.getColumnIndex(DB.KEY_STARTPLACE)))));
                    return true;
                }
                return true;
            }
        });
        walklist.setAdapter(adapter);

        walklist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
                selectItem(position, v);
                showWalkOnMap(MainActivity.this, walkId, MapActivity.MODE_PASSIVE,
                        cursor.getString(cursor.getColumnIndex(DB.KEY_TIMEZONE)), false, -1);
            }
        });
        walklist.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> arg0, View v, int position, long arg3) {
                selectItem(position, v);
                firstVisiblePos = walklist.getFirstVisiblePosition();  // >-1 - признак ухода в диалог
                walkInfo(MainActivity.this, walkId, MapActivity.MODE_PASSIVE); // В диалог
                return true;
            }
        });

        itemCount=cursor.getCount();
        updatedIcons=new Bitmap[itemCount];
        updatedIcons2=new boolean[itemCount];
        updatedComments=new String[itemCount];
        updatedStartPlaces=new String[itemCount];
        selectedView=null;
        if (selectedPos>=itemCount) {  // После удаления
            selectedPos=itemCount-1;
        }

        if (optionsMenu!=null) {
            invalidateOptionsMenu();
        }
        findViewById(R.id.textViewEmptyScreen).setVisibility(
                itemCount==0 && filterStr== FILTER_STR_NOT_IN_BIN ? View.VISIBLE : View.INVISIBLE);
    }
    void paintItem(View view, boolean isSelected) {
        int color=getResources().getColor(isSelected ?
                R.color.main_selectedBackground : R.color.main_normalBackground);
        color=view.getTag(R.id.tag_walkIsDeleted)!=null ?  // А это по tag'у !
                color - getResources().getColor(R.color.main_deletedDelta) : color; // Все темнее
        view.setBackgroundColor(color);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Utils.logD(TAG, "onCreateOptionsMenu");

        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);

        if (filterStr.equals(FILTER_STR_NOT_IN_BIN)) {
            menu.findItem(R.id.action_filter).setIcon(R.drawable.ic_action_filter);
            menu.findItem(R.id.action_new).setVisible(true);
            menu.findItem(R.id.action_empty_bin).setVisible(false);
        } else if (filterStr.contains(FILTER_STR_IN_BIN)) {
            menu.findItem(R.id.action_filter).setIcon(R.drawable.ic_action_filter_red);
            menu.findItem(R.id.action_new).setVisible(false);
            menu.findItem(R.id.action_empty_bin).setVisible(itemCount>0);
        } else {
            menu.findItem(R.id.action_filter).setIcon(R.drawable.ic_action_filter_green);
            menu.findItem(R.id.action_new).setVisible(true);
            menu.findItem(R.id.action_empty_bin).setVisible(false);
        }
        optionsMenu=menu;
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id=item.getItemId();
        if (id==R.id.action_new) { // Новая прогулка
            if ((walkId=Walk.create())>0) {
                firstVisiblePos=0;
                selectedPos=0;
                pleaseDo="refresh entire list, restore selection";
                Utils.FALogEvent("new_walk", "walkId", walkId + "");

                showWalkOnMap(MainActivity.this, walkId, MapActivity.MODE_RESUME,
                        TimeZone.getDefault().getID(), false, -1);
            }
        }
        if (id==R.id.action_settings) {
            settings(this,-1);
            return true;
        }
        if (id==R.id.action_filter) {
            firstVisiblePos=walklist.getFirstVisiblePosition();  // >-1 - признак ухода в диалог
            walkFilter();
            return true;
        }
        if (id==R.id.action_empty_bin) {
            selectedPos=-1;
            WalkInfoDialogFragment.walkDelete(this, filterStr, walklist.getChildAt(0),
                    null, itemCount);
            return true;
        }
        if (id==R.id.action_help) {
            showHelp(this);
            return true;
        }
        if (id==R.id.action_privacy_policy) {
            showPrivacyPolicy(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onDestroy() {
        Utils.logD(TAG, "onDestroy " + this);
        super.onDestroy();
    }
    @Override
    protected void onStart() {
        Utils.logD(TAG, "onStart");
        super.onStart();

//        new Handler().removeCallbacksAndMessages(null); // Очищаем лишние нажатия
    }
    @Override
    protected void onResume() {
        Utils.logD(TAG, "onResume");

        super.onResume();

        if (!pleaseDo.equals("")) {
            doAfterReturn();
        }
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Utils.logD(TAG, "onWindowFocusChanged "+" "+hasFocus+
                " "+selectedPos+" "+firstVisiblePos+" "+pleaseDo);

        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus) {
//            firstVisiblePos=walklist.getFirstVisiblePosition();
        } else if (firstVisiblePos<0) { // Первый после onCreate
            // Android при перевороте оставляет на месте первую строку, и строка в конце может
            // пропасть из виду - возвращаем (делает ее первой. Или не первой, но показывает !)
            if (selectedPos>=0 &&
                    (selectedPos<walklist.getFirstVisiblePosition() ||
                            selectedPos>walklist.getLastVisiblePosition())) {
                walklist.setSelection(selectedPos);
            }
        } else { // После возврата из другой Activity или диалога
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);  // Именно здесь !
            if (!pleaseDo.equals("")) {
                doAfterReturn();
            }
            firstVisiblePos=-1;
        }
    }
    void doAfterReturn() {
        Utils.logD(TAG, "doAfterReturn "+pleaseDo);

        if (pleaseDo.contains("refresh entire list")) {
            if (!pleaseDo.contains("restore selection")) {
                selectedPos=-1;
            }
            makeWalklist();
            if (pleaseDo.contains("restore selection") &&
                    firstVisiblePos>=0) {
                walklist.setSelection(firstVisiblePos);
            }
            pleaseDo="";
            return;
        }
        if (pleaseDo.contains("refresh selected item")) {
            updateSelectedItem();
            pleaseDo="";
            return;
        }
    }
    public static void showWalkOnMap(final Activity activity, final int walkId, final int mode,
                                     final String timeZoneId, boolean withConfirmation,
                                     final int afInGalleryNumber) {
        if (withConfirmation) {
            long timeFinish=0;
            Cursor cursor=DB.db.query(DB.TABLE_WALKS,new String[]{DB.KEY_STARTTIME, DB.KEY_DURATION},
                    DB.KEY_ID+"="+walkId, null, null, null, null);
                cursor.moveToFirst();
                timeFinish=cursor.getLong(cursor.getColumnIndex(DB.KEY_STARTTIME))+
                        cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION));
            String s;
            double timeAfterFinish=(System.currentTimeMillis()-timeFinish)/1e+3;
            if (timeAfterFinish>3600*24*365) {
                s=String.format(activity.getResources().getString(R.string.format_walk_resume_warning_years),
                        timeAfterFinish/(3600*24*365));
            } else if (timeAfterFinish>3600*24*30.5) {
                s=String.format(activity.getResources().getString(R.string.format_walk_resume_warning_months),
                        timeAfterFinish/(3600*24*30.5));
            } else if (timeAfterFinish>3600*24) {
                s=String.format(activity.getResources().getString(R.string.format_walk_resume_warning_days),
                        timeAfterFinish/(3600*24));
            } else {
                showWalkOnMap(activity, walkId, mode, timeZoneId, false, afInGalleryNumber);
                return;
            }
            new AlertDialog.Builder(activity)
                    .setMessage(s)
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    showWalkOnMap(activity, walkId, mode,
                                            timeZoneId, false, afInGalleryNumber);
                                }
                            })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return;
        }
        Utils.logD(TAG, "showWalkOnMap: "+activity.getLocalClassName());
        if (activity.getLocalClassName().endsWith("MapActivity")) {
            ((MapActivity) activity).enterResumeMode();
        } else {
            Intent intent = new Intent(activity, MapActivity.class);
            intent.putExtra("walkId", walkId).putExtra("mode", mode)
                    .putExtra("timeZoneId", timeZoneId)
                    .putExtra("afInGalleryNumber", afInGalleryNumber)
                    .putExtra("calledFrom", activity.getLocalClassName());
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
        }
    }
    void updateSelectedItem() {
        Utils.logD(TAG, "updateSelectedItem "+selectedPos+" "+walkId);

        String[] arrayColumns=new String[]{DB.KEY_ICONAFID,
                DB.KEY_COMMENT, DB.KEY_DURATION, DB.KEY_LENGTH, DB.KEY_STARTPLACE};
        Cursor cursor=DB.db.query(DB.TABLE_WALKS, arrayColumns,
                DB.KEY_ID+"="+walkId, null, null, null, null);
        if (cursor.moveToFirst()) { // if на всякий случай
            updatedIcons[selectedPos] =
                    formIcon(walkId, cursor.getLong(cursor.getColumnIndex(DB.KEY_ICONAFID)));
            updatedIcons2[selectedPos] = true; // Правильная иконка в updatedIcons
            String s = cursor.getString(cursor.getColumnIndex(DB.KEY_COMMENT));
            if (s.isEmpty()) {
                s = formDesc(cursor);   // Продолжительность и длина
            }
            updatedComments[selectedPos] = s;
            updatedStartPlaces[selectedPos] = cursor.getString(cursor.getColumnIndex(DB.KEY_STARTPLACE));
            walklist.invalidateViews();  // Перерисует все. Только так :(
        }
        cursor.close();
    }
    void selectItem(int position, View view) {
        Utils.logD(TAG, "selectItem " + position);

        selectedPos=position;
        if (selectedPos>=0) {
            if (selectedPos>=walklist.getCount()) {
                selectedPos=walklist.getCount()-1;
            }
            Cursor cursor=(Cursor) walklist.getItemAtPosition(selectedPos);
            walkId=cursor.getInt(cursor.getColumnIndex(DB.KEY_ID));

            if (selectedView==null) {  // В начале единственный способ найти selectedView
                for (int i=0; i<=walklist.getChildCount(); i++) {
                    if (walklist.getChildAt(i)!=null &&
                            walklist.getChildAt(i).getTag(R.id.tag_walkIsSelected)!=null) {
                        selectedView=walklist.getChildAt(i);
                    }
                }
            }
            if (selectedView!=null && selectedView!=view) {
                paintItem(selectedView,false);
                selectedView.setTag(R.id.tag_walkIsSelected, null);
            }
            selectedView=view;
            if (selectedView!=null) {
                paintItem(selectedView,true);
                selectedView.setTag(R.id.tag_walkIsSelected, "*");  // Отмечаем чтобы потом найти и стереть
            }
        }
    }
    static void walkInfo (Activity activity, int walkId, int mode) {
        WalkInfoDialogFragment dlg=new WalkInfoDialogFragment();
        Bundle args=new Bundle();
        args.putInt("walkId", walkId);
        args.putInt("mode", mode);
        dlg.setArguments(args);
        dlg.show(activity.getFragmentManager(), "dlg");
    }
    void walkFilter () {
        DialogFragment dlg=new WalkFilterDialogFragment();
        dlg.setArguments(filterParms);
        dlg.show(getFragmentManager(), "dlg");
    }
    static void settings(Activity activity, int walkId) {
        Intent intent=new Intent(activity, SettingsActivity.class);
        String preferencesFileName=walkId<0 ?
            activity.getPackageName() + "_preferences" : "CurrentWalk";
        intent.putExtra("walkId", walkId).putExtra("preferencesFileName", preferencesFileName);
        activity.startActivityForResult(intent, SETTINGS_REQUEST_CODE);
    }
    @Override protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SETTINGS_REQUEST_CODE:
                break;
        }
    }
    Bitmap formIcon(long walkId, long afId) {
        Utils.logD(TAG,"formIcon "+walkId+" "+afId);

        Bitmap bitmap=null;
        int nAFs=0;  // Общее число артефактов

        Cursor cursor;
        cursor=DB.db.query(DB.TABLE_AFS,
                new String[]{DB.KEY_AFID,DB.KEY_AFKIND,DB.KEY_AFURI,DB.KEY_AFFILEPATH},
//                             DB.KEY_AFDELETED},
                DB.KEY_AFWALKID+"="+walkId+" AND "+DB.KEY_AFDELETED+"=0",
                null,null,null,
                DB.KEY_AFID);
        boolean isFirst=true;
        while (isFirst && cursor!=null && cursor.moveToFirst() ||
                !isFirst && cursor.moveToNext()) {
            String s=cursor.getString(cursor.getColumnIndex(DB.KEY_AFFILEPATH));
            if (s!=null && !(new File(s).exists())) {
                continue;
            }
            isFirst=false;

            if (bitmap==null) {
                if (afId>=0 && afId==cursor.getInt(cursor.getColumnIndex(DB.KEY_AFID)) || afId<0) {
                    afId=cursor.getInt(cursor.getColumnIndex(DB.KEY_AFID));
                    if ((cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND))==Walk.AFKIND_PHOTO ||
                            cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND))==Walk.AFKIND_VIDEO)
//                            && (new File(cursor.getString(cursor.getColumnIndex(DB.KEY_AFFILEPATH))).exists())) {
                            && true) {
                        bitmap = MyMarker.decodeBitmap(null, 0,
                                cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND)),
                                cursor.getString(cursor.getColumnIndex(DB.KEY_AFURI)),
                                cursor.getString(cursor.getColumnIndex(DB.KEY_AFFILEPATH)),
                                getResources().getDimensionPixelSize(R.dimen.listviewitem_height1), false);
                        if (cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND))==Walk.AFKIND_VIDEO) {
                            bitmap=MyMarker.addOverlayOnBitmap(bitmap, this.getResources(),
                                    R.drawable.ic_action_play);
                        }
                    } else {
                        bitmap = MyMarker.decodeBitmap(getResources(),
                                MyMarker.mmoA[cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND))].
                                        iconResources[0],
                                0, null, null,
                                getResources().getDimensionPixelSize(R.dimen.listviewitem_height1),
                                false);
                    }
                }
            }
            nAFs++;
        }
        cursor.close();

        ContentValues values=new ContentValues();
        if (bitmap==null) {
            values.put(DB.KEY_ICON, ""); // Массив длиной 1, первый элемент - 0 !
            values.put(DB.KEY_ICONAFID, -1);
            DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID+"="+walkId, null);
            return null;
        }
        if (nAFs>0) {   // Добавляем число артефактов
            bitmap=MyMarker.addTextOnIcon(bitmap, ""+nAFs, 1, -1);  // В левый верхний угол контрастным цветом
        }
        // Сохраняем сформированную иконку
        ByteArrayOutputStream stream=new ByteArrayOutputStream(Utils.byteSizeOf(bitmap));
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
        values.put(DB.KEY_ICON, stream.toByteArray());
        values.put(DB.KEY_ICONAFID, afId);
        DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID+"="+walkId, null);
        return bitmap;
    }
    void condenseAFs() {
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        int i=DB.db.delete(DB.TABLE_AFS, DB.KEY_AFDELETED+"=1", null);
                    }
                }, "gfCondenseAFs").start();
    }
    String formDesc(Cursor cursor) {
        return Walk.formTotalStr(globalSettings,
                cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION)),
                cursor.getLong(cursor.getColumnIndex(DB.KEY_LENGTH)),
                0,0,
                cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION)), // Чтобы не писал скорость
                0,0,0,null);
    }
    static String secondStr(String timeZoneId, Long startTime, String startPlace) {
        String s=Utils.timeStr(startTime,TimeZone.getTimeZone(timeZoneId));
        if (startPlace!=null && !startPlace.contains("NoAddress")) {
            s+=" "+startPlace;
        }
        return s;
    }
    static void showHelp(Activity activity) {
        Intent intent=new Intent(activity, HelpActivity.class);
        activity.startActivity(intent);
    }
    static void showPrivacyPolicy(Activity activity) {
        Uri uri = Uri.parse(activity.getResources().getString(R.string.url_privacy_policy));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        activity.startActivity(intent);
    }
}
/* Сохранение базы на локальный комп. Файл будет виден только в Android Device Monitor'e в каталоге
 mnt\shell\emulated\0 (в Explorer'e не виден !), откуда его можно pull. В манифесте должно быть
     <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
Utils.copyFile("/data/data/com.gf.gfwalk/databases/db",
        Environment.getExternalStorageDirectory().toString()+"/db.db3");
*/
