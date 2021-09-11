package com.ciesielski.networkanalyzer2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries


class MainActivity : AppCompatActivity() {

    private val networkMonitor = NetworkMonitorUtil(this)

    private lateinit var series: LineGraphSeries<DataPoint>

    private var currentX: Double = 0.0

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layoutDisconnected = findViewById<LinearLayout>(R.id.layoutDisconnected)
        val layoutCellular = findViewById<LinearLayout>(R.id.layoutCellularConnected)
        val layoutWiFi = findViewById<LinearLayout>(R.id.layoutWiFiConnected)

        checkPermissions()
        networkMonitor.result = { isAvailable, type, WiFiParameters, CellParameters ->
            runOnUiThread {
                when (isAvailable) {
                    true -> {
                        when (type) {
                            ConnectionType.Wifi -> {
                                Log.i("NETWORK_MONITOR_STATUS", "Wifi Connection")
                                layoutDisconnected.visibility = View.GONE
                                layoutCellular.visibility = View.GONE
                                layoutWiFi.visibility = View.VISIBLE

                                val ssidTextView = findViewById<TextView>(R.id.WiFiSSIDText)
                                val ipv4TextView = findViewById<TextView>(R.id.WiFiIPv4Text)
                                val macTextView = findViewById<TextView>(R.id.WiFiMACAddressText)
                                val supportedBandwidth =
                                    findViewById<TextView>(R.id.WiFi5GSupportText)
                                val linkSpeedTextView =
                                    findViewById<TextView>(R.id.WiFiLinkSpeedText)
                                val freq = findViewById<TextView>(R.id.WiFiFrequency)

                                val ip: Int = WiFiParameters!!.ipAddress
                                val ipAddress: String = ipToString(ip)

                                val connectivityButton = findViewById<Button>(R.id.WiFiPingButton)

                                ssidTextView.text = "SSID: ${WiFiParameters.ssid}"
                                ipv4TextView.text = "IPv4 Address: $ipAddress"
                                macTextView.text = "MAC Address: ${WiFiParameters.macAddress}"
                                /*supportedBandwidth.text = "WiFi Standard: ${
                                    wifiStandard(
                                            WiFiParameters.wifiStandard
                                    )
                                }"*/
                                linkSpeedTextView.text =
                                    "Current Link Speed: ${WiFiParameters.linkSpeed} Mb/s"
                                freq.text = "Frequency: ${WiFiParameters.frequency}"

                                //GRAPH
                                val graph = findViewById<GraphView>(R.id.WiFigraph)

                                // data
                                currentX = 0.0
                                var values = arrayOf(DataPoint(0.0, 0.0))
                                if (this::series.isInitialized) {
                                    series.resetData(values)
                                    graph.onDataChanged(true, true)
                                } else series = LineGraphSeries()

                                graph.addSeries(series)

                                // customize graph
                                val viewport = graph.viewport
                                viewport.isYAxisBoundsManual = true
                                viewport.isXAxisBoundsManual = true
                                viewport.setMinY(-100.0)
                                viewport.setMaxY(-20.0)
                                viewport.setMinX(0.0)
                                viewport.isScrollable = true
                                viewport.isScalable = true

                                //Observe signal strength
                                networkMonitor.observe(this, Observer { signal ->

                                    if (signal != -127) {
                                        addEntry(signal)
                                    }
                                })

                                connectivityButton.setOnClickListener {
                                    switchActivities()
                                }

                            }
                            ConnectionType.Cellular -> {
                                Log.i("NETWORK_MONITOR_STATUS", "Cellular Connection")
                                layoutDisconnected.visibility = View.GONE
                                layoutCellular.visibility = View.VISIBLE
                                layoutWiFi.visibility = View.GONE

                                val ISPname = findViewById<TextView>(R.id.CellISPname)
                                val standard = findViewById<TextView>(R.id.CellStandard)
                                val roaming = findViewById<TextView>(R.id.CellRoaming)
                                val activity = findViewById<TextView>(R.id.CellDataActivity)
                                val concurentTransfer =
                                    findViewById<TextView>(R.id.CellConcurentVoice_Data)

                                ISPname.text = "Operator: ${CellParameters!!.networkOperatorName}"
                                standard.text =
                                    "Network: ${dataNetworkType(CellParameters.dataNetworkType)}"
                                roaming.text =
                                    "Roaming: ${roaming(CellParameters.isNetworkRoaming)}"
                                activity.text = dataActivity(CellParameters.dataActivity)
                                concurentTransfer.text = "Concurrent Voice And Data: ${
                                    concurentTransmission(
                                        CellParameters.isConcurrentVoiceAndDataSupported
                                    )
                                }"
                            }
                            else -> {
                                layoutDisconnected.visibility = View.VISIBLE
                                layoutCellular.visibility = View.GONE
                                layoutWiFi.visibility = View.GONE
                            }
                        }
                    }
                    false -> {
                        Log.i("NETWORK_MONITOR_STATUS", "No Connection")
                        layoutDisconnected.visibility = View.VISIBLE
                        layoutCellular.visibility = View.GONE
                        layoutWiFi.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun switchActivities() {
        val switchActivityIntent = Intent(this, PingUtil::class.java)
        startActivity(switchActivityIntent)
    }

    private fun dataActivity(dataActivity: Int): CharSequence? {
        return when(dataActivity){
            1 -> "Activity: Receiving IP PPP traffic"
            2 -> "Activity: Sending IP PPP traffic"
            3 -> "Activity: Both sending and receiving IP PPP traffic"
            4 -> "Activity: Data connection is active, but physical link is down"
            else -> "Activity: No traffic"
        }
    }

    private fun roaming(networkRoaming: Boolean): CharSequence? {
        return if(networkRoaming) "In use"
        else "Not in use"
    }

    private fun dataNetworkType(dataNetworkType: Int): CharSequence? {
        return when(dataNetworkType){
            1 -> "GPRS"
            2 -> "EDGE"
            3 -> "UMTS"
            4 -> "CDMA (IS95A or IS95B)"
            5 -> "EVDO revision 0"
            6 -> " EVDO revision A"
            7 -> "1xRTT"
            8 -> "HSDPA"
            9 -> "HSUPA"
            10 -> "HSPA"
            11 -> "IDEN"
            12 -> "EVDO revision B"
            13 -> "LTE"
            14 -> "eHRPD"
            15 -> "HSPA+"
            16 -> "GSM"
            17 -> "TD_SCDMA"
            18 -> "IWLAN"
            20 -> "NR 5G (New Radio)"
            else -> "Unknown"
        }
    }

    private fun concurentTransmission(concurrentVoiceAndDataSupported: Boolean): CharSequence? {
        return if(concurrentVoiceAndDataSupported) "Supported!"
        else "Not Supported!"
    }

    fun ipToString(i: Int): String {
        return (i and 0xFF).toString() + "." +
                (i shr 8 and 0xFF) + "." +
                (i shr 16 and 0xFF) + "." +
                (i shr 24 and 0xFF)
    }

    fun wifiStandard(standard: Int): String {
        return when (standard) {
            1 -> "802.11a/b/g"
            4 -> "802.11n"
            5 -> "802.11ac"
            6 -> "802.11ax"
            7 -> "802.11ad"
            else -> "Unknown"
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        networkMonitor.register()
    }

    fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
            return
        }
    }

    override fun onStop() {
        super.onStop()
        networkMonitor.unregister()
    }

    //SIGNAL STRENGTH CHART
    private fun addEntry(signal: Int) {
        // here, we choose to display max 50 points on the viewport and we scroll to end
        Log.i("NETWORK_MONITOR_STATUS", "Adding $signal to series..")
        series.appendData(DataPoint(currentX++, signal.toDouble()), true, 50)
    }
}