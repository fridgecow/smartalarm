package com.fridgecow.smartalarm;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * TODO: document your custom view class.
 */
public class CircularInputView extends View {
    private int mTextColor = ContextCompat.getColor(getContext(), R.color.card_text_color);
    private int mBackgroundColor = ContextCompat.getColor(getContext(), R.color.card_default_background);
    private int mThickness = 10;
    private int mMin = 0;
    private int mMax = 60;
    private double mAngle = 0;

    private TextPaint mTextPaint;
    private Paint mBackgroundPaint;
    private Paint mTransparentPaint;

    private float mTextHeight;
    private int mNumbers = 12;

    //Touch related
    private boolean mScrolling = false;
    private float mLastX;
    private float mLastY;

    //Layout related
    private int mPaddingLeft;
    private int mPaddingTop;
    private int mPaddingRight;
    private int mPaddingBottom;
    private int mContentWidth;
    private int mContentHeight;
    private int mOuterRadius;
    private int mInnerRadius;
    private int mMidRadius;
    private int mCenterX;
    private int mCenterY;


    public CircularInputView(Context context) {
        super(context);
        init(null, 0);
    }

    public CircularInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public CircularInputView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.CircularInputView, defStyle, 0);

        mTextColor = a.getColor(
                R.styleable.CircularInputView_textColor, mTextColor);
        mBackgroundColor = a.getColor(
                R.styleable.CircularInputView_backgroundColor,
                mBackgroundColor);

        // Use getDimensionPixelSize or getDimensionPixelOffset when dealing with
        // values that should fall on pixel boundaries.
        mThickness = a.getDimensionPixelSize(
                R.styleable.CircularInputView_thickness,
                mThickness);

        a.recycle();

        // Set up a default TextPaint object
        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.LEFT);

        mBackgroundPaint = new Paint();

        mTransparentPaint = new Paint();
        mTransparentPaint.setColor(getResources().getColor(android.R.color.transparent));
        mTransparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // Update paint and text measurements from attributes
        invalidatePaintAndMeasurements();
    }

    private void invalidatePaintAndMeasurements() {
        //TextPaint
        setTextSizeForWidth(mTextPaint, mThickness, Integer.toString(mMax));
        mTextPaint.setColor(mTextColor);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;

        //Background Paint
        mBackgroundPaint.setColor(mBackgroundColor);

        //Measure self
        mPaddingLeft = getPaddingLeft();
        mPaddingTop = getPaddingTop();
        mPaddingRight = getPaddingRight();
        mPaddingBottom = getPaddingBottom();

        mContentWidth = getWidth() - mPaddingLeft - mPaddingRight;
        mContentHeight = getHeight() - mPaddingTop - mPaddingBottom;

        mOuterRadius = Math.min(mContentWidth, mContentHeight);
        mInnerRadius = mOuterRadius - mThickness;
        mMidRadius = mOuterRadius - mThickness / 2;

        mCenterX = getWidth() / 2;
        mCenterY = getHeight() / 2;
    }

    @Override
    protected void onFinishInflate(){
        super.onFinishInflate();
    }
    /* stackoverflow.com/questions/12166476/android-canvas-drawtext-set-font-size-from-width */
    private static void setTextSizeForWidth(Paint paint, float desiredWidth,
                                            String text) {
        final float testTextSize = 48f;

        // Get the bounds of the text, using our testTextSize.
        paint.setTextSize(testTextSize);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        // Calculate the desired size as a proportion of our testTextSize.
        float desiredTextSize = testTextSize * desiredWidth / bounds.width();

        // Set the paint for that size.
        paint.setTextSize(desiredTextSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // TODO: cache as many computations as possible
        canvas.drawCircle(mCenterX, mCenterY, mOuterRadius, mBackgroundPaint);
        canvas.drawCircle(mCenterX, mCenterY, mInnerRadius, mTransparentPaint);

        final double angleDelta = (Math.PI * 2) / mNumbers;
        final double numberDelta = (mMax - mMin) / mNumbers;
        for(int i = 0; i < mNumbers; i++){
            final double angle = i*angleDelta + mAngle - Math.PI/2;
            final String number = Integer.toString((int) (i*numberDelta + mMin));

            final int x = (int) (mCenterX + Math.cos(angle)*mMidRadius);
            final int y = (int) (mCenterY + Math.cos(angle)*mMidRadius);

            final float textWidth = mTextPaint.measureText(number);

            canvas.drawText(number, x - textWidth/2, y - mTextHeight/2, mTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final int action = event.getAction();

        if(action == MotionEvent.ACTION_DOWN){
            //Check where touch occurred
            final double dist = Math.pow(x - mCenterX, 2) + Math.pow(y - mCenterY, 2);
            if(dist > mInnerRadius){
                mScrolling = true;
                mLastX = x;
                mLastY = y;
                return true;
            }else {
                mScrolling = false;
            }
        }else if(action == MotionEvent.ACTION_UP){
            mScrolling = false;
            return true;
        }else if(action == MotionEvent.ACTION_MOVE){
            //Find angular difference between positions
            //Use the cosine rule

            //Todo: cache b and bsq to half the amount of work
            final double asq = Math.pow(mCenterX - mLastX, 2) + Math.pow(mCenterY - mLastY, 2);
            final double bsq = Math.pow(mCenterX - x, 2) + Math.pow(mCenterY - y, 2);
            final double csq = Math.pow(mLastX - x, 2) + Math.pow(mLastY - y, 2);

            final double a = Math.sqrt(asq);
            final double b = Math.sqrt(bsq);

            final double cosdA = (asq + bsq - csq) / 2*a*b;
            setAngle(getAngle() + Math.acos(cosdA));

            return true;
        }

        return super.onTouchEvent(event);
    }

    public int getMin() {
        return mMin;
    }

    public void setMin(int min) {
        this.mMin = min;
    }

    public int getMax(){
        return mMax;
    }

    public void setMax(int max){
        mMax = max;
    }

    public void setThickness(int thickness){
        mThickness = thickness;
        invalidatePaintAndMeasurements();
    }

    public int getThickness(){
        return mThickness;
    }

    public void setTextColor(int color){
        mTextColor = color;
        invalidatePaintAndMeasurements();
    }

    public int getTextColor(){
        return mTextColor;
    }

    public void setBackgroundColor(int color){
        mBackgroundColor = color;
        invalidatePaintAndMeasurements();
    }

    public int getBackgroundColor(){
        return mBackgroundColor;
    }

    public double getAngle() {
        return mAngle;
    }

    public void setAngle(double mAngle) {
        this.mAngle = mAngle;
    }
}
