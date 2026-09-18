import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import com.android.hongguo.core.HookDispatcher

/**
 * 纯 LibXposed‑API 入口，完全不引入 de.robv.android.xposed
 * 类名保留 `آب`；混淆由 proguard 保护，框架通过 xposed_module.prop main 指定
 */
class `آب` : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        // object单例直接调用，不需要 getInstance()
        HookDispatcher.dispatch(param)
    }
}
