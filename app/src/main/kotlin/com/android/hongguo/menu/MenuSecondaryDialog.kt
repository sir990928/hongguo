package com.android.hongguo.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import com.android.hongguo.utils.manager.MMKVManager

object MenuSecondaryDialog {

    private const val KEY_PLAY_SPEED = "play_speed"

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** 滑条式二级窗：上/中/下 */
    fun showSliderDialog(ctx: Context, dialogTitle: String) {
        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        fun dp(v: Float) = dp(ctx, v)

        val mask = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(dp(24f), 0, dp(24f), 0)
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(Color.WHITE)
            }
            setPadding(dp(20f), dp(20f), dp(20f), dp(16f))
            isClickable = true
        }

        card.addView(TextView(ctx).apply {
            text = dialogTitle
            textSize = 20f
            setTextColor(Color.parseColor("#222222"))
        })

        card.addView(buildSliderRow(ctx, "上") { v -> MenuActionHandler.onSliderChanged(ctx, dialogTitle, "上", v) })
        card.addView(buildSliderRow(ctx, "中") { v -> MenuActionHandler.onSliderChanged(ctx, dialogTitle, "中", v) })
        card.addView(buildSliderRow(ctx, "下") { v -> MenuActionHandler.onSliderChanged(ctx, dialogTitle, "下", v) })

        val done = TextView(ctx).apply {
            text = "完成"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(14f), 0, dp(14f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(Color.parseColor("#FF5722"))
            }
            setOnClickListener { dialog.dismiss() }
        }
        card.addView(done, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20f) })

        mask.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        dialog.setContentView(mask)
        dialog.show()

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun buildSliderRow(ctx: Context, label: String, onChanged: (Int) -> Unit): View {
        fun dp(v: Float) = dp(ctx, v)

        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, dp(4f))
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val valueView = TextView(ctx).apply {
            text = "0"
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
        }

        topRow.addView(TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(valueView)

        val seek = SeekBar(ctx).apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    valueView.text = p.toString()
                    if (fromUser) onChanged(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        wrap.addView(topRow)
        wrap.addView(seek)
        return wrap
    }

    /** 播放倍数行：右侧小框 + 上方下拉 */
    fun buildSpinnerRow(ctx: Context, name: String): View {
        fun dp(v: Float) = dp(ctx, v)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(10f), dp(4f), dp(10f))
        }
        row.addView(TextView(ctx).apply {
            text = name
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        var current = MMKVManager.getFloat(KEY_PLAY_SPEED, 1.0f)
        if (MenuData.SPEEDS.none { it == current }) current = 1.0f

        val valueBox = TextView(ctx).apply {
            text = "${current}x  ▾"
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(8f).toFloat()
                setColor(Color.parseColor("#F2F2F2"))
            }
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
        }
        valueBox.setOnClickListener { anchor ->
            showSpeedPopup(ctx, anchor as View, current) { picked ->
                current = picked
                MMKVManager.putFloat(KEY_PLAY_SPEED, picked)
                valueBox.text = "${picked}x  ▾"
            }
        }
        row.addView(valueBox)
        return row
    }

    private fun showSpeedPopup(ctx: Context, anchor: View, current: Float, onPick: (Float) -> Unit) {
        fun dp(v: Float) = dp(ctx, v)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(Color.WHITE)
            }
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            elevation = dp(8f).toFloat()
        }

        val popup = PopupWindow(
            content,
            dp(160f),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        MenuData.SPEEDS.forEach { sp ->
            val selected = sp == current
            val row = TextView(ctx).apply {
                text = if (selected) "${sp}x  ✓" else "${sp}x"
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                setTextColor(if (selected) Color.parseColor("#FF5722") else Color.parseColor("#333333"))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8f).toFloat()
                    setColor(if (selected) Color.parseColor("#FFF0E6") else Color.TRANSPARENT)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.setOnClickListener {
                onPick(sp)
                popup.dismiss()
            }
            content.addView(row)
        }

        content.measure(
            View.MeasureSpec.makeMeasureSpec(dp(160f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupH = content.measuredHeight

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], loc[1] - popupH - dp(8f))
    }
}