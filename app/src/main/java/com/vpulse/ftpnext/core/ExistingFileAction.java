package com.vpulse.ftpnext.core;

import com.vpulse.ftpnext.R;

public enum ExistingFileAction {

    NOT_DEFINED(0),

    REPLACE_FILE(1),

    REPLACE_IF_FILE_IS_MORE_RECENT(2),

    REPLACE_IF_SIZE_IS_DIFFERENT(3),

    REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT(4),

    RESUME_FILE_TRANSFER(5),

    RENAME_FILE(6),

    IGNORE(7);

    private final int mValue;

    ExistingFileAction(int iValue) {
        this.mValue = iValue;
    }

    public static ExistingFileAction getValue(int iValue) {
        switch (iValue) {
            case 1:
                return ExistingFileAction.REPLACE_FILE;
            case 2:
                return ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT;
            case 3:
                return ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT;
            case 4:
                return ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT;
            case 5:
                return ExistingFileAction.RESUME_FILE_TRANSFER;
            case 6:
                return ExistingFileAction.RENAME_FILE;
            case 7:
                return ExistingFileAction.IGNORE;
            default:
                return ExistingFileAction.NOT_DEFINED;
        }
    }

    public static int getTextResourceId(int iValue) {
        return getTextResourceId(getValue(iValue));
    }

    public static int getTextResourceId(ExistingFileAction iValue) {
        switch (iValue) {
            case REPLACE_FILE:
                return R.string.existing_file_replace_file;
            case REPLACE_IF_FILE_IS_MORE_RECENT:
                return R.string.existing_file_if_more_recent;
            case REPLACE_IF_SIZE_IS_DIFFERENT:
                return R.string.existing_file_if_sizes_diff;
            case REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT:
                return R.string.existing_file_if_sizes_are_diff_or_more_recent;
            case RESUME_FILE_TRANSFER:
                return R.string.existing_file_resume_download;
            case RENAME_FILE:
                return R.string.existing_file_rename_file;
            case IGNORE:
                return R.string.existing_file_ignore;
            case NOT_DEFINED:
            default:
                return R.string.existing_file_ask_each_time;
        }
    }

    public int getValue() {
        return mValue;
    }
}