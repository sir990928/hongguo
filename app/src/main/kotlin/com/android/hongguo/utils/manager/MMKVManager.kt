package com.android.hongguo.utils.manager

import android.content.Context
import com.tencent.mmkv.MMKV
import com.android.hongguo.utils.LogUtils

object MMKVManager {
    private var mmkv: MMKV? = null
    private var isI = false
    private const val LOG = false
    private const val DEX_CACHE_ID = "hongguo_dex_cache"

    fun getRootDir(): String = "/data/local/tmp"

    fun init(context: Context?) {
        if (isI || context == null) return
        runCatching {
            val rootDir = MMKV.initialize(context.applicationContext)
            mmkv = MMKV.mmkvWithID(DEX_CACHE_ID, MMKV.MULTI_PROCESS_MODE); isI = true
            if (LOG) LogUtils.logSuccess("✅ MMKV初始化成功 rootDir:$rootDir")
        }.onFailure { if (LOG) LogUtils.logE("❌ MMKV初始化异常", it as Exception); isI = false }
    }

    fun isAvailable(): Boolean = isI && mmkv != null

    // ========== 读写 API (极致单行扁平) ==========
    fun putString(k: String, v: String) { if (isAvailable()) mmkv?.encode(k, v) }
    fun getString(k: String, def: String = ""): String = if (isAvailable()) mmkv?.decodeString(k, def) ?: def else def
    fun putInt(k: String, v: Int) { if (isAvailable()) mmkv?.encode(k, v) }
    fun getInt(k: String, def: Int = 0): Int = if (isAvailable()) mmkv?.decodeInt(k, def) ?: def else def
    fun putLong(k: String, v: Long) { if (isAvailable()) mmkv?.encode(k, v) }
    fun getLong(k: String, def: Long = 0L): Long = if (isAvailable()) mmkv?.decodeLong(k, def) ?: def else def
    fun putBoolean(k: String, v: Boolean) { if (isAvailable()) mmkv?.encode(k, v) }
    fun getBoolean(k: String, def: Boolean = false): Boolean = if (isAvailable()) mmkv?.decodeBool(k, def) ?: def else def
    fun putFloat(k: String, v: Float) { if (isAvailable()) mmkv?.encode(k, v) }
    fun getFloat(k: String, def: Float = 0f): Float = if (isAvailable()) mmkv?.decodeFloat(k, def) ?: def else def

    fun remove(k: String) { if (isAvailable()) mmkv?.removeValueForKey(k) }
    fun contains(k: String): Boolean = isAvailable() && mmkv?.containsKey(k) == true
    fun getMMKV(): MMKV? = mmkv

    fun clearDexCache() { if (isAvailable()) { mmkv?.clearAll(); isI = false; if (LOG) LogUtils.logSuccess("✅ DexKit缓存已清空") } }
}
