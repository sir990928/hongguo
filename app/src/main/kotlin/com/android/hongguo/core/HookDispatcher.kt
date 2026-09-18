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
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 中央调度器 - 纯 LibXposed API
 */
class HookDispatcher(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) : XposedModule(base, param) {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        if (packageName != "com.phoenix.read" && packageName != "com.phoenix.read.oversea.gp") {
            if (DISPATCHER_DEBUG_LOG) LogUtils.logI(TAG, "skip target pkg $packageName")
            return
        }

        if (DISPATCHER_DEBUG_LOG) LogUtils.logI(TAG, "target pkg loaded $packageName")

        param.appInfo.sourceDir.let { apkPath ->
            DexKitManager.init(apkPath)
        }

        XposedManager.init(base, param)

        XposedManager.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XposedInterface.Hooker {
                fun afterHookedMethod(hookParam: XposedInterface.MethodHookParam) {
                    val context = hookParam.thisObject as? Context ?: return

                    HookContext.init(context)
                    MMKVManager.init(context)

                    if (DISPATCHER_DEBUG_LOG) {
                        LogUtils.logI(TAG, "core service initialized")
                    }

                    loadHongguoHooks(param)
                }
            }
        )
    }

    private fun loadHongguoHooks(param: XposedModuleInterface.PackageLoadedParam) {
        if (DISPATCHER_DEBUG_LOG) LogUtils.logI(TAG, "executing module registry")
        HongguoModuleRegistry.executeAllModules(param)
    }

    companion object {
        private const val TAG = "HookDispatcher"
        private const val DISPATCHER_DEBUG_LOG = false
    }
}
