package com.android.hongguo.utils.hongguo

import com.android.hongguo.utils.LogUtils
import com.android.hongguo.utils.manager.DexKitManager
import com.android.hongguo.utils.manager.MMKVManager
import org.luckypray.dexkit.DexKitBridge

object HongGuoPreScan {
    private fun sF() = "F" + "A" + "I" + "L" + "E" + "D"

    val silentNullKeys = setOf<String>()

    val allKeys = listOf(
        "Share_key"
    )

    fun doScan(bridge: DexKitBridge) {
        save("Share_key", DexKitManager.fMQuery(bridge, methodName = "onCreate", paramTypes = listOf("android.os.Bundle"), methodCalls = listOf("getActivity", "resetDefaultDimCount")))
    }

    private fun save(key: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            LogUtils.logI("HongGuoScan: save [$key] = $value")
            MMKVManager.putString(key, value)
        } else {
            if (key !in silentNullKeys) LogUtils.logW("HongGuoScan: [${sF()}] $key 未找到")
            MMKVManager.remove(key)
        }
    }

    fun clearAllCache() = allKeys.forEach { MMKVManager.remove(it) }
}
