package com.wifiradarx.app.intelligence

import android.content.Context
import java.util.Locale

/**
 * Offline OUI (Organizationally Unique Identifier) lookup.
 * Reads from res/raw/oui_database.txt — format: "AA:BB:CC\tVendorName"
 */
class OuiLookup(private val context: Context) {

    private val ouiMap = mutableMapOf<String, String>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val resId = context.resources.getIdentifier(
                "oui_database", "raw", context.packageName
            )
            if (resId == 0) return
            context.resources.openRawResource(resId).bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                    val tab = trimmed.indexOf('\t')
                    if (tab > 0) {
                        val oui = trimmed.substring(0, tab).trim().uppercase(Locale.US)
                        val vendor = trimmed.substring(tab + 1).trim()
                        ouiMap[oui] = vendor
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Look up vendor name from a MAC address (any common format).
     * Returns empty string if not found.
     */
    fun lookup(mac: String): String {
        ensureLoaded()
        if (mac.isBlank()) return ""
        val normalized = mac.uppercase(Locale.US).replace("-", ":").replace(".", ":")
        // Try full 6-digit OUI (first 3 octets)
        val oui = normalized.split(":").take(3).joinToString(":")
        return ouiMap[oui] ?: ""
    }

    fun getVendorOrUnknown(mac: String): String {
        val v = lookup(mac)
        return if (v.isBlank()) "Unknown" else v
    }

    /** Check if the OUI belongs to a known router/AP manufacturer. */
    fun isKnownApVendor(mac: String): Boolean {
        val vendor = lookup(mac).lowercase(Locale.US)
        return AP_KEYWORDS.any { vendor.contains(it) }
    }

    companion object {
        private val AP_KEYWORDS = listOf(
            "cisco", "netgear", "tp-link", "asus", "linksys", "ubiquiti",
            "mikrotik", "dlink", "d-link", "tplink", "aruba", "ruckus",
            "zyxel", "huawei", "xiaomi", "qualcomm", "mediatek", "intel",
            "broadcom", "apple", "samsung", "google", "amazon", "eero"
        )
    }
}
