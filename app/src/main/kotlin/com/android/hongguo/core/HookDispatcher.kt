package com.android.hongguo.core

import android.app.Application
import android.content.Context
import android.os.Build
import com.android.hongguo.utils.hongguo.*
import com.android.hongguo.registry.*
import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object HookDispatcher {
    private lateinit var xposedModule: XposedModule
    private val inited = AtomicBoolean(false)

    fun init(module: XposedModule) {
        xposedModule = module
        XposedManager.init(module)
        LogUtils.logI("HookDispatcher", "XposedManager初始化完成")
    }

    fun dispatch(param: PackageLoadedParam) {
        val pkgName = param.packageName
        if ("com.phoenix.read" == pkgName || "com.phoenix.read.oversea.gp" == pkgName) {
            LogUtils.init("HongGuo", true, pkgName, pkgName)
            LogUtils.logI("Dispatcher", "=== 成功匹配目标应用 $pkgName，准备 Hook ===")

            hookInstrumentation(param)
        }
    }

    private fun getProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = Application.getProcessName()
            if (!processName.isNullOrEmpty()) return processName
        }
        return runCatching {
            File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
        }.getOrDefault(context.packageName)
    }

    private fun isMainProcess(context: Context): Boolean {
        val currentProcess = getProcessName(context)
        val isMain = currentProcess == context.packageName
        LogUtils.logI("Dispatcher", "进程检测: $currentProcess (主进程: $isMain)")
        return isMain
    }

    private fun hookInstrumentation(param: PackageLoadedParam) {
        runCatching {
            val instrumentationClass = Class.forName("android.app.Instrumentation")
            val callMethod = instrumentationClass.getDeclaredMethod(
                "callApplicationOnCreate",
                Application::class.java
            )
            callMethod.isAccessible = true

            XposedManager.hookMethod(callMethod, object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val app = chain.args.getOrNull(0) as? Application
                    
                    if (app != null) {
                        val context = app.applicationContext ?: app

                        if (isMainProcess(context)) {
                            if (inited.compareAndSet(false, true)) {
                                HookContext.setContext(context)
                                LogUtils.logI("Dispatcher", ">>> 成功抓取 Application Context: ${context.packageName}")

                                MMKVManager.init(context)
                                LogUtils.logI("Dispatcher", "MMKV初始化完成")

                                val rawPkgName = context.packageName
                                val apkPath = runCatching {
                                    context.packageManager.getApplicationInfo(rawPkgName, 0).sourceDir
                                }.getOrDefault(context.packageCodePath)

                                LogUtils.logI("Dispatcher", "apkPath = $apkPath")

                                DexKitManager.init(
                                    apkPath = apkPath,
                                    pkgName = rawPkgName,
                                    scanBlock = { bridge -> HongGuoPreScan.doScan(bridge) },
                                    onCompleted = {
                                        LogUtils.logI("Dispatcher", "DexKit初始化完成，开始执行业务Hook")
                                        HongguoModuleRegistry.executeAllModules(param)
                                    }
                                )
                            }
                        }
                    }

                    return chain.proceed()
                }
            })
            LogUtils.logI("Dispatcher", "Hook Instrumentation#callApplicationOnCreate 注册成功")
        }.onFailure { e ->
            LogUtils.logE("Dispatcher", "Hook Instrumentation 失败", e)
        }
    }
}
