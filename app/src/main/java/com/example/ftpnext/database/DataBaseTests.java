package com.example.ftpnext.database;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.TableTest1.TableTest1;

import java.util.List;

public class DataBaseTests {

    private static final String TAG = "DATABASE : Run tests";

    public static <T extends ABaseTable, V extends ADataAccessObject> void runTests(T iObjectInstance, V iDao) {

        iDao.add(iObjectInstance);
        iDao.add(iObjectInstance);

        List<T> lList = iDao.fetchAll();
        for (T lItem : lList)
            LogManager.info(TAG, "table id for " + iObjectInstance.getClass().getCanonicalName() + " : " + lItem.mDataBaseId);

        iDao.delete(lList.get(0));
        iDao.delete(lList.get(1).getDataBaseId());
        LogManager.info(TAG, "deleted");

        lList = iDao.fetchAll();
        if (lList.size() == 0) {
            LogManager.info(TAG, "table " + iObjectInstance.getClass().getCanonicalName() + " has been reset well.");
        } else {
            for (T lItem : lList)
                LogManager.error(TAG, "table id for " + iObjectInstance.getClass().getCanonicalName() + " sill in the DB : " + lItem.mDataBaseId);
        }
    }
}
