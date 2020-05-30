/**
 * Created by gf on 20.09.2015.
 */
package com.gf169.gfwalk;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class EditTextActivity extends Activity {
    static final String TAG = "gfEditTextActivity";

    String textFilePath;
    String s;
    Intent resultIntent = new Intent();
    String textIni="";
    boolean buttonsEnabled=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreate");
        super.onCreate(savedInstanceState);

//        AirbrakeNotifier.register(this,
//                "f17762b5ea71e1af3bcf37ba0cb2a67c",
//                "", false);

        setContentView(R.layout.activity_edittext);
        Bundle extras = getIntent().getExtras();
        textFilePath=extras.getString("textFilePath");
        setTitle(textFilePath);

        int screenSize=(getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK);
        if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
                screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL /* &&
                    getResources().getConfiguration().orientation==
                            Configuration.ORIENTATION_LANDSCAPE */) {
//            findViewById(android.R.id.title).getLayoutParams().height = Utils.dpyToPx(20);
//            setTitle(Html.fromHtml("<font size=\"3\">"+textFilePath+"</font>"));
//            ((TextView) findViewById(android.R.id.title)).
//                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(textFilePath))));
            while ((s=reader.readLine()) != null) textIni+=s+"\n";
            reader.close();
        }
        catch (Exception e) {
//            e.printStackTrace();
            Toast.makeText(this,
                   String.format(
                            getResources().getString(R.string.format_could_not_read_from_file),
                            textFilePath),
                    Toast.LENGTH_LONG).show();
            setResult(Activity.RESULT_CANCELED, resultIntent);
            finish();
            return;
        }
        ((TextView) findViewById(R.id.textViewEditText)).setText(textIni);
        EditText editText = (EditText)findViewById(R.id.textViewEditText);
        editText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (s.length()!=textIni.length() ||
                        !s.toString().equals(textIni)) {
                    if (!buttonsEnabled) {
                        buttonsEnabled=true;
                        enableButtons(true);
                    }
                } else {
                    if (buttonsEnabled) {
                        buttonsEnabled=false;
                        enableButtons(false);
                    }
                }
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        findViewById(R.id.buttonEditTextOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saveText()) {
                    setResult(Activity.RESULT_OK, resultIntent);
                } else {
                    setResult(Activity.RESULT_CANCELED, resultIntent);
                }
                finish();
            }
        });
        findViewById(R.id.buttonEditTextCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED, resultIntent);
                finish();
            }
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    boolean saveText() {
        Utils.logD(TAG, "saveText");

        s=((TextView) findViewById(R.id.textViewEditText)).getText().toString();
        try {
            OutputStreamWriter writer=new OutputStreamWriter(
                    new FileOutputStream(new File(textFilePath)));
            writer.write(s);
            writer.close();
            return true;
        } catch (IOException e) {
            Toast.makeText(this,
                    String.format(
                            getResources().getString(R.string.format_could_not_write_to_file),
                            textFilePath),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    void enableButtons(boolean state) {
        findViewById(R.id.buttonEditTextOk).setVisibility(state ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.buttonEditTextCancel).setVisibility(state ? View.VISIBLE : View.INVISIBLE);
    };
}
