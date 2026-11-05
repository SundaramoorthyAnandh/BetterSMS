package com.bettersms.app.classify

enum class SenderType { PHONE, SHORT_CODE, ALPHANUMERIC }

/** Suffix on Indian TRAI DLT sender headers, e.g. "AX-HDFCBK-S". */
enum class DltTag { PROMOTIONAL, SERVICE, TRANSACTIONAL, GOVERNMENT, NONE }

data class SenderInfo(
    val raw: String,
    val type: SenderType,
    /** Business header without the operator prefix, e.g. "HDFCBK". */
    val brand: String?,
    val dltTag: DltTag,
    /** Stable key used for blocking and per-sender settings. */
    val key: String,
    val displayName: String,
) {
    val isBusiness: Boolean get() = type != SenderType.PHONE
    /** Alphanumeric senders cannot receive replies. */
    val canReply: Boolean get() = type != SenderType.ALPHANUMERIC
}

object SenderAnalyzer {
    // AX-HDFCBK, VM-AMAZON-T, JD-SBIINB-S, CP-GOVTIN-G
    private val dltRegex = Regex("^[A-Z]{2}-([A-Z0-9]{2,9})(?:-([PSTG]))?$")

    private val knownBrands: List<Pair<String, String>> = listOf(
        "HDFCBK" to "HDFC Bank", "HDFC" to "HDFC Bank",
        "SBI" to "SBI", "CBSSBI" to "SBI", "ATMSBI" to "SBI",
        "ICICI" to "ICICI Bank", "AXISBK" to "Axis Bank", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank", "YESBNK" to "Yes Bank", "IDFCFB" to "IDFC First Bank",
        "INDUS" to "IndusInd Bank", "PNB" to "PNB", "BOI" to "Bank of India",
        "CANBNK" to "Canara Bank", "UNIONB" to "Union Bank", "BOB" to "Bank of Baroda",
        "PAYTM" to "Paytm", "PHONPE" to "PhonePe", "GPAY" to "Google Pay",
        "AMAZON" to "Amazon", "AMZN" to "Amazon", "FLPKRT" to "Flipkart", "FLIPKART" to "Flipkart",
        "MYNTRA" to "Myntra", "SWIGGY" to "Swiggy", "ZOMATO" to "Zomato", "BLINKT" to "Blinkit",
        "ZEPTO" to "Zepto", "AIRTEL" to "Airtel", "JIO" to "Jio", "VIINFO" to "Vi", "VICARE" to "Vi",
        "IRCTC" to "IRCTC", "OLA" to "Ola", "UBER" to "Uber", "GOOGLE" to "Google",
        "WHATSP" to "WhatsApp", "EPFO" to "EPFO", "UIDAI" to "UIDAI", "NPCI" to "NPCI",
    ).sortedByDescending { it.first.length }

    fun analyze(address: String): SenderInfo {
        val raw = address.trim()
        val upper = raw.uppercase()

        dltRegex.matchEntire(upper)?.let { m ->
            val brand = m.groupValues[1]
            return SenderInfo(
                raw = raw,
                type = SenderType.ALPHANUMERIC,
                brand = brand,
                dltTag = tagOf(m.groupValues[2]),
                key = "B:$brand",
                displayName = prettyBrand(brand) ?: brand,
            )
        }

        if (raw.any { it.isLetter() }) {
            val brand = upper.filter { it.isLetterOrDigit() }.ifEmpty { upper }
            return SenderInfo(raw, SenderType.ALPHANUMERIC, brand, DltTag.NONE, "B:$brand", prettyBrand(brand) ?: raw)
        }

        val digits = raw.filter { it.isDigit() }
        if (digits.length in 3..6) {
            return SenderInfo(raw, SenderType.SHORT_CODE, null, DltTag.NONE, "S:$digits", raw)
        }
        // Compare phone numbers on their last 10 digits so "+91 98765 43210" == "09876543210".
        val key = "P:" + digits.takeLast(10).ifEmpty { raw }
        return SenderInfo(raw, SenderType.PHONE, null, DltTag.NONE, key, raw)
    }

    fun keyOf(address: String): String = analyze(address).key

    fun prettyBrand(brand: String): String? =
        knownBrands.firstOrNull { brand.startsWith(it.first) }?.second

    private fun tagOf(s: String) = when (s) {
        "P" -> DltTag.PROMOTIONAL
        "S" -> DltTag.SERVICE
        "T" -> DltTag.TRANSACTIONAL
        "G" -> DltTag.GOVERNMENT
        else -> DltTag.NONE
    }
}
