/**
 * Created by gf on 16.05.2015.
 */

package com.gf169.gfwalk;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.Random;
import java.util.Vector;

public class MyMarker {
    static final String TAG = "gfMyMarker";

    static final int MINI_THUMBNAIL_HEIGHT=512; // x 384
    static final int MICRO_THUMBNAIL_HEIGHT=96; // x 96

    static final float MARKER_ROTATION=0.001F;

    static Activity curActivity;
    static Handler handler;

    static class MyMarkerOptions {
        int[] iconResources;
        float relativeHeight; // Относительно Высоты экрана
        int minHeight; // В мм
        int maxHeight; // В мм
        float anchorU;
        float anchorV;
        int textCorner; // 0 - в центре, 1 - левый верхний угол, 2 - правый верхний, 3 - правый нижний, 4 - левый нижний
        float alpha;
        long animInterval; // Промежуток между сменой кадров, мс, 0 - без анимации
        long animDuration; // Продолжительность анимации, мс, 0 - без конца
        int overlayRes;
        int bitmapIni;

        Bitmap[] bitmaps;
        int realHeight;

        MyMarkerOptions(int[] iconResources, float relativeHeight, int minHeight, int maxHeight,
                float anchorU, float anchorV, int textCorner, float alpha,
                long animInterval, long animDuration, int overlayRes, int bitmapIni) {
            this.iconResources = iconResources;
            this.relativeHeight = relativeHeight;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.anchorU = anchorU;
            this.anchorV = anchorV;
            this.textCorner = textCorner;
            this.alpha=alpha;
            this.animInterval = animInterval;
            this.animDuration = animDuration;
            this.overlayRes=overlayRes;
            this.bitmapIni=bitmapIni;
        }
    }

