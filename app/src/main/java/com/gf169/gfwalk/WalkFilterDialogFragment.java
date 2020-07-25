/**
 * Created by gf on 20.08.2015.
 */
package com.gf169.gfwalk;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.Date;

public class WalkFilterDialogFragment extends DialogFragment implements View.OnClickListener {
    static final String TAG = "gfWalkFilterDlgFragment";

    static MainActivity mainActivity;
    View v;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Utils.logD(TAG, "onCreateView");

/*
        String s=getResources().getString(R.string.filter_show_walks);
        getDialog().setTitle(s);
        int screenSize=(getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK);
        if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
                screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            getDialog().findViewById(android.R.id.title).getLayoutParams().height = Utils.dpyToPx(30);
            ((TextView) getDialog().findViewById(android.R.id.title)).
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        }
*/
        v = getDialog().findViewById(android.R.id.title);
        if (v != null) { // API 21 и далее... В 28-ом уже title bar'a нет
            v.getLayoutParams().height = 0;
        }

        v = inflater.inflate(R.layout.fragment_walkfilter, null);
        v.findViewById(R.id.buttonApplyFilter).setOnClickListener(this);
        v.findViewById(R.id.buttonClearFilter).setOnClickListener(this);

        setFilterParms(v, mainActivity.filterParms);

        return v;
    }

    public void onClick(View v) {
        Utils.logD(TAG, ""+((Button) v).getText());

        if (v==v.findViewById(R.id.buttonApplyFilter)) {
            formFilterParmsAndStr();
            MainActivity.pleaseDo="refresh entire list";
            dismiss();  // Вернемся сразу в Main
        } else if (v==v.findViewById(R.id.buttonClearFilter)) {
            setFilterParms((View) v.getParent(), null);
        }
    }

    public void onDismiss(DialogInterface dialog) { // При возврате в Main
        Utils.logD(TAG, "onDismiss "+new Date().toString());
        super.onDismiss(dialog);
    }

    public void onCancel(DialogInterface dialog) {  // При возврате в Main
        Utils.logD(TAG, "onCancel "+new Date().toString());
        super.onCancel(dialog);
    }

    void setFilterParms(View v, Bundle filterParms) {
        ViewGroup vg=(ViewGroup) v.findViewById(R.id.radioButtonsBean);
        TextView tv=(TextView) v.findViewById(R.id.textViewCommentContains2);
        if (filterParms==null) {
            ((RadioButton) vg.findViewById(R.id.radioButtonShowNotInBean)).setChecked(true);
            tv.setText("");
        } else {
            ((RadioButton) vg.findViewById(filterParms.getInt("inBean"))).setChecked(true);
            tv.setText(filterParms.getString("commentContains",""));
        }
    }

    void formFilterParmsAndStr() {
        Bundle b=new Bundle();
        String s;

        ViewGroup vg=(ViewGroup) getView().findViewById(R.id.radioButtonsBean);
        if (((RadioButton) vg.findViewById(R.id.radioButtonShowNotInBean)).isChecked()) {
            s=MainActivity.FILTER_STR_NOT_IN_BIN;
            b.putInt("inBean",R.id.radioButtonShowNotInBean);
        } else if (((RadioButton) vg.findViewById(R.id.radioButtonShowInBean)).isChecked()) {
            s="ifnull("+DB.KEY_DELETED+",0)=1";
            b.putInt("inBean",R.id.radioButtonShowInBean);
        } else {
            s="1=1";
            b.putInt("inBean",R.id.radioButtonShowAll);
        }

        TextView tv=(TextView) getView().findViewById(R.id.textViewCommentContains2);
        if (!tv.getText().toString().isEmpty()) {
            s=s+" AND "+DB.KEY_COMMENT+" like '%"+tv.getText()+"%'";
            b.putString("commentContains", ""+tv.getText());
        }
        mainActivity.filterStr=s;
        if (s.equals(MainActivity.FILTER_STR_NOT_IN_BIN)) {
            mainActivity.filterParms=null;
        } else {
            mainActivity.filterParms=b;
        }
    }
}
