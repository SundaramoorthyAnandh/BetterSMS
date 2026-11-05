package com.bettersms.app.classify

enum class Category(val label: String) {
    PERSONAL("Personal"),
    OTP("OTP"),
    TRANSACTIONS("Transactions"),
    BILLS("Bills"),
    OFFERS("Offers"),
    UPDATES("Updates"),
    SPAM("Spam"),
}

enum class TxnDirection { DEBIT, CREDIT, UNKNOWN }

data class TransactionInfo(val amount: String, val direction: TxnDirection)

data class Classification(
    val category: Category,
    val otp: OtpInfo?,
    val threat: ThreatReport,
    val transaction: TransactionInfo?,
) {
    companion object {
        val OUTGOING = Classification(Category.PERSONAL, null, ThreatReport.SAFE, null)
    }
}

object TransactionExtractor {
    private val IC = RegexOption.IGNORE_CASE
    private val amount = Regex("(rs\\.?|inr|₹|usd|\\$|eur|€|gbp|£)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", IC)
    private val debit = Regex("\\b(debited|spent|withdrawn|paid|sent|deducted|purchase|debit(?! card)|transferred to)\\b", IC)
    private val credit = Regex("\\b(credited|received|deposited|refunded|refund|credit(?! card))\\b", IC)

    fun extract(body: String): TransactionInfo? {
        val m = amount.find(body) ?: return null
        val symbol = when (m.groupValues[1].lowercase().trimEnd('.')) {
            "usd", "$" -> "$"
            "eur", "€" -> "€"
            "gbp", "£" -> "£"
            else -> "₹"
        }
        val value = m.groupValues[2].trimEnd(',', '.')
        if (value.isEmpty()) return null
        val d = debit.find(body)?.range?.first ?: Int.MAX_VALUE
        val c = credit.find(body)?.range?.first ?: Int.MAX_VALUE
        val direction = when {
            d == Int.MAX_VALUE && c == Int.MAX_VALUE -> TxnDirection.UNKNOWN
            d < c -> TxnDirection.DEBIT
            else -> TxnDirection.CREDIT
        }
        return TransactionInfo(symbol + value, direction)
    }
}

/**
 * On-device, rule-based message categoriser. Nothing is sent anywhere.
 */
object MessageClassifier {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val transactionRules = listOf(
        rx("\\b(debited|credited|withdrawn|deducted|spent)\\b") to 3,
        rx("\\b(a/c|acct|account)\\b.{0,30}(x+|\\*+)\\d{3,4}") to 2,
        rx("\\b(upi|imps|neft|rtgs|nach|ecs)\\b") to 2,
        rx("(avl\\.? ?bal|available balance|avbl bal|a/c bal|account balance|avl lmt|available limit)") to 3,
        rx("\\b(txn|transaction)\\b") to 1,
        rx("(rs\\.?|inr|₹)\\s*[0-9]") to 1,
        rx("\\bcard (ending|xx|no)") to 1,
        rx("\\b(transferred|received|sent) (rs\\.?|inr|₹)") to 2,
        rx("\\bpayment (of|for) .{0,30}(successful|received|done)") to 2,
        rx("\\brefund (of|for|processed|initiated|credited)") to 2,
    )
    private val billRules = listOf(
        rx("\\b(bill|invoice|statement)\\b") to 2,
        rx("(\\bdue (date|on|by|amount)|\\bis due\\b|overdue|payment due|amount due|min(imum)? (amt|amount) due)") to 3,
        rx("\\b(recharge|plan|pack|validity|subscription)\\b.{0,40}\\b(expir|end|due|renew)") to 3,
        rx("\\b(emi|premium|installment|instalment)\\b") to 2,
        rx("\\b(electricity|broadband|postpaid|gas|water|dth|fastag|insurance|policy)\\b") to 1,
        rx("\\b(pay now|pay before|renew now|avoid late fee|late (fee|payment))\\b") to 2,
        rx("\\boutstanding\\b") to 1,
    )
    private val offerRules = listOf(
        rx("\\d{1,2}\\s?% ?(off|discount|cashback)") to 3,
        rx("\\b(offer|offers|sale|discount|deal|deals|coupon|voucher|promo)\\b") to 2,
        rx("\\b(flat|upto|up to) (rs\\.?|₹|inr)?\\s?\\d+") to 1,
        rx("\\b(shop now|buy now|order now|book now|grab|hurry|limited (time|period|stock)|last day|ends (today|tonight|soon)|don'?t miss)\\b") to 2,
        rx("\\b(free|win|exclusive|special price|lowest price|best price|new arrivals?|launch(ed)?|upgrade)\\b") to 1,
        rx("\\b(pre-?approved|instant loan|loan offer|credit limit|apply now|get a loan)\\b") to 2,
        rx("(t&c|t&cs|\\btnc\\b|opt[- ]?out|\\bunsub|\\bstop to\\b|sms stop)") to 2,
        rx("\\bcashback\\b") to 1,
        rx("\\b(click|visit|download|install|tap)\\b.{0,20}(http|www|bit\\.ly|link|app)") to 1,
    )
    private val updateRules = listOf(
        rx("\\b(order|shipment|parcel|package|consignment)\\b.{0,40}\\b(placed|confirmed|shipped|dispatched|delivered|out for delivery|arriving|picked up|cancelled|returned)") to 3,
        rx("(out for delivery|has been delivered|tracking|track your|\\bawb\\b|delivery (partner|executive|agent))") to 2,
        rx("\\b(pnr|booking|ticket|boarding|flight|train|bus|seat|coach|berth|check-?in|gate)\\b") to 2,
        rx("\\b(appointment|scheduled|reminder|confirmed|cancelled|rescheduled)\\b") to 1,
        rx("\\b(service request|complaint|ticket no|sr no|request (id|no))\\b") to 2,
        rx("(data (usage|balance|pack)|daily (data|quota)|\\d+% (data|of your))") to 2,
        rx("\\b(welcome|thank you for|thanks for|registered|successfully (registered|updated|activated|linked))\\b") to 1,
        rx("\\b(alert|update|notice|information)\\b") to 1,
    )

