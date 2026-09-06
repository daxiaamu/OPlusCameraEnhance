# OPlus Camera Enhance

[![Version](https://img.shields.io/badge/version-0.3.8-007EC6?style=flat-square)](app/build.gradle.kts)
[![Android](https://img.shields.io/badge/Android-15%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](app/build.gradle.kts)
[![OnePlus](https://img.shields.io/badge/OnePlus-ColorOS-F5010C?style=flat-square&logo=oneplus&logoColor=white)](#使用)
[![Xposed](https://img.shields.io/badge/Xposed-API%20102-7952B3?style=flat-square)](app/src/main/resources/META-INF/xposed/scope.list)
[![Native protection](https://img.shields.io/badge/原生支持-自动跳过-2E7D32?style=flat-square)](app/src/main/java/com/daxiaamu/opluscameraenhance/NativeSupportPolicy.java)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](build.gradle.kts)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](app/src/main/java/com/daxiaamu/opluscameraenhance/MainActivity.kt)
[![COUI](https://img.shields.io/badge/UI-ColorOS%20COUI-00A862?style=flat-square)](app/src/main/java/com/daxiaamu/opluscameraenhance/ColorOsRuntime.kt)
[![DexKit](https://img.shields.io/badge/DexKit-2.2.0-FF6F00?style=flat-square)](app/src/main/java/com/daxiaamu/opluscameraenhance/DexUiResolver.kt)
[![Languages](https://img.shields.io/badge/语言-简繁中%20%C2%B7%20EN%20%C2%B7%20日本語%20%C2%B7%20한국어-546E7A?style=flat-square)](app/src/main/res/xml/locales_config.xml)

[![Stars](https://img.shields.io/github/stars/daxiaamu/OPlusCameraEnhance?style=flat-square&logo=github)](https://github.com/daxiaamu/OPlusCameraEnhance/stargazers)
[![Forks](https://img.shields.io/github/forks/daxiaamu/OPlusCameraEnhance?style=flat-square&logo=github)](https://github.com/daxiaamu/OPlusCameraEnhance/forks)
[![Issues](https://img.shields.io/github/issues/daxiaamu/OPlusCameraEnhance?style=flat-square)](https://github.com/daxiaamu/OPlusCameraEnhance/issues)
[![Last commit](https://img.shields.io/github/last-commit/daxiaamu/OPlusCameraEnhance?style=flat-square)](https://github.com/daxiaamu/OPlusCameraEnhance/commits/main/)
[![Repository size](https://img.shields.io/github/repo-size/daxiaamu/OPlusCameraEnhance?style=flat-square)](https://github.com/daxiaamu/OPlusCameraEnhance)


让原本不支持的一加手机，也能使用 **OPPO 哈苏磁吸闪光灯**。

## 功能

- **开机弹窗**：闪光灯开机后，显示原生发现与连接弹窗。
- **碰一碰互联**：支持通过闪光灯 NFC 标签触发连接。
- **原生相机控制**：支持自动闪光、手动闪光、闪光强度调节，以及常亮模式的亮度、色温调节。
- **完整面板交互**：修复闪光灯菜单展开、收起及控件显示异常。
- **原生支持保护**：已支持闪光灯的 OPPO 机型自动跳过增强。
- **双 UI**：可切换 Compose 与 ColorOS 官方组件界面，系统界面跟随主题色。

目前已在一加 15 上验证。真我暂未适配；其他机型和相机版本以实际适配结果为准。

## 使用

1. 使用官方系统，并安装支持现代 Xposed API 102 的框架。
2. 安装、启用模块，允许自动添加「相机」「设备空间」作用域。
3. 打开「强制支持磁吸闪光灯」，按界面提示重启目标应用；首次启用后如互联未生效，可重启手机。
4. 打开一次原生相机并等待初始化完成，再连接闪光灯。

支持简体中文、繁体中文（港澳台）、英语、日语和韩语。

[实现与 Hook 说明](docs/implementation.md) · [反馈问题](https://github.com/daxiaamu/OPlusCameraEnhance/issues)
