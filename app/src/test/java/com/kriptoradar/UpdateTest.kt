package com.kriptoradar

import com.kriptoradar.data.Http
import com.kriptoradar.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateTest {
    @Test fun parsesCiVersionJson() {
        val json = """{"versionCode":107,"versionName":"1.2.7","notes":"Yeni özellik","apkUrl":"https://x/KriptoRadar.apk","publishedAt":"2026-10-13T20:00:00Z"}"""
        val info = Http.json.decodeFromString(UpdateInfo.serializer(), json)
        assertEquals(107, info.versionCode)
        assertEquals("1.2.7", info.versionName)
        assertEquals("https://x/KriptoRadar.apk", info.apkUrl)
    }
}
