package com.android.hongguo.menu

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.hongguo.utils.manager.*

object MenuUIBuilder {

    private const val TAG = "MenuUIBuilder"

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /**
     * 造那个"图标 + 文字 + 箭头"的模块设置行。
     * 只管长得像菜单项、点得着；点的时候把 ctx 回调出去。
     */
    fun buildRow(ctx: Context, onClick: (Context) -> Unit): View? {
        return runCatching {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 12f).toFloat()
                    setColor(Color.parseColor("#F5F5F5"))
                }
                setOnClickListener { onClick(ctx) }
            }

            // 左图标（先占位，之后换成你的资源）
            val icon = ImageView(ctx).apply {
                setImageResource(android.R.drawable.ic_menu_preferences)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 24f), dp(ctx, 24f))
            }

            // 中间文字
            val title = TextView(ctx).apply {
                text = "模块设置"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = dp(ctx, 12f) }
            }

            // 右箭头
            val arrow = TextView(ctx).apply {
                text = "›"
                textSize = 20f
                setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(icon)
            row.addView(title)
            row.addView(arrow)
            row
        }.onFailure { e ->
            LogUtils.logE(TAG, "buildRow failed: ${e.message}", e)
        }.getOrNull()
    }
}