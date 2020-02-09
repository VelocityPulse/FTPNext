package com.example.ftpnext.database.PendingFileTable;

public interface IPendingFileSchema {

    String TABLE = "pending_file";

    //Columns
    String COLUMN_DATABASE_ID = "id";
    String COLUMN_SERVER_ID = "column_server_id";
    String COLUMN_LOAD_DIRECTION = "column_load_direction";
    String COLUMN_STARTED = "column_started";
    String COLUMN_PATH = "column_path";
    String COLUMN_IS_FOLDER = "column_is_folder";

    //Columns list
    String[] COLUMNS = new String[]{
            COLUMN_DATABASE_ID,
            COLUMN_SERVER_ID,
            COLUMN_LOAD_DIRECTION,
            COLUMN_STARTED,
            COLUMN_PATH,
            COLUMN_IS_FOLDER
    };

    //Create
    String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + COLUMN_SERVER_ID + " INTEGER, "
            + COLUMN_LOAD_DIRECTION + " INTEGER, "
            + COLUMN_STARTED + " INTEGER, "
            + COLUMN_PATH + " TEXT, "
            + COLUMN_IS_FOLDER + " INTEGER"
            + ")";

}
