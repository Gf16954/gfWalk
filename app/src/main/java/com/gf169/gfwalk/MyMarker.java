/**
 * Created by gf on 16.05.2015.
 */

package com.gf169.gfwalk;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.location.Location;
// import android.media.ExifInterface;
import androidx.exifinterface.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

class MyMarker {
    private static final String TAG = "gfMyMarker";

    private static final int MINI_THUMBNAIL_HEIGHT=512; // x 384
    private static final int MICRO_THUMBNAIL_HEIGHT=96; // x 96

    static Context appContext;
    static Activity curActivity;
    static MapActivity mapActivity;
    private static Handler handler;

    static class MyMarkerOptions {
        int[] iconResources;
        float relativeHeight; // Относительно Высоты экрана
        int minHeight; // В мм
        int maxHeight; // В мм
        float anchorU;
        float anchorV;
        int textCorner; // 0 - в центре, 1 - левый верхний угол, 2 - правый верхний, 3 - правый нижний, 4 - левый нижний
        float alpha;
        int animCycleDuration; // Промежуток между сменой кадров, мс, 0 - без анимации
        int animNumberOfCycles; // 0 - бесконечно
        int overlayRes;
        int bitmapIni;
        boolean rotatable; // Вращается вместе с картой

        Bitmap[] bitmaps;
        int realHeight;

        MyMarkerOptions(int[] iconResources, float relativeHeight, int minHeight, int maxHeight,
                float anchorU, float anchorV, int textCorner, float alpha,
                int animCycleDuration, int animNumberOfCycles, int overlayRes, int bitmapIni, boolean rotatable) {
            this.iconResources = iconResources;
            this.relativeHeight = relativeHeight;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.anchorU = anchorU;
            this.anchorV = anchorV;
            this.textCorner = textCorner;
            this.alpha = alpha;
            this.animCycleDuration = animCycleDuration;
            this.animNumberOfCycles = animNumberOfCycles;
            this.overlayRes = overlayRes;
            this.bitmapIni = bitmapIni;
            this.rotatable = rotatable;
        }
    }

