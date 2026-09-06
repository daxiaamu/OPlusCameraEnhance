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


提供 Compose / Android 系统组件双界面的相机增强模块，目前支持 OPPO 哈苏磁吸闪光灯。

## 使用

需要支持现代 Xposed API 102 的框架。安装并启用模块，打开模块界面，自动同步「相机」和「设备空间」作用域。首次授权仍由框架处理。

功能开关同时控制相机和设备空间，设置同步至框架远程存储，不要求模块界面保持运行。切换后页面持续提示强行停止并重新打开目标应用，收到本次设置的回执后消失。

已原生支持的设备显示「已原生支持，无需增强」，禁用开关并在 Hook 入口跳过全部修改。读取失败或非一加设备同样跳过。

界面按「功能」「关于」「界面样式」分组；功能组包含「强制支持磁吸闪光灯」开关和「信息」，关于组包含版本检查及源码入口。支持英语、简体中文、港澳台繁体中文、日语、韩语，跟随系统，也支持 Android 应用语言设置。

## 界面切换

0.3.5：移除功能卡中的冗长介绍；同组设置项之间复用设置 APK 的分割线 Drawable 与缩进资源，组尾不绘制。

0.3.4 支持 Compose / ColorOS 官方控件切换。ColorOS 模式只从当前安装的系统设置 APK 加载 COUI 类、布局、主题和分组接口，不复制控件代码或仿写圆角配色。类加载与模块隔离，布局资源使用设置 APK 的 Resources；应用文案先解析为字符串以避免两个 APK 的资源 ID 冲突。

“检查更新”使用设置 APK 的右侧 Assignment 文本布局；颜色直接读取当前小布助手 SettingsActivity 主题下的 selector_nx_summary_color 状态列表。通过包查询读取系统应用，未新增 Xposed 作用域。系统 APK 更新后读取其新版资源和实现；官方内部接口不保证跨版本稳定，加载不兼容时提示并退回 Compose。

两台设备已通过官方开关、偏好布局和三按钮弹窗回调测试。一加还通过小布颜色状态（正常/禁用）验证；最后一轮 OPPO 离线，颜色断言尚未补跑。更新网络端到端验证仍待发布更新清单。

    ./gradlew assembleDebugAndroidTest -PinstrumentationRunner=com.daxiaamu.opluscameraenhance.OfficialUiChecks
    adb shell am instrument -w com.daxiaamu.opluscameraenhance.test/com.daxiaamu.opluscameraenhance.OfficialUiChecks

UI 复用和具体 Hook 点见 [实现说明](docs/implementation.md)。

## 适配与状态

- 能力键：ro.oplus.camera.out.flash.support；只修改目标进程中此键的返回值。
- 在应用初始化前安装能力 Hook，避免缓存原始不支持结果。
- DexKit 2.2.0 根据字符串、调用关系、参数和字段类型定位面板动画修复；不依赖混淆类名。
- 0.3.2：DexKit 定位闪光灯发现入口及连接记录查询，仅在普通 BLE 发现、已配对但未连接的调用内解除已配对跳过弹窗判断；保留原生拒绝频率、连接状态和游戏/全屏限制，不删除配对记录。原生支持设备和关闭增强时均不启用此修复。
- DEX 缓存绑定固件、相机版本、更新时间及 APK 文件身份。
- 适配失败安全跳过 UI 修复，并显示相机版本供反馈。适配检测通过不等于未来版本的全部硬件功能已实测。
- 注入显示交叉检查框架实时目标进程与回执；回执绑定模块版本、目标 APK 版本/更新时间、开机次数和配置修订号。
- 模块重新打开时通过签名权限保护的广播刷新回执。状态提供者只接受相机、设备空间和本模块调用，不允许外部写入功能开关。

## 验证记录（2026-09-06）

