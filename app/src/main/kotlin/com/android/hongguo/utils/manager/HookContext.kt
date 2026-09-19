package com.android.hongguo.utils.manager

import android.content.Context

object HookContext {
    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext ?: context
    }

    fun getContext(): Context? = appContext
}
