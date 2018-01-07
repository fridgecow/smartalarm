package com.fridgecow.smartalarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by tom on 04/01/18.
 */

public class SleepView extends View {
    private SleepSummaryData mSleepData;

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

    public SleepView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    public SleepView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void attachSleepData(SleepSummaryData data){
        mSleepData = data;

        //Extract a start and end time, and a date label
        Date startDate = new Date((long) data.getStart());
        Date endDate = new Date((long) data.getEnd());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        timeFormat.setTimeZone(TimeZone.getDefault());

        SimpleDateFormat dateFormat = new SimpleDateFormat("E d MMM ''yy", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());

        mStartTime = timeFormat.format(startDate);
        mEndTime = timeFormat.format(endDate);

        mEndWidth = mTextPaint.measureText(mEndTime);

        if(dateFormat.format(startDate).equals(dateFormat.format(endDate))){
            mDateLabel = dateFormat.format(startDate);
        }else {
            mDateLabel = dateFormat.format(startDate)+" - "+dateFormat.format(endDate);
        }

        Rect bounds = new Rect();
        mTextPaint.getTextBounds(mEndTime, 0, mEndTime.length(), bounds);
        mTextHeight = bounds.height();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        int width, height;

        width = MeasureSpec.getSize(widthMeasureSpec);

        if(getVisibility() != View.GONE){
            height = 100;
        }else{
            height = 0;
        }

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
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public void onDraw(Canvas canvas){
        final int width = getWidth(), height = getHeight();
        final int left = getPaddingLeft(), top = getPaddingTop(), right = getPaddingRight(), bottom = getPaddingBottom();
        final int contentWidth = width - left - right;
        //Draw Test Text
        //canvas.drawText("Test", left, top, mTextPaint);

        if(mSleepData != null){
            final float tickerTop = top + mTextHeight + 5;
            final float tickerBottom = height - mTextHeight - 5 - bottom;

            //Draw background
            //mBackgroundPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(left, tickerTop, width - right, tickerBottom, mBackgroundPaint);

            //Draw Regions
            final float start = (float) mSleepData.getStart(), end = (float) mSleepData.getEnd();
            final float sleepLength = end - start;
            final float pixelScale = contentWidth/sleepLength;

            for(DataRegion d : mSleepData){
                if(d.getLabel().equals(SleepSummaryData.WAKEREGION)){
                    final float regionLeft = left + ((float) (d.getStart() - start)*pixelScale);
                    final float regionRight = left + ((float) (d.getEnd() - start)*pixelScale);

                    canvas.drawRect(regionLeft, tickerTop, regionRight, tickerBottom, mForegroundPaint);
                }
            }

            //Draw Start and end time
            canvas.drawText(mDateLabel, left, top + mTextHeight, mTextPaint);
            canvas.drawText(mStartTime, left, tickerBottom + 5 + mTextHeight, mTextPaint);
            canvas.drawText(mEndTime, width - mEndWidth - right, tickerBottom + 5 + mTextHeight, mTextPaint);
        }
    }
}