    static MyMarkerOptions[] mmoA=new MyMarkerOptions[]{
// iconResources,relativeHeight,minHeight,maxHeight,anchorU,anchorV,
// textCorner,alpha,animCycleDuration,animNumberOfCycles,overlay,rotateable
        /* 0 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_default},0.03F,3,6,0.5F,0.5F,0,1,0,0,0,0, true),
        /* 1 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_photo}, 0.075F,8,15,0.0F,0.0F,3,0.5F,0,0,0,0, false),  // Photo
        /* 2 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_speech},0.040F,5,10,0.0F,1.0F,2,0.5F,0,0,0,0, false),  // Speech
        /* 3 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_text},  0.040F,5,10,1.0F,0.0F,4,0.5F,0,0,0,0, false),  // Text
        /* 4 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_video}, 0.075F,8,15,1.0F,1.0F,1,0.5F,0,0,  // Video
                            R.drawable.overlay_play,0, false),
        /* 5 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_end_active},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0,0, false),
        /* 6 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_end_passive},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0,0, false),
        /* 7 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_start},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0,0, false),
        /* 8 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_start},0.015F,2,5,0.5F,0.5F,0,0.5F,0,0,0,0, false),
        null,null,
//            new MyMarkerOptions(new int[]{R.drawable.ic_cur_pos1},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0), // Текущее положение без направления
        new MyMarkerOptions(new int[]{  // 11 - без направления
            R.drawable.ic_cur_pos10,
            R.drawable.ic_cur_pos11,
            R.drawable.ic_cur_pos12,
            R.drawable.ic_cur_pos13,
            R.drawable.ic_cur_pos14,
            R.drawable.ic_cur_pos15,
            R.drawable.ic_cur_pos16,
            R.drawable.ic_cur_pos17,
            R.drawable.ic_cur_pos18,
            R.drawable.ic_cur_pos19,
            R.drawable.ic_cur_pos18,
            R.drawable.ic_cur_pos17,
            R.drawable.ic_cur_pos16,
            R.drawable.ic_cur_pos15,
            R.drawable.ic_cur_pos14,
            R.drawable.ic_cur_pos13,
            R.drawable.ic_cur_pos12,
            R.drawable.ic_cur_pos11,
            R.drawable.ic_cur_pos10,
            },0.030F,4,6,0.5F,0.5F,0,0.5F,0,0,0,6, true), // Текущее положение без направления
        new MyMarkerOptions(new int[]{  // 12 - направление по скорости движения
            R.drawable.ic_cur_pos50,
            R.drawable.ic_cur_pos51,
            R.drawable.ic_cur_pos52,
            R.drawable.ic_cur_pos53,
            R.drawable.ic_cur_pos54,
            R.drawable.ic_cur_pos55,
            R.drawable.ic_cur_pos56,
            R.drawable.ic_cur_pos57,
            R.drawable.ic_cur_pos58,
            R.drawable.ic_cur_pos59,
            R.drawable.ic_cur_pos58,
            R.drawable.ic_cur_pos57,
            R.drawable.ic_cur_pos56,
            R.drawable.ic_cur_pos55,
            R.drawable.ic_cur_pos54,
            R.drawable.ic_cur_pos53,
            R.drawable.ic_cur_pos52,
            R.drawable.ic_cur_pos51,
            R.drawable.ic_cur_pos50,
            },0.030F,4,6,0.5F,0.5F,0,0.99F,0,0,0,0, true), // Текущее положение c направлением
        new MyMarkerOptions(new int[]{ // 13 - направление по компасу
            R.drawable.ic_cur_pos70,
            R.drawable.ic_cur_pos71,
            R.drawable.ic_cur_pos72,
            R.drawable.ic_cur_pos73,
            R.drawable.ic_cur_pos74,
            R.drawable.ic_cur_pos75,
            R.drawable.ic_cur_pos76,
            R.drawable.ic_cur_pos77,
            R.drawable.ic_cur_pos78,
            R.drawable.ic_cur_pos79,
            R.drawable.ic_cur_pos78,
            R.drawable.ic_cur_pos77,
            R.drawable.ic_cur_pos76,
            R.drawable.ic_cur_pos75,
            R.drawable.ic_cur_pos74,
            R.drawable.ic_cur_pos73,
            R.drawable.ic_cur_pos72,
            R.drawable.ic_cur_pos71,
            R.drawable.ic_cur_pos70,
        },0.030F,4,6,0.5F,0.5F,0,0.99F,0,0,0,0, true), // Текущее положение c направлением
        /* 14 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_in_gallery},0.04F,4,6,0.5F,0.5F,0,0.75F,0,0,0,0, false),
        /* 15 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_end_passive},0.015F,2,5,0.5F,0.5F,0,0.5F,0,0,0,0, false),
        /* 16 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_default_passive},0.03F,3,6,0.5F,0.5F,0,1F,0,0,0,0, true), // Timer
        /* 17 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_default_with_afs},0.03F,3,6,0.5F,0.5F,0,1F,0,0,0,0, true), // Timer
        /* 18 */ new MyMarkerOptions(new int[]{R.drawable.ic_point_trace},0.025F,2,3,0.5F,0.5F,0,0.50F,1000,1,0,0, true),
    };
    static DisplayMetrics metrics=Resources.getSystem().getDisplayMetrics();
    static Random random=new Random();
    static Vector<Marker> workMarkers=new Vector<>(10,10); // Рабочий массив для одноразовых маркеров

    static void ini(Context appContext) {
        MyMarker.appContext = appContext;
    }

    static int xxx = 0;  // Сумма квадратов радиусов маркера текущего положения и маркера следа
    static Location skippedTraceLocation;

