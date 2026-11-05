package com.bettersms.app.classify

enum class ThreatLevel { SAFE, SUSPICIOUS, DANGEROUS }

data class LinkInfo(
    val url: String,
    val host: String,
    val start: Int,
    val end: Int,
    val suspicious: Boolean,
    val reason: String?,
)

data class ThreatReport(
    val level: ThreatLevel,
    val score: Int,
    val reasons: List<String>,
    val links: List<LinkInfo>,
) {
    val isThreat: Boolean get() = level != ThreatLevel.SAFE

    companion object {
        val SAFE = ThreatReport(ThreatLevel.SAFE, 0, emptyList(), emptyList())
    }
}

/**
 * Offline scam / phishing heuristics: suspicious links, impersonation,
 * KYC & lottery scams, job scams, requests for OTP/PIN, etc.
 */
object ThreatDetector {
    private val IC = RegexOption.IGNORE_CASE

    private val urlRegex = Regex(
        "((?:https?://|www\\.)[^\\s<>\"']+|" +
            "(?<![@\\w.])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+" +
            "(?:com|in|net|org|co|io|ly|gl|me|xyz|top|click|link|live|info|biz|site|online|app|cc|tk|ml|ga|cf|gq|" +
            "icu|buzz|shop|store|vip|win|loan|work|rest|cyou|sbs|page|to|gd|id|su|ru|cn|bank|gov)" +
            "(?![a-z0-9-])(?:/[^\\s<>\"']*)?)",
        IC,
    )

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.ly", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "goo.gl", "ow.ly",
        "rebrand.ly", "tiny.cc", "s.id", "v.gd", "t.co", "bl.ink", "short.io", "tny.im", "shorte.st",
        "adf.ly", "u.to", "qr.ae", "lnkd.in", "surl.li", "clck.ru", "urlz.fr", "i8.ae", "bitly.ws",
    )
    private val riskyTlds = setOf(
        "xyz", "top", "click", "link", "live", "icu", "buzz", "tk", "ml", "ga", "cf", "gq", "work", "loan",
        "win", "rest", "cyou", "sbs", "vip", "su", "ru", "cn", "online", "site",
    )
    // Brand words commonly abused in Indian phishing domains, with their genuine domains.
    private val brandDomains: Map<String, List<String>> = mapOf(
        "sbi" to listOf("sbi.co.in", "onlinesbi.sbi", "sbi.bank.in", "sbicard.com", "yonosbi.com", "onlinesbi.com"),
        "hdfc" to listOf("hdfcbank.com", "hdfc.bank.in", "hdfclife.com", "hdfc.com"),
        "icici" to listOf("icicibank.com", "icici.bank.in", "iciciprulife.com"),
        "axis" to listOf("axisbank.com", "axis.bank.in"),
        "kotak" to listOf("kotak.com", "kotak.bank.in"),
        "paytm" to listOf("paytm.com", "paytm.me"),
        "phonepe" to listOf("phonepe.com"),
        "amazon" to listOf("amazon.in", "amazon.com", "amzn.in", "amzn.to"),
        "flipkart" to listOf("flipkart.com", "fkrt.it", "fkrt.co"),
        "netflix" to listOf("netflix.com"),
        "indiapost" to listOf("indiapost.gov.in"),
        "irctc" to listOf("irctc.co.in"),
        "airtel" to listOf("airtel.in", "airtel.com"),
        "jio" to listOf("jio.com"),
        "uidai" to listOf("uidai.gov.in"),
        "aadhaar" to listOf("uidai.gov.in"),
        "aadhar" to listOf("uidai.gov.in"),
        "incometax" to listOf("incometax.gov.in"),
        "epfo" to listOf("epfindia.gov.in"),
        "npci" to listOf("npci.org.in"),
        "rbi" to listOf("rbi.org.in"),
        "kyc" to emptyList(),
        "fedex" to listOf("fedex.com"),
        "dhl" to listOf("dhl.com"),
        "bluedart" to listOf("bluedart.com"),
    )
    private val trustedSuffixes = listOf(".gov.in", ".nic.in", ".bank.in", ".gov")

    private data class Rule(val regex: Regex, val weight: Int, val reason: String)

    private val rules = listOf(
        Rule(Regex("kyc.{0,40}(update|expir|pending|suspend|block|verify|complete|deactivat)|(update|complete|verify).{0,20}kyc", IC), 3, "Fake KYC update request"),
        Rule(Regex("(account|a/c|sim|card|wallet|connection|number|yono|net ?banking).{0,30}(will be |has been |is |get )?(block|suspend|deactivat|disconnect|terminat|frozen|freeze|closed)", IC), 3, "Threatens to block your account"),
        Rule(Regex("(you have won|you've won|you won|congratulations.{0,40}(won|selected|winner)|lottery|lucky draw|prize money|jackpot|\\bkbc\\b)", IC), 3, "Lottery / prize scam pattern"),
        Rule(Regex("claim (your )?(reward|prize|gift|cashback|refund|bonus|points)", IC), 2, "Asks you to claim a reward"),
        Rule(Regex("(income tax|\\bit\\b|tax) refund", IC), 2, "Tax refund bait"),
        Rule(Regex("(part[ -]time|work from home|daily (income|earning)|earn (rs\\.?|₹|inr)? ?\\d+.{0,20}(per|a|/) ?(day|hour|daily)|like (and|&) earn|youtube like|telegram)", IC), 3, "Job / easy-money scam pattern"),
        Rule(Regex("(urgent|immediately|within 24 ?h|today itself|last (warning|chance|reminder)|act now|expire[sd]? today)", IC), 1, "Creates urgency"),
        Rule(Regex("electricity.{0,60}(disconnect|cut|discontinu)", IC), 3, "Electricity disconnection scam"),
        Rule(Regex("(parcel|package|shipment|courier).{0,40}(on hold|held|undeliver|failed|incomplete address|customs)", IC), 2, "Fake parcel / customs notice"),
        Rule(Regex("(pre-?approved|instant) loan|loan (approved|sanctioned).{0,30}(click|link|call)", IC), 1, "Instant-loan bait"),
        Rule(Regex("whatsapp.{0,20}\\+?\\d{10}", IC), 1, "Moves chat to WhatsApp"),
        Rule(Regex("(pan( card)?|aadhaa?r).{0,30}(update|link|block|expir|deactivat)", IC), 2, "PAN / Aadhaar update bait"),
        Rule(Regex("(anydesk|teamviewer|quick ?support|rustdesk)", IC), 4, "Asks to install remote-access app"),
        Rule(Regex("\\.apk\\b", IC), 4, "Asks to install an APK file"),
    )

    private val credentialAsk = Regex("(share|send|tell|forward|provide|reply with)\\s+(?:me\\s+|us\\s+)?(?:the\\s+|your\\s+)?(otp|pin|password|cvv|code|card number)", IC)
    private val negation = Regex("(not|never|don't|dont|no one|nobody|anyone)\\W*\\w*\\W*$", IC)
    private val bankClaim = Regex("(dear (customer|user|sir)|your (bank )?account|\\bbank\\b|\\bkyc\\b|credit card)", IC)
    private val moneyMention = Regex("(rs\\.?|inr|₹)\\s*\\d", IC)

    fun analyze(body: String, sender: SenderInfo, isContact: Boolean): ThreatReport {
        val links = extractLinks(body)
        var score = 0
        val reasons = linkedSetOf<String>()

        for (r in rules) {
            if (r.regex.containsMatchIn(body)) {
                score += r.weight
                reasons += r.reason
            }
        }

        credentialAsk.findAll(body).forEach { m ->
            val before = body.substring(maxOf(0, m.range.first - 20), m.range.first)
            if (!negation.containsMatchIn(before)) {
                score += 4
                reasons += "Asks you to share an OTP / PIN"
            }
        }

        val badLinks = links.filter { it.suspicious }
        if (badLinks.isNotEmpty()) {
            score += minOf(6, 3 * badLinks.size)
            badLinks.mapNotNullTo(reasons) { it.reason }
        }
        if (links.isNotEmpty() && score > 0) {
            score += 1
            reasons += "Contains a link"
        }

        if (sender.type == SenderType.PHONE && !isContact) {
            if (links.isNotEmpty()) score += 1
            if (bankClaim.containsMatchIn(body)) {
                score += 2
                reasons += "Claims to be a bank but comes from a personal number"
            }
            if (moneyMention.containsMatchIn(body) && links.isNotEmpty()) score += 1
        }
        if (isContact) score -= 2

        val level = when {
            score >= 6 -> ThreatLevel.DANGEROUS
            score >= 3 -> ThreatLevel.SUSPICIOUS
            else -> ThreatLevel.SAFE
        }
        // Most actionable first: what's wrong with the link, then the wording.
        val linkReasons = badLinks.mapNotNull { it.reason }
        val ordered = (linkReasons + reasons).distinct()
        return ThreatReport(level, score.coerceAtLeast(0), if (level == ThreatLevel.SAFE) emptyList() else ordered, links)
    }

    fun extractLinks(body: String): List<LinkInfo> = urlRegex.findAll(body).mapNotNull { m ->
        var url = m.value
        var end = m.range.last + 1
        while (url.isNotEmpty() && url.last() in ".,);:!?'\"]") {
            url = url.dropLast(1)
            end--
        }
        val host = hostOf(url) ?: return@mapNotNull null
        // Avoid treating things like "Rs.500" or "a.m" as links: require a real dotted host.
        if (!host.contains('.') || host.length < 4) return@mapNotNull null
        val (suspicious, reason) = assessLink(url, host)
        LinkInfo(url, host, m.range.first, end, suspicious, reason)
    }.toList()

    fun hostOf(url: String): String? {
        val noScheme = url.replaceFirst(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        val authority = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().removePrefix("www.")
        return host.ifEmpty { null }
    }

    private fun assessLink(url: String, host: String): Pair<Boolean, String?> {
        val tld = host.substringAfterLast('.')
        val labels = host.split('.')

        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) return true to "Link points to a raw IP address"
        if (host.contains("xn--")) return true to "Link uses look-alike (punycode) characters"
        val authority = url.replaceFirst(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "").substringBefore('/')
        if ('@' in authority) return true to "Link hides its real destination"
        if (host in shorteners) return true to "Shortened link hides the real website ($host)"

        if (trustedSuffixes.any { host.endsWith(it) }) return false to null

        for ((brand, official) in brandDomains) {
            if (host.contains(brand)) {
                val genuine = official.any { host == it || host.endsWith(".$it") }
                if (!genuine) return true to "Pretends to be ${brand.uppercase()} but isn't the official site"
            }
        }
        if (tld in riskyTlds) return true to "Uses a high-risk domain ending (.$tld)"
        if (labels.firstOrNull()?.count { it == '-' }?.let { it >= 2 } == true || host.length > 40) {
            return true to "Unusual, auto-generated looking web address"
        }
        return false to null
    }
}
