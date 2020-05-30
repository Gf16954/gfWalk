/**
 * Created by gf on 20.08.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class WalkInfoDialogFragment extends DialogFragment implements View.OnClickListener {
    static final String TAG = "gfWalkInfoDialogFragmnt";

    Activity activity;  // Кто вызвал
    MainActivity mainActivity;
    MapActivity mapActivity;
    GalleryActivity galleryActivity;
    int walkId; // Присваивает вызвавший
    int mode;   // Присваивает вызвавший

    String[] arrayColumns=new String[]{DB.KEY_ICON, DB.KEY_ID,
            DB.KEY_STARTTIME, DB.KEY_COMMENT, DB.KEY_STARTPLACE,
            DB.KEY_DURATION, DB.KEY_LENGTH, DB.KEY_DURATIONNETTO, DB.KEY_LENGTHNETTO,
            DB.KEY_TIMEZONE, DB.KEY_ICONAFID, DB.KEY_DELETED
    };
    Cursor cursor;
    View v;
    String commentOld;
    boolean isCancelled;

    SimpleDateFormat dateFormat=new SimpleDateFormat();
    TimeZone timeZone;

/*
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.textViewComment2).requestFocus();
    }
*/
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                               Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreateView");

        isCancelled=false;

        activity=getActivity();
        Bundle args=getArguments();
        walkId=args.getInt("walkId");
        mode=args.getInt("mode");

        if (activity.getLocalClassName().contains("Map")) {
            mapActivity=(MapActivity) activity; // Остальные остаются null
        } else if (activity.getLocalClassName().contains("Gallery")) {
            galleryActivity=(GalleryActivity) activity;
        } else {
            mainActivity=(MainActivity) activity;
        }

        Resources res=getResources();
        getDialog().setTitle(" " + res.getString(R.string.walk_header) + walkId);

