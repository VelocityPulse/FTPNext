package com.vpulse.ftpnext.database.FTPServerTable

open interface IFTPServerSchema {
    companion object {
        val TABLE: String = "ftp_server"

        //Columns
        const val COLUMN_DATABASE_ID: String = "id"
        const val COLUMN_NAME: String = "column_name"
        const val COLUMN_USER: String = "column_user"
        const val COLUMN_PASS: String = "column_pass"
        const val COLUMN_SERVER: String = "column_server"
        const val COLUMN_PORT: String = "column_port"
        const val COLUMN_FOLDER_NAME: String = "column_folder_name"
        const val COLUMN_ABSOLUTE_PATH: String = "column_absolute_path"
        const val COLUMN_CHARACTER_ENCODING: String = "column_encoding"
        const val COLUMN_TYPE: String = "column_type"

        //Columns list
        val COLUMN_ARRAY: Array<String?> = arrayOf(
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
        )

        //Create
        val TABLE_CREATE: String = ("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
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
                + ")")
    }
}