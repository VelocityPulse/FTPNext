package com.example.ftpnext.database;

import com.example.ftpnext.core.LogManager;

import java.util.List;

public class DataBaseTests {

    private static final String TAG = "DATABASE : Run tests";

    // Note : RunTests will automatically increment the database ID
    public static <T extends ABaseTable, V extends ADataAccessObject> void runTests(T iObjectInstance, V iDao) {

        int lId1 = iDao.add(iObjectInstance);
        int lId2 = iDao.add(iObjectInstance);
        LogManager.info(TAG, "new " + iObjectInstance.getClass().getSimpleName() + " created. Id : " + lId1);
        LogManager.info(TAG, "new " + iObjectInstance.getClass().getSimpleName() + " created. Id : " + lId2);

        List<T> lList = iDao.fetchAll();
        for (T lItem : lList)
            LogManager.info(TAG, "table id for " + iObjectInstance.getClass().getSimpleName() + " : " + lItem.mDataBaseId);

        iDao.delete(lId1);
        iDao.delete(lId2);
        LogManager.info(TAG, "deleted");

        lList = iDao.fetchAll();
        if (lList.size() == 0)
            LogManager.info(TAG, "table " + iObjectInstance.getClass().getSimpleName() + " has been reset well.");
        else {
            for (T lItem : lList)
                LogManager.error(TAG, "table id for " + iObjectInstance.getClass().getSimpleName() + " sill in the DB : " + lItem.mDataBaseId);
        }
    }
}
