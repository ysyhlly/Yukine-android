package app.yukine

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class StreamingWebAuthActivity : Activity() {
    private var webView: WebView? = null
    private var lastProviderUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyWebViewDataDirectorySuffix()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val webView = WebView(this).also { view ->
            view.setBackgroundColor(Color.TRANSPARENT)
            configureWebView(view)
            view.loadUrl(url)
        }
        this.webView = webView
        setContentView(loginLayout(webView))
        lastProviderUrl = url
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun configureWebView(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleAuthUrl(request.url)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleAuthUrl(Uri.parse(url))
            }

            override fun onPageFinished(view: WebView, url: String) {
                lastProviderUrl = url
                CookieManager.getInstance().flush()
                if (collectCookieHeader(logOnly = true) != null && shouldAutoComplete(url)) {
                    finishWithAuthCallback(fallbackCallbackUri())
                }
            }
        }
    }

    private fun loginLayout(webView: WebView): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(
                webView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(loginActionBar())
        }
    }

    private fun loginActionBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.rgb(250, 247, 241))

            addView(
                TextView(this@StreamingWebAuthActivity).apply {
                    text = "在网页里完成登录后，点右侧按钮保存到 Yukine"
                    textSize = 13f
                    setTextColor(Color.rgb(63, 55, 45))
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            addView(
                Button(this@StreamingWebAuthActivity).apply {
                    text = "登录完成"
                    setOnClickListener {
                        finishWithAuthCallback(fallbackCallbackUri())
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun shouldAutoComplete(url: String): Boolean {
        return !url.contains("/login", ignoreCase = true) &&
            !url.contains("passport", ignoreCase = true)
    }

    private fun handleAuthUrl(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "echo-next" || uri.host != "streaming-auth") {
            if (uri != null) {
                lastProviderUrl = uri.toString()
            }
            return false
        }
        finishWithAuthCallback(uri)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithAuthCallback(fallbackCallbackUri())
    }

    private fun finishWithAuthCallback(uri: Uri) {
        val cookieHeader = collectCookieHeader()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(withProviderFallback(uri))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (!cookieHeader.isNullOrBlank()) {
            intent.putExtra(EXTRA_COOKIE_HEADER, cookieHeader)
        }
        startActivity(intent)
        finish()
    }

    private fun fallbackCallbackUri(): Uri {
        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (!initialUrl.isNullOrBlank()) {
            val launchUri = Uri.parse(initialUrl)
            val callback = launchUri.getQueryParameter("echo_auth_callback")
            if (!callback.isNullOrBlank()) {
                return Uri.parse(callback)
            }
        }
        val provider = intent.getStringExtra(EXTRA_PROVIDER)
        val builder = Uri.Builder()
            .scheme("echo-next")
            .authority("streaming-auth")
        if (!provider.isNullOrBlank()) {
            builder.appendQueryParameter("provider", provider)
        }
        return builder.build()
    }

    private fun withProviderFallback(uri: Uri): Uri {
        if (uri.getQueryParameter("provider") != null) {
            return uri
        }
        val provider = intent.getStringExtra(EXTRA_PROVIDER)
        if (provider.isNullOrBlank()) {
            return uri
        }
        return uri.buildUpon().appendQueryParameter("provider", provider).build()
    }

    private fun collectCookieHeader(logOnly: Boolean = false): String? {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        // Query every domain the provider might store cookies on (login sub-domain AND the
        // registrable parent domain where the real session token usually lives), then merge them
        // into one header. Querying a single domain — as the old code did — silently dropped the
        // session token (e.g. NetEase MUSIC_U on .163.com, Bilibili SESSDATA on .bilibili.com),
        // which is why captured cookies looked "fake": present but missing the auth credential.
        val candidates = LinkedHashSet<String>()
        lastProviderUrl?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        val provider = intent.getStringExtra(EXTRA_PROVIDER)
            ?.takeIf { it.isNotBlank() }
            ?.let { app.yukine.streaming.StreamingProviderName.fromWireName(it) }
        if (provider != null) {
            candidates.addAll(app.yukine.streaming.LocalStreamingLoginEndpoints.cookieDomainHints(provider))
        }

        // name -> value, later domains do not overwrite an already-captured name.
        val merged = LinkedHashMap<String, String>()
        for (candidate in candidates) {
            val raw = cookieManager.getCookie(candidate)?.takeIf { it.isNotBlank() } ?: continue
            for (pair in raw.split(";")) {
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) continue
                val eq = trimmed.indexOf('=')
                if (eq <= 0) continue
                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (name.isNotEmpty() && !merged.containsKey(name)) {
                    merged[name] = value
                }
            }
        }
        Log.d(TAG, "Captured streaming cookie names: ${merged.keys.sorted().joinToString(",")}")
        if (merged.isEmpty()) {
            return null
        }
        val header = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (provider != null &&
            !app.yukine.streaming.LocalStreamingLoginEndpoints.hasSessionToken(provider, merged.keys)
        ) {
            // We captured cookies but none of the names the provider uses to prove login. Treat as
            // not-logged-in so the UI does not report a false success. Returning null lets
            // completeAuth() surface an "auth canceled / incomplete" state instead.
            return null
        }
        if (logOnly) {
            Log.d(TAG, "Streaming auth cookie is ready for provider=$provider")
        }
        return header
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "StreamingWebAuth"
        const val EXTRA_PROVIDER: String = "app.yukine.extra.PROVIDER"
        const val EXTRA_URL: String = "app.yukine.extra.URL"
        const val EXTRA_COOKIE_HEADER: String = "app.yukine.extra.COOKIE_HEADER"
        private var webViewSuffixApplied: Boolean = false

        @Synchronized
        private fun applyWebViewDataDirectorySuffix() {
            if (webViewSuffixApplied || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return
            }
            WebView.setDataDirectorySuffix("streaming_auth")
            webViewSuffixApplied = true
        }
    }
}
