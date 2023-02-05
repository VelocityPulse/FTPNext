package com.vpulse.ftpnext.core

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager {

    fun startPreferencesManager(iContext: Context) {
        if (mSharedPreferences == null) {
            mSharedPreferences =
                iContext.getSharedPreferences(AppCore.Companion.APP_ADDRESS, Context.MODE_PRIVATE)
            mSharedPreferences?.let {
                if (it.getBoolean(PREFERENCE_FIRST_RUN, true)) {
                    isTheFirstRun = true
                    it.edit().putBoolean(PREFERENCE_FIRST_RUN, false).apply()
                }
            }
        }
    }

    companion object {
        const val SILENT_LOG_CAT = true

        // Default values :
        const val DEFAULT_MAX_TRANSFERS_VALUE = 2
        val DEFAULT_EXISTING_FILE_ACTION = ExistingFileAction.NOT_DEFINED
        const val DEFAULT_WIFI_ONLY = false
        const val DEFAULT_IS_DARK_THEME = false
        const val DEFAULT_LOGS_VALUE = ""
        private const val TAG = "PREFERENCE MANAGER"

        // Preferences tags :
        private const val PREFERENCE_FIRST_RUN = "PREFS_FIRST_RUN"
        private const val PREFERENCE_MAX_TRANSFERS = "PREFS_MAX_TRANSFER"
        private const val PREFERENCE_EXISTING_FILE_ACTION = "PREFS_EXISTING_FILE_ACTION"
        private const val PREFERENCE_WIFI_ONLY = "PREF_WIFI_ONLY"
        private const val PREFERENCE_DARK_THEME = "PREF_DARK_THEME"
        private const val PREFERENCE_LOGS = "PREFS_LOGS"
        private var sSingleton: PreferenceManager? = null
        var isTheFirstRun = false
            private set
        private var mSharedPreferences: SharedPreferences? = null
        val instance: PreferenceManager?
            get() = if (sSingleton != null) sSingleton else PreferenceManager().also {
                sSingleton = it
            }
        var existingFileAction: ExistingFileAction
            get() {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return DEFAULT_EXISTING_FILE_ACTION
                }
                return ExistingFileAction.Companion.getValue(
                    mSharedPreferences!!.getInt(
                        PREFERENCE_EXISTING_FILE_ACTION, DEFAULT_EXISTING_FILE_ACTION.value
                    )
                )
            }
            set(iAction) {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return
                }
                mSharedPreferences!!.edit().putInt(PREFERENCE_EXISTING_FILE_ACTION, iAction.value)
                    .apply()
            }
        var isWifiOnly: Boolean
            get() {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return DEFAULT_WIFI_ONLY
                }
                return mSharedPreferences!!.getBoolean(PREFERENCE_WIFI_ONLY, DEFAULT_WIFI_ONLY)
            }
            set(iWifiOnly) {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return
                }
                mSharedPreferences!!.edit().putBoolean(PREFERENCE_WIFI_ONLY, iWifiOnly).apply()
            }
        var isDarkTheme: Boolean
            get() {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return DEFAULT_IS_DARK_THEME
                }
                return mSharedPreferences!!.getBoolean(PREFERENCE_DARK_THEME, DEFAULT_IS_DARK_THEME)
            }
            set(iIsDarkTheme) {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return
                }
                mSharedPreferences!!.edit().putBoolean(PREFERENCE_DARK_THEME, iIsDarkTheme).apply()
            }
        var maxTransfers: Int
            get() {
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return 0
                }
                return mSharedPreferences!!.getInt(
                    PREFERENCE_MAX_TRANSFERS, DEFAULT_MAX_TRANSFERS_VALUE
                )
            }
            set(iMax) {
                var iMax = iMax
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return
                }
                if (iMax <= 0) iMax = 1
                mSharedPreferences!!.edit().putInt(PREFERENCE_MAX_TRANSFERS, iMax).apply()
            }
        val fTPLogs: String?
            get() {
                if (!SILENT_LOG_CAT) LogManager.info(TAG, "Get FTP logs")
                if (mSharedPreferences == null) {
                    LogManager.error(TAG, "Shared preferences null")
                    return null
                }
                return mSharedPreferences!!.getString(PREFERENCE_LOGS, DEFAULT_LOGS_VALUE)
            }

        fun addFTPLog(iLogToAdd: String?) {
            var iLogToAdd = iLogToAdd
            if (!SILENT_LOG_CAT) LogManager.info(TAG, "Add FTP log logs")
            if (mSharedPreferences == null) {
                LogManager.error(TAG, "Shared preferences null")
                return
            }
            if (iLogToAdd == null) {
                LogManager.error(TAG, "Log == null")
                return
            }
            iLogToAdd = iLogToAdd.replace("\n".toRegex(), " ")
            iLogToAdd = iLogToAdd.trim { it <= ' ' }
            if (iLogToAdd.length == 0) {
                LogManager.error(TAG, "Nothing to log...")
                return
            }
            var lLogs = mSharedPreferences!!.getString(PREFERENCE_LOGS, DEFAULT_LOGS_VALUE)
            lLogs += """
                   
                   $iLogToAdd
                   """.trimIndent()
            mSharedPreferences!!.edit().putString(PREFERENCE_LOGS, lLogs).apply()
        }
    }
}