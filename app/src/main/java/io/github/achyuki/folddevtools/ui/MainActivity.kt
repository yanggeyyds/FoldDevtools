package io.github.achyuki.folddevtools.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.achyuki.folddevtools.TAG
import io.github.achyuki.folddevtools.preferences
import io.github.achyuki.folddevtools.service.bindShizukuUserService
import io.github.achyuki.folddevtools.service.unbindShizukuUserService
import io.github.achyuki.folddevtools.ui.screen.FrontendScreen
import io.github.achyuki.folddevtools.ui.screen.MainScreen
import io.github.achyuki.folddevtools.ui.screen.PageScreen
import io.github.achyuki.folddevtools.ui.screen.Screen
import io.github.achyuki.folddevtools.ui.screen.SettingScreen
import io.github.achyuki.folddevtools.ui.theme.AppTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 0x1001
    }

    /** Shizuku binder 就绪回调：检查状态并尝试绑定 UserService。 */
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        tryBindShizuku()
    }

    /** Shizuku binder 死亡回调。 */
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
    }

    /** Shizuku 权限请求结果回调。 */
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindShizukuUserService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        // 注册 Shizuku 监听
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        // 若 Shizuku 已就绪，立即尝试绑定
        if (Shizuku.pingBinder()) {
            tryBindShizuku()
        }

        setContent {
            AppTheme {
                val navController = rememberNavController()
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Main.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Main.route) {
                            MainScreen(navController)
                        }
                        composable(Screen.Setting.route) {
                            SettingScreen(navController)
                        }
                        composable(
                            route = "${Screen.Page.route}?title={title}",
                            arguments = listOf(
                                navArgument("title") {
                                    type = NavType.StringType
                                }
                            )
                        ) {
                            val title = it.arguments!!.getString("title")!!
                            PageScreen(navController, title)
                        }
                        composable(
                            route = "${Screen.Frontend.route}?title={title}&url={url}",
                            arguments = listOf(
                                navArgument("title") {
                                    type = NavType.StringType
                                },
                                navArgument("url") {
                                    type = NavType.StringType
                                }
                            )
                        ) {
                            val title = it.arguments!!.getString("title")!!
                            val url = it.arguments!!.getString("url")!!
                            FrontendScreen(navController, title, url)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { Shizuku.removeBinderReceivedListener(binderReceivedListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(binderDeadListener) } catch (_: Throwable) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResultListener) } catch (_: Throwable) {}
        // 仅在非 Shizuku 模式时解绑（避免切换模式时误杀）
        if (!preferences.getBoolean("shizukumode", false)) {
            unbindShizukuUserService()
        }
    }

    /** 检查 Shizuku 状态：已授权则绑定，未授权则请求权限。 */
    private fun tryBindShizuku() {
        if (!preferences.getBoolean("shizukumode", false)) return
        if (!Shizuku.pingBinder()) return
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "Shizuku pre-v11, too old")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindShizukuUserService()
        } else if (!Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }
}
