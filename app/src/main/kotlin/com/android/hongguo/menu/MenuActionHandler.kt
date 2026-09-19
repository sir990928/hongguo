package com.android.hongguo.menu

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.android.hongguo.utils.manager.*

object MenuActionHandler {

    private const val TAG = "MenuActionHandler"

    /** 默认最高画质开关 */
    const val KEY_HIGHEST_QUALITY = "highest_quality"

    /** 拦截广告开关 */
    const val KEY_AD_BLOCK = "ad_block"

    /** 解锁 VIP 开关 */
    const val KEY_VIP_UNLOCK = "vip_unlock"

    fun onMenuClick(ctx: Context) {
        LogUtils.logI(TAG, "menu row clicked, show dialog")
        runCatching { MenuDialog.showMenuDialog(ctx, "模块设置", MenuData.ITEMS) }
            .onFailure { e -> LogUtils.logE(TAG, "show dialog failed: ${e.message}", e) }
    }

    fun onDirectAction(ctx: Context, title: String) {
        LogUtils.logI(TAG, "direct action: $title")
        when (title) {
            "重新适配" -> {
                runCatching { MMKVManager.clearDexCache() }
                    .onFailure { LogUtils.logE(TAG, "clear MMKV failed", it) }

                runCatching {
                    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        val pi = PendingIntent.getActivity(
                            ctx, 0, launch,
                            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        am.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pi)
                    }
                }.onFailure { LogUtils.logE(TAG, "schedule relaunch failed", it) }

                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun onChildClick(ctx: Context, parent: String, child: String) {
        LogUtils.logI(TAG, "child clicked: $parent / $child")
        // TODO: 各功能逻辑
    }

    fun onToggleChanged(ctx: Context, parent: String, child: String, on: Boolean) {
        LogUtils.logI(TAG, "toggle changed: $parent / $child = $on")
        when (child) {
            "默认最高画质" -> {
                MMKVManager.putBoolean(KEY_HIGHEST_QUALITY, on)
                LogUtils.logI(TAG, "默认最高画质 = $on （已写入 MMKV）")
            }
            "拦截广告" -> {
                MMKVManager.putBoolean(KEY_AD_BLOCK, on)
                LogUtils.logI(TAG, "拦截广告 = $on （已写入 MMKV）")
            }
            "解锁vip" -> {
                MMKVManager.putBoolean(KEY_VIP_UNLOCK, on)
                LogUtils.logI(TAG, "解锁vip = $on （已写入 MMKV）")
            }
            else -> {
                // 其它开关以后再接
            }
        }
    }

    fun onSliderChanged(ctx: Context, panel: String, which: String, value: Int) {
        LogUtils.logI(TAG, "slider: $panel / $which = $value")
        // TODO: 各滑条逻辑
    }
}