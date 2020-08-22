package com.vpulse.ftpnext.database;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.vpulse.ftpnext.core.AppConstants;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.vpulse.ftpnext.database.FTPServerTable.IFTPServerSchema;
import com.vpulse.ftpnext.database.PendingFileTable.IPendingFileSchema;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFileDAO;
import com.vpulse.ftpnext.database.TableTest1.ITableTest1Schema;
import com.vpulse.ftpnext.database.TableTest1.TableTest1DAO;

import java.util.ArrayList;
import java.util.List;

public class DataBase {

    public static final String DATABASE_NAME = AppConstants.DATABASE_NAME;
    public static final int DATABASE_VERSION = AppConstants.DATABASE_VERSION;

    private static String TAG = "DATABASE : Database";

    private static TableTest1DAO sTableTest1Dao;
    private static FTPServerDAO sFTPServerDAO;
    private static PendingFileDAO sPendingFileDAO;

    private static DataBase sSingleton = null;
    private static boolean sDataBaseIsOpen = false;

    private DataBase() {
    }

    public static DataBase getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new DataBase());
    }

    public static TableTest1DAO getTableTest1DAO() {
        return sTableTest1Dao;
    }

    public static FTPServerDAO getFTPServerDAO() {
        return sFTPServerDAO;
    }

    public static PendingFileDAO getPendingFileDAO() {
        return sPendingFileDAO;
    }

    public static void initDataDirectory() {

    }

    public static boolean isDataBaseIsOpen() {
        return sDataBaseIsOpen;
    }

    /**
     * @param iContext Context of the app
     * @return True if DataBase is opened.
     * @throws SQLException Exception during opening database.
     */
    public boolean open(Context iContext) throws SQLException {
        if (sDataBaseIsOpen)
            return true;

        LogManager.info(TAG, "Open database");

        List<String> lTableSchemaToCreate = new ArrayList<>();

        // Table Create
        lTableSchemaToCreate.add(ITableTest1Schema.TABLE_CREATE);
        lTableSchemaToCreate.add(IFTPServerSchema.TABLE_CREATE);
        lTableSchemaToCreate.add(IPendingFileSchema.TABLE_CREATE);

        DataBaseOpenHelper mDataBaseOpenHelper = new DataBaseOpenHelper(iContext, lTableSchemaToCreate);
        SQLiteDatabase lDataBase = mDataBaseOpenHelper.getWritableDatabase();

        //DAO list
        sTableTest1Dao = new TableTest1DAO(lDataBase);
        sFTPServerDAO = new FTPServerDAO(lDataBase);
        sPendingFileDAO = new PendingFileDAO(lDataBase);

        return (sDataBaseIsOpen = true);
    }
}
