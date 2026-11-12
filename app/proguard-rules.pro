-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.**
-keepclassmembers class com.qianxian.MySidebar.data.** { *; }

# 反射调用 AndroidX 的 ViewTree* owner 挂接（被 @RestrictTo，需显式保留）
-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }
-keep class androidx.lifecycle.ViewTreeViewModelStoreOwner { *; }
-keep class androidx.savedstate.ViewTreeSavedStateRegistryOwner { *; }
