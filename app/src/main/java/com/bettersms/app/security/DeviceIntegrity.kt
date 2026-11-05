package com.bettersms.app.security

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.File

/** Lightweight device-health checks shown in the Security Center. */
object DeviceIntegrity {
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/su", "/data/local/su",
        "/data/local/bin/su", "/data/local/xbin/su", "/system/app/Superuser.apk", "/system/bin/.ext/su",
        "/system/usr/we-need-root/su", "/cache/su", "/data/adb/magisk", "/sbin/.magisk", "/system/xbin/daemonsu",
    )

    fun isLikelyRooted(): Boolean =
        suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            Build.TAGS?.contains("test-keys") == true

    fun isAdbEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun isDebuggerAttached(): Boolean = Debug.isDebuggerConnected() || Debug.waitingForDebugger()

    fun isDebuggableBuild(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun hasInternetPermission(context: Context): Boolean =
        context.packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName) == PackageManager.PERMISSION_GRANTED

    fun isBackupDisabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0
}
