package io.github.achyuki.folddevtools.service

import io.github.achyuki.folddevtools.IRemoteService
import io.github.achyuki.folddevtools.preferences

/**
 * 统一获取当前激活的 [IRemoteService]：根据 `shizukumode` 偏好选择 Shizuku 或 Root 路径。
 *
 * DevtoolsService 桥接、AttachPageList 列表拉取等下游逻辑统一调用此函数，
 * 无需感知服务来源差异。
 *
 * 优先级：shizukumode=true → Shizuku；否则 → Root。
 */
suspend fun getActiveRemoteService(): IRemoteService =
    if (preferences.getBoolean("shizukumode", false)) {
        getShizukuRemoteService()
    } else {
        getRemoteRootService()
    }
