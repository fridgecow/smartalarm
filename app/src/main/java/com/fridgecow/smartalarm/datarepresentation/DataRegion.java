package com.fridgecow.smartalarm.datarepresentation;

import com.jjoe64.graphview.series.DataPoint;

import java.io.Serializable;

/**
 * Created by tom on 04/01/18.
 */

public class DataRegion implements Serializable{
    private double mStart;
    private double mEnd;
    private String mLabel;

    public DataRegion(double start, double end, String label){
        mStart = start;
        mEnd = end;
        mLabel = label;
    }

    public DataRegion(DataPoint start, DataPoint end, String label){
        mStart = start.getX();
        mEnd = end.getX();
        mLabel = label;
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

    public String getLabel() {
        return mLabel;
    }

    public void setLabel(String mLabel) {
        this.mLabel = mLabel;
    }
}
