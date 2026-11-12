package com.qianxian.MySidebar.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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

data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean = false)

/** 已安装应用查询、应用图标内存缓存与启动逻辑。 */
class AppRepository(private val context: Context) {

    private val packageManager get() = context.packageManager
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // 遍历全部已安装应用，再逐包查其是否含 LAUNCHER 入口。
        // 关键：对每个包 intent.setPackage(pkg) 后查询，Android 视其为“显式指定包名”，
        // 不再受包可见性（Package Visibility）限制，能完整列出设备上的可启动应用，
        // 避免 queryIntentActivities 直接查询时被过滤导致数量不全、搜索不到。
        pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .filter { appInfo ->
                if (appInfo.packageName == context.packageName) return@filter false
                intent.setPackage(appInfo.packageName)
                pm.queryIntentActivities(intent, 0).isNotEmpty()
            }
            .map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (t: Throwable) {
                    appInfo.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(appInfo.packageName, label, isSystem)
            }
            .sortedWith(
                compareBy<AppInfo> { it.isSystem }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
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

    /** 出厂默认图标：仅固定锁定的三款应用，未安装的自动跳过，不预置其他应用。 */
    suspend fun defaultIconEntries(): List<IconEntry> = pinnedEntries()

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