    static MyMarkerOptions[] mmoA=new MyMarkerOptions[]{
// iconResources,relativeHeight,minHeight,maxHeight,anchorU,anchorV,
// textCorner,alpha,animInterval,animDuration,overlay
            new MyMarkerOptions(new int[]{R.drawable.ic_point_default},0.03F,3,6,0.5F,0.5F,0,1,0,0,0,0), // Timer
            new MyMarkerOptions(new int[]{R.drawable.ic_point_photo}, 0.075F,8,15,0.0F,0.0F,3,0.5F,0,0,0,0),  // Photo
            new MyMarkerOptions(new int[]{R.drawable.ic_point_speech},0.040F,5,10,0.0F,1.0F,2,0.5F,0,0,0,0),  // Speech
            new MyMarkerOptions(new int[]{R.drawable.ic_point_text},  0.040F,5,10,1.0F,0.0F,4,0.5F,0,0,0,0),  // Text
            new MyMarkerOptions(new int[]{R.drawable.ic_point_video}, 0.075F,8,15,1.0F,1.0F,1,0.5F,0,0,  // Video
                                R.drawable.ic_action_play,0),
            new MyMarkerOptions(new int[]{R.drawable.ic_point_end_active},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0,0),
            new MyMarkerOptions(new int[]{R.drawable.ic_point_end_passive},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0,0),
            new MyMarkerOptions(new int[]{R.drawable.ic_point_start},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0,0),
            new MyMarkerOptions(new int[]{R.drawable.ic_point_start},0.012F,2,5,0.5F,0.5F,0,0.5F,0,0,0,0),
            null,null,
//            new MyMarkerOptions(new int[]{R.drawable.ic_cur_pos1},0.025F,3,5,0.5F,0.5F,0,0.5F,0,0,0), // Текущее положение без направления
            new MyMarkerOptions(new int[]{
                    R.drawable.ic_cur_pos11,
                    R.drawable.ic_cur_pos12,
                    R.drawable.ic_cur_pos13,
                    R.drawable.ic_cur_pos14,
                    R.drawable.ic_cur_pos15,
                    R.drawable.ic_cur_pos16,
                    R.drawable.ic_cur_pos17,
                    R.drawable.ic_cur_pos18,
                    R.drawable.ic_cur_pos19,
                    R.drawable.ic_cur_pos20,
                    R.drawable.ic_cur_pos21,
                    R.drawable.ic_cur_pos20,
                    R.drawable.ic_cur_pos19,
                    R.drawable.ic_cur_pos18,
                    R.drawable.ic_cur_pos17,
                    R.drawable.ic_cur_pos16,
                    R.drawable.ic_cur_pos15,
                    R.drawable.ic_cur_pos14,
                    R.drawable.ic_cur_pos13,
                    R.drawable.ic_cur_pos12,
                    R.drawable.ic_cur_pos11,
                    },
                    0.030F,4,6,0.5F,0.5F,0,0.5F,300,0,0,6), // Текущее положение без направления
//            new MyMarkerOptions(new int[]{R.drawable.ic_cur_pos2},
            new MyMarkerOptions(new int[]{
                    R.drawable.ic_cur_pos51,
                    R.drawable.ic_cur_pos52,
                    R.drawable.ic_cur_pos53,
                    R.drawable.ic_cur_pos54,
                    R.drawable.ic_cur_pos55,
                    R.drawable.ic_cur_pos56,
                    R.drawable.ic_cur_pos57,
                    R.drawable.ic_cur_pos58,
                    R.drawable.ic_cur_pos59,
                    R.drawable.ic_cur_pos60,
                    R.drawable.ic_cur_pos61,
                    R.drawable.ic_cur_pos60,
                    R.drawable.ic_cur_pos59,
                    R.drawable.ic_cur_pos58,
                    R.drawable.ic_cur_pos57,
                    R.drawable.ic_cur_pos56,
                    R.drawable.ic_cur_pos55,
                    R.drawable.ic_cur_pos54,
                    R.drawable.ic_cur_pos53,
                    R.drawable.ic_cur_pos52,
                    R.drawable.ic_cur_pos51,
                    },
                    0.030F,4,6,0.5F,0.5F,0,0.5F,300,0,0,6), // Текущее положение c направлением
//            new MyMarkerOptions(new int[]{R.drawable.ic_cur_pos3},
            new MyMarkerOptions(new int[]{
                    R.drawable.ic_cur_pos31,
                    R.drawable.ic_cur_pos32,
                    R.drawable.ic_cur_pos33,
                    R.drawable.ic_cur_pos34,
                    R.drawable.ic_cur_pos35,
                    R.drawable.ic_cur_pos36,
                    R.drawable.ic_cur_pos37,
                    R.drawable.ic_cur_pos38,
                    R.drawable.ic_cur_pos39,
                    R.drawable.ic_cur_pos40,
                    R.drawable.ic_cur_pos41,
                    R.drawable.ic_cur_pos40,
                    R.drawable.ic_cur_pos39,
                    R.drawable.ic_cur_pos38,
                    R.drawable.ic_cur_pos37,
                    R.drawable.ic_cur_pos36,
                    R.drawable.ic_cur_pos35,
                    R.drawable.ic_cur_pos34,
                    R.drawable.ic_cur_pos33,
                    R.drawable.ic_cur_pos32,
                    R.drawable.ic_cur_pos31,
                    },
                    0.030F,4,6,0.5F,0.5F,0,0.5F,300,0,0,6), // Текущее положение c направлением
            new MyMarkerOptions(new int[]{R.drawable.ic_point_in_gallery},0.04F,4,6,0.5F,0.5F,0,0.75F,0,0,0,0),
            new MyMarkerOptions(new int[]{R.drawable.ic_point_end_passive},0.012F,2,5,0.5F,0.5F,0,0.5F,0,0,0,0),
            new MyMarkerOptions(new int[]{R.drawable.ic_point_default_passive},0.03F,3,6,0.5F,0.5F,0,1F,0,0,0,0), // Timer
            new MyMarkerOptions(new int[]{R.drawable.ic_point_default_with_afs},0.03F,3,6,0.5F,0.5F,0,1F,0,0,0,0), // Timer
    };
    static DisplayMetrics metrics=Resources.getSystem().getDisplayMetrics();
    static BitmapFactory.Options bfo = new BitmapFactory.Options();
    static Random random=new Random();
    static Vector<Marker> workMarkers=new Vector<>(10,10); // Рабочий массив для одноразовых маркеров

