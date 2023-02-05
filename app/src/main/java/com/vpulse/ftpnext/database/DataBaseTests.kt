package com.vpulse.ftpnext.database

import com.vpulse.ftpnext.core.*

object DataBaseTests {
    private const val TAG: String = "DATABASE : Run tests"

    // Note : RunTests will automatically increment the database ID
    fun <T : ABaseTable, V : ADataAccessObject<T>> runTests(iObjectInstance: T, iDao: V) {
        val lId1: Int = iDao.add(iObjectInstance)
        val lId2: Int = iDao.add(iObjectInstance)
        LogManager.info(
            TAG,
            "new " + iObjectInstance.javaClass.simpleName + " created. Id : " + lId1
        )
        LogManager.info(
            TAG,
            "new " + iObjectInstance.javaClass.simpleName + " created. Id : " + lId2
        )
        var lList: List<T> = iDao.fetchAll()
        for (lItem: T in lList) LogManager.info(
            TAG,
            "table id for " + iObjectInstance.javaClass.simpleName + " : " + lItem.dataBaseId
        )
        iDao.delete(lId1)
        iDao.delete(lId2)
        LogManager.info(TAG, "deleted")
        lList = iDao.fetchAll()
        if (lList.isEmpty()) LogManager.info(
            TAG,
            "table " + iObjectInstance.javaClass.simpleName + " has been reset well."
        ) else {
            for (lItem: T in lList) LogManager.error(
                TAG,
                "table id for " + iObjectInstance.javaClass.simpleName + " sill in the DB : " + lItem.dataBaseId
            )
        }
    }
}