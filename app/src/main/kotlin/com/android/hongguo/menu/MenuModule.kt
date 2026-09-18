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
        if ("com.phoenix.read" != lpparam.packageName) return

        initPureDexKitHooks(lpparam.classLoader)
    }

    private fun initPureDexKitHooks(classLoader: ClassLoader) {
        runCatching {
            val currentVersion = getAppVersionCode()
            val lastSearchedVersion = MMKVManager.getLong(KEY_LAST_SEARCHED_VERSION, -1L)

            if (currentVersion != lastSearchedVersion || !MMKVManager.contains(KEY_SHARE_DIALOG_CLASS)) {
                executor.execute {
                    val foundClass = DexKitManager.findShareDialogClass()
                    if (!foundClass.isNullOrEmpty()) {
                        MMKVManager.putString(KEY_SHARE_DIALOG_CLASS, foundClass)
                        MMKVManager.putLong(KEY_LAST_SEARCHED_VERSION, currentVersion)
                        if (MENU_MODULE_DEBUG_LOG) LogUtils.logI(TAG, "搜寻分享弹窗类名成功 $foundClass")
                    }
                }
            }

            val shareDialogClassName = MMKVManager.getString(KEY_SHARE_DIALOG_CLASS, null)
            if (!shareDialogClassName.isNullOrEmpty()) {
                hookShareDialog(shareDialogClassName, classLoader)
            } else {
                if (MENU_MODULE_DEBUG_LOG) LogUtils.logW(TAG, "未找到有效的分享弹窗类名")
            }
        }.onFailure { e ->
            LogUtils.logE(TAG, "初始化 DexKit 菜单 Hook 失败 err=${e.message}", e)
        }
    }

    private fun hookShareDialog(className: String, classLoader: ClassLoader) {
        XposedManager.findAndHookMethod(
            className,
            classLoader,
            "onCreate",
            Bundle::class.java,
            object : XposedInterface.BeforeAfterHookCallback {
                override fun afterHookedMethod(param: XposedInterface.HookParam) {
                    MenuActionHandler.setCurrentShareDialog(param.thisObject)

                    val currentAweme = XposedManager.getCurrentAweme()
                    if (currentAweme == null && MENU_MODULE_DEBUG_LOG) {
                        LogUtils.logW(TAG, "getCurrentAweme 返回空")
                    }

                    MenuUIBuilder.clearCurrentMenu()

                    Handler(Looper.getMainLooper()).postDelayed({
                        injectBottomMenuToDialog(param.thisObject)
                    }, 150)
                }
            }
        )

        XposedManager.findAndHookMethod(
            className,
            classLoader,
            "onDestroy",
            object : XposedInterface.BeforeAfterHookCallback {
                override fun beforeHookedMethod(param: XposedInterface.HookParam) {
                    if (MENU_MODULE_DEBUG_LOG) LogUtils.logI(TAG, "分享弹窗销毁 清理菜单状态")
                    MenuUIBuilder.clearCurrentMenu()
                    MenuActionHandler.clearShareDialog()
                }
            }
        )
    }

    private fun injectBottomMenuToDialog(dialog: Any) {
        runCatching {
            val window = XposedManager.callMethod(dialog, "getWindow") ?: return
            val rootView = XposedManager.callMethod(window, "getDecorView") as? ViewGroup ?: return

            val existingMenu = rootView.findViewWithTag<View>(CUSTOM_MENU_TAG)
            if (existingMenu != null) {
                (existingMenu.parent as? ViewGroup)?.removeView(existingMenu)
            }

            val buttonCount = 5
            val ourMenuContainer = MenuUIBuilder.createAdaptiveMenuContainer(rootView.context, buttonCount) ?: return

            addMenuToCorrectPosition(rootView, ourMenuContainer)
        }.onFailure { e ->
            LogUtils.logE(TAG, "注入自定义菜单失败 err=${e.message}", e)
        }
    }

    private fun addMenuToCorrectPosition(rootView: ViewGroup, menuToAdd: ViewGroup) {
        runCatching {
            val recyclerViews = mutableListOf<View>()
            findAllRecyclerViews(rootView, recyclerViews)
            if (recyclerViews.size < 2) return

            recyclerViews.sortBy { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                loc[1]
            }

            val bottomRecyclerView = recyclerViews.last()
            val parentContainer = bottomRecyclerView.parent as? ViewGroup ?: return

            menuToAdd.tag = CUSTOM_MENU_TAG
            parentContainer.addView(menuToAdd)

            val nativeMarginParams = bottomRecyclerView.layoutParams as? ViewGroup.MarginLayoutParams
            var nativeVerticalSpacing = nativeMarginParams?.topMargin ?: 0
            if (nativeVerticalSpacing <= 0) {
                nativeVerticalSpacing = (12 * rootView.context.resources.displayMetrics.density).toInt()
            }

            val ourMenuMarginParams = menuToAdd.layoutParams as? ViewGroup.MarginLayoutParams
            if (ourMenuMarginParams != null) {
                ourMenuMarginParams.topMargin = nativeVerticalSpacing
                menuToAdd.layoutParams = ourMenuMarginParams
            }

            if (MENU_MODULE_DEBUG_LOG) LogUtils.logSuccess(TAG, "菜单控件植入与布局对齐完成")
        }.onFailure { e ->
            LogUtils.logE(TAG, "自定义菜单定位对齐失败 err=${e.message}", e)
        }
    }

    private fun findAllRecyclerViews(root: ViewGroup, resultList: MutableList<View>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.javaClass.name.contains("RecyclerView")) {
                resultList.add(child)
            }
            if (child is ViewGroup) {
                findAllRecyclerViews(child, resultList)
            }
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
