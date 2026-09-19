package com.android.hongguo.utils.manager

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object XposedManager {
    private lateinit var module: XposedModule

    fun init(xposedModule: XposedModule) {
        module = xposedModule
    }

    /**
     * 底层 Hook 接口（支持传入原始 Hooker）
     */
    fun hookMethod(method: Method, hooker: XposedInterface.Hooker) {
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept(hooker)
        }.onFailure { e ->
            LogUtils.logE("XposedManager", "hookMethod 失败: ${method.declaringClass.name}#${method.name}", e)
        }
    }

    /**
     * 封装 Lambda 形式 hookMethod（afterMethod 可修改返回值）
     */
    fun hookMethod(
        method: Method,
        beforeMethod: ((thisObject: Any?, args: Array<Any?>) -> Unit)? = null,
        afterMethod: ((thisObject: Any?, args: Array<Any?>, result: Any?) -> Any?)? = null
    ) {
        hookMethod(method, object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val instance: Any? = chain.thisObject
                val argsArray: Array<Any?> = chain.args.toTypedArray()

                beforeMethod?.invoke(instance, argsArray)
                val result = chain.proceed()
                return afterMethod?.invoke(instance, argsArray, result) ?: result
            }
        })
    }

    /**
     * 查找并 Hook 方法（无需 parameterTypes，自动匹配指定名称的所有方法）
     */
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        beforeMethod: ((thisObject: Any?, args: Array<Any?>) -> Unit)? = null,
        afterMethod: ((thisObject: Any?, args: Array<Any?>, result: Any?) -> Any?)? = null
    ) {
        runCatching {
            val clazz = classLoader.loadClass(className)
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            if (methods.isEmpty()) {
                LogUtils.logE("XposedManager", "findAndHookMethod 未找到方法 [$className#$methodName]")
                return
            }
            methods.forEach { method ->
                hookMethod(method, beforeMethod, afterMethod)
            }
        }.onFailure { e ->
            LogUtils.logE("XposedManager", "findAndHookMethod 失败 [$className#$methodName]", e)
        }
    }

    /**
     * 查找并 Hook 方法（显式声明参数类型 + Lambda 简化）
     */
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        beforeMethod: ((thisObject: Any?, args: Array<Any?>) -> Unit)? = null,
        afterMethod: ((thisObject: Any?, args: Array<Any?>, result: Any?) -> Any?)? = null
    ) {
        runCatching {
            val clazz = classLoader.loadClass(className)
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
            hookMethod(method, beforeMethod, afterMethod)
        }.onFailure { e ->
            LogUtils.logE("XposedManager", "findAndHookMethod 失败 [$className#$methodName]", e)
        }
    }

    /**
     * 健壮的反射调用：支持向上递归查找父类，以及模糊匹配参数类型
     */
    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        return runCatching {
            var clazz: Class<*>? = obj.javaClass
            while (clazz != null && clazz != Any::class.java) {
                for (m in clazz.declaredMethods) {
                    if (m.name == methodName && isArgsCompatible(m.parameterTypes, args)) {
                        m.isAccessible = true
                        return m.invoke(obj, *args)
                    }
                }
                clazz = clazz.superclass
            }
            error("未找到匹配的方法: $methodName")
        }.onFailure { e ->
            LogUtils.logE("XposedManager", "callMethod 执行失败: $methodName", e)
        }.getOrNull()
    }

    private fun isArgsCompatible(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        if (paramTypes.size != args.size) return false
        for (i in paramTypes.indices) {
            val arg = args[i] ?: continue
            val paramType = paramTypes[i]
            val wrappedParamType = when (paramType) {
                Int::class.javaPrimitiveType -> Int::class.javaObjectType
                Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
                Long::class.javaPrimitiveType -> Long::class.javaObjectType
                Float::class.javaPrimitiveType -> Float::class.javaObjectType
                Double::class.javaPrimitiveType -> Double::class.javaObjectType
                Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
                Char::class.javaPrimitiveType -> Char::class.javaObjectType
                Short::class.javaPrimitiveType -> Short::class.javaObjectType
                else -> paramType
            }
            if (!wrappedParamType.isAssignableFrom(arg.javaClass)) {
                return false
            }
        }
        return true
    }
}