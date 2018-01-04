package com.fridgecow.smartalarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;

import com.jjoe64.graphview.series.DataPoint;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by tom on 04/01/18.
 */

public class SleepView extends View {
    private SleepData mSleepData;

    //Things for drawing
    private Paint mBackgroundPaint;
    private Paint mForegroundPaint;
    private TextPaint mTextPaint;

    private String mDateLabel;
    private String mStartTime;
    private String mEndTime;
    private float mEndWidth;

    private float mTextHeight;

    public SleepView(Context context) {
        super(context);

        init(context);
    }

    public void attachSleepData(SleepData data){
        mSleepData = data;

        //Extract a start and end time, and a date label
        Date startDate = new Date((long) data.getStart());
        Date endDate = new Date((long) data.getEnd());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        timeFormat.setTimeZone(TimeZone.getDefault());

        SimpleDateFormat dateFormat = new SimpleDateFormat("E d ''yy", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());

        mStartTime = timeFormat.format(startDate);
        mEndTime = timeFormat.format(endDate);

        mEndWidth = mTextPaint.measureText(mEndTime);

        if(dateFormat.format(startDate).equals(dateFormat.format(endDate))){
            mDateLabel = dateFormat.format(startDate);
        }else {
            mDateLabel = dateFormat.format(startDate)+" - "+dateFormat.format(endDate);
        }
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

        mBackgroundPaint.setColor(Color.GRAY);
        mForegroundPaint.setColor(Color.WHITE);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(20);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;
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
            canvas.drawRect(left, top + 5, contentWidth, contentHeight - mTextHeight - top - 15, mBackgroundPaint);

            //Draw Start and end time
            canvas.drawText(mDateLabel, left, top, mTextPaint);
            canvas.drawText(mStartTime, left, contentHeight - mTextHeight, mTextPaint);
            canvas.drawText(mEndTime, contentWidth - mEndWidth, contentHeight - mTextHeight, mTextPaint);

            //Draw Regions
            final float start = (float) mSleepData.getStart(), end = (float) mSleepData.getEnd();
            final float sleepLength = end - start;
            for(DataRegion d : mSleepData){
                if(d.getLabel().equals(SleepData.WAKEREGION)){
                    final float regionLeft = left + ((float) (d.getStart() - start)*contentWidth)/sleepLength;
                    final float regionRight = left + ((float) (d.getEnd() - start)*contentWidth)/sleepLength;

                    canvas.drawRect(regionLeft, top + 5, regionRight, contentHeight - top -mTextHeight - 15, mForegroundPaint);
                }
            }
        }
    }
}
