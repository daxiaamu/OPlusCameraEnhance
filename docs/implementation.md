# UI 复用与 Hook 点

## 模块界面

Compose 模式由模块自身的 Material 3 组件绘制。系统模式用隔离的 PathClassLoader 加载当前安装的 com.android.settings APK，通过独立 Context/Resources/Theme 实例化官方 COUIToolbar、COUISwitch、偏好项、分组标题与对话框。业务文案先解析成字符串，再交给官方 View，避免资源 ID 冲突。

系统主题通过 COUIThemeOverlay.getInstance().applyThemeOverlays(context) 应用；「检查更新」使用官方 assignment 布局，颜色取自 com.heytap.speechassist 的 selector_nx_summary_color，并应用相应官方主题覆盖层。源码箭头及分割线也来自设置资源。控件代码和资源不打包进模块；官方内部接口变更可能导致加载失败，此时回退 Compose。

UI 复用不会 Hook 设置、小布助手或 SystemUI。Xposed 作用域只有相机与设备空间。

## 能力开关：两个目标进程

目标：com.oplus.camera、com.heytap.mydevices。

在应用初始化之前拦截 android.os.SystemProperties 与 com.oplus.wrapper.os.SystemProperties 的 get/getInt/getLong/getBoolean 重载，仅当首参为 ro.oplus.camera.out.flash.support 时返回对应类型的 1/true。其他属性保持原行为，不修改真实系统属性。

设备空间现有 APPFeatureUtils 的能力检查会将 AppFeature 结果与此属性作 OR 判断，再用于闪光灯识别与 NFC 互联。模块没有直接 Hook NFC 服务或重写蓝牙协议。

## 相机面板修复

DexKit 通过以下字符串、签名与调用关系定位方法：

- expandAnim, debug, mbUseSharedIcon:：无参展开动画。
- collapseAnimFromCurrentPosition, fromCurrentPosition:：两个 boolean 参数的收起动画。
- playCheckListBarTransition, expanded:：两个 boolean 参数的列表过渡。

实际安装 after Hook 的是展开、收起两个方法；其余方法和字段是被定位并调用的辅助成员。仅在 out_flash_mode 存在且 out_flash_view_id 可见时，补齐原生图标、相邻项、间距及列表/内容透明度动画。自动/手动闪光、亮度与色温控制保留原生监听器和实现。

若展开逻辑已包含 pref_app_outflash_key，则认为官方已实现该分支，不重复修补。映射缓存绑定固件与目标 APK 身份；无法唯一定位则跳过并报告不支持。

## 相机开机发现弹窗修复

DexKit 用 OutFlashServiceListener、OUT_FLASH、intent_blue_tooth_mac_address 定位非静态 void(int, Activity, String, boolean) 发现入口，再通过 key_outflash_connected_device 定位注册表类及入口调用的静态 boolean(String, String) 已知设备查询。

两个 around Hook 配合：只有普通 BLE 发现（type=2）、设备已配对且 BluetoothDevice.isConnected() 为 false，才在当前线程/当前调用内，对同一地址的 OUT_FLASH 查询返回 false，避免因已有配对记录而跳过原生弹窗。退出调用立即恢复，实际配对记录不变。

NFC、显式连接请求、实际已连接设备等分支保持原行为。

## 回执与原生保护

两个目标还 Hook Application.attach(Context)，在原方法执行后注册受签名权限保护的刷新接收器、发送注入回执；相机进程再启动 DexKit 解析。该 Hook 用于初始化及状态反馈。

入口先读取真实能力属性。已经原生支持、非一加设备或无法可靠读取时，跳过所有 Hook。关闭增强后不安装能力/UI/发现修复，仍可保留初始化回执与适配检查。
