package com.example.ftpnext.database.PendingFileTable;

public interface IPendingFileSchema {

    String TABLE = "pending_file";

    //Columns
    String COLUMN_DATABASE_ID = "id";
    String COLUMN_SERVER_ID = "column_server_id";
    String COLUMN_LOAD_DIRECTION = "column_load_direction";
    String COLUMN_STARTED = "column_started";
    String COLUMN_NAME = "column_name";
    String COLUMN_PATH = "column_path";
    String COLUMN_ENCLOSING_NAME = "column_enclosing_name";
    String COLUMN_FINISHED = "column_finished";
    String COLUMN_PROGRESS = "column_progress";

    //Columns list
    String[] COLUMNS = new String[]{
            COLUMN_DATABASE_ID,
            COLUMN_SERVER_ID,
            COLUMN_LOAD_DIRECTION,
            COLUMN_STARTED,
            COLUMN_NAME,
            COLUMN_PATH,
            COLUMN_ENCLOSING_NAME,
            COLUMN_FINISHED,
            COLUMN_PROGRESS
    };

    //Create
    String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + COLUMN_SERVER_ID + " INTEGER, "
            + COLUMN_LOAD_DIRECTION + " INTEGER, "
            + COLUMN_STARTED + " INTEGER, "
            + COLUMN_NAME + " TEXT, "
            + COLUMN_PATH + " TEXT, "
            + COLUMN_ENCLOSING_NAME + " TEXT, "
            + COLUMN_FINISHED + " INTEGER, "
            + COLUMN_FINISHED + " INTEGER"
            + ")";

}
