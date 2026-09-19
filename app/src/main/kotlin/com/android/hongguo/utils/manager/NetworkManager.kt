package com.android.hongguo.utils.manager

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object NetworkManager {

    private const val TAG = "NetworkManager"
    private const val NETWORK_DEBUG_LOG = false

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_RETRY = 2
    private const val PROGRESS_UPDATE_INTERVAL = 200L

    private val downloadExecutor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var progressToast: Toast? = null
    private var currentDownloadType = ""
    private var spinIndex = 0
    private val spinFrames = arrayOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    private val httpsClient: OkHttpClient by lazy { createHttpsClient() }

    private val context: Context?
        get() = HookContext.getContext()

    fun downloadMusic(url: String, fileName: String, subfolder: String, callback: DownloadCallback) {
        currentDownloadType = "music"
        if (NETWORK_DEBUG_LOG) LogUtils.logI(TAG, "音乐下载 url=${getDomainFromUrl(url)}")
        downloadFile(url, fileName, "audio/mp3", subfolder, callback)
    }

    fun downloadVideo(url: String, fileName: String, subfolder: String, callback: DownloadCallback) {
        currentDownloadType = "video"
        if (NETWORK_DEBUG_LOG) LogUtils.logI(TAG, "视频下载 url=${getDomainFromUrl(url)}")
        downloadFile(url, fileName, "video/mp4", subfolder, callback)
    }

    fun downloadFile(url: String, fileName: String, mimeType: String, subfolder: String, callback: DownloadCallback) {
        downloadExecutor.execute { performDownload(url, fileName, mimeType, subfolder, callback, 0) }
    }

    private fun createHttpsClient(): OkHttpClient {
        return runCatching {
            val sslContext = SSLContext.getDefault()
            val trustManager = getSystemTrustManager()
            val hostnameVerifier = getSafeHostnameVerifier()

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (NETWORK_DEBUG_LOG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier(hostnameVerifier)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(loggingInterceptor)
                .addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val newRequest = originalRequest.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "video/mp4,video/webm,video/ogg,audio/mpeg,audio/ogg,audio/wav,*/*;q=0.9")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "identity")
                        .header("Connection", "keep-alive")
                        .header("Sec-Fetch-Mode", "no-cors")
                        .header("Referer", "https://www.douyin.com/")
                        .header("Origin", "https://www.douyin.com")
                        .build()

                    val startTime = System.currentTimeMillis()
                    if (NETWORK_DEBUG_LOG) LogUtils.logI(TAG, "请求开始 host=${originalRequest.url.host}")

                    runCatching {
                        val response = chain.proceed(newRequest)
                        val duration = System.currentTimeMillis() - startTime
                        if (NETWORK_DEBUG_LOG) LogUtils.logI(TAG, "请求成功 host=${originalRequest.url.host} code=${response.code} time=${duration}ms")
                        response
                    }.getOrElse { e ->
                        if (NETWORK_DEBUG_LOG) LogUtils.logE(TAG, "请求异常 host=${originalRequest.url.host} err=${e.message}", e)
                        throw if (e is SSLException) e else java.io.IOException("网络请求失败: ${e.message}")
                    }
                }
                .build()
        }.getOrElse { e ->
            if (NETWORK_DEBUG_LOG) LogUtils.logE(TAG, "创建安全客户端失败 降级处理 err=${e.message}", e)
            createSimpleClient()
        }
    }

    private fun getSystemTrustManager(): X509TrustManager {
        return runCatching {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as java.security.KeyStore?)
            factory.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
        }.getOrNull() ?: object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }

    private fun getSafeHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, session ->
            runCatching {
                val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                val result = defaultVerifier.verify(hostname, session)
                if (!result && (hostname.contains("douyin") || hostname.contains("tiktok") || hostname.contains("bytedance"))) {
                    if (NETWORK_DEBUG_LOG) LogUtils.logI(TAG, "放宽目标域名验证 host=$hostname")
                    return@HostnameVerifier true
                }
                result
            }.getOrElse { false }
        }
    }

    private fun createSimpleClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun performDownload(url: String, fileName: String, mimeType: String, subfolder: String, callback: DownloadCallback, retryCount: Int) {
        val totalStartTime = System.currentTimeMillis()
        val typeLabel = if (currentDownloadType == "music") "音频" else "视频"

        runCatching {
            updateProgressToast(getSpinningText("连接中 $typeLabel"), callback, 0)
            val request = Request.Builder().url(url).build()

            httpsClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}"
                    if (shouldRetry(response.code) && retryCount < MAX_RETRY) {
                        performDownload(url, fileName, mimeType, subfolder, callback, retryCount + 1)
                        return
                    }
                    safeCallbackError(callback, errorMsg)
                    return
                }

                val body = response.body ?: run {
                    safeCallbackError(callback, "服务器返回空数据")
                    return
                }

                val contentLength = body.contentLength()
                updateProgressToast(getSpinningText("下载中 $typeLabel"), callback, 0)

                body.byteStream().use { inputStream ->
                    saveStream(inputStream, fileName, mimeType, subfolder, contentLength, callback, totalStartTime)
                }
            }
        }.onFailure { e ->
            if (e is Exception && isRetryableException(e) && retryCount < MAX_RETRY) {
                performDownload(url, fileName, mimeType, subfolder, callback, retryCount + 1)
            } else {
                safeCallbackError(callback, getFriendlyErrorMessage(e as Exception))
            }
        }
    }

    private fun saveStream(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        subfolder: String,
        contentLength: Long,
        callback: DownloadCallback,
        totalStartTime: Long
    ) {
        var uri: Uri? = null
        runCatching {
            uri = createMediaFile(fileName, mimeType, subfolder) ?: throw Exception("创建媒体文件失败")
            val targetContext = context ?: throw Exception("Context缺失 无法写入存储")
            
            targetContext.contentResolver.openOutputStream(uri!!)?.use { outputStream ->
                downloadWithProgress(inputStream, outputStream, contentLength, callback, totalStartTime, subfolder)
            } ?: throw Exception("打开目标输出流失败")
        }.onFailure { e ->
            uri?.let {
                runCatching { context?.contentResolver?.delete(it, null, null) }
            }
            safeCallbackError(callback, getFriendlyErrorMessage(e as Exception))
        }
    }

    private fun downloadWithProgress(
        inputStream: InputStream,
        outputStream: OutputStream,
        contentLength: Long,
        callback: DownloadCallback,
        totalStartTime: Long,
        subfolder: String
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalBytes = 0L
        val downloadStartTime = System.currentTimeMillis()
        var lastUpdateTime = 0L
        val typeLabel = if (currentDownloadType == "music") "音频" else "视频"

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytes += bytesRead

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > PROGRESS_UPDATE_INTERVAL || totalBytes == contentLength) {
                val progress = if (contentLength > 0) (totalBytes * 100 / contentLength).toInt() else 0
                val progressMsg = if (contentLength > 0) {
                    getSpinningText("下载中 $typeLabel $progress%")
                } else {
                    getSpinningText("下载中 $typeLabel ${formatFileSize(totalBytes)}")
                }
                updateProgressToast(progressMsg, callback, progress)
                lastUpdateTime = currentTime
            }
        }

        completeDownload(totalStartTime, downloadStartTime, totalBytes, callback, subfolder)
    }

    private fun completeDownload(totalStartTime: Long, downloadStartTime: Long, totalBytes: Long, callback: DownloadCallback, subfolder: String) {
        val totalTime = System.currentTimeMillis() - totalStartTime
        val downloadTime = System.currentTimeMillis() - downloadStartTime
        val speed = if (downloadTime > 0) (totalBytes / 1024.0 / 1024.0) / (downloadTime / 1000.0) else 0.0

        if (NETWORK_DEBUG_LOG) {
            val info = String.format(Locale.US, "size=%s totalTime=%.1fs speed=%.1fMB/s", formatFileSize(totalBytes), totalTime / 1000.0, speed)
            LogUtils.logSuccess(TAG, "下载完成 $info")
        }

        safeCallbackProgress(callback, DownloadProgress.COMPLETED, "下载完成", 100)
        cancelProgressToast()
        spinIndex = 0

        showToast("已保存到 $subfolder")
    }

    private fun createMediaFile(fileName: String, mimeType: String, subfolder: String): Uri? {
        val targetContext = context ?: return null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, subfolder)
            }
            val uri = if (mimeType.startsWith("video")) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            targetContext.contentResolver.insert(uri, values)
        }.getOrNull()
    }

    private fun getSpinningText(text: String): String {
        val spinFrame = spinFrames[spinIndex]
        spinIndex = (spinIndex + 1) % spinFrames.size
        return "$spinFrame $text"
    }

    private fun getDomainFromUrl(url: String): String {
        return runCatching { URL(url).host }.getOrDefault(url)
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun shouldRetry(statusCode: Int): Boolean = statusCode == 408 || statusCode == 429 || statusCode in 500..599

    private fun isRetryableException(e: Exception): Boolean {
        return e is java.net.SocketTimeoutException || e is java.net.ConnectException || e is java.io.IOException
    }

    private fun getFriendlyErrorMessage(e: Exception): String {
        return when (e) {
            is java.net.SocketTimeoutException -> "连接超时"
            is java.net.ConnectException -> "网络连接失败"
            is SSLException -> "安全连接失败 请检查网络配置"
            is java.io.IOException -> "网络IO异常"
            else -> e.message ?: "下载失败"
        }
    }

    private fun safeCallbackError(callback: DownloadCallback?, error: String) {
        cancelProgressToast()
        spinIndex = 0
        callback?.let { mainHandler.post { it.onError(error) } }
        showToast(error)
    }

    private fun safeCallbackProgress(callback: DownloadCallback?, progress: DownloadProgress, message: String, percent: Int) {
        callback?.let { mainHandler.post { it.onProgress(progress, message, percent) } }
    }

    private fun updateProgressToast(message: String, callback: DownloadCallback?, percent: Int) {
        safeCallbackProgress(callback, DownloadProgress.DOWNLOADING, message, percent)
        showProgressToast(message)
    }

    private fun showProgressToast(message: String) {
        val targetContext = context ?: return
        mainHandler.post {
            progressToast?.setText(message) ?: run {
                progressToast = Toast.makeText(targetContext, message, Toast.LENGTH_LONG)
            }
            progressToast?.show()
        }
    }

    private fun cancelProgressToast() {
        mainHandler.post {
            progressToast?.cancel()
            progressToast = null
        }
    }

    private fun showToast(message: String) {
        val targetContext = context ?: return
        mainHandler.post {
            Toast.makeText(targetContext, message, Toast.LENGTH_LONG).show()
        }
    }

    fun destroy() {
        downloadExecutor.shutdown()
    }

    enum class DownloadProgress { CONNECTING, DOWNLOADING, COMPLETED }

    interface DownloadCallback {
        fun onProgress(progress: DownloadProgress, message: String, percent: Int)
        fun onError(error: String)
    }
}
