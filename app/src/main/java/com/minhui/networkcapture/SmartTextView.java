package com.minhui.networkcapture;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/9.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class SmartTextView extends android.support.v7.widget.AppCompatTextView {


    public SmartTextView(Context context) {
        super(context);
    }

    public SmartTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SmartTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
        }catch (Exception e){

        }

    }
}
