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
import android.view.WindowManager
import android.content.res.Configuration
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import com.google.android.gms.auth.api.identity.AuthorizationResult

class MainActivity : AppCompatActivity() {
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun lp(width: Int, height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(if (width > 0) dp(width) else width, if (height > 0) dp(height) else height)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GoogleSheetsApi.REQUEST_CODE) return
        val result = GoogleSheetsApi.handleAuthorizationResult(this, data)
        if (result?.accessToken?.isNullOrBlank() == false) {
            val id = prefs.getString("google_sheets_id", "") ?: ""
            if (id.isBlank()) googleSheetsCreateAfterAuth(result) else googleSheetsSyncAfterAuth(result)
        } else {
            val message = GoogleSheetsApi.lastError.ifBlank {
                if (resultCode == RESULT_CANCELED) "Google authorization was cancelled or denied." else "Google authorization failed."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            showGoogleSheetsSettings()
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
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(18), dp(10) + bars.top, dp(18), dp(8) + bars.bottom)
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
        includeFontPadding = true
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(line) }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(18f)
            setStroke(1, line)
        }
        elevation = dp(1f)
    }

    private fun smallButton(s: String, selected: Boolean = false) = TextView(this).apply {
        text = s
        textSize = 13f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setTextColor(if (selected) Color.WHITE else textColor)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (selected) accent else soft)
            cornerRadius = dp(22f)
        }
        minimumHeight = dp(44)
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
            setPadding(dp(0), dp(0), dp(0), dp(14))
        }
        scroll.addView(content)
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })

        // ALERT ACTIVE — deliberately above the clock and given enough height for all text.
        val status = panel().apply { setPadding(dp(24), dp(22), dp(24), dp(22)) }
        val statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val enabled = prefs.getBoolean("enabled", true)
        statusRow.addView(text("●", 22f, if (enabled) green else red).apply {
            gravity = Gravity.CENTER
        }, lp(42, 84))
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
        ).apply { setPadding(dp(0), dp(5), dp(0), dp(0)) })
        statusRow.addView(statusTexts, lp(0, 72).apply { weight = 1f })
        val sw = Switch(this).apply {
            isChecked = enabled
            minWidth = dp(62)
            scaleX = 1.08f
            scaleY = 1.08f
        }
        statusRow.addView(sw, lp(76, 72))
        status.addView(statusRow)
        sw.setOnCheckedChangeListener { _, value ->
            prefs.edit().putBoolean("enabled", value).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
        content.addView(status, lp(-1, 138).apply {
            setMargins(dp(0), dp(8), dp(0), dp(12))
        })

        // Header is compact; the clock remains the visual focus.
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(2))
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("Candle Alert", 25f))
        titleBox.addView(text("Candle-close notifications", 12f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(0))
        })
        header.addView(titleBox, lp(-1, 58))
        content.addView(header)

        // The clock uses the available content width so it never clips against root padding.
        val clockSize = resources.displayMetrics.widthPixels - dp(36)
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
            setMargins(dp(0), dp(0), dp(0), dp(4))
        })

        // Next alert stays directly under the clock, but remains compact.
        val nextMini = panel().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val nextMiniRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val nextMiniTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nextMiniTexts.addView(text("NEXT ALERT", 10f, muted))
        countdownView = text("Calculating…", 20f, accent).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(0), dp(3), dp(0), dp(0))
        }
        nextMiniTexts.addView(countdownView)
        nextDetailsView = text("Checking schedule…", 11f, muted).apply {
            setPadding(dp(0), dp(2), dp(0), dp(0))
        }
        nextMiniTexts.addView(nextDetailsView)
        nextMiniRow.addView(nextMiniTexts, lp(0, 66).apply { weight = 1f })
        nextMini.addView(nextMiniRow)
        content.addView(nextMini, lp(-1, 82).apply {
            setMargins(dp(0), dp(2), dp(0), dp(18))
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
            setPadding(dp(2), dp(0), dp(0), dp(8))
        })

        fun quickControl(labelText: String, value: String, click: () -> Unit): LinearLayout {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(20), dp(22), dp(20))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dp(18f)
                    setStroke(1, line)
                }
                elevation = dp(1f)
                minimumHeight = dp(118)
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
                setPadding(dp(0), dp(7), dp(0), dp(0))
            })
            box.addView(texts, lp(0, 88).apply { weight = 1f })
            box.addView(text("›", 30f, muted).apply { gravity = Gravity.CENTER })
            return box
        }

        val sleepSummary = prefs.getString("quiet", "00:00-07:30") ?: "00:00-07:30"
        content.addView(quickControl("TIMEFRAME", tfLabel) { chooseTf() },
            lp(-1, 118).apply { setMargins(dp(0), dp(0), dp(0), dp(12)) })
        content.addView(quickControl("MARKET", market) { chooseMarket() },
            lp(-1, 118).apply { setMargins(dp(0), dp(0), dp(0), dp(12)) })
        content.addView(quickControl("ALERT", timingSummary()) { chooseTiming() },
            lp(-1, 118).apply { setMargins(dp(0), dp(0), dp(0), dp(12)) })
        content.addView(quickControl("SLEEP HOURS", sleepSummary) { editQuiet() },
            lp(-1, 118).apply { setMargins(dp(0), dp(0), dp(0), dp(20)) })

        val navHost = LinearLayout(this).apply { setPadding(dp(18), dp(0), dp(18), dp(8)) }
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
        if (prefs.contains("alert_offset_minutes")) {
            val value = prefs.getInt("alert_offset_minutes", 0)
            return when {
                value > 0 -> "+" + value + " min • before close"
                value < 0 -> value.toString() + " min • after close"
                else -> "0 min • at close"
            }
        }
        val mode = prefs.getInt("mode", 1)
        val off = prefs.getInt("offset", 0)
        if (mode == 1 || off == 0) return "0 min • at close"
        return (if (mode == 0) "+" else "-") + formatOffset(off)
    }

    private fun formatOffset(s: Int): String = when (s) {
        10 -> "10s"; 30 -> "30s"; 45 -> "45s"; 60 -> "1m"; 120 -> "2m"; 180 -> "3m"; 300 -> "5m"
        else -> s.toString() + "s"
    }

    private fun addBottom(root: LinearLayout, active: String) {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp(24f)
                setStroke(1, line)
            }
            elevation = dp(2f)
        }
        val items = listOf("Home" to android.R.drawable.ic_menu_view, "Journal" to android.R.drawable.ic_menu_edit, "Settings" to android.R.drawable.ic_menu_preferences)
        items.forEach { pair ->
            val name = pair.first
            val selected = active == name.lowercase()
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(7), dp(8), dp(7))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(235, 243, 255) else cardColor)
                    cornerRadius = dp(18f)
                }
                minimumHeight = dp(88)
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
                setPadding(dp(4), dp(3), dp(4), dp(1))
            }
            item.addView(icon, lp(50, 44))
            item.addView(text(name, 13f, if (selected) accent else muted).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(0), dp(3), dp(0), dp(0))
            })
            nav.addView(item, lp(0, 90).apply { weight = 1f; setMargins(dp(5), dp(0), dp(5), dp(0)) })
        }
        root.addView(nav, lp(-1, 100))
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

    private fun journalDuration(start: Long, end: Long): String {
        val total = ((end - start).coerceAtLeast(0L)) / 1000L
        val d = total / 86400L
        val h = (total % 86400L) / 3600L
        val m = (total % 3600L) / 60L
        val sec = total % 60L
        return when {
            d > 0 -> String.format(Locale.getDefault(), "%dd %02dh %02dm", d, h, m)
            h > 0 -> String.format(Locale.getDefault(), "%dh %02dm %02ds", h, m, sec)
            else -> String.format(Locale.getDefault(), "%dm %02ds", m, sec)
        }
    }

    private fun journalDate(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(ms))

    private fun editAccountAmount(title: String, key: String, additive: Boolean) {
        val current = prefs.getString(key, "")?.toDoubleOrNull() ?: 0.0
        val e = EditText(this).apply {
            setText(if (current == 0.0) "" else String.format(Locale.US, "%.2f", current))
            hint = "Amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            textSize = 18f
        }
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = e.text.toString().trim().toDoubleOrNull()
                if (value == null || value < 0.0) {
                    Toast.makeText(this, "Enter a valid non-negative amount.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val stored = if (additive) current + value else value
                prefs.edit().putString(key, stored.toString()).apply()
                googleSheetsSyncIfConnected()
                showJournal()
            }.show()
    }

    private fun resetAccountBalance() {
        AlertDialog.Builder(this).setTitle("Reset account balance?")
            .setMessage("This resets Initial Balance, Deposits and Withdrawals to zero. Journal trades are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                prefs.edit().putString("account_initial", "0").putString("account_deposits", "0").putString("account_withdrawals", "0").apply()
                googleSheetsSyncIfConnected()
                showJournal()
            }.show()
    }

    private fun googleSheetsSyncIfConnected() {
        val id = prefs.getString("google_sheets_id", "") ?: return
        if (id.isBlank()) return
        GoogleSheetsApi.requestAuthorization(this, false) { result -> googleSheetsSyncAfterAuth(result) }
    }

    private fun googleSheetsSyncAfterAuth(result: AuthorizationResult) {
        val token = result.accessToken ?: return
        val id = prefs.getString("google_sheets_id", "") ?: return
        if (id.isBlank()) return
        val trades = journalTrades()
        val initial = prefs.getString("account_initial", "0") ?: "0"
        val deposits = prefs.getString("account_deposits", "0") ?: "0"
        val withdrawals = prefs.getString("account_withdrawals", "0") ?: "0"
        Thread {
            val ok = GoogleSheetsApi.syncJournal(token, id, trades.map { t ->
                listOf(t.id.toString(), if (t.exitTime == null) "OPEN" else "CLOSED", t.symbol, t.direction,
                    t.entryTime.toString(), t.entry, t.sl, t.tp, t.volume, t.exitTime?.toString() ?: "",
                    if (t.exitTime != null) journalDuration(t.entryTime, t.exitTime!!) else "",
                    t.exit, t.pnl, t.exitReason, t.notes)
            }, initial, deposits, withdrawals)
            runOnUiThread {
                if (ok) {
                    prefs.edit().putBoolean("google_sheets_connected", true).putLong("google_sheets_last_sync", System.currentTimeMillis()).apply()
                    Toast.makeText(this, "Google Sheets synced successfully.", Toast.LENGTH_SHORT).show()
                    showGoogleSheetsSettings()
                } else {
                    prefs.edit().putBoolean("google_sheets_connected", false).apply()
                    val msg = GoogleSheetsApi.lastError.ifBlank { "Google Sheets sync failed." }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    showGoogleSheetsSettings()
                }
            }
        }.start()
    }

    private fun googleSheetsCreateAfterAuth(result: AuthorizationResult) {
        val token = result.accessToken ?: return
        Thread {
            val created = GoogleSheetsApi.createJournalSheet(token)
            runOnUiThread {
                if (created != null) {
                    prefs.edit().putString("google_sheets_id", created.id).putString("google_sheets_url", created.url)
                        .putBoolean("google_sheets_connected", true).apply()
                    googleSheetsSyncAfterAuth(result)
                    showGoogleSheetsSettings()
                } else {
                    prefs.edit().putBoolean("google_sheets_connected", false).apply()
                    val msg = GoogleSheetsApi.lastError.ifBlank { "Could not create the Google Sheet." }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    showGoogleSheetsSettings()
                }
            }
        }.start()
    }

    private fun showGoogleSheetsSettings() {
        val root = base()
        root.addView(text("Google Sheets", 28f))
        root.addView(text("Keep your Journal in your personal Google Sheet so the data survives app replacement or deletion.", 14f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(14))
        })
        val connected = prefs.getBoolean("google_sheets_connected", false)
        val id = prefs.getString("google_sheets_id", "") ?: ""
        val url = prefs.getString("google_sheets_url", "") ?: ""
        val status = panel().apply { setPadding(dp(18), dp(16), dp(18), dp(16)) }
        status.addView(text(if (connected) "●  Connected" else "○  Not connected", 18f, if (connected) green else muted).apply { typeface = Typeface.DEFAULT_BOLD })
        status.addView(text(if (id.isBlank()) "No Journal Sheet selected." else "Candle Alert Journal is linked.", 13f, muted).apply { setPadding(dp(0), dp(5), dp(0), dp(0)) })
        root.addView(status, lp(-1, 94).apply { setMargins(dp(0), dp(0), dp(0), dp(12)) })
        root.addView(smallButton(if (connected) "Google Account  •  Connected" else "Connect Google Account", true).apply {
            setOnClickListener { googleSheetsConnect() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        root.addView(smallButton("Create New Personal Journal Sheet").apply {
            setOnClickListener { googleSheetsConnect(createIfMissing = true) }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        root.addView(smallButton("Use Existing Sheet ID / URL").apply {
            setOnClickListener { editGoogleSheetId() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        root.addView(smallButton("Sync Now").apply {
            setOnClickListener { googleSheetsSyncIfConnected() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        if (url.isNotBlank()) {
            root.addView(smallButton("Open Google Sheet", true).apply {
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        }
        root.addView(text("The sheet contains Trade ID, status, entry/exit data, P/L, reasons, notes and account balance.", 12f, muted).apply { setPadding(dp(2), dp(8), dp(2), dp(8)) })
        root.addView(Space(this), lp(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun googleSheetsConnect(createIfMissing: Boolean = false) {
        GoogleSheetsApi.requestAuthorization(this, true) { result ->
            val id = prefs.getString("google_sheets_id", "") ?: ""
            if (createIfMissing || id.isBlank()) googleSheetsCreateAfterAuth(result) else googleSheetsSyncAfterAuth(result)
        }
    }

    private fun editGoogleSheetId() {
        val e = EditText(this).apply { setText(prefs.getString("google_sheets_id", "")); hint = "Paste Spreadsheet ID or full URL"; textSize = 16f }
        AlertDialog.Builder(this).setTitle("Google Sheet ID / URL").setView(e)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save & Sync") { _, _ ->
                var value = e.text.toString().trim()
                if (value.contains("/d/")) value = value.substringAfter("/d/").substringBefore("/")
                if (value.isBlank()) Toast.makeText(this, "Enter a valid Spreadsheet ID or URL.", Toast.LENGTH_SHORT).show()
                else {
                    prefs.edit().putString("google_sheets_id", value).putBoolean("google_sheets_connected", true).apply()
                    googleSheetsConnect()
                }
            }.show()
    }

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

    private fun journalEditAsk(title: String, hint: String, initial: String = "", onDone: (String) -> Unit) {
        val e = EditText(this).apply {
            setText(initial)
            this.hint = hint
            textSize = 18f
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(e)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Keep") { _, _ -> onDone(initial) }
            .setPositiveButton("Save") { _, _ -> onDone(e.text.toString().trim()) }
            .show()
    }

    private fun showJournal() {
        val root = base()
        root.addView(text("Journal", 28f))
        root.addView(text("Record, review and close trades step by step.", 13f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(12))
        })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(12))
        }
        val trades = journalTrades()
        val open = trades.filter { it.exitTime == null }
        val closed = trades.filter { it.exitTime != null }

        content.addView(smallButton("+  Add Trade", true).apply {
            setOnClickListener { journalAddTrade() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(4), dp(0), dp(14)) })

        // Large performance summary: values are intentionally prominent and
        // profit/loss state is reinforced with explicit green/red typography.
        val closedWithPnl = closed.mapNotNull { it.pnl.toDoubleOrNull() }
        val wins = closedWithPnl.count { it > 0.0 }
        val losses = closedWithPnl.count { it < 0.0 }
        val grossProfit = closedWithPnl.filter { it > 0.0 }.sum()
        val grossLoss = kotlin.math.abs(closedWithPnl.filter { it < 0.0 }.sum())
        val netPnl = closedWithPnl.sum()
        val winRate = if (closedWithPnl.isNotEmpty()) wins.toDouble() / closedWithPnl.size * 100.0 else null
        val profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else null
        val plannedRRs = trades.mapNotNull { journalRR(it) }
        val avgPlannedRR = if (plannedRRs.isNotEmpty()) plannedRRs.average() else null
        val closedDurations = closed.mapNotNull { it.exitTime?.let { end -> (end - it.entryTime).coerceAtLeast(0L) } }
        val avgTradeDuration = if (closedDurations.isNotEmpty()) closedDurations.average().toLong() else null

        val summary = panel().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
        summary.addView(text("PERFORMANCE SUMMARY", 13f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(0), dp(0), dp(0), dp(14))
        })
        fun summaryRow(leftLabel: String, leftValue: String, leftColor: Int = textColor,
                       rightLabel: String, rightValue: String, rightColor: Int = textColor) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(0), dp(5), dp(0), dp(5))
            }
            fun cell(label: String, value: String, valueColor: Int): LinearLayout {
                return LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(0), dp(2), dp(10), dp(2))
                    addView(text(label, 12f, muted))
                    addView(text(value, 20f, valueColor).apply {
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(0), dp(5), dp(0), dp(0))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }
            row.addView(cell(leftLabel, leftValue, leftColor), lp(0, 70).apply { weight = 1f })
            row.addView(cell(rightLabel, rightValue, rightColor), lp(0, 70).apply { weight = 1f })
            summary.addView(row)
        }
        val netColor = when {
            closedWithPnl.isEmpty() -> muted
            netPnl > 0.0 -> green
            netPnl < 0.0 -> red
            else -> muted
        }
        val pfColor = when {
            profitFactor == null && grossProfit <= 0.0 -> muted
            (profitFactor ?: 0.0) >= 1.0 || (grossProfit > 0.0 && grossLoss == 0.0) -> green
            else -> red
        }
        val winColor = when {
            winRate == null -> muted
            winRate >= 50.0 -> green
            else -> red
        }
        summaryRow("Total Trades", trades.size.toString(), textColor, "Open", open.size.toString(), accent)
        summaryRow("Closed", closed.size.toString(), textColor, "Win Rate", winRate?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—", winColor)
        summaryRow("Net P/L", if (closedWithPnl.isNotEmpty()) String.format(Locale.US, "%.2f", netPnl) else "—", netColor,
            "Profit Factor", profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: if (grossProfit > 0.0 && grossLoss == 0.0) "∞" else "—", pfColor)
        summaryRow("Wins", wins.toString(), if (wins > 0) green else muted, "Losses", losses.toString(), if (losses > 0) red else muted)
        summaryRow("Avg Planned R:R", avgPlannedRR?.let { "1 : " + String.format(Locale.US, "%.2f", it) } ?: "—", accent,
            "Avg Trade Duration", avgTradeDuration?.let { journalDuration(0L, it) } ?: "—", accent)
        summaryRow("Result", when {
                closedWithPnl.isEmpty() -> "No closed P/L"
                netPnl > 0.0 -> "PROFIT"
                netPnl < 0.0 -> "LOSS"
                else -> "BREAK-EVEN"
            }, netColor, "Duration", closedDurations.size.toString() + " closed", textColor)
        content.addView(summary, lp(-1, -2).apply { setMargins(dp(0), dp(0), dp(0), dp(12)) })

        val initialBalance = prefs.getString("account_initial", "")?.toDoubleOrNull() ?: 0.0
        val deposits = prefs.getString("account_deposits", "")?.toDoubleOrNull() ?: 0.0
        val withdrawals = prefs.getString("account_withdrawals", "")?.toDoubleOrNull() ?: 0.0
        val currentBalance = initialBalance + deposits - withdrawals + netPnl
        val balanceCard = panel().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
        balanceCard.addView(text("ACCOUNT BALANCE", 13f, muted).apply { typeface = Typeface.DEFAULT_BOLD })
        balanceCard.addView(text(String.format(Locale.US, "%.2f", currentBalance), 28f,
            when { currentBalance > 0.0 -> green; currentBalance < 0.0 -> red; else -> textColor }).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(0), dp(7), dp(0), dp(2))
        })
        balanceCard.addView(text(
            "Initial " + String.format(Locale.US, "%.2f", initialBalance) +
                "  •  Deposits " + String.format(Locale.US, "%.2f", deposits) +
                "  •  Withdrawals " + String.format(Locale.US, "%.2f", withdrawals),
            12f, muted
        ).apply { setPadding(dp(0), dp(0), dp(0), dp(14)) })
        val balanceActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun balanceAction(label: String, click: () -> Unit): TextView = smallButton(label).apply {
            textSize = 12f
            setOnClickListener { click() }
        }
        balanceActions.addView(balanceAction("Initial") { editAccountAmount("Initial Balance", "account_initial", false) },
            lp(0, 46).apply { weight = 1f; setMargins(dp(2), dp(0), dp(2), dp(0)) })
        balanceActions.addView(balanceAction("Deposit") { editAccountAmount("Deposit", "account_deposits", true) },
            lp(0, 46).apply { weight = 1f; setMargins(dp(2), dp(0), dp(2), dp(0)) })
        balanceActions.addView(balanceAction("Withdraw") { editAccountAmount("Withdrawal", "account_withdrawals", true) },
            lp(0, 46).apply { weight = 1f; setMargins(dp(2), dp(0), dp(2), dp(0)) })
        balanceActions.addView(balanceAction("Reset") { resetAccountBalance() },
            lp(0, 46).apply { weight = 1f; setMargins(dp(2), dp(0), dp(2), dp(0)) })
        balanceCard.addView(balanceActions)
        content.addView(balanceCard, lp(-1, -2).apply { setMargins(dp(0), dp(0), dp(0), dp(14)) })

        if (open.isNotEmpty()) {
            content.addView(text("OPEN TRADES", 11f, muted).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(2), dp(4), dp(0), dp(6))
            })
            open.forEach { journalTradeCard(content, it) }
        }
        if (closed.isNotEmpty()) {
            content.addView(text("CLOSED TRADES", 11f, muted).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(2), dp(18), dp(0), dp(6))
            })
            closed.forEach { journalTradeCard(content, it) }
        }
        if (trades.isEmpty()) {
            content.addView(panel().apply {
                gravity = Gravity.CENTER
                addView(text("No trades yet", 18f).apply { gravity = Gravity.CENTER })
                addView(text("Tap + Add Trade to start.", 13f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(0), dp(8), dp(0), dp(0))
                })
            }, lp(-1, 150).apply { setMargins(dp(0), dp(8), dp(0), dp(0)) })
        }

        scroll.addView(content)
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        val navHost = LinearLayout(this).apply { setPadding(dp(18), dp(0), dp(18), dp(8)) }
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
        title.addView(text("Entry " + journalDate(t.entryTime), 12f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
        if (t.exitTime != null) {
            title.addView(text("Duration " + journalDuration(t.entryTime, t.exitTime!!), 11f, accent).apply {
                setPadding(dp(0), dp(3), dp(0), dp(0))
            })
        }
        row.addView(title, lp(0, 56).apply { weight = 1f })
        row.addView(text(if (isOpen) "OPEN" else "CLOSED", 11f, if (isOpen) accent else muted).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        c.addView(row)
        c.addView(text(
            "Entry " + t.entry.ifBlank { "—" } +
                "   SL " + t.sl.ifBlank { "—" } +
                "   TP " + t.tp.ifBlank { "—" }, 12f, muted
        ).apply { setPadding(dp(0), dp(8), dp(0), dp(0)) })
        val rr = journalRR(t)
        if (rr != null) {
            c.addView(text("Planned R:R  1 : " + String.format(Locale.US, "%.2f", rr), 12f, accent).apply {
                setPadding(dp(0), dp(5), dp(0), dp(0))
            })
        }
        if (!isOpen) {
            c.addView(text(
                "Exit " + t.exit.ifBlank { "—" } + "   •   P/L " + t.pnl.ifBlank { "—" },
                13f, if ((t.pnl.toDoubleOrNull() ?: 0.0) >= 0) green else red
            ).apply { setPadding(dp(0), dp(5), dp(0), dp(0)) })
            if (t.exitReason.isNotBlank()) {
                c.addView(text("Reason: " + t.exitReason, 12f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
            }
        }
        val actions = LinearLayout(this).apply { gravity = Gravity.END; setPadding(dp(0), dp(10), dp(0), dp(0)) }
        actions.addView(smallButton("Edit").apply { setOnClickListener { journalEditTrade(t) } })
        if (isOpen) {
            actions.addView(smallButton("Close").apply { setOnClickListener { journalCloseTrade(t) } })
        }
        actions.addView(smallButton("Delete").apply { setOnClickListener { journalDeleteTrade(t) } })
        c.addView(actions)
        parent.addView(c, lp(-1, -2).apply { setMargins(dp(0), dp(4), dp(0), dp(6)) })
    }

    private fun journalPickDateTime(title: String, initial: Long, onPicked: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = initial }
        android.app.DatePickerDialog(
            this,
            { _, year, month, day ->
                android.app.TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val chosen = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.YEAR, year)
                            set(java.util.Calendar.MONTH, month)
                            set(java.util.Calendar.DAY_OF_MONTH, day)
                            set(java.util.Calendar.HOUR_OF_DAY, hour)
                            set(java.util.Calendar.MINUTE, minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        onPicked(chosen.timeInMillis)
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply { setTitle(title) }.show()
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
        var selected = now
        val button = smallButton("Entry time: " + journalDate(selected), true).apply {
            setOnClickListener {
                journalPickDateTime("Entry Time", selected) {
                    selected = it
                    text = "Entry time: " + journalDate(selected)
                }
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(8))
            addView(text("Default is the moment you register the trade. Change it if you are entering a trade later.", 13f, muted).apply {
                setPadding(dp(0), dp(0), dp(0), dp(12))
            })
            addView(button, lp(-1, 50))
        }
        AlertDialog.Builder(this).setTitle("3 / 8  •  Entry Time")
            .setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Next") { _, _ -> t.entryTime = selected; journalEntryStep(t) }.show()
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(8))
        }
        fun addReviewRow(label: String, value: String) {
            box.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(0), dp(7), dp(0), dp(7))
                addView(text(label, 14f, muted), lp(82, 34))
                addView(text(value, 16f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                }, lp(0, 34).apply { weight = 1f })
            })
        }
        addReviewRow("Symbol", t.symbol + "  " + t.direction)
        addReviewRow("Entry", t.entry)
        addReviewRow("SL", t.sl.ifBlank { "—" })
        addReviewRow("TP", t.tp.ifBlank { "—" })
        journalRR(t)?.let { addReviewRow("R:R", "1 : " + String.format(Locale.US, "%.2f", it)) }
        box.addView(text("Save as OPEN trade?", 14f, muted).apply {
            setPadding(dp(0), dp(10), dp(0), dp(2))
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
        var selected = System.currentTimeMillis()
        val button = smallButton("Exit time: " + journalDate(selected), true).apply {
            setOnClickListener {
                journalPickDateTime("Exit Time", selected) {
                    selected = it
                    text = "Exit time: " + journalDate(selected)
                }
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(8))
            addView(text("Default is the moment you register the close. Change it if you are entering the trade closure later.", 13f, muted).apply {
                setPadding(dp(0), dp(0), dp(0), dp(12))
            })
            addView(button, lp(-1, 50))
        }
        AlertDialog.Builder(this).setTitle("2 / 5  •  Exit Time")
            .setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Next") { _, _ -> t.exitTime = selected; journalPnlStep(t) }.show()
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
                googleSheetsSyncIfConnected()
                showJournal()
            }.show()
    }

    private fun journalEditTrade(t: JournalTrade) {
        journalEditAsk("Edit • Symbol", "e.g. XAUUSD", t.symbol) {
            t.symbol = it.uppercase(Locale.getDefault())
            journalEditDirectionStep(t)
        }
    }

    private fun journalEditDirectionStep(t: JournalTrade) {
        val items = arrayOf("BUY", "SELL")
        val selected = if (t.direction == "SELL") 1 else 0
        AlertDialog.Builder(this).setTitle("Edit • Direction")
            .setSingleChoiceItems(items, selected, null)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { dialog, _ ->
                val listView = (dialog as AlertDialog).listView
                val checked = listView.checkedItemPosition
                if (checked >= 0) t.direction = items[checked]
                journalEditEntryTimeStep(t)
            }.show()
    }

    private fun journalEditEntryTimeStep(t: JournalTrade) {
        journalPickDateTime("Edit • Entry Time", t.entryTime) {
            t.entryTime = it
            journalEditEntryStep(t)
        }
    }

    private fun journalEditEntryStep(t: JournalTrade) {
        journalEditAsk("Edit • Entry Price", "Entry price", t.entry) { p ->
            if (p.toDoubleOrNull() == null) {
                Toast.makeText(this, "Invalid price.", Toast.LENGTH_SHORT).show()
                journalEditEntryStep(t)
            } else {
                t.entry = p
                journalEditSLStep(t)
            }
        }
    }

    private fun journalEditSLStep(t: JournalTrade) {
        journalEditAsk("Edit • Stop Loss", "Optional", t.sl) { sl ->
            t.sl = sl
            journalEditTPStep(t)
        }
    }

    private fun journalEditTPStep(t: JournalTrade) {
        val suggested = journalSuggestedTP(t)
        if (suggested == null) {
            journalEditAsk("Edit • Take Profit", "Optional", t.tp) { tp ->
                t.tp = tp
                journalEditVolumeStep(t)
            }
            return
        }
        val input = EditText(this).apply {
            setText(t.tp.ifBlank { String.format(Locale.US, "%.5f", suggested) })
            selectAll()
            textSize = 18f
        }
        AlertDialog.Builder(this)
            .setTitle("Edit • Take Profit")
            .setMessage("Suggested RR 2:1 TP: " + String.format(Locale.US, "%.5f", suggested))
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Keep") { _, _ -> journalEditVolumeStep(t) }
            .setPositiveButton("Save TP") { _, _ ->
                t.tp = input.text.toString().trim()
                journalEditVolumeStep(t)
            }.show()
    }

    private fun journalEditVolumeStep(t: JournalTrade) {
        journalEditAsk("Edit • Position Size", "Optional — e.g. 0.10 lot", t.volume) {
            t.volume = it
            journalEditNotesStep(t)
        }
    }

    private fun journalEditNotesStep(t: JournalTrade) {
        journalEditAsk("Edit • Notes", "Optional setup / reason / review", t.notes) {
            t.notes = it
            if (t.exitTime != null) journalEditExitPriceStep(t) else journalSaveEditedTrade(t)
        }
    }

    private fun journalEditExitPriceStep(t: JournalTrade) {
        journalEditAsk("Edit • Exit Price", "Exit price", t.exit) { exit ->
            if (exit.toDoubleOrNull() == null && exit.isNotBlank()) {
                Toast.makeText(this, "Invalid exit price.", Toast.LENGTH_SHORT).show()
                journalEditExitPriceStep(t)
            } else {
                t.exit = exit
                journalEditExitTimeStep(t)
            }
        }
    }

    private fun journalEditExitTimeStep(t: JournalTrade) {
        val current = t.exitTime ?: System.currentTimeMillis()
        val button = smallButton("Exit time: " + journalDate(current), true).apply {
            setOnClickListener {
                journalPickDateTime("Edit • Exit Time", current) { picked ->
                    t.exitTime = picked
                    journalSaveEditedTrade(t)
                }
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(8))
            addView(text("Change the actual time you closed the trade. The displayed duration will update automatically.", 13f, muted).apply {
                setPadding(dp(0), dp(0), dp(0), dp(12))
            })
            addView(button, lp(-1, 50))
        }
        AlertDialog.Builder(this).setTitle("Edit • Exit Time")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ -> journalSaveEditedTrade(t) }
            .show()
    }

    private fun journalEditPnlStep(t: JournalTrade) {
        val current = t.pnl
        journalEditAsk("Edit • Profit / Loss", "e.g. 125.50", current) { pnl ->
            t.pnl = pnl
            journalEditExitReasonStep(t)
        }
    }

    private fun journalEditExitReasonStep(t: JournalTrade) {
        val reasons = arrayOf("Take Profit", "Stop Loss", "Manual Close", "Signal Reversal", "Session End", "Other")
        val selected = reasons.indexOf(t.exitReason)
        AlertDialog.Builder(this).setTitle("Edit • Exit Reason")
            .setSingleChoiceItems(reasons, selected.coerceAtLeast(-1), null)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Keep") { _, _ -> journalEditClosingNotesStep(t) }
            .setPositiveButton("Save") { dialog, _ ->
                val listView = (dialog as AlertDialog).listView
                val checked = listView.checkedItemPosition
                if (checked >= 0) t.exitReason = reasons[checked]
                journalEditClosingNotesStep(t)
            }.show()
    }

    private fun journalEditClosingNotesStep(t: JournalTrade) {
        journalEditAsk("Edit • Closing Notes", "Closing notes", t.notes) { notes ->
            t.notes = notes
            journalSaveEditedTrade(t)
        }
    }

    private fun journalSaveEditedTrade(t: JournalTrade) {
        val list = journalTrades()
        val i = list.indexOfFirst { it.id == t.id }
        if (i >= 0) list[i] = t
        saveJournalTrades(list)
        googleSheetsSyncIfConnected()
        showJournal()
    }

    private fun journalDeleteTrade(t: JournalTrade) {
        AlertDialog.Builder(this).setTitle("Delete trade?")
            .setMessage(t.symbol + "  " + t.direction + "\nThis cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                saveJournalTrades(journalTrades().filterNot { it.id == t.id })
                googleSheetsSyncIfConnected()
                showJournal()
            }.show()
    }

    private fun showSettings() {
        val root = base()
        root.addView(text("Settings", 28f))
        root.addView(text("Configure how Candle Alert behaves", 14f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(12)) })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(0), dp(0), dp(0), dp(10)) }

        // Daily trading controls live on Home. Settings contains only behavior/system options.
        val options = listOf(
            "Open Market" to "Broker candle alignment start",
            "Theme" to "Light, dark, or system default",
            "Google Sheets Journal" to "Store Journal data in your personal Google Sheet",
            "Open App on Notification" to "Open your selected trading app",
            "Exact Alarm Permission" to "Allow precise background alerts"
        )

        options.forEach { pair ->
            val c = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            titles.addView(text(pair.first, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
            titles.addView(text(pair.second, 12f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
            row.addView(titles, lp(0, 64).apply { weight = 1f })
            row.addView(text("›", 28f, muted).apply { gravity = Gravity.CENTER })
            c.addView(row)
            c.setOnClickListener {
                when (pair.first) {
                    "Open Market" -> chooseOpenMarket()
                    "Theme" -> showThemeSettings()
                    "Google Sheets Journal" -> showGoogleSheetsSettings()
                    "Open App on Notification" -> chooseNotificationApp()
                    "Exact Alarm Permission" -> if (android.os.Build.VERSION.SDK_INT >= 31) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:" + packageName)
                        })
                    }
                }
            }
            content.addView(c, lp(-1, 92).apply { setMargins(dp(0), dp(5), dp(0), dp(5)) })
        }
        scroll.addView(content)
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun wheelDialog(title: String, values: List<String>, selected: Int, onSelected: (Int) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(4))
        }
        val picker = NumberPicker(this).apply {
            minValue = 0
            maxValue = values.lastIndex
            displayedValues = values.toTypedArray()
            value = selected.coerceIn(0, values.lastIndex)
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        box.addView(picker, lp(-1, 220))
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
        // Signed minute convention: + = before close, 0 = at close, - = after close.
        val items = listOf(
            "+2 min  •  before candle close",
            "+1 min  •  before candle close",
            "0 min   •  exactly at candle close",
            "-1 min  •  after candle close",
            "-2 min  •  after candle close",
            "10 sec  •  before close",
            "30 sec  •  before close",
            "45 sec  •  before close",
            "10 sec  •  after close",
            "30 sec  •  after close",
            "45 sec  •  after close"
        )
        val configs = listOf(
            0 to 120, 0 to 60, 1 to 0, 2 to 60, 2 to 120,
            0 to 10, 0 to 30, 0 to 45, 2 to 10, 2 to 30, 2 to 45
        )
        val current = configs.indexOf(prefs.getInt("mode", 1) to prefs.getInt("offset", 0)).let { if (it >= 0) it else 2 }
        wheelDialog("Alert Timing", items, current) { i ->
            val pair = configs[i]
            prefs.edit().putInt("mode", pair.first).putInt("offset", pair.second).remove("alert_offset_minutes").apply()
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
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(4), dp(10), dp(4)) }
        val hp = NumberPicker(this).apply { minValue=0; maxValue=23; value=h; wrapSelectorWheel=true; descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS }
        val mp = NumberPicker(this).apply { minValue=0; maxValue=59; value=m; wrapSelectorWheel=true; descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS }
        box.addView(hp, lp(0,220).apply{weight=1f})
        box.addView(text(":",28f).apply{gravity=Gravity.CENTER}, lp(36,220))
        box.addView(mp, lp(0,220).apply{weight=1f})
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
        root.addView(text("Select the Forex sessions used for alerts.", 14f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(12)) })
        val sessions = listOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        val selected = prefs.getStringSet("sessions", sessions.toSet())?.toMutableSet() ?: sessions.toMutableSet()
        sessions.forEach { s ->
            val c = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(s, 16f), lp(0, 58).apply { weight = 1f })
            val sw = Switch(this).apply { isChecked = selected.contains(s); minWidth = dp(52) }
            row.addView(sw, lp(56, 48))
            c.addView(row)
            sw.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(s) else selected.remove(s)
                prefs.edit().putStringSet("sessions", selected).apply()
                Scheduler.scheduleNext(this)
            }
            root.addView(c, lp(-1, 74).apply { setMargins(dp(0), dp(4), dp(0), dp(4)) })
        }
        root.addView(smallButton("Done", true).apply {
            setOnClickListener { showSettings() }
        }, lp(-1, 50).apply { setMargins(dp(0), dp(10), dp(0), dp(8)) })
        root.addView(Space(this), lp(1, 0).apply { weight = 1f })
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
        root.addView(text("Choose light, dark, or your phone's system theme.", 14f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(14)) })
        val modes = listOf("light" to "Light", "dark" to "Dark", "system" to "System default")
        modes.forEach { pair ->
            val mode = pair.first
            val c = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            titles.addView(text(pair.second, 17f).apply { typeface = Typeface.DEFAULT_BOLD })
            titles.addView(text(when(mode) {"light" -> "Always use light mode"; "dark" -> "Always use dark mode"; else -> "Match the phone's system theme"}, 12f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
            row.addView(titles, lp(0, 66).apply { weight = 1f })
            row.addView(text(if (prefs.getString("theme_mode", "light") == mode) "✓" else "", 23f, accent))
            c.addView(row)
            c.setOnClickListener { prefs.edit().putString("theme_mode", mode).apply(); applyNightModePreference(); showSettings() }
            root.addView(c, lp(-1, 84).apply { setMargins(dp(0), dp(5), dp(0), dp(5)) })
        }
        root.addView(Space(this), lp(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun chooseNotificationApp() {
        val root = base()
        root.addView(text("Notification App", 28f))
        root.addView(text("Choose which installed trading app opens when an alert arrives.", 14f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(12)) })
        val current = prefs.getString("notification_app_package", "") ?: ""
        val custom = prefs.getStringSet("custom_notification_apps", emptySet()) ?: emptySet()

        val none = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
        val nr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nr.addView(text("No app", 16f), lp(0, 58).apply { weight = 1f })
        nr.addView(text(if (current.isEmpty()) "✓" else "", 22f, accent))
        none.addView(nr)
        none.setOnClickListener { prefs.edit().remove("notification_app_package").remove("notification_app_label").apply(); showSettings() }
        root.addView(none, lp(-1, 74).apply { setMargins(dp(0), dp(4), dp(0), dp(10)) })

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }

        val words = listOf("metatrader","meta trader","tradingview","trading view","ctrader","ninjatrader","thinktrader","trading 212","ibkr","interactive brokers","etoro","binance","bybit","okx","kraken","coinbase","kucoin","bitget","mexc","deriv","exness","xm trading","alpari","fxtm","oanda","ic markets","pepperstone","eightcap","admirals","tickmill","fbs","roboforex","fxpro","xtb","capital.com","trading","trade","trader","broker","forex","crypto","exchange","invest")
        val tradingApps = allApps.filter { pair -> words.any { pair.second.lowercase().contains(it) } }
            .sortedWith(compareBy({ !(it.second.contains("MetaTrader", true) || it.second.contains("TradingView", true)) }, { it.second.lowercase() }))

        root.addView(text("TRADING APPS", 11f, muted).apply { setPadding(dp(2), dp(4), dp(0), dp(5)) })
        tradingApps.forEach { addNotificationAppRow(root, it.first, it.second, current) }
        root.addView(text("MY ADDED APPS", 11f, muted).apply { setPadding(dp(2), dp(12), dp(0), dp(5)) })
        allApps.filter { custom.contains(it.first) }.sortedBy { it.second.lowercase() }.forEach { addNotificationAppRow(root, it.first, it.second, current) }

        root.addView(panel().apply {
            val r = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(text("＋  Add installed app", 16f), lp(0, 58).apply { weight = 1f })
            r.addView(text("›", 28f, muted))
            addView(r)
            setOnClickListener { showInstalledAppsPicker() }
        }, lp(-1, 74).apply { setMargins(dp(0), dp(10), dp(0), dp(6)) })

        root.addView(Space(this), lp(1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun addNotificationAppRow(root: LinearLayout, pkg: String, name: String, current: String) {
        val c = panel().apply { setPadding(dp(16), dp(7), dp(14), dp(7)) }
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r.addView(text(name, 16f), lp(0, 58).apply { weight = 1f })
        if (pkg == current) r.addView(text("✓", 22f, accent))
        c.addView(r)
        c.setOnClickListener {
            prefs.edit().putString("notification_app_package", pkg).putString("notification_app_label", name).apply()
            showSettings()
        }
        root.addView(c, lp(-1, 74).apply { setMargins(dp(0), dp(4), dp(0), dp(4)) })
    }

    private fun showInstalledAppsPicker() {
        val root = base()
        root.addView(text("Add Installed App", 28f))
        root.addView(text("Select an app to add it to the notification list.", 14f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(12)) })
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val custom = prefs.getStringSet("custom_notification_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        val apps = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(0), dp(0), dp(0), dp(12)) }
        apps.forEach { pair ->
            val c = panel().apply { setPadding(dp(16), dp(7), dp(14), dp(7)) }
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(text(pair.second, 16f), lp(0, 58).apply { weight = 1f })
            r.addView(text(if (custom.contains(pair.first)) "✓" else "＋", 22f, if (custom.contains(pair.first)) accent else muted))
            c.addView(r)
            c.setOnClickListener {
                if (!custom.add(pair.first)) custom.remove(pair.first)
                prefs.edit().putStringSet("custom_notification_apps", custom).apply()
                showInstalledAppsPicker()
            }
            content.addView(c, lp(-1, 74).apply { setMargins(dp(0), dp(4), dp(0), dp(4)) })
        }
        scroll.addView(content)
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }
}
