package com.example.candlealert

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("prefs", 0) }
    private val bg get() = Color.parseColor(prefs.getString("theme_bg", "#E8F7FF") ?: "#E8F7FF")
    private val card get() = Color.parseColor(prefs.getString("theme_card", "#F7FCFF") ?: "#F7FCFF")
    private val card2 get() = Color.parseColor(prefs.getString("theme_card2", "#EAF8FF") ?: "#EAF8FF")
    private val accent get() = Color.parseColor(prefs.getString("theme_accent", "#238FF5") ?: "#238FF5")
    private val cyan = Color.rgb(80, 207, 220)
    private val green = Color.rgb(30, 190, 153)
    private val red = Color.rgb(230, 88, 103)
    private val textColor = Color.rgb(18, 48, 74)
    private val muted = Color.rgb(105, 132, 151)
    private val line get() = Color.parseColor(prefs.getString("theme_line", "#CDEAF8") ?: "#CDEAF8")

    private val handler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var nextDetailsView: TextView? = null
    private var ticker: Runnable? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        showHome()
        Scheduler.scheduleNext(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }
    }

    override fun onDestroy() {
        ticker?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun base(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(18, 14, 18, 8)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(18, 14 + bars.top, 18, 8 + bars.bottom)
            window.statusBarColor = bg
            window.navigationBarColor = bg
            insets
        }
        return root
    }

    private fun label(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
    }

    private fun rounded(c: Int, r: Float = 20f) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(c)
            cornerRadius = r
            setStroke(1, line)
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 16, 18, 16)
        background = rounded(card, 20f)
        elevation = 2f
    }

    private fun button(s: String, selected: Boolean = false) = TextView(this).apply {
        text = s
        textSize = 14f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setTextColor(if (selected) Color.WHITE else textColor)
        gravity = Gravity.CENTER
        setPadding(12, 8, 12, 8)
        background = rounded(if (selected) accent else card2, 28f)
    }

    private fun showHome() {
        val root = base()

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_popup_reminder)
            setColorFilter(accent)
            setPadding(8, 8, 8, 8)
        }, LinearLayout.LayoutParams(46, 46))
        head.addView(label("Candle", 24f, textColor))
        head.addView(label("Alert", 24f, accent))
        val gear = label("⚙", 25f, textColor).apply { gravity = Gravity.CENTER }
        head.addView(gear, LinearLayout.LayoutParams(0, 52).apply { weight = 1f })
        gear.setOnClickListener { showSettings() }
        root.addView(head)

        val clockCard = card().apply {
            setPadding(8, 8, 8, 14)
            background = rounded(card, 26f)
        }
        val clock = AnalogClockView(this, accent, textColor, muted) {
            prefs.getInt("tf", 60).coerceAtLeast(1)
        }
        clockCard.addView(clock, LinearLayout.LayoutParams(-1, 340))
        clockCard.addView(label("LOCAL TIME  •  CANDLE PROGRESS", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 2)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        })
        root.addView(clockCard, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 10, 0, 10)
        })

        val status = card()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label("●", 22f, if (prefs.getBoolean("enabled", true)) green else red))
        row.addView(
            label(
                if (prefs.getBoolean("enabled", true)) "  ALERT ACTIVE" else "  ALERTS PAUSED",
                16f,
                textColor
            ),
            LinearLayout.LayoutParams(0, 48).apply { weight = 1f }
        )
        val sw = Switch(this).apply { isChecked = prefs.getBoolean("enabled", true) }
        row.addView(sw)
        status.addView(row)
        status.addView(
            label(
                if (sw.isChecked) "Blue ring completes one full turn every selected timeframe."
                else "Turn on to receive alerts.",
                12f,
                muted
            )
        )
        sw.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("enabled", v).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, 8)
        })

        val next = card()
        next.addView(label("NEXT ALERT", 11f, muted))
        countdownView = label("Calculating…", 30f, accent).apply {
            setPadding(0, 5, 0, 0)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        next.addView(countdownView)
        nextDetailsView = label("Checking schedule…", 12f, muted).apply {
            setPadding(0, 2, 0, 0)
        }
        next.addView(nextDetailsView)

        val tf = prefs.getInt("tf", 5)
        val tfText = if (tf >= 60) (tf / 60).toString() + "H" else tf.toString() + "M"
        val market = when (prefs.getInt("market", 0)) {
            0 -> "Forex"
            1 -> "Crypto"
            else -> "Both"
        }
        next.addView(
            label(
                "SYMBOL  ${(prefs.getString("symbol","XAUUSD") ?: "XAUUSD")}    •    TIMEFRAME  $tfText    •    $market    •    " + timingSummary(),
                12f,
                textColor
            ).apply { setPadding(0, 10, 0, 0) }
        )
        root.addView(next, LinearLayout.LayoutParams(-1, 0).apply {
            weight = 1f
            setMargins(0, 0, 0, 8)
        })

        addBottom(root, "home")
        setContentView(root)
        updateCountdown()
    }

    private fun updateCountdown() {
        ticker?.let { handler.removeCallbacks(it) }
        val run = object : Runnable {
            override fun run() {
                if (isFinishing) return
                val trigger = Scheduler.nextTrigger(this@MainActivity)
                val now = System.currentTimeMillis() / 1000
                if (trigger == null) {
                    countdownView?.text =
                        if (!prefs.getBoolean("enabled", true)) "PAUSED" else "No alert scheduled"
                    nextDetailsView?.text = Scheduler.nextStatus(this@MainActivity)
                } else {
                    val left = (trigger - now).coerceAtLeast(0)
                    countdownView?.text = formatCountdown(left)
                    nextDetailsView?.text = Scheduler.nextStatus(this@MainActivity)
                }
                handler.postDelayed(this, 1000)
            }
        }
        ticker = run
        handler.post(run)
    }

    private fun formatCountdown(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, sec)
        else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
    }

    private fun timingSummary(): String {
        val mode = prefs.getInt("mode", 1)
        val off = prefs.getInt("offset", 0)
        if (mode == 1 || off == 0) return "At close"
        return (if (mode == 0) "Before " else "After ") + formatOffset(off)
    }

    private fun formatOffset(s: Int): String = when (s) {
        10 -> "10s"; 30 -> "30s"; 45 -> "45s"; 60 -> "1m"; 120 -> "2m"; 180 -> "3m"; 300 -> "5m"
        else -> s.toString() + "s"
    }

    private fun addBottom(root: LinearLayout, active: String) {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 2)
        }
        val icons = mapOf(
            "Home" to android.R.drawable.ic_menu_view,
            "Journal" to android.R.drawable.ic_menu_edit,
            "Settings" to android.R.drawable.ic_menu_preferences
        )
        listOf("Home", "Journal", "Settings").forEach { n ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(if (active == n.lowercase()) Color.WHITE else card2, 18f)
                setPadding(4, 4, 4, 4)
            }
            val icon = ImageView(this).apply {
                setImageResource(icons[n]!!)
                setColorFilter(if (active == n.lowercase()) accent else muted)
                setPadding(5, 4, 5, 1)
            }
            item.addView(icon, LinearLayout.LayoutParams(42, 34))
            item.addView(label(n, 10f, if (active == n.lowercase()) accent else muted).apply {
                gravity = Gravity.CENTER
            })
            item.setOnClickListener {
                when (n) {
                    "Home" -> showHome()
                    "Journal" -> showJournal()
                    "Settings" -> showSettings()
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, 70).apply {
                weight = 1f
                setMargins(4, 0, 4, 0)
            })
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, 76))
    }

    private fun showJournal() {
        val root = base()
        root.addView(label("Journal", 30f, textColor))
        root.addView(label("Your CandleAlert activity", 15f, muted))
        val b = card()
        b.addView(label("Trading journal", 20f, textColor))
        b.addView(label("Trade notes and performance tracking can be added here.", 14f, muted).apply {
            setPadding(0, 10, 0, 0)
        })
        root.addView(b, LinearLayout.LayoutParams(-1, 0).apply {
            weight = 1f
            setMargins(0, 16, 0, 16)
        })
        addBottom(root, "journal")
        setContentView(root)
    }

    private fun showSettings() {
        val root = base()
        root.addView(label("Settings", 30f, textColor))
        root.addView(label("Customize your alerts", 15f, muted))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(0, 8, 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val options = listOf(
            "Symbol" to "Choose the instrument",
            "Timeframe" to "Choose candle duration",
            "Open Market" to "Set your broker candle start time",
            "Alert Timing" to "Before, at, or after close",
            "Market & Sessions" to "Forex, crypto and sessions",
            "Sleep Hours" to "Quiet period for notifications",
            "Theme" to "Crystal Water appearance and colors",
            "Open App on Notification" to "Open your selected trading app",
            "Exact Alarm Permission" to "Allow precise background alerts"
        )

        options.forEach { (name, subtitle) ->
            val c = card().apply { setPadding(18, 12, 18, 12) }
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            texts.addView(label(name, 18f, textColor))
            texts.addView(label(subtitle, 13f, muted).apply { setPadding(0, 4, 0, 0) })
            r.addView(texts, LinearLayout.LayoutParams(0, 68).apply { weight = 1f })
            r.addView(label("›", 30f, accent))
            c.addView(r)
            c.setOnClickListener {
                when (name) {
                    "Symbol" -> chooseSymbol()
                    "Timeframe" -> chooseTf()
                    "Open Market" -> chooseOpenMarket()
                    "Alert Timing" -> chooseTiming()
                    "Market & Sessions" -> chooseMarket()
                    "Sleep Hours" -> editQuiet()
                    "Theme" -> showThemeSettings()
                    "Open App on Notification" -> chooseNotificationApp()
                    "Exact Alarm Permission" -> if (android.os.Build.VERSION.SDK_INT >= 31) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        })
                    }
                }
            }
            content.addView(c, LinearLayout.LayoutParams(-1, 88).apply {
                setMargins(0, 6, 0, 6)
            })
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun wheelDialog(
        title: String,
        values: List<String>,
        selected: Int,
        onSelected: (Int) -> Unit
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22, 8, 22, 8)
            background = rounded(card, 28f)
        }
        val picker = NumberPicker(this).apply {
            minValue = 0
            maxValue = values.lastIndex
            displayedValues = values.toTypedArray()
            value = selected.coerceIn(0, values.lastIndex)
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        box.addView(picker, LinearLayout.LayoutParams(-1, 220))
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ -> onSelected(picker.value) }
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(rounded(card, 28f))
    }

    private fun chooseSymbol() {
        val values = listOf(
            "XAUUSD  •  Gold", "EURUSD", "GBPUSD", "USDJPY",
            "XAGUSD  •  Silver", "USOIL  •  Oil", "BTCUSD", "ETHUSD", "Custom Symbol"
        )
        val current = prefs.getString("symbol", "XAUUSD") ?: "XAUUSD"
        val idx = listOf("XAUUSD","EURUSD","GBPUSD","USDJPY","XAGUSD","USOIL","BTCUSD","ETHUSD","CUSTOM")
            .indexOf(current).coerceAtLeast(0)
        wheelDialog("Symbol", values, idx) { i ->
            if (i == 8) {
                val e = EditText(this).apply {
                    setText(if (current == "XAUUSD") "" else current)
                    hint = "e.g. NAS100"
                    textSize = 18f
                }
                AlertDialog.Builder(this).setTitle("Custom Symbol").setView(e)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        val v = e.text.toString().trim().uppercase(Locale.getDefault())
                        if (v.isNotEmpty()) prefs.edit().putString("symbol", v).apply()
                        showSettings()
                    }.show()
            } else {
                val syms = listOf("XAUUSD","EURUSD","GBPUSD","USDJPY","XAGUSD","USOIL","BTCUSD","ETHUSD")
                prefs.edit().putString("symbol", syms[i]).apply()
                showSettings()
            }
        }
    }

    private fun chooseOpenMarket() {
        val current = prefs.getString("open_market", "00:00") ?: "00:00"
        val parts = current.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            background = rounded(card, 28f)
        }
        val hp = NumberPicker(this).apply { minValue=0; maxValue=23; value=h; wrapSelectorWheel=true; descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS }
        val mp = NumberPicker(this).apply { minValue=0; maxValue=59; value=m; wrapSelectorWheel=true; descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS }
        box.addView(hp, LinearLayout.LayoutParams(0,220).apply{weight=1f})
        box.addView(label(":",28f,textColor).apply{gravity=Gravity.CENTER}, LinearLayout.LayoutParams(36,220))
        box.addView(mp, LinearLayout.LayoutParams(0,220).apply{weight=1f})
        AlertDialog.Builder(this).setTitle("Open Market")
            .setMessage("Candle alignment starts from this broker open time, using your phone local time.")
            .setView(box).setNegativeButton("Cancel",null)
            .setPositiveButton("Done"){_,_->
                prefs.edit().putString("open_market", String.format(Locale.getDefault(), "%02d:%02d", hp.value, mp.value)).apply()
                Scheduler.scheduleNext(this); showSettings()
            }.show()
    }

    private fun showThemeSettings() {
        val root = base()
        root.addView(label("Crystal Water", 30f, textColor))
        root.addView(label("Customize the glass, background and accent.", 15f, muted).apply{setPadding(0,4,0,14)})
        val presets = listOf(
            Triple("Crystal Water", "#E8F7FF", "#F7FCFF"),
            Triple("Arctic Glass", "#EEF6FF", "#FFFFFF"),
            Triple("Aqua Mist", "#E6FAF8", "#F5FFFF"),
            Triple("Midnight Glass", "#0E1B2A", "#14283A")
        )
        presets.forEach { (name,bgHex,cardHex) ->
            val c=card()
            val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
            r.addView(label(name,18f,textColor),LinearLayout.LayoutParams(0,58).apply{weight=1f})
            r.addView(label("●",24f,Color.parseColor(if(name=="Midnight Glass")"#238FF5" else "#55CFE0")))
            c.addView(r)
            c.setOnClickListener {
                val dark=name=="Midnight Glass"
                prefs.edit().putString("theme_bg",bgHex).putString("theme_card",cardHex)
                    .putString("theme_card2",if(dark)"#18364A" else "#EAF8FF")
                    .putString("theme_accent",if(dark)"#58B7FF" else "#238FF5")
                    .putString("theme_line",if(dark)"#31516A" else "#CDEAF8").apply()
                showSettings()
            }
            root.addView(c,LinearLayout.LayoutParams(-1,76).apply{setMargins(0,5,0,5)})
        }
        val accents=listOf("Ocean Blue" to "#238FF5","Crystal Cyan" to "#42C9E8","Lagoon" to "#17BFA3","Violet Ice" to "#7C7AE8")
        root.addView(label("ACCENT COLOR",12f,muted).apply{setPadding(4,14,0,4)})
        accents.forEach{(n,hx)->
            val c=card(); val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
            r.addView(label(n,17f,textColor),LinearLayout.LayoutParams(0,58).apply{weight=1f})
            r.addView(label("●",24f,Color.parseColor(hx))); c.addView(r)
            c.setOnClickListener{prefs.edit().putString("theme_accent",hx).apply();showSettings()}
            root.addView(c,LinearLayout.LayoutParams(-1,72).apply{setMargins(0,4,0,4)})
        }
        root.addView(label("GLASS INTENSITY",12f,muted).apply{setPadding(4,14,0,4)})
        val intensity=listOf("Soft Glass" to "#F7FCFF","Clear Glass" to "#FFFFFF","Deep Glass" to "#EAF6FF")
        intensity.forEach{(n,hx)->
            val c=card(); c.addView(label(n,17f,textColor).apply{gravity=Gravity.CENTER_VERTICAL})
            c.setPadding(18,8,18,8); c.setOnClickListener{prefs.edit().putString("theme_card",hx).apply();showSettings()}
            root.addView(c,LinearLayout.LayoutParams(-1,64).apply{setMargins(0,4,0,4)})
        }
        root.addView(Space(this),LinearLayout.LayoutParams(1,0).apply{weight=1f})
        addBottom(root,"settings"); setContentView(root)
    }

    private fun chooseTf() {
        val values = listOf(
            "1 Minute  •  M1", "3 Minutes •  M3", "5 Minutes •  M5",
            "15 Minutes • M15", "30 Minutes • M30", "1 Hour     • H1", "4 Hours    • H4"
        )
        val nums = listOf(1, 3, 5, 15, 30, 60, 240)
        wheelDialog("Timeframe", values, nums.indexOf(prefs.getInt("tf", 5)).coerceAtLeast(0)) { index ->
            prefs.edit().putInt("tf", nums[index]).apply()
            Scheduler.scheduleNext(this)
            showSettings()
        }
    }

    private fun chooseTiming() {
        val items = mutableListOf<String>()
        val configs = mutableListOf<Pair<Int, Int>>()
        listOf(10, 30, 45, 60, 120, 180, 300).forEach {
            items.add("Before Close  •  ${formatOffset(it)}")
            configs.add(0 to it)
        }
        items.add("At Candle Close")
        configs.add(1 to 0)
        listOf(10, 30, 45, 60, 120, 180, 300).forEach {
            items.add("After Close   •  ${formatOffset(it)}")
            configs.add(2 to it)
        }
        val current = configs.indexOf(prefs.getInt("mode", 0) to prefs.getInt("offset", 120)).let {
            if (it >= 0) it else 0
        }
        wheelDialog("Alert Timing", items, current) { index ->
            val (mode, off) = configs[index]
            prefs.edit().putInt("mode", mode).putInt("offset", off).apply()
            Scheduler.scheduleNext(this)
            showSettings()
        }
    }

    private fun chooseMarket() {
        val values = listOf("Forex", "Crypto  •  24/7", "Forex + Crypto")
        wheelDialog("Market", values, prefs.getInt("market", 0)) { index ->
            prefs.edit().putInt("market", index).apply()
            Scheduler.scheduleNext(this)
            showSessions()
        }
    }

    private fun showSessions() {
        val root = base()
        root.addView(label("Trading Sessions", 30f, textColor))
        root.addView(label("Choose one or more sessions for Forex alerts.", 15f, muted).apply {
            setPadding(0, 4, 0, 12)
        })
        val sessions = listOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        val selected = prefs.getStringSet("sessions", sessions.toSet())?.toMutableSet()
            ?: sessions.toMutableSet()

        sessions.forEach { s ->
            val c = card().apply { setPadding(18, 10, 18, 10) }
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(label(s, 18f, textColor), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
            val sw = Switch(this).apply { isChecked = selected.contains(s) }
            r.addView(sw)
            c.addView(r)
            sw.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(s) else selected.remove(s)
                prefs.edit().putStringSet("sessions", selected).apply()
                Scheduler.scheduleNext(this)
            }
            root.addView(c, LinearLayout.LayoutParams(-1, 78).apply {
                setMargins(0, 6, 0, 6)
            })
        }

        root.addView(button("Done", true).apply {
            textSize = 15f
            setPadding(18, 14, 18, 14)
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(-1, 58).apply {
            setMargins(0, 10, 0, 8)
        })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun editQuiet() {
        val e = EditText(this).apply {
            setText(prefs.getString("quiet", "00:00-07:30"))
            hint = "00:00-07:30"
            textSize = 18f
        }
        AlertDialog.Builder(this)
            .setTitle("Sleep Hours")
            .setMessage("No notifications during this period (phone local time).")
            .setView(e)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("quiet", e.text.toString()).apply()
                Scheduler.scheduleNext(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseNotificationApp() {
        val root = base()
        root.addView(label("Open App on Notification", 30f, textColor))
        root.addView(label("Only trading apps are shown. You can also add any installed app manually.", 15f, muted).apply {
            setPadding(0, 4, 0, 12)
        })

        val current = prefs.getString("notification_app_package", "") ?: ""
        val custom = prefs.getStringSet("custom_notification_apps", emptySet()) ?: emptySet()

        val none = card()
        val nr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nr.addView(label("No app", 18f, textColor), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        nr.addView(label(if (current.isEmpty()) "✓" else "", 24f, accent))
        none.addView(nr)
        none.setOnClickListener {
            prefs.edit().remove("notification_app_package").remove("notification_app_label").apply()
            showSettings()
        }
        root.addView(none, LinearLayout.LayoutParams(-1, 76).apply { setMargins(0, 6, 0, 10) })

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }

        val tradingWords = listOf(
            "metatrader", "meta trader", "tradingview", "trading view", "ctrader", "c trader",
            "ninjatrader", "thinktrader", "trading 212", "ibkr", "interactive brokers", "etoro",
            "binance", "bybit", "okx", "kraken", "coinbase", "kucoin", "bitget", "mexc", "deriv",
            "exness", "xm trading", "alpari", "fxtm", "oanda", "ic markets", "pepperstone",
            "eightcap", "admirals", "tickmill", "fbs", "roboforex", "fxpro", "xtb", "capital.com",
            "trading", "trade", "trader", "broker", "forex", "crypto", "exchange", "invest"
        )
        val tradingApps = allApps.filter { (_, name) ->
            tradingWords.any { name.lowercase().contains(it) }
        }.sortedWith(compareBy({
            !(it.second.contains("MetaTrader", true) || it.second.contains("TradingView", true))
        }, { it.second.lowercase() }))

        root.addView(label("TRADING APPS", 12f, muted).apply { setPadding(4, 6, 0, 4) })
        tradingApps.forEach { addNotificationAppRow(root, it.first, it.second, current) }

        root.addView(label("MY ADDED APPS", 12f, muted).apply { setPadding(4, 14, 0, 4) })
        allApps.filter { custom.contains(it.first) }.sortedBy { it.second.lowercase() }
            .forEach { addNotificationAppRow(root, it.first, it.second, current) }

        root.addView(card().apply {
            val ar = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            ar.addView(label("＋  Add from installed apps", 18f, textColor), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
            ar.addView(label("›", 30f, accent))
            addView(ar)
            setOnClickListener { showInstalledAppsPicker() }
        }, LinearLayout.LayoutParams(-1, 76).apply { setMargins(0, 12, 0, 6) })

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun addNotificationAppRow(root: LinearLayout, pkg: String, name: String, current: String) {
        val c = card()
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r.addView(label(name, 17f, textColor), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        if (pkg == current) r.addView(label("✓", 24f, accent))
        c.addView(r)
        c.setOnClickListener {
            prefs.edit().putString("notification_app_package", pkg).putString("notification_app_label", name).apply()
            showSettings()
        }
        root.addView(c, LinearLayout.LayoutParams(-1, 76).apply { setMargins(0, 4, 0, 4) })
    }

    private fun showInstalledAppsPicker() {
        val root = base()
        root.addView(label("Add Installed App", 30f, textColor))
        root.addView(label("Select an installed app to add it to your notification app list.", 15f, muted).apply {
            setPadding(0, 4, 0, 12)
        })
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val custom = prefs.getStringSet("custom_notification_apps", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 16)
        }
        apps.forEach { (pkg, name) ->
            val c = card()
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(label(name, 17f, textColor), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
            r.addView(label(if (custom.contains(pkg)) "✓" else "＋", 24f, if (custom.contains(pkg)) accent else muted))
            c.addView(r)
            c.setOnClickListener {
                if (!custom.add(pkg)) custom.remove(pkg)
                prefs.edit().putStringSet("custom_notification_apps", custom).apply()
                showInstalledAppsPicker()
            }
            content.addView(c, LinearLayout.LayoutParams(-1, 76).apply { setMargins(0, 4, 0, 4) })
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }
}
