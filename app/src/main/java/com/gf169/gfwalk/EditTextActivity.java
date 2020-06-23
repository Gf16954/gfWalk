package com.gf169.gfwalk;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class EditTextActivity extends AppCompatActivity {
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
            StringBuilder s2 = new StringBuilder();
            while ((s=reader.readLine()) != null) s2.append(s).append("\n");
            textIni = s2.toString();
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
        EditText editText = findViewById(R.id.textViewEditText);
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
        editText.requestFocus();  // !!!
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

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
    }

    boolean saveText() {
        Utils.logD(TAG, "saveText");

        s=((TextView) findViewById(R.id.textViewEditText)).getText().toString().trim() + "   ";
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
