package com.wifiradarx.app.intelligence

/**
 * Rogue AP detection with multi-factor threat scoring.
 * Compares current scan against trusted BSSID profiles.
 */
class RogueApDetector(private val alertThreshold: Int = 65) {

    data class TrustedProfile(
        val bssid: String,
        val ssid: String,
        val expectedChannel: Int,
        val expectedCapabilities: String,
        val vendorOui: String,
        val hasWps: Boolean
    )

    data class ThreatAssessment(
        val bssid: String,
        val ssid: String,
        val threatScore: Int,    // 0-100
        val isAlert: Boolean,
        val factors: List<ThreatFactor>
    )

    data class ThreatFactor(val description: String, val points: Int)

    private val trustedProfiles = mutableMapOf<String, TrustedProfile>()
    private val ssidBssidHistory = mutableMapOf<String, MutableSet<String>>() // ssid -> known bssids

    fun addTrustedProfile(profile: TrustedProfile) {
        trustedProfiles[profile.bssid] = profile
        ssidBssidHistory.getOrPut(profile.ssid) { mutableSetOf() }.add(profile.bssid)
    }

    fun removeTrustedProfile(bssid: String) {
        trustedProfiles[bssid]?.let { ssidBssidHistory[it.ssid]?.remove(bssid) }
        trustedProfiles.remove(bssid)
    }

    /**
     * Assess threat level for a detected AP.
     * @param bssid detected BSSID
     * @param ssid detected SSID
     * @param channel detected channel
     * @param capabilities detected capabilities string
     * @param vendorOui detected OUI
     * @param rssi current RSSI (used for direction mismatch proxy)
     */
    fun assess(
        bssid: String,
        ssid: String,
        channel: Int,
        capabilities: String,
        vendorOui: String,
        rssi: Int
    ): ThreatAssessment {
        val factors = mutableListOf<ThreatFactor>()
        var score = 0

        val trusted = trustedProfiles[bssid]
        val knownBssidsForSsid = ssidBssidHistory[ssid] ?: emptySet<String>()

        if (trusted == null) {
            // Unknown BSSID broadcasting a known SSID
            if (knownBssidsForSsid.isNotEmpty() && bssid !in knownBssidsForSsid) {
                factors.add(ThreatFactor("SSID matches known network but BSSID is new", 40))
                score += 40
            }
        } else {
            // Known BSSID — check for changes
            if (trusted.ssid != ssid) {
                factors.add(ThreatFactor("SSID changed from '${trusted.ssid}' to '$ssid'", 40))
                score += 40
            }

            // OUI vendor mismatch
            if (vendorOui.isNotBlank() && trusted.vendorOui.isNotBlank()
                && !vendorOui.startsWith(trusted.vendorOui.take(8), ignoreCase = true)
            ) {
                factors.add(ThreatFactor("Vendor OUI changed: ${trusted.vendorOui} → $vendorOui", 20))
                score += 20
            }

            // Security capabilities changed
            val oldHasWpa3 = trusted.expectedCapabilities.uppercase().contains("WPA3")
            val newHasWpa3 = capabilities.uppercase().contains("WPA3")
            val oldHasWpa2 = trusted.expectedCapabilities.uppercase().contains("WPA2")
            val newHasOpen = !capabilities.uppercase().contains("WPA")
            if (oldHasWpa3 && !newHasWpa3) {
                factors.add(ThreatFactor("Security downgraded: WPA3 removed", 15))
                score += 15
            }
            if (oldHasWpa2 && newHasOpen) {
                factors.add(ThreatFactor("Security downgraded: network now open", 25))
                score += 25
            }

            // Channel changed
            if (trusted.expectedChannel != channel && channel != 0) {
                factors.add(ThreatFactor("Channel changed: ${trusted.expectedChannel} → $channel", 10))
                score += 10
            }

            // WPS appeared unexpectedly
            val newHasWps = capabilities.uppercase().contains("WPS")
            if (!trusted.hasWps && newHasWps) {
                factors.add(ThreatFactor("WPS appeared on previously WPS-free AP", 10))
                score += 10
            }
        }

        // Unusually strong signal — possible AP placed close for evil-twin attack
        if (rssi > -35) {
            factors.add(ThreatFactor("Unusually strong signal (${rssi} dBm) — AP may be physically close", 15))
            score += 15
        }

        score = score.coerceIn(0, 100)
        return ThreatAssessment(bssid, ssid, score, score >= alertThreshold, factors)
    }

    fun getTrustedProfiles(): Map<String, TrustedProfile> = trustedProfiles.toMap()
    fun clearTrustedProfiles() { trustedProfiles.clear(); ssidBssidHistory.clear() }
}
