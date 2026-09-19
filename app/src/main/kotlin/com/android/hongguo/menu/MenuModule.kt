package com.android.hongguo.menu

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class MenuModule : IHookModule {

    override fun getModuleName(): String = "DrawerMenuModule"

    override fun handleLoadPackage(param: PackageLoadedParam) {
        runCatching {
            val cl = param.defaultClassLoader
            val raw = MMKVManager.getString(KEY_SHARE, "")
            val target = HookDescriptor.parse(raw ?: "") ?: return

            val paramTypes: Array<Class<*>> = target.paramDescriptors
                .mapNotNull { HookDescriptor.descriptorToClass(it, cl) }
                .toTypedArray()

            XposedManager.findAndHookMethod(
                className = target.className,
                classLoader = cl,
                methodName = target.methodName,
                parameterTypes = paramTypes,
                afterMethod = { thisObj, _, result ->   // ← 改成三参
                    if (thisObj != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            inject(thisObj)
                        }, 200)
                    }
                    result                              // ← 原样返回，不改变原方法返回值
                }
            )
        }.onFailure { LogUtils.logE(TAG, "hook failed", it) }
    }

    /** 只负责把菜单行植进去 */
    private fun inject(su3m: Any) {
        val parent = su3m as? LinearLayout ?: return
        val d = parent.getChildAt(0) as? FrameLayout ?: return
        if (d.findViewWithTag<View>(CUSTOM_TAG) != null) return

        val row = MenuUIBuilder.buildRow(d.context) { ctx ->
            MenuActionHandler.onMenuClick(ctx)     // 点击 → 交给 ActionHandler
        } ?: return

        row.tag = CUSTOM_TAG
        d.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            MenuUIBuilder.dp(d.context, 56f),
            android.view.Gravity.BOTTOM
        ).apply {
            marginStart = MenuUIBuilder.dp(d.context, 12f)
            marginEnd = MenuUIBuilder.dp(d.context, 12f)
            bottomMargin = MenuUIBuilder.dp(d.context, 12f)
        })
    }

    companion object {
        private const val TAG = "DrawerMenuModule"
        private const val KEY_SHARE = "Share_key"
        private const val CUSTOM_TAG = "custom_drawer_menu"
    }
}