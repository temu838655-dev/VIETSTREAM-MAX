package com.vietstream.max

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class InAppWebActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback:
        WebChromeClient.CustomViewCallback? = null

    private var originalSystemUiVisibility = 0

    private val isTv: Boolean
        get() = packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK
        ) || resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        originalSystemUiVisibility =
            window.decorView.systemUiVisibility

        val title = intent.getStringExtra("web_title")
            ?: "VIETSTREAM MAX"

        val initialUrl = intent.getStringExtra("web_url").orEmpty()

        setContent {
            var loading by remember { mutableStateOf(true) }
            var progress by remember { mutableIntStateOf(0) }
            var errorMessage by remember {
                mutableStateOf<String?>(null)
            }

            var pageCanGoBack by remember {
                mutableStateOf(false)
            }

            var pageView by remember {
                mutableStateOf<WebView?>(null)
            }

            // Luôn nhận phím Back:
            // Fullscreen -> trang trước -> đóng Activity.
            BackHandler(enabled = true) {
                when {
                    fullscreenView != null -> hideFullscreen()

                    pageView?.canGoBack() == true -> {
                        pageView?.goBack()
                    }

                    else -> finish()
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    releaseWebView()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {

                if (fullscreenView == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF151515))
                            .padding(
                                horizontal = if (isTv) 16.dp else 8.dp,
                                vertical = if (isTv) 10.dp else 5.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        IconButton(
                            onClick = {
                                when {
                                    pageView?.canGoBack() == true ->
                                        pageView?.goBack()

                                    else -> finish()
                                }
                            },
                            modifier = Modifier.size(
                                if (isTv) 64.dp else 48.dp
                            )
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                errorMessage = null
                                pageView?.reload()
                            },
                            modifier = Modifier.size(
                                if (isTv) 64.dp else 48.dp
                            )
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Tải lại",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (loading && fullscreenView == null) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE50914),
                        trackColor = Color(0xFF333333)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),

                        factory = { context ->
                            WebView(context).apply {

                                webView = this
                                pageView = this

                                setBackgroundColor(
                                    android.graphics.Color.BLACK
                                )

                                isFocusable = true
                                isFocusableInTouchMode = true

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true

                                    mediaPlaybackRequiresUserGesture = false

                                    loadsImagesAutomatically = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true

                                    cacheMode = WebSettings.LOAD_DEFAULT

                                    javaScriptCanOpenWindowsAutomatically =
                                        false

                                    setSupportMultipleWindows(false)

                                    allowFileAccess = false
                                    allowContentAccess = true

                                    mixedContentMode =
                                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                }

                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)

                                    setAcceptThirdPartyCookies(
                                        this@apply,
                                        true
                                    )
                                }

                                webViewClient = object : WebViewClient() {

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        loading = true
                                        progress = 0
                                        errorMessage = null
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?
                                    ) {
                                        loading = false
                                        progress = 100
                                        pageCanGoBack =
                                            view?.canGoBack() == true
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            errorMessage =
                                                "Không thể tải trang. Hãy kiểm tra kết nối Internet."
                                        }
                                    }
                                }

                                webChromeClient =
                                    object : WebChromeClient() {

                                        override fun onProgressChanged(
                                            view: WebView?,
                                            newProgress: Int
                                        ) {
                                            progress = newProgress
                                            loading = newProgress < 100
                                        }

                                        override fun onShowCustomView(
                                            view: View?,
                                            callback: CustomViewCallback?
                                        ) {
                                            if (view == null) return

                                            if (fullscreenView != null) {
                                                callback?.onCustomViewHidden()
                                                return
                                            }

                                            fullscreenView = view
                                            fullscreenCallback = callback

                                            val decor =
                                                window.decorView as FrameLayout

                                            decor.addView(
                                                view,
                                                FrameLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    Gravity.CENTER
                                                )
                                            )

                                            enableFullscreen()
                                        }

                                        override fun onHideCustomView() {
                                            hideFullscreen()
                                        }
                                    }

                                val parsedUri =
                                    android.net.Uri.parse(initialUrl)

                                val validUrl =
                                    (parsedUri.scheme == "https" ||
                                        parsedUri.scheme == "http") &&
                                        !parsedUri.host.isNullOrBlank()

                                if (validUrl) {
                                    loadUrl(initialUrl)
                                } else {
                                    loading = false
                                    errorMessage =
                                        "Địa chỉ trang web không hợp lệ."
                                }
                            }
                        },

                        update = { view ->
                            pageView = view
                        }
                    )

                    if (errorMessage != null &&
                        fullscreenView == null
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                text = errorMessage ?: "",
                                color = Color.White
                            )

                            Spacer(
                                modifier = Modifier.height(16.dp)
                            )

                            Button(
                                onClick = {
                                    errorMessage = null
                                    pageView?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE50914)
                                )
                            ) {
                                Text("Thử lại")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enableFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun hideFullscreen() {
        val view = fullscreenView ?: return

        // Xóa trạng thái trước để tránh gọi lặp.
        fullscreenView = null

        (view.parent as? ViewGroup)?.removeView(view)

        val callback = fullscreenCallback
        fullscreenCallback = null

        callback?.onCustomViewHidden()

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            originalSystemUiVisibility
    }

    private fun releaseWebView() {
        hideFullscreen()

        val view = webView ?: return

        // Đặt null trước để tránh giải phóng hai lần.
        webView = null

        view.stopLoading()
        view.onPause()

        view.webChromeClient = null
        view.webViewClient = WebViewClient()

        (view.parent as? ViewGroup)?.removeView(view)

        view.removeAllViews()
        view.destroy()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        releaseWebView()

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        when {
            fullscreenView != null -> hideFullscreen()

            webView?.canGoBack() == true ->
                webView?.goBack()

            else -> super.onBackPressed()
        }
    }
}
