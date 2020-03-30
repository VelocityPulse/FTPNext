package com.vpulse.ftpnext.database.PendingFileTable;

public interface IPendingFileSchema {

    String TABLE = "pending_file";

    //Columns
    String COLUMN_DATABASE_ID = "id";
    String COLUMN_SERVER_ID = "column_server_id";
    String COLUMN_LOAD_DIRECTION = "column_load_direction";
    String COLUMN_NAME = "column_name";
    String COLUMN_REMOTE_PATH = "column_remote_path";
    String COLUMN_LOCAL_PATH = "column_local_path";
    String COLUMN_FINISHED = "column_finished";
    String COLUMN_PROGRESS = "column_progress";
    String COLUMN_EXISTING_FILE_ACTION = "column_existing_file_action";

    //Columns list
    String[] COLUMN_ARRAY = new String[]{
            COLUMN_DATABASE_ID,
            COLUMN_SERVER_ID,
            COLUMN_LOAD_DIRECTION,
            COLUMN_NAME,
            COLUMN_REMOTE_PATH,
            COLUMN_LOCAL_PATH,
            COLUMN_FINISHED,
            COLUMN_PROGRESS,
            COLUMN_EXISTING_FILE_ACTION
    };

    //Create
    String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + COLUMN_SERVER_ID + " INTEGER, "
            + COLUMN_LOAD_DIRECTION + " INTEGER, "
            + COLUMN_NAME + " TEXT, "
            + COLUMN_REMOTE_PATH + " TEXT, "
            + COLUMN_LOCAL_PATH + " TEXT, "
            + COLUMN_FINISHED + " INTEGER, "
            + COLUMN_PROGRESS + " INTEGER, "
            + COLUMN_EXISTING_FILE_ACTION + " INTEGER"
            + ")";

}
