package com.fridgecow.smartalarm;

import android.content.Context;
import android.util.Log;

import com.jjoe64.graphview.series.DataPoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by tom on 04/01/18.
 */

public class SleepSummaryData extends ArrayList<DataRegion> implements Serializable{
    public static final String WAKEREGION = "wakeful";
    public static final String REMREGION = "rem";

    private static final String TAG = SleepSummaryData.class.getSimpleName();

    private double mStart;
    private double mEnd;

    public SleepSummaryData(double start, double end){
        super();

        mStart = start;
        mEnd = end;
    }

    public SleepSummaryData(double start, double end, List<DataRegion> list){
        super(list);

        mStart = start;
        mEnd = end;
    }

    private double maxintime(List<DataPoint> list, double start, double end){
        double max = list.get(0).getY();
        for(DataPoint d : list){
            if(d.getX() >= start && d.getX() <= end){
                if(max < d.getY()){
                    max = d.getY();
                }
            }
        }
        return max;
    }
    public SleepSummaryData(SleepData data){
        super();

        if(data.getDataLength() == 0){
            return;
        }

        mStart = data.getStart();
        mEnd = data.getEnd();

        //Process in sleepdata
        //Do actigraphy algorithm
        //https://github.com/fridgecow/smartalarm/wiki/Sleep-Detection

        //Try to detect REM by estimating HRV
        double HRVthresh = 0;
        List<DataPoint> HRV = null;
        if(data.hasHRData()) {
            if(!data.hasSDNNData()) {
                HRV = new ArrayList<>();
                List<Double> HRVfilter = new ArrayList<>();
                List<DataPoint> HRLowPass = data.getLowpassHR(0.15);
                for (int i = 0; i < HRLowPass.size(); i++) {
                    final double lp = HRLowPass.get(i).getY();
                    final double hr = data.getHRAt(i);
                    final double hrv = Math.abs(lp - hr);
                    HRV.add(new DataPoint(HRLowPass.get(i).getX(), hrv));

                    if (hrv > 18) {
                        HRVfilter.add(hrv);
                    }
                }

                Collections.sort(HRVfilter);
                if (HRVfilter.size() > 0) {
                    HRVthresh = HRVfilter.get((int) (0.8 * HRVfilter.size()));
                }
            }else{
                HRVthresh = 0.19;
            }
        }


        //Wrist actigraphy
        boolean sleeping = false;
        double lastTime = getStart();
        for(double i = getStart(); i < getEnd(); i += SleepData.STEPMILLIS){
            if(data.getSleepingAt(i)) { //Sleep
                Log.d(TAG, "Sleeping at time "+i);
                if(!sleeping){
                    //End a region
                    boolean remclassifier = false;
                    if(data.hasSDNNData()){
                        remclassifier = maxintime(data.getSDNN(), lastTime, i - SleepData.STEPMILLIS)  < HRVthresh;
                    }else if(data.hasHRData()){
                        remclassifier =  maxintime(HRV, lastTime, i - SleepData.STEPMILLIS) < HRVthresh;
                    }
                    if(!data.hasHRData() || remclassifier) {
                        add(new DataRegion(
                                lastTime,
                                i - SleepData.STEPMILLIS,
                                WAKEREGION
                        ));
                    }else{
                        add(new DataRegion(
                                lastTime,
                                i - SleepData.STEPMILLIS,
                                REMREGION
                        ));
                    }
                    sleeping = true;
                }
            }else { //Wakefulness
                Log.d(TAG, "Wakeful at time "+i);
                if(sleeping){
                    //Start a region
                    lastTime = i;
                    sleeping = false;
                }
            }
        }

        //Deal with last section
        if(!sleeping){
            add(new DataRegion(
                    lastTime,
                    data.getTimeAt(data.getDataLength()-1),
                    WAKEREGION
            ));
        }
    }

    public SleepSummaryData(Context c, String filename) throws IOException{
        readIn(c, filename);
    }

    public void writeOut(Context c, String filename) throws IOException {
        final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(c.openFileOutput(filename, 0)));

        out.write("tracking,"+this.getStart()+","+this.getEnd()+"\n");
        for(DataRegion d : this){
            out.write(d.getLabel()+","+d.getStart()+","+d.getEnd()+"\n");
            Log.d(TAG, d.getLabel()+","+d.getStart()+","+d.getEnd());
        }
        out.close();
    }

    public void readIn(Context c, String filename) throws IOException {
        final BufferedReader in = new BufferedReader(new InputStreamReader(c.openFileInput(filename)));

        //First line
        String line = in.readLine();
        if(line != null) {
            String[] meta = line.split(",");
            mStart = Double.parseDouble(meta[1]);
            mEnd = Double.parseDouble(meta[2]);
        }else{
            throw new IOException("SleepSummaryData file has no contents");
        }

        //Other lines
        while((line = in.readLine()) != null){
            String[] data = line.split(",");
            if(data.length == 3) {
                this.add(new DataRegion(Double.parseDouble(data[1]), Double.parseDouble(data[2]), data[0]));
            }
        }

        in.close();
    }

    public double getStart() {
        return mStart;
    }

    public void setStart(double mStart) {
        this.mStart = mStart;
    }

    public double getEnd() {
        return mEnd;
    }

    public void setEnd(double mEnd) {
        this.mEnd = mEnd;
    }
}
