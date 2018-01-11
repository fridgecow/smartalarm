package com.fridgecow.smartalarm;

import android.content.Context;
import android.provider.ContactsContract;
import android.util.Log;

import com.jjoe64.graphview.series.DataPoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Created by tom on 07/01/18.
 */

public class SleepData {
    private static final String TAG = "SleepData";
    private static final String OFFLINE_ACC = "sleepdata.csv";
    private static final String OFFLINE_HRM = "sleephrm.csv";

    public static final int STEPMILLIS = 60000;
    private final double P = 0.001;
    private final double[] W = {106.0, 54.0, 58.0, 76.0, 230.0, 74.0, 67.0};

    private Context mContext;

    //Stores long-term tracking data
    private List<DataPoint> mSleepMotion;
    private List<DataPoint> mSleepHR;
    private List<DataPoint> mSleepSDNN;


    //For accelerometer
    private double mAccelMax = 0.0;
    private boolean mAccelDirty = false;

    //For HR
    private double mHRMax = 0.0;
    private boolean mHRDirty = false;
    private double mNNtotal = 0.0;
    private double mNNsum = 0.0;
    private double mNNsqsum = 0.0;

    public SleepData(Context context){
        mContext = context;
        reset();
    }

    public void reset(){
        mSleepHR = new ArrayList<>();
        mSleepMotion = new ArrayList<>();
        mSleepSDNN = new ArrayList<>();

        mAccelMax = 0.0;
        mHRMax = 0.0;

        mNNtotal = 0;
        mNNsum = 0;
        mNNsqsum = 0;

        mAccelDirty = false;
        mHRDirty = false;

        //Empty offline store and mAccelData etc
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

        Collections.sort(list, new Comparator<DataPoint>() {
            @Override
            public int compare(DataPoint dataPoint, DataPoint t1) {
                if(dataPoint.getX() < t1.getX()){
                    return -1;
                }else if(dataPoint.getX() == t1.getX()){
                    return 0;
                }else{
                    return 1;
                }
            }
        });

        br.close();
    }

    public void recordSensor(double accel, double hr){
        recordAccelSensor(accel);
        recordHRSensor(hr, false);
    }

    public void recordAccelSensor(double accel){
        if(accel > mAccelMax){
            mAccelMax = accel;
        }
        mAccelDirty = true;
    }

    public void recordHRSensor(double hr, boolean guessHRV){
        if(hr > mHRMax){
            mHRMax = hr;
        }
        mHRDirty = true;

        if(guessHRV){
            double NN = (60 / hr);

            mNNtotal += 1;
            mNNsum += NN;
            mNNsqsum += NN*NN;
        }
    }

    public void recordHBSensor(double hb){
        Log.d(TAG, "Heart Beat "+hb+" "+System.currentTimeMillis());
    }

    public void recordPoint(){
        long time = System.currentTimeMillis();

        if(mAccelDirty) {
            mSleepMotion.add(new DataPoint(time, mAccelMax));
        }

        double SDNN = 0;
        if(mHRDirty) {
            mSleepHR.add(new DataPoint(time, mHRMax));
            SDNN = Math.sqrt((mNNtotal*mNNsqsum - mNNsum*mNNsum)/(mNNtotal*(mNNtotal-1)));
            mSleepSDNN.add(new DataPoint(time, SDNN));
        }

        Log.d(TAG, "Max Acc: " + mAccelMax + ", HR: "+mHRMax+" SDNN: "+SDNN);
        mAccelMax = 0.0;
        mHRMax = 0.0;
        mNNsum = 0;
        mNNsqsum = 0;
        mNNtotal = 0;
        mAccelDirty = false;
        mHRDirty = false;
    }

    public List<DataPoint> getSleepMotion(){
        return mSleepMotion;
    }

    public DataPoint[] getSleepMotionArray(){
        return mSleepMotion.toArray(new DataPoint[mSleepMotion.size()]);
    }

    public List<DataPoint> getSleepHR(){
        return mSleepHR;
    }

    public DataPoint[] getSleepHRArray(){
        return mSleepHR.toArray(new DataPoint[mSleepHR.size()]);
    }
    public double getMotionAt(int index){
        if(index < mSleepMotion.size()) {
            return mSleepMotion.get(index).getY();
        }else{
            return 0;
        }
    }

    private int binaryTimeSearch(List<DataPoint> list, double target){
        //Do a binary search on list to find the lower index where the target time lies
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

    private double interpolateListFromTime(List<DataPoint> list, double target){
        if(target >= list.get(0).getX() && target <= list.get(list.size()-1).getX()) {
            int lower = binaryTimeSearch(list, target);
            int upper = lower + 1;

            if(upper < list.size()) {
                double lowerTime = list.get(lower).getX(), upperTime = list.get(upper).getX();
                double ratio = (target - lowerTime) / (upperTime - lowerTime);

                //Log.d(TAG, "ratio: "+ratio+", got motion: "+((1 - ratio)*list.get(lower).getY() + (ratio)*list.get(upper).getY()));

                return (1 - ratio)*list.get(lower).getY() + (ratio)*list.get(upper).getY();
            }else{
                return list.get(lower).getY();
            }
        }else{
            Log.d(TAG, "Time to interpolate outside of range - assuming closest value");
            if(target < list.get(0).getX()){
                return list.get(0).getY();
            }else{
                return list.get(list.size()-1).getY();
            }
        }
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
        return mSleepMotion.get(index).getX();
    }

    public double getMotionMean(){
        double tot = 0;
        for(DataPoint d : mSleepMotion){
            tot += d.getY();
        }
        return tot / mSleepMotion.size();
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
        return mSleepMotion.get(mSleepMotion.size()-1).getX();
    }

    public boolean getSleepingAt(int index){
        return getSleepingAt(getTimeAt(index));
    }

    public boolean getSleepingAt(double time){
        if(getDataLength() < 5){
            return false; //Not enough data yet - guess "awake"
        }

        double D = 0;
        for(int j = 0; j < W.length; j++){
            //Ensure index is in range - if out of range, duplicate data
            double t = Math.max(Math.min(time+(j-4)*STEPMILLIS, getEnd()), getStart());
            double a = Math.min(getMotionAt(t), 300); //Cap at 300

            D += a*W[j];
        }
        D *= P;

        Log.d(TAG, "At time "+(new Date((long) time))+" D="+D);

        return D < 1;
    }

    public String getCSV(boolean useHRM){
        //Loop through datapoints to get CSV data
        StringBuilder csv;
        if(useHRM){
            csv =new StringBuilder("Unix Time,Motion,Heart Rate, SDNN\n");
        }else{
            csv = new StringBuilder("Unix Time,Motion\n");
        }
        for (int i = 0; i < getDataLength(); i++) {
            long t = (long) getTimeAt(i);
            double m = getMotionAt(i);
            if (useHRM) {
                double h, n;
                if(mSleepHR.size() < mSleepMotion.size()){
                    //Find the HR for this time
                    h = getHRAt(t);
                }else {
                    //Simply get it from the index
                    h = getHRAt(i);
                }
                n = getSDNNAt(t);
                csv.append(t).append(",").append(m).append(",").append(h).append(",").append(n).append("\n");
            } else {
                csv.append(t).append(",").append(m).append("\n");
            }
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
