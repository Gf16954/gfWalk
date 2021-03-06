/**
 * Created by gf on 20.09.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {
    static final String TAG = "gfHelpActivity";

    String s;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate");

        SharedPreferences globalSettings = SettingsActivity.getCurrentWalkSettings(this, -1);
        setTheme(MainActivity.getThemeX(globalSettings));
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_help);
        setTitle(getResources().getString(R.string.app_name) +
            " " + BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")" +
            " " + getResources().getString(R.string.app_copyright));
        TextView textView = findViewById(R.id.textViewHelp);
        textView.setText(Html.fromHtml(getResources().getString(R.string.help)));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
