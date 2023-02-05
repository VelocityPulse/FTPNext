package com.vpulse.ftpnext.core

import android.annotation.SuppressLint
import android.content.Context
import com.vpulse.ftpnext.*
import com.vpulse.ftpnext.database.*
import com.vpulse.ftpnext.ftpservices.FTPLogManager


class AppCore private constructor() {
    fun startApplication(iMainActivityContext: Context) { // Data base
        val lDataBase: DataBase = DataBase.instance!!
        lDataBase.open(iMainActivityContext)

        // Preference manager
        val lPreferenceManager: PreferenceManager = PreferenceManager.instance!!
        lPreferenceManager.startPreferencesManager(iMainActivityContext)

        // Network manager
        networkManager = NetworkManager.instance
        networkManager!!.startMonitoring(iMainActivityContext)

        // FTP log manager
        FTPLogManager.init(iMainActivityContext)
        isApplicationStarted = true
    }

    companion object {
        const val APP_ADDRESS = "com.vpulse.ftpnext"
        const val FLOATING_ACTION_BUTTON_INTERPOLATOR = 2.3f
        var networkManager: NetworkManager? = null
            private set
        var isApplicationStarted = false
            private set

        @SuppressLint("StaticFieldLeak")
        private var sSingleton: AppCore? = null
        val instance: AppCore?
            get() = if (sSingleton != null) sSingleton else AppCore().also {
                sSingleton = it
            }
        val appTheme: Int
            get() = if (PreferenceManager.isDarkTheme) {
                R.style.AppTheme_Dark
            } else {
                R.style.AppTheme_Light
            }
        val dialogTheme: Int
            get() = if (PreferenceManager.isDarkTheme) {
                R.style.AppTheme_Dark_Dialog
            } else {
                R.style.AppTheme_Light_Dialog
            }
    }
}