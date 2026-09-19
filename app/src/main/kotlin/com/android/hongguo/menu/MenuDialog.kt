package com.android.hongguo.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

object MenuDialog {

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** 列表式弹窗：一级、列表型二级共用 */
    fun showMenuDialog(ctx: Context, dialogTitle: String, items: List<MenuData.ExpandableItem>) {
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

        val title = TextView(ctx).apply {
            text = dialogTitle
            textSize = 20f
            setTextColor(Color.parseColor("#222222"))
        }
        card.addView(title)

        val search = EditText(ctx).apply {
            hint = "搜索"
            textSize = 14f
            setSingleLine()
            setTextColor(Color.parseColor("#333333"))
            setHintTextColor(Color.parseColor("#999999"))
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(Color.parseColor("#F2F2F2"))
            }
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
        }
        card.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16f) })

        val listContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(ctx).apply {
            addView(listContainer)
            isFillViewport = false
        }
        val listParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12f) }
        card.addView(scroll, listParams)

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
        ).apply { topMargin = dp(16f) })

        mask.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        fun renderList(filter: String) {
            listContainer.removeAllViews()
            items.forEach { item ->
                val hit = filter.isEmpty() ||
                        item.title.contains(filter, true) ||
                        item.children.any { MenuData.childName(it).contains(filter, true) }
                if (!hit) return@forEach

                listContainer.addView(buildParentRow(ctx, item) {
                    if (item.children.isEmpty()) {
                        MenuActionHandler.onDirectAction(ctx, item.title)
                    } else {
                        item.expanded = !item.expanded
                        renderList(filter)
                    }
                })

                if (item.expanded) {
                    item.children.forEach { child ->
                        when (child) {
                            is MenuData.Child.Plain -> listContainer.addView(
                                buildChildRow(ctx, child.name) {
                                    MenuActionHandler.onChildClick(ctx, item.title, child.name)
                                }
                            )
                            is MenuData.Child.Toggle -> listContainer.addView(
                                buildToggleRow(ctx, child) { on ->
                                    MenuActionHandler.onToggleChanged(ctx, item.title, child.name, on)
                                }
                            )
                            is MenuData.Child.SubMenu -> listContainer.addView(
                                buildSubMenuRow(ctx, child.name) {
                                    showMenuDialog(ctx, child.name,
                                        listOf(MenuData.ExpandableItem(child.name, child.items)))
                                }
                            )
                            is MenuData.Child.SliderPanel -> listContainer.addView(
                                buildSubMenuRow(ctx, child.name) { MenuSecondaryDialog.showSliderDialog(ctx, child.name) }
                            )
                            is MenuData.Child.SpinnerRow -> listContainer.addView(
                                MenuSecondaryDialog.buildSpinnerRow(ctx, child.name)
                            )
                        }
                    }
                }
            }
            scroll.post {
                val contentH = listContainer.height
                val maxH = (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt()
                scroll.layoutParams = listParams.apply {
                    height = if (contentH > maxH) maxH else LinearLayout.LayoutParams.WRAP_CONTENT
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderList(s?.toString().orEmpty())
            }
        })

        renderList("")

        dialog.setContentView(mask)
        dialog.show()

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun buildParentRow(ctx: Context, item: MenuData.ExpandableItem, onClick: () -> Unit): View {
        fun dp(v: Float) = dp(ctx, v)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(14f), dp(4f), dp(14f))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(ctx).apply {
            text = item.title
            textSize = 15f
            setTextColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (item.children.isNotEmpty()) {
            row.addView(TextView(ctx).apply {
                text = if (item.expanded) "▾" else "▸"
                textSize = 14f
                setTextColor(Color.parseColor("#999999"))
            })
        }
        return row
    }

    private fun buildChildRow(ctx: Context, name: String, onClick: () -> Unit): View {
        fun dp(v: Float) = dp(ctx, v)
        return TextView(ctx).apply {
            text = name
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(12f), dp(4f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    private fun buildSubMenuRow(ctx: Context, name: String, onClick: () -> Unit): View {
        fun dp(v: Float) = dp(ctx, v)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(12f), dp(4f), dp(12f))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(ctx).apply {
            text = name
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = "›"
            textSize = 16f
            setTextColor(Color.parseColor("#BBBBBB"))
        })
        return row
    }

    private fun buildToggleRow(ctx: Context, toggle: MenuData.Child.Toggle, onChanged: (Boolean) -> Unit): View {
        fun dp(v: Float) = dp(ctx, v)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(8f), dp(4f), dp(8f))
        }
        row.addView(TextView(ctx).apply {
            text = toggle.name
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Switch(ctx).apply {
            // ★ 有 liveRead 就现读 MMKV；否则退回内存值
            isChecked = toggle.liveRead?.invoke() ?: toggle.on
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                toggle.on = checked
                onChanged(checked)
            }
        })
        return row
    }
}