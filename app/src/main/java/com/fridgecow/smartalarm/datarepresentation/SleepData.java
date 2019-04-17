package com.fridgecow.smartalarm.datarepresentation;

import android.content.Context;
import android.util.Log;

import com.fridgecow.smartalarm.interfaces.CSVable;
import com.jjoe64.graphview.series.DataPoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by tom on 07/01/18.
 */

public class SleepData implements CSVable {
    private static final String TAG = "SleepData";
    private static final String OFFLINE_ACC = "sleepdata.csv";
    private static final String OFFLINE_HRM = "sleephrm.csv";

    public static final int STEPMILLIS = 60000;
    private static final double P = 0.001;
    private static final double[] W = {106.0, 54.0, 58.0, 76.0, 230.0, 74.0, 67.0};
    private static final int DATAPOINTS_BEFORE_WRITEOUT = 1000;

    private Context mContext;

    private double maxAccel;
    private double maxHR;

    // Stores long-term tracking data
    private List<DataPoint> mSleepMotion;
    private List<DataPoint> mSleepHR;
    private List<DataPoint> mSleepSDNN;

    public SleepData(Context context){
        mContext = context;
        reset();
    }

    //For accelerometer
    private double mAccelMax = 0.0;
    private boolean mAccelDirty = false;

    //For HR
    private double mHRMax = 0.0;
    private boolean mHRDirty = false;
    private double mNNtotal = 0.0;
    private double mNNsum = 0.0;
    private double mNNsqsum = 0.0;

    public void reset(){
        mSleepHR = new ArrayList<>();
        mSleepMotion = new ArrayList<>();
        mSleepSDNN = new ArrayList<>();

        // Empty offline store and mAccelData etc
        try{
            final BufferedWriter DataStore = new BufferedWriter(new OutputStreamWriter(mContext.openFileOutput(OFFLINE_ACC, 0)));
            DataStore.close();

            final BufferedWriter DataStore2 = new BufferedWriter(new OutputStreamWriter(mContext.openFileOutput(OFFLINE_HRM, 0)));
            DataStore2.close();

        }catch(IOException e){
            Log.d(TAG, "Failed to reset");
        }
    }

    public void writeOut() throws IOException{
        Log.d(TAG, "Writing to offline store");

        writeListToCSV(mSleepMotion, OFFLINE_ACC);
        writeListToCSV(mSleepHR, OFFLINE_HRM);
    }

    private void writeListToCSV(List<DataPoint> list, String file) throws IOException{
        final BufferedWriter DataStore = new BufferedWriter(new OutputStreamWriter(mContext.openFileOutput(file, Context.MODE_APPEND)));
        for (DataPoint d : list) {
            DataStore.write(d.getX() + "," + d.getY() + "\n");
        }
        DataStore.close();
    }

    public void readIn() throws IOException{
        Log.d(TAG, "Reading from offline store");

        readCSVToList(OFFLINE_ACC, mSleepMotion);
        readCSVToList(OFFLINE_HRM, mSleepHR);
    }

    private void readCSVToList(String file, List<DataPoint> list) throws IOException{
        FileInputStream fis = mContext.openFileInput(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));

