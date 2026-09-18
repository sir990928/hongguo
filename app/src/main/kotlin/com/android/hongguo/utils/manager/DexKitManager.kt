package com.android.hongguo.utils.manager

import com.android.hongguo.utils.LogUtils
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.*
import org.luckypray.dexkit.query.matchers.*
import java.util.concurrent.Executors

object DexKitManager {
    private var bridge: DexKitBridge? = null
    private val sTP = Executors.newSingleThreadExecutor()
    private const val DEBUG_MODE = true
    private var isL = false; private var isI = false; private var onCacheReady: (() -> Unit)? = null
    
    fun setOnCacheReadyListener(listener: (() -> Unit)?) { this.onCacheReady = listener }
    
    private fun gT() = "D" + "e" + "x" + "U" + "l" + "t" + "r" + "a"
    private fun sSt() = "S" + "T" + "A" + "R" + "T"
    private fun sFi() = "F" + "I" + "N" + "I" + "S" + "H"

    /**
     * 初始化入口
     * @param scanBlock 传入具体业务的扫描实现（如 HongGuoPreScan::doScan）
     */
    fun init(apkPath: String, forceSearch: Boolean = false, scanBlock: ((DexKitBridge) -> Unit)? = null, onCompleted: (() -> Unit)? = null) {
        val vK = "d" + "k" + "_" + "v" + "_" + apkPath.hashCode()
        if (!forceSearch && !DEBUG_MODE && MMKVManager.getBoolean(vK, false)) {
            isI = true; onCompleted?.invoke(); onCacheReady?.invoke(); return 
        }
        runCatching { loadSo(); bridge = DexKitBridge.create(apkPath) }.onFailure { return }

        val task = Runnable {
            try {
                LogUtils.logI("${gT()}: >>> " + sSt()); val sTime = System.currentTimeMillis()
                val b = bridge
                if (b != null) {
                    scanBlock?.invoke(b) // 执行外部传入的预扫描逻辑
                }
                MMKVManager.putBoolean(vK, true); isI = true
                LogUtils.logI("${gT()}: <<< " + sFi() + " " + (System.currentTimeMillis() - sTime) + "ms")
                onCacheReady?.invoke(); onCompleted?.invoke()
            } catch (e: Exception) { onCompleted?.invoke() }
        }
        if (forceSearch) sTP.execute(task) else task.run()
    }

    // =========================================================================
    // 公开匹配工具 API
    // =========================================================================

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
        try {
            val mm = MethodMatcher.create()
            if (modifiers != 0) mm.modifiers(modifiers)
            paramTypes?.let { mm.paramTypes(it) }
            s?.let { mm.usingStrings(listOf(it)) }
            methodName?.let { mm.name(it) }
            returnType?.let { mm.returnType(it) }
            strings?.let { mm.usingStrings(it) }
            methodCalls?.forEach { c -> mm.addInvoke { name = c } }
            if (classPrefix != null || fieldNames != null) {
                val cm = ClassMatcher.create()
                classPrefix?.let { cm.className(it) }
                fieldNames?.forEach { f -> cm.addField { name = f } }
                mm.declaredClass(cm)
            }
            val res = b.findMethod(FindMethod.create().matcher(mm)) ?: return null
            for (method in res) {
                if (requireNoStrings && method.usingStrings.isNotEmpty()) continue
                val rt = method.returnType.toString()
                if (filter?.invoke(rt) ?: true) return method.descriptor
            }
        } catch (e: Exception) { }
        return null
    }

    fun fM(bridge: DexKitBridge? = DexKitManager.bridge, s: String): String? = fMQuery(bridge, s = s)
    fun fM(bridge: DexKitBridge? = DexKitManager.bridge, p: List<String>, r: String? = null): String? = fMQuery(bridge, paramTypes = p, returnType = r)
    fun fM(bridge: DexKitBridge? = DexKitManager.bridge, ss: List<String>): String? = fMQuery(bridge, strings = ss)
    fun fM(bridge: DexKitBridge? = DexKitManager.bridge, cn: String, mn: String): String? = fMQuery(bridge, classPrefix = cn, methodName = mn)

    fun fC(bridge: DexKitBridge? = DexKitManager.bridge, className: String): String? {
        val b = bridge ?: return null
        return try {
            val res = b.findClass(FindClass.create().matcher(ClassMatcher.create().name(className)))
            val target = res.firstOrNull() ?: return null
            "L${target.toString().replace(".", "/")};"
        } catch (e: Exception) { null }
    }

    fun fCByStrings(bridge: DexKitBridge? = DexKitManager.bridge, vararg ss: String): String? {
        val b = bridge ?: return null
        return try {
            val res = b.findClass(FindClass.create().matcher(ClassMatcher.create().usingStrings(ss.toList())))
            val target = res.firstOrNull() ?: return null
            "L${target.toString().replace(".", "/")};"
        } catch (e: Exception) { null }
    }

    fun fCByPattern(bridge: DexKitBridge? = DexKitManager.bridge, p: String): String? {
        val b = bridge ?: return null
        return try {
            val res = b.findClass(FindClass.create().matcher(ClassMatcher.create().className(p)))
            val target = res.firstOrNull() ?: return null
            "L${target.toString().replace(".", "/")};"
        } catch (e: Exception) { null }
    }

    private fun loadSo() { if (!isL) try { System.loadLibrary("dexkit"); isL = true } catch (e: Throwable) {} }
    fun clearCache() { DexCacheManager.clearCache(); isI = false }
    fun close() { try { bridge?.close() } catch (e: Exception) {}; bridge = null }
}
