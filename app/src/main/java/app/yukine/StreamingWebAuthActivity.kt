package app.yukine

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.yukine.streaming.StreamingProviderName

class StreamingWebAuthActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var lastProviderUrl: String? = null
    private var qqRiskCountdown: Runnable? = null
    private var loginPageStarted = false
    private var authCompletionStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyWebViewDataDirectorySuffix()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        if (providerName() == StreamingProviderName.QQ_MUSIC) {
            showQqLoginRiskConfirmation { startLoginPage(url) }
        } else {
            startLoginPage(url)
        }
    }

    override fun onDestroy() {
        cancelQqRiskCountdown()
        webView?.let { view ->
            view.stopLoading()
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun startLoginPage(url: String) {
        if (loginPageStarted || isFinishing) {
            return
        }
        loginPageStarted = true
        val webView = WebView(this).also { view ->
            view.setBackgroundColor(Color.TRANSPARENT)
            configureWebView(view)
            view.loadUrl(url)
        }
        this.webView = webView
        setContentView(loginLayout(webView))
        lastProviderUrl = url
    }

    private fun showQqLoginRiskConfirmation(onConfirmed: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(text("streaming.web.auth.qq.risk.title"))
            .setMessage(text("streaming.web.auth.qq.risk.message"))
            .setNegativeButton(text("cancel")) { _, _ ->
                cancelQqRiskCountdown()
                finish()
            }
            .setPositiveButton(qqRiskCountdownLabel(QQ_LOGIN_RISK_CONFIRM_SECONDS), null)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirmButton.isEnabled = false
            confirmButton.setOnClickListener {
                cancelQqRiskCountdown()
                dialog.dismiss()
                onConfirmed()
            }
            var secondsRemaining = QQ_LOGIN_RISK_CONFIRM_SECONDS
            val countdown = object : Runnable {
                override fun run() {
                    if (isFinishing || !dialog.isShowing) {
                        return
                    }
                    secondsRemaining -= 1
                    if (secondsRemaining <= 0) {
                        confirmButton.text = text("streaming.web.auth.qq.risk.continue")
                        confirmButton.isEnabled = true
                        qqRiskCountdown = null
                    } else {
                        confirmButton.text = qqRiskCountdownLabel(secondsRemaining)
                        mainHandler.postDelayed(this, ONE_SECOND_MS)
                    }
                }
            }
            qqRiskCountdown = countdown
            mainHandler.postDelayed(countdown, ONE_SECOND_MS)
        }
        dialog.show()
    }

    private fun qqRiskCountdownLabel(seconds: Int): String {
        return text("streaming.web.auth.qq.risk.countdown").replace("%d", seconds.toString())
    }

    private fun cancelQqRiskCountdown() {
        qqRiskCountdown?.let(mainHandler::removeCallbacks)
        qqRiskCountdown = null
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
                if (!authCompletionStarted &&
                    !isFinishing &&
                    !isDestroyed &&
                    shouldAutoComplete(url) &&
                    collectCookieHeader(logOnly = true) != null
                ) {
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
                    text = authHintText()
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
                    text = text("streaming.web.auth.open.browser")
                    setOnClickListener {
                        openLoginInExternalBrowser()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                Button(this@StreamingWebAuthActivity).apply {
                    text = if (providerName() == StreamingProviderName.QQ_MUSIC) {
                        text("streaming.web.auth.manual.cookie")
                    } else {
                        text("streaming.web.auth.done")
                    }
                    setOnClickListener {
                        if (providerName() == StreamingProviderName.QQ_MUSIC) {
                            finishWithAuthCallback(manualCookieCallbackUri())
                        } else {
                            finishWithAuthCallback(fallbackCallbackUri())
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun authHintText(): String {
        return if (providerName() == StreamingProviderName.QQ_MUSIC) {
            text("streaming.web.auth.qq.hint")
        } else {
            text("streaming.web.auth.hint")
        }
    }

    private fun openLoginInExternalBrowser() {
        val url = intent.getStringExtra(EXTRA_URL) ?: lastProviderUrl
        if (url.isNullOrBlank()) {
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, text("streaming.web.auth.browser.failed"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldAutoComplete(url: String): Boolean {
        return providerName() != StreamingProviderName.KUGOU &&
            !url.contains("/login", ignoreCase = true) &&
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
        if (!loginPageStarted) {
            finish()
            return
        }
        finishWithAuthCallback(fallbackCallbackUri())
    }

    private fun finishWithAuthCallback(uri: Uri) {
        if (authCompletionStarted || isFinishing || isDestroyed) {
            return
        }
        authCompletionStarted = true
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

    private fun manualCookieCallbackUri(): Uri {
        return fallbackCallbackUri()
            .buildUpon()
            .appendQueryParameter("manualCookie", "1")
            .build()
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
        val provider = intent.getStringExtra(EXTRA_PROVIDER)
            ?.takeIf { it.isNotBlank() }
            ?.let { StreamingProviderName.fromWireName(it) }
        val header = AndroidStreamingWebCookieSessionSource.collectCookieHeader(
            provider = provider,
            extraCandidates = listOfNotNull(lastProviderUrl, intent.getStringExtra(EXTRA_URL))
        )
        if (logOnly) {
            Log.d(TAG, "Streaming auth cookie is ${if (header == null) "not ready" else "ready"} for provider=$provider")
        }
        return header
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun providerName(): StreamingProviderName? {
        return intent.getStringExtra(EXTRA_PROVIDER)
            ?.takeIf { it.isNotBlank() }
            ?.let { StreamingProviderName.fromWireName(it) }
    }

    private fun text(key: String): String {
        return AppLanguage.text(AppLanguage.MODE_SYSTEM, key)
    }

    companion object {
        private const val TAG = "StreamingWebAuth"
        private const val QQ_LOGIN_RISK_CONFIRM_SECONDS = 5
        private const val ONE_SECOND_MS = 1_000L
        const val EXTRA_PROVIDER: String = "app.yukine.extra.PROVIDER"
        const val EXTRA_URL: String = "app.yukine.extra.URL"
        const val EXTRA_COOKIE_HEADER: String = "app.yukine.extra.COOKIE_HEADER"
        private var webViewSuffixApplied: Boolean = false

        /** Must run before any CookieManager access so session maintenance uses the isolated jar. */
        @JvmStatic
        internal fun prepareStreamingAuthCookieStore() {
            applyWebViewDataDirectorySuffix()
        }

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