    fun classify(
        address: String,
        body: String,
        isContact: Boolean = false,
        scamProtection: Boolean = true,
    ): Classification {
        val sender = SenderAnalyzer.analyze(address)
        val fullThreat = ThreatDetector.analyze(body, sender, isContact)
        val threat = if (scamProtection) fullThreat else ThreatReport.SAFE.copy(links = fullThreat.links)
        val otp = OtpExtractor.extract(body)?.let { o ->
            if (o.service == null && sender.isBusiness) o.copy(service = sender.displayName) else o
        }
        val txn = TransactionExtractor.extract(body)

        val category = when {
            threat.level == ThreatLevel.DANGEROUS -> Category.SPAM
            otp != null -> Category.OTP
            isContact -> Category.PERSONAL
            !sender.isBusiness -> personalOrPromo(body)
            else -> businessCategory(body, sender)
        }
        return Classification(category, otp, threat, if (category == Category.TRANSACTIONS || category == Category.BILLS) txn else null)
    }

    private fun score(body: String, rules: List<Pair<Regex, Int>>): Int =
        rules.sumOf { (r, w) -> if (r.containsMatchIn(body)) w else 0 }

    private fun personalOrPromo(body: String): Category {
        val offers = score(body, offerRules)
        return if (offers >= 5) Category.OFFERS else Category.PERSONAL
    }

    private fun businessCategory(body: String, sender: SenderInfo): Category {
        val scores = linkedMapOf(
            Category.TRANSACTIONS to score(body, transactionRules),
            Category.BILLS to score(body, billRules),
            Category.UPDATES to score(body, updateRules),
            Category.OFFERS to score(body, offerRules),
        )
        when (sender.dltTag) {
            DltTag.PROMOTIONAL -> scores[Category.OFFERS] = scores.getValue(Category.OFFERS) + 4
            DltTag.TRANSACTIONAL -> scores[Category.TRANSACTIONS] = scores.getValue(Category.TRANSACTIONS) + 1
            DltTag.SERVICE -> scores[Category.UPDATES] = scores.getValue(Category.UPDATES) + 1
            DltTag.GOVERNMENT -> scores[Category.UPDATES] = scores.getValue(Category.UPDATES) + 2
            DltTag.NONE -> Unit
        }
        // A bill reminder mentions an amount but is not a transaction.
        if (scores.getValue(Category.BILLS) >= 4) {
            scores[Category.TRANSACTIONS] = scores.getValue(Category.TRANSACTIONS) - 1
        }
        // linkedMapOf keeps priority order on ties: Transactions > Bills > Updates > Offers.
        val best = scores.entries.maxByOrNull { it.value }!!
        return if (best.value < 2) {
            if (sender.dltTag == DltTag.PROMOTIONAL) Category.OFFERS else Category.UPDATES
        } else {
            best.key
        }
    }
}
