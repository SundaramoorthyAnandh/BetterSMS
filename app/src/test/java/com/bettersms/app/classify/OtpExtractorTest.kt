package com.bettersms.app.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpExtractorTest {

    private fun code(body: String) = OtpExtractor.extract(body)?.code

    @Test fun simpleOtp() {
        val o = OtpExtractor.extract("123456 is your OTP for login to Swiggy. Valid for 10 mins. Do not share it with anyone.")!!
        assertEquals("123456", o.code)
        assertEquals(10, o.validityMinutes)
        assertTrue(o.doNotShareWarning)
    }

    @Test fun ignoresAmountsAndCardDigits() {
        assertEquals(
            "847362",
            code("Your OTP for transaction of Rs. 5,000.00 at AMAZON on card ending 1234 is 847362. Valid for 3 minutes."),
        )
    }

    @Test fun googleStylePrefix() {
        val o = OtpExtractor.extract("G-482913 is your Google verification code.")!!
        assertEquals("482913", o.code)
        assertEquals("Google", o.service)
    }

    @Test fun whatsappStyle() {
        assertEquals("4821", code("Use verification code 4821 to verify your WhatsApp account"))
    }

    @Test fun androidHashPrefix() {
        assertEquals("553201", code("<#> 553201 is your Instagram code. Don't share it. FA+9qCX9VSu"))
    }

    @Test fun alphanumericCode() {
        assertEquals("AB12CD", code("Your one time password is AB12CD. It expires in 5 minutes."))
    }

    @Test fun validityInSecondsRoundsUp() {
        assertEquals(2, OtpExtractor.extract("OTP 9081 valid for 90 seconds")!!.validityMinutes)
    }

    @Test fun noOtpInTransactionAlert() {
        assertNull(code("Rs.2500 debited from A/c XX1234 on 05-11-26. Avl Bal Rs 10234.50. Not you? Call 18001234 Never share UPI PIN"))
    }

    @Test fun noOtpInPromoCode() {
        assertNull(code("Use code SAVE50 to get 50% off on your next order! Hurry"))
    }

    @Test fun noOtpInPlainChat() {
        assertNull(code("Hey, call me at 9876543210 when you're free"))
        assertNull(code("Meeting moved to 1530 tomorrow"))
    }

    @Test fun noOtpInDate() {
        assertNull(code("Your verification is scheduled on 05/11/2026"))
    }

    @Test fun serviceFromForPhrase() {
        val o = OtpExtractor.extract("OTP for Zomato login is 7731. Do not share.")!!
        assertEquals("7731", o.code)
        assertEquals("Zomato", o.service)
    }
}
