package com.kriptoradar

import com.kriptoradar.alerts.Voice
import com.kriptoradar.data.Http
import com.kriptoradar.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyPrefsTest {
    @Test fun defaultsAreOff() {
        val s = Settings()
        assertFalse(s.casualMode); assertFalse(s.onlySelectedCoins)
        assertTrue(s.allows("ANYCOIN"))
    }

    @Test fun onlySelectedFilters() {
        val s = Settings(onlySelectedCoins = true, notifyCoins = setOf("BTC", "SOL"))
        assertTrue(s.allows("BTC")); assertFalse(s.allows("PEPE"))
    }

    @Test fun oldSavedSettingsStillLoad() {
        val old = """{"quote":"USDT","rapidMovePct":2.5}"""
        val s = Http.json.decodeFromString(Settings.serializer(), old)
        assertFalse(s.casualMode); assertTrue(s.rapidMovePct == 2.5)
    }

    @Test fun printCasualSamples() {
        repeat(3) {
            println(Voice.rapidMove("BTC", true, 3.2, 5, "84.120,00 USDT", 4.1))
            println(Voice.rapidMove("SOL", false, -4.5, 5, "142,10 USDT", -7.0))
        }
        println(Voice.reversal("UNI", 31.0, 11.7, "7,8200"))
        println(Voice.signals(listOf(Triple("INJ", 78, 6.2), Triple("FIL", 66, 3.1))))
    }
}

class InboxTest {
    @Test fun inboxRoundTripAndNewSettingDefaults() {
        val item = com.kriptoradar.data.InboxItem(1, 2, com.kriptoradar.data.InboxKind.REVERSAL, "t", "x", "SOLUSDT", "SOLUSDT", 150.0)
        val back = Http.json.decodeFromString(com.kriptoradar.data.InboxItem.serializer(),
            Http.json.encodeToString(com.kriptoradar.data.InboxItem.serializer(), item))
        assertTrue(back == item)
        val s = Http.json.decodeFromString(Settings.serializer(), """{"casualMode":true}""")
        assertTrue(s.casualMode && s.autoProtect && s.quickBuyAmount == 1000.0 && s.dailySummary)
    }
}
