package com.example.ftpnext.database.FTPHostTable;

public interface IFTPHostSchema {

    String TABLE = "ftp_host";

    //Columns
    String COLUMN_DATABASE_ID = "id";
    String COLUMN_NAME = "column_name";
    String COLUMN_USER = "column_user";
    String COLUMN_PASS = "column_pass";
    String COLUMN_HOST = "column_host";
    String COLUMN_PORT = "column_port";
    String COLUMN_ATTRIBUTED_FOLDER = "column_folder";
    String COLUMN_CHARACTER_ENCODING = "column_encoding";
    String COLUMN_TYPE = "column_type";

    //Columns list
    String[] COLUMNS = new String[]{
            COLUMN_DATABASE_ID,
            COLUMN_NAME,
            COLUMN_USER,
            COLUMN_PASS,
            COLUMN_HOST,
            COLUMN_PORT,
            COLUMN_ATTRIBUTED_FOLDER,
            COLUMN_CHARACTER_ENCODING,
            COLUMN_TYPE
    };

    //Create
    String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + COLUMN_NAME + " TEXT, "
            + COLUMN_USER + " TEXT, "
            + COLUMN_PASS + " TEXT, "
            + COLUMN_HOST + " TEXT, "
            + COLUMN_PORT + " INTEGER, "
            + COLUMN_ATTRIBUTED_FOLDER + " TEXT, "
            + COLUMN_CHARACTER_ENCODING + " TEXT, "
            + COLUMN_TYPE + " INTEGER"
            + ")";
}
