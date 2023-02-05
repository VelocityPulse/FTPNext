package com.vpulse.ftpnext.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

class NetworkManager private constructor() {
    private val mOnNetworkAvailableList: MutableList<OnNetworkAvailable?>
    private val mOnNetworkLostList: MutableList<OnNetworkLost?>
    var availableNetwork: Network? = null
        private set
    private var mNetworkCapabilities: NetworkCapabilities? = null
    private var mAvailableNetworkFired = false

    init {
        mOnNetworkAvailableList = ArrayList()
        mOnNetworkLostList = ArrayList()
    }

    fun startMonitoring(iContext: Context) {
        initializeNetworkCallback(iContext)
    }

    fun subscribeNetworkAvailable(iOnNetworkAvailable: OnNetworkAvailable?) {
        if (iOnNetworkAvailable == null) {
            LogManager.error(TAG, "Subscribe network available parameter null")
            return
        }
        mOnNetworkAvailableList.add(iOnNetworkAvailable)
    }

    fun subscribeOnNetworkLost(iOnNetworkLost: OnNetworkLost?) {
        if (iOnNetworkLost == null) {
            LogManager.error(TAG, "Subscribe on network lost parameter null")
            return
        }
        mOnNetworkLostList.add(iOnNetworkLost)
    }

    fun unsubscribeOnNetworkAvailable(iOnNetworkAvailable: OnNetworkAvailable?) {
        if (!mOnNetworkAvailableList.remove(iOnNetworkAvailable)) LogManager.error(
            TAG,
            "Unsubscribe network available failed !"
        )
    }

    fun unsubscribeOnNetworkLost(iOnNetworkLost: OnNetworkLost?) {
        if (!mOnNetworkLostList.remove(iOnNetworkLost)) LogManager.error(
            TAG,
            "Unsubscribe network lost failed !"
        )
    }

    val isNetworkAvailable: Boolean
        get() = availableNetwork != null && mNetworkCapabilities != null &&
                mNetworkCapabilities!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isCurrentNetworkIsWifi: Boolean
        get() = mNetworkCapabilities != null &&
                mNetworkCapabilities!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

    private fun initializeNetworkCallback(iContext: Context) {
        if (sStarted) return
        sStarted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val lConnectivityManager =
                iContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lNetworkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                availableNetwork = lConnectivityManager.activeNetwork
                if (availableNetwork != null) {
                    LogManager.info(TAG, "Network fetched : " + availableNetwork.toString())
                    mNetworkCapabilities = lConnectivityManager.getNetworkCapabilities(
                        availableNetwork
                    )
                }
            }
            lConnectivityManager.registerNetworkCallback(
                lNetworkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(iNetwork: Network) {
                        super.onAvailable(iNetwork)
                        LogManager.info(TAG, "On available")
                        availableNetwork = iNetwork
                        mAvailableNetworkFired = false
                        mNetworkCapabilities = lConnectivityManager.getNetworkCapabilities(iNetwork)
                        if (mNetworkCapabilities != null && mNetworkCapabilities!!.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                            )
                        ) {
                            fireNetworkAvailable(
                                mNetworkCapabilities!!.hasTransport(
                                    NetworkCapabilities.TRANSPORT_WIFI
                                ), iNetwork
                            )
                            mAvailableNetworkFired = true
                        }
                    }

                    override fun onLost(iNetwork: Network) {
                        super.onLost(iNetwork)
                        LogManager.info(TAG, "On lost $iNetwork")

//                    fireNetworkLost();
                        if (iNetwork == availableNetwork) {
                            availableNetwork = null
                            fireNetworkLost()
                        }
                    }

                    override fun onCapabilitiesChanged(
                        iNetwork: Network,
                        iNetworkCapabilities: NetworkCapabilities
                    ) {
                        super.onCapabilitiesChanged(iNetwork, iNetworkCapabilities)
                        //                    LogManager.info(TAG, "On capabilities changed");
                        if (availableNetwork == null) return
                        if (availableNetwork == iNetwork) mNetworkCapabilities =
                            iNetworkCapabilities
                        if (mAvailableNetworkFired) return
                        if (availableNetwork == iNetwork && iNetworkCapabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                            )
                        ) {
                            fireNetworkAvailable(
                                iNetworkCapabilities.hasTransport(
                                    NetworkCapabilities.TRANSPORT_WIFI
                                ), iNetwork
                            )
                            mAvailableNetworkFired = true
                        }
                    }
                })
        }
    }

    private fun fireNetworkAvailable(iIsWifi: Boolean, iNewNetwork: Network) {
        LogManager.info(TAG, "Fire new network available. Wifi : $iIsWifi")
        for (lCallback in mOnNetworkAvailableList) lCallback!!.onNetworkAvailable(
            iIsWifi,
            iNewNetwork
        )
    }

    private fun fireNetworkLost() {
        LogManager.info(TAG, "Fire network lost.")
        for (lCallback in mOnNetworkLostList) lCallback!!.onNetworkLost()
    }

    interface OnNetworkAvailable {
        fun onNetworkAvailable(iIsWifi: Boolean, iNewNetwork: Network?)
    }

    interface OnNetworkLost {
        fun onNetworkLost()
    }

    companion object {
        const val WIFI = "Wi-Fi"
        const val ANY = "Any"
        const val TAG = "NETWORK MANAGER"
        private var sSingleton: NetworkManager? = null
        private var sStarted = false
        val instance: NetworkManager?
            get() = if (sSingleton != null) sSingleton else NetworkManager().also {
                sSingleton = it
            }
    }
}