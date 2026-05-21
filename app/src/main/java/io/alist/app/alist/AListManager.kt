package io.alist.app.alist

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class AListManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AListManager"
        private const val ALIST_DIR_NAME = "alist"
        private const val ALIST_BINARY_NAME = "alist"
        private const val ALIST_ASSET_PATH = "alist/alist"
        private const val PREF_NAME = "alist_prefs"
        private const val KEY_BINARY_VERSION = "binary_version"
        private const val CURRENT_BINARY_VERSION = "1"
        const val ALIST_PORT = 5244
        const val ALIST_URL = "http://127.0.0.1:$ALIST_PORT"

        private val DOWNLOAD_URLS = listOf(
            "https://ghfast.top/https://github.com/AlistGo/alist/releases/latest/download/alist-android-arm64.tar.gz",
            "https://mirror.ghproxy.com/https://github.com/AlistGo/alist/releases/latest/download/alist-android-arm64.tar.gz",
            "https://gh-proxy.com/https://github.com/AlistGo/alist/releases/latest/download/alist-android-arm64.tar.gz",
            "https://github.com/AlistGo/alist/releases/latest/download/alist-android-arm64.tar.gz"
        )

        @Volatile
        private var instance: AListManager? = null

        fun getInstance(context: Context): AListManager {
            return instance ?: synchronized(this) {
                instance ?: AListManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val alistDir: File = File(context.filesDir, ALIST_DIR_NAME)
    private val alistBinary: File = File(alistDir, ALIST_BINARY_NAME)
    private val dataDir: File = File(context.filesDir, "alist_data")
    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRunning: Boolean = false
        private set

    val isReady: Boolean
        get() = alistBinary.exists() && alistBinary.canExecute()

    fun extractBinary(): Boolean {
        try {
            if (!alistDir.exists()) {
                alistDir.mkdirs()
            }
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val savedVersion = prefs.getString(KEY_BINARY_VERSION, "")

            if (isReady && savedVersion == CURRENT_BINARY_VERSION) {
                Log.d(TAG, "Binary already extracted and up to date")
                return true
            }

            Log.d(TAG, "Extracting AList binary from assets...")

            context.assets.open(ALIST_ASSET_PATH).use { input ->
                FileOutputStream(alistBinary).use { output ->
                    input.copyTo(output)
                }
            }

            val exitCode = Runtime.getRuntime().exec(
                arrayOf("chmod", "755", alistBinary.absolutePath)
            ).waitFor()
            if (exitCode != 0) {
                Log.e(TAG, "chmod failed with exit code: $exitCode")
                return false
            }

            prefs.edit().putString(KEY_BINARY_VERSION, CURRENT_BINARY_VERSION).apply()
            Log.d(TAG, "AList binary extracted successfully")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract AList binary", e)
            return false
        }
    }

    suspend fun downloadBinary(onProgress: ((Int) -> Unit)? = null): Boolean {
        if (!alistDir.exists()) alistDir.mkdirs()
        if (!dataDir.exists()) dataDir.mkdirs()

        for (url in DOWNLOAD_URLS) {
            try {
                Log.d(TAG, "Trying download from: $url")
                val connection = java.net.URL(url).openConnection()
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()

                val contentLength = connection.getHeaderFieldInt("Content-Length", -1)
                val input = connection.getInputStream()
                val tempFile = File(context.cacheDir, "alist_download.tar.gz")

                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (contentLength > 0) {
                            onProgress?.invoke((totalBytes * 100 / contentLength).toInt())
                        }
                    }
                }
                input.close()

                if (tempFile.length() < 10000) {
                    Log.w(TAG, "Downloaded file too small, probably error page from: $url")
                    tempFile.delete()
                    continue
                }

                Log.d(TAG, "Downloaded ${tempFile.length()} bytes, extracting...")
                val extractDir = File(context.cacheDir, "alist_extract")
                extractDir.mkdirs()

                val process = Runtime.getRuntime().exec(
                    arrayOf("tar", "-xzf", tempFile.absolutePath, "-C", extractDir.absolutePath)
                )
                val exitCode = process.waitFor()
                tempFile.delete()

                if (exitCode != 0) {
                    Log.e(TAG, "tar extraction failed with exit code: $exitCode")
                    extractDir.deleteRecursively()
                    continue
                }

                val binary = extractDir.listFiles()?.flatMap { it.listFiles()?.toList() ?: listOf(it) }
                    ?.find { it.name == "alist" && it.length() > 10000 }
                    ?: extractDir.listFiles()?.find { it.name == "alist" && it.length() > 10000 }

                if (binary != null) {
                    binary.copyTo(alistBinary, overwrite=true)
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", alistBinary.absolutePath)).waitFor()

                    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_BINARY_VERSION, CURRENT_BINARY_VERSION).apply()

                    extractDir.deleteRecursively()
                    Log.d(TAG, "AList binary downloaded and installed successfully")
                    return true
                } else {
                    Log.e(TAG, "alist binary not found in archive")
                    extractDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed from $url: ${e.message}")
            }
        }
        Log.e(TAG, "All download sources failed")
        return false
    }

    fun startAList(): Boolean {
        if (isRunning && isProcessAlive()) {
            Log.d(TAG, "AList is already running")
            return true
        }

        if (!isReady) {
            if (!extractBinary()) {
                Log.e(TAG, "Binary not ready and extraction failed")
                return false
            }
        }

        return try {
            val pb = ProcessBuilder(alistBinary.absolutePath, "server")
            pb.directory(dataDir)
            pb.environment()["ALIST_PORT"] = ALIST_PORT.toString()
            pb.redirectErrorStream(true)

            process = pb.start()
            isRunning = true

            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "AList: $line")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading AList output", e)
                }
            }

            scope.launch {
                try {
                    val exitCode = process!!.waitFor()
                    Log.w(TAG, "AList process exited with code: $exitCode")
                    isRunning = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error waiting for AList process", e)
                    isRunning = false
                }
            }

            Log.i(TAG, "AList started on port $ALIST_PORT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AList", e)
            isRunning = false
            false
        }
    }

    fun stopAList() {
        try {
            process?.let { p ->
                p.destroy()
                Thread.sleep(500)
                if (isProcessAlive()) {
                    @Suppress("DEPRECATION")
                    p.destroyForcibly()
                }
            }
            process = null
            isRunning = false
            Log.i(TAG, "AList stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AList", e)
        }
    }

    fun restartAList() {
        stopAList()
        Thread.sleep(1000)
        startAList()
    }

    fun isAListRunning(): Boolean = isRunning && isProcessAlive()

    private fun isProcessAlive(): Boolean {
        return try {
            process?.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        } catch (_: Exception) {
            false
        }
    }
}
