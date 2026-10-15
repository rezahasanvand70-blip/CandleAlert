package com.example.candlealert

import java.util.Locale

/**
 * Maps the calendar provider's country/currency metadata and event wording to a stable
 * display/filter region. More specific currency evidence always wins over generic
 * provider labels such as "Global".
 */
object EconomicEventRegionClassifier {
    fun label(countryValue: String, currencyValue: String, titleValue: String, categoryValue: String): String {
        val country = countryValue.trim().lowercase(Locale.US)
        val currency = currencyValue.trim().uppercase(Locale.US).replace(Regex("[^A-Z]"), "")
        val raw = listOf(countryValue, currencyValue, titleValue, categoryValue)
            .joinToString(" ")
            .lowercase(Locale.US)
        val normalized = raw.replace(Regex("[^a-z0-9]+"), " ").trim()
        val tokens = if (normalized.isBlank()) emptySet() else normalized.split(Regex("\\s+")).toSet()
        fun token(vararg values: String): Boolean = values.any { it in tokens }
        fun contains(vararg values: String): Boolean = values.any { raw.contains(it) }
        val ukAbbreviation = Regex("(^|[^a-z])u\\.?k\\.?([^a-z]|$)").containsMatchIn(raw)

        return when {
            currency == "EUR" ||
                country in setOf("eu", "euro area", "eurozone", "european union") ||
                contains("euro area", "eurozone", "european central bank") || token("ecb") ->
                "🇪🇺 Euro (EUR)"

            currency == "USD" ||
                country in setOf("us", "usa", "u.s.", "u.s.a.", "united states", "united states of america", "america") ||
                contains("united states", "federal reserve", "nonfarm payroll", "non-farm payroll", "us dollar", "fomc", "fed funds rate") ||
                token("usa", "usd", "fed", "fomc") ->
                "🇺🇸 US Dollar (USD)"

            currency == "JPY" ||
                country in setOf("jp", "jpn", "japan") ||
                contains("bank of japan", "japan") || token("jpy") ->
                "🇯🇵 Japan (JPY)"

            currency == "GBP" || currencyValue.contains("£") ||
                country in setOf("gb", "gbr", "uk", "u.k.", "united kingdom", "great britain", "britain", "england", "scotland", "wales") ||
                contains(
                    "united kingdom", "great britain", "britain", "british", "bank of england",
                    "pound sterling", "sterling", "claimant count", "average earnings", "bank rate"
                ) ||
                token("gb", "gbr", "gbp", "boe", "pound", "sterling", "british") || ukAbbreviation ->
                "🇬🇧 UK Pound (GBP)"

            currency == "AUD" ||
                country in setOf("au", "aus", "australia") ||
                contains("reserve bank of australia", "australia") || token("aud") ->
                "🇦🇺 Australian Dollar (AUD)"

            currency == "CAD" ||
                country in setOf("ca", "can", "canada") ||
                contains("bank of canada", "canada") || token("cad") ->
                "🇨🇦 Canadian Dollar (CAD)"

            currency == "CHF" ||
                country in setOf("ch", "che", "switzerland") ||
                contains("swiss national bank", "switzerland") || token("chf") ->
                "🇨🇭 Swiss Franc (CHF)"

            currency == "NZD" ||
                country in setOf("nz", "nzl", "new zealand") ||
                contains("reserve bank of new zealand", "new zealand") || token("nzd") ->
                "🇳🇿 New Zealand Dollar (NZD)"

            currency in setOf("CNY", "CNH") ||
                country in setOf("cn", "chn", "china") ||
                contains("people's bank of china", "china") || token("cny", "cnh") ->
                "🇨🇳 Chinese Yuan (CNY)"

            currency == "INR" ||
                country in setOf("in", "ind", "india") ||
                contains("reserve bank of india", "india") || token("inr") ->
                "🇮🇳 Indian Rupee (INR)"

            currency == "BRL" ||
                country in setOf("br", "bra", "brazil") ||
                contains("brazil") || token("brl") ->
                "🇧🇷 Brazilian Real (BRL)"

            currency == "MXN" ||
                country in setOf("mx", "mex", "mexico") ||
                contains("mexico") || token("mxn") ->
                "🇲🇽 Mexican Peso (MXN)"

            country in setOf("global", "world", "worldwide", "global / not specified") ||
                contains("global", "worldwide") ->
                "🌐 Global / not specified"

            else -> "🌐 Global / not specified"
        }
    }
}
