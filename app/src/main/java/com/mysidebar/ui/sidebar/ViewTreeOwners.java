package com.mysidebar.ui.sidebar;

import android.view.View;

import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/**
 * Lifecycle 2.8 / SavedState 1.2 把 ViewTree*.set() 标记为 Kotlin internal，
 * Kotlin 侧无法直接引用；但字节码中仍是 public static，Java 侧可以安全地直接调用。
 * Service 中的 ComposeView 需要这三个 owner 才能正常组合。
 */
public final class ViewTreeOwners {

    private ViewTreeOwners() {
    }

    public static void attach(View view, OverlayLifecycleOwner owner) {
        ViewTreeLifecycleOwner.set(view, owner);
        ViewTreeViewModelStoreOwner.set(view, owner);
        ViewTreeSavedStateRegistryOwner.set(view, owner);
    }
}
