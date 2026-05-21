package io.alist.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.alist.app.alist.AListManager
import io.alist.app.databinding.ActivityWebviewBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebViewActivity"
    }

    private lateinit var binding: ActivityWebviewBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide system bars for immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setupActivityResultLaunchers()
        setupWebView()
        loadAListUrl()
    }

    private fun setupActivityResultLaunchers() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val resultUri = if (data?.data != null) {
                    arrayOf(data.data!!)
                } else if (cameraPhotoUri != null) {
                    arrayOf(cameraPhotoUri!!)
                } else {
                    null
                }
                fileUploadCallback?.onReceiveValue(resultUri)
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
        }

        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fileUploadCallback?.onReceiveValue(
                    if (cameraPhotoUri != null) arrayOf(cameraPhotoUri!!) else null
                )
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = binding.webview.settings

        // JavaScript
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true

        // DOM Storage
        webSettings.domStorageEnabled = true

        // Cache
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        // Zoom
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false

        // Viewport
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        // Mixed content (for local HTTP)
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Allow file access
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true

        // Multi-window support
        webSettings.setSupportMultipleWindows(false)

        // Cookie
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webview, true)

        // Dark mode
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true)
        }

        // User Agent
        webSettings.userAgentString = "${webSettings.userAgentString} AListAndroid/1.0.0"

        // WebViewClient
        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // Handle alist:// deep links
                if (url.startsWith("alist://")) {
                    return true
                }

                // External links -> open in browser
                if (!url.startsWith("http://127.0.0.1") && !url.startsWith(AListManager.ALIST_URL)) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening external URL", e)
                    }
                    return true
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished loading: $url")
            }
        }

        // WebChromeClient for file upload and download
        binding.webview.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Cancel any existing callback
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                // Create file chooser intent
                val intent = fileChooserParams?.createIntent()
                if (intent != null) {
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching file chooser", e)
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = null
                        return false
                    }
                }
                return true
            }
        }

        // Download listener
        binding.webview.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling download", e)
                Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAListUrl() {
        binding.webview.loadUrl(AListManager.ALIST_URL)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webview.canGoBack()) {
            binding.webview.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        binding.webview.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        binding.webview.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        binding.webview.apply {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }
}
