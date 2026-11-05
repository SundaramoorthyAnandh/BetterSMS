package com.bettersms.app.classify

data class OtpInfo(
    val code: String,
    /** Who the code is for, e.g. "Google" or "HDFC Bank". */
    val service: String?,
    /** Validity stated in the message, if any. */
    val validityMinutes: Int?,
    /** Message itself warns "do not share". */
    val doNotShareWarning: Boolean,
) {
    companion object {
        const val DEFAULT_VALIDITY_MINUTES = 10
    }

    val effectiveValidityMinutes: Int get() = validityMinutes ?: DEFAULT_VALIDITY_MINUTES
}

/**
 * Finds one-time passwords in SMS text without false positives on amounts,
 * account masks, dates, reference numbers or phone numbers.
 */
object OtpExtractor {
    private val IC = RegexOption.IGNORE_CASE

    private val strongKeyword = Regex(
        "\\b(otp|one[ -]?time[ -]?(?:password|passcode|pin|code)|verification code|verification pin|verify code|" +
            "security code|auth(?:entication|orization)? code|log[ -]?in code|sign[ -]?in code|passcode|access code|" +
            "confirmation code|activation code|secret code|2fa|two[ -]factor|mpin|tpin)\\b",
        IC,
    )
    private val weakKeyword = Regex("\\b(code|pin|password|token)\\b", IC)

    private val promoGuard = Regex("(\\d+\\s?% ?off|\\boff\\b|discount|coupon|cashback|promo|voucher|\\bsale\\b|\\boffer)", IC)
    private val txnGuard = Regex("\\b(debited|credited|spent|withdrawn|deducted)\\b", IC)

    // 4–8 digit groups not glued to letters/digits (so "XX1234" or "A12345" never match).
    private val numericCandidate = Regex("(?<![A-Za-z0-9])([0-9]{4,8})(?![0-9])")
    // 6–8 char uppercase codes with at least one letter and one digit.
    private val alnumCandidate = Regex("(?<![A-Za-z0-9])(?=[A-Z0-9]*[0-9])(?=[A-Z0-9]*[A-Z])([A-Z0-9]{6,8})(?![A-Za-z0-9])")

    private val badPrefix = Regex(
        "(?:^|[^a-z0-9])(rs\\.?|inr|₹|\\$|usd|eur|€|£|amt|amount|bal|balance|limit|a/c(?: no)?\\.?|ac|acct|account|card|" +
            "ending(?: with| in)?|ends with|ref(?:erence)?(?: no| num(?:ber)?)?\\.?|txn(?: id| no)?\\.?|transaction id|" +
            "order(?: id| no)?\\.?|id|no\\.?|#|call|dial|contact|helpline|whatsapp|sms|toll[ -]?free|tel|ph|phone|" +
            "mob(?:ile)?|pnr|flight|train|bill no\\.?|invoice|block|to|" +
            "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?,?)\\s*[:#.\\-]?\\s*$",
        IC,
    )
    private val badSuffix = Regex("^\\s*(%|mb\\b|gb\\b|pts\\b|points\\b|kg\\b|km\\b|/-)", IC)

    private val validityRegex = Regex(
        "(?:valid|validity|expires?|expiring|expiry)\\s*(?:is|for|in|within|till|upto|up to|only for|only)?\\s*" +
            "(?:the next\\s*|next\\s*)?(\\d{1,3})\\s*(seconds|second|secs|sec|minutes|minute|mins|min|hours|hour|hrs|hr|m|h)\\b",
        IC,
    )
    private val doNotShare = Regex("\\b(do not|don't|dont|never|pls do not|please do not)\\s+(share|disclose|tell|give)", IC)

    private val servicePatterns = listOf(
        Regex("\\bis your ([A-Z][A-Za-z0-9&.'-]+(?: [A-Z][A-Za-z0-9&.'-]+)?) (?i:verification|login|log-in|security|one|otp|account|confirmation|authentication|sign|activation|code|password|pin)"),
        Regex("(?i:otp|code|password|pin)\\s+(?i:for|to)\\s+(?:(?i:login|log in|sign in|signing in|logging in|access|accessing|verify|verifying|your)\\s+(?:(?i:to|into|on|your)\\s+)?)?([A-Z][A-Za-z0-9&.'-]+(?: [A-Z][A-Za-z0-9&.'-]+)?)"),
        Regex("^(?:<#>\\s*)?\\[([^\\]]{2,24})\\]"),
        Regex("(?i:welcome to) ([A-Z][A-Za-z0-9&.'-]+)"),
    )
    private val serviceStopwords = setOf(
        "Login", "Transaction", "Your", "The", "Rs", "INR", "Txn", "Verification", "Account", "Registration",
        "Online", "Dear", "Customer", "User", "OTP", "A", "An", "This", "Mobile", "Payment", "Card", "Net",
        "Banking", "Sign", "Log", "Use", "Verify", "Code", "Pin", "PIN", "Is", "It", "Every", "Any",
    )

