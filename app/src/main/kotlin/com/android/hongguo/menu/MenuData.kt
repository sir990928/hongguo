package com.android.hongguo.menu

import com.android.hongguo.utils.manager.MMKVManager

object MenuData {

    /** 默认最高画质开关（与 MenuActionHandler / SpeedProbe 共用） */
    const val KEY_HIGHEST_QUALITY = "highest_quality"

    /** 拦截广告开关（与 MenuActionHandler / AdProbe 共用） */
    const val KEY_AD_BLOCK = "ad_block"

    /** 解锁 VIP 开关（与 MenuActionHandler / AdProbe 共用） */
    const val KEY_VIP_UNLOCK = "vip_unlock"

    sealed class Child {
        data class Plain(val name: String) : Child()

        data class Toggle(
            val name: String,
            var on: Boolean = false,
            val liveRead: (() -> Boolean)? = null
        ) : Child()

        data class SubMenu(val name: String, val items: List<Child>) : Child()

        data class SliderPanel(val name: String) : Child()

        data class SpinnerRow(val name: String) : Child()
    }

    data class ExpandableItem(
        val title: String,
        val children: List<Child>,
        var expanded: Boolean = false
    )

    val SPEEDS = arrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)

    private fun subItemsFor(name: String): List<Child> = when (name) {
        "顶部菜单项" -> listOf(Child.Plain("二级项 A"), Child.Plain("二级项 B"))
        "底部菜单项" -> listOf(Child.Plain("二级项 A"), Child.Plain("二级项 B"))
        "视频ui组件" -> listOf(Child.Plain("二级项 A"), Child.Plain("二级项 B"))
        else -> emptyList()
    }

    val ITEMS = listOf(
        ExpandableItem(
            "界面设置",
            listOf(
                Child.SubMenu("顶部菜单项", subItemsFor("顶部菜单项")),
                Child.SubMenu("底部菜单项", subItemsFor("底部菜单项")),
                Child.SubMenu("视频ui组件", subItemsFor("视频ui组件")),
                Child.Toggle("状态栏透明"),
                Child.Toggle("底部透明"),
                Child.Toggle("导航栏透明"),
                Child.Toggle("隐藏导航栏"),
                Child.Toggle("隐藏状态栏"),
                Child.SliderPanel("首页控件透明度")
            )
        ),
        ExpandableItem(
            "视频功能",
            listOf(
                Child.SubMenu("手势设置", listOf(Child.Plain("三级项 A"), Child.Plain("三级项 B"))),
                Child.SpinnerRow("播放倍数"),
                Child.Toggle(
                    "默认最高画质",
                    on = true,
                    liveRead = { MMKVManager.getBoolean(KEY_HIGHEST_QUALITY, true) }
                )
            )
        ),
        ExpandableItem(
            "广告与Vip",
            listOf(
                Child.Toggle(
                    "拦截广告",
                    on = true,
                    liveRead = { MMKVManager.getBoolean(KEY_AD_BLOCK, true) }
                ),
                Child.Toggle(
                    "解锁vip",
                    on = false,
                    liveRead = { MMKVManager.getBoolean(KEY_VIP_UNLOCK, false) }
                )
            )
        ),
        ExpandableItem("重新适配", emptyList())
    )

    fun childName(c: Child): String = when (c) {
        is Child.Plain -> c.name
        is Child.Toggle -> c.name
        is Child.SubMenu -> c.name
        is Child.SliderPanel -> c.name
        is Child.SpinnerRow -> c.name
    }
}