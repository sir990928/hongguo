package com.android.hongguo.utils.manager

import android.app.Application
import android.content.Context
import com.android.hongguo.utils.LogUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import io.tehc.dexkit.DexKit
import io.tehc.dexkit.entity.MethodResult
import java.lang.reflect.Method

object XposedManager {
    private var mParam: XposedModuleInterface.PackageLoadedParam? = null
    private var mCurrentPackage: String = ""
    private var sModuleContext: Context? = null
    private const val MODULE_PACKAGE_NAME = "com.android.hongguo"
    var GLOBAL_DEBUG_LOG = true

    fun init(param: XposedModuleInterface.PackageLoadedParam, currentPackage: String) {
        mParam = param
        mCurrentPackage = currentPackage
        DexKit.init(param.classLoader)
        if (!DexKit.hasCache()) {
            logInfo("XposedManager", "$mCurrentPackage DexKit无缓存 执行扫描")
            DexKit.startScan()
        } else logInfo("XposedManager", "$mCurrentPackage 复用已有DexKit缓存")
        hookObtainModuleContext()
    }

    /** 动态获取红果/宿主App的全局Application Context */
    fun getHostContext(): Context? {
        return runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val method = atClass.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Context
        }.getOrNull()
    }

    // ===================== 日志输出（碎片化去方括号） =====================
    fun logDebug(tag: String, msg: String) { if (GLOBAL_DEBUG_LOG) LogUtils.logI("$tag: $msg") }
    fun logInfo(tag: String, msg: String) = LogUtils.logI("$tag: $msg")
    fun logSuccess(tag: String, msg: String) = LogUtils.logSuccess("$tag: $msg")
    fun logError(tag: String, msg: String) = LogUtils.logE("$tag: $msg")
    fun logError(tag: String, msg: String, t: Throwable) = LogUtils.logE("$tag: $msg", t)
    fun getModuleContext(): Context? = sModuleContext

    private fun hookObtainModuleContext() {
        val hooker = mParam?.hooker ?: return
        runCatching {
            val onCreate: Method = Application::class.java.getDeclaredMethod("onCreate")
            hooker.hook(onCreate, object : XposedInterface.AfterHookCallback {
                override fun afterHookedMethod(param: XposedInterface.HookParam) {
                    val appCtx = param.thisObject as? Context ?: return
                    if (sModuleContext == null) {
                        runCatching {
                            sModuleContext = appCtx.createPackageContext(MODULE_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
                            logSuccess("XposedManager", "成功获取模块上下文 $MODULE_PACKAGE_NAME")
                        }.onFailure { logError("XposedManager", "获取模块上下文失败", it) }
                    }
                }
            })
        }.onFailure { logError("XposedManager", "Hook Application onCreate 失败", it) }
    }

    fun findClass(className: String): Class<*>? {
        val cl = mParam?.classLoader ?: return null
        return runCatching { Class.forName(className, false, cl) }.getOrElse {
            logDebug("XposedManager", "类未找到 $className"); null
        }
    }

    // ===================== 强化版原生反射（支持向上递归父类） =====================
    fun getObjectField(obj: Any?, fieldName: String): Any? {
        obj ?: return null
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            runCatching {
                val f = clazz!!.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(obj)
            }
            clazz = clazz.superclass
        }
        logError("XposedManager", "getObjectField 失败 ${obj.javaClass.name}.$fieldName")
        return null
    }

    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        obj ?: return null
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val methods = clazz.declaredMethods
            for (m in methods) {
                if (m.name == methodName && m.parameterTypes.size == args.size) {
                    runCatching {
                        m.isAccessible = true
                        return m.invoke(obj, *args)
                    }
                }
            }
            clazz = clazz.superclass
        }
        logError("XposedManager", "callMethod 失败 ${obj.javaClass.name}.$methodName")
        return null
    }

    // ===================== DexKit & Hook 逻辑封装 =====================
    fun findMethodsByName(methodName: String): List<MethodResult> =
        runCatching { DexKit.findMethods { it.name == methodName } }.getOrElse { logError("XposedManager", "DexKit查询方法 $methodName 异常", it); emptyList() }

    fun findMethodsByClassAndMethod(className: String, methodName: String): List<MethodResult> =
        runCatching { DexKit.findMethods { mr -> mr.className == className && mr.name == methodName } }.getOrElse { logError("XposedManager", "DexKit查询 $className#$methodName 异常", it); emptyList() }

    fun hookByMethodName(methodName: String, before: ((XposedInterface.HookParam) -> Unit)? = null, after: ((XposedInterface.HookParam) -> Unit)? = null) {
        val hooker = mParam?.hooker ?: return
        val list = findMethodsByName(methodName)
        if (list.isEmpty()) { logError("XposedManager", "$mCurrentPackage 未找到方法 $methodName"); return }
        list.forEach { mr ->
            mr.method?.let { m ->
                hooker.hook(m, object : XposedInterface.BeforeAfterHookCallback {
                    override fun beforeHookedMethod(p: XposedInterface.HookParam) { before?.invoke(p) }
                    override fun afterHookedMethod(p: XposedInterface.HookParam) { after?.invoke(p) }
                })
            }
        }
    }

    fun hookByClassAndMethod(className: String, methodName: String, before: ((XposedInterface.HookParam) -> Unit)? = null, after: ((XposedInterface.HookParam) -> Unit)? = null) {
        val hooker = mParam?.hooker ?: return
        val list = findMethodsByClassAndMethod(className, methodName)
        if (list.isEmpty()) { logError("XposedManager", "$mCurrentPackage 找不到 $className#$methodName"); return }
        list.forEach { mr ->
            mr.method?.let { m ->
                hooker.hook(m, object : XposedInterface.BeforeAfterHookCallback {
                    override fun beforeHookedMethod(p: XposedInterface.HookParam) { before?.invoke(p) }
                    override fun afterHookedMethod(p: XposedInterface.HookParam) { after?.invoke(p) }
                })
            }
        }
    }
}
