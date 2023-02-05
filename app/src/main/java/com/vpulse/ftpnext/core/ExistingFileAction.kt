package com.vpulse.ftpnext.core

import com.vpulse.ftpnext.R

enum class ExistingFileAction(val value: Int) {
    NOT_DEFINED(0),
    REPLACE_FILE(1),
    REPLACE_IF_FILE_IS_MORE_RECENT(2),
    REPLACE_IF_SIZE_IS_DIFFERENT(
        3
    ),
    REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT(4),
    RESUME_FILE_TRANSFER(5),
    RENAME_FILE(6),
    IGNORE(
        7
    );

    companion object {
        fun getValue(iValue: Int): ExistingFileAction {
            return when (iValue) {
                1 -> REPLACE_FILE
                2 -> REPLACE_IF_FILE_IS_MORE_RECENT
                3 -> REPLACE_IF_SIZE_IS_DIFFERENT
                4 -> REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT
                5 -> RESUME_FILE_TRANSFER
                6 -> RENAME_FILE
                7 -> IGNORE
                else -> NOT_DEFINED
            }
        }

        fun getTextResourceId(iValue: Int): Int {
            return getTextResourceId(getValue(iValue))
        }

        fun getTextResourceId(iValue: ExistingFileAction?): Int {
            return when (iValue) {
                REPLACE_FILE -> R.string.existing_file_replace_file
                REPLACE_IF_FILE_IS_MORE_RECENT -> R.string.existing_file_if_more_recent
                REPLACE_IF_SIZE_IS_DIFFERENT -> R.string.existing_file_if_sizes_diff
                REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT -> R.string.existing_file_if_sizes_are_diff_or_more_recent
                RESUME_FILE_TRANSFER -> R.string.existing_file_resume_download
                RENAME_FILE -> R.string.existing_file_rename_file
                IGNORE -> R.string.existing_file_ignore
                NOT_DEFINED -> R.string.existing_file_ask_each_time
                else -> R.string.existing_file_ask_each_time
            }
        }
    }
}