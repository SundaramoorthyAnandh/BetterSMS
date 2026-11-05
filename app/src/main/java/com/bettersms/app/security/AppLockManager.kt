package com.bettersms.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bettersms.app.data.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Locks the UI when the app goes to the background (immediately or after a grace period).
 * Observes the whole process, so switching between our own screens never re-locks.
 */
class AppLockManager(private val store: SecureStore) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(store.settings.appLockEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Increments every time the app locks; the lock screen auto-prompts once per value. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** True while a biometric/PIN prompt is showing, so its own activity switch doesn't re-lock. */
    @Volatile var authInProgress = false

    private var stoppedAt = 0L

    fun unlock() {
        _locked.value = false
    }

    fun lockNow() {
        if (!store.settings.appLockEnabled) return
        if (!_locked.value) _generation.update { it + 1 }
        _locked.value = true
    }

    fun onLockDisabled() {
        _locked.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        if (authInProgress) return
        stoppedAt = SystemClock.elapsedRealtime()
        val s = store.settings
        if (s.appLockEnabled && s.lockTimeoutSec == 0) lockNow()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (authInProgress || stoppedAt == 0L) return
        val s = store.settings
        if (s.appLockEnabled && !_locked.value &&
            SystemClock.elapsedRealtime() - stoppedAt >= s.lockTimeoutSec * 1000L
        ) {
            lockNow()
        }
    }
}
