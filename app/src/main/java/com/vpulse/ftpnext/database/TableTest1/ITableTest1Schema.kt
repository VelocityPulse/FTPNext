package com.vpulse.ftpnext.database.TableTest1

open interface ITableTest1Schema {
    companion object {
        const val TABLE: String = "tabletest1"

        //Columns
        const val COLUMN_DATABASE_ID: String = "id"
        const val COLUMN_VALUE: String = "column_value"

        //Columns list
        val COLUMNS: Array<String?> = arrayOf(
            COLUMN_DATABASE_ID,
            COLUMN_VALUE
        )

        //Create
        const val TABLE_CREATE: String = ("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + COLUMN_VALUE + " INTEGER"
                + ")")
    }
}