        String line = "";
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            list.add(new DataPoint(Double.parseDouble(data[0]), Double.parseDouble(data[1])));
        }

        Collections.sort(list, (dataPoint, t1) -> Double.compare(dataPoint.getX(), t1.getX()));

        br.close();
    }

    public void processAccelSensor(double accel) {
        if (accel > mAccelMax) {
            mAccelMax = accel;
        }
        mAccelDirty = true;
    }

    public void processHRSensor(double hr) {
        if (hr > mHRMax) {
            mHRMax = hr;
        }
        mHRDirty = true;

        double NN = (60 / hr);
        mNNtotal += 1;
        mNNsum += NN;
        mNNsqsum += NN * NN;
    }

    public void recordPoint() {
        long time = System.currentTimeMillis();

        if (mAccelDirty) {
            mSleepMotion.add(new DataPoint(time, Math.sqrt(mAccelMax)));
        }

        double SDNN = 0;
        if (mHRDirty) {
            mSleepHR.add(new DataPoint(time, mHRMax));

            if (mNNtotal > 0) {
                SDNN = Math.sqrt((mNNtotal * mNNsqsum - mNNsum * mNNsum) / (mNNtotal * (mNNtotal - 1)));
                mSleepSDNN.add(new DataPoint(time, SDNN));
            }
        }

        Log.d(TAG, "Max Acc: " + mAccelMax + ", HR: " + mHRMax + " SDNN: " + SDNN);
        mAccelMax = 0.0;
        mHRMax = 0.0;
        mNNsum = 0;
        mNNsqsum = 0;
        mNNtotal = 0;
        mAccelDirty = false;
        mHRDirty = false;
    }

    public DataPoint[] getSleepMotionArray(){
        return mSleepMotion.toArray(new DataPoint[0]);
    }

    public DataPoint[] getSleepHRArray(){
        return mSleepHR.toArray(new DataPoint[0]);
    }

    public double getMotionAt(int index){
        if(index < mSleepMotion.size()) {
            return mSleepMotion.get(index).getY();
        }else{
            return 0;
        }
    }

    private int binaryTimeSearch(List<DataPoint> list, double target){
        // Do a binary search on list to find the lower index where the target time lies
        int lower = 0, upper = list.size()-1;
        while(upper - lower > 1){
            int trial = (lower + upper)/2;
            if(list.get(trial).getX() < target){
                lower = trial;
            }else if(list.get(trial).getX() > target){
                upper = trial;
            }else{
                return trial;
            }
        }
        return lower;
    }

    private double interpolateListFromTime(List<DataPoint> list, double target) {
        if (list.size() == 0) return 0;
        if (target <= list.get(0).getX()) return list.get(0).getY();
        if (target >= list.get(list.size() - 1).getX()) return list.get(list.size() - 1).getY();

        int lower = binaryTimeSearch(list, target);
        int upper = lower + 1;

        if(upper >= list.size()) return list.get(lower).getY();

        double lowerTime = list.get(lower).getX(), upperTime = list.get(upper).getX();
        double ratio = (target - lowerTime) / (upperTime - lowerTime);

        return (1 - ratio)*list.get(lower).getY() + (ratio)*list.get(upper).getY();
    }

    public double getMotionAt(double time){
        return interpolateListFromTime(mSleepMotion, time);
    }

    public double getHRAt(double time){
        return interpolateListFromTime(mSleepHR, time);
    }

    public double getHRAt(int index) {
        if (index < mSleepHR.size()){
            return mSleepHR.get(index).getY();
        }else{
            return 0.0;
        }
    }

    public double getSDNNAt(double time){
        return interpolateListFromTime(mSleepSDNN, time);
    }

    public double getTimeAt(int index){
        if(mSleepMotion.size() == 0){
            return 0;
        }

        if(index < 0){
            return mSleepMotion.get(0).getX();
        }else if(index >= mSleepMotion.size()){
            return mSleepMotion.get(mSleepMotion.size() -1).getX();
        }else {
            return mSleepMotion.get(index).getX();
        }
    }

    public double getHRMean(){
        double tot = 0;
        for(DataPoint d : mSleepHR){
            tot += d.getY();
        }
        return tot / mSleepHR.size();
    }

    public int getDataLength(){
        return mSleepMotion.size();
    }

    public double getStart(){
        return mSleepMotion.get(0).getX();
    }

    public double getEnd(){
        if(mSleepMotion.size() == 0){
            return 0;
        }else {
            return mSleepMotion.get(mSleepMotion.size() - 1).getX();
        }
    }

    public boolean getSleepingAt(int index) {
        return getDataLength() != 0 && getSleepingAt(getTimeAt(index));
    }

    public boolean getSleepingAt(double time){
        if(getDataLength() < 5){
            return false; // Not enough data yet - guess "awake"
        }

        double D = 0;
        for(int j = 0; j < W.length; j++){
            // Ensure index is in range - if out of range, duplicate data
            double t = Math.max(Math.min(time+(j-4)*STEPMILLIS, getEnd()), getStart());
            double a = Math.min(getMotionAt(t), 300); // Cap at 300

            D += a*W[j];
        }
        D *= P;
        Log.d(TAG, "At "+System.currentTimeMillis()+", D = "+D+". Size: "+getDataLength());

        return D < 1;
    }

    public String getCSV(){
        int hrmSize = mSleepHR.size();

        boolean useHRM = hrmSize > 0;
        boolean useSDNN = useHRM & mSleepSDNN.size() > 0;

        // Generate header
        StringBuilder csv = new StringBuilder("Unix Time,Motion");
        if(useHRM) {
            csv.append(",Heart Rate");
        }

        if(useSDNN) {
            csv.append(",SDNN");
        }

        // Loop through datapoints to get CSV data
        csv.append("\n");
        for (int i = 0; i < getDataLength(); i++) {
            long t = (long) getTimeAt(i);
            double m = getMotionAt(i);

            csv.append(t).append(",").append(m);

            if (useHRM) {
                // Find HR based on time or index, depending on need to interpolate
                double h = getHRAt(hrmSize < mSleepMotion.size() ? t : i);
                csv.append(",").append(h);
            }

            if(useSDNN) {
                csv.append(",").append(getSDNNAt(t));
            }

            csv.append("\n");
        }

        return csv.toString();
    }

    public List<DataPoint> getLowpassHR(double alpha){
        List<DataPoint> ret = new ArrayList<>();
        double state = getHRMean();
        for(DataPoint d : mSleepHR){
            state += alpha*(d.getY() - state);
            ret.add(new DataPoint(d.getX(), state));
        }
        return ret;
    }

    public boolean hasHRData(){
        return mSleepHR.size() > 0;
    }

    public boolean hasSDNNData() {
        return mSleepSDNN.size() > 0;
    }

    public List<DataPoint> getSDNN(){
        return mSleepSDNN;
    }
}
