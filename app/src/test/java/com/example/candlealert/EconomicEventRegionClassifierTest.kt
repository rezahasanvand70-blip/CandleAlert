package com.example.candlealert

import org.junit.Assert.assertEquals
import org.junit.Test

class EconomicEventRegionClassifierTest {
    @Test
    fun classifiesUkEventsFromCurrencyCodeEvenWhenProviderSaysGlobal() {
        assertEquals(
            "🇬🇧 UK Pound (GBP)",
            EconomicEventRegionClassifier.label("Global / not specified", "GBp", "Monthly GDP", "")
        )
    }

    @Test
    fun classifiesUkEventsFromUkAbbreviationInTitle() {
        assertEquals(
            "🇬🇧 UK Pound (GBP)",
            EconomicEventRegionClassifier.label("", "", "UK CPI y/y", "inflation")
        )
    }

    @Test
    fun classifiesUkEventsFromBritishPoundWording() {
        assertEquals(
            "🇬🇧 UK Pound (GBP)",
            EconomicEventRegionClassifier.label("", "Pound Sterling", "Retail Sales", "")
        )
    }

    @Test
    fun countryMetadataTakesPriorityOverGenericGlobalWording() {
        assertEquals(
            "🇬🇧 UK Pound (GBP)",
            EconomicEventRegionClassifier.label("GB", "", "Global calendar: UK Bank Rate", "")
        )
    }

    @Test
    fun preservesUsDollarClassificationWhenCurrencyIsKnown() {
        assertEquals(
            "🇺🇸 US Dollar (USD)",
            EconomicEventRegionClassifier.label("Global", "USD", "CPI release", "")
        )
    }

    @Test
    fun leavesUnidentifiedEventsInGlobalBucket() {
        assertEquals(
            "🌐 Global / not specified",
            EconomicEventRegionClassifier.label("", "", "Global market update", "")
        )
    }
}
