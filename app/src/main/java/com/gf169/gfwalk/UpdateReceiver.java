package com.gf169.gfwalk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateReceiver extends BroadcastReceiver {
    static final String TAG = "gfUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Utils.logD(TAG, "onReceive");

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
