package com.fridgecow.smartalarm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tom on 04/01/18.
 */

public class SleepData extends ArrayList<DataRegion> {
    private double mStart;
    private double mEnd;

    public SleepData(double start, double end){
        mStart = start;
        mEnd = end;
    }

    public SleepData(double start, double end, List<DataRegion> list){
        super(list);

        mStart = start;
        mEnd = end;
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
