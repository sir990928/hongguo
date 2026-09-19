package com.android.hongguo.utils.manager

import io.github.libxposed.api.XposedModuleInterface

/**
 * Hook 模块统一接口
 */
interface IHookModule {

    /**
     * 模块唯一标识符
     */
    fun getModuleName(): String

    /**
     * 执行 Hook 加载逻辑
     *
     * @param param LibXposed PackageLoadedParam
     */
    fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam)
}
