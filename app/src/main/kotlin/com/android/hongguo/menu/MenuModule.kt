package com.android.hongguo.menu

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.android.hongguo.core.IHookModule
import com.android.hongguo.utils.HookContext
import com.android.hongguo.utils.LogUtils
import com.android.hongguo.utils.manager.DexKitManager
import com.android.hongguo.utils.manager.MMKVManager
import com.android.hongguo.utils.manager.XposedManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.Executors

class MenuModule : IHookModule {

    private val executor = Executors.newSingleThreadExecutor()

    override fun getModuleName(): String = "CustomMenu"

    override fun handleLoadPackage(lpparam: XposedModuleInterface.PackageLoadedParam) {
        if ("com.phoenix.read" != lpparam.packageName && "com.phoenix.read.oversea.gp" != lpparam.packageName) return

        initHongguoMenuHooks(lpparam.classLoader)
    }

    private fun initHongguoMenuHooks(classLoader: ClassLoader) {
        runCatching {
            val currentVersion = getAppVersionCode()
            val lastSearchedVersion = MMKVManager.getLong(KEY_LAST_SEARCHED_VERSION, -1L)

            // 版本变更或缓存缺失时，在后台线程触发 DexKit 动态查找类名
            if (currentVersion != lastSearchedVersion || !MMKVManager.contains(KEY_SHARE_DIALOG_CLASS)) {
                executor.execute {
                    val foundClass = DexKitManager.findShareDialogClass()
                    if (!foundClass.isNullOrEmpty()) {
                        MMKVManager.putString(KEY_SHARE_DIALOG_CLASS, foundClass)
                        MMKVManager.putLong(KEY_LAST_SEARCHED_VERSION, currentVersion)
                        if (MENU_MODULE_DEBUG_LOG) LogUtils.logI(TAG, "find share dialog class success $foundClass")
                    }
                }
            }

            val shareDialogClassName = MMKVManager.getString(KEY_SHARE_DIALOG_CLASS, "")
            if (!shareDialogClassName.isNullOrEmpty()) {
                hookShareDialog(shareDialogClassName, classLoader)
            } else {
                if (MENU_MODULE_DEBUG_LOG) LogUtils.logW(TAG, "empty share dialog class name")
            }
        }.onFailure { e ->
            LogUtils.logE(TAG, "init menu hooks failed err=${e.message}", e)
        }
    }

    private fun hookShareDialog(className: String, classLoader: ClassLoader) {
        XposedManager.findAndHookMethod(
            className,
            classLoader,
            "onCreate",
            Bundle::class.java,
            object : XposedInterface.Hooker {
                fun afterHookedMethod(param: XposedInterface.MethodHookParam) {
                    val targetDialog = param.thisObject ?: return
                    MenuActionHandler.setCurrentShareDialog(targetDialog)

                    MenuUIBuilder.clearCurrentMenu()

                    // 延迟等弹窗布局完成渲染后注入菜单视图
                    Handler(Looper.getMainLooper()).postDelayed({
                        injectMenuToDialog(targetDialog)
                    }, 150)
                }
            }
        )

        XposedManager.findAndHookMethod(
            className,
            classLoader,
            "onDestroy",
            object : XposedInterface.Hooker {
                fun beforeHookedMethod(param: XposedInterface.MethodHookParam) {
                    if (MENU_MODULE_DEBUG_LOG) LogUtils.logI(TAG, "share dialog destroyed clear state")
                    MenuUIBuilder.clearCurrentMenu()
                    MenuActionHandler.clearShareDialog()
                }
            }
        )
    }

        private fun injectMenuToDialog(dialog: Any) {
        runCatching {
            val window = XposedManager.callMethod(dialog, "getWindow") ?: return
            val decorView = XposedManager.callMethod(window, "getDecorView") as? ViewGroup ?: return

            // 防重复注入清理
            val existingMenu = decorView.findViewWithTag<View>(CUSTOM_MENU_TAG)
            if (existingMenu != null) {
                (existingMenu.parent as? ViewGroup)?.removeView(existingMenu)
            }

            // 获取真正的弹窗内容容器（通常是 android.R.id.content 或 DecorView 的第一个子 ViewGroup）
            val contentContainer = decorView.findViewById<ViewGroup>(android.R.id.content) 
                ?: (decorView.getChildAt(0) as? ViewGroup) 
                ?: decorView

            val buttonCount = 5
            val ourMenuContainer = MenuUIBuilder.createAdaptiveMenuContainer(contentContainer.context, buttonCount) ?: return
            ourMenuContainer.tag = CUSTOM_MENU_TAG

            // 动态加入到内容容器底部
            contentContainer.addView(ourMenuContainer)

            if (MENU_MODULE_DEBUG_LOG) LogUtils.logI(TAG, "custom menu attached to content container")
        }.onFailure { e ->
            LogUtils.logE(TAG, "inject menu failed err=${e.message}", e)
        }
    }


    private fun getAppVersionCode(): Long {
        return runCatching {
            val context = HookContext.getContext() ?: return -1L
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode
        }.getOrDefault(-1L)
    }

    companion object {
        private const val TAG = "MenuModule"
        private const val MENU_MODULE_DEBUG_LOG = false
        private const val KEY_SHARE_DIALOG_CLASS = "share_dialog_class_name"
        private const val KEY_LAST_SEARCHED_VERSION = "last_searched_version"
        private const val CUSTOM_MENU_TAG = "custom_menu_tag"
    }
}
