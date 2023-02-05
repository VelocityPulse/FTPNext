package com.vpulse.ftpnext

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.vpulse.ftpnext.adapters.MainRecyclerViewAdapter
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.DataBase
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO
import com.vpulse.ftpnext.ftpservices.AFTPConnection.ErrorCodeDescription
import com.vpulse.ftpnext.ftpservices.AFTPConnection.OnConnectionResult
import com.vpulse.ftpnext.ftpservices.FTPLogManager
import com.vpulse.ftpnext.ftpservices.FTPServices
import com.vpulse.ftpnext.navigation.NavigationActivity

/*
TODO : Resume when screen change orientation
TODO : Add a security network connection / network authorisation
TODO : Securities authorization popups
Ideas :
    - Research mode in FTPNavigationActivity
    - Ask to respawn to the last folder visited if diff than root
    - Display to type of file/dir rights xxx-xxx-xxx or read
    - Remember the last folder used for create a server
    - Queues of file to download even if the connection fail
    - Notification view of transfer
    - Check if the download can continue after a stop
    - Connection with ssh
    - Multiple theme
    - Add a shortcut of a server on the desktop
    - A FTPTransferActivity for show / manage all the transfers
    - Ask to show adds
    - Stop phone(?) after download
    - Airplane mode after download
    - Song after download...
    - Duplicate server
    - Sort per type, name, size, and date

    - Log historic connection for the user

 */
