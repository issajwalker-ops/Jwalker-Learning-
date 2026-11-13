package com.example.ui.components

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.GatewayPreferencesRepository

@Stable
class GatewayWebController {
    internal var webView: WebView? = null

    fun goBack() {
        webView?.let { if (it.canGoBack()) it.goBack() }
    }

    fun goForward() {
        webView?.let { if (it.canGoForward()) it.goForward() }
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    fun goHome() {
        webView?.loadUrl(GatewayPreferencesRepository.PORTAL_HOME_URL)
    }

    fun clearCache() {
        webView?.clearCache(true)
        webView?.reload()
    }
}

@Composable
fun rememberGatewayWebController(): GatewayWebController = remember { GatewayWebController() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GatewayWebView(
    initialUrl: String,
    useSoftwareLayer: Boolean,
    controller: GatewayWebController,
    onPageStarted: (String) -> Unit,
    onPageFinished: (url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onVisitedHistoryUpdated: (url: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onTitleReceived: (String?) -> Unit,
    onMainFrameError: (String?) -> Unit,
    onRenderProcessGone: (didCrash: Boolean, isForeground: Boolean) -> Unit,
    onForegroundResumed: () -> Unit,
    onDownloadStarted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnVisitedHistoryUpdated by rememberUpdatedState(onVisitedHistoryUpdated)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTitleReceived by rememberUpdatedState(onTitleReceived)
    val currentOnMainFrameError by rememberUpdatedState(onMainFrameError)
    val currentOnRenderProcessGone by rememberUpdatedState(onRenderProcessGone)
    val currentOnForegroundResumed by rememberUpdatedState(onForegroundResumed)
    val currentOnDownloadStarted by rememberUpdatedState(onDownloadStarted)

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    controller.webView?.onResume()
                    currentOnForegroundResumed()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    controller.webView?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            controller.webView?.let { wv ->
                runCatching {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                }
            }
            controller.webView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                if (useSoftwareLayer) {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                }

                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setSupportMultipleWindows(false)
                    userAgentString = userAgentString.replace("; wv", "")
                }

                val webViewInstance = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webViewInstance, true)
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        val scheme = uri.scheme?.lowercase() ?: return false
                        if (scheme == "http" || scheme == "https") {
                            return false
                        }
                        if (scheme == "intent") {
                            val handled = runCatching {
                                val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                ctx.startActivity(intent)
                            }.isSuccess
                            return handled
                        }
                        return runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        }.getOrDefault(true)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let { currentOnPageStarted(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view != null && url != null) {
                            currentOnPageFinished(
                                url,
                                view.title,
                                view.canGoBack(),
                                view.canGoForward()
                            )
                        }
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (view != null && url != null) {
                            currentOnVisitedHistoryUpdated(
                                url,
                                view.canGoBack(),
                                view.canGoForward()
                            )
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            currentOnMainFrameError(error?.description?.toString())
                        }
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            detail?.didCrash() == true
                        } else {
                            true
                        }
                        Log.w(
                            "GatewayWebView",
                            "WebView render process gone (didCrash=$didCrash); recovering safely."
                        )
                        if (view != null) {
                            runCatching {
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.stopLoading()
                                view.destroy()
                            }
                        }
                        if (controller.webView === view) {
                            controller.webView = null
                        }
                        val isForeground =
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        currentOnRenderProcessGone(didCrash, isForeground)
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        currentOnProgressChanged(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        currentOnTitleReceived(title)
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        callback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = callback
                        val acceptTypes = fileChooserParams?.acceptTypes
                            ?.filter { it.isNotBlank() }
                            ?.toTypedArray()
                            ?.takeIf { it.isNotEmpty() }
                            ?: arrayOf("*/*")
                        return try {
                            filePickerLauncher.launch(acceptTypes)
                            true
                        } catch (_: ActivityNotFoundException) {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                            false
                        }
                    }
                }

                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        enqueueDownload(
                            context = ctx,
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            onDownloadStarted = currentOnDownloadStarted
                        )
                    } else {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                }

                controller.webView = this
                loadUrl(initialUrl)
            }
        },
        update = { view ->
            controller.webView = view
        }
    )
}

private fun enqueueDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
    onDownloadStarted: (String) -> Unit
) {
    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setMimeType(mimeType)
        if (!userAgent.isNullOrBlank()) {
            addRequestHeader("User-Agent", userAgent)
        }
        val cookie = CookieManager.getInstance().getCookie(url)
        if (!cookie.isNullOrBlank()) {
            addRequestHeader("Cookie", cookie)
        }
        setTitle(fileName)
        setDescription("Learning Gateway download")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
    }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    if (dm != null) {
        runCatching {
            dm.enqueue(request)
            onDownloadStarted(fileName)
        }
    }
}
