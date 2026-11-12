package com.qianxian.MySidebar.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置的 JSON 编解码。使用平台内置 org.json，避免引入序列化编译器插件，
 * 保持应用体积轻量；读取时对缺失/非法字段一律回落到默认值。
 */
object ConfigExporter {

    fun encode(s: SidebarSettings): String {
        val icons = JSONArray()
        s.icons.forEach {
            icons.put(
                JSONObject()
                    .put("packageName", it.packageName)
                    .put("label", it.label)
                    .put("locked", it.locked)
                    .put("component", it.component ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("masterEnabled", s.masterEnabled)
            .put("paused", s.paused)
            .put("side", s.side.name)
            .put("anchor", s.anchor.name)
            .put("panelWidthDp", s.panelWidthDp)
            .put("panelHeightPercent", s.panelHeightPercent)
            .put("panelAlpha", s.panelAlpha.toDouble())
            .put("animationDurationMs", s.animationDurationMs)
            .put("edgeSensitivityDp", s.edgeSensitivityDp)
            .put("triggerHeightPercent", s.triggerHeightPercent)
            .put("autoStart", s.autoStart)
            .put("icons", icons)
            .toString()
    }

    fun decode(raw: String?): SidebarSettings {
        if (raw.isNullOrBlank()) return SidebarSettings()
        return try {
            val o = JSONObject(raw)
            val icons = ArrayList<IconEntry>()
            val arr = o.optJSONArray("icons")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val pkg = item.optString("packageName", "")
                    if (pkg.isBlank()) continue
                    icons.add(
                        IconEntry(
                            packageName = pkg,
                            label = item.optString("label", ""),
                            locked = item.optBoolean("locked", false),
                            component = item.optString("component", "").ifBlank { null }
                        )
                    )
                }
            }
            SidebarSettings(
                masterEnabled = o.optBoolean("masterEnabled", true),
                paused = o.optBoolean("paused", false),
                side = if (o.optString("side", Side.LEFT.name)
                        .equals(Side.RIGHT.name, ignoreCase = true)
                ) Side.RIGHT else Side.LEFT,
                anchor = VerticalAnchor.values().firstOrNull {
                    it.name.equals(o.optString("anchor", ""), ignoreCase = true)
                } ?: VerticalAnchor.CENTER,
                panelWidthDp = o.optInt("panelWidthDp", 56),
                panelHeightPercent = o.optInt("panelHeightPercent", 45),
                panelAlpha = o.optDouble("panelAlpha", 0.92).toFloat(),
                animationDurationMs = o.optInt("animationDurationMs", 280),
                edgeSensitivityDp = o.optInt("edgeSensitivityDp", 16),
                triggerHeightPercent = o.optInt("triggerHeightPercent", 65),
                autoStart = o.optBoolean("autoStart", true),
                icons = icons
            ).sanitized()
        } catch (t: Throwable) {
            SidebarSettings()
        }
    }
}
