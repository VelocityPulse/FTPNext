package com.vpulse.ftpnext.navigation

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.*
import android.os.*
import android.text.Editable
import android.view.*
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.adapters.NavigationRecyclerViewAdapter
import com.vpulse.ftpnext.adapters.NavigationRecyclerViewAdapter.*
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.commons.interfaces.AfterTextChanged
import com.vpulse.ftpnext.commons.interfaces.OnEndAnimation
import com.vpulse.ftpnext.commons.interfaces.OnStartAnimation
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.DataBase
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO
import com.vpulse.ftpnext.ftpservices.AFTPConnection.*
import com.vpulse.ftpnext.ftpservices.FTPServices
import com.vpulse.ftpnext.ftpservices.FTPTransfer
import org.apache.commons.net.ftp.FTPFile
import java.util.*

class NavigationActivity : AppCompatActivity() {
    var mCurrentAdapter: NavigationRecyclerViewAdapter? = null
    var mCancelingDialog: AlertDialog? = null
    internal var mReconnectDialog: AlertDialog? = null
    internal var mFTPServiceConnectionDialog: AlertDialog? = null
    var mFetchDirLoadingDialog: AlertDialog? = null
    var mFetchLargeDirDialog: AlertDialog? = null
    var mErrorADialog: AlertDialog? = null
    internal var mSuccessDialog: AlertDialog? = null
    var mCreateFolderDialog: AlertDialog? = null
    internal var mIndexingPendingFilesDialog: AlertDialog? = null
    internal var mTransferDialog: AlertDialog? = null
    internal var mChooseExistingFileActionDialog: AlertDialog? = null
    var mDeletingInfoDialog: AlertDialog? = null
    var mDeletingErrorDialog: AlertDialog? = null
    internal var mHandler: Handler? = null
    internal var mWasOnPause: Boolean = false
    var mIsDirectoryFetchFinished: Boolean = false
    internal var mIsShowingTransfer: Boolean = false
    internal var mIsLargeDirectory: Boolean = false
    internal var mFTPServer: FTPServer? = null
    var mFTPServices: FTPServices? = null
    var mDirectoryPath: String? = null
    var mErrorCode: Int = 0
    private var mIsRunning: Boolean = false
    private var mNavigationTransfer: NavigationTransfer? = null
    private var mNavigationFetchDir: NavigationFetchDir? = null
    private var mNavigationDelete: NavigationDelete? = null
    private var mNavigationNewFolder: NavigationNewFolder? = null
    private var mOnPermissionAnswer: OnPermissionAnswer? = null
    private var mRecyclerSection: FrameLayout? = null
    private var mMainFAB: FloatingActionButton? = null
    private var mCreateFolderFAB: FloatingActionButton? = null
    private var mUploadFileFAB: FloatingActionButton? = null
    private var mSearchEditText: TextInputEditText? = null
    private var mSearchEditTextLayout: TextInputLayout? = null
    private var mSearchBar: LinearLayout? = null
    private var mToolBar: Toolbar? = null
    private var mIsFABOpen: Boolean = false
    private var mIsResumeFromActivityResult: Boolean = false
    private var mIsSearchDisplayed: Boolean = false
    override fun onCreate(iSavedInstanceState: Bundle?) {
        LogManager.info(TAG, "On create")
        super.onCreate(iSavedInstanceState)
        setTheme(AppCore.Companion.appTheme)
        setContentView(R.layout.activity_navigation)
        mIsRunning = true
        initializeGUI()
        initializeHandler()
        initialize()
        initializeFTPServices(false, false)
    }

    override fun onResume() {
        LogManager.info(TAG, "On resume")
        super.onResume()
        LogManager.info(TAG, "Was on pause : " + mWasOnPause)
        if (mWasOnPause) initializeFTPServices(true, mIsResumeFromActivityResult)
        mIsResumeFromActivityResult = false
        mNavigationDelete!!.onResume()
        mNavigationFetchDir!!.onResume()
        mNavigationTransfer!!.onResume()
        mNavigationNewFolder!!.onResume()
    }

    override fun onPause() {
        LogManager.info(TAG, "On pause")
        mWasOnPause = true
        super.onPause()
    }

    override fun onDestroy() {
        LogManager.info(TAG, "On destroy")
        mIsRunning = false
        dismissAllDialogs()
        if (mFTPServices != null && mFTPServices!!.isFetchingFolders) mFTPServices!!.abortFetchDirectoryContent()
        if (mFTPServices != null) mFTPServices!!.destroyConnection()
        mNavigationTransfer!!.onDestroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        LogManager.info(TAG, "On back pressed")
        if (mFTPServices!!.isBusy) return
        if (mIsFABOpen) closeFABMenu()
        if (mIsSearchDisplayed) {
            hideSearchBar()
            return
        }
        if (mCurrentAdapter!!.isInSelectionMode) {
            closeSelectionMode()
            return
        }
        if (mCurrentAdapter!!.previousAdapter != null) {
            destroyCurrentAdapter()
            mFTPServices!!.updateWorkingDirectory(mDirectoryPath)
            return
        }
        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(iMenu: Menu): Boolean {
        menuInflater.inflate(R.menu.navigation, iMenu)
        return true
    }

    private fun showMenuMore(iAnchor: View): Boolean {

        // Inflate the popup_layout.xml
        val layoutInflater: LayoutInflater =
            this.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val layout: View = layoutInflater.inflate(R.layout.action_navigation_more, mToolBar, false)

        // Creating the PopupWindow
        val lMenuMorePopUp: PopupWindow = PopupWindow(
            layout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
        )
        layout.findViewById<View>(R.id.popup_action_download).setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {
                val lPermissions: Array<String> = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                requestPermission(this,
                    lPermissions,
                    ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE,
                    object : OnPermissionAnswer() {
                        override fun onAccepted() {
                            onDownloadClicked()
                        }
                    })
            }
            onDownloadClicked()
            lMenuMorePopUp.dismiss()
        }
        layout.findViewById<View>(R.id.popup_action_delete).setOnClickListener {
            onDeleteClicked()
            lMenuMorePopUp.dismiss()
        }
        lMenuMorePopUp.width = LinearLayout.LayoutParams.WRAP_CONTENT
        lMenuMorePopUp.height = LinearLayout.LayoutParams.WRAP_CONTENT
        lMenuMorePopUp.isFocusable = true
        lMenuMorePopUp.showAsDropDown(iAnchor)
        return true
    }

