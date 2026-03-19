package com.wifiradarx.app.intelligence

/**
 * Parses WifiScanResult.capabilities strings and computes a security score.
 */
class SecurityAuditor {

    enum class SecurityLevel { SECURE, CAUTION, DANGER }

    data class AuditResult(
        val score: Int,      // 0-100
        val level: SecurityLevel,
        val badges: List<String>,
        val issues: List<String>,
        val details: String
    )

    /**
     * Score a capabilities string like "[WPA2-PSK-CCMP][ESS]"
     * Higher score = more secure.
     */
    fun scoreCapabilities(capabilities: String): Int {
        val caps = capabilities.uppercase()
        var score = 50

        when {
            caps.contains("WPA3") && caps.contains("SAE") -> score += 30
            caps.contains("WPA3") -> score += 25
            caps.contains("WPA2") && caps.contains("CCMP") -> score += 15
            caps.contains("WPA2") -> score += 10
            caps.contains("WPA-") || caps.contains("[WPA-") -> score += 5
            caps.isEmpty() || caps.contains("ESS") && !caps.contains("WPA") -> score -= 40
        }

        if (caps.contains("WPS")) score -= 30
        if (caps.contains("TKIP") && !caps.contains("CCMP")) score -= 15
        if (caps.contains("OWE")) score += 10
        if (caps.contains("PMF") || caps.contains("MFP")) score += 10
        if (caps.contains("EAP")) score += 15   // enterprise
        if (caps.contains("IBSS")) score -= 10  // ad-hoc

        return score.coerceIn(0, 100)
    }

    fun audit(ssid: String, capabilities: String, rssi: Int): AuditResult {
        val caps = capabilities.uppercase()
        val issues = mutableListOf<String>()
        val badges = mutableListOf<String>()
        var score = scoreCapabilities(capabilities)

        // Detect specific issues
        if (caps.isEmpty() || (!caps.contains("WPA") && !caps.contains("OWE"))) {
            issues.add("Open network — traffic is unencrypted")
            badges.add("OPEN")
        }
        if (caps.contains("WPS")) {
            issues.add("WPS enabled — vulnerable to brute-force")
            badges.add("WPS")
        }
        if (caps.contains("TKIP") && !caps.contains("CCMP")) {
            issues.add("TKIP-only encryption — deprecated, use AES/CCMP")
            badges.add("WEAK-ENC")
        }
        if (caps.contains("WPA-") && !caps.contains("WPA2") && !caps.contains("WPA3")) {
            issues.add("WPA1 only — outdated protocol")
        }
        if (caps.contains("WPA2") && !caps.contains("WPA3")) {
            issues.add("WPA2 only — upgrade to WPA3 for better security")
        }
        if (caps.contains("WPA3")) badges.add("WPA3")
        if (caps.contains("EAP")) badges.add("ENTERPRISE")
        if (caps.contains("PMF") || caps.contains("MFP")) badges.add("PMF")

        // Weak signal could indicate rogue AP
        if (rssi > -40) badges.add("STRONG-SIGNAL")

        val level = when {
            score >= 70 -> SecurityLevel.SECURE
            score >= 45 -> SecurityLevel.CAUTION
            else -> SecurityLevel.DANGER
        }

        val details = buildString {
            append("Capabilities: $capabilities\n")
            append("Score: $score/100\n")
            if (issues.isEmpty()) append("No issues detected.") else {
                append("Issues:\n")
                issues.forEach { append("• $it\n") }
            }
        }

        return AuditResult(score, level, badges, issues, details)
    }

    fun getOverallRating(scores: List<Int>): String {
        if (scores.isEmpty()) return "No data"
        val avg = scores.average()
        return when {
            avg >= 70 -> "Network environment is SECURE (avg ${avg.toInt()}/100)"
            avg >= 45 -> "Network environment has RISKS (avg ${avg.toInt()}/100)"
            else -> "Network environment is DANGEROUS (avg ${avg.toInt()}/100)"
        }
    }
}
