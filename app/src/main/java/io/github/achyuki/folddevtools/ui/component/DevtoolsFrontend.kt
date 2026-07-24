package io.github.achyuki.folddevtools.ui.component

import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.achyuki.folddevtools.TAG
import java.io.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevtoolsFrontend(url: String) {
    val context = LocalContext.current

    Log.i(TAG, "loadweb $url")
    ComposeWebView(
        url,
        modifier = Modifier
            .fillMaxSize()
    )
}

@Composable
fun ComposeWebView(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    AndroidView(
        factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowContentAccess = true
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // 手机/悬浮窗优化：允许手势缩放，适配窄屏
                    setSupportZoom(true)
                    // 初始缩放略小，让 DevTools 面板在窄屏能看到更多内容
                    setInitialScale(80)
                    // 文字可缩放，配合 CSS 注入的密度优化
                    textZoom = 90
                }
                // 注入 viewport meta + 手机适配 JS：页面加载完成后调整布局
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("""
                            (function() {
                                // 确保 viewport meta 适配窄屏
                                var vp = document.querySelector('meta[name="viewport"]');
                                if (!vp) {
                                    vp = document.createElement('meta');
                                    vp.name = 'viewport';
                                    vp.content = 'width=device-width, initial-scale=0.8, minimum-scale=0.4, maximum-scale=3.0, user-scalable=yes';
                                    document.head.appendChild(vp);
                                }
                            })();
                        """.trimIndent(), null)
                    }
                }
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        if (canGoBack()) {
                            goBack()
                        } else {
                            activity.onBackPressedDispatcher.onBackPressed()
                        }
                        true
                    } else {
                        false
                    }
                }

                loadUrl(url)
            }
        },
        modifier = modifier,
        update = {}
    )
}
