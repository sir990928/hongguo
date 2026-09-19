import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import com.android.hongguo.core.HookDispatcher

/**
 * 纯 LibXposed‑API 入口，完全不引入 de.robv.android.xposed
 * 类名保留 `آب`；混淆由 proguard 保护，框架通过 xposed_module.prop main 指定
 */
class `آب` : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        // 1. 初始化HookDispatcher，传入XposedModule实例（关键！）
        HookDispatcher.init(this)
        // 2. 分发到HookDispatcher处理
        HookDispatcher.dispatch(param)
    }
}
