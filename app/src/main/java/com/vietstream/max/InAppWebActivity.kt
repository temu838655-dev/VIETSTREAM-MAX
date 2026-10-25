
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
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val title = intent.getStringExtra("web_title")
            ?: "VIETSTREAM MAX"

        val initialUrl = intent.getStringExtra("web_url").orEmpty()

        setContent {
            var loading by remember { mutableStateOf(true) }
            var progress by remember { mutableIntStateOf(0) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var pageView by remember { mutableStateOf<WebView?>(null) }

            BackHandler(
                enabled = fullscreenView != null ||
                    (pageView?.canGoBack() == true)
            ) {
                if (fullscreenView != null) {
                    hideFullscreen()
                } else {
                    pageView?.goBack()
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF151515))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(
                        onClick = {
                            if (fullscreenView != null) {
                                hideFullscreen()
                            } else if (pageView?.canGoBack() == true) {
                                pageView?.goBack()
                            } else {
                                finish()
                            }
                        }
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
                        }
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Tải lại",
                            tint = Color.White
                        )
                    }
                }

                if (loading) {
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

                                setBackgroundColor(android.graphics.Color.BLACK)

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

                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)

                                    allowFileAccess = false
                                    allowContentAccess = true

                                    mixedContentMode =
                                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                }

                                val cookieManager =
                                    CookieManager.getInstance()

                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(
                                    this,
                                    true
                                )

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
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            errorMessage =
                                                "Không thể tải trang. Hãy kiểm tra Internet."
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {

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

                                if (
                                    initialUrl.startsWith("https://") ||
                                    initialUrl.startsWith("http://")
                                ) {
                                    loadUrl(initialUrl)
                                } else {
                                    loading = false
                                    errorMessage = "Địa chỉ trang web không hợp lệ."
                                }
                            }
                        }
                    )

                    if (errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
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

        (view.parent as? ViewGroup)?.removeView(view)

        fullscreenView = null

        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun releaseWebView() {
        hideFullscreen()

        webView?.let { view ->
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = WebViewClient()

            (view.parent as? ViewGroup)?.removeView(view)

            view.removeAllViews()
            view.destroy()
        }

        webView = null
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
            webView?.canGoBack() == true -> webView?.goBack()
            else -> super.onBackPressed()
        }
    }
}
