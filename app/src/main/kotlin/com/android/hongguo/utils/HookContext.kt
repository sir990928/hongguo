package com.android.hongguo.utils

import android.content.Context
import com.android.hongguo.utils.manager.XposedManager

object HookContext {
    private var hostContext: Context? = null

    /**
     * 捕获并缓存宿主 Application Context
     */
    fun setContext(context: Context?) {
        if (hostContext == null && context != null) {
            hostContext = context.applicationContext
            LogUtils.logI("HookContext", "宿主Context捕获成功 ${hostContext?.packageName}")
        }
    }

    /**
     * 获取宿主 Context：优先返回已缓存实例，若为空则尝试从 XposedManager 降级兜底获取
     */
    fun getContext(): Context? {
        return hostContext ?: XposedManager.getHostContext()?.also { setContext(it) }
    }
}
