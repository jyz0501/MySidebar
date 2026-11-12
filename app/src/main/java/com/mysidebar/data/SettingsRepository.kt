package com.mysidebar.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.sidebarDataStore: DataStore<Preferences> by preferencesDataStore(name = "sidebar_settings")

/**
 * 首次引导状态。刻意不放进 [SidebarSettings]，避免导入他人配置时把引导重置。
 */
data class OnboardingFlags(
    val demoPlayed: Boolean = false,
    val guideDismissed: Boolean = false
)

private data class InitialState(
    val settings: SidebarSettings,
    val onboarding: OnboardingFlags
)

/**
 * 配置仓库：对外暴露永不为空的 [StateFlow]，服务层订阅即可实时生效，
 * 无需重启悬浮窗服务。写操作先更新内存再异步落盘，保证 UI 与悬浮窗零延迟响应。
 */
class SettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SettingsRepository"
        private val CONFIG_KEY = stringPreferencesKey("config_json")
        private val DEMO_PLAYED_KEY = booleanPreferencesKey("demo_played")
        private val GUIDE_DISMISSED_KEY = booleanPreferencesKey("guide_dismissed")
    }

    /** 配置 JSON 与引导标志合并为一次读取，避免两次阻塞式 DataStore 访问。 */
    private val initialState: InitialState = runBlocking(Dispatchers.IO) {
        try {
            val prefs = context.sidebarDataStore.data.first()
            InitialState(
                settings = ConfigExporter.decode(prefs[CONFIG_KEY]),
                onboarding = OnboardingFlags(
                    demoPlayed = prefs[DEMO_PLAYED_KEY] ?: false,
                    guideDismissed = prefs[GUIDE_DISMISSED_KEY] ?: false
                )
            )
        } catch (t: Throwable) {
            Log.w(TAG, "failed to read preferences", t)
            InitialState(SidebarSettings(), OnboardingFlags())
        }
    }

    private val _settings = MutableStateFlow(initialState.settings)
    val settings: StateFlow<SidebarSettings> = _settings.asStateFlow()

    private val _onboarding = MutableStateFlow(initialState.onboarding)
    val onboarding: StateFlow<OnboardingFlags> = _onboarding.asStateFlow()

    fun current(): SidebarSettings = _settings.value

    fun update(transform: (SidebarSettings) -> SidebarSettings) {
        val next = transform(_settings.value).sanitized()
        if (next == _settings.value) return
        _settings.value = next
        persistSettings(next)
    }

    fun replace(next: SidebarSettings) = update { next }

    fun markDemoPlayed() = updateOnboarding { it.copy(demoPlayed = true) }

    fun dismissGuide() = updateOnboarding { it.copy(guideDismissed = true) }

    private fun updateOnboarding(transform: (OnboardingFlags) -> OnboardingFlags) {
        val next = transform(_onboarding.value)
        if (next == _onboarding.value) return
        _onboarding.value = next
        scope.launch(Dispatchers.IO) {
            try {
                context.sidebarDataStore.edit {
                    it[DEMO_PLAYED_KEY] = next.demoPlayed
                    it[GUIDE_DISMISSED_KEY] = next.guideDismissed
                }
            } catch (t: Throwable) {
                Log.w(TAG, "persist onboarding failed", t)
            }
        }
    }

    private fun persistSettings(s: SidebarSettings) {
        val json = ConfigExporter.encode(s)
        scope.launch(Dispatchers.IO) {
            try {
                context.sidebarDataStore.edit { it[CONFIG_KEY] = json }
            } catch (t: Throwable) {
                // 落盘失败不阻塞当前会话；下次设置变更会重试。
                Log.w(TAG, "persist failed", t)
            }
        }
    }

    /** 首次启动插入默认图标；之后只补齐缺失的固定锁定应用，未安装的一律跳过。 */
    suspend fun ensureDefaults(apps: AppRepository) {
        if (_settings.value.icons.isEmpty()) {
            val defaults = apps.defaultIconEntries()
            if (defaults.isNotEmpty()) update { it.copy(icons = defaults) }
            return
        }
        val pinned = apps.pinnedEntries()
        if (pinned.isEmpty()) return
        val missing = pinned.filter { p -> _settings.value.icons.none { it.packageName == p.packageName } }
        if (missing.isEmpty()) return
        update { s -> s.copy(icons = (s.icons + missing).take(40)) }
    }
}
