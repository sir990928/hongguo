package com.android.hongguo.core

import android.app.Application
import android.content.Context
import com.android.hongguo.registry.HongguoModuleRegistry
import com.android.hongguo.utils.HookContext
import com.android.hongguo.utils.LogUtils
import com.android.hongguo.utils.manager.DexKitManager
import com.android.hongguo.utils.manager.MMKVManager
import com.android.hongguo.utils.manager.XposedManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * 中央调度器 - 仅红果，纯 LibXposed API
 */
object HookDispatcher {

    private const val TAG = "HookDispatcher"
    private const val DISPATCHER_DEBUG_LOG = false

    fun dispatch(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        if (packageName != "com.phoenix.read" && packageName != "com.phoenix.read.oversea.gp") {
            if (DISPATCHER_DEBUG_LOG) LogUtils.logI(TAG, "非目标应用跳过 $packageName")
            return
        }

        if (DISPATCHER_DEBUG_LOG) LogUtils.logI(TAG, "加载目标包 $packageName")

        param.appInfo?.sourceDir?.let { apkPath ->
            DexKitManager.init(apkPath)
        }

        XposedManager.init(param, packageName)

        XposedManager.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XposedInterface.BeforeAfterHookCallback {
                override fun afterHookedMethod(hookParam: XposedInterface.HookParam) {
                    val context = hookParam.thisObject as? Context ?: return

                    HookContext.init(context)
                    MMKVManager.init(context)

                    if (DISPATCHER_DEBUG_LOG) {
                        LogUtils.logI(TAG, "全局核心服务初始化完成")
                    }

                    loadHongguoHooks(param)
                }
            }
        )
    }

    private fun loadHongguoHooks(param: XposedModuleInterface.PackageLoadedParam) {
        if (DISPATCHER_DEBUG_LOG) LogUtils.logI(TAG, "执行红果模块注册")
        HongguoModuleRegistry.executeAllModules(param)
    }
}
