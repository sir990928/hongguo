package com.android.hongguo.utils.manager

import com.android.hongguo.BuildConfig
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.File
import java.util.concurrent.Executors

object DexKitManager {
    @Volatile
    private var bridge: DexKitBridge? = null
    private val sTP = Executors.newSingleThreadExecutor()
    private var isL = false
    private var onCacheReady: (() -> Unit)? = null

    fun setOnCacheReadyListener(listener: (() -> Unit)?) {
        this.onCacheReady = listener
    }

    private fun gT() = "DexUltra"

    /**
     * 合并版 init：
     * - Debug 模式：彻底屏蔽缓存，每次启动 100% 重新扫描，方便调试。
     * - Release 模式：严格依赖缓存，仅首次安装或 APK 更新时才重新扫描。
     */
    fun init(
        apkPath: String,
        pkgName: String? = null,
        forceSearch: Boolean = false,
        scanBlock: ((DexKitBridge) -> Unit)? = null,
        onCompleted: (() -> Unit)? = null
    ) {
        val realPath = sanitizeApkPath(apkPath, pkgName)
        val apkFile = File(realPath)

        if (!apkFile.exists()) {
            LogUtils.logE(gT(), "init 失败，APK 文件不存在: $realPath")
            onCompleted?.invoke()
            return
        }

        // 构造动态缓存 Key：结合路径、文件大小、最后修改时间
        val vK = "dk_v_${realPath.hashCode()}_${apkFile.length()}_${apkFile.lastModified()}"

        // 🔥 核心判断：非 Debug 模式、非强制搜索 且 MMKV 已有缓存标记时，才跳过扫描
        val isReleaseMode = !BuildConfig.DEBUG
        if (isReleaseMode && !forceSearch && MMKVManager.getBoolean(vK, false)) {
            LogUtils.logI(gT(), "[Release] 命中 MMKV 版本缓存，跳过 DexKit 扫描")
            onCompleted?.invoke()
            onCacheReady?.invoke()
            return
        }

        if (BuildConfig.DEBUG) {
            LogUtils.logI(gT(), "======== [Debug 模式] 强制触发全量 DexKit 扫描 ========")
        } else {
            LogUtils.logI(gT(), "======== [Release 模式] 检测到新版 APK，开始扫描 ========")
        }

        val task = Runnable {
            try {
                loadSo()

                // 避免旧的 Bridge 造成 Native 内存泄露
                close()

                val b = DexKitBridge.create(realPath)
                bridge = b

                // 执行 Hook 特征码匹配逻辑
                scanBlock?.invoke(b)

                // 仅在 Release 模式下写入缓存标记，保证 Debug 下始终重搜
                if (isReleaseMode) {
                    MMKVManager.putBoolean(vK, true)
                    LogUtils.logI(gT(), "[Release] 扫描成功，已写入 MMKV 版本缓存")
                }

                onCacheReady?.invoke()
                onCompleted?.invoke()
            } catch (e: Throwable) {
                LogUtils.logE(gT(), "DexKit 扫描过程抛出异常: ${e.message}", e)
                onCompleted?.invoke()
            } finally {
                // 释放 Native 内存
                close()
            }
        }

        if (forceSearch) sTP.execute(task) else task.run()
    }

    /**
     * 过滤与净化 APK 路径，解决宿主插件化框架导致的 sourceDir 污染
     */
    private fun sanitizeApkPath(apkPath: String, pkgName: String?): String {
        if (apkPath.startsWith("/data/app/") && apkPath.contains("base.apk")) {
            return apkPath
        }

        if (pkgName != null) {
            val mainApkFromSystem = findMainApkViaSystem(pkgName)
            if (mainApkFromSystem != null) {
                return mainApkFromSystem
            }
        }

        return apkPath
    }

    private fun findMainApkViaSystem(pkgName: String): String? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            val compatInfoClass = Class.forName("android.content.res.CompatibilityInfo")
            val getApplicationInfoMethod = activityThreadClass.getDeclaredMethod(
                "getApplicationInfo",
                String::class.java,
                compatInfoClass,
                Int::class.javaPrimitiveType
            )
            getApplicationInfoMethod.isAccessible = true

            val appInfo = getApplicationInfoMethod.invoke(
                currentActivityThread,
                pkgName,
                null,
                0
            ) as? android.content.pm.ApplicationInfo

            val sourceDir = appInfo?.sourceDir
            if (!sourceDir.isNullOrEmpty() && File(sourceDir).exists()) {
                LogUtils.logI(gT(), "findMainApkViaSystem 获取主包成功: $sourceDir")
                return sourceDir
            }
            null
        } catch (e: Throwable) {
            LogUtils.logE(gT(), "findMainApkViaSystem 失败: ${e.message}", e)
            null
        }
    }

    private fun cleanDescriptor(desc: String?): String? {
        if (desc == null) return null
        return if (desc.startsWith("L") && desc.endsWith(";")) {
            desc.substring(1, desc.length - 1).replace('/', '.')
        } else {
            desc
        }
    }
    
    /**
 * 批量查询版：返回所有匹配的方法描述符（不是第一个）。
 * 用于"一次扫出整个类里的一批方法"，如 VIP 的 is/has/can。
 */
