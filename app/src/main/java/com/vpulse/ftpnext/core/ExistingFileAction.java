package com.vpulse.ftpnext.core;

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

    public int getValue() {
        return mValue;
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
}