class MainActivity : AppCompatActivity() {
    private var mAdapter: MainRecyclerViewAdapter? = null
    private var mIsRunning: Boolean = false
    private var mIsBusy: Boolean = false
    private var mDialog: Dialog? = null
    private var mFTPServerDAO: FTPServerDAO? = null
    override fun onCreate(iSavedInstanceState: Bundle?) {
        super.onCreate(iSavedInstanceState)
        AppCore.instance!!.startApplication(this)
        setTheme(AppCore.appTheme)
        FTPLogManager.notifyThemeChanged(this)
        setContentView(R.layout.activity_main)
        initializeGUI()
        initializeAdapter()
        initialize()
        initializePermissions()
        runTests()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mDialog != null && mDialog!!.isShowing) mDialog!!.dismiss()
        mIsRunning = false
    }

    override fun onCreateOptionsMenu(iMenu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, iMenu)
        return true
    }

    override fun onOptionsItemSelected(iMenuItem: MenuItem): Boolean {
        val id: Int = iMenuItem.itemId
        if (id == R.id.action_parameters) {
            startSettingsActivity()
            return true
        }
        return super.onOptionsItemSelected(iMenuItem)
    }

    private fun initializeGUI() {
        setSupportActionBar(findViewById<View>(R.id.main_toolbar) as Toolbar?)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        val lFloatingActionButton: FloatingActionButton =
            findViewById(R.id.navigation_floating_action_button)
        lFloatingActionButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                startFTPConfigureServerActivity(ConfigureServerActivity.Companion.NO_DATABASE_ID)
            }
        })
    }

    private fun initializeAdapter() {
        val lRecyclerView: RecyclerView = findViewById(R.id.main_recycler_view)
        lRecyclerView.layoutManager = LinearLayoutManager(this)
        mAdapter = MainRecyclerViewAdapter(lRecyclerView, this)
        lRecyclerView.adapter = mAdapter
        val mDividerItemDecoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        lRecyclerView.addItemDecoration(mDividerItemDecoration)
        mAdapter!!.setOnLongClickListener(object : MainRecyclerViewAdapter.OnLongClickListener {
            override fun onLongClick(iServerID: Int) {
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(this@MainActivity) // TODO strings
                builder.setTitle(mFTPServerDAO!!.fetchById(iServerID)!!.name)
                    .setItems(R.array.edit_server_array) { _: DialogInterface?, iWhich: Int ->
                        if (iWhich == 0) {
                            AlertDialog.Builder(this@MainActivity).setTitle("Deleting :")
                                .setMessage("Are you sure to delete this server ?")
                                .setPositiveButton(
                                    "yes"
                                ) { dialog: DialogInterface?, which: Int ->
                                    mFTPServerDAO!!.delete(iServerID)
                                    mAdapter!!.removeItem(iServerID)
                                }.setNegativeButton("no", null).show()
                        } else if (iWhich == 1) startFTPConfigureServerActivity(iServerID)
                    }
                builder.create()
                builder.show()
            }
        })

        mAdapter!!.setOnClickListener(object : MainRecyclerViewAdapter.OnClickListener {
            override fun onClick(iServerID: Int) {
                onServerClicked(iServerID)
            }
        })
    }

    private fun initialize() {
        mFTPServerDAO = DataBase.fTPServerDAO
        mIsRunning = true
        val lFTPServers: List<FTPServer?>? = mFTPServerDAO!!.fetchAll()
        for (lFTPServer: FTPServer? in lFTPServers!!) {
            mAdapter!!.insertItem(lFTPServer)
        }
    }

    private fun initializePermissions() {
        val lPermissionsReadWrite: Array<String> = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(
            this, lPermissionsReadWrite, ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE
        )
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
                } else {
                    LogManager.info(TAG, "PERMISSION DENY : READ_EXTERNAL_STORAGE")
                }

                // TODO : Make an automatic permission algorithm
                val lPermissionNetwork: Array<String> = arrayOf(
                    Manifest.permission.INTERNET
                )
                ActivityCompat.requestPermissions(
                    this, lPermissionNetwork, ACTIVITY_REQUEST_CODE_INTERNET
                )
            }
        }
    }

    public override fun onActivityResult(iRequestCode: Int, iResultCode: Int, iResultData: Intent?
    ) {
        super.onActivityResult(iRequestCode, iResultCode, iResultData)
        if (iRequestCode == ACTIVITY_REQUEST_CODE_CONFIGURE_SERVER) {
            overridePendingTransition(R.anim.no_animation, R.anim.activity_fade_out_centered)
            if (iResultData != null) {
                val lId: Int =
                    iResultData.getIntExtra(ConfigureServerActivity.Companion.KEY_DATABASE_ID, -1)
                if (lId != -1) {
                    if (iResultCode == ConfigureServerActivity.Companion.ACTIVITY_RESULT_ADD_SUCCESS) mAdapter!!.insertItem(
                        mFTPServerDAO!!.fetchById(lId)
                    ) else if (iResultCode == ConfigureServerActivity.Companion.ACTIVITY_RESULT_UPDATE_SUCCESS) mAdapter!!.updateItem(
                        mFTPServerDAO!!.fetchById(lId)
                    )
                }
            }
        }
    }

    private fun onServerClicked(iServerID: Int) {
        if (mIsBusy) {
            LogManager.error(TAG, "Already trying a connection")
            return
        }
        mIsBusy = true

        //        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) !=
        //                PackageManager.PERMISSION_GRANTED) {
        //
        //            ActivityCompat.requestPermissions(this,
        //                    new String[] {Manifest.permission.INTERNET},
        //                    ACTIVITY_REQUEST_CODE_INTERNET);
        //            return;
        //        }
        val lFTPServer: FTPServer? = mFTPServerDAO!!.fetchById(iServerID)
        val lLoadingAlertDialog: AlertDialog?
        if (lFTPServer == null) {
            Utils.createErrorAlertDialog(this, "FTP Server cannot be found").show()
            return
        }
        if (PreferenceManager.isWifiOnly && !AppCore.networkManager!!.isCurrentNetworkIsWifi) {
            Utils.createAlertDialog(
                this,
                "Wi-Fi only",
                "You can activate cellulars data in the settings page.",
                "Ok",
                null
            ).show()
            return
        }
        val lNewFTPServices = FTPServices(lFTPServer)
        lLoadingAlertDialog =
            Utils.createProgressDialog(this, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    lNewFTPServices.abortConnection()
                    lNewFTPServices.destroyConnection()
                    mIsBusy = false
                }
            })
        lLoadingAlertDialog.setTitle("Connection...") // TODO : strings
        lLoadingAlertDialog.create()
        lLoadingAlertDialog.show()
        lNewFTPServices.connect(object : OnConnectionResult {
            override fun onSuccess() {
                mIsBusy = false
                Utils.cancelAlertDialogOnUIThread(this@MainActivity, lLoadingAlertDialog)
                startFTPNavigationActivity(iServerID)
            }

            override fun onFail(iErrorEnum: ErrorCodeDescription, iErrorCode: Int) {
                mIsBusy = false
                if (iErrorEnum == ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED) return
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (mIsRunning) {
                            lLoadingAlertDialog.cancel()
                            if (lNewFTPServices.isConnecting) lNewFTPServices.abortConnection()
                            lNewFTPServices.destroyConnection()
                            mDialog = AlertDialog.Builder(this@MainActivity)
                                .setTitle("Error") // TODO string
                                .setMessage(
                                    ("Connection has failed..." + "\nMessage : " + iErrorEnum.name + "\nCode : " + iErrorCode)
                                ).setPositiveButton("Ok", null)
                                .setOnDismissListener(object : DialogInterface.OnDismissListener {
                                    override fun onDismiss(dialog: DialogInterface) {
                                        mIsBusy = false
                                    }
                                }).create()
                            mDialog!!.show()
                        }
                        lNewFTPServices.destroyConnection()
                    }
                })
            }
        })
    }

    private fun startFTPNavigationActivity(iServerID: Int) {
        val lIntent: Intent = Intent(this@MainActivity, NavigationActivity::class.java)
        lIntent.putExtra(NavigationActivity.Companion.KEY_DATABASE_ID, iServerID)
        lIntent.putExtra(
            NavigationActivity.Companion.KEY_DIRECTORY_PATH,
            NavigationActivity.Companion.ROOT_DIRECTORY
        )
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_NAVIGATION)
    }

    private fun startFTPConfigureServerActivity(iFTPServerId: Int) {
        val lIntent: Intent = Intent(this@MainActivity, ConfigureServerActivity::class.java)
        lIntent.putExtra(ConfigureServerActivity.Companion.KEY_DATABASE_ID, iFTPServerId)
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_CONFIGURE_SERVER)
        overridePendingTransition(R.anim.activity_fade_in_centered, R.anim.no_animation)
    }

    private fun startSettingsActivity() {
        val lIntent: Intent = Intent(this@MainActivity, SettingsActivity::class.java)
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_SETTINGS)
    }

    private fun runTests() {


        // Note : RunTests will automatically increment the database ID
        //        DataBaseTests.runTests(new TableTest1(), DataBase.getTableTest1DAO());
        //        DataBaseTests.runTests(new FTPServer(), DataBase.getFTPServerDAO());
        //        DataBaseTests.runTests(new PendingFile(), DataBase.getPendingFileDAO());

        //        for (int i = 0; i < 20; i++) {
        //            mAdapter.mItemList.add("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + i);
        //        }

        //        TextInputLayout lTextInputLayout = (TextInputLayout) findViewById(R.id.form_server_host);
        //        lTextInputLayout.setError("error");
        //        lTextInputLayout.setErrorTextColor(ColorStateList.valueOf(0xFFDE4255));
    }

    companion object {
        private val TAG: String = "MAIN ACTIVITY"
        private val ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE: Int = 1
        private val ACTIVITY_REQUEST_CODE_CONFIGURE_SERVER: Int = 2
        private val ACTIVITY_REQUEST_CODE_NAVIGATION: Int = 3
        private val ACTIVITY_REQUEST_CODE_SETTINGS: Int = 4
        private val ACTIVITY_REQUEST_CODE_INTERNET: Int = 5
    }
}