    private fun showMenuSort(iAnchor: View): Boolean {

        // Inflate the popup_layout.xml
        val layoutInflater: LayoutInflater =
            this.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val lLayout: View = layoutInflater.inflate(R.layout.action_navigation_sort, mToolBar, false)

        // Creating the PopupWindow
        val lMenuSortPopUp: PopupWindow = PopupWindow(
            lLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
        )
        lLayout.findViewById<View>(R.id.popup_action_a_to_z).setOnClickListener { iView: View? ->
            lMenuSortPopUp.dismiss()
            mCurrentAdapter!!.comparator = AtoZComparator()
            mCurrentAdapter!!.notifyComparatorChanged()
        }
        lLayout.findViewById<View>(R.id.popup_action_z_to_a).setOnClickListener { iView: View? ->
            lMenuSortPopUp.dismiss()
            mCurrentAdapter!!.comparator = ZtoAComparator()
            mCurrentAdapter!!.notifyComparatorChanged()
        }
        lLayout.findViewById<View>(R.id.popup_action_recent).setOnClickListener { iView: View? ->
            lMenuSortPopUp.dismiss()
            mCurrentAdapter!!.comparator = RecentComparator()
            mCurrentAdapter!!.notifyComparatorChanged()
        }
        lMenuSortPopUp.width = LinearLayout.LayoutParams.WRAP_CONTENT
        lMenuSortPopUp.height = LinearLayout.LayoutParams.WRAP_CONTENT
        lMenuSortPopUp.isFocusable = true
        lMenuSortPopUp.showAsDropDown(iAnchor)
        return true
    }

    override fun onOptionsItemSelected(iItem: MenuItem): Boolean {
        when (iItem.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_search -> {
                showSearchBar()
                return true
            }
            R.id.action_more -> {
                showMenuMore(mToolBar!!.findViewById(R.id.action_more))
                return true
            }
            R.id.action_choose_sort -> {
                showMenuSort(mToolBar!!.findViewById(R.id.action_choose_sort))
                return true
            }
            else -> return super.onOptionsItemSelected(iItem)
        }
    }

    override fun onRequestPermissionsResult(iRequestCode: Int,
                                            iPermissions: Array<String>,
                                            iGrantResults: IntArray
    ) {
        super.onRequestPermissionsResult(iRequestCode, iPermissions, iGrantResults)
        when (iRequestCode) {
            ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE -> {

                // If request is cancelled, the result arrays are empty.
                if (iGrantResults.size > 0 && iGrantResults.get(0) == PackageManager.PERMISSION_GRANTED) {
                    LogManager.info(TAG, "PERMISSION OK : READ_EXTERNAL_STORAGE")
                    if (mOnPermissionAnswer != null) mOnPermissionAnswer!!.onAccepted()
                } else {
                    LogManager.info(TAG, "PERMISSION DENY : READ_EXTERNAL_STORAGE")
                    if (mOnPermissionAnswer != null) mOnPermissionAnswer!!.onDenied()
                }
            }
        }
    }

    override fun onActivityResult(iRequestCode: Int, iResultCode: Int, iData: Intent?) {
        super.onActivityResult(iRequestCode, iResultCode, iData)

        // onActivityResult() is called before onResume()
        mIsResumeFromActivityResult = true
        if (iRequestCode == ACTIVITY_REQUEST_CODE_SELECT_FILES) {
            if (iResultCode == RESULT_OK) {
                val lUriList: MutableList<Uri> = ArrayList()
                if (iData!!.data == null) {
                    val lCD: ClipData? = iData.clipData
                    var i: Int = -1
                    while (++i < lCD!!.itemCount) lUriList.add(lCD.getItemAt(i).uri)
                } else lUriList.add(iData.data!!)
                if (lUriList.size > 0) {
                    mNavigationTransfer!!.createDialogUploadSelection(lUriList.toTypedArray())
                }
            }
        }
    }

    private fun terminateNavigation() {
        mFTPServices!!.destroyConnection()
        if (mIsShowingTransfer) {
            val lFTPTransfers: Array<FTPTransfer> =
                FTPTransfer.getFTPTransferInstance(mFTPServer!!.dataBaseId)
            for (lItem: FTPTransfer in lFTPTransfers) {
                lItem.destroyConnection()
            }
        }
        finish()
    }

