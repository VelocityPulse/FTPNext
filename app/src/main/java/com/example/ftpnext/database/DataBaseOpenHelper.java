package com.example.ftpnext.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.ftpnext.database.TableTest1.TableTest1DAO;

public class DataBaseOpenHelper extends SQLiteOpenHelper {

    public DataBaseOpenHelper(Context context) {
        super(context, DataBase.DATABASE_NAME, null, DataBase.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // test debug :
        TableTest1DAO t = new TableTest1DAO();

        t.


//        db.execSQL();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
