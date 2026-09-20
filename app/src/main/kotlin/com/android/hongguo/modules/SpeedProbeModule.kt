package com.android.hongguo.modules

import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface

object SpeedProbeModule : IHookModule {
    private const val TAG = "SpeedProbeModule"
    override fun getModuleName(): String = "SpeedProbeModule"
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching { SpeedProbeTest.hook(param.defaultClassLoader); LogUtils.logI(TAG, "SpeedProbeModule hook done") }
            .onFailure { e -> LogUtils.logE(TAG, "SpeedProbeModule hook failed: ${e.message}", e) }
    }
}

object SpeedProbeTest {
    private const val TAG = "SpeedProbe"
    private const val KEY_SPEED = "Multiple_key"
    private const val KEY_QUALITY = "1080p_key"
    private const val KEY_RES_GET = "1080p_key"
    private const val KEY_PLAY_SPEED = "play_speed"
    private const val KEY_HIGHEST_QUALITY = "highest_quality"
    private val WANT_QUALITY = listOf("4k", "1080p+", "1080p", "720p")
    private val IGNORE_NAMES = setOf("auto")

    fun hook(cl: ClassLoader) { hookSpeed(cl); hookGetResolution(cl) }

    private fun hookSpeed(cl: ClassLoader) {
        val target = HookDescriptor.parse(MMKVManager.getString(KEY_SPEED, "")) ?: run { LogUtils.logW(TAG, "speed desc 解析失败"); return }
        runCatching {
            XposedManager.findAndHookMethod(className = target.className, classLoader = cl, methodName = target.methodName,
                parameterTypes = arrayOf(Int::class.javaPrimitiveType!!),
                beforeMethod = { _, args ->
                    val raw = args.getOrNull(0) as? Int
                    val speed = MMKVManager.getFloat(KEY_PLAY_SPEED, -1f)
                    if (speed > 0f) { val t = (speed * 100f).toInt(); LogUtils.logI(TAG, "setPlaySpeed 原=$raw → 强制=$t (倍速=${speed})"); args[0] = t }
                    else LogUtils.logI(TAG, "setPlaySpeed 原=$raw (MMKV 没设倍速，不改)")
                })
            LogUtils.logI(TAG, "hook speed 注册成功: ${target.className}#${target.methodName}")
        }.onFailure { e -> LogUtils.logE(TAG, "hook speed 失败: ${e.message}", e) }
    }

    private fun hookGetResolution(cl: ClassLoader) {
        val target = HookDescriptor.parse(MMKVManager.getString(KEY_RES_GET, "")) ?: run { LogUtils.logW(TAG, "res_get desc 解析失败"); return }
        val retTypeName = HookDescriptor.descriptorToClassName(target.returnDescriptor)
        if (retTypeName == null) { LogUtils.logW(TAG, "res_get 返回类型不是对象: ${target.returnDescriptor}"); return }
        runCatching {
            val resClass = Class.forName(retTypeName, true, cl)
            if (!resClass.isEnum) { LogUtils.logW(TAG, "返回类型不是枚举，放弃: $retTypeName"); return }
            val quality: Any? = pickTarget(resClass)
            if (quality == null) { LogUtils.logW(TAG, "没找到目标画质枚举，放弃 hook getResolution"); return }
            XposedManager.findAndHookMethod(className = target.className, classLoader = cl, methodName = target.methodName,
                parameterTypes = emptyArray(),
                afterMethod = { _, _, result ->
                    if (MMKVManager.getBoolean(KEY_HIGHEST_QUALITY, true)) { LogUtils.logI(TAG, "getResolution() 原=$result → 强制=$quality (开关=开)"); quality }
                    else { LogUtils.logI(TAG, "getResolution() 原=$result (开关=关，放行)"); result }
                })
            LogUtils.logI(TAG, "hook getResolution 注册成功: ${target.className}#${target.methodName} 目标=$quality")
        }.onFailure { e -> LogUtils.logE(TAG, "hook getResolution 失败: ${e.message}", e) }
    }

    private fun pickTarget(resClass: Class<*>): Any? {
        val all = resClass.enumConstants ?: run { LogUtils.logW(TAG, "pickTarget: enumConstants 为 null（类未初始化？）"); return null }
        val named = all.filter { (it as Enum<*>).name.isNotBlank() && it.name !in IGNORE_NAMES }
        for (want in WANT_QUALITY) {
            val hit = named.firstOrNull { val e = it as Enum<*>; e.name.trim().equals(want, true) || e.toString().trim().equals(want, true) }
            if (hit != null) return hit
        }
        LogUtils.logW(TAG, "pickTarget: WANT_QUALITY 未命中，候选=[${named.joinToString { "\"${(it as Enum<*>).name}\"" }}]")
        return named.maxByOrNull { (it as Enum<*>).ordinal }
    }
}