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
import android.view.View
import android.content.res.Configuration
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("prefs", 0) }

    private val darkMode get() = when (prefs.getString("theme_mode", "light")) { "dark" -> true; "system" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES; else -> false }
    private val bg get() = if (darkMode) Color.rgb(18, 22, 28) else Color.rgb(247, 249, 252)
    private val cardColor get() = if (darkMode) Color.rgb(28, 34, 42) else Color.WHITE
    private val soft get() = if (darkMode) Color.rgb(36, 43, 53) else Color.rgb(242, 246, 250)
    private val accent get() = Color.parseColor(prefs.getString("theme_accent", "#1677FF") ?: "#1677FF")
    private val green = Color.rgb(25, 171, 111)
    private val red = Color.rgb(220, 75, 88)
    private val textColor get() = if (darkMode) Color.rgb(241, 245, 249) else Color.rgb(24, 32, 43)
    private val muted get() = if (darkMode) Color.rgb(166, 177, 190) else Color.rgb(105, 116, 130)
    private val line get() = if (darkMode) Color.rgb(55, 64, 76) else Color.rgb(224, 229, 236)

    private val handler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var nextDetailsView: TextView? = null
    private var ticker: Runnable? = null

    override fun onCreate(b: Bundle?) {
        applyNightModePreference()
        super.onCreate(b)
        applyFreshInstallDefaults()
        applySystemBars()
        showHome()
        Scheduler.scheduleNext(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }
    }

    private fun applyNightModePreference() {
        val mode = prefs.getString("theme_mode", "light")
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    private fun applyFreshInstallDefaults() {
        val e = prefs.edit()
        if (!prefs.contains("symbol")) e.putString("symbol", "XAUUSD")
        if (!prefs.contains("tf")) e.putInt("tf", 60)
        if (!prefs.contains("mode")) e.putInt("mode", 1)
        if (!prefs.contains("offset")) e.putInt("offset", 0)
        if (!prefs.contains("open_market")) e.putString("open_market", "00:00")
        if (!prefs.contains("theme_mode")) e.putString("theme_mode", "light")
        if (!prefs.contains("theme_accent")) e.putString("theme_accent", "#1677FF")
        e.apply()

        val migrated = prefs.getBoolean("defaults_migrated_v3", false)
        if (!migrated) {
            val symbol = prefs.getString("symbol", "XAUUSD") ?: "XAUUSD"
            val tf = prefs.getInt("tf", 60)
            val mode = prefs.getInt("mode", 1)
            val offset = prefs.getInt("offset", 0)
            if (symbol == "EURUSD" && tf == 5 && mode == 0 && offset == 120) {
                prefs.edit().putString("symbol", "XAUUSD").putInt("tf", 60).putInt("mode", 1).putInt("offset", 0).apply()
            }
            prefs.edit().putBoolean("defaults_migrated_v3", true).apply()
        }
    }

    override fun onDestroy() {
        ticker?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun base(edgeToEdge: Boolean = false): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(18, 10, 18, 8)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(18, 10 + bars.top, 18, 8 + bars.bottom)
            applySystemBars()
            insets
        }
        return root
    }

    private fun applySystemBars() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            var flags = 0
            if (!darkMode) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (android.os.Build.VERSION.SDK_INT >= 26 && !darkMode) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun text(s: String, size: Float, color: Int = textColor) = TextView(this).apply {
        this.text = s
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(line) }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16, 14, 16, 14)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = 18f
            setStroke(1, line)
        }
        elevation = 1f
    }

    private fun smallButton(s: String, selected: Boolean = false) = TextView(this).apply {
        text = s
        textSize = 13f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setTextColor(if (selected) Color.WHITE else textColor)
        gravity = Gravity.CENTER
        setPadding(16, 10, 16, 10)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (selected) accent else soft)
            cornerRadius = 22f
        }
        minimumHeight = 44
    }

    private fun showHome() {
        val root = base(edgeToEdge = true)
        // Home is intentionally a real scroll surface. Nothing is squeezed just to fit
        // one phone viewport: the alert status, clock, next alert and every control can scroll.
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 14)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })

        // ALERT ACTIVE — deliberately above the clock and given enough height for all text.
        val status = panel().apply { setPadding(24, 22, 24, 22) }
        val statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val enabled = prefs.getBoolean("enabled", true)
        statusRow.addView(text("●", 22f, if (enabled) green else red).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(42, 84))
        val statusTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusTexts.addView(text(if (enabled) "Alerts active" else "Alerts paused", 19f).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        statusTexts.addView(text(
            if (enabled) "Next candle alert is scheduled." else "Turn alerts on to schedule the next candle.",
            12f, muted
        ).apply { setPadding(0, 5, 0, 0) })
        statusRow.addView(statusTexts, LinearLayout.LayoutParams(0, 72).apply { weight = 1f })
        val sw = Switch(this).apply {
            isChecked = enabled
            minWidth = 62
            scaleX = 1.08f
            scaleY = 1.08f
        }
        statusRow.addView(sw, LinearLayout.LayoutParams(76, 72))
        status.addView(statusRow)
        sw.setOnCheckedChangeListener { _, value ->
            prefs.edit().putBoolean("enabled", value).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
        content.addView(status, LinearLayout.LayoutParams(-1, 138).apply {
            setMargins(0, 8, 0, 12)
        })

        // Header is compact; the clock remains the visual focus.
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 2)
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("Candle Alert", 25f))
        titleBox.addView(text("Candle-close notifications", 12f, muted).apply {
            setPadding(0, 4, 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(-1, 58))
        content.addView(header)

        // The clock uses the available content width so it never clips against root padding.
        val clockSize = resources.displayMetrics.widthPixels - 36
        val clock = AnalogClockView(
            this,
            accent,
            textColor,
            muted,
            darkMode,
            { prefs.getInt("tf", 60).coerceAtLeast(1) }
        ) { session ->
            val sessions = prefs.getStringSet(
                "sessions",
                setOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
            )?.toMutableSet() ?: mutableSetOf()
            if (!sessions.remove(session)) sessions.add(session)
            prefs.edit().putStringSet("sessions", sessions).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
        content.addView(clock, LinearLayout.LayoutParams(-1, clockSize).apply {
            setMargins(0, 0, 0, 4)
        })

        // Next alert stays directly under the clock, but remains compact.
        val nextMini = panel().apply { setPadding(16, 8, 16, 8) }
        val nextMiniRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val nextMiniTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nextMiniTexts.addView(text("NEXT ALERT", 10f, muted))
        countdownView = text("Calculating…", 20f, accent).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 3, 0, 0)
        }
        nextMiniTexts.addView(countdownView)
        nextDetailsView = text("Checking schedule…", 11f, muted).apply {
            setPadding(0, 2, 0, 0)
        }
        nextMiniTexts.addView(nextDetailsView)
        nextMiniRow.addView(nextMiniTexts, LinearLayout.LayoutParams(0, 66).apply { weight = 1f })
        nextMini.addView(nextMiniRow)
        content.addView(nextMini, LinearLayout.LayoutParams(-1, 82).apply {
            setMargins(0, 2, 0, 18)
        })

        val tf = prefs.getInt("tf", 60)
        val tfLabel = if (tf >= 60) (tf / 60).toString() + "H" else tf.toString() + "M"
        val market = when (prefs.getInt("market", 0)) {
            0 -> "Forex"
            1 -> "Crypto"
            else -> "Forex + Crypto"
        }

        // Quick Controls are NOT inside a card anymore. Each control is a large,
        // independent row with generous height and readable typography.
        content.addView(text("QUICK CONTROLS", 12f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(2, 0, 0, 8)
        })

        fun quickControl(labelText: String, value: String, click: () -> Unit): LinearLayout {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 20, 22, 20)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = 18f
                    setStroke(1, line)
                }
                elevation = 1f
                minimumHeight = 118
                isClickable = true
                setOnClickListener { click() }
            }
            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            texts.addView(text(labelText, 13f, muted))
            texts.addView(text(value, 23f).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 7, 0, 0)
            })
            box.addView(texts, LinearLayout.LayoutParams(0, 88).apply { weight = 1f })
            box.addView(text("›", 30f, muted).apply { gravity = Gravity.CENTER })
            return box
        }

        val sleepSummary = prefs.getString("quiet", "00:00-07:30") ?: "00:00-07:30"
        content.addView(quickControl("TIMEFRAME", tfLabel) { chooseTf() },
            LinearLayout.LayoutParams(-1, 118).apply { setMargins(0, 0, 0, 12) })
        content.addView(quickControl("MARKET", market) { chooseMarket() },
            LinearLayout.LayoutParams(-1, 118).apply { setMargins(0, 0, 0, 12) })
        content.addView(quickControl("ALERT", timingSummary()) { chooseTiming() },
            LinearLayout.LayoutParams(-1, 118).apply { setMargins(0, 0, 0, 12) })
        content.addView(quickControl("SLEEP HOURS", sleepSummary) { editQuiet() },
            LinearLayout.LayoutParams(-1, 118).apply { setMargins(0, 0, 0, 20) })

        val navHost = LinearLayout(this).apply { setPadding(18, 0, 18, 8) }
        addBottom(navHost, "home")
        root.addView(navHost)
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
                    countdownView?.text = if (!prefs.getBoolean("enabled", true)) "PAUSED" else "No alert scheduled"
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
            setPadding(6, 6, 6, 6)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 24f
                setStroke(1, line)
            }
            elevation = 2f
        }
        val items = listOf("Home" to android.R.drawable.ic_menu_view, "Journal" to android.R.drawable.ic_menu_edit, "Settings" to android.R.drawable.ic_menu_preferences)
        items.forEach { pair ->
            val name = pair.first
            val selected = active == name.lowercase()
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 7, 8, 7)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(235, 243, 255) else cardColor)
                    cornerRadius = 18f
                }
                minimumHeight = 88
                setOnClickListener {
                    when (name) {
                        "Home" -> showHome()
                        "Journal" -> showJournal()
                        "Settings" -> showSettings()
                    }
                }
            }
            val icon = ImageView(this).apply {
                setImageResource(pair.second)
                setColorFilter(if (selected) accent else muted)
                setPadding(4, 3, 4, 1)
            }
            item.addView(icon, LinearLayout.LayoutParams(50, 44))
            item.addView(text(name, 13f, if (selected) accent else muted).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 3, 0, 0)
            })
            nav.addView(item, LinearLayout.LayoutParams(0, 90).apply { weight = 1f; setMargins(5, 0, 5, 0) })
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, 100))
    }


    private data class JournalTrade(
        val id: Long,
        var symbol: String,
        var direction: String,
        var entryTime: Long,
        var exitTime: Long?,
        var entry: String,
        var sl: String,
        var tp: String,
        var exit: String,
        var volume: String,
        var notes: String
    )

    private fun loadTrades(): MutableList<JournalTrade> {
        val raw = prefs.getString("journal_trades_v1", "[]") ?: "[]"
        val result = mutableListOf<JournalTrade>()
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(JournalTrade(
                    o.optLong("id"), o.optString("symbol", "XAUUSD"),
                    o.optString("direction", "BUY"),
                    o.optLong("entryTime", System.currentTimeMillis()),
                    if (o.has("exitTime") && !o.isNull("exitTime")) o.optLong("exitTime") else null,
                    o.optString("entry"), o.optString("sl"), o.optString("tp"),
                    o.optString("exit"), o.optString("volume"), o.optString("notes")
                ))
            }
        } catch (_: Exception) { }
        return result.sortedByDescending { it.entryTime }
    }

    private fun saveTrades(trades: List<JournalTrade>) {
        val arr = org.json.JSONArray()
        trades.forEach { t -> arr.put(org.json.JSONObject().apply {
            put("id", t.id); put("symbol", t.symbol); put("direction", t.direction)
            put("entryTime", t.entryTime)
            if (t.exitTime == null) put("exitTime", org.json.JSONObject.NULL) else put("exitTime", t.exitTime)
            put("entry", t.entry); put("sl", t.sl); put("tp", t.tp); put("exit", t.exit)
            put("volume", t.volume); put("notes", t.notes)
        }) }
        prefs.edit().putString("journal_trades_v1", arr.toString()).apply()
    }

    private fun formatTradeDateTime(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault()).format(java.util.Date(ms))

    private fun formatDuration(start: Long, end: Long?): String {
        if (end == null) return "Open"
        val total = ((end - start).coerceAtLeast(0L)) / 1000L
        val d = total / 86400; val h = (total % 86400) / 3600
        val m = (total % 3600) / 60; val s = total % 60
        return when {
            d > 0 -> String.format(Locale.getDefault(), "%dd %02dh %02dm", d, h, m)
            h > 0 -> String.format(Locale.getDefault(), "%dh %02dm %02ds", h, m, s)
            else -> String.format(Locale.getDefault(), "%dm %02ds", m, s)
        }
    }

    private fun pickTradeDateTime(title: String, initial: Long, onPicked: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = initial }
        android.app.DatePickerDialog(this, { _, year, month, day ->
            android.app.TimePickerDialog(this, { _, hour, minute ->
                val chosen = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year); set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, day); set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                onPicked(chosen.timeInMillis)
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
            setTitle(title)
        }.show()
    }

    private fun showJournal() {
        val root = base()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("Journal", 28f))
        titleBox.addView(text("Every trade is saved as a separate card.", 14f, muted).apply { setPadding(0, 4, 0, 0) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, 70).apply { weight = 1f })
        header.addView(smallButton("＋ Add", true).apply { setOnClickListener { showTradeEditor(null) } }, LinearLayout.LayoutParams(94, 50))
        root.addView(header)

        val trades = loadTrades()
        val closed = trades.count { it.exitTime != null }
        val wins = trades.count {
            val e = it.entry.toDoubleOrNull(); val x = it.exit.toDoubleOrNull()
            e != null && x != null && if (it.direction == "BUY") x > e else x < e
        }
        val avgDuration = trades.filter { it.exitTime != null }.map { it.exitTime!! - it.entryTime }.average()
        val avgDurationText = if (avgDuration.isNaN()) "—" else formatDuration(0, avgDuration.toLong())

        val summary = panel().apply { setPadding(12, 10, 12, 10) }
        val sr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun stat(label: String, value: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(text(value, 17f, accent).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
            addView(text(label, 9f, muted).apply { setPadding(0, 4, 0, 0); gravity = Gravity.CENTER })
        }
        sr.addView(stat("Trades", trades.size.toString()), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        sr.addView(stat("Closed", closed.toString()), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        sr.addView(stat("Win Rate", if (closed > 0) String.format(Locale.getDefault(), "%.0f%%", wins * 100.0 / closed) else "—"), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        sr.addView(stat("Avg Duration", avgDurationText), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        summary.addView(sr)
        root.addView(summary, LinearLayout.LayoutParams(-1, 86).apply { setMargins(0, 4, 0, 10) })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 12) }
        if (trades.isEmpty()) {
            val empty = panel().apply {
                addView(text("No trades yet", 18f).apply { typeface = Typeface.DEFAULT_BOLD })
                addView(text("Tap ＋ Add to register a trade. Entry time defaults to the registration moment, and can be changed later.", 13f, muted).apply { setPadding(0, 8, 0, 0) })
            }
            content.addView(empty)
        } else trades.forEach { addTradeCard(content, it) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        addBottom(root, "journal")
        setContentView(root)
    }

    private fun addTradeCard(parent: LinearLayout, trade: JournalTrade) {
        val closed = trade.exitTime != null
        val card = panel().apply { setPadding(16, 14, 16, 12) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(trade.symbol, 19f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, 42).apply { weight = 1f })
        top.addView(text(if (closed) "CLOSED" else "OPEN", 11f, if (closed) green else accent).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(12, 8, 12, 8)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (closed) Color.argb(28, 25, 171, 111) else Color.argb(28, 22, 119, 255)); cornerRadius = 18f
            }
        })
        card.addView(top)
        card.addView(text(trade.direction + "   •   Entry: " + trade.entry.ifBlank { "—" } + "   •   SL: " + trade.sl.ifBlank { "—" } + "   •   TP: " + trade.tp.ifBlank { "—" }, 12f, muted).apply { setPadding(0, 5, 0, 0) })
        card.addView(text("Entry time: " + formatTradeDateTime(trade.entryTime), 12f).apply { setPadding(0, 8, 0, 0) })
        card.addView(text("Exit time: " + (trade.exitTime?.let { formatTradeDateTime(it) } ?: "Not closed"), 12f).apply { setPadding(0, 4, 0, 0) })
        card.addView(text("Duration: " + formatDuration(trade.entryTime, trade.exitTime), 12f, accent).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, 4, 0, 0) })
        if (trade.notes.isNotBlank()) card.addView(text(trade.notes, 12f, muted).apply { setPadding(0, 6, 0, 0) })
        val actions = LinearLayout(this).apply { gravity = Gravity.END; setPadding(0, 10, 0, 0) }
        actions.addView(smallButton("Edit"), LinearLayout.LayoutParams(88, 44).apply { setMargins(6, 0, 0, 0) })
        actions.addView(smallButton("Delete"), LinearLayout.LayoutParams(88, 44).apply { setMargins(6, 0, 0, 0) })
        actions.getChildAt(0).setOnClickListener { showTradeEditor(trade.id) }
        actions.getChildAt(1).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Delete trade?").setMessage("This trade will be removed from the journal.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                    saveTrades(loadTrades().filterNot { it.id == trade.id }); showJournal()
                }.show()
        }
        card.addView(actions)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 7) })
    }

    private fun showTradeEditor(id: Long?) {
        val trades = loadTrades()
        val existing = id?.let { wanted -> trades.firstOrNull { it.id == wanted } }
        val entryDefault = existing?.entryTime ?: System.currentTimeMillis()
        var entryTime = entryDefault
        var exitTime = existing?.exitTime
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 2, 8, 0) }

        val symbol = EditText(this).apply { hint = "Symbol"; setText(existing?.symbol ?: (prefs.getString("symbol", "XAUUSD") ?: "XAUUSD")); textSize = 16f }
        val direction = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("BUY", "SELL"))
            setSelection(if (existing?.direction == "SELL") 1 else 0)
        }
        fun priceField(hintText: String, value: String = "") = EditText(this).apply {
            hint = hintText; setText(value); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; textSize = 16f
        }
        val entry = priceField("Entry price", existing?.entry ?: "")
        val sl = priceField("Stop Loss", existing?.sl ?: "")
        val tp = priceField("Take Profit", existing?.tp ?: "")
        val exit = priceField("Exit price (optional)", existing?.exit ?: "")
        val volume = priceField("Volume (optional)", existing?.volume ?: "")
        val notes = EditText(this).apply { hint = "Notes (optional)"; setText(existing?.notes ?: ""); minLines = 2; gravity = Gravity.TOP; textSize = 15f }

        val entryTimeButton = Button(this).apply {
            text = "Entry time: " + formatTradeDateTime(entryTime)
            setOnClickListener { pickTradeDateTime("Entry time", entryTime) { entryTime = it; text = "Entry time: " + formatTradeDateTime(it) } }
        }
        val exitTimeButton = Button(this).apply {
            text = "Exit time: " + (exitTime?.let { formatTradeDateTime(it) } ?: "Not set")
            setOnClickListener { pickTradeDateTime("Exit time", exitTime ?: System.currentTimeMillis()) { exitTime = it; text = "Exit time: " + formatTradeDateTime(it) } }
        }
        val clearExit = smallButton("Clear exit time")
        clearExit.setOnClickListener { exitTime = null; exitTimeButton.text = "Exit time: Not set" }

        box.addView(text("Entry/exit time is editable. New trades default to the moment you register them.", 12f, muted).apply { setPadding(0, 0, 0, 6) })
        listOf(symbol, direction, entry, sl, tp, exit, volume, entryTimeButton, exitTimeButton, clearExit, notes).forEach {
            box.addView(it, LinearLayout.LayoutParams(-1, if (it == direction) 52 else 56).apply { setMargins(0, 2, 0, 2) })
        }

        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Add Trade" else "Edit Trade")
            .setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val now = System.currentTimeMillis()
                val finalEntry = if (existing == null && entryTime == entryDefault) now else entryTime
                val t = existing ?: JournalTrade(now,
                    symbol.text.toString().trim().uppercase(Locale.getDefault()).ifBlank { "XAUUSD" },
                    if (direction.selectedItemPosition == 1) "SELL" else "BUY", finalEntry, exitTime,
                    entry.text.toString().trim(), sl.text.toString().trim(), tp.text.toString().trim(),
                    exit.text.toString().trim(), volume.text.toString().trim(), notes.text.toString().trim())
                if (existing != null) {
                    t.symbol = symbol.text.toString().trim().uppercase(Locale.getDefault()).ifBlank { "XAUUSD" }
                    t.direction = if (direction.selectedItemPosition == 1) "SELL" else "BUY"
                    t.entryTime = entryTime; t.exitTime = exitTime
                    t.entry = entry.text.toString().trim(); t.sl = sl.text.toString().trim(); t.tp = tp.text.toString().trim()
                    t.exit = exit.text.toString().trim(); t.volume = volume.text.toString().trim(); t.notes = notes.text.toString().trim()
                }
                val updated = trades.filterNot { it.id == t.id }.toMutableList()
                updated.add(t); saveTrades(updated); dialog.dismiss(); showJournal()
            }
        }
        dialog.show()
    }

    private fun showSettings() {
        val root = base()
        root.addView(text("Settings", 28f))
        root.addView(text("Configure how Candle Alert behaves", 14f, muted).apply { setPadding(0, 4, 0, 12) })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 10) }

        // Daily trading controls live on Home. Settings contains only behavior/system options.
        val options = listOf(
            "Open Market" to "Broker candle alignment start",
            "Theme" to "Light, dark, or system default",
            "Open App on Notification" to "Open your selected trading app",
            "Exact Alarm Permission" to "Allow precise background alerts"
        )

        options.forEach { pair ->
            val c = panel().apply { setPadding(16, 8, 14, 8) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            titles.addView(text(pair.first, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
            titles.addView(text(pair.second, 12f, muted).apply { setPadding(0, 4, 0, 0) })
            row.addView(titles, LinearLayout.LayoutParams(0, 64).apply { weight = 1f })
            row.addView(text("›", 28f, muted).apply { gravity = Gravity.CENTER })
            c.addView(row)
            c.setOnClickListener {
                when (pair.first) {
                    "Open Market" -> chooseOpenMarket()
                    "Theme" -> showThemeSettings()
                    "Open App on Notification" -> chooseNotificationApp()
                    "Exact Alarm Permission" -> if (android.os.Build.VERSION.SDK_INT >= 31) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:" + packageName)
                        })
                    }
                }
            }
            content.addView(c, LinearLayout.LayoutParams(-1, 92).apply { setMargins(0, 5, 0, 5) })
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun wheelDialog(title: String, values: List<String>, selected: Int, onSelected: (Int) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 6, 16, 4)
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
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ -> onSelected(picker.value) }.create()
        dialog.show()
    }

    private fun chooseSymbol() {
        val values = listOf("XAUUSD  •  Gold", "EURUSD", "GBPUSD", "USDJPY", "XAGUSD  •  Silver", "USOIL  •  Oil", "BTCUSD", "ETHUSD", "Custom Symbol")
        val syms = listOf("XAUUSD","EURUSD","GBPUSD","USDJPY","XAGUSD","USOIL","BTCUSD","ETHUSD")
        val current = prefs.getString("symbol", "XAUUSD") ?: "XAUUSD"
        val idx = syms.indexOf(current).let { if (it >= 0) it else 8 }
        wheelDialog("Symbol", values, idx) { i ->
            if (i == 8) {
                val e = EditText(this).apply { setText(if (current == "XAUUSD") "" else current); hint = "e.g. NAS100"; textSize = 17f }
                AlertDialog.Builder(this).setTitle("Custom Symbol").setView(e)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        val v = e.text.toString().trim().uppercase(Locale.getDefault())
                        if (v.isNotEmpty()) prefs.edit().putString("symbol", v).apply()
                        showHome()
                    }.show()
            } else {
                prefs.edit().putString("symbol", syms[i]).apply()
                showHome()
            }
        }
    }

    private fun chooseTf() {
        val values = listOf("1 Minute  •  M1", "3 Minutes •  M3", "5 Minutes •  M5", "15 Minutes • M15", "30 Minutes • M30", "1 Hour     • H1", "4 Hours    • H4")
        val nums = listOf(1, 3, 5, 15, 30, 60, 240)
        val current = nums.indexOf(prefs.getInt("tf", 60)).let { if (it >= 0) it else 5 }
        wheelDialog("Timeframe", values, current) { i ->
            prefs.edit().putInt("tf", nums[i]).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
    }

    private fun chooseTiming() {
        val items = mutableListOf<String>()
        val configs = mutableListOf<Pair<Int, Int>>()
        listOf(10, 30, 45, 60, 120, 180, 300).forEach {
            items.add("Before Close  •  " + formatOffset(it)); configs.add(0 to it)
        }
        items.add("At Candle Close"); configs.add(1 to 0)
        listOf(10, 30, 45, 60, 120, 180, 300).forEach {
            items.add("After Close   •  " + formatOffset(it)); configs.add(2 to it)
        }
        val current = configs.indexOf(prefs.getInt("mode", 1) to prefs.getInt("offset", 0)).let { if (it >= 0) it else 7 }
        wheelDialog("Alert Timing", items, current) { i ->
            val pair = configs[i]
            prefs.edit().putInt("mode", pair.first).putInt("offset", pair.second).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
    }

    private fun chooseMarket() {
        val values = listOf("Forex", "Crypto  •  24/7", "Forex + Crypto")
        val current = prefs.getInt("market", 0).coerceIn(0, 2)
        wheelDialog("Market", values, current) { i ->
            prefs.edit().putInt("market", i).apply()
            Scheduler.scheduleNext(this)
            showSessions()
        }
    }

    private fun chooseOpenMarket() {
        val current = prefs.getString("open_market", "00:00") ?: "00:00"
        val parts = current.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(10, 4, 10, 4) }
        val hp = NumberPicker(this).apply { minValue=0; maxValue=23; value=h; wrapSelectorWheel=true; descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS }
        val mp = NumberPicker(this).apply { minValue=0; maxValue=59; value=m; wrapSelectorWheel=true; descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS }
        box.addView(hp, LinearLayout.LayoutParams(0,220).apply{weight=1f})
        box.addView(text(":",28f).apply{gravity=Gravity.CENTER}, LinearLayout.LayoutParams(36,220))
        box.addView(mp, LinearLayout.LayoutParams(0,220).apply{weight=1f})
        AlertDialog.Builder(this).setTitle("Open Market")
            .setMessage("Candle alignment starts from this broker open time, using phone local time.")
            .setView(box).setNegativeButton("Cancel",null)
            .setPositiveButton("Done"){_,_->
                prefs.edit().putString("open_market", String.format(Locale.getDefault(), "%02d:%02d", hp.value, mp.value)).apply()
                Scheduler.scheduleNext(this); showSettings()
            }.show()
    }

    private fun showSessions() {
        val root = base()
        root.addView(text("Trading Sessions", 28f))
        root.addView(text("Select the Forex sessions used for alerts.", 14f, muted).apply { setPadding(0, 4, 0, 12) })
        val sessions = listOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        val selected = prefs.getStringSet("sessions", sessions.toSet())?.toMutableSet() ?: sessions.toMutableSet()
        sessions.forEach { s ->
            val c = panel().apply { setPadding(16, 8, 14, 8) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(s, 16f), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
            val sw = Switch(this).apply { isChecked = selected.contains(s); minWidth = 52 }
            row.addView(sw, LinearLayout.LayoutParams(56, 48))
            c.addView(row)
            sw.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(s) else selected.remove(s)
                prefs.edit().putStringSet("sessions", selected).apply()
                Scheduler.scheduleNext(this)
            }
            root.addView(c, LinearLayout.LayoutParams(-1, 74).apply { setMargins(0, 4, 0, 4) })
        }
        root.addView(smallButton("Done", true).apply {
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(-1, 50).apply { setMargins(0, 10, 0, 8) })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun editQuiet() {
        val e = EditText(this).apply { setText(prefs.getString("quiet", "00:00-07:30")); hint = "00:00-07:30"; textSize = 17f }
        AlertDialog.Builder(this).setTitle("Sleep Hours")
            .setMessage("No notifications during this period, using phone local time.")
            .setView(e).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("quiet", e.text.toString()).apply()
                Scheduler.scheduleNext(this)
                showSettings()
            }.show()
    }

    private fun showThemeSettings() {
        val root = base()
        root.addView(text("Theme", 28f))
        root.addView(text("Choose light, dark, or your phone's system theme.", 14f, muted).apply { setPadding(0, 4, 0, 14) })
        val modes = listOf("light" to "Light", "dark" to "Dark", "system" to "System default")
        modes.forEach { pair ->
            val mode = pair.first
            val c = panel().apply { setPadding(16, 8, 14, 8) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            titles.addView(text(pair.second, 17f).apply { typeface = Typeface.DEFAULT_BOLD })
            titles.addView(text(when(mode) {"light" -> "Always use light mode"; "dark" -> "Always use dark mode"; else -> "Match the phone's system theme"}, 12f, muted).apply { setPadding(0, 4, 0, 0) })
            row.addView(titles, LinearLayout.LayoutParams(0, 66).apply { weight = 1f })
            row.addView(text(if (prefs.getString("theme_mode", "light") == mode) "✓" else "", 23f, accent))
            c.addView(row)
            c.setOnClickListener { prefs.edit().putString("theme_mode", mode).apply(); applyNightModePreference(); showSettings() }
            root.addView(c, LinearLayout.LayoutParams(-1, 84).apply { setMargins(0, 5, 0, 5) })
        }
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun chooseNotificationApp() {
        val root = base()
        root.addView(text("Notification App", 28f))
        root.addView(text("Choose which installed trading app opens when an alert arrives.", 14f, muted).apply { setPadding(0, 4, 0, 12) })
        val current = prefs.getString("notification_app_package", "") ?: ""
        val custom = prefs.getStringSet("custom_notification_apps", emptySet()) ?: emptySet()

        val none = panel().apply { setPadding(16, 8, 14, 8) }
        val nr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nr.addView(text("No app", 16f), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        nr.addView(text(if (current.isEmpty()) "✓" else "", 22f, accent))
        none.addView(nr)
        none.setOnClickListener { prefs.edit().remove("notification_app_package").remove("notification_app_label").apply(); showSettings() }
        root.addView(none, LinearLayout.LayoutParams(-1, 74).apply { setMargins(0, 4, 0, 10) })

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }

        val words = listOf("metatrader","meta trader","tradingview","trading view","ctrader","ninjatrader","thinktrader","trading 212","ibkr","interactive brokers","etoro","binance","bybit","okx","kraken","coinbase","kucoin","bitget","mexc","deriv","exness","xm trading","alpari","fxtm","oanda","ic markets","pepperstone","eightcap","admirals","tickmill","fbs","roboforex","fxpro","xtb","capital.com","trading","trade","trader","broker","forex","crypto","exchange","invest")
        val tradingApps = allApps.filter { pair -> words.any { pair.second.lowercase().contains(it) } }
            .sortedWith(compareBy({ !(it.second.contains("MetaTrader", true) || it.second.contains("TradingView", true)) }, { it.second.lowercase() }))

        root.addView(text("TRADING APPS", 11f, muted).apply { setPadding(2, 4, 0, 5) })
        tradingApps.forEach { addNotificationAppRow(root, it.first, it.second, current) }
        root.addView(text("MY ADDED APPS", 11f, muted).apply { setPadding(2, 12, 0, 5) })
        allApps.filter { custom.contains(it.first) }.sortedBy { it.second.lowercase() }.forEach { addNotificationAppRow(root, it.first, it.second, current) }

        root.addView(panel().apply {
            val r = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(text("＋  Add installed app", 16f), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
            r.addView(text("›", 28f, muted))
            addView(r)
            setOnClickListener { showInstalledAppsPicker() }
        }, LinearLayout.LayoutParams(-1, 74).apply { setMargins(0, 10, 0, 6) })

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun addNotificationAppRow(root: LinearLayout, pkg: String, name: String, current: String) {
        val c = panel().apply { setPadding(16, 7, 14, 7) }
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r.addView(text(name, 16f), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
        if (pkg == current) r.addView(text("✓", 22f, accent))
        c.addView(r)
        c.setOnClickListener {
            prefs.edit().putString("notification_app_package", pkg).putString("notification_app_label", name).apply()
            showSettings()
        }
        root.addView(c, LinearLayout.LayoutParams(-1, 74).apply { setMargins(0, 4, 0, 4) })
    }

    private fun showInstalledAppsPicker() {
        val root = base()
        root.addView(text("Add Installed App", 28f))
        root.addView(text("Select an app to add it to the notification list.", 14f, muted).apply { setPadding(0, 4, 0, 12) })
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val custom = prefs.getStringSet("custom_notification_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        val apps = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 12) }
        apps.forEach { pair ->
            val c = panel().apply { setPadding(16, 7, 14, 7) }
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(text(pair.second, 16f), LinearLayout.LayoutParams(0, 58).apply { weight = 1f })
            r.addView(text(if (custom.contains(pair.first)) "✓" else "＋", 22f, if (custom.contains(pair.first)) accent else muted))
            c.addView(r)
            c.setOnClickListener {
                if (!custom.add(pair.first)) custom.remove(pair.first)
                prefs.edit().putStringSet("custom_notification_apps", custom).apply()
                showInstalledAppsPicker()
            }
            content.addView(c, LinearLayout.LayoutParams(-1, 74).apply { setMargins(0, 4, 0, 4) })
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }
}
