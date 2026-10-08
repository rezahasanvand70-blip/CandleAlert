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
        var entry: String,
        var sl: String,
        var tp: String,
        var volume: String,
        var exitTime: Long?,
        var exit: String,
        var pnl: String,
        var exitReason: String,
        var notes: String
    )

    private fun journalTrades(): MutableList<JournalTrade> {
        val raw = prefs.getString("journal_trades_v2", "[]") ?: "[]"
        val out = mutableListOf<JournalTrade>()
        try {
            val a = org.json.JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(JournalTrade(
                    o.optLong("id"),
                    o.optString("symbol", "XAUUSD"),
                    o.optString("direction", "BUY"),
                    o.optLong("entryTime", System.currentTimeMillis()),
                    o.optString("entry"),
                    o.optString("sl"),
                    o.optString("tp"),
                    o.optString("volume"),
                    if (o.isNull("exitTime")) null else o.optLong("exitTime"),
                    o.optString("exit"),
                    o.optString("pnl"),
                    o.optString("exitReason"),
                    o.optString("notes")
                ))
            }
        } catch (_: Exception) {}
        return out.sortedByDescending { it.entryTime }.toMutableList()
    }

    private fun saveJournalTrades(list: List<JournalTrade>) {
        val a = org.json.JSONArray()
        list.forEach { t ->
            a.put(org.json.JSONObject().apply {
                put("id", t.id)
                put("symbol", t.symbol)
                put("direction", t.direction)
                put("entryTime", t.entryTime)
                put("entry", t.entry)
                put("sl", t.sl)
                put("tp", t.tp)
                put("volume", t.volume)
                if (t.exitTime == null) put("exitTime", org.json.JSONObject.NULL) else put("exitTime", t.exitTime)
                put("exit", t.exit)
                put("pnl", t.pnl)
                put("exitReason", t.exitReason)
                put("notes", t.notes)
            })
        }
        prefs.edit().putString("journal_trades_v2", a.toString()).apply()
    }

    private fun journalDate(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(ms))

    private fun journalRR(t: JournalTrade): Double? {
        val e = t.entry.toDoubleOrNull() ?: return null
        val s = t.sl.toDoubleOrNull() ?: return null
        val p = t.tp.toDoubleOrNull() ?: return null
        val risk = kotlin.math.abs(e - s)
        if (risk == 0.0) return null
        return kotlin.math.abs(p - e) / risk
    }

    private fun journalSuggestedTP(t: JournalTrade): Double? {
        val e = t.entry.toDoubleOrNull() ?: return null
        val s = t.sl.toDoubleOrNull() ?: return null
        if (e == s) return null
        val risk = kotlin.math.abs(e - s)
        return if (t.direction == "BUY") e + risk * 2.0 else e - risk * 2.0
    }

    private fun showJournal() {
        val root = base()
        root.addView(text("Journal", 28f))
        root.addView(text("Record, review and close trades step by step.", 13f, muted).apply {
            setPadding(0, 4, 0, 12)
        })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 12)
        }
        val trades = journalTrades()
        val open = trades.filter { it.exitTime == null }
        val closed = trades.filter { it.exitTime != null }

        content.addView(smallButton("+  Add Trade", true).apply {
            setOnClickListener { journalAddTrade() }
        }, LinearLayout.LayoutParams(-1, 54).apply { setMargins(0, 4, 0, 14) })

        if (open.isNotEmpty()) {
            content.addView(text("OPEN TRADES", 11f, muted).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(2, 4, 0, 6)
            })
            open.forEach { journalTradeCard(content, it) }
        }
        if (closed.isNotEmpty()) {
            content.addView(text("CLOSED TRADES", 11f, muted).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(2, 18, 0, 6)
            })
            closed.forEach { journalTradeCard(content, it) }
        }
        if (trades.isEmpty()) {
            content.addView(panel().apply {
                gravity = Gravity.CENTER
                addView(text("No trades yet", 18f).apply { gravity = Gravity.CENTER })
                addView(text("Tap + Add Trade to start.", 13f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 8, 0, 0)
                })
            }, LinearLayout.LayoutParams(-1, 150).apply { setMargins(0, 8, 0, 0) })
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        val navHost = LinearLayout(this).apply { setPadding(18, 0, 18, 8) }
        addBottom(navHost, "journal")
        root.addView(navHost)
        setContentView(root)
    }

    private fun journalTradeCard(parent: LinearLayout, t: JournalTrade) {
        val isOpen = t.exitTime == null
        val c = panel().apply {
            setBackgroundColor(if (isOpen) {
                if (darkMode) Color.rgb(31, 42, 48) else Color.rgb(244, 249, 253)
            } else cardColor)
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(text(t.symbol + "  •  " + t.direction, 18f).apply { typeface = Typeface.DEFAULT_BOLD })
        title.addView(text("Entry " + journalDate(t.entryTime), 12f, muted).apply { setPadding(0, 4, 0, 0) })
        row.addView(title, LinearLayout.LayoutParams(0, 56).apply { weight = 1f })
        row.addView(text(if (isOpen) "OPEN" else "CLOSED", 11f, if (isOpen) accent else muted).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        c.addView(row)
        c.addView(text(
            "Entry " + t.entry.ifBlank { "—" } +
                "   SL " + t.sl.ifBlank { "—" } +
                "   TP " + t.tp.ifBlank { "—" }, 12f, muted
        ).apply { setPadding(0, 8, 0, 0) })
        val rr = journalRR(t)
        if (isOpen && rr != null) {
            c.addView(text("R:R  1 : " + String.format(Locale.US, "%.2f", rr), 12f, accent).apply {
                setPadding(0, 5, 0, 0)
            })
        }
        if (!isOpen) {
            c.addView(text(
                "Exit " + t.exit.ifBlank { "—" } + "   •   P/L " + t.pnl.ifBlank { "—" },
                13f, if ((t.pnl.toDoubleOrNull() ?: 0.0) >= 0) green else red
            ).apply { setPadding(0, 5, 0, 0) })
        }
        val actions = LinearLayout(this).apply { gravity = Gravity.END; setPadding(0, 10, 0, 0) }
        actions.addView(smallButton("Edit").apply { setOnClickListener { journalEditTrade(t) } })
        if (isOpen) {
            actions.addView(smallButton("Close").apply { setOnClickListener { journalCloseTrade(t) } })
        }
        actions.addView(smallButton("Delete").apply { setOnClickListener { journalDeleteTrade(t) } })
        c.addView(actions)
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4, 0, 6) })
    }

    private fun journalAsk(title: String, hint: String, initial: String = "", optional: Boolean = true, onDone: (String) -> Unit) {
        val e = EditText(this).apply {
            setText(initial)
            this.hint = hint
            textSize = 18f
        }
        val b = AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel", null)
        if (optional) b.setNeutralButton("Skip") { _, _ -> onDone("") }
        b.setPositiveButton("Next") { _, _ -> onDone(e.text.toString().trim()) }
        b.show()
    }

    private fun journalAddTrade() {
        journalSymbolStep(JournalTrade(System.currentTimeMillis(), "XAUUSD", "BUY", System.currentTimeMillis(), "", "", "", "", null, "", "", "", ""))
    }

    private fun journalSymbolStep(t: JournalTrade) {
        journalAsk("1 / 8  •  Symbol", "e.g. XAUUSD", t.symbol, false) {
            t.symbol = it.uppercase(Locale.getDefault())
            journalDirectionStep(t)
        }
    }

    private fun journalDirectionStep(t: JournalTrade) {
        AlertDialog.Builder(this).setTitle("2 / 8  •  Direction")
            .setItems(arrayOf("BUY", "SELL")) { _, i ->
                t.direction = if (i == 0) "BUY" else "SELL"
                journalEntryTimeStep(t)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun journalEntryTimeStep(t: JournalTrade) {
        val now = System.currentTimeMillis()
        val e = EditText(this).apply { setText(journalDate(t.entryTime)); textSize = 17f }
        AlertDialog.Builder(this).setTitle("3 / 8  •  Entry Time")
            .setMessage("Use the time picker if you want to change it. The current time is the default.")
            .setView(e).setNegativeButton("Cancel", null)
            .setPositiveButton("Next") { _, _ -> t.entryTime = now; journalEntryStep(t) }.show()
    }

    private fun journalEntryStep(t: JournalTrade) {
        journalAsk("4 / 8  •  Entry Price", "Entry price", "", false) {
            if (it.toDoubleOrNull() == null) {
                Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show()
                journalEntryStep(t)
            } else {
                t.entry = it
                journalSLStep(t)
            }
        }
    }

    private fun journalSLStep(t: JournalTrade) {
        journalAsk("5 / 8  •  Stop Loss", "Optional — skip if no SL", "", true) {
            t.sl = it
            journalTPStep(t)
        }
    }

    private fun journalTPStep(t: JournalTrade) {
        val suggested = journalSuggestedTP(t)
        if (suggested == null) {
            journalAsk("6 / 8  •  Take Profit", "Optional — skip if no TP", "", true) {
                t.tp = it
                journalVolumeStep(t)
            }
            return
        }
        val input = EditText(this).apply {
            setText(String.format(Locale.US, "%.5f", suggested))
            selectAll()
            hint = "Optional TP"
            textSize = 18f
        }
        AlertDialog.Builder(this).setTitle("6 / 8  •  Take Profit")
            .setMessage("Suggested TP for RR 2:1: " + String.format(Locale.US, "%.5f", suggested) +
                "\nYou can edit it or choose Skip.")
            .setView(input).setNegativeButton("Cancel", null)
            .setNeutralButton("Skip") { _, _ -> t.tp = ""; journalVolumeStep(t) }
            .setPositiveButton("Next") { _, _ ->
                t.tp = input.text.toString().trim()
                journalVolumeStep(t)
            }.show()
    }

    private fun journalVolumeStep(t: JournalTrade) {
        journalAsk("7 / 8  •  Position Size", "Optional — e.g. 0.10 lot", "", true) {
            t.volume = it
            journalNotesStep(t)
        }
    }

    private fun journalNotesStep(t: JournalTrade) {
        journalAsk("8 / 8  •  Notes", "Optional setup / reason / review", "", true) {
            t.notes = it
            journalConfirmNew(t)
        }
    }

    private fun journalConfirmNew(t: JournalTrade) {
        // Use a custom, padded content view instead of AlertDialog's default message view.
        // This prevents the second line (Entry) and other rows from being vertically clipped
        // on smaller phones / larger font settings.
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 8)
        }
        fun addReviewRow(label: String, value: String) {
            box.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 7, 0, 7)
                addView(text(label, 14f, muted), LinearLayout.LayoutParams(82, 34))
                addView(text(value, 16f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, 34).apply { weight = 1f })
            })
        }
        addReviewRow("Symbol", t.symbol + "  " + t.direction)
        addReviewRow("Entry", t.entry)
        addReviewRow("SL", t.sl.ifBlank { "—" })
        addReviewRow("TP", t.tp.ifBlank { "—" })
        journalRR(t)?.let { addReviewRow("R:R", "1 : " + String.format(Locale.US, "%.2f", it)) }
        box.addView(text("Save as OPEN trade?", 14f, muted).apply {
            setPadding(0, 10, 0, 2)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Review Trade")
            .setView(box)
            .setNegativeButton("Back", null)
            .setPositiveButton("Save") { _, _ ->
                val list = journalTrades()
                list.add(t)
                saveJournalTrades(list)
                showJournal()
            }
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.90f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun journalCloseTrade(t: JournalTrade) {
        journalAsk("1 / 5  •  Exit Price", "Exit price", "", false) {
            if (it.toDoubleOrNull() == null) {
                Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show()
                journalCloseTrade(t)
            } else {
                t.exit = it
                journalExitTimeStep(t)
            }
        }
    }

    private fun journalExitTimeStep(t: JournalTrade) {
        t.exitTime = System.currentTimeMillis()
        journalPnlStep(t)
    }

    private fun journalPnlStep(t: JournalTrade) {
        val e = t.entry.toDoubleOrNull()
        val x = t.exit.toDoubleOrNull()
        val volume = t.volume.toDoubleOrNull() ?: 1.0
        val calculated = if (e != null && x != null) {
            (if (t.direction == "BUY") x - e else e - x) * volume
        } else null
        journalAsk(
            "3 / 5  •  Profit / Loss",
            calculated?.let { "Suggested P/L: " + String.format(Locale.US, "%.5f", it) } ?: "Enter P/L",
            "", true
        ) {
            t.pnl = it.ifBlank { calculated?.toString() ?: "" }
            journalExitReasonStep(t)
        }
    }

    private fun journalExitReasonStep(t: JournalTrade) {
        val reasons = arrayOf("Take Profit", "Stop Loss", "Manual Close", "Signal Reversal", "Session End", "Other")
        AlertDialog.Builder(this).setTitle("4 / 5  •  Exit Reason")
            .setItems(reasons) { _, i -> t.exitReason = reasons[i]; journalCloseNotesStep(t) }
            .setNeutralButton("Skip") { _, _ -> t.exitReason = ""; journalCloseNotesStep(t) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun journalCloseNotesStep(t: JournalTrade) {
        journalAsk("5 / 5  •  Closing Notes", "Optional — what went well / what to improve", "", true) {
            t.notes = if (it.isBlank()) t.notes else if (t.notes.isBlank()) it else t.notes + "\n" + it
            journalConfirmClose(t)
        }
    }

    private fun journalConfirmClose(t: JournalTrade) {
        var msg = t.symbol + "  " + t.direction +
            "\nEntry: " + t.entry +
            "\nExit: " + t.exit +
            "\nP/L: " + t.pnl.ifBlank { "—" } +
            "\nExit time: " + (t.exitTime?.let { journalDate(it) } ?: "—")
        journalRR(t)?.let { msg += "\nR:R planned: 1 : " + String.format(Locale.US, "%.2f", it) }
        AlertDialog.Builder(this).setTitle("Close Trade").setMessage(msg)
            .setNegativeButton("Back", null)
            .setPositiveButton("Save & Close") { _, _ ->
                val list = journalTrades()
                val i = list.indexOfFirst { it.id == t.id }
                if (i >= 0) list[i] = t
                saveJournalTrades(list)
                showJournal()
            }.show()
    }

    private fun journalEditTrade(t: JournalTrade) {
        journalAsk("Edit • Symbol", "e.g. XAUUSD", t.symbol, false) {
            t.symbol = it.uppercase(Locale.getDefault())
            journalAsk("Edit • Entry Price", "Entry price", t.entry, false) { p ->
                if (p.toDoubleOrNull() == null) {
                    Toast.makeText(this, "Invalid price.", Toast.LENGTH_SHORT).show()
                } else {
                    t.entry = p
                    journalAsk("Edit • Stop Loss", "Optional", t.sl, true) { sl ->
                        t.sl = sl
                        val suggested = journalSuggestedTP(t)
                        if (suggested != null) {
                            val input = EditText(this).apply {
                                setText(t.tp.ifBlank { String.format(Locale.US, "%.5f", suggested) })
                                textSize = 18f
                            }
                            AlertDialog.Builder(this).setTitle("Edit • Take Profit")
                                .setMessage("Suggested RR 2:1 TP: " + String.format(Locale.US, "%.5f", suggested))
                                .setView(input).setNegativeButton("Cancel", null)
                                .setNeutralButton("Skip") { _, _ -> t.tp = ""; journalFinishEdit(t) }
                                .setPositiveButton("Save TP") { _, _ -> t.tp = input.text.toString().trim(); journalFinishEdit(t) }
                                .show()
                        } else {
                            journalAsk("Edit • Take Profit", "Optional", t.tp, true) { tp -> t.tp = tp; journalFinishEdit(t) }
                        }
                    }
                }
            }
        }
    }

    private fun journalFinishEdit(t: JournalTrade) {
        journalAsk("Edit • Position Size", "Optional", t.volume, true) { volume ->
            t.volume = volume
            journalAsk("Edit • Notes", "Optional", t.notes, true) { notes ->
                t.notes = notes
                val list = journalTrades()
                val i = list.indexOfFirst { it.id == t.id }
                if (i >= 0) list[i] = t
                saveJournalTrades(list)
                showJournal()
            }
        }
    }

    private fun journalDeleteTrade(t: JournalTrade) {
        AlertDialog.Builder(this).setTitle("Delete trade?")
            .setMessage(t.symbol + "  " + t.direction + "\nThis cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                saveJournalTrades(journalTrades().filterNot { it.id == t.id })
                showJournal()
            }.show()
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
