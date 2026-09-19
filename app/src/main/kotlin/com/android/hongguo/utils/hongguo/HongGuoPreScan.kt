package com.android.hongguo.utils.hongguo

import com.android.hongguo.utils.manager.*
import org.luckypray.dexkit.DexKitBridge

object HongGuoPreScan {

    /** 扫描失败时不打印警告的 key（可选） */
    val silentNullKeys = setOf<String>()

    /** 全部 key —— clearAllCache 用 */
    val allKeys = listOf(
        // ## 界面 / 播放器
        "Share_key", "1080p_key", "Multiple_key",
        // ## 去广告
        "Ad_key", "AdIconLayer_key", "AdEndLayer_key",
        // ## VIP
        "Vip_key"
    )

    fun doScan(bridge: DexKitBridge) {
        LogUtils.logI("PreScan", "doScan entered")
        val t0 = System.currentTimeMillis()

        // ## 界面 / 播放器 ==========================================

        // ## Share_key —— 分享面板抽屉相关方法
        save("Share_key", DexKitManager.fMQuery(bridge, strings = listOf("endTouchMoveAnimation isOpen:", " fromTouchMove:", " marginStart:"))?.substringBefore("->")?.removePrefix("L")?.removeSuffix(";")?.replace('/', '.')?.let { DexKitManager.fMQuery(bridge = bridge, classPrefix = it, methodCalls = listOf("onDrawerOpened", "setAlpha", "setVisibility")) })

        // ## 1080p_key —— 画质 / 分辨率相关（getResolution 等）
        save("1080p_key", DexKitManager.fMQuery(bridge, strings = listOf("updateResolution resolution:", "1080P", "definition_cache", "definition_cache_"))?.substringBefore("->")?.removePrefix("L")?.removeSuffix(";")?.replace('/', '.')?.let { cls -> DexKitManager.fMQuery(bridge = bridge, classPrefix = cls, paramTypes = emptyList(), methodCalls = listOf("getSupportResolutions", "getVideoModel"), filter = { rt -> rt.contains("Resolution") }) })

        // ## Multiple_key —— 倍速设置（setPlaySpeed）
        save("Multiple_key", DexKitManager.fMQuery(bridge, strings = listOf("hongguo_play_rate", "setPlaySpeed speed=%d")))

        // ## 去广告 ================================================

        // ## Ad_key —— 暂停广告入口（enablePauseAd）
        save("Ad_key", DexKitManager.fMQuery(bridge, methodName = "enablePauseAd", methodCalls = listOf("getClass")))

        // ## AdEndLayer_key —— 片尾广告层（AdVideoEndLayer#handleVideoEvent）
        save("AdEndLayer_key", DexKitManager.fMQuery(bridge, strings = listOf("show ad video end view ,layer parent = %s, visibility=%s", "Video_AD")))

        // ## AdIconLayer_key —— 广告图标层（CommunityVideoTitleBarLayer#handleVideoEvent）
        save("AdIconLayer_key", DexKitManager.fMQuery(bridge, methodName = "handleVideoEvent", methodCalls = listOf("setVisibility")))

        // ## VIP ===================================================

        // ## Vip_key —— PrivilegeManager 的会员方法
        // ##   布尔: isVip / isAnyVip / canReadShortStory /
        // ##         hasVipShortSeriesPrivilege / hasNoAdFollAllScene / hasNoAdForShortSeries
        // ##   模型: getVipInfo
        // ##   列表: getAllVipInfo
        save("Vip_key", listOf("isVip", "isAnyVip", "canReadShortStory", "hasVipShortSeriesPrivilege", "hasNoAdFollAllScene", "hasNoAdForShortSeries", "getVipInfo", "getAllVipInfo").mapNotNull { n -> DexKitManager.fMQueryAll(bridge = bridge, classPrefix = "com.dragon.read.component.biz.impl.privilege.PrivilegeManager", methodName = n).firstOrNull() }.joinToString(","))

        LogUtils.logI("PreScan", "doScan cost = ${System.currentTimeMillis() - t0}ms")
    }

    /** 保存扫描结果到 MMKV；空则移除 */
    private fun save(key: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            LogUtils.logI("PreScan", "save $key = $value")
            MMKVManager.putString(key, value)
        } else {
            if (key !in silentNullKeys) LogUtils.logW("PreScan", "failed $key not found")
            MMKVManager.remove(key)
        }
    }

    /** 清空全部缓存 key */
    fun clearAllCache() = allKeys.forEach { MMKVManager.remove(it) }
}