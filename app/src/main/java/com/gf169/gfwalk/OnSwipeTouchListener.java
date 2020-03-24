package com.gf169.gfwalk;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by gf on 08.10.2015.
 http://stackoverflow.com/questions/4139288/android-how-to-handle-right-to-left-swipe-gestures
*/
public class OnSwipeTouchListener implements View.OnTouchListener {
    static final String TAG = "gfOnSwipeTouchListener";

    private GestureDetector gestureDetector;

    public OnSwipeTouchListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
    }
    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }
    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD=100;
        private static final int SWIPE_VELOCITY_THRESHOLD=100;

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            Utils.logD(TAG, "onFling");
            boolean result=false;
            try {
                float diffY=e2.getY()-e1.getY();
                float diffX=e2.getX()-e1.getX();
                if (Math.abs(diffX)>Math.abs(diffY)) {
                    if (Math.abs(diffX)>SWIPE_THRESHOLD && Math.abs(velocityX)>SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX>0) {
                            result=onSwipeRight();
                        } else {
                            result=onSwipeLeft();
                        }
                    }
                } else {
                    if (Math.abs(diffY)>SWIPE_THRESHOLD && Math.abs(velocityY)>SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY>0) {
                            result=onSwipeBottom();
                        } else {
                            result=onSwipeTop();
                        }
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            onClick();
            return super.onSingleTapConfirmed(e);
        }
    }
    public boolean onSwipeRight() {
        return false;
    }
    public boolean onSwipeLeft() {
        return false;
    }
    public boolean onSwipeTop() {
        return false;
    }
    public boolean onSwipeBottom() {
        return false;
    }
    public boolean onClick(){
        return false;
    }
}