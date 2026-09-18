package com.android.hongguo.menu

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.android.hongguo.utils.LogUtils
import java.lang.ref.WeakReference

object MenuUIBuilder {

    private const val TAG = "MenuUIBuilder"
    private var currentMenuRef: WeakReference<ViewGroup>? = null

    /**
     * 清理当前菜单引用
     */
    fun clearCurrentMenu() {
        currentMenuRef?.get()?.let { menuView ->
            (menuView.parent as? ViewGroup)?.removeView(menuView)
        }
        currentMenuRef?.clear()
        currentMenuRef = null
    }

    /**
     * 创建自适应数量的菜单容器
     */
    fun createAdaptiveMenuContainer(context: Context, buttonCount: Int): ViewGroup? {
        return runCatching {
            val density = context.resources.displayMetrics.density

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val marginHorizontal = (16 * density).toInt()
                    setMargins(marginHorizontal, (8 * density).toInt(), marginHorizontal, (8 * density).toInt())
                }
            }

            val itemTitles = arrayOf("选项一", "选项二", "选项三", "选项四", "选项五")
            val actualCount = buttonCount.coerceAtMost(itemTitles.size)

            for (i in 0 until actualCount) {
                val button = createMenuItem(context, itemTitles[i], i)
                container.addView(button)
            }

            currentMenuRef = WeakReference(container)
            container
        }.onFailure { e ->
            LogUtils.logE(TAG, "构建菜单容器失败 err=${e.message}", e)
        }.getOrNull()
    }

    private fun createMenuItem(context: Context, title: String, index: Int): View {
        val density = context.resources.displayMetrics.density

        return TextView(context).apply {
            text = title
            textSize = 12f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

            setOnClickListener {
                MenuActionHandler.onMenuActionClick(index)
            }
        }
    }
}
