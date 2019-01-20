package com.example.ftpnext.database.TableTest1;

public interface ITableTest1Schema {

    String TABLE = "tabletest1";

    //Columns
    String COLUMN_ID = "id";
    String COLUMN_VALUE = "column_value";

    //Columns list
    String[] COLUMNS = new String[]{
            COLUMN_ID,
            COLUMN_VALUE,
    };

    //Create
    String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + COLUMN_VALUE + " INTEGER"
            + ")";
}
