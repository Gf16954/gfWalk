package com.gf169.gfwalk;

import android.app.Application;
import android.content.Context;

public class MyApplication extends Application {
    static Context appContext;

    public void onCreate(){
        super.onCreate();

        appContext = getApplicationContext();
        Utils.ini(appContext);
        MyMarker.ini(appContext);
    }
}
