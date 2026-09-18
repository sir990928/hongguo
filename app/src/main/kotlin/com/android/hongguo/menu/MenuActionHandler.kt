package com.android.hongguo.menu

import com.android.hongguo.utils.LogUtils
import java.lang.ref.WeakReference

object MenuActionHandler {

    private const val TAG = "MenuActionHandler"
    private var shareDialogRef: WeakReference<Any>? = null

    fun setCurrentShareDialog(dialog: Any) {
        shareDialogRef = WeakReference(dialog)
    }

    fun clearShareDialog() {
        shareDialogRef?.clear()
        shareDialogRef = null
    }

    fun getShareDialog(): Any? = shareDialogRef?.get()

    /**
     * 响应菜单按钮点击事件
     */
    fun onMenuActionClick(actionType: Int) {
        LogUtils.logI(TAG, "点击菜单按钮 actionType=$actionType")
        when (actionType) {
            0 -> handleActionZero()
            1 -> handleActionOne()
            2 -> handleActionTwo()
            3 -> handleActionThree()
            4 -> handleActionFour()
            else -> LogUtils.logW(TAG, "未知动作类型 $actionType")
        }
    }

    private fun handleActionZero() {
        LogUtils.logI(TAG, "执行动作 0")
    }

    private fun handleActionOne() {
        LogUtils.logI(TAG, "执行动作 1")
    }

    private fun handleActionTwo() {
        LogUtils.logI(TAG, "执行动作 2")
    }

    private fun handleActionThree() {
        LogUtils.logI(TAG, "执行动作 3")
    }

    private fun handleActionFour() {
        LogUtils.logI(TAG, "执行动作 4")
    }
}
