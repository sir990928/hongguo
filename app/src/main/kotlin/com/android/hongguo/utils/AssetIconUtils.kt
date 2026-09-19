package com.android.hongguo.utils

import com.android.hongguo.utils.manager.*
import android.widget.ImageView
import android.graphics.drawable.Drawable

object AssetIconUtils {
    fun setIconFromAssets(imageView: ImageView?, iconFileName: String?) {
        if (imageView == null || iconFileName.isNullOrEmpty()) return
        val path = if (iconFileName.endsWith(".webp")) iconFileName else "$iconFileName.webp"
        runCatching {
            imageView.context.assets.open(path).use { stream ->
                val drawable = Drawable.createFromStream(stream, null)
                imageView.setImageDrawable(drawable)
            }
        }.onFailure { e ->
            LogUtils.logE("AssetIconUtils", "加载图标失败 $path", e)
            imageView.setImageResource(android.R.drawable.stat_notify_error)
        }
    }
}
