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
    private val bg get() = if (darkMode) Color.rgb(17, 27, 29) else Color.rgb(245, 244, 239)
    private val cardColor get() = if (darkMode) Color.rgb(28, 34, 42) else Color.WHITE
    private val soft get() = if (darkMode) Color.rgb(30, 49, 48) else Color.rgb(229, 240, 235)
    private val accent get() = Color.parseColor(prefs.getString("theme_accent", "#176B63") ?: "#176B63")
    private val green = Color.rgb(22, 132, 96)
    private val red = Color.rgb(194, 76, 72)
    private val textColor get() = if (darkMode) Color.rgb(237, 243, 240) else Color.rgb(30, 42, 40)
    private val muted get() = if (darkMode) Color.rgb(161, 179, 174) else Color.rgb(112, 124, 120)
    private val line get() = if (darkMode) Color.rgb(49, 69, 66) else Color.rgb(226, 230, 224)

    private val handler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var nextDetailsView: TextView? = null
    private var computerClockView: TextView? = null
    private var candleCountdownView: TextView? = null
    private var ticker: Runnable? = null
    private data class MarketCardRef(
        val session: TradingSessions.Session,
        val timeView: TextView,
        val statusView: TextView,
        val alertView: TextView,
        val card: View,
        var lastOpen: Boolean? = null,
        var lastAlertSelected: Boolean? = null
    )
    private var marketCardRefs: List<MarketCardRef> = emptyList()
    private var settingsVisible = false
    private var settingsSubpageVisible = false

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            settingsSubpageVisible -> showSettings()
            settingsVisible -> showHome()
            else -> super.onBackPressed()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // Activity context is only attached after the constructor has completed.
        // Load preference-backed fields here, not in property initializers.
        economicDays = prefs.getInt("news_filter_days", 7).let { saved -> if (saved in listOf(1, 7, 14, 30)) saved else 7 }
        economicImpact = prefs.getString("news_filter_impact", "All impact") ?: "All impact"
        economicCountry = prefs.getString("news_filter_country", "All countries") ?: "All countries"
        applyNightModePreference()
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
        if (!prefs.contains("theme_accent")) e.putString("theme_accent", "#176B63")
        val oldAccent = prefs.getString("theme_accent", "")
        if (oldAccent == "#1677FF" || oldAccent == "#B28A2A") e.putString("theme_accent", "#176B63")
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
            if (id.isBlank()) googleSheetsFindOrCreateAfterAuth(result) else googleSheetsSyncAfterAuth(result)
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
            cornerRadius = dp(20f)
            setStroke(1, line)
        }
        elevation = dp(3f)
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
        settingsVisible = false
        settingsSubpageVisible = false
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

        // Compact alert toggle: status remains clear without taking vertical space.
        val status = panel().apply { setPadding(dp(12), dp(5), dp(10), dp(5)) }
        val statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val enabled = prefs.getBoolean("enabled", true)
        statusRow.addView(text("●", 16f, if (enabled) green else red).apply {
            gravity = Gravity.CENTER
        }, lp(24, 38))
        val statusText = text(if (enabled) "Alerts on" else "Alerts off", 15f).apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        statusRow.addView(statusText, lp(0, -2).apply { weight = 1f })
        val sw = Switch(this).apply {
            isChecked = enabled
            minWidth = dp(50)
            scaleX = 0.88f
            scaleY = 0.88f
        }
        statusRow.addView(sw, lp(54, 40))
        status.addView(statusRow)
        sw.setOnCheckedChangeListener { _, value ->
            prefs.edit().putBoolean("enabled", value).apply()
            Scheduler.scheduleNext(this)
            showHome()
        }
        content.addView(status, lp(-1, -2).apply {
            setMargins(dp(0), dp(2), dp(0), dp(6))
        })

        // The clock uses the available content width so it never clips against root padding.
        val clockSize = minOf(resources.displayMetrics.widthPixels - dp(36), dp(420))
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

        // Compact three-column status strip: device time, candle timer, and next alert.
        val nextMini = panel().apply { setPadding(dp(8), dp(9), dp(8), dp(9)) }
        val metricsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun metric(title: String, value: TextView): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(2), dp(3), dp(2))
                addView(text(title, 9f, muted).apply { gravity = Gravity.CENTER; maxLines = 1 })
                addView(value, lp(-1, -2).apply { topMargin = dp(4) })
            }
        }
        computerClockView = text("--:--:--", 13f, textColor).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
        candleCountdownView = text("--:--", 13f, accent).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
        countdownView = text("Calculating…", 13f, accent).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
        val displayZone = TradingSessions.displayZone(this)
        val clockTitle = if (prefs.getString("clock_timezone", null).isNullOrBlank()) "LOCAL TIME" else "TIME • ${displayZone.id.substringAfterLast('/')}"
        metricsRow.addView(metric(clockTitle, computerClockView!!), lp(0, -2).apply { weight = 1f })
        val separator1 = divider()
        metricsRow.addView(separator1, lp(1, 34))
        metricsRow.addView(metric("CANDLE", candleCountdownView!!), lp(0, -2).apply { weight = 1f })
        val separator2 = divider()
        metricsRow.addView(separator2, lp(1, 34))
        metricsRow.addView(metric("NEXT ALERT", countdownView!!), lp(0, -2).apply { weight = 1.15f })
        nextMini.addView(metricsRow)
        nextDetailsView = text("Checking schedule…", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(0), dp(6), dp(0), dp(0))
            maxLines = 1
        }
        nextMini.addView(nextDetailsView)
        content.addView(nextMini, lp(-1, -2).apply {
            setMargins(dp(0), dp(2), dp(0), dp(12))
        })

        // Live market panel: actual city-local time, weekday open/closed state, and
        // alert selection are presented separately so these states cannot be confused.
        val marketHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        marketHeader.addView(text("MARKET SESSIONS", 11f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }, lp(0, -2).apply { weight = 1f })
        marketHeader.addView(text("LIVE · LOCAL TIME", 8f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        content.addView(marketHeader)

        val marketRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val marketRefs = mutableListOf<MarketCardRef>()
        val selectedSessionNames = prefs.getStringSet(
            "sessions",
            setOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        ) ?: emptySet()
        fun marketCode(name: String) = when (name) {
            "Sydney" -> "SYD"
            "Tokyo" -> "TYO"
            "Frankfurt" -> "FRA"
            "London" -> "LDN"
            "New York" -> "NY"
            else -> name.take(3).uppercase(Locale.ROOT)
        }
        TradingSessions.sessions.forEach { session ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(3))
                minimumHeight = dp(69)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dp(11f)
                    setStroke(dp(1), line)
                }
            }
            val code = text(marketCode(session.name), 8.5f, session.color).apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
            }
            val localTime = text("--:--", 9f, textColor).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
                setPadding(0, dp(4), 0, dp(2))
            }
            val openState = text("CLOSED", 6.3f, muted).apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
            }
            val alertState = text(
                if (selectedSessionNames.contains(session.name)) "ALERT ON" else "ALERT OFF",
                5.8f,
                if (selectedSessionNames.contains(session.name)) accent else muted
            ).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            }
            card.addView(code, lp(-1, dp(12)))
            card.addView(localTime, lp(-1, dp(15)))
            card.addView(openState, lp(-1, dp(10)))
            card.addView(alertState, lp(-1, dp(9)))
            marketRow.addView(card, lp(0, 0).apply {
                weight = 1f
                setMargins(dp(2), 0, dp(2), 0)
            })
            marketRefs.add(MarketCardRef(session, localTime, openState, alertState, card))
        }
        marketCardRefs = marketRefs
        content.addView(marketRow, lp(-1, -2).apply {
            setMargins(0, 0, 0, dp(13))
        })

        val tf = prefs.getInt("tf", 60)
        val tfLabel = if (tf >= 60) (tf / 60).toString() + "H" else tf.toString() + "M"
        val market = when (prefs.getInt("market", 0)) {
            0 -> "Forex"
            1 -> "Crypto"
            else -> "Forex + Crypto"
        }

        content.addView(text("QUICK CONTROLS", 11f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(0), dp(0), dp(6))
        })

        fun quickControl(labelText: String, value: String, click: () -> Unit): LinearLayout {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(8), dp(7), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dp(15f)
                    setStroke(1, line)
                }
                elevation = dp(1f)
                minimumHeight = dp(66)
                isClickable = true
                setOnClickListener { click() }
            }
            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            texts.addView(text(labelText, 9.5f, muted).apply {
                maxLines = 1
                setSingleLine(true)
            })
            texts.addView(text(value, 13f).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(0), dp(3), dp(0), dp(0))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val quickKind = when (labelText) {
                "TIMEFRAME" -> "time"
                "MARKET" -> "market"
                "ALERT" -> "forex"
                "SLEEP HOURS" -> "time"
                else -> "market"
            }
            box.addView(TradingIllustrationView(quickKind, accent), lp(28, 28).apply { setMargins(0, 0, dp(7), 0) })
            box.addView(texts, lp(0, -2).apply { weight = 1f })
            return box
        }

        fun addQuickRow(left: View, right: View) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(left, lp(0, 68).apply { weight = 1f; setMargins(0, 0, dp(4), 0) })
            row.addView(right, lp(0, 68).apply { weight = 1f; setMargins(dp(4), 0, 0, 0) })
            content.addView(row, lp(-1, -2).apply { setMargins(0, 0, 0, dp(7)) })
        }

        val sleepSummary = prefs.getString("quiet", "00:00-07:30") ?: "00:00-07:30"
        addQuickRow(
            quickControl("TIMEFRAME", tfLabel) { chooseTf() },
            quickControl("MARKET", market) { chooseMarket() }
        )
        addQuickRow(
            quickControl("ALERT", timingSummary()) { chooseTiming() },
            quickControl("SLEEP HOURS", sleepSummary) { editQuiet() }
        )

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
                val instant = java.time.ZonedDateTime.now(TradingSessions.displayZone(this@MainActivity))
                computerClockView?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", instant.hour, instant.minute, instant.second)

                val nowInstant = instant.toInstant()
                val selectedMarkets = prefs.getStringSet(
                    "sessions",
                    setOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
                ) ?: emptySet()
                marketCardRefs.forEach { ref ->
                    val marketLocalTime = nowInstant.atZone(java.time.ZoneId.of(ref.session.zone))
                    val localText = String.format(Locale.US, "%02d:%02d", marketLocalTime.hour, marketLocalTime.minute)
                    if (ref.timeView.text != localText) ref.timeView.text = localText

                    val isOpen = TradingSessions.isOpen(ref.session.name, nowInstant)
                    if (ref.lastOpen != isOpen) {
                        ref.lastOpen = isOpen
                        ref.statusView.text = if (isOpen) "OPEN" else "CLOSED"
                        ref.statusView.setTextColor(if (isOpen) green else muted)
                        ref.card.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(cardColor)
                            cornerRadius = dp(11f)
                            setStroke(dp(1), if (isOpen) ref.session.color else line)
                        }
                        ref.card.elevation = if (isOpen) dp(2f) else dp(0f)
                    }

                    val alertsSelected = selectedMarkets.contains(ref.session.name)
                    if (ref.lastAlertSelected != alertsSelected) {
                        ref.lastAlertSelected = alertsSelected
                        ref.alertView.text = if (alertsSelected) "ALERT ON" else "ALERT OFF"
                        ref.alertView.setTextColor(if (alertsSelected) accent else muted)
                    }
                    ref.card.contentDescription =
                        "${ref.session.name}; local time $localText; ${if (isOpen) "open" else "closed"}; ${if (alertsSelected) "alerts enabled" else "alerts disabled"}"
                }

                val tfMinutes = prefs.getInt("tf", 60).coerceAtLeast(1)
                val marketOpenParts = (prefs.getString("open_market", "00:00") ?: "00:00").split(":")
                val openHour = marketOpenParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
                val openMinute = marketOpenParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                var anchor = instant.toLocalDate().atTime(openHour, openMinute).atZone(instant.zone)
                if (instant.isBefore(anchor)) anchor = anchor.minusDays(1)
                val periodSeconds = tfMinutes * 60L
                val elapsedSeconds = java.time.Duration.between(anchor, instant).seconds.coerceAtLeast(0L)
                val candleRemaining = (periodSeconds - (elapsedSeconds % periodSeconds)).coerceIn(0L, periodSeconds)
                candleCountdownView?.text = String.format(Locale.getDefault(), "%02d:%02d", candleRemaining / 60L, candleRemaining % 60L)
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

    private fun houseDrawable(tint: Int): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = dp(2f)
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        override fun draw(canvas: android.graphics.Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val p = android.graphics.Path().apply {
                moveTo(w * .16f, h * .47f); lineTo(w * .50f, h * .18f); lineTo(w * .84f, h * .47f)
                moveTo(w * .25f, h * .43f); lineTo(w * .25f, h * .84f); lineTo(w * .75f, h * .84f); lineTo(w * .75f, h * .43f)
                moveTo(w * .44f, h * .84f); lineTo(w * .44f, h * .60f); lineTo(w * .56f, h * .60f); lineTo(w * .56f, h * .84f)
            }
            canvas.drawPath(p, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun addBottom(root: LinearLayout, active: String) {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp(24f)
                setStroke(1, line)
            }
            elevation = dp(2f)
        }
        val items = listOf("Home" to 0, "Journal" to android.R.drawable.ic_menu_edit, "News" to android.R.drawable.ic_menu_today, "Settings" to android.R.drawable.ic_menu_preferences)
        items.forEach { pair ->
            val name = pair.first
            val selected = active == name.lowercase()
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) Color.rgb(246, 237, 207) else cardColor)
                    cornerRadius = dp(18f)
                }
                minimumHeight = dp(54)
                setOnClickListener {
                    when (name) {
                        "Home" -> showHome()
                        "Journal" -> showJournal()
                        "News" -> showEconomicCalendar()
                        "Settings" -> showSettings()
                    }
                }
            }
            val icon = ImageView(this).apply {
                if (name == "Home") setImageDrawable(houseDrawable(if (selected) accent else muted)) else setImageResource(pair.second)
                setColorFilter(if (name == "Home") android.graphics.Color.TRANSPARENT else if (selected) accent else muted)
                setPadding(dp(4), dp(3), dp(4), dp(1))
            }
            item.addView(icon, lp(28, 28))
            item.addView(text(name, 13f, if (selected) accent else muted).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(0), dp(3), dp(0), dp(0))
            })
            nav.addView(item, lp(0, 58).apply { weight = 1f; setMargins(dp(3), dp(0), dp(3), dp(0)) })
        }
        root.addView(nav, lp(-1, 66))
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
        var entryNotes: String,
        var exitNotes: String = "",
        var swap: String = "0",
        var commission: String = "0"
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
                    o.optString("entryNotes", o.optString("notes", "")),
                    o.optString("exitNotes", ""),
                    o.optString("swap", "0"),
                    o.optString("commission", "0")
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
                put("entryNotes", t.entryNotes)
                put("exitNotes", t.exitNotes)
                put("swap", t.swap)
                put("commission", t.commission)
            })
        }
        prefs.edit().putString("journal_trades_v2", a.toString()).apply()
        // Automatically queue a cloud sync after every journal change.
        googleSheetsSyncIfConnected()
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
                    t.exit, t.pnl, t.swap, t.commission, t.exitReason, t.entryNotes, t.exitNotes,
                    String.format(Locale.US, "%.2f", (t.pnl.toDoubleOrNull() ?: 0.0) + (t.swap.toDoubleOrNull() ?: 0.0) - kotlin.math.abs(t.commission.toDoubleOrNull() ?: 0.0)))
            }, initial, deposits, withdrawals)
            runOnUiThread {
                if (ok) {
                    prefs.edit().putBoolean("google_sheets_connected", true).putLong("google_sheets_last_sync", System.currentTimeMillis()).apply()
                    // Keep the user on the current screen; automatic sync should be invisible.
                } else {
                    prefs.edit().putBoolean("google_sheets_connected", false).apply()
                    val msg = GoogleSheetsApi.lastError.ifBlank { "Google Sheets sync failed." }
                    // Do not interrupt journal entry; failure remains available in Sheets settings.
                }
            }
        }.start()
    }

    private fun googleSheetsFindOrCreateAfterAuth(result: AuthorizationResult) {
        val token = result.accessToken ?: return
        Thread {
            val lookup = GoogleSheetsApi.findJournalSheet(token)
            runOnUiThread {
                if (!lookup.checked) {
                    val msg = GoogleSheetsApi.lastError.ifBlank { "Could not check Google Drive for an existing journal." }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    showGoogleSheetsSettings()
                } else if (lookup.sheet != null) {
                    val existing = lookup.sheet
                    prefs.edit().putString("google_sheets_id", existing.id)
                        .putString("google_sheets_url", existing.url)
                        .putBoolean("google_sheets_connected", true).apply()
                    Toast.makeText(this, "Existing Candle Alert Journal found. Reusing it.", Toast.LENGTH_LONG).show()
                    googleSheetsSyncAfterAuth(result)
                    showGoogleSheetsSettings()
                } else {
                    googleSheetsCreateAfterAuth(result)
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
        settingsVisible = true
        settingsSubpageVisible = true
        val root = base()
        root.addView(text("Google Sheets", 23f))
        root.addView(text("Keep your Journal in your personal Google Sheet so the data survives app replacement or deletion.", 14f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(14))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(12))
        }
        scroll.addView(content)

        val connected = prefs.getBoolean("google_sheets_connected", false)
        val id = prefs.getString("google_sheets_id", "") ?: ""
        val url = prefs.getString("google_sheets_url", "") ?: ""
        val status = panel().apply { setPadding(dp(18), dp(16), dp(18), dp(16)) }
        status.addView(text(if (connected) "●  Connected" else "○  Not connected", 18f, if (connected) green else muted).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        status.addView(text(if (id.isBlank()) "No Journal Sheet selected." else "Candle Alert Journal is linked.", 13f, muted).apply {
            setPadding(dp(0), dp(5), dp(0), dp(0))
        })
        content.addView(status, lp(-1, -2).apply { setMargins(dp(0), dp(0), dp(0), dp(12)) })
        content.addView(smallButton(if (connected) "Google Account  •  Connected" else "Connect Google Account", true).apply {
            setOnClickListener { googleSheetsConnect() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        content.addView(smallButton("Create New Personal Journal Sheet").apply {
            setOnClickListener { googleSheetsConnect(createIfMissing = true) }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        content.addView(smallButton("Use Existing Sheet ID / URL").apply {
            setOnClickListener { editGoogleSheetId() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        content.addView(smallButton("Sync Now").apply {
            setOnClickListener { googleSheetsSyncIfConnected() }
        }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        if (url.isNotBlank()) {
            content.addView(smallButton("Open Google Sheet", true).apply {
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }, lp(-1, 54).apply { setMargins(dp(0), dp(0), dp(0), dp(8)) })
        }
        content.addView(text("The sheet contains Trade ID, status, entry/exit data, P/L, reasons, notes and account balance.", 12f, muted).apply {
            setPadding(dp(2), dp(8), dp(2), dp(8))
        })

        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun googleSheetsConnect(createIfMissing: Boolean = false) {
        Toast.makeText(this, "Connecting to Google…", Toast.LENGTH_SHORT).show()
        GoogleSheetsApi.requestAuthorization(this, true) { result ->
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                Toast.makeText(this, GoogleSheetsApi.lastError.ifBlank { "Google authorization returned no access token." }, Toast.LENGTH_LONG).show()
                showGoogleSheetsSettings()
                return@requestAuthorization
            }
            val id = prefs.getString("google_sheets_id", "") ?: ""
            if (createIfMissing) googleSheetsCreateAfterAuth(result)
            else if (id.isBlank()) googleSheetsFindOrCreateAfterAuth(result)
            else googleSheetsSyncAfterAuth(result)
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
        root.addView(text("Journal", 23f))
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
        val closedWithPnl = closed.mapNotNull { it.pnl.toDoubleOrNull()?.plus(it.swap.toDoubleOrNull() ?: 0.0)?.minus(kotlin.math.abs(it.commission.toDoubleOrNull() ?: 0.0)) }
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
        val balanceBreakdown = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(0), dp(2), dp(0), dp(12))
        }
        fun balanceMetric(label: String, amount: Double): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(4), dp(0))
            addView(text(label, 10f, muted).apply {
                maxLines = 1
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(text(String.format(Locale.US, "%.2f", amount), 12f).apply {
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                setSingleLine(true)
            })
        }
        balanceBreakdown.addView(balanceMetric("Initial", initialBalance), lp(0, -2).apply { weight = 1f })
        balanceBreakdown.addView(balanceMetric("Deposits", deposits), lp(0, -2).apply { weight = 1f })
        balanceBreakdown.addView(balanceMetric("Withdrawals", withdrawals), lp(0, -2).apply { weight = 1f })
        balanceCard.addView(balanceBreakdown)
        val balanceActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun balanceAction(label: String, click: () -> Unit): TextView = smallButton(label).apply {
            textSize = 11f
            setPadding(dp(5), dp(8), dp(5), dp(8))
            maxLines = 1
            setSingleLine(true)
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

    /**
     * Custom-drawn trading illustrations: real shapes, not text glyphs or emoji.
     * Kept in Canvas so they remain crisp at every Android density without external assets.
     */
    private inner class TradingIllustrationView(
        private val kind: String,
        private val tint: Int = accent,
        private val compact: Boolean = false
    ) : View(this@MainActivity) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private fun color(c: Int) { p.color = c; p.style = android.graphics.Paint.Style.FILL; p.strokeWidth = dp(1.5f) }
        private fun path(canvas: android.graphics.Canvas, pts: List<Pair<Float, Float>>, fill: Int) {
            color(fill)
            val path = android.graphics.Path()
            pts.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.first, pt.second) else path.lineTo(pt.first, pt.second) }
            path.close()
            canvas.drawPath(path, p)
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val side = minOf(width, height).toFloat()
            if (side <= 0f) return
            val save = canvas.save()
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            canvas.translate(left, top)
            canvas.scale(side / 64f, side / 64f)
            val bg = if (darkMode) Color.rgb(34, 53, 49) else Color.rgb(239, 243, 235)
            color(bg); canvas.drawRoundRect(1f, 1f, 63f, 63f, 17f, 17f, p)
            val gold = Color.rgb(205, 163, 76)
            val goldLight = Color.rgb(246, 216, 139)
            val goldDark = Color.rgb(145, 103, 34)
            val silver = Color.rgb(157, 174, 180)
            val silverLight = Color.rgb(226, 234, 232)
            val silverDark = Color.rgb(105, 126, 133)
            when (kind.lowercase(Locale.US)) {
                "gold" -> {
                    path(canvas, listOf(10f to 37f, 17f to 23f, 45f to 23f, 54f to 37f, 48f to 49f, 16f to 49f), goldDark)
                    path(canvas, listOf(10f to 37f, 17f to 23f, 45f to 23f, 37f to 37f), goldLight)
                    path(canvas, listOf(37f to 37f, 45f to 23f, 54f to 37f, 48f to 49f), gold)
                    path(canvas, listOf(10f to 37f, 37f to 37f, 48f to 49f, 16f to 49f), gold)
                    color(goldLight); p.strokeWidth = 1.7f; p.style = android.graphics.Paint.Style.STROKE
                    canvas.drawLine(20f, 28f, 39f, 28f, p); p.style = android.graphics.Paint.Style.FILL
                    color(goldDark); p.textSize = 6.5f; p.typeface = Typeface.DEFAULT_BOLD; canvas.drawText("999.9", 23f, 43f, p)
                }
                "silver" -> {
                    path(canvas, listOf(10f to 37f, 18f to 24f, 45f to 24f, 54f to 37f, 47f to 49f, 16f to 49f), silverDark)
                    path(canvas, listOf(10f to 37f, 18f to 24f, 45f to 24f, 37f to 37f), silverLight)
                    path(canvas, listOf(37f to 37f, 45f to 24f, 54f to 37f, 47f to 49f), silver)
                    path(canvas, listOf(10f to 37f, 37f to 37f, 47f to 49f, 16f to 49f), silver)
                    color(Color.WHITE); p.strokeWidth = 1.5f; p.style = android.graphics.Paint.Style.STROKE
                    canvas.drawLine(21f, 28f, 39f, 28f, p); p.style = android.graphics.Paint.Style.FILL
                }
                "crypto" -> {
                    color(Color.rgb(220, 150, 55)); canvas.drawCircle(32f, 32f, 21f, p)
                    color(Color.rgb(250, 195, 93)); canvas.drawCircle(32f, 32f, 17f, p)
                    color(Color.rgb(174, 104, 30)); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2.5f
                    canvas.drawCircle(32f, 32f, 17f, p); p.style = android.graphics.Paint.Style.FILL
                    color(Color.rgb(140, 83, 23)); p.textSize = 27f; p.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText(if (tint == Color.rgb(205, 126, 48)) "₿" else "Ξ", 21f, 41f, p)
                }
                "index" -> {
                    color(Color.rgb(202, 220, 210)); canvas.drawRoundRect(10f, 11f, 54f, 53f, 7f, 7f, p)
                    val vals = listOf(17f to 42f, 25f to 33f, 32f to 37f, 40f to 23f, 49f to 27f)
                    color(tint); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 3f
                    val line = android.graphics.Path(); vals.forEachIndexed { i, pt -> if (i == 0) line.moveTo(pt.first, pt.second) else line.lineTo(pt.first, pt.second) }
                    canvas.drawPath(line, p); p.style = android.graphics.Paint.Style.FILL
                    vals.forEach { color(tint); canvas.drawCircle(it.first, it.second, 2.2f, p) }
                }
                "forex", "pair" -> {
                    color(tint); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2.4f
                    canvas.drawCircle(25f, 30f, 15f, p); canvas.drawCircle(39f, 35f, 15f, p); p.style = android.graphics.Paint.Style.FILL
                    color(tint); p.textSize = 17f; p.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText("$", 15f, 36f, p); canvas.drawText("€", 32f, 41f, p)
                    color(gold); p.strokeWidth = 2.5f
                    canvas.drawLine(31f, 13f, 43f, 13f, p); canvas.drawLine(43f, 13f, 39f, 9f, p); canvas.drawLine(43f, 13f, 39f, 17f, p)
                }
                "time" -> {
                    color(tint); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2.5f
                    canvas.drawCircle(32f, 32f, 20f, p); p.style = android.graphics.Paint.Style.FILL
                    color(tint); canvas.drawRoundRect(30f, 17f, 34f, 33f, 2f, 2f, p); canvas.drawRoundRect(31f, 30f, 44f, 34f, 2f, 2f, p)
                    color(gold); canvas.drawCircle(32f, 32f, 3f, p)
                }
                "market" -> {
                    color(tint); canvas.drawRoundRect(12f, 14f, 52f, 50f, 8f, 8f, p)
                    color(bg); canvas.drawRect(18f, 22f, 22f, 42f, p); canvas.drawRect(29f, 18f, 33f, 42f, p); canvas.drawRect(40f, 27f, 44f, 42f, p)
                    color(gold); p.strokeWidth = 2.4f; p.style = android.graphics.Paint.Style.STROKE
                    val path = android.graphics.Path().apply { moveTo(17f, 34f); lineTo(27f, 27f); lineTo(35f, 31f); lineTo(47f, 20f) }
                    canvas.drawPath(path, p); p.style = android.graphics.Paint.Style.FILL
                }
                "settings" -> {
                    color(tint); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 5f
                    canvas.drawCircle(32f, 32f, 14f, p); p.style = android.graphics.Paint.Style.FILL
                    for (i in 0..7) {
                        val a = Math.PI * i / 4.0
                        val x1 = 32f + (16f * kotlin.math.cos(a)).toFloat()
                        val y1 = 32f + (16f * kotlin.math.sin(a)).toFloat()
                        val x2 = 32f + (22f * kotlin.math.cos(a)).toFloat()
                        val y2 = 32f + (22f * kotlin.math.sin(a)).toFloat()
                        color(tint); p.strokeWidth = 4f; canvas.drawLine(x1, y1, x2, y2, p)
                    }
                    color(gold); canvas.drawCircle(32f, 32f, 6f, p); color(bg); canvas.drawCircle(32f, 32f, 2.5f, p)
                }
                else -> {
                    color(tint); p.strokeWidth = 3f; p.style = android.graphics.Paint.Style.STROKE
                    canvas.drawRoundRect(12f, 12f, 52f, 52f, 9f, 9f, p); p.style = android.graphics.Paint.Style.FILL
                    color(gold); canvas.drawCircle(32f, 32f, 9f, p)
                }
            }
            canvas.restoreToCount(save)
        }
    }

    private fun illustrationKind(symbol: String): String {
        val s = symbol.uppercase(Locale.US)
        return when {
            s.contains("XAU") || s.contains("GOLD") -> "gold"
            s.contains("XAG") || s.contains("SILVER") -> "silver"
            s.contains("BTC") || s.contains("ETH") -> "crypto"
            s.contains("US30") || s.contains("NAS") || s.contains("SPX") || s.contains("DAX") || s.contains("INDEX") -> "index"
            s.contains("OIL") || s.contains("WTI") || s.contains("BRENT") -> "market"
            s.length == 6 && s.all { it.isLetter() } -> "forex"
            else -> "market"
        }
    }

    private fun symbolMark(symbol: String): String {
        val s = symbol.uppercase(Locale.US)
        return when {
            s.contains("XAU") || s.contains("GOLD") -> "Au"
            s.contains("XAG") || s.contains("SILVER") -> "Ag"
            s.contains("BTC") -> "₿"
            s.contains("ETH") -> "◆"
            s.contains("US30") || s.contains("NAS") || s.contains("SPX") || s.contains("DAX") || s.contains("INDEX") -> "▥"
            s.contains("OIL") || s.contains("WTI") || s.contains("BRENT") -> "◉"
            s.length == 6 && s.all { it.isLetter() } -> "↔"
            else -> s.take(2)
        }
    }

    private fun symbolMarkColor(symbol: String): Int {
        val s = symbol.uppercase(Locale.US)
        return when {
            s.contains("XAU") || s.contains("GOLD") -> Color.rgb(177, 132, 43)
            s.contains("XAG") || s.contains("SILVER") -> Color.rgb(112, 128, 142)
            s.contains("BTC") || s.contains("ETH") -> Color.rgb(205, 126, 48)
            s.contains("OIL") || s.contains("WTI") || s.contains("BRENT") -> Color.rgb(79, 96, 103)
            s.contains("US30") || s.contains("NAS") || s.contains("SPX") || s.contains("DAX") || s.contains("INDEX") -> Color.rgb(69, 111, 155)
            else -> accent
        }
    }

    private fun journalTradeCard(parent: LinearLayout, t: JournalTrade) {
        val isOpen = t.exitTime == null
        val c = panel().apply {
            setBackgroundColor(cardColor)
            setPadding(dp(14), dp(14), dp(14), dp(12))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = TradingIllustrationView(illustrationKind(t.symbol), symbolMarkColor(t.symbol))
        row.addView(mark, lp(54, 54).apply { setMargins(0, 0, dp(12), 0) })
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(text(t.symbol, 17f).apply { typeface = Typeface.DEFAULT_BOLD })
        title.addView(text("Entry " + journalDate(t.entryTime), 12f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
        if (t.exitTime != null) {
            title.addView(text("Duration " + journalDuration(t.entryTime, t.exitTime!!), 11f, accent).apply {
                setPadding(dp(0), dp(3), dp(0), dp(0))
            })
        }
        row.addView(title, lp(0, -2).apply { weight = 1f })
        val badgeRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val directionColor = if (t.direction == "BUY") green else red
        badgeRow.addView(TextView(this).apply {
            text = if (t.direction == "BUY") "↑  BUY" else "↓  SELL"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(directionColor)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(if (t.direction == "BUY") Color.rgb(226, 242, 234) else Color.rgb(250, 233, 230))
            }
        })
        badgeRow.addView(TextView(this).apply {
            text = if (isOpen) "● OPEN" else "■ CLOSED"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (isOpen) green else muted)
            setPadding(dp(4), dp(6), dp(4), 0)
        })
        row.addView(badgeRow, lp(-2, -2).apply { setMargins(dp(8), 0, 0, 0) })
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
            val grossPnl = t.pnl.toDoubleOrNull() ?: 0.0
            val swapValue = t.swap.toDoubleOrNull() ?: 0.0
            val commissionValue = kotlin.math.abs(t.commission.toDoubleOrNull() ?: 0.0)
            val netPnl = grossPnl + swapValue - commissionValue
            val costs = kotlin.math.abs(swapValue) + commissionValue
            c.addView(text(
                "Exit " + t.exit.ifBlank { "—" } + "   •   Net P/L " + String.format(Locale.US, "%.2f", netPnl),
                13f, if (netPnl >= 0) green else red
            ).apply { setPadding(dp(0), dp(5), dp(0), dp(0)) })
            if (costs > 0.0) {
                c.addView(text("Swap (− debit / + credit): " + t.swap + "   •   Commission: " + t.commission, 12f, muted)
                    .apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
            }
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
        journalAsk("8 / 8  •  Entry Notes", "Optional — setup, entry rationale, market context", "", true) {
            t.entryNotes = it
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
            "3 / 7  •  Gross Profit / Loss",
            calculated?.let { "Suggested P/L: " + String.format(Locale.US, "%.5f", it) } ?: "Enter P/L",
            "", true
        ) {
            t.pnl = it.ifBlank { calculated?.toString() ?: "" }
            journalSwapStep(t)
        }
    }

    private fun journalSwapStep(t: JournalTrade) {
        journalAsk("4 / 7  •  Swap (±)", "Negative = charge, positive = credit; 0 if none", t.swap.takeUnless { it == "0" } ?: "", true) {
            val amount = if (it.isBlank()) 0.0 else it.toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(this, "Enter a valid signed swap amount (negative or positive).", Toast.LENGTH_SHORT).show()
                journalSwapStep(t)
            } else {
                t.swap = amount.toString()
                journalCommissionStep(t)
            }
        }
    }

    private fun journalCommissionStep(t: JournalTrade) {
        journalAsk("5 / 7  •  Commission Cost", "Commission cost (0 if none)", t.commission.takeUnless { it == "0" } ?: "", true) {
            val amount = if (it.isBlank()) 0.0 else it.toDoubleOrNull()
            if (amount == null || amount < 0.0) {
                Toast.makeText(this, "Enter a valid non-negative commission.", Toast.LENGTH_SHORT).show()
                journalCommissionStep(t)
            } else {
                t.commission = amount.toString()
                journalExitReasonStep(t)
            }
        }
    }

    private fun journalExitReasonStep(t: JournalTrade) {
        val reasons = arrayOf("Take Profit", "Stop Loss", "Manual Close", "Signal Reversal", "Session End", "Other")
        AlertDialog.Builder(this).setTitle("6 / 7  •  Exit Reason")
            .setItems(reasons) { _, i -> t.exitReason = reasons[i]; journalCloseNotesStep(t) }
            .setNeutralButton("Skip") { _, _ -> t.exitReason = ""; journalCloseNotesStep(t) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun journalCloseNotesStep(t: JournalTrade) {
        journalAsk("7 / 7  •  Exit Notes", "Optional — result review, execution, lessons learned", "", true) {
            t.exitNotes = it
            journalConfirmClose(t)
        }
    }

    private fun journalConfirmClose(t: JournalTrade) {
        var msg = t.symbol + "  " + t.direction +
            "\nEntry: " + t.entry +
            "\nExit: " + t.exit +
            "\nGross P/L: " + t.pnl.ifBlank { "—" } +
            "\nSwap: " + t.swap +
            "\nCommission: " + t.commission +
            "\nNet P/L after costs: " + String.format(Locale.US, "%.2f", (t.pnl.toDoubleOrNull() ?: 0.0) + (t.swap.toDoubleOrNull() ?: 0.0) - kotlin.math.abs(t.commission.toDoubleOrNull() ?: 0.0)) +
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
        journalEditAsk("Edit • Entry Notes", "Optional setup / entry rationale / market context", t.entryNotes) {
            t.entryNotes = it
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
        var selected = t.exitTime ?: System.currentTimeMillis()
        val button = smallButton("Exit time: " + journalDate(selected), true).apply {
            setOnClickListener {
                journalPickDateTime("Edit • Exit Time", selected) { picked ->
                    selected = picked
                    t.exitTime = selected
                    text = "Exit time: " + journalDate(selected)
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
            .setPositiveButton("Done") { _, _ ->
                t.exitTime = selected
                journalEditPnlStep(t)
            }
            .show()
    }

    private fun journalEditPnlStep(t: JournalTrade) {
        journalEditAsk("Edit • Gross Profit / Loss", "Before swap and commission", t.pnl) { pnl ->
            if (pnl.toDoubleOrNull() == null) {
                Toast.makeText(this, "Invalid profit/loss amount.", Toast.LENGTH_SHORT).show()
                journalEditPnlStep(t)
            } else {
                t.pnl = pnl
                journalEditSwapStep(t)
            }
        }
    }

    private fun journalEditSwapStep(t: JournalTrade) {
        journalEditAsk("Edit • Swap (±)", "Negative = charge, positive = credit; blank or 0 if none", t.swap) { swap ->
            val amount = if (swap.isBlank()) 0.0 else swap.toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(this, "Enter a valid signed swap amount.", Toast.LENGTH_SHORT).show()
                journalEditSwapStep(t)
            } else {
                t.swap = amount.toString()
                journalEditCommissionStep(t)
            }
        }
    }

    private fun journalEditCommissionStep(t: JournalTrade) {
        journalEditAsk("Edit • Commission Cost", "Enter positive cost; blank or 0 if none", t.commission) { commission ->
            val amount = commission.toDoubleOrNull()
            if (commission.isNotBlank() && (amount == null || amount < 0)) {
                Toast.makeText(this, "Enter a valid non-negative commission cost.", Toast.LENGTH_SHORT).show()
                journalEditCommissionStep(t)
            } else {
                t.commission = if (commission.isBlank()) "0" else commission
                journalEditExitReasonStep(t)
            }
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
        journalEditAsk("Edit • Exit Notes", "Optional closing review / lessons learned", t.exitNotes) { notes ->
            t.exitNotes = notes
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


    private var economicEvents: List<EconomicEvent> = emptyList()
    private var economicLoading = false
    private var economicAttempted = false
    private var economicError: String? = null
    private var economicDays = 7
    private var economicImpact = "All impact"
    private var economicCountry = "All countries"

    private fun eventRegionLabel(event: EconomicEvent): String {
        val country = event.country.trim().lowercase(Locale.US)
        val currency = event.currency.trim().uppercase(Locale.US)
        val source = "${event.country} ${event.currency} ${event.title} ${event.category}".lowercase(Locale.US)
        fun hasToken(value: String, token: String): Boolean =
            Regex("(^|[^a-z])" + Regex.escape(token.lowercase(Locale.US)) + "([^a-z]|$)").containsMatchIn(value)
        return when {
            currency == "EUR" || country in setOf("eu", "euro area", "eurozone", "euro area (euro)", "european union") ||
                source.contains("euro area") || source.contains("eurozone") || source.contains("european central bank") ||
                hasToken(source, "ecb") -> "🇪🇺 Euro (EUR)"
            currency == "USD" || country in setOf("us", "usa", "u.s.", "u.s.a.", "united states", "united states of america", "america") ||
                source.contains("united states") || source.contains("united states of america") || source.contains("federal reserve") ||
                source.contains("nonfarm payroll") || source.contains("non-farm payroll") || source.contains("us dollar") ||
                hasToken(source, "usa") || hasToken(source, "usd") || hasToken(source, "us") ||
                source.contains("consumer price index") && (source.contains("cpi") || source.contains("inflation")) -> "🇺🇸 US Dollar (USD)"
            currency == "JPY" || country in setOf("jp", "jpn", "japan") || source.contains("bank of japan") ||
                source.contains("japan") || hasToken(source, "jpy") -> "🇯🇵 Japan (JPY)"
            currency == "GBP" || country in setOf("gb", "gbr", "uk", "united kingdom", "britain", "great britain") ||
                source.contains("bank of england") || source.contains("united kingdom") || source.contains("britain") || hasToken(source, "gbp") -> "🇬🇧 UK Pound (GBP)"
            currency == "AUD" || country in setOf("au", "aus", "australia") || source.contains("reserve bank of australia") ||
                source.contains("australia") || hasToken(source, "aud") -> "🇦🇺 Australian Dollar (AUD)"
            currency == "CAD" || country in setOf("ca", "can", "canada") || source.contains("bank of canada") ||
                source.contains("canada") || hasToken(source, "cad") -> "🇨🇦 Canadian Dollar (CAD)"
            currency == "CHF" || country in setOf("ch", "che", "switzerland") || source.contains("swiss national bank") ||
                source.contains("switzerland") || hasToken(source, "chf") -> "🇨🇭 Swiss Franc (CHF)"
            currency == "NZD" || country in setOf("nz", "nzl", "new zealand") || source.contains("reserve bank of new zealand") ||
                source.contains("new zealand") || hasToken(source, "nzd") -> "🇳🇿 New Zealand Dollar (NZD)"
            currency in setOf("CNY", "CNH") || country in setOf("cn", "chn", "china") || source.contains("people's bank of china") ||
                source.contains("china") || hasToken(source, "cny") || hasToken(source, "cnh") -> "🇨🇳 Chinese Yuan (CNY)"
            currency == "INR" || country in setOf("in", "ind", "india") || source.contains("reserve bank of india") ||
                source.contains("india") || hasToken(source, "inr") -> "🇮🇳 Indian Rupee (INR)"
            currency == "BRL" || country in setOf("br", "bra", "brazil") || source.contains("brazil") || hasToken(source, "brl") -> "🇧🇷 Brazilian Real (BRL)"
            currency == "MXN" || country in setOf("mx", "mex", "mexico") || source.contains("mexico") || hasToken(source, "mxn") -> "🇲🇽 Mexican Peso (MXN)"
            country in setOf("global", "world", "worldwide", "global / not specified") ||
                source.contains("global") || source.contains("worldwide") -> "🌐 Global / not specified"
            else -> "🌐 Global / not specified"
        }
    }

    private fun showEconomicCalendar(refresh: Boolean = false) {
        settingsVisible = false
        settingsSubpageVisible = false
        if (refresh || !economicAttempted) loadEconomicEvents()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(0))
            setBackgroundColor(bg)
        }
        root.addView(text("News", 23f).apply { typeface = Typeface.DEFAULT_BOLD })
        root.addView(text("Scheduled market-moving releases • times in ${java.time.ZoneId.systemDefault().id}", 12f, muted).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun filterSpinner(options: List<String>, selected: String, changed: (String) -> Unit): Spinner {
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            spinner.setSelection(options.indexOf(selected).coerceAtLeast(0))
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val value = options.getOrNull(position) ?: return
                    if (value != selected) changed(value)
                }
            }
            return spinner
        }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dayOptions = listOf("Today" to 1, "7 days" to 7, "14 days" to 14, "30 days" to 30)
        val daySpinner = filterSpinner(dayOptions.map { it.first }, dayOptions.firstOrNull { it.second == economicDays }?.first ?: "7 days") {
            economicDays = dayOptions.firstOrNull { pair -> pair.first == it }?.second ?: 7
            prefs.edit().putInt("news_filter_days", economicDays).apply()
            showEconomicCalendar()
        }
        val impactOptions = listOf("All impact", "High", "Medium", "Low")
        val impactSpinner = filterSpinner(impactOptions, economicImpact) {
            economicImpact = it
            prefs.edit().putString("news_filter_impact", economicImpact).apply()
            showEconomicCalendar()
        }
        filterRow.addView(daySpinner, lp(0, 48).apply { weight = 1f; setMargins(0, 0, dp(4), 0) })
        filterRow.addView(impactSpinner, lp(0, 48).apply { weight = 1f; setMargins(dp(4), 0, 0, 0) })
        controls.addView(filterRow)
        val countries = listOf("All countries") + economicEvents.map { eventRegionLabel(it) }.distinct().sorted()
        val countrySpinner = filterSpinner(countries, economicCountry) {
            economicCountry = it
            prefs.edit().putString("news_filter_country", economicCountry).apply()
            showEconomicCalendar()
        }
        controls.addView(countrySpinner, lp(-1, 48))
        root.addView(controls, lp(-1, -2))
        root.addView(smallButton(if (economicLoading) "Updating news…" else "↻  Refresh news", true).apply {
            isEnabled = !economicLoading
            setOnClickListener { showEconomicCalendar(refresh = true) }
        }, lp(-1, 46).apply { setMargins(0, dp(6), 0, dp(8)) })

        val scroll = ScrollView(this).apply { isFillViewport = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (economicLoading && economicEvents.isEmpty()) {
            content.addView(panel().apply {
                addView(text("Loading news…", 15f).apply { gravity = Gravity.CENTER })
                addView(text("Fetching the free calendar feed.", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
            }, lp(-1, 110).apply { setMargins(0, dp(6), 0, dp(8)) })
        } else if (economicError != null && economicEvents.isEmpty()) {
            content.addView(panel().apply {
                addView(text("Calendar unavailable", 17f).apply { typeface = Typeface.DEFAULT_BOLD })
                addView(text(economicError ?: "Could not load events.", 13f, muted).apply { setPadding(0, dp(6), 0, dp(8)) })
                addView(smallButton("Try again", true).apply { setOnClickListener { showEconomicCalendar(refresh = true) } })
            }, lp(-1, -2).apply { setMargins(0, dp(6), 0, dp(8)) })
        } else {
            val now = java.time.Instant.now()
            val zone = java.time.ZoneId.systemDefault()
            val until = now.plusSeconds(economicDays * 86400L)
            val filtered = economicEvents.filter { event ->
                val instant = runCatching { java.time.Instant.parse(event.timeUtc) }.getOrNull()
                val impactOk = economicImpact == "All impact" || event.impact.equals(economicImpact, true)
                val countryOk = economicCountry == "All countries" || eventRegionLabel(event) == economicCountry
                instant != null && !instant.isBefore(now) && !instant.isAfter(until) && impactOk && countryOk
            }
            content.addView(text("${filtered.size} scheduled events", 12f, muted).apply {
                setPadding(dp(2), dp(6), dp(2), dp(8))
            })
            if (filtered.isEmpty()) {
                content.addView(panel().apply {
                    addView(text("No events match these filters", 16f).apply { gravity = Gravity.CENTER })
                    addView(text("Try a wider date range or another impact level.", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
                }, lp(-1, 100).apply { setMargins(0, dp(4), 0, dp(8)) })
            }
            filtered.forEach { event ->
                val card = panel()
                val impactColor = when (event.impact.lowercase(Locale.US)) {
                    "high" -> Color.rgb(194, 76, 72)
                    "medium" -> Color.rgb(194, 139, 42)
                    "low" -> green
                    else -> muted
                }
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(text(event.displayTime(zone), 12f, accent).apply { typeface = Typeface.DEFAULT_BOLD }, lp(0, -2).apply { weight = 1f })
                top.addView(TextView(this).apply {
                    text = event.impact.uppercase(Locale.US)
                    textSize = 10f
                    setTextColor(impactColor)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (darkMode) Color.rgb(38, 47, 46) else Color.rgb(246, 242, 226))
                        cornerRadius = dp(12f)
                    }
                })
                card.addView(top)
                card.addView(text(event.title, 16f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(8), 0, dp(4))
                    maxLines = 4
                })
                card.addView(text(listOf(eventRegionLabel(event), event.category).filter { it.isNotBlank() }.distinct().joinToString(" • "), 12f, muted).apply { setPadding(0, dp(2), 0, dp(2)) })
                val values = listOf(
                    "Forecast" to event.consensus,
                    "Previous" to event.previous,
                    "Actual" to event.actual
                ).filter { it.second.isNotBlank() }
                if (values.isNotEmpty()) {
                    val valueRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    values.forEach { (label, value) ->
                        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(4)) }
                        cell.addView(text(label, 10f, muted))
                        cell.addView(text(value, 12f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(3), 0, 0) })
                        valueRow.addView(cell, lp(0, -2).apply { weight = 1f })
                    }
                    card.addView(valueRow)
                }
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val hasAlarm = EconomicAlertScheduler.hasAlarm(this@MainActivity, event.id)
                actions.addView(smallButton(if (hasAlarm) "✓  Alarm set" else "＋  Set alarm", hasAlarm).apply {
                    setOnClickListener { chooseEconomicAlarm(event) }
                }, lp(0, 42).apply { weight = 1f; setMargins(0, dp(8), dp(4), 0) })
                actions.addView(smallButton("Source ↗").apply {
                    setOnClickListener {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(event.url))) }
                            .onFailure { Toast.makeText(this@MainActivity, "Could not open source link.", Toast.LENGTH_SHORT).show() }
                    }
                }, lp(0, 42).apply { weight = 1f; setMargins(dp(4), dp(8), 0, 0) })
                card.addView(actions)
                content.addView(card, lp(-1, -2).apply { setMargins(0, dp(3), 0, dp(8)) })
            }
        }
        content.addView(text("Data: FinanceCalendar • Free calendar feed", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(4))
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.financecalendar.com/api/"))) } }
        })
        scroll.addView(content)
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        val navHost = LinearLayout(this).apply { setPadding(dp(18), dp(0), dp(18), dp(8)) }
        addBottom(navHost, "news")
        root.addView(navHost)
        setContentView(root)
    }

    private fun loadEconomicEvents() {
        if (economicLoading) return
        economicLoading = true
        economicAttempted = true
        economicError = null
        val from = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val to = from.plusDays(30)
        EconomicCalendarRepository.load(from, to) { result ->
            runOnUiThread {
                economicLoading = false
                result.onSuccess {
                    economicEvents = it
                    economicError = if (it.isEmpty()) "The provider returned no scheduled events." else null
                    economicCountry = if (economicCountry != "All countries" && it.none { event -> eventRegionLabel(event) == economicCountry }) "All countries" else economicCountry
                }.onFailure {
                    economicError = it.message ?: "Network error. Check your connection and try again."
                }
                if (!isFinishing) showEconomicCalendar()
            }
        }
    }

    private fun chooseEconomicAlarm(event: EconomicEvent) {
        if (EconomicAlertScheduler.hasAlarm(this, event.id)) {
            AlertDialog.Builder(this)
                .setTitle("Economic event reminder")
                .setMessage("A reminder is already set for this event. Cancel it?")
                .setNegativeButton("Keep alarm", null)
                .setPositiveButton("Cancel alarm") { _, _ ->
                    EconomicAlertScheduler.cancel(this, event.id)
                    showEconomicCalendar()
                }
                .show()
            return
        }
        val choices = arrayOf("At release time", "5 minutes before", "15 minutes before", "30 minutes before", "60 minutes before")
        val minutes = intArrayOf(0, 5, 15, 30, 60)
        AlertDialog.Builder(this)
            .setTitle("Remind me before release")
            .setItems(choices) { _, which ->
                val ok = EconomicAlertScheduler.schedule(this, event, minutes[which])
                val message = if (ok) "Reminder saved on this device." else "This reminder time has already passed, or Android could not schedule it. Choose a future event and check notification/alarm permissions."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                showEconomicCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettings() {
        settingsVisible = true
        settingsSubpageVisible = false
        val root = base()
        root.addView(text("Settings", 23f))
        root.addView(text("Configure how Candle Alert behaves", 14f, muted).apply { setPadding(dp(0), dp(4), dp(0), dp(12)) })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(0), dp(0), dp(0), dp(10)) }

        // Daily trading controls live on Home. Settings contains only behavior/system options.
        val openMarketValue = prefs.getString("open_market", "00:00") ?: "00:00"
        val themeValue = when (prefs.getString("theme_mode", "light")) {
            "dark" -> "Dark appearance"
            "system" -> "Follow phone theme"
            else -> "Light appearance"
        }
        val sheetValue = if (prefs.getBoolean("google_sheets_connected", false)) "Connected and syncing" else "Not connected"
        val appValue = prefs.getString("notification_app_label", "")?.takeIf { it.isNotBlank() } ?: "No app selected"
        val alarmValue = if (android.os.Build.VERSION.SDK_INT < 31 ||
            getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()) {
            "Precise alerts allowed"
        } else {
            "Permission needed"
        }
        val options = listOf(
            Triple("Open Market", "Broker candle starts at $openMarketValue", "market"),
            Triple("Clock Time Zone", clockTimeZoneSummary(), "time"),
            Triple("Theme", themeValue, "settings"),
            Triple("Google Sheets Journal", sheetValue, "index"),
            Triple("Open App on Notification", appValue, "forex"),
            Triple("Exact Alarm Permission", alarmValue, "time")
        )

        content.addView(text("APP PREFERENCES", 11f, muted).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(2), dp(0), dp(6))
        })
        options.forEach { option ->
            val titleText = option.first
            val c = panel().apply { setPadding(dp(12), dp(9), dp(10), dp(9)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TradingIllustrationView(option.third, accent), lp(40, 40).apply { setMargins(0, 0, dp(10), 0) })
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            titles.addView(text(titleText, 14.5f).apply {
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            titles.addView(text(option.second, 11f, muted).apply {
                setPadding(dp(0), dp(3), dp(0), dp(0))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(titles, lp(0, -2).apply { weight = 1f })
            row.addView(text("›", 25f, accent).apply { gravity = Gravity.CENTER })
            c.addView(row)
            c.setOnClickListener {
                when (titleText) {
                    "Open Market" -> chooseOpenMarket()
                    "Theme" -> showThemeSettings()
                    "Clock Time Zone" -> showTimeZoneSettings()
                    "Google Sheets Journal" -> showGoogleSheetsSettings()
                    "Open App on Notification" -> chooseNotificationApp()
                    "Exact Alarm Permission" -> if (android.os.Build.VERSION.SDK_INT >= 31) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:" + packageName)
                        })
                    }
                }
            }
            content.addView(c, lp(-1, -2).apply { setMargins(dp(0), dp(3), dp(0), dp(5)) })
        }
        scroll.addView(content)
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun clockTimeZoneSummary(): String {
        val zone = TradingSessions.displayZone(this)
        val source = if (prefs.getString("clock_timezone", null).isNullOrBlank()) "Follow phone" else "Manual"
        return "$source • ${TradingSessions.zoneLabel(zone)}"
    }

    private fun showTimeZoneSettings() {
        settingsVisible = true
        settingsSubpageVisible = true
        val root = base()
        root.addView(text("Local Time Zone", 23f))
        root.addView(text("Choose the time zone used by the clock and session rings. Session boundaries are converted through UTC using the rules for each market.", 14f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(12))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(12))
        }
        scroll.addView(content)

        val automatic = prefs.getString("clock_timezone", null).isNullOrBlank()
        val autoCard = panel().apply { setPadding(dp(14), dp(10), dp(12), dp(10)) }
        val autoRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val autoText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Follow phone time zone", 15f).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(text("Recommended • updates when the phone's zone changes", 11f, muted).apply {
                setPadding(0, dp(3), 0, 0)
            })
        }
        autoRow.addView(autoText, lp(0, -2).apply { weight = 1f })
        val autoSwitch = Switch(this).apply { isChecked = automatic }
        autoRow.addView(autoSwitch, lp(56, 48))
        autoCard.addView(autoRow)
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            val edit = prefs.edit()
            if (checked) edit.remove("clock_timezone")
            else edit.putString("clock_timezone", java.time.ZoneId.systemDefault().id)
            edit.apply()
            showTimeZoneSettings()
        }
        content.addView(autoCard, lp(-1, -2).apply { setMargins(0, dp(3), 0, dp(6)) })

        val selectedZone = TradingSessions.displayZone(this)
        val zoneCard = panel().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        zoneCard.addView(text("Current display zone", 12f, muted))
        zoneCard.addView(text(TradingSessions.zoneLabel(selectedZone), 16f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(3))
        })
        zoneCard.addView(text(if (automatic) "Source: phone settings" else "Source: manually selected", 11f, muted))
        content.addView(zoneCard, lp(-1, -2).apply { setMargins(0, dp(3), 0, dp(6)) })

        val chooseCard = panel().apply { setPadding(dp(14), dp(12), dp(12), dp(12)) }
        val chooseRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val chooseText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Choose a time zone", 15f).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(text(if (automatic) "Select a zone to use a manual override" else "Tap to change the selected zone", 11f, muted).apply {
                setPadding(0, dp(3), 0, 0)
            })
        }
        chooseRow.addView(chooseText, lp(0, -2).apply { weight = 1f })
        chooseRow.addView(text("›", 25f, accent).apply { gravity = Gravity.CENTER })
        chooseCard.addView(chooseRow)
        chooseCard.setOnClickListener { chooseClockTimeZone() }
        content.addView(chooseCard, lp(-1, -2).apply { setMargins(0, dp(3), 0, dp(8)) })

        val sourceCard = panel().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        sourceCard.addView(text("Time-zone rules", 14f).apply { typeface = Typeface.DEFAULT_BOLD })
        sourceCard.addView(text("Uses the IANA time-zone rules supplied by Android, not a fixed UTC offset. Daylight-saving and legal time changes are applied when the device's time-zone data is updated. This feature does not require location permission or a live internet connection.", 12f, muted).apply {
            setPadding(0, dp(5), 0, dp(8))
        })
        sourceCard.addView(text("IANA Time Zone Database ↗", 12f, accent).apply { typeface = Typeface.DEFAULT_BOLD })
        sourceCard.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.iana.org/time-zones")))
            }.onFailure {
                Toast.makeText(this, "Unable to open the time-zone information page.", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(sourceCard, lp(-1, -2).apply { setMargins(0, dp(3), 0, dp(8)) })

        content.addView(text("Broker candle alignment, quiet hours, and Android alarm delivery continue to use the phone's own time zone.", 11f, muted).apply {
            setPadding(dp(3), dp(4), dp(3), dp(8))
        })
        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        root.addView(smallButton("Done", true).apply {
            setOnClickListener { showSettings() }
        }, lp(-1, 50).apply { setMargins(0, dp(8), 0, dp(8)) })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun chooseClockTimeZone() {
        data class TimeZoneChoice(val id: String, val label: String) {
            override fun toString(): String = label
        }

        val now = java.time.Instant.now()
        val choices = (java.time.ZoneId.getAvailableZoneIds() + "UTC").distinct().sorted().map { id ->
            val zone = java.time.ZoneId.of(id)
            TimeZoneChoice(id, TradingSessions.zoneLabel(zone, now))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(4))
        }
        val search = EditText(this).apply {
            hint = "Search city or zone, e.g. Tehran"
            setSingleLine(true)
            textSize = 15f
        }
        val list = ListView(this).apply {
            dividerHeight = dp(1)
            choiceMode = ListView.CHOICE_MODE_SINGLE
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, choices)
        list.adapter = adapter
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        box.addView(search, lp(-1, 52))
        box.addView(list, lp(-1, 350))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose time zone")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Use phone time zone") { _, _ ->
                prefs.edit().remove("clock_timezone").apply()
                showTimeZoneSettings()
            }
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val chosen = adapter.getItem(position) ?: return@setOnItemClickListener
            prefs.edit().putString("clock_timezone", chosen.id).apply()
            dialog.dismiss()
            showTimeZoneSettings()
        }
        dialog.show()
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
        root.addView(text("Trading Sessions", 23f))
        root.addView(text("Select the Forex sessions used for alerts.", 14f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(12))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(12))
        }
        scroll.addView(content)

        val sessions = listOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        val selected = prefs.getStringSet("sessions", sessions.toSet())?.toMutableSet() ?: sessions.toMutableSet()
        sessions.forEach { s ->
            val c = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(s, 16f), lp(0, -2).apply { weight = 1f })
            val sw = Switch(this).apply { isChecked = selected.contains(s); minWidth = dp(52) }
            row.addView(sw, lp(56, 48))
            c.addView(row)
            sw.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(s) else selected.remove(s)
                prefs.edit().putStringSet("sessions", selected).apply()
                Scheduler.scheduleNext(this)
            }
            content.addView(c, lp(-1, -2).apply { setMargins(dp(0), dp(4), dp(0), dp(4)) })
        }

        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        root.addView(smallButton("Done", true).apply {
            setOnClickListener { showSettings() }
        }, lp(-1, 50).apply { setMargins(dp(0), dp(10), dp(0), dp(8)) })
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
        settingsVisible = true
        settingsSubpageVisible = true
        val root = base()
        root.addView(text("Theme", 23f))
        root.addView(text("Choose light, dark, or your phone's system theme.", 14f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(14))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(12))
        }
        scroll.addView(content)

        val modes = listOf("light" to "Light", "dark" to "Dark", "system" to "System default")
        modes.forEach { pair ->
            val mode = pair.first
            val c = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titles.addView(text(pair.second, 17f).apply { typeface = Typeface.DEFAULT_BOLD })
            titles.addView(text(
                when (mode) {
                    "light" -> "Always use light mode"
                    "dark" -> "Always use dark mode"
                    else -> "Match the phone's system theme"
                }, 12f, muted
            ).apply { setPadding(dp(0), dp(4), dp(0), dp(0)) })
            row.addView(titles, lp(0, -2).apply { weight = 1f })
            row.addView(text(if (prefs.getString("theme_mode", "light") == mode) "✓" else "", 23f, accent))
            c.addView(row)
            c.setOnClickListener {
                prefs.edit().putString("theme_mode", mode).apply()
                applyNightModePreference()
                showSettings()
            }
            content.addView(c, lp(-1, -2).apply { setMargins(dp(0), dp(5), dp(0), dp(5)) })
        }

        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }


    private fun chooseNotificationApp() {
        val root = base()
        root.addView(text("Notification App", 23f))
        root.addView(text("Choose which installed trading app opens when an alert arrives.", 14f, muted).apply {
            setPadding(dp(0), dp(4), dp(0), dp(12))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(12))
        }
        scroll.addView(content)

        val current = prefs.getString("notification_app_package", "") ?: ""
        val custom = prefs.getStringSet("custom_notification_apps", emptySet()) ?: emptySet()

        val none = panel().apply { setPadding(dp(16), dp(8), dp(14), dp(8)) }
        val nr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nr.addView(text("No app", 16f), lp(0, -2).apply { weight = 1f })
        nr.addView(text(if (current.isEmpty()) "✓" else "", 22f, accent))
        none.addView(nr)
        none.setOnClickListener {
            prefs.edit().remove("notification_app_package").remove("notification_app_label").apply()
            showSettings()
        }
        content.addView(none, lp(-1, -2).apply { setMargins(dp(0), dp(4), dp(0), dp(10)) })

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(intent, 0).map {
            it.activityInfo.packageName to it.loadLabel(pm).toString()
        }.filter { it.first != packageName }.distinctBy { it.first }

        val words = listOf(
            "metatrader", "meta trader", "tradingview", "trading view", "ctrader", "ninjatrader",
            "thinktrader", "trading 212", "ibkr", "interactive brokers", "etoro", "binance", "bybit",
            "okx", "kraken", "coinbase", "kucoin", "bitget", "mexc", "deriv", "exness", "xm trading",
            "alpari", "fxtm", "oanda", "ic markets", "pepperstone", "eightcap", "admirals", "tickmill",
            "fbs", "roboforex", "fxpro", "xtb", "capital.com", "trading", "trade", "trader", "broker",
            "forex", "crypto", "exchange", "invest"
        )
        val tradingApps = allApps.filter { pair -> words.any { pair.second.lowercase().contains(it) } }
            .sortedWith(compareBy(
                { !(it.second.contains("MetaTrader", true) || it.second.contains("TradingView", true)) },
                { it.second.lowercase() }
            ))

        content.addView(text("TRADING APPS", 11f, muted).apply {
            setPadding(dp(2), dp(4), dp(0), dp(5))
        })
        tradingApps.forEach { addNotificationAppRow(content, it.first, it.second, current) }
        content.addView(text("MY ADDED APPS", 11f, muted).apply {
            setPadding(dp(2), dp(12), dp(0), dp(5))
        })
        allApps.filter { custom.contains(it.first) }.sortedBy { it.second.lowercase() }
            .forEach { addNotificationAppRow(content, it.first, it.second, current) }

        content.addView(panel().apply {
            val r = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(text("＋  Add installed app", 16f), lp(0, -2).apply { weight = 1f })
            r.addView(text("›", 28f, muted))
            addView(r)
            setOnClickListener { showInstalledAppsPicker() }
        }, lp(-1, -2).apply { setMargins(dp(0), dp(10), dp(0), dp(6)) })

        root.addView(scroll, lp(-1, 0).apply { weight = 1f })
        addBottom(root, "settings")
        setContentView(root)
    }

    private fun addNotificationAppRow(root: LinearLayout, pkg: String, name: String, current: String) {
        val c = panel().apply { setPadding(dp(16), dp(7), dp(14), dp(7)) }
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        r.addView(text(name, 16f), lp(0, -2).apply { weight = 1f })
        if (pkg == current) r.addView(text("✓", 22f, accent))
        c.addView(r)
        c.setOnClickListener {
            prefs.edit().putString("notification_app_package", pkg).putString("notification_app_label", name).apply()
            showSettings()
        }
        root.addView(c, lp(-1, -2).apply { setMargins(dp(0), dp(4), dp(0), dp(4)) })
    }

    private fun showInstalledAppsPicker() {
        val root = base()
        root.addView(text("Add Installed App", 23f))
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
