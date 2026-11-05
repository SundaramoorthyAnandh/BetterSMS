package com.bettersms.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.bettersms.app.data.SecureStore
import com.bettersms.app.data.SmsRepository
import com.bettersms.app.notify.Notifier
import com.bettersms.app.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = SecureStore(app)
    val repo = SmsRepository(app, store, appScope)
    val lock = AppLockManager(store)
}

class BetterSmsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lock)
        container.repo.start()
    }
}

fun Context.container(): AppContainer = (applicationContext as BetterSmsApp).container
