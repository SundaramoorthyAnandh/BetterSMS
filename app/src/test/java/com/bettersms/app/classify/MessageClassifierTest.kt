package com.bettersms.app.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageClassifierTest {

    private fun cat(address: String, body: String, contact: Boolean = false) =
        MessageClassifier.classify(address, body, contact).category

    @Test fun otpFromBank() {
        val c = MessageClassifier.classify("AX-HDFCBK-T", "Your OTP is 123456 for txn of Rs 499 at Netflix. Do not share it with anyone.")
        assertEquals(Category.OTP, c.category)
        assertEquals("123456", c.otp!!.code)
        assertEquals(ThreatLevel.SAFE, c.threat.level)
    }

    @Test fun transactionAlert() {
        val c = MessageClassifier.classify(
            "VM-HDFCBK-S",
            "Rs 2,500.00 debited from A/c XX1234 on 05-Nov-26 to VPA shop@ybl. UPI Ref 412345678901. Not you? Call 18002586161",
        )
        assertEquals(Category.TRANSACTIONS, c.category)
        assertNotNull(c.transaction)
        assertEquals("₹2,500.00", c.transaction!!.amount)
        assertEquals(TxnDirection.DEBIT, c.transaction!!.direction)
    }

    @Test fun billReminder() {
        assertEquals(Category.BILLS, cat("JD-AIRTEL-S", "Your Airtel postpaid bill of Rs 599 is due on 10-Nov. Pay now to avoid late fee."))
    }

    @Test fun promotional() {
        assertEquals(Category.OFFERS, cat("BZ-MYNTRA-P", "Flat 60% OFF on top brands! Sale ends tonight. Shop now: myntra.com/sale T&C"))
    }

    @Test fun deliveryUpdate() {
        assertEquals(Category.UPDATES, cat("VK-AMAZON-S", "Your order #405-123 has been shipped and will be delivered by Friday. Track your package in the app."))
    }

    @Test fun personalChat() {
        assertEquals(Category.PERSONAL, cat("+919876543210", "Hey! Are we still meeting for lunch today?"))
    }

    @Test fun contactIsAlwaysPersonal() {
        assertEquals(Category.PERSONAL, cat("+919876543210", "Get 20% off, use my referral", contact = true))
    }

    @Test fun kycPhishingIsSpam() {
        val c = MessageClassifier.classify(
            "+919812345678",
            "Dear customer, your SBI account will be blocked today. Update your KYC immediately: http://sbi-kyc-update.xyz/login",
        )
        assertEquals(Category.SPAM, c.category)
        assertEquals(ThreatLevel.DANGEROUS, c.threat.level)
        assertTrue(c.threat.links.single().suspicious)
    }

    @Test fun lotteryIsSpam() {
        assertEquals(Category.SPAM, cat("+918888888888", "Congratulations! You have won Rs 25,00,000 in KBC lottery. Claim your prize: bit.ly/abc123"))
    }

    @Test fun otpPhishingAskIsFlagged() {
        val c = MessageClassifier.classify("+917777777777", "Sir I sent you 5000 by mistake, please send the OTP you received to reverse it")
        assertTrue(c.threat.isThreat)
    }

    @Test fun genuineOtpNotFlaggedForSharingWarning() {
        val c = MessageClassifier.classify("AD-ICICIB-T", "Never share your OTP with anyone. 482910 is the OTP for your ICICI Bank transaction.")
        assertFalse(c.threat.isThreat)
        assertEquals(Category.OTP, c.category)
    }

    @Test fun senderParsing() {
        val s = SenderAnalyzer.analyze("AX-HDFCBK-S")
        assertEquals("HDFCBK", s.brand)
        assertEquals(DltTag.SERVICE, s.dltTag)
        assertEquals("HDFC Bank", s.displayName)
        assertFalse(s.canReply)
        assertEquals(SenderAnalyzer.keyOf("+91 98765 43210"), SenderAnalyzer.keyOf("09876543210"))
    }

    @Test fun officialLinkIsNotSuspicious() {
        val links = ThreatDetector.extractLinks("Login at https://www.onlinesbi.sbi or visit hdfcbank.com/kyc")
        assertEquals(2, links.size)
        assertTrue(links.none { it.suspicious })
    }

    @Test fun emailIsNotALink() {
        assertTrue(ThreatDetector.extractLinks("Write to care@hdfcbank.com for help").isEmpty())
    }
}
