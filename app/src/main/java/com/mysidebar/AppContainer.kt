package com.mysidebar

import android.content.Context
import com.mysidebar.data.AppRepository
import com.mysidebar.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 极简 ServiceLocator：项目规模很小，不引入 DI 框架。 */
object AppContainer {

    private lateinit var appContext: Context

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: SettingsRepository by lazy { SettingsRepository(appContext, scope) }
    val apps: AppRepository by lazy { AppRepository(appContext) }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        settings // 触发初始化，尽早读回配置
        scope.launch {
            runCatching { settings.ensureDefaults(apps) }
        }
    }
}
