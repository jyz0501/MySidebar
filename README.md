# 轻边栏 (mySidebar)

一个轻量、无广告、不上网的 Android 悬浮侧边栏。从屏幕边缘轻点或滑动即可唤出一条可自定义的图标栏，快速启动常用应用。

## 功能特性

- **边缘手势唤出**：屏幕侧边一条常驻手势条，轻点或向内滑动即展开侧边栏；点击空白处收起。
- **六向停靠**：左/右 × 上/中/下共六种停靠位置，侧边栏与手势条同步移动。
- **外观自定义**：宽度、高度、整体透明度、划出动画速度均可调，在真实悬浮层上实时预览。
- **手势可调**：触发灵敏度、触发区高度独立调节。
- **应用管理**：添加 / 删除 / 锁定 / 排序应用快捷方式；锁定项不可删除或拖动（默认锁定若干系统/内置应用，未安装的自动跳过）。
- **开机自启动**：通过 `BOOT_COMPLETED` 广播恢复悬浮窗服务，并提供跳转系统自启动设置的入口。
- **前台/后台切换**：总开关一键启停；可暂停唤出（保留服务，通知栏提供「收起」操作）。
- **滑出音效**：展开时播放一段极短的卡通式「冒出」音（程序合成，无音频资源）。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- 悬浮窗服务（`OverlayService`）+ 前台服务通知
- 配置本地持久化（`DataStore`），导出/导入的内部 JSON 编解码保留
- `minSdk = 26`，`targetSdk = 34`，`compileSdk = 34`
- Release 构建开启 R8 代码压缩与资源压缩（APK 约 1.1 MB）

## 构建

```bash
./gradlew assembleRelease          # 生成签名 release 包
./gradlew bumpVersion              # 递增版本号（major*1000 + minor*100 + patch）
```

### 签名

仓库不包含签名文件。复制 `keystore.properties.example` 为 `keystore.properties` 并填写：

```
STORE_FILE=keystore/mysidebar.jks
STORE_PASSWORD=***
KEY_ALIAS=mysidebar
KEY_PASSWORD=***
```

若缺少该文件，release 构建会回退到 debug 签名，便于覆盖安装保留配置数据。

## 权限说明

- **悬浮窗（SYSTEM_ALERT_WINDOW）**：必需，侧边栏与手势条显示在其它应用之上。
- **前台服务 / 通知**：常驻服务需要，通知栏常驻图标用于状态提示与收起操作。
- **开机自启动**：`BOOT_COMPLETED` 广播（需同时到系统设置中允许自启动/后台运行）。
- **后台弹出界面**：部分国产 ROM 限制从后台启动 Activity，需在系统设置中允许，否则点击图标仅提示。

## 权限与隐私

不联网、无广告、不收集任何数据；所有配置仅保存在本机。

## 目录概览

```
app/src/main/java/com/qianxian/MySidebar/
├── MainActivity.kt                启动与设置宿主界面
├── service/
│   ├── OverlayService.kt          悬浮窗服务、手势条、面板、动作分发、音效触发
│   └── ServiceController.kt       启停与重启服务
├── data/
│   ├── SidebarSettings.kt         配置模型与取值范围
│   ├── AppRepository.kt           已安装应用、图标、默认与锁定项、启动
│   └── SettingsRepository.kt      配置读写与默认值补齐
├── ui/
│   ├── sidebar/                   悬浮侧边栏（SidebarRoot/Panel/Window）
│   └── settings/                  设置页（外观/手势/图标/通用）
└── util/                          Permissions、SidebarSound 等
```