    static void drawMarker(
        final GoogleMap map,
        final Vector<Marker> markers,
        final int markerIndex,
        int markerKind,
        Location location,
        String title,
        String snippet,
        String afUri, String afFilePath,
        String textOnIcon,
        float rotation,
        boolean visible,
        final boolean removeOld) {

        Bitmap bitmap=formMarkerIcon(markerKind,afUri,afFilePath,textOnIcon);

        final MyMarkerOptions mmo=mmoA[markerKind];
        final MarkerOptions markerOptions =
                new MarkerOptions()
                        .position(Utils.loc2LatLng(location))
                        .title("\u200e"+title)
                                // Впереди Unicode RTL mark, без него если адрес на иврите, заголовок не показывается - пустая строка
                                // Таинственным образом если адрес НЕ на иврите, показывается нормально, слева направо :)
                        .snippet(snippet)
                        .flat(true)
                        .rotation(MARKER_ROTATION) // !!!
                        .alpha(mmo.alpha)
                        .anchor(mmo.anchorU, mmo.anchorV)
                        .infoWindowAnchor(mmo.anchorU, mmo.anchorV)
                        .visible(visible)
                        .icon(BitmapDescriptorFactory.fromBitmap(bitmap));
        if (rotation>=0) {
            markerOptions.rotation(rotation);
        }
        curActivity.runOnUiThread(    // Наконец рисуем маркер
            new Runnable() {
                @Override
                public void run() {
                    if (markerIndex < 0) {
                        markers.add(map.addMarker(markerOptions));
                    } else {
                        if (removeOld && markers.get(markerIndex) != null) {
                            killMarker(markers,markerIndex);
                        }
                        markers.set(markerIndex, map.addMarker(markerOptions));
                    }
                    if (mmo.animInterval > 0) {  // Анимация
                        if (handler==null) {
                            handler = new Handler(); // Main thread
                        }
                        final int iconIndex[] = {0};

                        final Marker marker0 = markers.get(markerIndex);
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Marker marker = markers.get(markerIndex);
                                    if (marker==null || marker!=marker0) {
                                        return;
                                    }
                                    if (marker.isVisible()) {
                                        boolean isInfoWindowShown = marker.isInfoWindowShown();
                                        iconIndex[0] = (iconIndex[0] + 1) % mmo.bitmaps.length;
                                        marker.setIcon(
                                                BitmapDescriptorFactory.fromBitmap(
                                                        mmo.bitmaps[iconIndex[0]]));
                                        if (isInfoWindowShown) {
                                            marker.showInfoWindow();
                                        }
                                    }
                                    handler.postDelayed(this,mmo.animInterval);
                                } catch (Throwable e) {  // removed
                                }
                            }
                        },mmo.animInterval);
                    }
                }
            }
        );
    }
    static void turnAnimationOnOff(int markerIndex, boolean on) {
        mmoA[markerIndex].animInterval=Math.abs(mmoA[markerIndex].animInterval)*(on ? 1 : -1);
    }
    static Bitmap formMarkerIcon(
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
                mmo.bitmaps[k]=decodeBitmap(curActivity.getResources(),mmo.iconResources[k],
                        0,null,null,mmo.realHeight,false);
            }
        }
        Bitmap bitmap=null;  // Формируем иконку нужного размера
        if ((markerKind==Walk.AFKIND_PHOTO || markerKind==Walk.AFKIND_VIDEO) &&
                (Uri != null | filePath!=null))  {
            bitmap = decodeBitmap(null,0,markerKind,Uri,filePath,mmo.realHeight,false);
            if (mmo.overlayRes!=0) {
                bitmap=addOverlayOnBitmap(bitmap, curActivity.getResources(), mmo.overlayRes);
            }
        }
        if (bitmap==null) {
            bitmap=Bitmap.createBitmap(mmo.bitmaps[
                    mmo.bitmapIni>=0 ? mmo.bitmapIni : random.nextInt(mmo.bitmaps.length)]);
        }
        if (textOnIcon!=null) {       // Накладываем на нее текст
            bitmap=addTextOnIcon(bitmap, textOnIcon, mmo.textCorner,
                   textOnIcon.equals(curActivity.getResources().getString(R.string.af_has_gone)) ?
                           Color.RED : -1);
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
            return decodeBitmap2(curActivity.getResources(),mmoA[afKind].iconResources[0],
                    0,null,null,height,dontResize);
        }
    }
    static Bitmap decodeBitmap2(
            Resources resources, int resId, int afKind, String afUri, String afFilePath, int height,
            boolean dontResize) {
        Bitmap bitmap=null;

        if (resources != null) {  // Иконка в ресурсах
            bitmap=BitmapFactory.decodeResource(resources, resId);
            try {
                bitmap=ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeResource(resources, resId), height, height);
            } catch (Exception e) {  // Исходный bitmap меньше требуемого (в gallery) - оставляем как есть
//                e.printStackTrace();
            }
            return bitmap;
        }
        if (height<=MINI_THUMBNAIL_HEIGHT && afUri!=null) {  // Получаем уже созданные или просим создать
            Utils.logD(TAG,"Decoding by URI: "+afKind+" "+afUri+" "+afFilePath+" "+height);

            int kind=height<=MICRO_THUMBNAIL_HEIGHT ?
                    MediaStore.Images.Thumbnails.MICRO_KIND : MediaStore.Images.Thumbnails.MINI_KIND;
            if (afKind==Walk.AFKIND_PHOTO) {
                bitmap=MediaStore.Images.Thumbnails.getThumbnail(
                        curActivity.getContentResolver(),
                        Long.parseLong(Uri.parse(afUri).getLastPathSegment()),
                        kind, null);
                bitmap=rotateBitmap(bitmap, afFilePath);
            } else if (afKind==Walk.AFKIND_VIDEO) {
                bitmap=MediaStore.Video.Thumbnails.getThumbnail(
                        curActivity.getContentResolver(),
                        Long.parseLong(Uri.parse(afUri).getLastPathSegment()),
                        kind, null);
            }
        }
        if (bitmap==null) { // Нужен большой или Uri почему-то неизвестен или bitmap почему-то не создался - сами создаем из файла
            if (afKind==Walk.AFKIND_PHOTO) {
                Utils.logD(TAG,"Decoding image file: "+afFilePath+" "+height);

                bfo.inJustDecodeBounds=true; // Узнаем размер исходной картинки
                BitmapFactory.decodeFile(afFilePath, bfo);
                bfo.inJustDecodeBounds=false;
                bfo.inMutable=true;
                bfo.inTargetDensity=metrics.densityDpi;  // Так ...
                if (false) {//dontResize) //TODO: Не переворачивает. И пропадает ! Разобраться !
                    bfo.inScaled=false;
                    bfo.inDensity=0;
                } else {
                    bfo.inScaled=true;
                    bfo.inDensity=bfo.inTargetDensity*bfo.outHeight/height; // и только так !
                }
                bitmap=BitmapFactory.decodeFile(afFilePath, bfo);
                bitmap=rotateBitmap(bitmap, afFilePath);
            } else if (afKind==Walk.AFKIND_VIDEO) {
                Utils.logD(TAG,"Decoding video file: "+afFilePath+" "+height);

                bitmap=ThumbnailUtils.createVideoThumbnail(afFilePath,
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
        if (!bitmap.isMutable()) {
            bitmap=bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }

        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float height=curActivity.getResources().getInteger(R.integer.text_on_bmp_height)/100.0F;
        paint.setTextSize((int) (bitmap.getHeight()*height));
        paint.setFakeBoldText(true);
//        paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);
        Rect bounds=new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int x=(bitmap.getWidth()-bounds.width())/2; // textCorner==0
        int y=(bitmap.getHeight()+bounds.height())/2;
        float padding=curActivity.getResources().getInteger(R.integer.text_on_bmp_padding)/100.0F;
        int padX=(int) (padding*bitmap.getWidth());
        int padY=(int) (padding*bitmap.getHeight());
        if (textCorner==0) {  // Координаты левого нижнего угла прямоугольника в котором текст
            x=Math.max(bitmap.getWidth()/2-bounds.width()/2, 0);
            y=bitmap.getHeight()/2+bounds.height()/2;
        } else if (textCorner==1) {
            x=padX;
            y=bounds.height()+padY;
        } else if (textCorner==2) {
            x=Math.max(bitmap.getWidth()-padX-bounds.width(), 0);
            y=bounds.height()+padY;
        } else if (textCorner==3) {
            x=Math.max(bitmap.getWidth()-padX-bounds.width(), 0);
            y=Math.max(bitmap.getHeight()-padY, 0);
        } else if (textCorner==4) {
            x=padX;
            y=Math.max(bitmap.getHeight()-padY, 0);
        }

        paint.setColor(color);
        if (color==-1) {
            paint.setColor(getContrastColor(
                    Bitmap.createBitmap(bitmap, x, Math.max(y-bounds.height(), 0),
                            Math.min(bounds.width(), bitmap.getWidth()-x),
                            Math.min(bounds.height(), bitmap.getHeight()-y))));
        }

        new Canvas(bitmap).drawText(text,x,y,paint);
        return bitmap;
    }
    static Bitmap addOverlayOnBitmap(Bitmap bitmap, Resources resources, int resId) {
        // Получаем overlay - Bitmap такой же высоты, как и bitmap
        bfo.inJustDecodeBounds=true; // Узнаем размер исходной картинки
        BitmapFactory.decodeResource(resources, resId, bfo);
        bfo.inJustDecodeBounds=false;
        bfo.inTargetDensity=metrics.densityDpi;  // Так ...
        bfo.inDensity=bfo.inTargetDensity*bfo.outHeight/bitmap.getHeight(); // и только так !
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
    static int getContrastColor (Bitmap bitmap) {
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
    }
    public static Bitmap rotateBitmap(Bitmap bitmap, String bitmapPath) {
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
