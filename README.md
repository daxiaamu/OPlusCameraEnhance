# OPlus Camera Enhance

[![License: GPL v3 or later](https://img.shields.io/badge/License-GPL%20v3%2B-blue.svg?style=flat-square)](LICENSE)

[![Version](https://img.shields.io/badge/version-0.3.10-007EC6?style=flat-square)](app/build.gradle.kts)
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


让原本不支持的一加、真我及移植相应系统的设备，也能使用 **OPPO 哈苏磁吸闪光灯**。

## 功能

- **开机弹窗**：闪光灯开机后，显示原生发现与连接弹窗。
- **碰一碰互联**：支持通过闪光灯 NFC 标签触发连接。
- **原生相机控制**：支持自动闪光、手动闪光、闪光强度调节，以及常亮模式的亮度、色温调节。
- **完整面板交互**：修复闪光灯菜单展开、收起及控件显示异常。
- **原生支持保护**：已支持闪光灯的 OPPO 机型自动跳过增强。
- **双 UI**：可切换 Compose 与 ColorOS 官方组件界面，系统界面跟随主题色。

## 支持范围

面向以下官方系统进行适配：

| 系统 | 当前状态 |
| --- | --- |
| **ColorOS 16** | 一加 15 已实机验证；原生已支持闪光灯的设备自动跳过增强 |
| **OxygenOS 16** | 待验证，不能保证当前版本可用 |
| **realme UI 7.0** | GT8 Pro ROM 静态检查通过，已移除品牌限制，待真机验证 |

不限制设备品牌，小米等设备的移植系统也可尝试；须保留兼容的原生相机与设备空间，具体兼容性取决于系统和应用实现。

## 使用

1. 使用上述系统（含保留兼容组件的移植版），并安装支持现代 Xposed API 102 的框架。
2. 安装、启用模块，允许自动添加「相机」「设备空间」作用域。
3. 打开「强制支持磁吸闪光灯」，按界面提示重启目标应用；首次启用后如互联未生效，可重启手机。
4. 打开一次原生相机并等待初始化完成，再连接闪光灯。

支持简体中文、繁体中文（港澳台）、英语、日语和韩语。

[反馈问题](https://github.com/daxiaamu/OPlusCameraEnhance/issues)

## 许可证

本项目原创代码采用 [GNU 通用公共许可证第 3 版或（由你选择）任何更高版本](LICENSE)（SPDX：`GPL-3.0-or-later`）。第三方依赖遵循各自的许可证。
