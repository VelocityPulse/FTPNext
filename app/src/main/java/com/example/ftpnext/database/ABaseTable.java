package com.example.ftpnext.database;

public abstract class ABaseTable {

    protected int mDataBaseId;

    public ABaseTable() {

    }

    public int getDataBaseId() {
        return mDataBaseId;
    }

    protected abstract void setDataBaseId(int iDataBaseId);

}
