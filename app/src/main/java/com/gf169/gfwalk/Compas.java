package com.gf169.gfwalk;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public class Compas implements SensorEventListener {
    static final String TAG = "gfCompas";

    private SensorManager mSensorManager;
    private final float[] mAccelerometerReading = new float[3];
    private final float[] mMagnetometerReading = new float[3];

    private final float[] mRotationMatrix = new float[9];
    private final float[] mOrientationAngles = new float[3];

    private Sensor mAccelerometer;
    private Sensor mMagnetometer;

    private Context context;
    public boolean isDisabled;
    public boolean isTurnedOn;

    private Display display;

    Compas(Context context) {
        if (isDisabled) return;

        this.context=context;

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer==null) {
            Utils.logD(TAG, "No accelerometer!");
            isDisabled=true;
            return;
        }
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (mMagnetometer==null) {
            Utils.logD(TAG, "No magnetometer!");
            isDisabled = true;
            return;
        }

        display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
        // You must implement this callback in your code.
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, mAccelerometerReading,
                    0, mAccelerometerReading.length);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mMagnetometerReading,
                    0, mMagnetometerReading.length);
        }
        updateOrientationAngles();
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    private void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(mRotationMatrix, null,
                mAccelerometerReading, mMagnetometerReading);
        // "mRotationMatrix" now has up-to-date information.
        SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);
        // "mOrientationAngles" now has up-to-date information.

//        getAzimuth();
    }

    float getAzimuth() {
        float azimuth;
        switch (display.getRotation()) {
            case Surface.ROTATION_90:
                azimuth = (float) Math.atan2(mRotationMatrix[0],mRotationMatrix[3]);
                break;
            case Surface.ROTATION_270:
                azimuth = (float) Math.atan2(-mRotationMatrix[0],-mRotationMatrix[3]);
                break;
            default:
                azimuth = mOrientationAngles[0]; // = Math.atan2(mRotationMatrix[1],mRotationMatrix[4])
        }
        // Utils.logD(TAG, "azimuth "+(azimuth>=0 ? azimuth : azimuth + 6.28f)/3.14f*180);
        return (azimuth>=0 ? azimuth : azimuth + 6.28f)/3.14f*180; // 0 - 360
    }

    boolean turnOn(boolean on) {
        if (isDisabled) return false;

        if (on) {
            // Get updates from the accelerometer and magnetometer at a constant rate.
            // To make batch operations more efficient and reduce power consumption,
            // provide support for delaying updates to the application.
            //
            // In this example, the sensor reporting delay is small enough such that
            // the application receives an update before the system checks the sensor
            // readings again.
            if (isTurnedOn) return true;
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_UI);
            isTurnedOn=true;
        } else {
            if (!isTurnedOn) return true;
            mSensorManager.unregisterListener(this);
            isTurnedOn=false;
        }
        return true;
    }
}
