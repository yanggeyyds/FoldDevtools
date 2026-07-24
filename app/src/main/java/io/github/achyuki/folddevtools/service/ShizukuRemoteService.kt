package io.github.achyuki.folddevtools.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import io.github.achyuki.folddevtools.BuildConfig
import io.github.achyuki.folddevtools.IRemoteService
import io.github.achyuki.folddevtools.TAG
import io.github.achyuki.folddevtools.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * Shizuku 绑定模块：通过 Shizuku UserService 在 shell uid 进程内获取 [IRemoteService]。
 *
 * 与 [getRemoteRootService] 的 Root 路径并列，二者返回同一 [IRemoteService] 接口，
 * 下游 DevtoolsService / RemoteAppsList 无需感知来源差异。
 *
 * 流程（由 [io.github.achyuki.folddevtools.ui.MainActivity] 驱动）：
 *  1. Shizuku binder 就绪（[Shizuku.OnBinderReceivedListener]）
 *  2. 校验版本（!isPreV11）与权限（checkSelfPermission）
 *  3. 未授权则 [Shizuku.requestPermission]，结果回调里再绑定
 *  4. [Shizuku.bindUserService] 成功后缓存 binder
 *
 * UserService 即 [RemoteService]：它继承 IRemoteService.Stub 且实现了 destroy()，
 * 可同时被 RootService（进程内实例化）与 Shizuku（反射实例化）复用。
 *
 * 来源: https://github.com/RikkaApps/Shizuku-API/blob/master/demo/src/main/java/rikka/shizuku/demo/DemoActivity.java
 */
private val shizukuMutex = Mutex()
private const val SHIZUKU_TIMEOUT_MS = 10000L
private var shizukuServiceCached: IRemoteService? = null
private var shizukuBound: Boolean = false

val isShizukuRemoteServiceActive: Boolean
    get() = shizukuServiceCached != null

/**
 * Shizuku UserService 参数：进程名后缀 "shizuku"，版本跟随 versionCode。
 * Shizuku 用 version 字段判断是否需要重启 UserService 进程。
 */
private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
    Shizuku.UserServiceArgs(
        ComponentName(appContext, RemoteService::class.java)
    )
        .daemon(false)
        .processNameSuffix("shizuku")
        .version(BuildConfig.VERSION_CODE)
}

private val shizukuServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        Log.i(TAG, "Shizuku UserService connected")
        if (binder != null && binder.pingBinder()) {
            shizukuServiceCached = IRemoteService.Stub.asInterface(binder)
        } else {
            Log.e(TAG, "Shizuku UserService binder invalid")
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.w(TAG, "Shizuku UserService disconnected")
        shizukuServiceCached = null
        shizukuBound = false
    }
}

/**
 * Shizuku 状态自检：返回当前是否可立即绑定 UserService。
 * 返回值含义：
 *  - READY：binder 存活、非 pre-v11、权限已授予
 *  - NOT_RUNNING：Shizuku 未运行
 *  - TOO_OLD：pre-v11 旧服务
 *  - NEED_PERMISSION：需要请求授权
 */
enum class ShizukuState { READY, NOT_RUNNING, TOO_OLD, NEED_PERMISSION }

fun checkShizukuState(): ShizukuState {
    if (!Shizuku.pingBinder()) return ShizukuState.NOT_RUNNING
    if (Shizuku.isPreV11()) return ShizukuState.TOO_OLD
    return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        ShizukuState.READY
    } else {
        ShizukuState.NEED_PERMISSION
    }
}

/**
 * 绑定 Shizuku UserService。仅在 [checkShizukuState] 返回 READY 时调用。
 * 重复调用安全：已绑定则跳过。
 */
fun bindShizukuUserService() {
    if (shizukuBound) return
    try {
        Shizuku.bindUserService(userServiceArgs, shizukuServiceConnection)
        shizukuBound = true
    } catch (t: Throwable) {
        Log.e(TAG, "bindShizukuUserService failed: ${t.message}")
    }
}

/** 解绑 UserService，供 Activity onDestroy / 模式切换时调用。 */
fun unbindShizukuUserService() {
    if (!shizukuBound) return
    try {
        Shizuku.unbindUserService(userServiceArgs, shizukuServiceConnection, true)
    } catch (_: Throwable) {}
    shizukuBound = false
    shizukuServiceCached = null
}

/**
 * 获取已缓存的 Shizuku [IRemoteService]；未就绪则抛异常提示用户。
 * 与 [getRemoteRootService] 对称，供 [io.github.achyuki.folddevtools.ui.screen.loadServiceScreen] 调用。
 */
suspend fun getShizukuRemoteService(): IRemoteService = shizukuMutex.withLock {
    shizukuServiceCached ?: withContext(Dispatchers.IO) {
        // 等待 MainActivity 驱动的异步绑定完成
        withTimeout(SHIZUKU_TIMEOUT_MS) {
            while (shizukuServiceCached == null) {
                kotlinx.coroutines.delay(200)
                val st = checkShizukuState()
                if (st == ShizukuState.NOT_RUNNING) {
                    throw RemoteServiceException("Shizuku 未运行")
                }
                if (st == ShizukuState.NEED_PERMISSION) {
                    throw RemoteServiceException("Shizuku 未授权，请在 Shizuku App 内授权")
                }
            }
            shizukuServiceCached!!
        }
    }
}
