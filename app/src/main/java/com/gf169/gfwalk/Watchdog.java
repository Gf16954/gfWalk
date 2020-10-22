package com.gf169.gfwalk;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

public class Watchdog extends Service {
    static final String TAG = "gfWatchdog";
    static final String ACTION_START = "Start";
    static final String ACTION_STOP = "Stop";

    boolean isWorking;
    Thread waitchdogThread;

    @Override
    public void onCreate() {
        Utils.logD(TAG, "onCreate");

        SharedPreferences walkSettings =
            SettingsActivity.getCurrentWalkSettings(this, -1);  // Глобальные
        WalkRecorder.switchDevelopersLog(true, walkSettings);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.logD(TAG, "onStartCommand " + (intent == null ? null : intent.getAction()));

        if (intent == null) return START_STICKY;

        final String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            if (isWorking) return START_STICKY;
            isWorking = true;

            int walkId = intent.getIntExtra("walkId", 0);
            int timeout = 10000;
            waitchdogThread = new Thread(() ->  {
                Utils.logD(TAG, "Watchdog thread started");

                boolean interrupted = false;
                while (!interrupted) {
                    Utils.logD(TAG, "Watchdog is working");

                    Intent intent2 = new Intent(this, WalkRecorder.class);
                    intent2.setAction(WalkRecorder.ACTION_START)
                        .putExtra("walkId", walkId)
                        .putExtra("restart", true);
                    startService(intent2);

                    interrupted = Utils.sleep(timeout, true);
                }

                Utils.logD(TAG, "Watchdog thread ended");
                WalkRecorder.switchDevelopersLog(false, null);
            },"gfWatchdogThread");
            waitchdogThread.start();

        } else if (ACTION_STOP.equals(action)) {
            if (waitchdogThread != null) {
                waitchdogThread.interrupt();
            }
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
