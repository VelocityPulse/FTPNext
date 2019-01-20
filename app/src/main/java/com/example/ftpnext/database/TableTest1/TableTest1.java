package com.example.ftpnext.database.TableTest1;

public class TableTest1 {

    private int mId = 0;
    private int mValue = 0;

    public TableTest1() {

    }

    public TableTest1(int iValue) {
        mValue = iValue;
    }

    public void setId(int iId) {
        mId = iId;
    }

    public void setValue(int iValue) {
        mValue = iValue;
    }

    public int getId() {
        return mId;
    }

    public int getValue() {
        return mValue;
    }
}