- 一加 15 / 相机 6.070.215：现代 API Hook 被实际读取、DexKit 适配通过，用户确认手动蓝牙连接可用。
- 本轮出现碰一碰无弹窗；完成设置存储与启动时序修复后，按用户要求重启一加，用户确认碰一碰已连接成功，系统 BLE 链路也确认已连接。无需因此声称所有设备都不需要首次重启。
- 现代 API 版本连接后已复核：闪光灯面板的关闭、开启、常亮可见，自动按钮与强度滑条显示正常；常亮亮度、色温控件可见。已恢复闪光模式。所有拍摄模式、横屏及未来相机版本未做全面硬件回归。
- 0.3.2 开机发现：用户确认只开启闪光灯电源即可弹窗，实机捕获 OutFlashDiscoveryDialogActivity；点击连接后显示“已连接 OPPO 磁吸闪光灯”和电量 44%。首次更新后须让相机完成启动以注册扫描服务。
- OPPO Find X9 Pro：原生保护界面与禁用开关已检查；未进行 OPPO 目标进程注入（该设备无可用 root 框架）。
- 七种语言/地区配置均已在 OPPO 实机检查，最后恢复跟随系统语言。
- assembleDebug、lintDebug 通过。
- 原生保护 JUnit 10 项、Android 更新元数据/策略检查 12 项、发布脚本离线检查 4 项通过。

## 更新

源码入口：https://github.com/daxiaamu/OPlusCameraEnhance

当前客户端使用 stable 渠道。更新采用唯一 GitHub 权威指针、七个元数据来源、不可变清单哈希、revision 防回滚和冲突检查。APK 必须有至少五个不同 CDN 主机，并在安装前重新校验 SHA-256、包名、versionCode 与签名连续性。

普通更新提供跳过、稍后、下载与安装；强制边界优先于跳过。自动与手动检查共享运行中的会话，手动检查重新联网。界面支持本地时间和安全 Markdown。

.github/workflows/publish-update.yml 仅在已发布 Release 后运行，不创建 Release。脚本从唯一 APK 读取版本，校验五个 CDN 字节一致性，再生成不可变清单和指针。每次发布须人工递增 update/policy-stable.json 的 policyRevision，强制边界不得自动修改。预发布工作流输出 beta 指针，但当前 stable 客户端不接收 beta。

源码与发布工作流已就绪；尚未创建 Release 或发布更新清单，线上更新下载安装尚未做端到端验证。

## 构建与测试

JDK 17+、Android SDK 37 / Build Tools 36.0.0：

    ./gradlew assembleDebug lintDebug
    ./gradlew assembleDebugAndroidTest
    adb shell am instrument -w com.daxiaamu.opluscameraenhance.test/com.daxiaamu.opluscameraenhance.UpdateChecks
    python scripts/test_publish_update.py
    powershell -File scripts/test-policy.ps1

本机 Gradle 单元测试 worker 缓存存在环境问题；原生保护测试用 test-policy.ps1 直接执行同一组 JUnit 测试。

### 0.3.6 界面调整

- 移除打开相机入口；相机、设备空间的注入状态与版本、适配结果和必要的重启提示集中在说明项正文。两套界面共用说明内容。
- 查看源码使用当前系统设置 APK 的 coui_preference_widget_jump 官方右侧入口组件。
- 一加实机确认说明正文与官方箭头正常，两个目标已注入、相机适配通过；assembleDebug、lintDebug 通过。

### 0.3.7 系统主题色

- 加载官方 COUIThemeOverlay，在创建组件之前应用系统当前主题；检查更新沿用小布官方颜色状态列表并应用对应主题覆盖层。
- 返回系统界面时检测配置变化并重建主题。
- 一加绿色主题与小布检查更新实机对比通过；官方开关与更新文字当前主题颜色一致、禁用状态和对话框检查通过。assembleDebug、lintDebug 通过。

### 0.3.8 触摸修复

- 官方开关由整行设置项处理点击，避免开关消费触摸事件而无响应。
- ADB 触摸验证两套界面往返切换、磁吸闪光灯开关关闭与恢复开启通过；恢复后相机和设备空间已注入。保留 0.3.7 系统主题色支持。
