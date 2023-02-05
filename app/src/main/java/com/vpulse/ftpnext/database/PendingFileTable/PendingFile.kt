package com.vpulse.ftpnext.database.PendingFileTable

import com.vpulse.ftpnext.core.ExistingFileAction
import com.vpulse.ftpnext.core.LoadDirection
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.database.ABaseTable

class PendingFile : ABaseTable {
    var serverId: Int = 0
        private set
    var loadDirection: LoadDirection? = null
        private set
    var name: String? = null
        private set
    var remotePath: String? = null
        private set
    var localPath: String? = null
        private set
    var isFinished: Boolean = false
        private set
    var progress: Int = 0
        private set
    var existingFileAction: ExistingFileAction? = null

    // Not in database :
    var size: Int = 0

    // Getter and Setter are not in DataBase from here
    var isSelected: Boolean = false
    var isConnected: Boolean = false
    var isAnError: Boolean = false
    var speedInByte: Long = 0
    var remainingTimeInMin: Int = 0

    constructor() {}
    constructor(
        iServerId: Int, iLoadDirection: LoadDirection?, iSelected: Boolean,
        iName: String?, iRemotePath: String?, iLocalPath: String?,
        iExistingFileAction: ExistingFileAction?
    ) {
        serverId = iServerId
        loadDirection = iLoadDirection
        isSelected = iSelected
        name = iName
        remotePath = iRemotePath
        localPath = iLocalPath
        existingFileAction = iExistingFileAction
    }

    fun setServerId(iServerId: Int): PendingFile {
        serverId = iServerId
        return this
    }

    fun setLoadDirection(iLoadDirection: LoadDirection?): PendingFile {
        loadDirection = iLoadDirection
        return this
    }

    fun setName(iName: String?): PendingFile {
        name = iName
        return this
    }

    fun setRemotePath(iRemotePath: String?): PendingFile {
        remotePath = iRemotePath
        return this
    }

    fun setLocalPath(iEnclosureName: String?): PendingFile {
        localPath = iEnclosureName
        return this
    }

    fun setFinished(iFinished: Boolean): PendingFile {
        isFinished = iFinished
        return this
    }

    fun setProgress(iProgress: Int): PendingFile {
        progress = iProgress
        return this
    }

    public override fun toString(): String {
        val oToString: String
        oToString = ("Database id: " + dataBaseId +
                "\nServerId: " + serverId +
                "\nLoadDirection: " + loadDirection.toString() +
                "\nStarted: " + isSelected +
                "\nRemote path:\t\t" + remotePath +
                "\nLocal path:\t\t" + localPath +
                "\nFinished: " + isFinished +
                "\nmProgress: " + progress)
        return oToString
    }

    fun updateContent(iPendingFile: PendingFile) {
        if (this === iPendingFile) {
            LogManager.info(TAG, "Useless updating content")
            return
        }
        serverId = iPendingFile.serverId
        loadDirection = iPendingFile.loadDirection
        isSelected = iPendingFile.isSelected
        name = iPendingFile.name
        remotePath = iPendingFile.remotePath
        localPath = iPendingFile.localPath
        isFinished = iPendingFile.isFinished
        progress = iPendingFile.progress
    }

    companion object {
        private val TAG: String = "DATABASE : Pending file"
    }
}