    fun extract(body: String): OtpInfo? {
        if (body.isBlank()) return null
        val strong = strongKeyword.findAll(body).map { it.range }.toList()
        val weak = weakKeyword.findAll(body).map { it.range }.toList()
        if (strong.isEmpty() && weak.isEmpty()) return null
        if (strong.isEmpty() && (promoGuard.containsMatchIn(body) || txnGuard.containsMatchIn(body))) return null

        data class Candidate(val code: String, val distance: Int, val numeric: Boolean)

        val candidates = mutableListOf<Candidate>()

        for (m in numericCandidate.findAll(body)) {
            val range = m.groups[1]!!.range
            if (!isCleanContext(body, range)) continue
            val distance = proximity(range, strong, weak, strongMax = 110, weakMax = 25) ?: continue
            candidates += Candidate(m.groupValues[1], distance, numeric = true)
        }
        if (strong.isNotEmpty()) {
            for (m in alnumCandidate.findAll(body)) {
                val range = m.groups[1]!!.range
                if (!isCleanContext(body, range)) continue
                val distance = proximity(range, strong, emptyList(), strongMax = 30, weakMax = 0) ?: continue
                candidates += Candidate(m.groupValues[1], distance + 5, numeric = false)
            }
        }
        val best = candidates.minWithOrNull(
            compareBy<Candidate> { it.distance }
                .thenBy { if (it.numeric) 0 else 1 }
                .thenBy { kotlin.math.abs(it.code.length - 6) },
        ) ?: return null

        return OtpInfo(
            code = best.code,
            service = findService(body),
            validityMinutes = findValidity(body),
            doNotShareWarning = doNotShare.containsMatchIn(body),
        )
    }

    private fun isCleanContext(body: String, range: IntRange): Boolean {
        val start = range.first
        val end = range.last + 1
        val prev = body.getOrNull(start - 1)
        val next = body.getOrNull(end)

        // Dates, times, decimals, ranges: 05-11-2026, 2026/11/05, 10:30, 2500.50
        if (prev != null && prev in "/.:,₹$" && body.getOrNull(start - 2)?.isDigit() == true) return false
        if (prev == '-' && body.getOrNull(start - 2)?.isDigit() == true) return false
        if (next != null && next in "/:-" && body.getOrNull(end + 1)?.isDigit() == true) return false
        if (next != null && next in ".," && body.getOrNull(end + 1)?.isDigit() == true) return false
        if (prev == '₹' || prev == '$') return false

        val before = body.substring(maxOf(0, start - 18), start)
        if (badPrefix.containsMatchIn(before)) return false
        val after = body.substring(end, minOf(body.length, end + 8))
        if (badSuffix.containsMatchIn(after)) return false
        return true
    }

    private fun proximity(
        range: IntRange,
        strong: List<IntRange>,
        weak: List<IntRange>,
        strongMax: Int,
        weakMax: Int,
    ): Int? {
        fun dist(a: IntRange, b: IntRange): Int = when {
            a.first > b.last -> a.first - b.last - 1
            b.first > a.last -> b.first - a.last - 1
            else -> 0
        }
        val s = strong.minOfOrNull { dist(range, it) }?.takeIf { it <= strongMax }
        val w = weak.minOfOrNull { dist(range, it) }?.takeIf { it <= weakMax }?.let { it + 10 }
        return listOfNotNull(s, w).minOrNull()
    }

    private fun findValidity(body: String): Int? {
        val m = validityRegex.find(body) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        val minutes = when {
            unit.startsWith("s") -> (n + 59) / 60
            unit.startsWith("h") -> n * 60
            else -> n
        }
        return minutes.coerceIn(1, 7 * 24 * 60)
    }

    private fun findService(body: String): String? {
        for (p in servicePatterns) {
            val m = p.find(body) ?: continue
            val name = m.groupValues[1].trim().trimEnd('.', ',', ':', '-', '\'')
            val first = name.substringBefore(' ')
            if (name.length < 2 || first in serviceStopwords) continue
            return name
        }
        return null
    }
}
