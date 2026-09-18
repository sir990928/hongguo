package com.android.hongguo.utils

import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtils {
    private const val TAG = "HongGuo"
    private const val LOG_ROOT = "/storage/emulated/0/Android/media"
    private const val LOG_DIR = "logs"
    private const val LOG_RETENTION_MS = 24L * 60L * 60L * 1000L

    private var moduleName = "HongGuo"
    private var isDebug = true
    private var isEnabled = true

    private var logFile: File? = null
    private var writer: FileWriter? = null
    private var fileLogAvailable = false

    private val timeFormat by lazy { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    private val fileDateFormat by lazy { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }

    fun init(name: String, debug: Boolean, processName: String?, ownerPackage: String) {
        moduleName = name
        isDebug = debug

        logI("LogSystem", "init $moduleName")

        initFileLog(processName, ownerPackage)
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        logI("LogSystem", "status changed enabled=$enabled")
    }

    fun isEnabled(): Boolean = isEnabled

    private fun initFileLog(processName: String?, ownerPackage: String) {
        runCatching {
            val logDirPath = "$LOG_ROOT/$ownerPackage/$LOG_DIR"
            val dir = File(logDirPath)
            if (!dir.exists()) dir.mkdirs()

            cleanExpiredLogs(dir)

            val safeProcName = (processName ?: "unknown")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .takeLast(56)
            val pid = Process.myPid()
            val fileName = "${fileDateFormat.format(Date())}_${safeProcName}_pid${pid}.log"

            logFile = File(dir, fileName)
            writer = FileWriter(logFile, true)
            fileLogAvailable = true

            writeFileRaw("===================================")
            writeFileRaw("init log file=${logFile?.absolutePath}")
            writeFileRaw("process=$safeProcName pid=$pid ownerPackage=$ownerPackage")
            writeFileRaw("===================================")
        }.onFailure { ex ->
            fileLogAvailable = false
            logW("LogSystem", "init file log failed fallback err=${ex.message}")
        }
    }

    private fun cleanExpiredLogs(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".log", ignoreCase = true)) {
                val m = f.lastModified()
                if (m > 0 && now - m > LOG_RETENTION_MS) {
                    f.delete()
                }
            }
        }
    }

    private fun writeFileRaw(text: String) {
        if (!fileLogAvailable) return
        runCatching {
            writer?.write(text)
            writer?.write("\n")
            writer?.flush()
        }
    }

    private fun writeFile(level: String, tag: String, msg: String) {
        if (!fileLogAvailable) return
        val ts = timeFormat.format(Date())
        writeFileRaw("$ts $level $tag: $msg")
    }

    // ==================== 碎片化英文日志输出方法 ====================
    fun logI(tag: String, message: String) {
        if (!isEnabled) return
        val fullMsg = "$tag: $message"
        if (isDebug) Log.i(TAG, fullMsg)
        writeFile("I", tag, message)
    }

    fun logI(message: String) = logI("Info", message)

    fun logD(tag: String, message: String) {
        if (!isEnabled || !isDebug) return
        val fullMsg = "$tag: $message"
        Log.d(TAG, fullMsg)
        writeFile("D", tag, message)
    }

    fun logD(message: String) = logD("Debug", message)

    fun logW(tag: String, message: String) {
        if (!isEnabled) return
        val fullMsg = "$tag: $message"
        if (isDebug) Log.w(TAG, fullMsg)
        writeFile("W", tag, message)
    }

    fun logW(message: String) = logW("Warn", message)

    @JvmOverloads
    fun logE(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val fullMsg = "$tag: $message"
        Log.e(TAG, fullMsg, throwable)

        writeFile("E", tag, message)
        throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            writeFileRaw(sw.toString())
        }
    }

    fun logE(message: String, throwable: Throwable? = null) = logE("Error", message, throwable)

    fun logSuccess(tag: String, message: String) {
        if (!isEnabled) return
        val fullMsg = "$tag: success $message"
        if (isDebug) Log.i(TAG, fullMsg)
        writeFile("S", tag, message)
    }

    fun logSuccess(message: String) = logSuccess("Success", message)

    // 别名与快捷方式
    fun d(tag: String, msg: String) = logD(tag, msg)
    fun i(tag: String, msg: String) = logI(tag, msg)
    fun w(tag: String, msg: String) = logW(tag, msg)
    @JvmOverloads fun e(tag: String, msg: String, tr: Throwable? = null) = logE(tag, msg, tr)

    fun info(message: String) = logI(message)
    fun debug(message: String) = logD(message)
    fun warn(message: String) = logW(message)
    fun error(message: String, tr: Throwable? = null) = logE(message, tr)
    fun success(message: String) = logSuccess(message)

    fun getLogFilePath(): String = logFile?.absolutePath ?: "file log disabled"
}
