package com.vpulse.ftpnext.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class NetworkManager {

    public static final String WIFI = "Wi-Fi";
    public static final String ANY = "Any";
    public static final String TAG = "NETWORK MANAGER";

    private static NetworkManager sSingleton = null;
    private static boolean sStarted = false;

    private List<OnNetworkAvailable> mOnNetworkAvailableList;
    private List<OnNetworkLost> mOnNetworkLostList;

    private Network mAvailableNetwork;
    private NetworkCapabilities mNetworkCapabilities;
    private boolean mAvailableNetworkFired;

    private NetworkManager() {
        mOnNetworkAvailableList = new ArrayList<>();
        mOnNetworkLostList = new ArrayList<>();
    }

    public static NetworkManager getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new NetworkManager());
    }

    public void startMonitoring(Context iContext) {
        initializeNetworkCallback(iContext);
    }

    public void subscribeNetworkAvailable(OnNetworkAvailable iOnNetworkAvailable) {
        if (iOnNetworkAvailable == null) {
            LogManager.error(TAG, "Subscribe network available parameter null");
            return;
        }
        mOnNetworkAvailableList.add(iOnNetworkAvailable);
    }

    public void subscribeOnNetworkLost(OnNetworkLost iOnNetworkLost) {
        if (iOnNetworkLost == null) {
            LogManager.error(TAG, "Subscribe on network lost parameter null");
            return;
        }
        mOnNetworkLostList.add(iOnNetworkLost);
    }

    public void unsubscribeOnNetworkAvailable(OnNetworkAvailable iOnNetworkAvailable) {
        if (!mOnNetworkAvailableList.remove(iOnNetworkAvailable))
            LogManager.error(TAG, "Unsubscribe network available failed !");
    }

    public void unsubscribeOnNetworkLost(OnNetworkLost iOnNetworkLost) {
        if (!mOnNetworkLostList.remove(iOnNetworkLost))
            LogManager.error(TAG, "Unsubscribe network lost failed !");
    }

    public boolean isNetworkAvailable() {
        return mAvailableNetwork != null && mNetworkCapabilities != null &&
                mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public boolean isCurrentNetworkIsWifi() {
        return mNetworkCapabilities != null &&
                mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public Network getAvailableNetwork() {
        return mAvailableNetwork;
    }

    private void initializeNetworkCallback(Context iContext) {
        if (sStarted)
            return;
        sStarted = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final ConnectivityManager lConnectivityManager = (ConnectivityManager) iContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkRequest lNetworkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mAvailableNetwork = lConnectivityManager.getActiveNetwork();
                if (mAvailableNetwork != null) {
                    LogManager.info(TAG, "Network fetched : " + mAvailableNetwork.toString());
                    mNetworkCapabilities = lConnectivityManager.getNetworkCapabilities(mAvailableNetwork);
                }
            }

            lConnectivityManager.registerNetworkCallback(lNetworkRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network iNetwork) {
                    super.onAvailable(iNetwork);
                    LogManager.info(TAG, "On available");
                    mAvailableNetwork = iNetwork;
                    mAvailableNetworkFired = false;
                    mNetworkCapabilities = lConnectivityManager.getNetworkCapabilities(iNetwork);
                    if (mNetworkCapabilities != null && mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        fireNetworkAvailable(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), iNetwork);
                        mAvailableNetworkFired = true;
                    }
                }

                @Override
                public void onLost(Network iNetwork) {
                    super.onLost(iNetwork);
                    LogManager.info(TAG, "On lost " + iNetwork.toString());

//                    fireNetworkLost();

                    if (iNetwork.equals(mAvailableNetwork)) {
                        mAvailableNetwork = null;
                        fireNetworkLost();
                    }
                }

                @Override
                public void onCapabilitiesChanged(Network iNetwork, NetworkCapabilities iNetworkCapabilities) {
                    super.onCapabilitiesChanged(iNetwork, iNetworkCapabilities);
//                    LogManager.info(TAG, "On capabilities changed");

                    if (mAvailableNetwork == null)
                        return;

                    if (mAvailableNetwork.equals(iNetwork))
                        mNetworkCapabilities = iNetworkCapabilities;

                    if (mAvailableNetworkFired)
                        return;

                    if (mAvailableNetwork.equals(iNetwork) && iNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        fireNetworkAvailable(iNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), iNetwork);
                        mAvailableNetworkFired = true;
                    }
                }
            });
        }
    }

    private void fireNetworkAvailable(boolean iIsWifi, Network iNewNetwork) {
        LogManager.info(TAG, "Fire new network available. Wifi : " + iIsWifi);
        for (OnNetworkAvailable lCallback : mOnNetworkAvailableList)
            lCallback.onNetworkAvailable(iIsWifi, iNewNetwork);
    }

    private void fireNetworkLost() {
        LogManager.info(TAG, "Fire network lost.");
        for (OnNetworkLost lCallback : mOnNetworkLostList)
            lCallback.onNetworkLost();
    }

    public interface OnNetworkAvailable {
        void onNetworkAvailable(boolean iIsWifi, Network iNewNetwork);
    }

    public interface OnNetworkLost {
        void onNetworkLost();
    }
}