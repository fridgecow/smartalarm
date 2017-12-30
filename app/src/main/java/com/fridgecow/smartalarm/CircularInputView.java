package com.fridgecow.smartalarm;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.v4.content.ContextCompat;
import android.support.wear.widget.SwipeDismissFrameLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.math.BigDecimal;

/**
 * TODO: document your custom view class.
 */
public class CircularInputView extends View {
    private static final String TAG = CircularInputView.class.getSimpleName();
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
    private int mNumber = mMin;

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

    public static abstract class onChangeListener{
        public abstract void onChange(int newNumber);
    }
    private onChangeListener mListener;

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
        setTextSizeForWidth(mTextPaint, mThickness/2, Integer.toString(mMax));
        mTextPaint.setColor(mTextColor);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;

        //Background Paint
        mBackgroundPaint.setColor(mBackgroundColor);
    }

    @Override
    protected void onFinishInflate(){
        super.onFinishInflate();
    }
    /* stackoverflow.com/questions/12166476/android-canvas-drawtext-set-font-size-from-width */
    private static void setTextSizeForWidth(Paint paint, float desiredWidth, String text) {
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
        //Measure self
        mPaddingLeft = getPaddingLeft();
        mPaddingTop = getPaddingTop();
        mPaddingRight = getPaddingRight();
        mPaddingBottom = getPaddingBottom();

        //Log.d(TAG, "Padding: "+mPaddingTop+","+mPaddingRight+","+mPaddingBottom+","+mPaddingLeft);

        mContentWidth = canvas.getWidth() - mPaddingLeft - mPaddingRight;
        mContentHeight = canvas.getHeight() - mPaddingTop - mPaddingBottom;

        //Log.d(TAG, "Dimensions: "+mContentWidth+","+mContentHeight);

        mOuterRadius = Math.min(mContentWidth, mContentHeight) / 2;
        mInnerRadius = mOuterRadius - mThickness;
        mMidRadius = mOuterRadius - mThickness / 2;

        //Log.d(TAG, "Radii: "+mInnerRadius+","+mMidRadius+","+mOuterRadius);

        mCenterX = canvas.getWidth() / 2;
        mCenterY = canvas.getHeight() / 2;

        //Log.d(TAG, "Center: "+mCenterX+","+mCenterY);

        canvas.drawCircle(mCenterX, mCenterY, mOuterRadius, mBackgroundPaint);
        canvas.drawCircle(mCenterX, mCenterY, mInnerRadius, mTransparentPaint);

        final double angleDelta = (Math.PI * 2) / mNumbers;
        final double numberDelta = (mMax - mMin) / mNumbers;
        for(int i = 0; i < mNumbers; i++){
            final double angle = i*angleDelta + mAngle - Math.PI/2;
            final String number = Integer.toString((int) (i*numberDelta + mMin));

            final int x = (int) (mCenterX + Math.cos(angle)*mMidRadius);
            final int y = (int) (mCenterY + Math.sin(angle)*mMidRadius);

            //Log.d(TAG, "Drawing "+number+" at "+x+","+y);

            final float textWidth = mTextPaint.measureText(number);

            canvas.drawText(number, x - textWidth/2, y + mTextHeight/2, mTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final int action = event.getAction();

        if(action == MotionEvent.ACTION_DOWN){
            Log.d(TAG, "Touch Down");
            //Check where touch occurred
            final double dist = Math.pow(x - mCenterX, 2) + Math.pow(y - mCenterY, 2);
            if(dist > Math.pow(mInnerRadius, 2)){
                Log.d(TAG, "Within Boundary");
                mScrolling = true;
                mLastX = x;
                mLastY = y;
                return true;
            }else {
                mScrolling = false;
            }
        }else if(action == MotionEvent.ACTION_UP){
            Log.d(TAG, "Touch Up");
            mScrolling = false;
            return true;
        }else if(action == MotionEvent.ACTION_MOVE && mScrolling){
            //Find angular difference between positions
            //Use the cosine rule
            //Log.d(TAG, "X: "+x+" Y: "+y);

            final double curAngle = Math.atan2(mCenterY - y, mCenterX - x);
            final double lastAngle = Math.atan2(mCenterY - mLastY, mCenterX - mLastX);

            //Log.d(TAG, "Current: "+curAngle+" Last: "+lastAngle);

            final double dAngle = Math.atan2(Math.sin(curAngle-lastAngle), Math.cos(curAngle-lastAngle));

            //Log.d(TAG, "Delta: "+dAngle);

            setAngle(getAngle() + dAngle);

            mLastX = x;
            mLastY = y;

            return true;
        }

        return super.onTouchEvent(event);
    }

    public void setOnChangeListener(onChangeListener listener){
        mListener = listener;
        listener.onChange(mNumber);
    }

    public void clearOnChangeListener(){
        mListener = null;
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

    public void setAngle(double angle) {
        mAngle = angle % (Math.PI*2);
        if(mAngle < 0){
            mAngle += Math.PI*2;
        }

        //Get number from angle around circle
        int number = mMax - (int) ((angle / (Math.PI*2))*(mMax - mMin));

        if(mListener != null && number != mNumber){
            mNumber = number;
            mListener.onChange(mNumber);
        }
        invalidate();
    }

    public int getNumber(){
        return mNumber;
    }

    public boolean canScrollHorizontally(int direction) {
        return mScrolling;
    }
}
