/**
 * Created by gf on 20.09.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

public class PrivacyPolicyActivity extends Activity {
    static final String TAG = "gfPrivacyPolicyActivity";

    String s;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_help);
        setTitle(getResources().getString(R.string.app_name)+
                " "+BuildConfig.VERSION_NAME+"("+BuildConfig.VERSION_CODE+")"+
                " "+getResources().getString(R.string.app_copyright));
        ((TextView) findViewById(R.id.textViewHelp)).setText(
                Html.fromHtml(getResources().getString(R.string.help)));
    }
}
