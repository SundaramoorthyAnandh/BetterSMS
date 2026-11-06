package com.bettersms.app.ui

import android.Manifest
import android.graphics.Bitmap
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.bettersms.app.data.OutgoingRecord
import com.bettersms.app.sms.Sims
import com.bettersms.app.ui.conversation.SimPickerDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bettersms.app.BetterSmsApp
import com.bettersms.app.data.SmsRepository
import com.bettersms.app.data.SmsType
import com.bettersms.app.ui.conversation.ConversationScreen
import com.bettersms.app.ui.glass.GlassBackground
import com.bettersms.app.ui.home.HomeScreen
import com.bettersms.app.ui.lock.LockScreen
import com.bettersms.app.ui.onboarding.OnboardingScreen
import com.bettersms.app.ui.settings.SecurityCenterScreen
import com.bettersms.app.ui.settings.SettingsScreen
import com.bettersms.app.ui.theme.BetterSmsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import java.io.File

/**
 * Renders the real screens with sample messages on the JVM. Acts as a smoke test (every screen
 * must compose without crashing) and writes PNGs to app/build/screenshots for review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h850dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: BetterSmsApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.container.store.updateSettings { it.copy(onboardingDone = true, appLockEnabled = true) }
        // Let the startup load (empty in Robolectric) finish before injecting sample data.
        Thread.sleep(600)
        runBlocking { app.container.repo.injectForTest(sampleMessages()) }
        compose.mainClock.autoAdvance = false
    }

    private fun snap(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            BetterSmsTheme(themeMode = if (dark) 2 else 1) {
                GlassBackground { content() }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        shadowOf(Looper.getMainLooper()).idle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    @Test fun home() = snap("01_home") { HomeScreen({}, {}, {}, {}) }

    @Test fun homeDark() = snap("02_home_dark", dark = true) { HomeScreen({}, {}, {}, {}) }

    @Test fun otpChat() = snap("03_otp_chat") { ConversationScreen(1L, null, null) {} }

    @Test fun scamChat() = snap("04_scam_chat") { ConversationScreen(3L, null, null) {} }

    @Test fun settings() = snap("05_settings") { SettingsScreen({}, {}, {}) }

    @Test fun securityCenter() = snap("06_security_center", dark = true) { SecurityCenterScreen({}, {}) }

    @Test fun lock() {
        val bm = app.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
        Shadow.extract<ShadowBiometricManager>(bm).setCanAuthenticate(true)
        snap("07_lock") { LockScreen() }
    }

    @Test fun onboarding() = snap("08_onboarding") { OnboardingScreen {} }

    private fun setUpDualSim() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        shadowOf(app.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfos(
            SubscriptionInfoBuilder.newBuilder().setId(1).setSimSlotIndex(0).setCarrierName("Jio")
                .setIconTint(0xFF3B82F6.toInt()).buildSubscriptionInfo(),
            SubscriptionInfoBuilder.newBuilder().setId(2).setSimSlotIndex(1).setCarrierName("Airtel")
                .setIconTint(0xFFEF4444.toInt()).buildSubscriptionInfo(),
        )
        shadowOf(app.getSystemService(TelephonyManager::class.java)).setActiveModemCount(2)
    }

    @Test fun dualSimChat() {
        setUpDualSim()
        val now = System.currentTimeMillis()
        app.container.store.update {
            it.copy(
                outgoing = listOf(
                    OutgoingRecord(-11, "+919876543210", "Sure! Calling you in 5 mins", now - 5 * 60_000, 2, SmsType.SENT),
                    OutgoingRecord(-12, "+919876543210", "Can you hear me?", now - 60_000, 2, SmsType.FAILED),
                    OutgoingRecord(-13, "+919876543210", "Network is bad here, will text instead", now - 10_000, 1, SmsType.OUTBOX),
                ),
            )
        }
        runBlocking { app.container.repo.injectForTest(sampleMessages()) }
        snap("09_dual_sim_chat") { ConversationScreen(2L, null, null) {} }
    }

    @Test fun simPicker() {
        setUpDualSim()
        snap("10_sim_picker") {
            SimPickerDialog(Sims.active(app), receivedOn = 2, forChat = false, onPick = { _, _ -> }, onDismiss = {})
        }
        val dialog = ShadowDialog.getLatestDialog()
        val view = dialog.window!!.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/10_sim_picker_dialog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleMessages(): List<SmsRepository.RawSms> {
        val now = System.currentTimeMillis()
        val min = 60_000L
        val hour = 60 * min
        val day = 24 * hour
        var id = 1L
        fun m(thread: Long, addr: String, body: String, ago: Long, type: Int = SmsType.INBOX, read: Boolean = true, sub: Int = -1) =
            SmsRepository.RawSms(id++, thread, addr, body, now - ago, type, read, sub)
        return listOf(
            m(1, "AX-HDFCBK-T", "482913 is your OTP for HDFC Bank NetBanking login. Valid for 5 mins. Do not share it with anyone.", 1 * min, read = false),
            m(2, "+919876543210", "Reached home safely. Call me when you're free!", 20 * min, read = false, sub = 2),
            m(2, "+919876543210", "Leaving office now, will call in 10", 26 * min, type = SmsType.SENT, sub = 1),
            m(3, "+918123456789", "Dear customer, your SBI YONO account will be blocked today. Update your PAN/KYC immediately: http://sbi-kyc-verify.xyz/login", 2 * hour, read = false),
            m(1, "AX-HDFCBK-T", "Rs 2,499.00 debited from A/c XX4521 on 05-Nov-26 at AMAZON PAY. Avl Bal Rs 18,240.50. Not you? Call 18002586161", 3 * hour),
            m(4, "JD-AIRTEL-S", "Your Airtel postpaid bill of Rs 599 is due on 10-Nov. Pay now to avoid late fee: airtel.in/pay", 5 * hour),
            m(5, "VK-AMAZON-S", "Your order #405-1234567 has been shipped and will be delivered by Friday. Track your package in the Amazon app.", day),
            m(6, "BZ-MYNTRA-P", "Flat 60% OFF on top brands! End of Season Sale ends tonight. Shop now: myntra.com/eoss T&C", day + 2 * hour),
            m(8, "AD-ICICIB-S", "INR 15,000.00 credited to A/c XX9012 on 04-Nov-26 by NEFT from ACME TECHNOLOGIES. Avl Bal INR 64,210.00", day + 5 * hour),
            m(9, "+917700112233", "Congratulations! You have won Rs 25,00,000 in KBC lucky draw. Claim your prize now: bit.ly/kbc-claim", 2 * day),
            m(7, "VM-SWIGGY-T", "7731 is your Swiggy verification code. Do not share.", 3 * day),
        ).sortedByDescending { it.date }
    }
}
