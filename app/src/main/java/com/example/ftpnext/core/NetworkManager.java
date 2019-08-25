package com.example.ftpnext.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

public class NetworkManager {

    public static final String WIFI = "Wi-Fi";
    public static final String ANY = "Any";
    public static final String TAG = "NETWORK BROADCAST RECEIVER";

    private Context mContext;
    private boolean mStarted;

    public NetworkManager(Context iContext) {
        mContext = iContext;
    }

    public void startMonitoring() {
        initializeNetworkCallback();
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
                    .build();

            lConnectivityManager.registerNetworkCallback(lNetworkRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    LogManager.error(TAG, "On available");
                }

                @Override
                public void onUnavailable() {
                    super.onUnavailable();
                    LogManager.error(TAG, "On unavailable ");
                }

                @Override
                public void onLosing(Network network, int maxMsToLive) {
                    super.onLosing(network, maxMsToLive);
                    LogManager.error(TAG, "On losing " + network.toString());
                }

                // Si on lost ou on losing : Tout déconnecter
                // Des que available avec internet : tenter une reconnection

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    LogManager.error(TAG, "On lost " + network.toString());
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities);
                    LogManager.error(TAG, "On capabilities changed");
                    LogManager.info(TAG, String.valueOf(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
                    LogManager.info(TAG, String.valueOf(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)));
                    LogManager.info(TAG, String.valueOf(networkCapabilities.getLinkDownstreamBandwidthKbps()));
                    LogManager.info(TAG, String.valueOf(networkCapabilities.getLinkUpstreamBandwidthKbps()));
//                    LogManager.info(TAG, networkCapabilities.toString());
                }
            });
        }
    }
}
