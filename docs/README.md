# FoldDevtools

> 在 Android 上使用 Chrome DevTools 调试 WebView 等。

| <img src="Screenshot.jpg" width="216" height="480" /> | [从 releases 下载](https://github.com/achyuki/FoldDevtools/releases) |
-|-

## 功能特性

* 使用 Root 权限调试本地 WebView
* 通过远程地址调试浏览器、Node.js 等
* 使用 XPosed 强制启用 WebView 调试
* 通过悬浮窗使用 DevTools
* 支持 [Stetho](https://github.com/facebook/stetho)/[StethoX](https://github.com/5ec1cff/StethoX)

## 免 Root

> [!warning]
> 对于未 Root 的 Android 设备，你需要使用 adb 手动将 WebView/Stetho 的调试 socket 转发到本地端口，然后使用 FoldDevtools 的远程模式连接到该端口（例如 `127.0.0.1:9222`）。

Termux：
```
# 获取 WebView/Stetho 的调试本地 socket 名称
adb shell cat /proc/net/unix | grep devtools_remote
# 0000000000000000: 00000002 00000000 00010000 0001 01 xxxxxxx @webview_devtools_remote_<pid>
# 0000000000000000: 00000002 00000000 00010000 0001 01 xxxxxxx @stetho_<packageName>_devtools_remote

# 执行端口转发
adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>
```

## 问题反馈

请查看 [#1](https://github.com/achyuki/FoldDevtools/issues/1) [#2](https://github.com/achyuki/FoldDevtools/issues/2)

## 许可证

基于 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) 许可证授权。
