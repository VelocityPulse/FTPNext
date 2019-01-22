package com.example.ftpnext.database.TableTest1;

import com.example.ftpnext.database.ABaseTable;

public class TableTest1 extends ABaseTable {

    private int mValue = 0;

    public TableTest1() { }

    public TableTest1(int iValue) {
        mValue = iValue;
    }

    public void setValue(int iValue) {
        mValue = iValue;
    }

    public int getValue() {
        return mValue;
    }

    @Override
    public int getDataBaseId() {
        return mDataBaseId;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }
}
