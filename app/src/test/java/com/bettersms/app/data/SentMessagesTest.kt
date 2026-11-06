package com.bettersms.app.data

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bettersms.app.BetterSmsApp
import com.bettersms.app.classify.SenderAnalyzer
import com.bettersms.app.sms.SimChoice
import com.bettersms.app.sms.SimInfo
import com.bettersms.app.sms.Sims
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Sent messages must show up in the chat even on phones whose SMS store never saves messages
 * sent by a non-default app, without duplicates once the store does save them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SentMessagesTest {
    private val app: BetterSmsApp get() = ApplicationProvider.getApplicationContext()
    private val repo get() = app.container.repo
    private val store get() = app.container.store

    private val friend = "+919876543210"

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
        // Let the startup load (empty in Robolectric) finish so it can't overwrite injected data.
        Thread.sleep(600)
    }

    private fun incoming(now: Long) =
        SmsRepository.RawSms(1, 2, friend, "Are you coming?", now - 120_000, SmsType.INBOX, true, subId = 2)

    @Test
    fun sentMessageShowsEvenIfSystemNeverSavesIt() = runBlocking {
        val now = System.currentTimeMillis()
        repo.injectForTest(listOf(incoming(now)))

        assertTrue(repo.send(friend, "On my way", 1))
        repo.injectForTest(listOf(incoming(now))) // system store still has only the incoming SMS

        val sent = repo.state.value.threads.getValue(2).last()
        assertEquals("On my way", sent.body)
        assertEquals(SmsType.OUTBOX, sent.type)
        assertTrue(sent.isLocal)
        assertEquals(1, sent.subId)

        repo.onSendResult(sent.id, null, ok = true, lastPart = true)
        repo.injectForTest(listOf(incoming(now)))
        assertEquals(SmsType.SENT, repo.state.value.threads.getValue(2).last().type)
    }

    @Test
    fun systemCopyReplacesLocalCopy() = runBlocking {
        val now = System.currentTimeMillis()
        repo.injectForTest(listOf(incoming(now)))
        repo.send(friend, "On my way", SimChoice.PHONE_DEFAULT)
        val record = store.data.value.outgoing.single()

        // The system saved it a moment later, with the number formatted differently.
        val systemCopy = SmsRepository.RawSms(99, 2, "9876543210", "On my way", record.date + 1_500, SmsType.SENT, true)
        repo.injectForTest(listOf(systemCopy, incoming(now)))

        val copies = repo.state.value.threads.getValue(2).filter { it.body == "On my way" }
        assertEquals(1, copies.size)
        assertEquals(99L, copies.single().id)
        assertTrue(store.data.value.outgoing.isEmpty())
    }

    @Test
    fun newConversationAppearsBeforeSystemSavesIt() = runBlocking {
        repo.injectForTest(emptyList())
        repo.send("+911234567890", "Hello!", SimChoice.PHONE_DEFAULT)
        repo.injectForTest(emptyList())

        val conv = repo.state.value.findConversation(NO_THREAD, "+911234567890", SenderAnalyzer.keyOf("+911234567890"))
        assertNotNull(conv)
        assertTrue(conv!!.threadId < 0)
        assertEquals("Hello!", conv.latest.body)
    }

    @Test
    fun failedSendCanBeRetried() = runBlocking {
        repo.injectForTest(emptyList())
        repo.send(friend, "Ping", 1)
        val firstId = store.data.value.outgoing.single().id
        repo.onSendResult(firstId, null, ok = false, lastPart = true)
        repo.injectForTest(emptyList())

        val failed = repo.state.value.findConversation(NO_THREAD, friend, SenderAnalyzer.keyOf(friend))!!.latest
        assertEquals(SmsType.FAILED, failed.type)

        repo.retry(failed, 1)
        val records = store.data.value.outgoing
        assertEquals(1, records.size)
        assertNotEquals(firstId, records.single().id)
        assertEquals(SmsType.OUTBOX, records.single().status)
    }

    @Test
    fun sendWithoutSmsServiceFailsGracefully() = runBlocking {
        repo.injectForTest(emptyList())
        // Robolectric has no default SMS service, like a phone with no usable SIM.
        assertEquals(false, repo.send(friend, "Hi", SimChoice.PHONE_DEFAULT))
        repo.injectForTest(emptyList())
        val msg = repo.state.value.findConversation(NO_THREAD, friend, SenderAnalyzer.keyOf(friend))!!.latest
        assertEquals(SmsType.FAILED, msg.type)
    }

    @Test
    fun simResolution() {
        val sims = listOf(SimInfo(1, 0, "Jio", 0), SimInfo(2, 1, "Airtel", 0))
        assertNull("dual SIM + ask => picker", Sims.resolveWithoutAsking(SimChoice.ASK, sims))
        assertEquals(2, Sims.resolveWithoutAsking(2, sims))
        assertNull("a removed SIM falls back to asking", Sims.resolveWithoutAsking(7, sims))
        assertEquals("single SIM never asks", 1, Sims.resolveWithoutAsking(SimChoice.ASK, sims.take(1)))
        assertEquals(SimChoice.PHONE_DEFAULT, Sims.resolveWithoutAsking(SimChoice.ASK, emptyList()))
    }
}
