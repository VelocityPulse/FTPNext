package com.example.ftpnext.database;

import org.jetbrains.annotations.NotNull;

public abstract class ABaseTable {

    protected int mDataBaseId;

    public ABaseTable() {

    }

    public int getDataBaseId() {
        return mDataBaseId;
    }

    protected abstract void setDataBaseId(int iDataBaseId);

    @NotNull
    public abstract String toString();

}