    private fun requestPermission(iActivity: Activity,
                                  iPermissions: Array<String>,
                                  iRequestCode: Int,
                                  iOnPermissionAnswer: OnPermissionAnswer
    ) {
        mOnPermissionAnswer = iOnPermissionAnswer
        ActivityCompat.requestPermissions(
            this, iPermissions, iRequestCode
        )
    }

    private fun initialize() {
        val lFTPServerDAO: FTPServerDAO? = DataBase.fTPServerDAO
        val lBundle: Bundle? = intent.extras

        // Server ID
        val lServerId: Int = lBundle!!.getInt(KEY_DATABASE_ID)
        if (lServerId != NO_DATABASE_ID) {
            mFTPServer = lFTPServerDAO!!.fetchById(lServerId)
        } else {
            LogManager.error(TAG, "Server id is not initialized")
        }

        // FTPServer fetch
        mFTPServer = lFTPServerDAO!!.fetchById(lServerId)
        if (mFTPServer == null) {
            Utils.createErrorAlertDialog(this, "Navigation page has failed...").show()
            finish()
        }

        // Directory path
        if (mDirectoryPath == null) mDirectoryPath =
            lBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY)

        // Download procedures
        mNavigationTransfer = NavigationTransfer(this, mHandler)

        // Fetch directory procedures
        mNavigationFetchDir = NavigationFetchDir(this, mHandler)

        // Delete procedures
        mNavigationDelete = NavigationDelete(this, mHandler)

        // New folder procedures
        mNavigationNewFolder = NavigationNewFolder(this, mHandler)

