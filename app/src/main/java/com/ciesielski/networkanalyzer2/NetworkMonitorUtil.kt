@file:Suppress("DEPRECATION")

package com.ciesielski.networkanalyzer2

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfoGsm
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.LiveData
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import kotlin.concurrent.thread


enum class ConnectionType {
    Wifi, Cellular
}

class NetworkMonitorUtil(context: Context): LiveData<Int>() {

    private var mContext = context

    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    lateinit var result: ((isAvailable: Boolean, type: ConnectionType?, WiFiParameters: WifiInfo?, CellParameters: TelephonyManager?) -> Unit)

    private var threadPresence: Boolean = false

    private lateinit var currConnectionType: ConnectionType

    @Suppress("DEPRECATION")
    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val connectivityManager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (connectivityManager.activeNetwork == null) {

                // UNAVAILABLE
                result(false, null, null, null)
                postValue(0)
            }

            // Check when the connection changes
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    super.onLost(network)

                    // UNAVAILABLE
                    result(false, null, null, null)
                    postValue(0)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    when {
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {

                            // WIFI
                            currConnectionType = ConnectionType.Wifi
                            result(true, ConnectionType.Wifi, getWiFiParameters(), null)
                        }
                        else -> {
                            // CELLULAR
                            currConnectionType = ConnectionType.Cellular
                            result(true, ConnectionType.Cellular, null, getCellParameters())
                        }
                    }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val intentFilter = IntentFilter()
            intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE")
            mContext.registerReceiver(networkChangeReceiver, intentFilter)
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val connectivityManager =
                    mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } else {
            mContext.unregisterReceiver(networkChangeReceiver)
        }
    }

    @Suppress("DEPRECATION")
    private val networkChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo

            if (activeNetworkInfo != null) {
                // Get Type of Connection
                when (activeNetworkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> {

                        // WIFI
                        currConnectionType = ConnectionType.Wifi
                        result(true, ConnectionType.Wifi, getWiFiParameters(), null)
                    }
                    else -> {

                        // CELLULAR
                        currConnectionType = ConnectionType.Cellular
                        result(true, ConnectionType.Cellular, null, getCellParameters())
                    }
                }
            } else {

                // UNAVAILABLE
                result(false, null, null, null)
                postValue(0)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCellParameters() : TelephonyManager {

        val telephonyManager = mContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        currConnectionType = ConnectionType.Cellular
        if (!threadPresence) startThread()

        var ipaddr = getMobileIPAddress()
        Log.i("NETWORK_MONITOR_STATUS", "mobile ip address is $ipaddr")

        return telephonyManager
    }

    fun getMobileIPAddress(): String? {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
        } // for now eat exceptions
        return ""
    }

    private fun getWiFiParameters(): WifiInfo {
        val wifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        currConnectionType = ConnectionType.Wifi
        if (!threadPresence) startThread()
        return wifiManager.connectionInfo
    }

    @SuppressLint("MissingPermission")
    fun startThread() {
        val wifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

       // val telephonyManager = mContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        // for example value of first element
        //val cellInfoGsm = telephonyManager.allCellInfo[0] as CellInfoGsm
        //val cellSignalStrengthGsm = cellInfoGsm.cellSignalStrength
        //cellSignalStrengthGsm.dbm


        threadPresence = true
        var index = 0
        thread {
            while (true){
                Log.i("NETWORK_MONITOR_STATUS", "index: ${index++}")

                postValue(wifiManager.connectionInfo.rssi)

                /*if(currConnectionType == ConnectionType.Wifi){
                    postValue(wifiManager.connectionInfo.rssi)
                    Log.i("NETWORK_MONITOR_STATUS", "posting for WiFi")
                }
                else if(currConnectionType == ConnectionType.Cellular){
                    postValue(cellSignalStrengthGsm.dbm)
                    Log.i("NETWORK_MONITOR_STATUS", "posting for Cellular")
                }
*/
                // sleep
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }
}