fun fMQueryAll(
    bridge: DexKitBridge? = DexKitManager.bridge,
    methodName: String? = null,
    paramTypes: List<String>? = null,
    classPrefix: String? = null,
    returnType: String? = null,
    modifiers: Int = 0,
    strings: List<String>? = null,
    methodCalls: List<String>? = null
): List<String> {
    val b = bridge ?: return emptyList()
    return try {
        val mm = MethodMatcher()
        if (modifiers != 0) mm.modifiers(modifiers)
        paramTypes?.let { mm.paramTypes(it) }
        methodName?.let { mm.name(it) }
        returnType?.let { mm.returnType(it) }
        strings?.let { mm.usingStrings(it) }

        methodCalls?.forEach { c ->
            mm.addInvoke { name = c }
        }

        if (classPrefix != null) {
            mm.declaredClass(ClassMatcher().className(classPrefix))
        }

        b.findMethod(FindMethod.create().matcher(mm))
            .map { it.descriptor }
    } catch (e: Throwable) {
        LogUtils.logE(gT(), "fMQueryAll Exception [class=$classPrefix, name=$methodName]: ${e.message}", e)
        emptyList()
    }
}

    fun fMQuery(
        bridge: DexKitBridge? = DexKitManager.bridge,
        s: String? = null,
        methodName: String? = null,
        paramTypes: List<String>? = null,
        classPrefix: String? = null,
        fieldNames: List<String>? = null,
        modifiers: Int = 0,
        returnType: String? = null,
        strings: List<String>? = null,
        requireNoStrings: Boolean = false,
        methodCalls: List<String>? = null,
        filter: ((String) -> Boolean)? = null
    ): String? {
        val b = bridge ?: return null
        return try {
            val mm = MethodMatcher()
            if (modifiers != 0) mm.modifiers(modifiers)
            paramTypes?.let { mm.paramTypes(it) }
            s?.let { mm.usingStrings(listOf(it)) }
            methodName?.let { mm.name(it) }
            returnType?.let { mm.returnType(it) }
            strings?.let { mm.usingStrings(it) }

            methodCalls?.forEach { c ->
                mm.addInvoke { name = c }
            }

            if (classPrefix != null || fieldNames != null) {
                val cm = ClassMatcher()
                classPrefix?.let { cm.className(it) }
                fieldNames?.forEach { fieldNameStr ->
                    cm.addField(FieldMatcher().name(fieldNameStr))
                }
                mm.declaredClass(cm)
            }

            val res = b.findMethod(FindMethod.create().matcher(mm))

            for (methodData in res) {
                if (requireNoStrings && methodData.usingStrings.isNotEmpty()) continue
                val rt = methodData.returnType?.descriptor ?: ""
                if (filter?.invoke(rt) != false) {
                    return methodData.descriptor
                }
            }
            null
        } catch (e: Throwable) {
            LogUtils.logE(gT(), "fMQuery Exception [methodName=$methodName, s=$s]: ${e.message}", e)
            null
        }
    }

    fun fM(bridge: DexKitBridge? = DexKitManager.bridge, s: String): String? =
        fMQuery(bridge, s = s)

    fun fC(bridge: DexKitBridge? = DexKitManager.bridge, className: String): String? {
        val b = bridge ?: return null
        return try {
            val res = b.findClass(FindClass.create().matcher(ClassMatcher().className(className)))
            cleanDescriptor(res.firstOrNull()?.descriptor)
        } catch (e: Throwable) {
            LogUtils.logE(gT(), "fC Exception [className=$className]: ${e.message}", e)
            null
        }
    }

    fun fCByStrings(bridge: DexKitBridge? = DexKitManager.bridge, vararg ss: String): String? {
        val b = bridge ?: return null
        return try {
            val res = b.findClass(FindClass.create().matcher(ClassMatcher().usingStrings(ss.toList())))
            cleanDescriptor(res.firstOrNull()?.descriptor)
        } catch (e: Throwable) {
            LogUtils.logE(gT(), "fCByStrings Exception: ${e.message}", e)
            null
        }
    }

    fun loadSo() {
        if (!isL) {
            runCatching {
                System.loadLibrary("dexkit")
                isL = true
            }.onFailure { e ->
                LogUtils.logE(gT(), "loadLibrary dexkit 失败: ${e.message}", e)
            }
        }
    }

    fun close() {
        runCatching {
            bridge?.close()
            bridge = null
        }
    }
}