    static void drawMarker(
        final GoogleMap map,
        final Vector<Marker> markers,
        final int markerIndex,
        final int markerKind,
        Location location,
        String title,
        String snippet,
        String afUri, String afFilePath,
        String textOnIcon,
        float rotation,
        float pulsationsPerSecond,
        boolean visible,
        final boolean removeOld) {

        Bitmap bitmap=formMarkerIcon(markerKind,afUri,afFilePath,textOnIcon);

        // Маркеры следа не должны накладываться друг на друга и на маркер текущего положения
        if (markers == mapActivity.curPosMarkers && xxx == 0) {
            xxx = - bitmap.getHeight() * bitmap.getHeight();
        } else if (markers == mapActivity.traceMarkers && xxx < 0) {
            xxx = -xxx + bitmap.getHeight() * bitmap.getHeight();
        }
        if (markers == mapActivity.traceMarkers) {
            if (skippedTraceLocation != null) {
                location.set(skippedTraceLocation); // !!!
            }
            Point p1 = map.getProjection().toScreenLocation(mapActivity.curPosMarkers.get(0).getPosition());
            Point p2 = map.getProjection().toScreenLocation(Utils.loc2LatLng(location));
            if ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y) < xxx) { // Наложатся - не рисуем
                skippedTraceLocation = new Location("");
                skippedTraceLocation.set(location);
                return;
            } else {
                skippedTraceLocation = null;
            }
        }

        final MyMarkerOptions mmo=mmoA[markerKind];
        final MarkerOptions markerOptions = new MarkerOptions()
            .position(Utils.loc2LatLng(location))
            .title("\u200e"+title)
                    // Впереди Unicode RTL mark, без него если адрес на иврите, заголовок не показывается - пустая строка
                    // Таинственным образом если адрес НЕ на иврите, показывается нормально, слева направо :)
            .snippet(snippet)
            .alpha(mmo.alpha)
            .anchor(mmo.anchorU, mmo.anchorV)
            .infoWindowAnchor(mmo.anchorU, mmo.anchorV)
            .flat(mmo.rotatable)
            .visible(visible)
            .icon(BitmapDescriptorFactory.fromBitmap(bitmap));
        if (rotation>=0) {
            markerOptions.rotation(rotation);
        }
        curActivity.runOnUiThread(() -> {    // Наконец рисуем маркер
            Marker marker = map.addMarker(markerOptions);
            int markerIndex2 = markerIndex;
            if (markerIndex2 < 0) {
                markerIndex2 = Utils.addToList(markers, marker);
            } else {
                if (removeOld && markers.get(markerIndex2) != null) {
                    killMarker(markers, markerIndex2);
                }
                markers.set(markerIndex2, marker);
            }
            if (markers != mapActivity.traceMarkers) {
                int i;
                if (removeOld &&
                    (i = mapActivity.allMarkers.indexOf(markers.get(markerIndex2))) >= 0) {
                    mapActivity.allMarkers.set(i, marker);
                } else {
                    mapActivity.allMarkers.add(marker);
                }
            }

            if (marker.getSnippet() != null &&
                    marker.getPosition().hashCode() == mapActivity.markerWithInfoWindow) {
                marker.showInfoWindow();
            }

            if (pulsationsPerSecond > 0) {
                    mmo.animCycleDuration = Math.round(1000 / pulsationsPerSecond); // Длительность одного кадра в миллисекундах
            } // Остальные - как задано в mmo
// mmo.animCycleDuration = 0;
            if (mmo.animCycleDuration > 0) {  // Анимация (пульсация)
                if (handler==null) {
                    handler = new Handler(); // Main thread
                }
                final int[] iconIndex = {0};
                final int[] nCycles = {1};
                int frameDuration =  mmo.animCycleDuration / mmo.bitmaps.length;

                final int markerIndex3 = markerIndex2;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (marker != markers.get(markerIndex3)) { // Убит
                                return;
                            }
                            if (marker.isVisible()) {
                                boolean isInfoWindowShown = marker.isInfoWindowShown();

                                iconIndex[0] = (iconIndex[0] + 1) % mmo.bitmaps.length;
                                if (mmo.animNumberOfCycles > 0) {
                                    nCycles[0] += iconIndex[0] == 0 ? 1 : 0;
                                    if (nCycles[0] > mmo.animNumberOfCycles) {
                                        killMarker(markers, markerIndex3);
                                        return;
                                    }
                                }
                                marker.setIcon(
                                    BitmapDescriptorFactory.fromBitmap(
                                        mmo.bitmaps[iconIndex[0]]));

                                if (markers == mapActivity.curPosMarkers &&  // Заодно update'им по компасу
                                    markerKind == MapActivity.MO_CUR_POS_WITH_DIRECTION_2) {
                                    marker.setRotation(mapActivity.compas.getAzimuth());
                                }

                                if (isInfoWindowShown) {
                                    marker.showInfoWindow();
                                }

                            }
                            handler.postDelayed(this, frameDuration);  // Поэтому нельзя заменить на лямбду
                        } catch (Throwable e) {}  // NPE - marker was removed - выход из цикла
                    }
                }, frameDuration);
            }
            // Поворот в направлении взгляда - если выше не поворачивали (слишком редкая пульсация)
            if (markers == mapActivity.curPosMarkers &&
                    markerKind == MapActivity.MO_CUR_POS_WITH_DIRECTION_2 && // компас
                    (mmo.animCycleDuration==0 || mmo.animCycleDuration > MapActivity.SENSOR_DELAY)) {
                if (handler == null) {
                    handler = new Handler(); // Main thread
                }
                final int markerIndex3 = markerIndex2;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (marker != markers.get(markerIndex3)) {  // Убит
                                return;
                            }
                            if (marker.isVisible()) {
                                boolean isInfoWindowShown = marker.isInfoWindowShown();
                                marker.setRotation(mapActivity.compas.getAzimuth());
                                if (isInfoWindowShown) {
                                    marker.showInfoWindow();
                                }
                            }
                            handler.postDelayed(this, MapActivity.SENSOR_DELAY);
                        } catch (Throwable e) {}  // NPE - marker was removed - выход из цикла
                    }
                }, MapActivity.SENSOR_DELAY);
            }
        });
    }

    private static Bitmap formMarkerIcon(
            int markerKind,
            String Uri,
            String filePath,
            String textOnIcon) {

        MyMarkerOptions mmo=mmoA[markerKind];
        if (mmo.bitmaps==null) { // Инициализируем
            float srcHeight; // pixels
            float dstHeight; // pixels
            mmo.bitmaps=new Bitmap[mmo.iconResources.length];
            for (int k=0; k<mmo.iconResources.length; k++) {
                dstHeight=(float)Math.max(metrics.heightPixels,metrics.widthPixels)
                        *mmo.relativeHeight;
                dstHeight=Math.max(dstHeight,mmo.minHeight*metrics.densityDpi/25.4F);
                dstHeight=Math.min(dstHeight,mmo.maxHeight*metrics.densityDpi/25.4F);
                mmo.realHeight=(int) dstHeight;  // Это требуемая высота иконки в пикселах
                mmo.bitmaps[k]=decodeBitmap(appContext.getResources(),mmo.iconResources[k],
                        0,null,null,mmo.realHeight,false);
            }
        }
        Bitmap bitmap=null;  // Формируем иконку нужного размера
        if ((markerKind==Walk.AFKIND_PHOTO || markerKind==Walk.AFKIND_VIDEO) &&
                (Uri != null | filePath!=null))  {
            bitmap = decodeBitmap(null,0,markerKind,Uri,filePath,mmo.realHeight,false);
            if (mmo.overlayRes!=0) {
                bitmap=addOverlayOnBitmap(bitmap, appContext.getResources(), mmo.overlayRes);
            }
        }
        if (bitmap==null) {
            bitmap=Bitmap.createBitmap(mmo.bitmaps[
                mmo.bitmapIni>=0 ? mmo.bitmapIni : random.nextInt(mmo.bitmaps.length)]);
        }
        if (textOnIcon!=null) {       // Накладываем на нее текст
            bitmap=addTextOnIcon(bitmap, textOnIcon, mmo.textCorner,
                   textOnIcon.equals(appContext.getResources().getString(R.string.af_has_gone)) ?
                           Color.RED : Color.TRANSPARENT);
        }
        return bitmap;
    }

    static Bitmap decodeBitmap(
            Resources resources, int resId, int afKind, String afUri, String afFilePath, int height,
            boolean dontResize) {
        try {
            return decodeBitmap2(resources, resId, afKind, afUri, afFilePath, height,dontResize);
        } catch (OutOfMemoryError e) {  // Дефолтная иконка
            e.printStackTrace();
            return decodeBitmap2(appContext.getResources(),mmoA[afKind].iconResources[0],
                    0,null,null,height,dontResize);
        }
    }

    private static Bitmap decodeBitmap2(
            Resources resources, int resId, int afKind, String afUri, String afFilePath, int height,
            boolean dontResize) {
        Bitmap bitmap=null;

        if (resources != null) {  // Иконка в ресурсах
            bitmap=BitmapFactory.decodeResource(resources, resId);
            try {
                bitmap=ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeResource(resources, resId), height, height);
            } catch (Exception e) {  // Исходный bitmap меньше требуемого (в gallery) - оставляем как есть
            }
            return bitmap;
        }
        if (height<=MINI_THUMBNAIL_HEIGHT && afUri!=null) {  // Получаем уже созданные или просим создать
            Utils.logD(TAG,"Decoding by URI: "+afKind+" "+afUri+" "+afFilePath+" "+height);

            int kind=height<=MICRO_THUMBNAIL_HEIGHT ?
                    MediaStore.Images.Thumbnails.MICRO_KIND : MediaStore.Images.Thumbnails.MINI_KIND;
            if (afKind==Walk.AFKIND_PHOTO) {
                bitmap=MediaStore.Images.Thumbnails.getThumbnail(
                        appContext.getContentResolver(),
                        Long.parseLong(Uri.parse(afUri).getLastPathSegment()),
                        kind, null);
                bitmap=rotateBitmap(bitmap, afFilePath);
            } else if (afKind==Walk.AFKIND_VIDEO) {
                bitmap=MediaStore.Video.Thumbnails.getThumbnail(
                        appContext.getContentResolver(),
                        Long.parseLong(Uri.parse(afUri).getLastPathSegment()),
                        kind, null);
            }
        }
        if (bitmap==null && new File(afFilePath).exists()) { // Нужен большой или Uri почему-то неизвестен или bitmap почему-то не создался - сами создаем из файла
            if (afKind == Walk.AFKIND_PHOTO) {
                Utils.logD(TAG, "Decoding image file: " + afFilePath + " " + height);

                BitmapFactory.Options bfo = new BitmapFactory.Options();
                bfo.inJustDecodeBounds = true; // Узнаем размер исходной картинки
                BitmapFactory.decodeFile(afFilePath, bfo);
                bfo.inJustDecodeBounds = false;
                bfo.inMutable = true;
                bfo.inTargetDensity = metrics.densityDpi;  // Так ...
                //if (false) {//dontResize) //Глюк? TODO: Не переворачивает. И пропадает ! Разобраться !
                if (dontResize) {
                    bfo.inScaled = false;
                    bfo.inDensity = 0;
                } else {
                    bfo.inScaled = true;
                    bfo.inDensity = bfo.inTargetDensity * bfo.outHeight / height; // и только так !
                }
                bitmap = BitmapFactory.decodeFile(afFilePath, bfo);
                bitmap = rotateBitmap(bitmap, afFilePath);
            } else if (afKind == Walk.AFKIND_VIDEO) {
                Utils.logD(TAG, "Decoding video file: " + afFilePath + " " + height);

                bitmap = ThumbnailUtils.createVideoThumbnail(afFilePath,
                    MediaStore.Video.Thumbnails.MINI_KIND); // immutable !
            }
        }
        if (bitmap==null) {
            return null;  // Будет default'ный маркер
        }
        if (dontResize) return bitmap;
        if (bitmap.getHeight()>height && bitmap.getWidth()>height) {
            bitmap=ThumbnailUtils.extractThumbnail(bitmap, height, height);
        }
        return bitmap;
    }

    static void changeMarkerKind( // Меняем только иконку - не анимированный маркер!
            final Vector<Marker> markers,
            final int markerIndex,
            int markerKind) {
        final Bitmap bitmap=formMarkerIcon(markerKind, null, null, null);
        curActivity.runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    markers.get(markerIndex).setIcon(BitmapDescriptorFactory.fromBitmap(bitmap));
                }
            }
        );
    }

    static Bitmap addTextOnIcon(Bitmap bitmap, String text, int textCorner, int color) {
        return addTextOnIcon(bitmap, text, textCorner, color,
                appContext.getResources().getInteger(R.integer.text_on_bmp_padding));
    }

    static Bitmap addTextOnIcon(Bitmap bitmap, String text, int textCorner, int color, int padding /* % */) {
        if (!bitmap.isMutable()) {
            bitmap=bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }

        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float height=appContext.getResources().getInteger(R.integer.text_on_bmp_height)/100.0F;
        paint.setTextSize((int) (bitmap.getHeight()*height));
        paint.setFakeBoldText(true);

        if (color == Color.BLACK) { // 0xff000000
            paint.setShadowLayer(2f, 2f, 2f, Color.WHITE);
        } else if (color == Color.WHITE) {
            paint.setShadowLayer(2f, 2f, 2f, Color.BLACK);
        }

        Rect bounds=new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int padX=(int) (padding / 100.0F * bitmap.getWidth());
        int padY=(int) (padding / 100.0F * bitmap.getHeight());
        //                  * (0,0)
        //                    1 | 2
        //                    --0--
        //                    4 | 3
        // Координаты левого нижнего угла прямоугольника в котором текст
        int x = 0;
        int y = 0;
        if (textCorner == 0) {
            x=Math.max(bitmap.getWidth() / 2 - bounds.width() / 2, 0);
            y=bitmap.getHeight() / 2 + bounds.height() / 2;
        } else if (textCorner==1) {
            x=padX;
            y=bounds.height()+padY;
        } else if (textCorner == 2) {
            x = Math.max(bitmap.getWidth() - padX - bounds.width(), 0);
            y = bounds.height() + padY;
        } else if (textCorner == 3) {
            x = Math.max(bitmap.getWidth() - padX - bounds.width(), 0);
            y = Math.max(bitmap.getHeight() - padY, 0);
        } else if (textCorner == 4) {
            x = padX;
            y = Math.max(bitmap.getHeight() - padY, 0);
        }

        paint.setColor(color);
        if (color == Color.TRANSPARENT) {
            paint.setColor(getContrastColor(
                    Bitmap.createBitmap(bitmap, x, Math.max(y - bounds.height() - padY, 0),
                            Math.min(bounds.width(), bitmap.getWidth() - x),  // Ширина
                            Math.min(bounds.height(), bitmap.getHeight() - y))));  // Высота
        }

        new Canvas(bitmap).drawText(text,x,y,paint);
        return bitmap;
    }

    static Bitmap addOverlayOnBitmap(Bitmap bitmap, Resources resources, int resId) {
        // Получаем overlay - Bitmap такой же высоты, как и bitmap
        BitmapFactory.Options bfo = new BitmapFactory.Options();
        bfo.inJustDecodeBounds=true; // Узнаем размер исходной картинки
        BitmapFactory.decodeResource(resources, resId, bfo);
        bfo.inJustDecodeBounds=false;
        bfo.inTargetDensity=metrics.densityDpi;  // Так ...
        bfo.inDensity=bfo.inTargetDensity * bfo.outHeight / bitmap.getHeight(); // и только так !
        bfo.inMutable=true;
        Bitmap overlay=BitmapFactory.decodeResource(resources, resId, bfo);

        if (!bitmap.isMutable()) {
            bitmap=bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(overlay, (bitmap.getWidth() - overlay.getWidth()) / 2, 0, paint);
        return bitmap;
    }

    private static int getContrastColor (Bitmap bitmap) {
        int c, nPixels=0;
        long alphaBucket=0, redBucket=0, greenBucket=0, blueBucket=0;
        for (int y=0; y<bitmap.getHeight(); y++) {
            for (int x=0; x<bitmap.getWidth(); x++) {
                c=bitmap.getPixel(x, y);
                alphaBucket+=Color.alpha(c);
                redBucket+=Color.red(c);
                greenBucket+=Color.green(c);
                blueBucket+=Color.blue(c);
                nPixels++;
            }
        }
// http://gamedev.stackexchange.com/questions/38536/given-a-rgb-color-x-how-to-find-the-most-contrasting-color-y
        int color=Color.BLACK; // Если прозрачная картинка, считаем фон светлым
        if (alphaBucket/nPixels>128 &&
                (0.2126 * redBucket/nPixels*redBucket/nPixels +
                 0.7152 * greenBucket/nPixels*greenBucket/nPixels +
                 0.0722 * blueBucket/nPixels*blueBucket/nPixels) /255/255<0.5) {
            color=Color.WHITE;
        }
        return color;
/*
        Когда я искал лучший способ сделать это, я наткнулся на руководство Adobe Illustrator,
        в котором упоминается, как они создают дополнительные цвета. Они говорят:
        Дополнение изменяет каждый компонент цвета на новое значение на основе суммы самых высоких и
        самых низких значений RGB в выбранном цвете. Illustrator добавляет самые низкие и самые
        высокие значения RGB текущего цвета, а затем вычитает значение каждого компонента из этого
        числа, чтобы создать новые значения RGB. Например, предположим, что вы выбрали цвет со
        значением RGB 102 для красного, 153 для зеленого и 51 для синего.
        Иллюстратор добавляет высокое (153) и низкое (51) значения, чтобы в итоге получить новое
        значение (204). Каждое из значений RGB в существующем цвете вычитается из нового значения
        для создания новых дополнительных значений RGB: 204 – 102 (текущее красное значение) = 102
        для нового красного значения, 204 – 153 (текущее зеленое значение) = 51 для нового зеленого
        значения и 204 – 51 (текущее синее значение) = 153 для нового синего значения.
*/
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, String bitmapPath) {
        // http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
        ExifInterface exif;
        try {
            exif = new ExifInterface(bitmapPath);
        } catch (IOException e) {
            e.printStackTrace();
            return bitmap;
        }
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    static Bitmap cropCircle(Bitmap bitmap) { // https://gist.github.com/jewelzqiu/c0633c9f3089677ecf85
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    static void killMarker(final Vector<Marker> markers, final int markerIndex) {
        curActivity.runOnUiThread(new Runnable() {
            public void run() {
                if (markers.get(markerIndex) != null) {
                    markers.get(markerIndex).remove();
                    markers.set(markerIndex, null);
                }

            }
        });
    }
}
