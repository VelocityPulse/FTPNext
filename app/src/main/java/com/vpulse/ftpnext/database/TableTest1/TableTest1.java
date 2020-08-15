package com.vpulse.ftpnext.database.TableTest1;

import com.vpulse.ftpnext.database.ABaseTable;

import org.jetbrains.annotations.NotNull;

public class TableTest1 extends ABaseTable {

    private int mValue = 0;

    public TableTest1() {
    }

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
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }

    @NotNull
    @Override
    public String toString() {
        return "test";
    }
}
