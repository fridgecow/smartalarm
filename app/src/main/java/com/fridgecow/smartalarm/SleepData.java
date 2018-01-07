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
import java.util.List;

/**
 * Created by tom on 07/01/18.
 */

public class SleepData {
    private static final String TAG = "SleepData";
    private static final String OFFLINE_ACC = "sleepdata.csv";
    private static final String OFFLINE_HRM = "sleephrm.csv";

    private final double P = 0.001;
    private final double[] W = {106.0, 54.0, 58.0, 76.0, 230.0, 74.0, 67.0};

    private Context mContext;

    //Stores long-term tracking data
    private List<DataPoint> mSleepMotion;
    private List<DataPoint> mSleepHR;

    //For accelerometer
    private double mAccelMax = 0.0;

    //For HR
    private double mHRMax = 0.0;

    public SleepData(Context context){
        mContext = context;
        reset();
    }

    public void reset(){
        mSleepHR = new ArrayList<>();
        mSleepMotion = new ArrayList<>();

        mAccelMax = 0.0;
        mHRMax = 0.0;

        //Empty offline store and mAccelData etc
        try{
            //List<DataPoint> emptyList = new ArrayList<>();
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
        recordHRSensor(hr);
    }

    public void recordAccelSensor(double accel){
        if(accel > mAccelMax){
            mAccelMax = accel;
        }
    }

    public void recordHRSensor(double hr){
        if(hr > mHRMax){
            mHRMax = hr;
        }
    }

    public void recordPoint(){
        long time = System.currentTimeMillis();

        mSleepMotion.add(new DataPoint(time, mAccelMax));
        mSleepHR.add(new DataPoint(time, mHRMax));

        Log.d(TAG, "Max Acc: " + mAccelMax + ", HR: "+mHRMax);
        mAccelMax = 0.0;
        mHRMax = 0.0;
    }

    public List<DataPoint> getSleepMotion(){
        return mSleepMotion;
    }

    public List<DataPoint> getSleepHR(){
        return mSleepHR;
    }

    public double getMotionAt(int index){
        return mSleepMotion.get(index).getX();
    }

    public double getHRAt(int index) {
        if (index < mSleepHR.size()){
            return mSleepHR.get(index).getX();
        }else{
            return 0.0;
        }
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

    public int getDataLength(){
        return mSleepMotion.size();
    }

    public double getStart(){
        return mSleepMotion.get(0).getX();
    }

    public double getEnd(){
        return mSleepMotion.get(mSleepMotion.size()).getX();
    }

    public boolean getSleepingAt(int index){
        if(getDataLength() < 7){
            return true; //Not enough data yet - guess wakeful
        }

        if(index >= getDataLength() - 2){
            index = getDataLength() - 2; //Estimate current based on past :/
        }else if(index < 4){
            index = 4; //Estimate based on first available data
        }
        double D = 0;
        for(int j = 0; j < W.length; j++){
            double a = mSleepHR.get(index+j-4).getY();
            if(a > 300){
                a = 300;
            }

            D += a*W[j];
        }
        D *= P;

        return D < 1;
    }
}
