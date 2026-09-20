package com.android.hongguo.registry

import com.android.hongguo.probe.*
import com.android.hongguo.modules.*
import com.android.hongguo.core.*
import com.android.hongguo.menu.*
import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface

object HongguoModuleRegistry {

    private const val TAG = "HongguoModuleRegistry"
    private val moduleMap = LinkedHashMap<String, IHookModule>()

    init {
    registerModule(MenuModule())
   // registerModule(ProbeModule)
    registerModule(SpeedProbeModule)  
    registerModule(AdProbeModule) // ← 加这行
}

    private fun registerModule(module: IHookModule) {
        val name = module.getModuleName()
        if (moduleMap.containsKey(name)) {
            LogUtils.logW(TAG, "模块已存在 覆盖注册 $name")
        }
        moduleMap[name] = module
    }

    /**
     * 执行所有已注册模块的 Hook 逻辑
     */
    fun executeAllModules(param: XposedModuleInterface.PackageLoadedParam) {
        moduleMap.forEach { (name, module) ->
            runCatching {
                module.handleLoadPackage(param)
            }.onFailure { e ->
                LogUtils.logE(TAG, "执行模块 Hook 异常 $name err=${e.message}", e)
            }
        }
    }
}
