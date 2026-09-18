package com.android.hongguo.utils.manager

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

object XposedManager {

    private lateinit var xposedInterface: XposedInterface
    private lateinit var packageParam: XposedModuleInterface.PackageLoadedParam

    fun init(base: XposedInterface, param: XposedModuleInterface.PackageLoadedParam) {
        this.xposedInterface = base
        this.packageParam = param
    }

    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndHooker: Any
    ) {
        val hooker = parameterTypesAndHooker.last() as? XposedInterface.Hooker ?: return
        val paramTypes = parameterTypesAndHooker.dropLast(1).filterIsInstance<Class<*>>().toTypedArray()

        runCatching {
            val method: Method = clazz.getDeclaredMethod(methodName, *paramTypes)
            xposedInterface.hookMethod(method, hooker)
        }
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        vararg parameterTypesAndHooker: Any
    ) {
        runCatching {
            val clazz = classLoader.loadClass(className)
            findAndHookMethod(clazz, methodName, *parameterTypesAndHooker)
        }
    }

    fun getCurrentAweme(): Any? {
        return null
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        return runCatching {
            val argTypes = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
            val method = obj.javaClass.getMethod(methodName, *argTypes)
            method.invoke(obj, *args)
        }.getOrNull()
    }
}
