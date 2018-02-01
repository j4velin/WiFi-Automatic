package de.j4velin.wifiAutoOff;

import android.content.Context;
import android.graphics.Color;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class NoticePreference extends Preference {

    public NoticePreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(final ViewGroup parent) {
        View v = super.onCreateView(parent);
        v.setBackgroundColor(Color.parseColor("#66ccff"));
        return v;
    }
}
