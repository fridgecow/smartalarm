package com.fridgecow.smartalarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;

import com.jjoe64.graphview.series.DataPoint;

/**
 * Created by tom on 04/01/18.
 */

public class SleepView extends View {
    private SleepData mSleepData;

    //Things for drawing
    private Paint mBackgroundPaint;
    private Paint mForegroundPaint;
    private TextPaint mTextPaint;

    public SleepView(Context context) {
        super(context);

        init(context);
    }

    public void attachSleepData(SleepData data){
        mSleepData = data;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        int width, height;

        width = MeasureSpec.getSize(widthMeasureSpec);
        height = 100;

        setMeasuredDimension(width, height);
    }

    private void init(Context context){
        mBackgroundPaint = new Paint();
        mForegroundPaint = new Paint();
        mTextPaint = new TextPaint();

        mBackgroundPaint.setColor(Color.BLACK);
        mForegroundPaint.setColor(Color.WHITE);
        mTextPaint.setColor(Color.GRAY);
        mTextPaint.setTextSize(20);
    }

    @Override
    public void onDraw(Canvas canvas){
        final int width = getWidth(), height = getHeight();
        final int left = getPaddingLeft(), top = getPaddingTop();
        final int contentWidth = width - left - getPaddingRight(),
                contentHeight = height - top - getPaddingBottom();

        //Draw Test Text
        //canvas.drawText("Test", left, top, mTextPaint);

        if(mSleepData != null){
            //Draw background
            mBackgroundPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(left, top, contentWidth, contentHeight, mBackgroundPaint);

            //Draw Regions
            final float start = (float) mSleepData.getStart(), end = (float) mSleepData.getEnd();
            final float sleepLength = end - start;
            for(DataRegion d : mSleepData){
                if(d.getLabel().equals(SleepData.WAKEREGION)){
                    final float regionLeft = left + ((float) (d.getStart() - start)*contentWidth)/sleepLength;
                    final float regionRight = left + ((float) (d.getEnd() - start)*contentWidth)/sleepLength;
                    Log.d("SleepView", "Region "+regionLeft+"-"+regionRight);
                    canvas.drawRect(regionLeft, top, regionRight, contentHeight, mForegroundPaint);
                }
            }
        }
    }
}
