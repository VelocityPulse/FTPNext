package com.vpulse.ftpnext.database.FTPServerTable;

public interface IFTPServerSchema {

    String TABLE = "ftp_server";

    //Columns
    String COLUMN_DATABASE_ID = "id";
    String COLUMN_NAME = "column_name";
    String COLUMN_USER = "column_user";
    String COLUMN_PASS = "column_pass";
    String COLUMN_SERVER = "column_server";
    String COLUMN_PORT = "column_port";
    String COLUMN_FOLDER_NAME = "column_folder_name";
    String COLUMN_ABSOLUTE_PATH = "column_absolute_path";
    String COLUMN_CHARACTER_ENCODING = "column_encoding";
    String COLUMN_TYPE = "column_type";

    //Columns list
    String[] COLUMN_ARRAY = new String[]{
            COLUMN_DATABASE_ID,
            COLUMN_NAME,
            COLUMN_USER,
            COLUMN_PASS,
            COLUMN_SERVER,
            COLUMN_PORT,
            COLUMN_FOLDER_NAME,
            COLUMN_ABSOLUTE_PATH,
            COLUMN_CHARACTER_ENCODING,
            COLUMN_TYPE
    };

    //Create
    String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + COLUMN_NAME + " TEXT, "
            + COLUMN_SERVER + " TEXT, "
            + COLUMN_USER + " TEXT, "
            + COLUMN_PASS + " TEXT, "
            + COLUMN_PORT + " INTEGER, "
            + COLUMN_FOLDER_NAME + " TEXT, "
            + COLUMN_ABSOLUTE_PATH + " TEXT, "
            + COLUMN_CHARACTER_ENCODING + " TEXT, "
            + COLUMN_TYPE + " INTEGER"
            + ")";
}
