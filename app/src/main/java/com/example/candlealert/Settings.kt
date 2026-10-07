package com.example.candlealert

enum class Market { FOREX, CRYPTO, BOTH }
enum class AlertMode { BEFORE, AT_CLOSE, AFTER }

data class Settings(
    val enabled: Boolean = true,
    val tfMinutes: Int = 5,
    val market: Market = Market.FOREX,
    val mode: AlertMode = AlertMode.BEFORE,
    val offsetSeconds: Int = 120,
    val sessions: Set<String> = setOf("Sydney", "Tokyo", "Frankfurt", "London", "New York"),
    val quietStart: Int = 0,
    val quietEnd: Int = 450
)