//        getDialog().getWindow().setBackgroundDrawableResource(R.drawable.background_info);
//  Пропадает тень :(
        TextView t=(TextView) getDialog().findViewById(android.R.id.title);  // Заголовок рихтуем здесь
        if (t!=null) { // Андроид сам может выкинуть если мало места
            t.setBackgroundColor(res.getColor(R.color.walkinfo_color0));
            t.setTextColor(res.getColor(R.color.walkinfo_color1));
//            View titleDivider = getDialog().findViewById(android.R.id.titleDivider);
//  Не компилируется !!!
            View titleDivider = getDialog().findViewById(
                    res.getIdentifier("titleDivider", "id", "android"));
            if (titleDivider != null) {
                titleDivider.setBackgroundColor(res.getColor(R.color.walkinfo_color0));
            }
//  А остальное красится в layout'e
        }
        int screenSize=(res.getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK);
        if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
            screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL && t!=null) { // Андроид сам может выкинуть если мало места
                t.getLayoutParams().height=Utils.dpyToPx(20);
                t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            }
        }

        v = inflater.inflate(R.layout.fragment_walkinfo, null);

        DB.dbInit(activity);
        cursor=DB.db.query(DB.TABLE_WALKS, arrayColumns,
                DB.KEY_ID+"="+walkId, null, null, null, null);
        if (cursor==null || !cursor.moveToFirst()) {
            getDialog().setTitle(
                    String.format(res.getString(R.string.format_walk_has_gone),
                            walkId));
            return v;
        }

        timeZone=TimeZone.getTimeZone(cursor.getString(
                cursor.getColumnIndex(DB.KEY_TIMEZONE)));
        dateFormat.setTimeZone(timeZone);
        String s=Utils.dateStr(cursor.getLong(cursor.getColumnIndex(DB.KEY_STARTTIME)),dateFormat);
        ((TextView) v.findViewById(R.id.textViewStartTime)).setText(s);

        s=cursor.getString(cursor.getColumnIndex(DB.KEY_STARTPLACE));
        if (s!=null && s.contains("NoAddress")) s="";
        ((TextView) v.findViewById(R.id.textViewStartPlace)).setText(s);

        s=Utils.durationStr(cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION)),
                cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATIONNETTO)));
        ((TextView) v.findViewById(R.id.textViewDuration2)).setText(s);

        s=Utils.lengthStr(cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTH)),
                cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTHNETTO)));
        ((TextView) v.findViewById(R.id.textViewLength2)).setText(s);

        s=Utils.speedStr(cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATION)),
                cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTH)),
                cursor.getLong(cursor.getColumnIndex(DB.KEY_DURATIONNETTO)),
                cursor.getFloat(cursor.getColumnIndex(DB.KEY_LENGTHNETTO)));
        ((TextView) v.findViewById(R.id.textViewSpeed2)).setText(s);

        int j=Walk.AFKIND_MAX+1;
        for (int i=Walk.AFKIND_MAX; i>=Walk.AFKIND_MIN; i--) {
            Cursor cursor2=DB.db.query(DB.TABLE_AFS,new String[]{DB.KEY_AFFILEPATH},
                    DB.KEY_AFWALKID+"="+walkId+" AND "+DB.KEY_AFDELETED+"=0"+
                    " AND "+DB.KEY_AFKIND+"="+i, null, null, null, null);
//            if (cursor2.getCount()>0) {
            int k=0;
            if (cursor2.moveToFirst()) {
                do {
                    s=cursor2.getString(cursor2.getColumnIndex(DB.KEY_AFFILEPATH));
                    if (s!=null && !(new File(s).exists())) {
                        continue;
                    }
                    k++;
                } while (cursor2.moveToNext());
            }
            if (k>0) {
                j--;
                int resId=0;
                switch (i) {
                    case Walk.AFKIND_PHOTO:
                        resId=R.drawable.ic_point_photo2; // с 2 без рамки
                        break;
                    case Walk.AFKIND_SPEECH:
                        resId=R.drawable.ic_point_speech2;
                        break;
                    case Walk.AFKIND_TEXT:
                        resId=R.drawable.ic_point_text2;
                        break;
                    case Walk.AFKIND_VIDEO:
                        resId=R.drawable.ic_point_video2;
                        break;
                }
                ((ImageView) v.findViewWithTag("imageViewAFKind"+j)).setImageResource(resId);
//                ((TextView) v.findViewWithTag("textViewAFsNumber"+j)).setText(""+cursor2.getCount());
                ((TextView) v.findViewWithTag("textViewAFsNumber"+j)).setText(""+k);
            }
            cursor2.close();
        }
        if (j==Walk.AFKIND_MAX+1) {
            ((TextView) v.findViewById(R.id.textViewAFsNumber4)).setText(""); //нету
        }

        s=commentOld=cursor.getString(cursor.getColumnIndex(DB.KEY_COMMENT));
        TextView tv=(TextView)v.findViewById(R.id.textViewComment2);
        tv.setText(s);
