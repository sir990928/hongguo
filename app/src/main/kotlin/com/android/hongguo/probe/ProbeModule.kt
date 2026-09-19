package com.android.hongguo.probe

import android.view.ViewParent
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.hongguo.utils.manager.*
import io.github.libxposed.api.XposedModuleInterface

object ProbeModule : IHookModule {
   

    private const val TAG = "ProbeModule"
    private var lastVolumeUpAt = 0L
    private var volumeUpCount = 0

    // 长按探针模式开关
    @Volatile private var probeMode = false

    // 长按检测
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    override fun getModuleName(): String = "ProbeModule"

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            val cl = param.defaultClassLoader

            // 1) 音量键：3 连按 切换探针模式
            XposedManager.findAndHookMethod(
                className = "android.app.Activity",
                classLoader = cl,
                methodName = "dispatchKeyEvent",
                parameterTypes = arrayOf(KeyEvent::class.java),
                beforeMethod = { thisObj, args ->
                    val ev = args.getOrNull(0) as? KeyEvent
                    if (ev != null) onKey(ev)
                }
            )

            // 2) 触摸：探针模式下长按 -> 报命中的 View
            XposedManager.findAndHookMethod(
                className = "android.app.Activity",
                classLoader = cl,
                methodName = "dispatchTouchEvent",
                parameterTypes = arrayOf(MotionEvent::class.java),
                beforeMethod = { thisObj, args ->
                    val ev = args.getOrNull(0) as? MotionEvent
                    if (ev != null) onTouch(ev, thisObj as? Activity)
                }
            )

            LogUtils.logI(TAG, "ProbeModule hook done")
        }.onFailure { e ->
            LogUtils.logE(TAG, "hook failed: ${e.message}", e)
        }
    }

    private fun onKey(ev: KeyEvent) {
        if (ev.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return
        if (ev.action != KeyEvent.ACTION_DOWN) return

        val now = System.currentTimeMillis()
        if (now - lastVolumeUpAt > 1200) volumeUpCount = 0
        lastVolumeUpAt = now
        volumeUpCount++

        if (volumeUpCount >= 3) {
            volumeUpCount = 0
            probeMode = !probeMode
            LogUtils.logI(TAG, "★ 长按探针模式 = $probeMode")
        }
    }

    private fun onTouch(ev: MotionEvent, activity: Activity?) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                downAt = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                if (!probeMode) return
                val dur = System.currentTimeMillis() - downAt
                val dx = Math.abs(ev.rawX - downX)
                val dy = Math.abs(ev.rawY - downY)
                if (dur >= 500 && dx < 20 && dy < 20) {
                    val x = ev.rawX.toInt()
                    val y = ev.rawY.toInt()
                    LogUtils.logI(TAG, "★ 长按命中检测 x=$x y=$y")
                    Handler(Looper.getMainLooper()).post {
                        hitTest(activity, x, y)
                    }
                }
            }
        }
    }

    private fun hitTest(activity: Activity?, x: Int, y: Int) {
        LogUtils.logI(TAG, "===== HIT START ($x,$y) =====")
        try {
            val decor = activity?.window?.decorView as? ViewGroup
            if (decor == null) {
                LogUtils.logE(TAG, "decorView null", null)
                return
            }
            val hit = findDeepestHit(decor, x, y)
            if (hit == null) {
                LogUtils.logI(TAG, "该坐标未命中任何 View")
            } else {
                logViewWithParents(hit)
            }
        } catch (e: Throwable) {
            LogUtils.logE(TAG, "hitTest failed: ${e.message}", e)
        }
        LogUtils.logI(TAG, "===== HIT END =====")
    }

    /** 找最深（最上层）命中该坐标的可见 View */
    private fun findDeepestHit(root: View, x: Int, y: Int): View? {
        if (root.visibility != View.VISIBLE) return null
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val left = loc[0]
        val top = loc[1]
        val right = left + root.width
        val bottom = top + root.height
        if (x < left || x >= right || y < top || y >= bottom) return null

        if (root is ViewGroup) {
            // 从后往前（后加的在上层）
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                val hit = findDeepestHit(child, x, y)
                if (hit != null) return hit
            }
        }
        return root
    }

    /** 打印命中的 View + 父链 */
    private fun logViewWithParents(v: View) {
        // 命中者
        LogUtils.logI(TAG, "命中: ${describe(v)}")

        // 父链
        var p: ViewParent? = v.parent
        var depth = 0
        while (p is View && depth < 40) {
            LogUtils.logI(TAG, "  ↑父: ${describe(p)}")
            p = p.parent
            depth++
        }
    }

    private fun describe(v: View): String {
        val sb = StringBuilder()
        sb.append(v.javaClass.name)
        val idName = try {
            if (v.id != View.NO_ID) v.resources.getResourceEntryName(v.id) else "none"
        } catch (_: Throwable) { "0x" + Integer.toHexString(v.id) }
        sb.append(" id=").append(idName)
        sb.append(" ").append(v.width).append("x").append(v.height)
        sb.append(" vis=").append(v.visibility)
        if (v is TextView) {
            val t = try { v.text?.toString() } catch (_: Throwable) { null }
            if (!t.isNullOrBlank()) sb.append(" text=\"").append(t).append("\"")
        }
        if (v is ViewGroup) sb.append(" childCount=").append(v.childCount)
        // 屏幕坐标
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        sb.append(" pos=(").append(loc[0]).append(",").append(loc[1]).append(")")
        return sb.toString()
    }
}