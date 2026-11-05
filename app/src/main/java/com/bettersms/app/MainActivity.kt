package com.bettersms.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bettersms.app.sms.HeadlessSmsSendService
import com.bettersms.app.ui.AppRoot
import com.bettersms.app.ui.NavRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : FragmentActivity() {
    private val pendingNav = MutableStateFlow<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val c = container()

        applyWindowSecurity(c.store.settings.blockScreenshots)
        lifecycleScope.launch {
            c.store.data.map { it.settings.blockScreenshots }.distinctUntilChanged().collect { applyWindowSecurity(it) }
        }
        // Tapjacking defence: ignore touches while another app draws over us.
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            AppRoot(pendingNav = pendingNav, onNavConsumed = { pendingNav.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container().repo.start()
    }

    private fun applyWindowSecurity(secure: Boolean) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!secure)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_THREAD -> intent.getStringExtra(EXTRA_ADDRESS)?.let { pendingNav.value = NavRequest.OpenThread(it) }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme !in setOf("sms", "smsto", "mms", "mmsto")) return
                val recipients = HeadlessSmsSendService.recipientsOf(data)
                val body = intent.getStringExtra("sms_body")
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: bodyFromUri(data)
                pendingNav.value = NavRequest.Compose(recipients.firstOrNull(), body)
            }
            Intent.ACTION_SEND -> {
                val body = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                pendingNav.value = NavRequest.Compose(null, body)
            }
        }
    }

    private fun bodyFromUri(uri: Uri): String? {
        val query = uri.schemeSpecificPart?.substringAfter('?', "") ?: return null
        return query.split('&')
            .firstOrNull { it.startsWith("body=") }
            ?.removePrefix("body=")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.bettersms.app.OPEN_THREAD"
        const val EXTRA_ADDRESS = "address"
    }
}
