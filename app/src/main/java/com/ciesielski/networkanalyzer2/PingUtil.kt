package com.ciesielski.networkanalyzer2

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.EditText
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.ping_main.*
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.roundToLong

class PingUtil : AppCompatActivity() {
    private val pingRegex = "(?:\\[(?<ts>[0-9.]+)] )?(?<size>[0-9]+) bytes from (?<ip>[0-9.]+): icmp_seq=(?<seq>[0-9]+) ttl=(?<ttl>[0-9]+)(?: time=(?<rtt>[0-9.]+) (?<rttmetric>\\w+))?"
    private val outerClass = WeakReference<PingUtil>(this)
    private var mHandlerThread = MyHandler(outerClass)

    private var mThread: Thread? = null
    private var isThreadRunning = false
    private var errorMessage = ""

    val queue: Queue<String> = ArrayDeque<String>()

    companion object {
        private const val START = 100
        private const val STOP = 101
        private const val PING = 102
    }

    object Params {
        var server = "8.8.8.8"
        var size = 16
        var interval = 1.0
        var count = 5
    }

    private var params = Params

    private fun showSettingsPopup() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.alert_dialog_settings, null)

        val serverText  = dialogLayout.findViewById<EditText>(R.id.addressText)
        val sizeText  = dialogLayout.findViewById<EditText>(R.id.sizeText)
        val intervalText  = dialogLayout.findViewById<EditText>(R.id.intervalText)
        val countText  = dialogLayout.findViewById<EditText>(R.id.countText)

        serverText.setText(params.server)
        sizeText.setText(params.size.toString())
        intervalText.setText(params.interval.toString())
        countText.setText(params.count.toString())

        builder.setView(dialogLayout)
        builder.setPositiveButton("OK") {
                _, _ ->
            params.server = serverText.text.toString()
            params.size = sizeText.text.toString().toInt()
            params.interval = intervalText.text.toString().toDouble()
            params.count = countText.text.toString().toInt()

        }
        builder.show()
    }

    private class MyHandler(private val outerClass: WeakReference<PingUtil>) : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when {
                msg.what == PING -> outerClass.get()?.updateText()
                msg.what == STOP -> outerClass.get()?.togglePing(false)
                msg.what == START -> outerClass.get()?.togglePing(true)
            }
        }
    }

    private fun parsePingString(s: String): Matcher {
        val re = Pattern.compile(
            pingRegex,
            Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
        return re.matcher(s)

    }

    private fun updateText() {
        if (queue.size == 0) {
            return
        }

        val res = parsePingString(queue.remove())

        if (!res.find()) {
            return
        }
        val row = TableRow(this)
        row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT)

        val rowView = layoutInflater.inflate(R.layout.result_row, tableLayout, false)
        val tvTimestamp = rowView.findViewById(R.id.tvTimestamp) as? TextView
        val tvSize = rowView.findViewById(R.id.tvSize) as? TextView
        val tvTarget = rowView.findViewById(R.id.tvTarget) as? TextView
        val tvSeqN = rowView.findViewById(R.id.tvSeqN) as? TextView
        val tvTtl = rowView.findViewById(R.id.tvTtl) as? TextView
        val tvRtt = rowView.findViewById(R.id.tvRtt) as? TextView

        var dateTimeStr = res.group(1)
        try {
            val sdf = SimpleDateFormat(getString(R.string.datetime_format), Locale.US)
            val netDate = Date((dateTimeStr.toDouble()*1000).roundToLong())
            dateTimeStr = sdf.format(netDate)
        } finally {
            tvTimestamp?.text = dateTimeStr
        }

        tvSize?.text = res.group(2)
        tvTarget?.text =res.group(3)
        tvSeqN?.text = res.group(4)
        tvTtl?.text = res.group(5)

        val rttConcat = if (res.group(6) != null)
            resources.getString(R.string.format_rtt, res.group(6), res.group(7))
        else resources.getString(R.string.value_na)

        tvRtt?.text = rttConcat
        tableLayout.addView(rowView, 1)
    }

    internal inner class PingProcess : Runnable {
        override fun run() {
            val cmd = mutableListOf("ping", "-D")
            if (params.size > 0) {
                cmd.addAll(arrayOf("-s", params.size.toString()))
            }
            if (params.interval > 0) {
                cmd.addAll(arrayOf("-i", params.interval.toString()))
            }
            if (params.count > 0) {
                cmd.addAll(arrayOf("-c", params.count.toString()))
            }
            cmd.add(params.server)

            val builder = ProcessBuilder()
            builder.command(cmd)

            val process = builder.start()
            val stdInput = process.inputStream.bufferedReader()

            val messageStart = Message()
            messageStart.what = START
            mHandlerThread.sendMessage(messageStart)

            while (isThreadRunning) {
                val currentStr = try {
                    stdInput.readLine()
                } catch (e: IllegalStateException) {
                    break
                } ?: break

                pingRegex.toRegex().find(currentStr) ?: continue

                queue.add(currentStr)

                val messagePing = Message()
                messagePing.what = PING
                mHandlerThread.sendMessage(messagePing)
            }
            if (isThreadRunning) {
                errorMessage = try {
                    process.errorStream.bufferedReader().readLine()
                } catch (e: IllegalStateException) {
                    "IllegalStateException"
                } catch (e: NullPointerException) {
                    "NullPointerException"
                }
            }

            val messageStop = Message()
            messageStop.what = STOP
            mHandlerThread.sendMessage(messageStop)

            process.destroy()
        }
    }

    private fun togglePing(on: Boolean) {
        if (errorMessage != "") {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }

        if (on) {
            pingButton.text = resources.getString(R.string.btn_stop)
        } else {
            mThread = null
            pingButton.text = resources.getString(R.string.btn_start)
        }
        errorMessage = ""
        pingButton.isClickable = true
    }


    private fun triggerTogglePing() {
        pingButton.isClickable = false

        val doEnable = mThread == null
        isThreadRunning = doEnable

        if (doEnable) {
            mThread = Thread(PingProcess())
            mThread?.start()
        }
    }

    private fun initTableView() {
        tableLayout.removeAllViews()
        val headerRow = layoutInflater.inflate(R.layout.result_row, tableLayout, false)
        tableLayout.addView(headerRow)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ping_main)
        supportActionBar?.hide()


        initTableView()

        clearButton.setOnClickListener {
            initTableView()
        }

        pingButton.setOnClickListener {
            triggerTogglePing()
        }

        settingsButton.setOnClickListener {
            showSettingsPopup()
        }

        goBackButton.setOnClickListener {
            finish()
        }
    }
}