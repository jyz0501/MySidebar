import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

/** 三段版本号，每段 0-9；逐级进位，9.9.9 为上限。 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) {
    val versionName: String get() = "$major.$minor.$patch"
    val versionCode: Int get() = major * 1000 + minor * 100 + patch

    fun next(): AppVersion = when {
        major == 9 && minor == 9 && patch == 9 ->
            throw GradleException("版本号已达到上限 9.9.9，无法继续递增")
        patch < 9 -> copy(patch = patch + 1)
        minor < 9 -> copy(minor = minor + 1, patch = 0)
        else -> copy(major = major + 1, minor = 0, patch = 0)
    }
}

fun loadAppVersion(file: File): AppVersion {
    if (!file.isFile) {
        throw GradleException("缺少版本文件：${file.absolutePath}")
    }
    val props = Properties().apply { file.inputStream().use { load(it) } }

    fun part(name: String): Int {
        val raw = props.getProperty(name)?.trim()
            ?: throw GradleException("version.properties 缺少 $name")
        val value = raw.toIntOrNull()
            ?: throw GradleException("version.properties 的 $name 不是整数：$raw")
        if (value !in 0..9) {
            throw GradleException("version.properties 的 $name 必须在 0..9 之间：$raw")
        }
        return value
    }

    return AppVersion(part("major"), part("minor"), part("patch"))
}

/**
 * 手动递增版本：仅显式运行 `./gradlew bumpVersion` 才改变版本，
 * 不挂到 assemble，也不依赖版本控制工具。
 */
tasks.register("bumpVersion") {
    group = "versioning"
    description = "按 0-9 进位规则递增应用版本（手动运行，不与构建绑定）"

    doLast {
        val file = rootProject.file("version.properties")
        val current = loadAppVersion(file)
        val next = current.next()
        val content = buildString {
            appendLine("# 应用版本单一真源：三段各 0-9，逐级进位")
            appendLine("# versionCode = major*1000 + minor*100 + patch")
            appendLine("# 仅由 ./gradlew bumpVersion 递增，不与构建绑定")
            appendLine("major=${next.major}")
            appendLine("minor=${next.minor}")
            appendLine("patch=${next.patch}")
        }
        val temp = File(file.parentFile, "version.properties.tmp")
        temp.writeText(content, Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        logger.lifecycle(
            "版本已递增：${current.versionName} (${current.versionCode}) → " +
                "${next.versionName} (${next.versionCode})"
        )
    }
}
