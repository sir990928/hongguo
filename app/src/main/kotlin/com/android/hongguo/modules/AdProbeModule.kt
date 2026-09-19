package com.android.hongguo.modules

import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface

object AdProbeModule : IHookModule {
    private const val TAG = "AdProbe"
    override fun getModuleName() = "AdProbeModule"
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            AdProbe.hook(param.defaultClassLoader)
            LogUtils.logI(TAG, "AdProbeModule hook done")
        }.onFailure { e -> LogUtils.logE(TAG, "AdProbeModule hook failed: ${e.message}", e) }
    }
}

object AdProbe {

    private const val TAG = "AdProbe"
    private const val KEY_AD_BLOCK   = "ad_block"
    private const val KEY_VIP_UNLOCK = "vip_unlock"
    private const val KEY_AD         = "Ad_key"
    private const val KEY_AD_END     = "AdEndLayer_key"
    private const val KEY_AD_ICON    = "AdIconLayer_key"

    /** VIP 相关的 4 个 key —— 全从 MMKV 读，不硬编码类/方法 */
    private val VIP_KEYS = listOf("Vip_key", "NsUser_key", "NsComic_key", "NsVip_key")

    private fun adBlockOn(): Boolean = MMKVManager.getBoolean(KEY_AD_BLOCK, true)
    private fun vipOn(): Boolean     = MMKVManager.getBoolean(KEY_VIP_UNLOCK, false)

    fun hook(cl: ClassLoader) {
        hookByKey(cl, KEY_AD,      ReturnMode.FALSE)
        hookByKey(cl, KEY_AD_END,  ReturnMode.FALSE)
        hookByKey(cl, KEY_AD_ICON, ReturnMode.FALSE)
        VIP_KEYS.forEach { hookVipFromKey(cl, it) }
    }

    // ---------- VIP 解锁（全 key 驱动，零类名/方法名硬编码） ----------

    /**
     * 读指定 key（逗号拼接 descriptor），按返回类型分派：
     *   )Z                                  → true
     *   )Lcom/.../VipInfoModel;             → 假模型
     *   )Ljava/util/List;                   → 假列表
     */
    private fun hookVipFromKey(cl: ClassLoader, key: String) {
        val raw = MMKVManager.getString(key, "")
        if (raw.isEmpty()) { LogUtils.logW(TAG, "$key 为空"); return }
        raw.split(",").forEach { desc ->
            val cls = desc.substringAfter("L").substringBefore(";").replace("/", ".")
            val m   = desc.substringAfter("->").substringBefore("(")
            runCatching {
                when {
                    desc.endsWith(")Z") -> XposedManager.findAndHookMethod(cls, cl, m,
                        afterMethod = { _, _, r -> if (vipOn()) true else r })

                    desc.contains(")Lcom/dragon/read/user/model/VipInfoModel;") -> {
                        val sub = Class.forName("com.dragon.read.rpc.model.VipCommonSubType", false, cl)
                        val fake = buildFakeVip(cl, sub.enumConstants?.firstOrNull())
                        XposedManager.findAndHookMethod(cls, cl, m,
                            afterMethod = { _, _, r -> if (vipOn()) fake else r })
                    }

                    desc.endsWith(")Ljava/util/List;") -> {
                        val fake = buildFakeVipList(cl)
                        XposedManager.findAndHookMethod(cls, cl, m,
                            afterMethod = { _, _, r -> if (vipOn()) fake else r })
                    }

                    else -> return@forEach
                }
                LogUtils.logI(TAG, "✓ $key $cls#$m")
            }.onFailure { e -> LogUtils.logE(TAG, "✗ $key $cls#$m: ${e.message}", e) }
        }
    }

    private fun buildFakeVipList(cl: ClassLoader): List<Any> {
        val sub = Class.forName("com.dragon.read.rpc.model.VipCommonSubType", false, cl)
        return (sub.enumConstants ?: emptyArray()).mapNotNull {
            runCatching { buildFakeVip(cl, it) }.getOrNull()
        }
    }

    private fun buildFakeVip(cl: ClassLoader, subType: Any?): Any {
        val model = Class.forName("com.dragon.read.user.model.VipInfoModel", false, cl)
        val sub   = Class.forName("com.dragon.read.rpc.model.VipCommonSubType", false, cl)
        val ctor  = model.getConstructor(
            String::class.java, String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, sub
        )
        return ctor.newInstance("2099-12-31 23:59:59", "1", "99999999", true, false, 0, true, subType)
    }

    // ---------- 去广告 ----------

    enum class ReturnMode { FALSE, NULL }

    private fun hookByKey(cl: ClassLoader, key: String, mode: ReturnMode) {
        val desc = MMKVManager.getString(key, "")
        if (desc.isNullOrEmpty()) { LogUtils.logW(TAG, "$key 为空，跳过"); return }
        val target = HookDescriptor.parse(desc)
        if (target == null) { LogUtils.logW(TAG, "$key 解析失败: $desc"); return }
        runCatching {
            XposedManager.findAndHookMethod(
                className = target.className, classLoader = cl, methodName = target.methodName,
                afterMethod = { _, _, result ->
                    if (adBlockOn()) {
                        LogUtils.logI(TAG, "${target.className}#${target.methodName} → ${mode.name} (原=$result)")
                        when (mode) { ReturnMode.FALSE -> false; ReturnMode.NULL -> null }
                    } else result
                }
            )
            LogUtils.logI(TAG, "✓ $key → ${target.className}#${target.methodName}")
        }.onFailure { e -> LogUtils.logE(TAG, "✗ $key hook 失败: ${e.message}", e) }
    }
}