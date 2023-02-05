package com.vpulse.ftpnext.database.PendingFileTable

open interface IPendingFileSchema {
    companion object {
        val TABLE: String = "pending_file"

        //Columns
        const val COLUMN_DATABASE_ID: String = "id"
        const val COLUMN_SERVER_ID: String = "column_server_id"
        const val COLUMN_LOAD_DIRECTION: String = "column_load_direction"
        const val COLUMN_NAME: String = "column_name"
        const val COLUMN_REMOTE_PATH: String = "column_remote_path"
        const val COLUMN_LOCAL_PATH: String = "column_local_path"
        const val COLUMN_FINISHED: String = "column_finished"
        const val COLUMN_PROGRESS: String = "column_progress"
        const val COLUMN_EXISTING_FILE_ACTION: String = "column_existing_file_action"

        //Columns list
        val COLUMN_ARRAY: Array<String?> = arrayOf(
            COLUMN_DATABASE_ID,
            COLUMN_SERVER_ID,
            COLUMN_LOAD_DIRECTION,
            COLUMN_NAME,
            COLUMN_REMOTE_PATH,
            COLUMN_LOCAL_PATH,
            COLUMN_FINISHED,
            COLUMN_PROGRESS,
            COLUMN_EXISTING_FILE_ACTION
        )

        //Create
        val TABLE_CREATE: String = ("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + COLUMN_SERVER_ID + " INTEGER, "
                + COLUMN_LOAD_DIRECTION + " INTEGER, "
                + COLUMN_NAME + " TEXT, "
                + COLUMN_REMOTE_PATH + " TEXT, "
                + COLUMN_LOCAL_PATH + " TEXT, "
                + COLUMN_FINISHED + " INTEGER, "
                + COLUMN_PROGRESS + " INTEGER, "
                + COLUMN_EXISTING_FILE_ACTION + " INTEGER"
                + ")")
    }
}