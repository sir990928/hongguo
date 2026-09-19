package com.android.hongguo.utils.manager

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtils {
    private val dateFormat = SimpleDateFormat("yyyy‑MM‑dd HH:mm:ss.SSS", Locale.getDefault())
    private var targetPkg = "com.phoenix.read"

    fun init(tag: String = "HongGuo", debug: Boolean = true, processName: String = "", packageName: String = "") {
        if (packageName.isNotEmpty()) {
            targetPkg = packageName
        }
        // 根据目标包名自动创建完整目录链
        val fullLogDir = File("/storage/emulated/0/Android/media/$targetPkg/hook_logs/")
        runCatching { fullLogDir.mkdirs() }

        logI("LogUtils", "初始化完成 | 目标包：$targetPkg | 日志目录：$fullLogDir")
    }

    fun logI(tag: String, msg: String = "") {
        Log.i(tag, "[$tag] $msg")
        writeToFile("I", tag, msg)
    }

    fun logSuccess(tag: String, msg: String = "") {
        Log.i(tag, "[$tag] SUCCESS: $msg")
        writeToFile("S", tag, msg)
    }

    fun logW(tag: String, msg: String = "") {
        Log.w(tag, "[$tag] $msg")
        writeToFile("W", tag, msg)
    }

    fun logE(tag: String, msg: String = "", e: Throwable? = null) {
        Log.e(tag, "[$tag] $msg", e)
        val fullMsg = if (e != null) "$msg\n${Log.getStackTraceString(e)}" else msg
        writeToFile("E", tag, fullMsg)
    }

    fun logE(tag: String, e: Throwable, msg: String = "") {
        val detailMsg = if (msg.isNotEmpty()) "$msg: ${e.message}" else e.message ?: "Unknown Exception"
        Log.e(tag, "[$tag] $detailMsg", e)
        writeToFile("E", tag, "$detailMsg\n${Log.getStackTraceString(e)}")
    }

    @Synchronized
    private fun writeToFile(level: String, tag: String, msg: String) {
        val timeStr = dateFormat.format(Date())
        val logLine = "$timeStr [$level][$tag]: $msg\n"
        val logFile = File("/storage/emulated/0/Android/media/$targetPkg/hook_logs/hongguo_hook.txt")
        writeSingleFile(logFile, logLine)
    }

    private fun writeSingleFile(file: File, content: String) {
        try {
            file.parentFile?.mkdirs()
            FileWriter(file, true).use { writer ->
                writer.write(content)
            }
        } catch (_: Exception) {
        }
    }
}
