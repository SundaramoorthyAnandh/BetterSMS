package com.bettersms.app.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bettersms.app.container
import com.bettersms.app.ui.compose.NewMessageScreen
import com.bettersms.app.ui.conversation.ConversationScreen
import com.bettersms.app.ui.glass.GlassBackground
import com.bettersms.app.ui.glass.rememberAnimationsEnabled
import com.bettersms.app.ui.home.HomeScreen
import com.bettersms.app.ui.lock.LockScreen
import com.bettersms.app.ui.onboarding.OnboardingScreen
import com.bettersms.app.ui.settings.BlockedScreen
import com.bettersms.app.ui.settings.SecurityCenterScreen
import com.bettersms.app.ui.settings.SettingsScreen
import com.bettersms.app.ui.theme.BetterSmsTheme
import kotlinx.coroutines.flow.StateFlow

sealed interface NavRequest {
    data class OpenThread(val address: String) : NavRequest
    data class Compose(val address: String?, val body: String?) : NavRequest
}

private object Routes {
    const val HOME = "home"
    const val CHAT = "chat?threadId={threadId}&address={address}&body={body}"
    const val NEW = "new?body={body}"
    const val SETTINGS = "settings"
    const val SECURITY = "security"
    const val BLOCKED = "blocked"

    fun chat(threadId: Long = -1, address: String? = null, body: String? = null): String =
        "chat?threadId=$threadId" +
            (address?.let { "&address=" + Uri.encode(it) } ?: "") +
            (body?.let { "&body=" + Uri.encode(it) } ?: "")

    fun new(body: String? = null) = "new" + (body?.let { "?body=" + Uri.encode(it) } ?: "")
}

@Composable
fun AppRoot(pendingNav: StateFlow<NavRequest?>, onNavConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { ctx.container() }
    val data by container.store.data.collectAsStateWithLifecycle()
    val locked by container.lock.locked.collectAsStateWithLifecycle()
    val request by pendingNav.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val settings = data.settings
    val showLock = locked && settings.appLockEnabled

    BetterSmsTheme(themeMode = settings.themeMode) {
        GlassBackground {
            if (!settings.onboardingDone) {
                OnboardingScreen(onDone = {})
                return@GlassBackground
            }

            // The app stays composed underneath so state survives, but it's invisible and
            // hidden from accessibility services while locked.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (showLock) 0f else 1f }
                    .then(if (showLock) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                AppNavHost(navController)
            }
            if (showLock) LockScreen()

            LaunchedEffect(request, showLock) {
                val r = request ?: return@LaunchedEffect
                if (showLock) return@LaunchedEffect
                when (r) {
                    is NavRequest.OpenThread -> navController.navigate(Routes.chat(address = r.address)) { launchSingleTop = true }
                    is NavRequest.Compose ->
                        if (r.address != null) navController.navigate(Routes.chat(address = r.address, body = r.body))
                        else navController.navigate(Routes.new(r.body))
                }
                onNavConsumed()
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    val animate = rememberAnimationsEnabled()
    val dur = if (animate) 260 else 0
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(dur)) + slideInHorizontally(tween(dur)) { it / 10 } },
        exitTransition = { fadeOut(tween(dur)) },
        popEnterTransition = { fadeIn(tween(dur)) },
        popExitTransition = { fadeOut(tween(dur)) + slideOutHorizontally(tween(dur)) { it / 10 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenThread = { navController.navigate(Routes.chat(threadId = it)) },
                onNewMessage = { navController.navigate(Routes.new()) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onSecurity = { navController.navigate(Routes.SECURITY) },
            )
        }
        composable(
            Routes.CHAT,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("address") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("body") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val args = entry.arguments
            ConversationScreen(
                threadId = args?.getLong("threadId") ?: -1L,
                address = args?.getString("address"),
                initialBody = args?.getString("body"),
                onBack = { if (!navController.popBackStack()) navController.navigate(Routes.HOME) },
            )
        }
        composable(
            Routes.NEW,
            arguments = listOf(navArgument("body") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val body = entry.arguments?.getString("body")
            NewMessageScreen(
                onBack = { navController.popBackStack() },
                onPick = { address ->
                    navController.navigate(Routes.chat(address = address, body = body)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSecurity = { navController.navigate(Routes.SECURITY) },
                onBlocked = { navController.navigate(Routes.BLOCKED) },
            )
        }
        composable(Routes.SECURITY) {
            SecurityCenterScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.BLOCKED) {
            BlockedScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { navController.navigate(Routes.chat(threadId = it)) },
            )
        }
    }
}
