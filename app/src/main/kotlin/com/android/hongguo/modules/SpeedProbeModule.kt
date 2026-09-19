package com.android.hongguo.modules

import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface

/**
 * 独立模块：让注册器能激活播放器探针。
 */
object SpeedProbeModule : IHookModule {

    private const val TAG = "SpeedProbeModule"

    override fun getModuleName(): String = "SpeedProbeModule"

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            SpeedProbeTest.hook(param.defaultClassLoader)
            LogUtils.logI(TAG, "SpeedProbeModule hook done")
        }.onFailure { e ->
            LogUtils.logE(TAG, "SpeedProbeModule hook failed: ${e.message}", e)
        }
    }
}

object SpeedProbeTest {

    private const val TAG = "SpeedProbe"

    private const val KEY_SPEED = "Multiple_key"     // l35/w;->setPlaySpeed(I)V
    private const val KEY_QUALITY = "1080p_key"      // l35/w;->getResolution()Resolution
    private const val KEY_RES_GET = "1080p_key"      // 默认画质走这里
    private const val KEY_PLAY_SPEED = "play_speed"  // 用户设的倍速（float）

    /** 默认最高画质开关（与 MenuActionHandler 共用的 MMKV key） */
    private const val KEY_HIGHEST_QUALITY = "highest_quality"

    /**
     * 默认最高画质优先级：
     * 有 4k 就用 4k；没有 4k 依次退 1080p+ → 1080p → 720p。
     * 名字从 Resolution 枚举动态匹配，不硬编码类名。
     */
    private val WANT_QUALITY = listOf("4k", "1080p+", "1080p", "720p")

    /** 排序时要忽略的名字：auto / 空名等非画质项 */
    private val IGNORE_NAMES = setOf("auto")

    fun hook(cl: ClassLoader) {
        hookSpeed(cl)
        hookGetResolution(cl)
    }

    /** 倍速：beforeMethod 改 args[0] 强制成 MMKV 里的值 */
    private fun hookSpeed(cl: ClassLoader) {
        val target = HookDescriptor.parse(MMKVManager.getString(KEY_SPEED, "")) ?: run {
            LogUtils.logW(TAG, "speed desc 解析失败")
            return
        }

        runCatching {
            XposedManager.findAndHookMethod(
                className = target.className,
                classLoader = cl,
                methodName = target.methodName,
                parameterTypes = arrayOf(Int::class.javaPrimitiveType!!),
                beforeMethod = { _, args ->
                    val raw = args.getOrNull(0) as? Int
                    val speed = MMKVManager.getFloat(KEY_PLAY_SPEED, -1f)
                    if (speed > 0f) {
                        val t = (speed * 100f).toInt()
                        LogUtils.logI(TAG, "setPlaySpeed 原=$raw → 强制=$t (倍速=${speed})")
                        args[0] = t
                    } else {
                        LogUtils.logI(TAG, "setPlaySpeed 原=$raw (MMKV 没设倍速，不改)")
                    }
                }
            )
            LogUtils.logI(TAG, "hook speed 注册成功: ${target.className}#${target.methodName}")
        }.onFailure { e ->
            LogUtils.logE(TAG, "hook speed 失败: ${e.message}", e)
        }
    }

    /**
     * getResolution()：无参，返回当前画质。
     * afterMethod 改返回值 → 开关打开时默认播放就是最高画质。
     * 返回类型从 HookDescriptor 解析，不硬编码类名。
     */
    private fun hookGetResolution(cl: ClassLoader) {
        val target = HookDescriptor.parse(MMKVManager.getString(KEY_RES_GET, "")) ?: run {
            LogUtils.logW(TAG, "res_get desc 解析失败")
            return
        }
        val retTypeName = HookDescriptor.descriptorToClassName(target.returnDescriptor)
        if (retTypeName == null) {
            LogUtils.logW(TAG, "res_get 返回类型不是对象: ${target.returnDescriptor}")
            return
        }

        runCatching {
            // ★ true：强制初始化枚举类，否则 enumConstants 可能为 null
            val resClass = Class.forName(retTypeName, true, cl)
            if (!resClass.isEnum) {
                LogUtils.logW(TAG, "返回类型不是枚举，放弃: $retTypeName")
                return
            }
            val quality: Any? = pickTarget(resClass)
            if (quality == null) {
                LogUtils.logW(TAG, "没找到目标画质枚举，放弃 hook getResolution")
                return
            }

            XposedManager.findAndHookMethod(
                className = target.className,
                classLoader = cl,
                methodName = target.methodName,
                parameterTypes = emptyArray(),               // ★ 无参
                afterMethod = { _, _, result ->
                    val on = MMKVManager.getBoolean(KEY_HIGHEST_QUALITY, true)
                    if (on) {
                        LogUtils.logI(TAG, "getResolution() 原=$result → 强制=$quality (开关=开)")
                        quality
                    } else {
                        LogUtils.logI(TAG, "getResolution() 原=$result (开关=关，放行)")
                        result
                    }
                }
            )
            LogUtils.logI(TAG, "hook getResolution 注册成功: ${target.className}#${target.methodName} 目标=$quality")
        }.onFailure { e ->
            LogUtils.logE(TAG, "hook getResolution 失败: ${e.message}", e)
        }
    }

    /**
     * 选目标画质：
     * 1) WANT_QUALITY 非空 → 按里面的名字优先级找（有 4k 就 4k）
     * 2) 全找不到 → 打印候选 + 退到 ordinal 最大
     */
    private fun pickTarget(resClass: Class<*>): Any? {
        val all = resClass.enumConstants
        if (all == null) {
            LogUtils.logW(TAG, "pickTarget: enumConstants 为 null（类未初始化？）")
            return null
        }
        val named = all.filter { (it as Enum<*>).name.isNotBlank() && it.name !in IGNORE_NAMES }

        for (want in WANT_QUALITY) {
            val hit = named.firstOrNull {
                val e = it as Enum<*>
                e.name.trim().equals(want, true) || e.toString().trim().equals(want, true)
            }
            if (hit != null) return hit
        }

        LogUtils.logW(TAG, "pickTarget: WANT_QUALITY 未命中，候选=[${
            named.joinToString { "\"${(it as Enum<*>).name}\"" }
        }]")
        return named.maxByOrNull { (it as Enum<*>).ordinal }
    }
}