        // Bad connection, Large dir, Reconnect dialog
        initializeDialogs()
    }

    private fun initializeFTPServices(iIsUpdating: Boolean, iBlockFetchDirIfSuccessRecovery: Boolean
    ) {
        LogManager.info(TAG, "Retrieve FTP Services")
        mFTPServices = FTPServices.Companion.getFTPServicesInstance(mFTPServer!!.dataBaseId)
        if (mFTPServices == null) { // Can happens on android studio apply changes
            LogManager.error(TAG, "FTP CONNECTION NULL")
            Utils.showLongToast(this, "FTP CONNECTION NULL")
            initializeFTPConnection()
        } else {
            LogManager.info(TAG, "FTP Services fully recovered by get instance")
            LogManager.info(TAG, "FTP Services instance busy : " + mFTPServices!!.isBusy)
            if (mFTPServices!!.isBusy) return
            if (!AppCore.networkManager!!.isNetworkAvailable) {
                mHandler!!.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST)
            } else if (!mFTPServices!!.isLocallyConnected) {
                mHandler!!.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST)
            } else {
                mFTPServices!!.isRemotelyConnectedAsync(object : OnRemotelyConnectedResult {
                    override fun onResult(iResult: Boolean) {
                        if (iResult) {
                            if (!mIsShowingTransfer && !iBlockFetchDirIfSuccessRecovery) mHandler!!.sendMessage(
                                Message.obtain(
                                    mHandler, NAVIGATION_ORDER_FETCH_DIRECTORY, iIsUpdating
                                )
                            )
                        } else mHandler!!.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST)
                    }
                })
            }
        }
        mFTPServices!!.setOnConnectionLost(object : OnConnectionLost {
            override fun onConnectionLost() {
                mHandler!!.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST)
            }
        })
    }

    private fun initializeFTPConnection() {
        LogManager.info(TAG, "Build FTP Connection")
        if (mFTPServer == null) {
            LogManager.error(TAG, "mFTPServer is null")
            LogManager.error(
                TAG, Arrays.toString(Exception("mFTPServer instance is null").stackTrace)
            )
            return
        }
        mFTPServices = FTPServices(mFTPServer)

        //        mFetchDirLoadingDialog.setTitle("Connection..."); // TODO : strings
        //        mFetchDirLoadingDialog.show();
        mFTPServiceConnectionDialog = Utils.createProgressDialog(this,
            "Connection...",
            object : DialogInterface.OnClickListener {
                override fun onClick(iDialog: DialogInterface, iWhich: Int) {
                    iDialog.dismiss()
                    mFTPServices!!.destroyConnection()
                    finish()
                }
            })
        mFTPServices!!.connect(object : OnConnectionResult {
            override fun onSuccess() {
                mHandler!!.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS)
                mHandler!!.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_SUCCESS)
            }

            override fun onFail(iErrorEnum: ErrorCodeDescription, iErrorCode: Int) {
                if (iErrorEnum == ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED) return
                mErrorCode = iErrorCode
                mHandler!!.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS)
                mHandler!!.sendMessage(
                    Message.obtain(
                        mHandler, NAVIGATION_MESSAGE_CONNECTION_FAIL, 1, 0, iErrorEnum
                    )
                )
            }
        })
    }

    private fun initializeGUI() {
        mToolBar = findViewById(R.id.navigation_toolbar)
        setSupportActionBar(mToolBar)
        mMainFAB = findViewById(R.id.navigation_floating_action_button)
        mCreateFolderFAB = findViewById(R.id.navigation_fab_create_folder)
        mUploadFileFAB = findViewById(R.id.navigation_fab_upload_file)
        mMainFAB!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                if (!mIsFABOpen) {
                    openFABMenu()
                } else {
                    closeFABMenu()
                }
            }
        })
        mCreateFolderFAB!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                mNavigationNewFolder!!.createDialogNewFolder()
            }
        })
        mUploadFileFAB!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                onUploadFileClicked()
            }
        })
        mRecyclerSection = findViewById(R.id.navigation_recycler_section)
        mSearchBar = findViewById(R.id.navigation_search_bar)
        mSearchEditTextLayout = findViewById(R.id.search_edit_text_layout)
        mSearchEditText = findViewById(R.id.search_edit_text)
        val lSearchBarHomeLayout: View = findViewById(R.id.navigation_home_layout)
        lSearchBarHomeLayout.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                onBackPressed()
            }
        })
        val lSearchBarHomeButton: View = findViewById(R.id.navigation_home_button)
        lSearchBarHomeButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                onBackPressed()
            }
        })
        mSearchEditTextLayout!!.setEndIconOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                hideSearchBar()
            }
        })
        mSearchEditText!!.addTextChangedListener(object : AfterTextChanged() {
            override fun afterTextChanged(s: Editable) {
                mCurrentAdapter!!.filter.filter(s.toString())
            }
        })
        val lRootView: View = findViewById(android.R.id.content)
        lRootView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val lMeasureRect: Rect =
                    Rect() //you should cache this, onGlobalLayout can get called often
                lRootView.getWindowVisibleDisplayFrame(lMeasureRect)

                // lMeasureRect.bottom is the position above soft keypad
                val lKeypadHeight: Int = lRootView.rootView.height - lMeasureRect.bottom
                if (lKeypadHeight > 0) { // keyboard is opened
                } else { // keyboard is closed
                    if (mIsSearchDisplayed) onBackPressed()
                }
            }
        })
    }

    private fun initializeHandler() {
        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(iMsg: Message) {
                val lErrorDescription: ErrorCodeDescription
                val lFiles: Array<FTPFile>
                if (!mIsRunning || isFinishing) return
                when (iMsg.what) {
                    NAVIGATION_ORDER_DISMISS_DIALOGS -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_DISMISS_DIALOGS")
                        dismissAllDialogs()
                    }
                    NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS")
                        if (mFetchDirLoadingDialog != null) mFetchDirLoadingDialog!!.dismiss()
                        if (mFetchLargeDirDialog != null) mFetchLargeDirDialog!!.dismiss()
                    }
                    NAVIGATION_ORDER_SELECTED_MODE_ON -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_SELECTED_MODE_ON")
                        if (mCurrentAdapter != null && !mCurrentAdapter!!.isInSelectionMode) {
                            mCurrentAdapter!!.setSelectionMode(true)
                            mCurrentAdapter!!.swipeRefreshLayout.isEnabled = false
                        }
                    }
                    NAVIGATION_ORDER_SELECTED_MODE_OFF -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_SELECTED_MODE_OFF")
                        if (mCurrentAdapter != null && mCurrentAdapter!!.isInSelectionMode) {
                            mCurrentAdapter!!.setSelectionMode(false)
                            mCurrentAdapter!!.swipeRefreshLayout.isEnabled = true
                        }
                    }
                    NAVIGATION_ORDER_FETCH_DIRECTORY -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_FETCH_DIRECTORY")
                        val lIsUpdating: Boolean = iMsg.obj as Boolean
                        mNavigationFetchDir!!.runFetchProcedures(
                            mDirectoryPath, mIsLargeDirectory, lIsUpdating
                        )
                    }
                    NAVIGATION_ORDER_REFRESH_DATA -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_REFRESH_DATA")
                        if (!mFTPServices!!.isReconnecting && mIsDirectoryFetchFinished) {
                            mCurrentAdapter!!.swipeRefreshLayout.isRefreshing = true
                            mNavigationFetchDir!!.runFetchProcedures(mDirectoryPath, false, true)
                        }
                    }
                    NAVIGATION_ORDER_RECONNECT -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_RECONNECT")
                        mReconnectDialog!!.show()
                        mFTPServices!!.reconnect(object : OnConnectionRecover {
                            override fun onConnectionRecover() {
                                mHandler!!.sendEmptyMessage(NAVIGATION_MESSAGE_RECONNECT_SUCCESS)
                            }

                            override fun onConnectionDenied(iErrorEnum: ErrorCodeDescription,
                                                            iErrorCode: Int
                            ) {
                                if (mFTPServices != null) mFTPServices!!.disconnect()
                                mErrorCode = iErrorCode
                                mHandler!!.sendMessage(
                                    Message.obtain(
                                        mHandler, NAVIGATION_MESSAGE_RECONNECT_FAIL, iErrorEnum
                                    )
                                )
                            }
                        })
                    }
                    NAVIGATION_MESSAGE_RECONNECT_SUCCESS -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_SUCCESS")
                        dismissAllDialogsExcepted(mTransferDialog, mChooseExistingFileActionDialog)
                        if (!mIsShowingTransfer) mNavigationFetchDir!!.runFetchProcedures(
                            mDirectoryPath, mIsLargeDirectory, true
                        )
                    }
                    NAVIGATION_MESSAGE_CONNECTION_SUCCESS -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_SUCCESS")
                        mNavigationFetchDir!!.runFetchProcedures(
                            mDirectoryPath, mIsLargeDirectory, true
                        )
                    }
                    NAVIGATION_MESSAGE_CONNECTION_FAIL -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_FAIL")
                        val lIsRecovering: Boolean = iMsg.arg1 == 1
                        lErrorDescription = iMsg.obj as ErrorCodeDescription
                        AlertDialog.Builder(this@NavigationActivity)
                            .setTitle("Error") // TODO string
                            .setMessage(
                                ((if (lIsRecovering) "Reconnection" else "Connection") + " failed..." + "\nError : " + lErrorDescription + "\nCode :" + mErrorCode)
                            ).setCancelable(false).setNegativeButton(
                                "Terminate",
                                object : DialogInterface.OnClickListener {
                                    override fun onClick(dialog: DialogInterface, which: Int
                                    ) {
                                        dialog.dismiss()
                                        finish()
                                    }
                                }).create().show()
                        if (mFTPServices!!.isConnecting) mFTPServices!!.abortConnection()
                    }
                    NAVIGATION_MESSAGE_CONNECTION_LOST -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_LOST")
                        mFTPServices!!.abortFetchDirectoryContent()
                        mFTPServices!!.abortDeleting()
                        dismissAllDialogsExcepted(
                            mTransferDialog, mChooseExistingFileActionDialog, mReconnectDialog
                        )
                        if (!mIsShowingTransfer) mHandler!!.sendEmptyMessage(
                            NAVIGATION_ORDER_RECONNECT
                        )
                    }
                    NAVIGATION_MESSAGE_TRANSFER_FINISHED, NAVIGATION_MESSAGE_DELETE_FINISHED -> {
                        LogManager.info(
                            TAG,
                            "Handle : NAVIGATION_MESSAGE_DOWNLOAD_FINISHED or " + "NAVIGATION_MESSAGE_DELETE_FINISHED"
                        )
                        showFABMenu()
                        mFTPServices!!.isRemotelyConnectedAsync(object : OnRemotelyConnectedResult {
                            override fun onResult(iResult: Boolean) {
                                if (!iResult) {
                                    dismissAllDialogs()
                                    mHandler!!.sendEmptyMessage(NAVIGATION_ORDER_RECONNECT)
                                } else {
                                    mHandler!!.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA)
                                }
                            }
                        })
                    }
                    NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS -> {
                        LogManager.info(
                            TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS"
                        ) // TODO : Sort items
                        val lNewDirectory: FTPFile = iMsg.obj as FTPFile
                        mFetchDirLoadingDialog!!.dismiss()
                        mCurrentAdapter!!.insertItemPersistently(lNewDirectory, 0)
                        mCurrentAdapter!!.recyclerView.scrollToPosition(0)
                    }
                    NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL")
                        lErrorDescription = iMsg.obj as ErrorCodeDescription
                        mFetchDirLoadingDialog!!.dismiss()
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog!!.isShowing)) {
                            mErrorADialog = AlertDialog.Builder(this@NavigationActivity)
                                .setTitle("Error") // TODO string
                                .setMessage("Creation has failed...\nCode : " + lErrorDescription.name)
                                .setCancelable(false).setPositiveButton(
                                    "Ok"
                                ) { iDialog, iWhich -> iDialog.dismiss() }.create()
                            mErrorADialog!!.show()
                        }
                    }
                    NAVIGATION_MESSAGE_RECONNECT_FAIL -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_FAIL")
                        dismissAllDialogs()
                        lErrorDescription = iMsg.obj as ErrorCodeDescription
                        AlertDialog.Builder(this@NavigationActivity)
                            .setTitle("Reconnection denied") // TODO string
                            .setMessage("Reconnection has failed...\nCode : " + lErrorDescription.name)
                            .setCancelable(false)
                            .setPositiveButton("Ok", object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface, which: Int) {
                                    dialog.dismiss()
                                    finish()
                                }
                            }).create().show()
                    }
                    NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE")
                        lFiles = iMsg.obj as Array<FTPFile>
                        if (mCurrentAdapter == null) inflateNewAdapter(
                            lFiles, mDirectoryPath, true
                        ) else {
                            mCurrentAdapter!!.setData(lFiles)
                            mCurrentAdapter!!.appearVertically()
                            mCurrentAdapter!!.swipeRefreshLayout.isRefreshing = false
                        }
                    }
                    NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH -> {
                        LogManager.info(
                            TAG, "Handle : NAVIGATION_MESSAGE_NEW_DIRECTORY_SUCCESS_FETCH"
                        )
                        mHandler!!.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS)
                        lFiles = iMsg.obj as Array<FTPFile>
                        inflateNewAdapter(lFiles, mDirectoryPath, false)
                    }
                    NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH -> {
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH")
                        mIsDirectoryFetchFinished = true
                        lErrorDescription = iMsg.obj as ErrorCodeDescription
                        if (mCurrentAdapter != null) mCurrentAdapter!!.setItemsClickable(true)
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog!!.isShowing)) {
                            mErrorADialog = AlertDialog.Builder(this@NavigationActivity)
                                .setTitle("Error") // TODO string
                                .setMessage("Connection has failed...\nCode : " + lErrorDescription.name)
                                .setCancelable(false)
                                .setPositiveButton("Ok"
                                ) { iDialog, iWhich ->
                                    iDialog.dismiss()
                                    if (mCurrentAdapter!!.previousAdapter == null) onBackPressed()
                                }.create()
                            mErrorADialog!!.show()
                        }
                        if (mCurrentAdapter != null && mCurrentAdapter!!.swipeRefreshLayout.isRefreshing) {
                            mCurrentAdapter!!.swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun initializeDialogs() {
        mErrorADialog = Utils.createErrorAlertDialog(this, "")
        mSuccessDialog = Utils.createSuccessAlertDialog(this, "")

        // Loading dialog
        mFetchDirLoadingDialog = Utils.createProgressDialog(this, null)
        mFetchDirLoadingDialog!!.setTitle("Loading...") // TODO : strings
        mFetchDirLoadingDialog!!.create()

        // Reconnection dialog
        mReconnectDialog =
            Utils.createProgressDialog(this, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    dialog.dismiss()
                    terminateNavigation()
                }
            })
        mReconnectDialog!!.setCancelable(false)
        mReconnectDialog!!.setTitle("Reconnection...") // TODO : strings
        mReconnectDialog!!.create()
    }

    private fun openFABMenu() {
        if (!mIsFABOpen) {
            mIsFABOpen = true
            ViewCompat.animate((mMainFAB)!!).rotation(45f).withLayer().setDuration(500L)
                .setInterpolator(BounceInterpolator()).start()
            (mCreateFolderFAB as View?)!!.visibility = View.VISIBLE
            (mUploadFileFAB as View?)!!.visibility = View.VISIBLE
            mCreateFolderFAB!!.animate()
                .translationY(-resources.getDimension(R.dimen.sub_fab_floor_1)).interpolator =
                DecelerateInterpolator(AppCore.Companion.FLOATING_ACTION_BUTTON_INTERPOLATOR)
            mUploadFileFAB!!.animate()
                .translationY(-resources.getDimension(R.dimen.sub_fab_floor_2)).interpolator =
                DecelerateInterpolator(AppCore.Companion.FLOATING_ACTION_BUTTON_INTERPOLATOR)
        }
    }

    fun closeFABMenu() {
        if (mIsFABOpen) {
            mIsFABOpen = false
            ViewCompat.animate((mMainFAB)!!).rotation(0.0f).withLayer().setDuration(500L)
                .setInterpolator(BounceInterpolator()).start()
            mCreateFolderFAB!!.animate().translationY(0f).withEndAction(object : Runnable {
                override fun run() {
                    (mCreateFolderFAB as View?)!!.visibility = View.GONE
                }
            })
            mUploadFileFAB!!.animate().translationY(0f).withEndAction(object : Runnable {
                override fun run() {
                    (mUploadFileFAB as View?)!!.visibility = View.GONE
                }
            })
        }
    }

    protected fun closeSelectionMode() {
        mCurrentAdapter!!.setSelectionMode(false)
        showFABMenu()
    }

    protected fun hideFABMenu() {
        mMainFAB!!.hide()
        mCreateFolderFAB!!.hide()
        mUploadFileFAB!!.hide()
    }

    private fun showFABMenu() {
        mMainFAB!!.show()
        mCreateFolderFAB!!.show()
        mUploadFileFAB!!.show()
    }

    private fun showSearchBar() {
        if (mIsSearchDisplayed) return
        val fadeOut: Animation = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = DecelerateInterpolator() //add this
        fadeOut.duration = 200
        fadeOut.setAnimationListener(object : OnEndAnimation() {
            override fun onAnimationEnd(animation: Animation) {
                mToolBar!!.visibility = View.INVISIBLE
            }
        })
        mToolBar!!.startAnimation(fadeOut)
        Utils.showKeyboard(this, mSearchEditText)
        hideFABMenu()

        // If we set true now, globalLayout will back press because keyboard is not showed yet
        mHandler!!.post(Runnable({ mIsSearchDisplayed = true }))
    }

    private fun hideSearchBar() {
        if (!mIsSearchDisplayed) return
        mIsSearchDisplayed = false
        val fadeIn: Animation = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator() //add this
        fadeIn.duration = 200
        fadeIn.setAnimationListener(object : OnStartAnimation() {
            override fun onAnimationStart(animation: Animation) {
                mToolBar!!.visibility = View.VISIBLE
            }
        })
        mCurrentAdapter!!.filter.filter("")
        mToolBar!!.startAnimation(fadeIn)
        mHandler!!.postDelayed(Runnable({ Utils.hideKeyboard(this) }), 200)
        showFABMenu()
    }

    private fun destroyCurrentAdapter() {
        LogManager.info(TAG, "Destroy current adapter")
        val lDeprecatedAdapter: NavigationRecyclerViewAdapter? = mCurrentAdapter
        lDeprecatedAdapter!!.disappearOnRightAndDestroy(object : Runnable {
            override fun run() {
                lDeprecatedAdapter.recyclerView.adapter = null
                mRecyclerSection!!.removeView(lDeprecatedAdapter.swipeRefreshLayout)
            }
        })
        mCurrentAdapter = lDeprecatedAdapter.previousAdapter
        mCurrentAdapter!!.appearFromLeft()
        mCurrentAdapter!!.nextAdapter = null
        mDirectoryPath = mCurrentAdapter!!.directoryPath
    }

    private fun inflateNewAdapter(iFTPFiles: Array<FTPFile>,
                                  iDirectoryPath: String?,
                                  iForceVerticalAppear: Boolean
    ) {
        LogManager.info(TAG, "Inflate new adapter")
        val lSwipeRefreshLayout: SwipeRefreshLayout =
            View.inflate(this, R.layout.navigation_recycler_layout, null) as SwipeRefreshLayout
        lSwipeRefreshLayout.setOnRefreshListener(object : OnRefreshListener {
            override fun onRefresh() {
                mHandler!!.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA)
            }
        })
        lSwipeRefreshLayout.setColorSchemeResources(
            R.color.lightAccent, R.color.lightPrimary, R.color.lightPrimaryDark
        )
        mRecyclerSection!!.addView(lSwipeRefreshLayout)
        val lNewRecyclerView: RecyclerView =
            lSwipeRefreshLayout.findViewById(R.id.navigation_recycler_view)
        lNewRecyclerView.layoutManager = LinearLayoutManager(this)
        val lNewAdapter: NavigationRecyclerViewAdapter = NavigationRecyclerViewAdapter(
            this, mRecyclerSection, lNewRecyclerView, lSwipeRefreshLayout, iDirectoryPath, false
        )
        val lDividerItemDecoration: DividerItemDecoration =
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        lNewRecyclerView.addItemDecoration(lDividerItemDecoration)
        lNewRecyclerView.adapter = lNewAdapter
        lNewAdapter.setOnClickListener(object : OnClickListener {
            override fun onClick(iFTPFile: FTPFile) {
                if (lNewAdapter.isInSelectionMode) {
                    lNewAdapter.switchCheckBox(iFTPFile)
                } else {
                    if (iFTPFile.isDirectory) {
                        hideSearchBar()
                        lNewAdapter.setItemsClickable(false)
                        mIsLargeDirectory =
                            iFTPFile.size > NavigationFetchDir.Companion.LARGE_DIRECTORY_SIZE
                        mNavigationFetchDir!!.runFetchProcedures(
                            iDirectoryPath + iFTPFile.name + "/", mIsLargeDirectory, false
                        )
                    } else { // If it is a file
                        lNewAdapter.setItemsClickable(true)
                    }
                }
            }
        })
        lNewAdapter.setOnLongClickListener(object : OnLongClickListener {
            override fun onLongClick(iFTPFile: FTPFile?) {
                if (!lNewAdapter.isInSelectionMode) {
                    closeFABMenu()
                    hideFABMenu()
                    lNewAdapter.setSelectionMode(true)
                    lNewAdapter.setSelectedCheckBox(iFTPFile, true)
                }
            }
        })
        val lCurrentAdapterSavedStatus: NavigationRecyclerViewAdapter? = mCurrentAdapter
        val lIsTheFirstAdapter: Boolean = mCurrentAdapter == null
        lNewAdapter.onFirstViewHolderCreation = object : OnFirstViewHolderCreation {
            override fun onCreation() {
                mHandler!!.post {
                    closeFABMenu()

                    // if it's not the first list
                    if (!lIsTheFirstAdapter) {
                        lNewAdapter.previousAdapter = lCurrentAdapterSavedStatus
                        lCurrentAdapterSavedStatus!!.nextAdapter = lNewAdapter
                        lCurrentAdapterSavedStatus.disappearOnLeft()
                    }

                    // if it's the first list or need to appear vertically
                    if (lIsTheFirstAdapter || iForceVerticalAppear) {
                        lNewAdapter.appearVertically()
                    } else {
                        lNewAdapter.appearFromRight()
                    }
                }
            }
        }
        lNewAdapter.setData(iFTPFiles)
        mCurrentAdapter = lNewAdapter
        if (iFTPFiles.isEmpty()) {
            mCurrentAdapter!!.onFirstViewHolderCreation!!.onCreation()
        }
    }

    private fun onUploadFileClicked() {
        val lEnclosingDirectory: FTPFile? = mFTPServices!!.currentDirectory
        if (lEnclosingDirectory != null && !lEnclosingDirectory.hasPermission(
                FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION
            )) {
            val lErrorMessage: String = "Can't upload here\nYou don't have the permissions"
            createDialogError(lErrorMessage).show()
            return
        }
        val lIntent: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        lIntent.type = "*/*"
        lIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        lIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_SELECT_FILES)
    }

    private fun onDeleteClicked() {
        if (mCurrentAdapter!!.isInSelectionMode) {
            if (mCurrentAdapter!!.selection.isEmpty()) {
                showFABMenu()
                mCurrentAdapter!!.setSelectionMode(false)
            } else mNavigationDelete!!.createDialogDeleteSelection()
        } else {
            hideFABMenu()
            mCurrentAdapter!!.setSelectionMode(true)
        }
    }

    private fun onDownloadClicked() {
        if (mCurrentAdapter!!.isInSelectionMode) {
            if (mCurrentAdapter!!.selection.isEmpty()) {
                showFABMenu()
                mCurrentAdapter!!.setSelectionMode(false)
            } else mNavigationTransfer!!.createDialogDownloadSelection()
        } else {
            hideFABMenu()
            mCurrentAdapter!!.setSelectionMode(true)
        }
    }

    fun createDialogError(iMessage: String?): AlertDialog { // TODO : Replace by resources ID
        mErrorADialog =
            AlertDialog.Builder(this@NavigationActivity).setTitle("Error") // TODO : string
                .setMessage(iMessage).setCancelable(false)
                .setPositiveButton("Ok", object : DialogInterface.OnClickListener {
                    override fun onClick(iDialog: DialogInterface, iWhich: Int) {
                        iDialog.dismiss()
                    }
                }).create()
        return mErrorADialog!!
    }

    fun dismissAllDialogs() {
        if (mErrorADialog != null) mErrorADialog!!.cancel()
        if (mSuccessDialog != null) mSuccessDialog!!.cancel()
        if (mFetchDirLoadingDialog != null) mFetchDirLoadingDialog!!.cancel()
        if (mIndexingPendingFilesDialog != null) mIndexingPendingFilesDialog!!.cancel()
        if (mReconnectDialog != null) mReconnectDialog!!.cancel()
        if (mFetchLargeDirDialog != null) mFetchLargeDirDialog!!.cancel()
        if (mTransferDialog != null) mTransferDialog!!.cancel()
        if (mChooseExistingFileActionDialog != null) mChooseExistingFileActionDialog!!.cancel()
        if (mDeletingInfoDialog != null) mDeletingInfoDialog!!.cancel()
        if (mDeletingErrorDialog != null) mDeletingErrorDialog!!.cancel()
        if (mFTPServiceConnectionDialog != null) mFTPServiceConnectionDialog!!.cancel()
    }

    protected fun dismissAllDialogsExcepted(vararg iToNotDismiss: DialogInterface?) {
        val lDialogList: List<*> = Arrays.asList(*iToNotDismiss)
        if (mErrorADialog != null && !lDialogList.contains(mErrorADialog)) mErrorADialog!!.cancel()
        if (mSuccessDialog != null && !lDialogList.contains(mSuccessDialog)) mSuccessDialog!!.cancel()
        if (mFetchDirLoadingDialog != null && lDialogList.contains(mFetchDirLoadingDialog)) mFetchDirLoadingDialog!!.cancel()
        if (mIndexingPendingFilesDialog != null && !lDialogList.contains(mIndexingPendingFilesDialog)) mIndexingPendingFilesDialog!!.cancel()
        if (mReconnectDialog != null && !lDialogList.contains(mReconnectDialog)) mReconnectDialog!!.cancel()
        if (mFetchLargeDirDialog != null && !lDialogList.contains(mFetchLargeDirDialog)) mFetchLargeDirDialog!!.cancel()
        if (mTransferDialog != null && !lDialogList.contains(mTransferDialog)) mTransferDialog!!.cancel()
        if (mChooseExistingFileActionDialog != null && !lDialogList.contains(
                mChooseExistingFileActionDialog
            )) mChooseExistingFileActionDialog!!.cancel()
        if (mDeletingInfoDialog != null && !lDialogList.contains(mDeletingInfoDialog)) mDeletingInfoDialog!!.cancel()
        if (mDeletingErrorDialog != null && !lDialogList.contains(mDeletingErrorDialog)) mDeletingErrorDialog!!.cancel()
        if (mFTPServiceConnectionDialog != null && !lDialogList.contains(mFTPServiceConnectionDialog)) mFTPServiceConnectionDialog!!.cancel()
    }

    private abstract inner class OnPermissionAnswer {
        open fun onAccepted() {}
        fun onDenied() {}
    }

    companion object {
        const val NO_DATABASE_ID: Int = -1
        const val ROOT_DIRECTORY: String = "/"
        const val KEY_DATABASE_ID: String = "KEY_DATABASE_ID"
        const val KEY_DIRECTORY_PATH: String = "KEY_DIRECTORY_PATH"
        const val NAVIGATION_MESSAGE_CONNECTION_SUCCESS: Int = 10
        const val NAVIGATION_MESSAGE_CONNECTION_FAIL: Int = 11
        const val NAVIGATION_MESSAGE_CONNECTION_LOST: Int = 12
        const val NAVIGATION_MESSAGE_RECONNECT_SUCCESS: Int = 13
        const val NAVIGATION_MESSAGE_RECONNECT_FAIL: Int = 14
        const val NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS: Int = 15
        const val NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL: Int = 16
        const val NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE: Int = 17
        const val NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH: Int = 18
        const val NAVIGATION_MESSAGE_DIRECTORY_FAIL_UPDATE: Int = 20
        const val NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH: Int = 19
        const val NAVIGATION_MESSAGE_TRANSFER_FINISHED: Int = 21
        const val NAVIGATION_MESSAGE_DELETE_FINISHED: Int = 23
        const val NAVIGATION_ORDER_DISMISS_DIALOGS: Int = 100
        const val NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS: Int = 101
        const val NAVIGATION_ORDER_FETCH_DIRECTORY: Int = 102
        const val NAVIGATION_ORDER_REFRESH_DATA: Int = 103
        const val NAVIGATION_ORDER_SELECTED_MODE_ON: Int = 104
        const val NAVIGATION_ORDER_SELECTED_MODE_OFF: Int = 105
        const val NAVIGATION_ORDER_RECONNECT: Int = 106
        private const val TAG: String = "FTP NAVIGATION ACTIVITY"
        private const val ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE: Int = 1
        private const val ACTIVITY_REQUEST_CODE_SELECT_FILES: Int = 2
    }
}