package io.github.achyuki.folddevtools.ui.component

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import io.github.achyuki.folddevtools.R
import io.github.achyuki.folddevtools.TAG
import io.github.achyuki.folddevtools.core.DevtoolsClient
import io.github.achyuki.folddevtools.core.PageInfo
import io.github.achyuki.folddevtools.core.prasePageInfo
import io.github.achyuki.folddevtools.preferences
import io.github.achyuki.folddevtools.ui.screen.Screen
import io.github.achyuki.folddevtools.ui.screen.ScreenState
import java.io.*
import java.net.URI
import java.net.URL
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachPageList(navigator: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var pagesScreenState by remember { mutableStateOf<ScreenState<List<PageInfo>>>(ScreenState.Loading) }
    val bindPort = preferences.getInt("bindport", 9223)
    var isForeground = true
    val devtoolsClient = DevtoolsClient("127.0.0.1", bindPort)

    when (val state = pagesScreenState) {
        is ScreenState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is ScreenState.Success -> {
            val pages = state.pack
            if (pages.size > 0) {
                Box(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pages) {
                            AttachPageItem(it, devtoolsClient) {
                                var dbgUrl = "http://127.0.0.1:$bindPort/"
                                var entryPage = preferences.getString("entrypage", null) ?: "<AUTO>"
                                var entryArg =
                                    getEntryArg(it.devtoolsFrontendUrl) ?: "devtools_app.html" to
                                        "ws=" + it.webSocketDebuggerUrl.removePrefix("ws://").removePrefix("ws:\\/\\/")
                                if (entryPage != "<AUTO>") {
                                    entryArg = entryArg.copy(first = "$entryPage.html")
                                }
                                if (it.type == "app") {
                                    // Stetho
                                    entryArg = entryArg.copy(second = "ws=127.0.0.1:$bindPort/inspector")
                                }
                                dbgUrl += "${entryArg.first}?${entryArg.second}"

                                val extBrowser = preferences.getBoolean("extbrowser", false)
                                if (extBrowser) {
                                    uriHandler.openUri(dbgUrl)
                                } else {
                                    navigator.navigate(Screen.Frontend.create(it.title, dbgUrl))
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "  -_-#\n${stringResource(R.string.empty)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        is ScreenState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.connection_interrupted),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (state.message != null) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                if (isForeground) {
                    try {
                        val pages = devtoolsClient.getPages()
                        val pageList = mutableListOf<PageInfo>()
                        for (i in 0 until pages.length()) {
                            try {
                                pageList.add(prasePageInfo(pages.getJSONObject(i)))
                            } catch (e: Exception) {
                                Log.e(TAG, e.stackTraceToString())
                            }
                        }
                        pagesScreenState =
                            ScreenState.Success(pageList)
                        Log.d(TAG, "pages $pageList")
                    } catch (e: Exception) {
                        pagesScreenState = ScreenState.Error(describeDevtoolsError(e, bindPort))
                        Log.w(TAG, e.stackTraceToString())
                    }
                }
                delay(600)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun AttachPageItem(pageInfo: PageInfo, devtoolsClient: DevtoolsClient, onClick: (pageInfo: PageInfo) -> Unit = {}) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick(pageInfo) }
        ) {
            Column(
                modifier = Modifier
                    .padding(all = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.padding(end = 8.dp)

                    ) {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        devtoolsClient.closePage(pageInfo.id)
                                    }
                                }
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .size(16.dp)
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "${pageInfo.type}",
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (pageInfo.attached == true) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.attached),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (pageInfo.empty == true) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.empty_label),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (pageInfo.visible == true) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.visible),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    pageInfo.width?.let {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = "${pageInfo.width}x${pageInfo.height!!}",
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val faviconUrl = pageInfo.url.takeIf { it.startsWith("http") }?.let { getFaviconUrl(pageInfo.url) }
                    Log.d(TAG, "geticon $faviconUrl")

                    SubcomposeAsyncImage(
                        model = faviconUrl,
                    /*loading = {
                        CircularProgressIndicator()
                    },*/
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = pageInfo.title,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = pageInfo.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun getFaviconUrl(urlStr: String): String? = try {
    val url = URL(urlStr)
    val protocol = url.protocol
    val host = url.host
    val port = if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else ""

    "$protocol://$host$port/favicon.ico"
} catch (e: Exception) {
    null
}

private fun getEntryArg(urlStr: String): Pair<String, String>? = try {
    val url = URI(urlStr)
    val path = url.path
    val query = url.query
    val entry = path.substringAfterLast("/")

    entry to query
} catch (e: Exception) {
    null
}

/**
 * 把 DevtoolsClient 的异常翻译成人类可读诊断，辅助用户定位「连接失败」原因。
 *
 * 分类：
 *  - DEVCGET 前缀：HTTP 非 200（桥接到了非 CDP 服务或 Chrome 拒绝）
 *  - ConnectException / Connection refused：桥接端口未监听
 *  - SocketTimeoutException：浏览器未响应（后台/锁屏）
 *  - 其他：原始消息
 */
private fun describeDevtoolsError(e: Exception, bindPort: Int): String {
    val msg = e.message ?: e.javaClass.simpleName
    return when {
        msg.startsWith("DEVCGET") -> {
            // "DEVCGET 404: ..." → 提取状态码
            val code = msg.substringAfter("DEVCGET ").substringBefore(":").trim()
            "HTTP $code：桥接到的服务不是 CDP（或 Chrome 拒绝了请求）\n端口 $bindPort"
        }
        msg.contains("Connection refused", ignoreCase = true) ||
        e is java.net.ConnectException ->
            "桥接被拒：127.0.0.1:$bindPort 未监听（DevtoolsService 可能已停止）"
        e is java.net.SocketTimeoutException ->
            "请求超时：浏览器未在预期时间内响应（可能已后台/锁屏/被冻结）"
        msg.contains("CLEARTEXT", ignoreCase = true) ||
        msg.contains("network security policy", ignoreCase = true) ->
            "明文 HTTP 被网络安全策略拦截（需配置 cleartext）"
        else -> msg
    }
}
