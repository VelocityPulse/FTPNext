package com.vpulse.ftpnext.database.FTPServerTable

import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.FTPCharacterEncoding
import com.vpulse.ftpnext.core.FTPType
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.database.ABaseTable
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

// TODO : When FTPServer is deleted, delete all pending file referenced
class FTPServer : ABaseTable() {
    var name: String? = null
    var server: String? = null
    var user: String? = null
    var pass: String? = null
    var port: Int = 0
    var folderName: String? = null
    var absolutePath: String? = null
    var fTPCharacterEncoding: FTPCharacterEncoding = FTPCharacterEncoding.DEFAULT
    var fTPType: FTPType = FTPType.DEFAULT
    val isEmpty: Boolean
        get() {
            return (Utils.isNullOrEmpty(name) &&
                    Utils.isNullOrEmpty(server) &&
                    Utils.isNullOrEmpty(user) &&
                    Utils.isNullOrEmpty(pass) &&
                    Utils.isNullOrEmpty(folderName) &&
                    Utils.isNullOrEmpty(absolutePath))
        }

    fun updateContent(iFTPServer: FTPServer?) {
        if (this === iFTPServer) {
            LogManager.info(TAG, "Useless updating content")
            return
        }
        name = iFTPServer!!.name
        server = iFTPServer.server
        user = iFTPServer.user
        pass = iFTPServer.pass
        port = iFTPServer.port
        fTPType = iFTPServer.fTPType
        folderName = iFTPServer.folderName
        absolutePath = iFTPServer.absolutePath
    }

    override fun equals(iObj: Any?): Boolean {
        if (iObj == null) return false
        if (iObj === this) return true
        if (!(iObj is FTPServer)) return false
        val lFTPServer: FTPServer? = iObj
        if (lFTPServer == null || ((!(name == lFTPServer.name) ||
                    !(server == lFTPServer.server) ||
                    !(user == lFTPServer.user) ||
                    !(pass == lFTPServer.pass) || (
                    port != lFTPServer.port) || (
                    fTPType != lFTPServer.fTPType) ||
                    !(folderName == lFTPServer.folderName) ||
                    !(absolutePath == lFTPServer.absolutePath)))
        ) {
            return false
        }
        return true
    }

    override fun toString(): String {
        var oToString: String
        oToString = name + "\n"
        oToString += server + "\n"
        oToString += user + "\n"
        var lMD: MessageDigest? = null
        try {
            lMD = MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            oToString += ">> No MD5 instance"
        }
        lMD!!.update(pass!!.toByteArray(), 0, pass!!.length)
        oToString += "MD5 pass : " + BigInteger(1, lMD.digest()).toString(16)
        oToString += port.toString() + "\n"
        oToString += fTPType.name + "\n"
        oToString += folderName + "\n"
        oToString += absolutePath + "\n"
        return oToString
    }

    companion object {
        private val TAG: String = "DATABASE : FTP Server"
    }
}