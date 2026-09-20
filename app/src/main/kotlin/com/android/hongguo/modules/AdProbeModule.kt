package com.android.hongguo.modules

import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface

object AdProbeModule : IHookModule {
    private const val TAG = "AdProbe"
    override fun getModuleName() = "AdProbeModule"
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching { AdProbe.hook(param.defaultClassLoader); LogUtils.logI(TAG, "AdProbeModule hook done") }
            .onFailure { e -> LogUtils.logE(TAG, "AdProbeModule hook failed: ${e.message}", e) }
    }
}

object AdProbe {
    private const val TAG = "AdProbe"
    private const val KEY_AD_BLOCK = "ad_block"
    private const val KEY_VIP_UNLOCK = "vip_unlock"
    private const val KEY_AD = "Ad_key"
    private const val KEY_AD_END = "AdEndLayer_key"
    private const val KEY_AD_ICON = "AdIconLayer_key"
    private const val KEY_VIP_SUBTYPE = "VipSubType_key"
    private val VIP_KEYS = listOf("Vip_key", "NsUser_key", "NsComic_key", "NsVip_key")

    private fun adBlockOn() = MMKVManager.getBoolean(KEY_AD_BLOCK, true)
    private fun vipOn() = MMKVManager.getBoolean(KEY_VIP_UNLOCK, false)

    fun hook(cl: ClassLoader) {
        hookByKey(cl, KEY_AD, ReturnMode.FALSE); hookByKey(cl, KEY_AD_END, ReturnMode.FALSE); hookByKey(cl, KEY_AD_ICON, ReturnMode.FALSE)
        VIP_KEYS.forEach { hookVipFromKey(cl, it) }
    }

    private fun hookVipFromKey(cl: ClassLoader, key: String) {
        val raw = MMKVManager.getString(key, "")
        if (raw.isEmpty()) { LogUtils.logW(TAG, "$key 为空"); return }
        val modelCls = vipModelClass()?.let { runCatching { cl.loadClass(it) }.getOrNull() }
        raw.split(",").forEach { desc ->
            val t = HookDescriptor.parse(desc)
            if (t == null) { LogUtils.logW(TAG, "$key 解析失败: $desc"); return@forEach }
            runCatching {
                val methods = cl.loadClass(t.className).declaredMethods.filter { it.name == t.methodName }
                if (methods.isEmpty()) { LogUtils.logW(TAG, "未找到 ${t.className}#${t.methodName}"); return@forEach }
                methods.forEach { m ->
                    val rt = m.returnType
                    val hooker: ((Any?, Array<Any?>, Any?) -> Any?)? = when {
                        rt == Boolean::class.javaPrimitiveType || rt == java.lang.Boolean::class.java -> { _, _, r -> if (vipOn()) true else r }
                        rt == List::class.java -> { _, _, r -> if (vipOn()) buildFakeVipList(cl) else r }
                        modelCls != null && rt == modelCls -> { _, _, r -> if (vipOn()) buildFakeVip(cl) else r }
                        else -> null
                    }
                    if (hooker == null) { LogUtils.logW(TAG, "跳过 ${t.className}#${m.name} 返回类型 ${rt.simpleName}"); return@forEach }
                    XposedManager.hookMethod(m, afterMethod = hooker)
                    LogUtils.logI(TAG, "✓ $key ${t.className}#${m.name} (${rt.simpleName})")
                }
            }.onFailure { e -> LogUtils.logE(TAG, "✗ $key ${t.className}#${t.methodName}: ${e.message}", e) }
        }
    }

    private fun vipModelClass(): String? {
        val raw = MMKVManager.getString("Vip_key", "")
        if (raw.isEmpty()) return null
        return raw.split(",").asSequence().mapNotNull { HookDescriptor.parse(it)?.returnDescriptor }
            .mapNotNull { HookDescriptor.descriptorToClassName(it) }.firstOrNull { it != "java.util.List" }
    }

    private fun vipSubTypeClass(): String? = MMKVManager.getString(KEY_VIP_SUBTYPE, "").ifEmpty { null }

    private fun buildFakeVipList(cl: ClassLoader): List<Any> = runCatching {
        val subName = vipSubTypeClass()
        if (subName == null) { LogUtils.logW(TAG, "$KEY_VIP_SUBTYPE 未配置，返回空列表"); return emptyList() }
        val sub = Class.forName(subName, false, cl)
        (sub.enumConstants ?: emptyArray()).mapNotNull { runCatching { buildFakeVip(cl, it) }.getOrNull() }
    }.getOrElse { emptyList() }

    private fun buildFakeVip(cl: ClassLoader, subType: Any? = null): Any? = runCatching {
        val modelName = vipModelClass(); val subName = vipSubTypeClass()
        if (modelName == null) { LogUtils.logW(TAG, "Vip_key 未解析到 VipInfoModel"); return null }
        if (subName == null) { LogUtils.logW(TAG, "$KEY_VIP_SUBTYPE 未配置，无法造模型"); return null }
        val model = Class.forName(modelName, false, cl); val sub = Class.forName(subName, false, cl)
        val st = subType ?: sub.enumConstants?.firstOrNull()
        model.getConstructor(String::class.java, String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, sub
        ).newInstance("2099-12-31 23:59:59", "1", "99999999", true, false, 0, true, st)
    }.getOrNull()

    enum class ReturnMode { FALSE, NULL }

    private fun hookByKey(cl: ClassLoader, key: String, mode: ReturnMode) {
        val desc = MMKVManager.getString(key, "")
        if (desc.isNullOrEmpty()) { LogUtils.logW(TAG, "$key 为空，跳过"); return }
        val t = HookDescriptor.parse(desc)
        if (t == null) { LogUtils.logW(TAG, "$key 解析失败: $desc"); return }
        runCatching {
            XposedManager.findAndHookMethod(className = t.className, classLoader = cl, methodName = t.methodName,
                afterMethod = { _, _, result ->
                    if (adBlockOn()) { LogUtils.logI(TAG, "${t.className}#${t.methodName} → ${mode.name} (原=$result)"); when (mode) { ReturnMode.FALSE -> false; ReturnMode.NULL -> null } } else result
                })
            LogUtils.logI(TAG, "✓ $key → ${t.className}#${t.methodName}")
        }.onFailure { e -> LogUtils.logE(TAG, "✗ $key hook 失败: ${e.message}", e) }
    }
}