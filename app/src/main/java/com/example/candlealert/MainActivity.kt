package com.example.candlealert

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("prefs", 0) }
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        buildUi()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
        }
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(box)
        root.addView(scroll, ViewGroup.LayoutParams(-1, 0).apply { height = 0; weight = 1f })

        status = TextView(this).apply { textSize = 22f }
        box.addView(status)

        val on = Switch(this).apply {
            text = "Candle Alert"
            isChecked = prefs.getBoolean("enabled", true)
        }
        box.addView(on)

        box.addView(TextView(this).apply { text = "Timeframe"; textSize = 18f })
        val values = listOf(1, 3, 5, 15, 30, 60, 240)
        val tf = Spinner(this)
        tf.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            values.map { v -> if (v < 60) v.toString() + "M" else (v / 60).toString() + "H" }
        )
        tf.setSelection(values.indexOf(prefs.getInt("tf", 5)))
        box.addView(tf)

        box.addView(TextView(this).apply { text = "Market"; textSize = 18f })
        val market = Spinner(this)
        market.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Forex", "Crypto (24/7)", "Forex + Crypto")
        )
        market.setSelection(prefs.getInt("market", 0))
        box.addView(market)

        box.addView(TextView(this).apply { text = "Alert timing"; textSize = 18f })
        val mode = Spinner(this)
        mode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Before candle close", "At candle close", "After candle close")
        )
        mode.setSelection(prefs.getInt("mode", 0))
        box.addView(mode)

        val secs = EditText(this).apply {
            hint = "Offset in seconds (e.g. 30, 120, 300)"
            inputType = 2
            setText(prefs.getInt("offset", 120).toString())
        }
        box.addView(secs)
        box.addView(TextView(this).apply {
            text = "30 = 30 seconds, 120 = 2 minutes, 300 = 5 minutes. At Close ignores offset."
        })

        box.addView(TextView(this).apply {
            text = "Trading sessions (select any number)"
            textSize = 18f
        })
        val sessions = listOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        val checks = mutableMapOf<String, CheckBox>()
        sessions.forEach { s ->
            val cb = CheckBox(this).apply {
                text = s
                isChecked = prefs.getStringSet("sessions", sessions.toSet())!!.contains(s)
            }
            checks[s] = cb
            box.addView(cb)
        }

        box.addView(TextView(this).apply {
            text = "Quiet hours (phone local time)"
            textSize = 18f
        })
        val quiet = EditText(this).apply {
            hint = "e.g. 00:00-07:30"
            setText(prefs.getString("quiet", "00:00-07:30"))
            inputType = 1
        }
        box.addView(quiet)

        val save = Button(this).apply { text = "SAVE & SCHEDULE" }
        box.addView(save)

        val exact = Button(this).apply { text = "Open Exact Alarm permission" }
        box.addView(exact)
        exact.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

        save.setOnClickListener {
            prefs.edit()
                .putBoolean("enabled", on.isChecked)
                .putInt("tf", values[tf.selectedItemPosition])
                .putInt("market", market.selectedItemPosition)
                .putInt("mode", mode.selectedItemPosition)
                .putInt("offset", secs.text.toString().toIntOrNull()?.coerceIn(0, 86400) ?: 120)
                .putStringSet("sessions", checks.filter { it.value.isChecked }.keys)
                .putString("quiet", quiet.text.toString())
                .apply()
            Scheduler.scheduleNext(this)
            updateStatus()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        setContentView(root)
        updateStatus()
    }

    private fun updateStatus() {
        val state = if (prefs.getBoolean("enabled", true)) "ON" else "OFF"
        status.text = state + "  •  Next alerts use " + prefs.getInt("tf", 5) + "M candles"
    }
}
