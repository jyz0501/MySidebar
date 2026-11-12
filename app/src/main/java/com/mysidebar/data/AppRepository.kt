package com.mysidebar.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(val packageName: String, val label: String)

/** 已安装应用查询、应用图标内存缓存与启动逻辑。 */
class AppRepository(private val context: Context) {

    private val packageManager get() = context.packageManager
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = try {
                info.loadLabel(packageManager).toString()
            } catch (t: Throwable) {
                pkg
            }
            AppInfo(pkg, label)
        }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    suspend fun iconFor(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache[packageName]?.let { return@withContext it }
        val bitmap = try {
            packageManager.getApplicationIcon(packageName).toImageBitmap()
        } catch (t: Throwable) {
            null
        }
        if (bitmap != null) iconCache[packageName] = bitmap
        bitmap
    }

    fun labelFor(packageName: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    } catch (t: Throwable) {
        packageName
    }

    /** 出厂默认固定锁定的应用；未安装的会自动跳过，不会造成缺项或崩溃。 */
    private val pinnedApps = listOf(
        "com.autolink.engineermode/.MainActivity",
        "com.yunpan.appmanage",
        "com.qianxian.adbmanager"
    )

    /** 固定应用是否已安装。未安装的一律忽略，界面与启动都不会因此出错。 */
    fun isInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (t: Throwable) {
        false
    }

    /** 固定应用中已安装的那些，构造为锁定条目。 */
    fun pinnedEntries(): List<IconEntry> {
        val result = ArrayList<IconEntry>(pinnedApps.size)
        for (spec in pinnedApps) {
            val pkg = spec.substringBefore('/')
            if (!isInstalled(pkg)) continue
            val component = spec.substringAfter('/', "").ifBlank { null }
            result.add(IconEntry(pkg, labelFor(pkg), locked = true, component = component))
        }
        return result
    }

    /** 出厂默认图标：固定锁定的应用优先，不足时用常见系统应用补足 6 个。 */
    suspend fun defaultIconEntries(): List<IconEntry> {
        val installed = loadLaunchableApps()
        val byPackage = installed.associateBy { it.packageName }
        val result = ArrayList<IconEntry>(6)
        for (entry in pinnedEntries()) {
            if (result.size >= 6) break
            if (result.any { it.packageName == entry.packageName }) continue
            result.add(entry)
        }
        val preferred = listOf(
            "com.android.dialer", "com.google.android.dialer",
            "com.google.android.apps.messaging", "com.android.mms",
            "com.android.chrome", "org.mozilla.firefox",
            "com.android.camera2", "com.google.android.GoogleCamera",
            "com.google.android.apps.photos", "com.android.gallery3d",
            "com.android.settings", "com.google.android.apps.maps",
            "com.google.android.calendar", "com.android.calculator2"
        )
        for (pkg in preferred) {
            if (result.size >= 6) break
            val app = byPackage[pkg] ?: continue
            if (result.any { it.packageName == pkg }) continue
            result.add(IconEntry(pkg, app.label))
        }
        for (app in installed) {
            if (result.size >= 6) break
            if (result.any { it.packageName == app.packageName }) continue
            result.add(IconEntry(app.packageName, app.label))
        }
        return result
    }

    /**
     * 从悬浮窗启动应用。若遇到部分 ROM 的「后台弹出界面」限制，
     * 退回到 PendingIntent.send()，兼容性更好。
     * 未安装或入口不存在时返回 false，由调用方提示，不抛异常。
     */
    fun launch(packageName: String, component: String? = null): Boolean {
        val intent = buildLaunchIntent(packageName, component) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            try {
                PendingIntent.getActivity(
                    context,
                    packageName.hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ).send()
                true
            } catch (t2: Throwable) {
                false
            }
        }
    }

    /** 优先使用显式组件启动，其次退回包的默认入口。 */
    private fun buildLaunchIntent(packageName: String, component: String?): Intent? {
        val spec = component?.trim().orEmpty()
        if (spec.isNotEmpty()) {
            val slash = spec.indexOf('/')
            val pkg = if (slash > 0) spec.substring(0, slash) else packageName
            val raw = if (slash > 0) spec.substring(slash + 1) else spec
            val cls = if (raw.startsWith(".")) pkg + raw else raw
            if (pkg.isNotEmpty() && cls.isNotEmpty()) {
                return Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(android.content.ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        }
        return try {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun Drawable.toImageBitmap(): ImageBitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
        val width = intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
        val height = intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        return bitmap.asImageBitmap()
    }
}