/*
        ImageSpan span = new ImageSpan(activity, R.drawable.hint_write,
                ImageSpan.ALIGN_BOTTOM);
        SpannableStringBuilder ssb = new SpannableStringBuilder(" ");
        ssb.setSpan(span,0,1,Spannable.SPAN_INCLUSIVE_INCLUSIVE );
        tv.setHint(ssb);
*/
/* Запрет клавиатуре автоматически вылезать. Из ~10 перепробованных способов работает только это
        getDialog().getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
*/
/* Был и такой вариант
        Typeface font = Typeface.createFromAsset(activity.getAssets(), "fonts/wingding.ttf");
        ((TextView) v.findViewById(R.id.textViewComment2)).setTypeface(font);
*/
        Drawable image;
        if (cursor.getInt(cursor.getColumnIndex(DB.KEY_DELETED))==0) {
            ((TextView) v.findViewById(R.id.buttonToFromBin)).
                    setText(getString(R.string.action_walk_to_bin));
            image=res.getDrawable(R.drawable.ic_action_to_bin);
        } else {
            ((TextView) v.findViewById(R.id.buttonToFromBin)).
                    setText(getString(R.string.action_walk_from_bin));
            image=res.getDrawable(R.drawable.ic_action_from_bin);
        }
        image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
        ((Button) v.findViewById(R.id.buttonToFromBin)).setCompoundDrawables(image, null, null, null);

        v.findViewById(R.id.buttonViewOnMap).setOnClickListener(this);
        v.findViewById(R.id.buttonViewOnMap).setVisibility(
                mapActivity!=null ? View.GONE : View.VISIBLE); // GONE не оствляет пустое место
        v.findViewById(R.id.buttonViewInGallery).setOnClickListener(this);
        v.findViewById(R.id.buttonViewInGallery).setVisibility(
                galleryActivity!=null ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.buttonToFromBin).setOnClickListener(this);
        v.findViewById(R.id.buttonResume).setOnClickListener(this);
        v.findViewById(R.id.buttonResume).setVisibility(
                galleryActivity==null && mode==MapActivity.MODE_PASSIVE ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.buttonDelete).setOnClickListener(this);

        return v;
    }
    public void onClick(View v) {
        Utils.logD(TAG, ""+((Button) v).getText());

        if (v==v.findViewById(R.id.buttonViewOnMap)) {
            if (galleryActivity!=null) {
                galleryActivity.showWalkOnMap();
            } else {
                MainActivity.showWalkOnMap(activity, walkId, MapActivity.MODE_PASSIVE,
                        timeZone.getID(), false, -1);
            }
        } else if (v==v.findViewById(R.id.buttonViewInGallery)) {
            if (mapActivity!=null) {
                mapActivity.showGallery2();
            } else {
                MapActivity.showGallery(activity, new Walk(activity, walkId),
                        null, null, -1, MapActivity.MODE_PASSIVE, false);
            }
        } else if (v==v.findViewById(R.id.buttonToFromBin)) {
            toFromBin();
            MainActivity.pleaseDo=MainActivity.pleaseDo+"refresh entire list, restore selection";
        } else if (v==v.findViewById(R.id.buttonResume)) {
            MainActivity.showWalkOnMap(activity, walkId, MapActivity.MODE_RESUME,
                    timeZone.getID(), true, -1);
        } else if (v==v.findViewById(R.id.buttonDelete)) {
            walkDelete(activity,DB.KEY_ID+"=" + walkId,v,this,1);
            return; // !!!
        }
        dismiss();  // Вернемся сразу в Main
    }

    public void onCancel(DialogInterface dialog) {  // При возврате в Main
        // Последовательность: onCancell, MainActivity.onWindowFocusChanged(true), onDismiss
        Utils.logD(TAG, "onCancel");

        ContentValues values = new ContentValues();
        String s = (((TextView) v.findViewById(R.id.textViewComment2)).getText()).toString();
        if (!s.equals(commentOld)) {
            values.put(DB.KEY_COMMENT, s);
            DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID + "=" + walkId, null);
            MainActivity.pleaseDo = "refresh selected item";
        }
        isCancelled=true;
        super.onCancel(dialog);
    }
    public void onDismiss(DialogInterface dialog) { // При возврате в Main и при запуске Map и Gallery
        Utils.logD(TAG, "onDismiss");

        if (!isCancelled) { // Не было onCancel и не будет - вызов MapActivity или GalleryActivity
            ContentValues values = new ContentValues();
            String s = (((TextView) v.findViewById(R.id.textViewComment2)).getText()).toString();
            if (!s.equals(commentOld)) {
                values.put(DB.KEY_COMMENT, s);
                DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID + "=" + walkId, null);
                MainActivity.pleaseDo = "refresh selected item";
            }
        }
        super.onDismiss(dialog);
    }
    public static void walkDelete(final Activity activity,       final String what, final View anchor,
                                  final DialogFragment dialogFragment,
                                  final int itemCount) {
        Context context = activity;
        // Меню без разделителей. Статически (by XML) не получилось.
        context = new ContextThemeWrapper(context, R.style.Theme_AppCompat_Light_DarkActionBar);
        PopupMenu popup=new PopupMenu(context,anchor);
        popup.getMenuInflater()
                .inflate(R.menu.main_activity_delete_modes, popup.getMenu());
        if (itemCount>1) {
            popup.getMenu().getItem(0). // Будет удалено прогулок: %d
                    setTitle(String.format(activity.getResources().getString(
                    R.string.format_walk_delete_menu_header), itemCount));
        } else {
            popup.getMenu().getItem(0).setVisible(false);
        }
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.mode1:
                        walkDelete2(activity, 0, what);
                        break;
                    case R.id.mode2:
                        walkDelete2(activity, 1, what);
                        break;
                    case R.id.mode3:
                        walkDelete2(activity, 2, what);
                        break;
                }
                if (dialogFragment!=null) {  // Из WalkInfo, 1 прогулку
                    if (activity.getLocalClassName().contains("Map")) {
                        ((MapActivity) activity).onSupportNavigateUp();  // Все завершаем
                    } else if (activity.getLocalClassName().contains("Gallery")) {
                        ((GalleryActivity) activity).onSupportNavigateUp();  // Все завершаем
                    }
                    MainActivity.pleaseDo="refresh entire list, restore selection";
                    dialogFragment.dismiss();  // Вернемся сразу в Main
                } else { // Из main, очистка корзины
                    Toast.makeText(activity,String.format(
                                    activity.getResources().getString(R.string.format_walks_deleted),
                            itemCount),
                            itemCount<2 ? Toast.LENGTH_SHORT: Toast.LENGTH_LONG).show();
                    ((MainActivity) activity).filterParms=null;
                    ((MainActivity) activity).filterStr=
                            MainActivity.FILTER_STR_NOT_IN_BIN;
                    ((MainActivity) activity).makeWalklist();
                }
                return true;
            }
        });
        popup.show(); //showing popup menu
    }
    static void walkDelete2(Activity activity, int mode, String what) {
        Cursor cursor=DB.db.query(DB.TABLE_WALKS, new String[]{DB.KEY_ID},
                what, null, null, null, null);
        boolean flagFirst=true;
        while (flagFirst && cursor!=null && cursor.moveToFirst() ||
                !flagFirst && cursor.moveToNext()) {
            flagFirst=false;
            walkDelete3(activity, mode, cursor.getInt(cursor.getColumnIndex(DB.KEY_ID)));
        }
        cursor.close();
    }
    static void walkDelete3(Activity activity, int mode, int walkId) {
        DB.db.delete(DB.TABLE_WALKS, DB.KEY_ID+"="+walkId, null);

        Cursor cursor = DB.db.query(DB.TABLE_POINTS, new String[]{DB.KEY_POINTID},
                DB.KEY_POINTWALKID+"="+walkId,null, null, null,null);
        boolean flagFirst=true;
        while (flagFirst && cursor!=null && cursor.moveToFirst() ||
                !flagFirst && cursor.moveToNext()) {
            flagFirst=false;
            DB.db.delete(DB.TABLE_POINTS,
                    DB.KEY_POINTID+"="+cursor.getString(cursor.getColumnIndex(DB.KEY_POINTID)), null);
        }
        if (mode>0) {  // Сказано удалять с артефактами
            String s="";
            if (mode==1) {  // Сказано не удалять фото и видео
                s=" AND "+DB.KEY_AFKIND + "!=" + Walk.AFKIND_PHOTO +
                    " AND "+DB.KEY_AFKIND + "!=" + Walk.AFKIND_VIDEO;
            }
            cursor = DB.db.query(DB.TABLE_AFS,
                    new String[]{DB.KEY_AFID,DB.KEY_AFKIND,DB.KEY_AFURI,DB.KEY_AFFILEPATH},
                    DB.KEY_AFWALKID + "=" + walkId + s,
                    null, null, null,null);
            flagFirst=true;
            while (flagFirst && cursor!=null && cursor.moveToFirst() ||
                    !flagFirst && cursor.moveToNext()) {
                flagFirst=false;

                DB.db.delete(DB.TABLE_AFS,
                        DB.KEY_AFID+"="+cursor.getString(cursor.getColumnIndex(DB.KEY_AFID)), null);

                if ((s=cursor.getString(cursor.getColumnIndex(DB.KEY_AFURI)))!=null) {
                    new File(cursor.getString(cursor.getColumnIndex(DB.KEY_AFFILEPATH))).delete();
                    if (cursor.getInt(cursor.getColumnIndex(DB.KEY_AFKIND))!=Walk.AFKIND_TEXT) {
                        Uri uri=Uri.parse(s);
                        activity.getContentResolver().delete(uri, null, null);
                    }
                }
            }
        }
        cursor.close();
    }
    void toFromBin() {
        ContentValues values = new ContentValues();
        values.put(DB.KEY_DELETED,cursor.getInt(cursor.getColumnIndex(DB.KEY_DELETED))==0);
        DB.db.update(DB.TABLE_WALKS, values, DB.KEY_ID+"="+walkId, null);
    }
}
