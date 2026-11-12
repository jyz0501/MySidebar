package com.mysidebar

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mysidebar.service.OnboardingController
import com.mysidebar.service.ServiceBridge
import com.mysidebar.service.ServiceController
import com.mysidebar.ui.settings.SettingsApp
import com.mysidebar.ui.settings.SettingsPage
import com.mysidebar.ui.theme.MySidebarTheme
import com.mysidebar.util.Permissions
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_PICKER = "com.mysidebar.extra.OPEN_PICKER"
    }

    /** 从侧边栏「添加应用」跳转而来时跳过首次演示，避免打断用户当前操作。 */
    private var skipOnboarding = false

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) toast("未授予通知权限，前台服务通知可能不显示")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openPicker = intent?.getBooleanExtra(EXTRA_OPEN_PICKER, false) == true
        skipOnboarding = openPicker
        setContent {
            MySidebarTheme {
                SettingsApp(
                    startPage = if (openPicker) SettingsPage.Icons else SettingsPage.Home,
                    openPicker = openPicker
                )
            }
        }
        if (!Permissions.notificationPermissionGranted(this)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!skipOnboarding) OnboardingController.play(this@MainActivity)
                try {
                    awaitCancellation()
                } finally {
                    OnboardingController.cancel()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ServiceBridge.hostInForeground.value = true
        ServiceController.sync(this)
        ServiceBridge.sync()
    }

    override fun onPause() {
        ServiceBridge.hostInForeground.value = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        skipOnboarding = intent.getBooleanExtra(EXTRA_OPEN_PICKER, false)
        if (skipOnboarding) recreate()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
