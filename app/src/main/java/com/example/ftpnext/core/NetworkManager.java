package com.example.ftpnext.core;

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
    public static final String TAG = "NETWORK BROADCAST RECEIVER";

    private Context mContext;
    private boolean mStarted;

    private List<OnNetworkAvailable> mOnNetworkAvailableList;
    private List<OnNetworkLost> mOnNetworkLostList;

    private Network mAvailableNetwork;
    private boolean mAvailableNetworkFired;

    //TODO : Airplane mode

    public NetworkManager(Context iContext) {
        mContext = iContext;

        mOnNetworkAvailableList = new ArrayList<>();
        mOnNetworkLostList = new ArrayList<>();
    }

    public void startMonitoring() {
        initializeNetworkCallback();
    }

    public void subscribeNetworkAvailable(OnNetworkAvailable iOnNetworkAvailable) {
        mOnNetworkAvailableList.add(iOnNetworkAvailable);
    }

    public void subscribeOnNetworkLost(OnNetworkLost iOnNetworkLost) {
        mOnNetworkLostList.add(iOnNetworkLost);
    }

    public void unsubscribeOnNetworkAvailable(OnNetworkAvailable iOnNetworkAvailable) {
        mOnNetworkAvailableList.remove(iOnNetworkAvailable);
    }

    public void unsubscribeOnNetworkLost(OnNetworkLost iOnNetworkLost) {
        mOnNetworkLostList.remove(iOnNetworkLost);
    }

    private void initializeNetworkCallback() {
        if (mStarted)
            return;
        mStarted = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final ConnectivityManager lConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkRequest lNetworkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .build();

            lConnectivityManager.registerNetworkCallback(lNetworkRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network iNetwork) {
                    super.onAvailable(iNetwork);
                    mAvailableNetwork = iNetwork;
                    mAvailableNetworkFired = false;
                    LogManager.error(TAG, "On available");
                }

                @Override
                public void onLost(Network iNetwork) {
                    super.onLost(iNetwork);

                    if (iNetwork == mAvailableNetwork)
                        fireNetworkLost();
                    LogManager.error(TAG, "On lost " + iNetwork.toString());
                }

                @Override
                public void onCapabilitiesChanged(Network iNetwork, NetworkCapabilities iNetworkCapabilities) {
                    super.onCapabilitiesChanged(iNetwork, iNetworkCapabilities);
                    LogManager.debug(TAG, "On capabilities changed");
                    LogManager.debug(TAG, String.valueOf(iNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
                    LogManager.debug(TAG, String.valueOf(iNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)));
                    LogManager.debug(TAG, String.valueOf(iNetworkCapabilities.getLinkDownstreamBandwidthKbps()));
                    LogManager.debug(TAG, String.valueOf(iNetworkCapabilities.getLinkUpstreamBandwidthKbps()));

                    if (mAvailableNetworkFired)
                        return;

                    if (mAvailableNetwork == iNetwork && iNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        fireNetworkAvailable(iNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));

                }
            });
        }
    }

    private void fireNetworkAvailable(boolean iIsWifi) {
        LogManager.info(TAG, "Fire new network available. Wifi : " + iIsWifi);
        for (OnNetworkAvailable lCallback : mOnNetworkAvailableList)
            lCallback.onNetworkAvailable(iIsWifi);
    }

    private void fireNetworkLost() {
        LogManager.info(TAG, "Fire network lost.");
        for (OnNetworkLost lCallback : mOnNetworkLostList)
            lCallback.onUsedNetworkLost();
    }

    public interface OnNetworkAvailable {
        void onNetworkAvailable(boolean iIsWifi);
    }

    public interface OnNetworkLost {
        void onUsedNetworkLost();
    }
}