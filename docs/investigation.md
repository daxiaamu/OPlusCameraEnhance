# 实机分析记录 — 2026-09-06

以下为按时间保留的早期分析记录，当前实现以 [实现说明](implementation.md) 和源码为准。

OPPO 存在 ro.oplus.camera.out.flash.support=1，一加缺少此键。
一加相机 r7.k0.W() 首次读取后缓存为 Boolean。必须在相机新进程中注入。

一加相机包含：
- com.oplus.camera.bluetooth.BlueToothScanService：OplusOutFlashServiceId，服务 UUID 0000079a-0000-1000-8000-00805f9b34fb，服务数据首字节 0xf7。
- v9.a.i(BluetoothDevice)：厂商蓝牙类别 16187590 或名称包含 OPPO-FLASH。
- y9.m（OutFlashGattDevice）：GATT 服务 fff0，通知 fff2/fff5/fff4，时间同步 fff3。
- y9.m.l()：同步时钟、更新时钟、等待心跳后才标记同步完成。
- com.oplus.ocs.camera.CameraParameter：OUT_FLASH_CONNECTED、HALL_STATUS、UI_STATUS、FLASH_STRENGTH、TORCH_STRENGTH、TORCH_COLOR_TEMPERATURE。

OPPO 连接对照日志确认 OPPO-FLASH 和 fff2/fff5/fff4 通知订阅。用户确认拍照成功。
OPPO 的 com.heytap.mydevices 会查询 AppFeature oplus.camera.out.flash.support；这与相机读取的 ro 属性是不同入口，可能影响配件页面，尚未修改。


配件框架版本：OPPO 17.6.5，一加 16.35.0。JADX 默认 KotlinMetadata 插件对配件 APK 大量报错；关闭 kotlin-metadata 后只剩 30 处反编译错误。不得将反编译失败当作功能不存在的证据。

待验证：hook 实际读取、蓝牙连接、补光亮度和色温、前后摄拍照同步、断连重连、禁用模块恢复。
## 0.1.2 实机进展

- 初版仅 hook android.os.SystemProperties，已注入但未命中实际属性读取。
- 加入 com.oplus.wrapper.os.SystemProperties 后，21:21:57 已记录实际属性命中，21:21:58 相机订阅 fff2/fff5/fff4 通知。
- 0.1.1 临时诊断的 f36949i 是 JADX 自动重命名字段，运行时不存在；该诊断已在 0.1.2 完全删除。当前不依赖任何相机混淆类名、方法名或字段名。
- 设备空间：一加 17.4.10，OPPO 17.23.5。一加 APPFeatureUtils.r(Context) 调用 p(Context, "oplus.camera.out.flash.support", "ro.oplus.camera.out.flash.support")，返回 AppFeature 查询值 OR 系统属性 > 0。
- OplusBluetoothAdapterWrapper.e(BluetoothDevice) 对 OPPO-FLASH 要求 R() 为真；R() 的惰性缓存来自 APPFeatureUtils.r(Context)。作用域变更后必须重启设备空间。
- 新版作用域为相机和设备空间；磁吸自动互联尚待用户配合验证，不等同于已订阅相机 GATT。
## 0.1.5：磁吸闪光灯 UI 修复

用户已确认连接、拍照及前一步联动验证成功。随后复现点击闪光灯仅展开背景、没有模式按钮与滑条的问题。

当前相机 6.070.215 的 CameraMenuContainerExpandTopSingle.B() 在 W0=1（子面板显示）时，仅处理 pref_camera_flashmode_key，漏掉 pref_app_outflash_key；布局已创建，模式列表和闪光灯内容仍为 alpha=0。A(boolean,boolean) 的收起分支也缺少相应处理。

FlashUiFix 仅匹配该相机版本和 pref_app_outflash_key 的 on/torch 状态。调用原生 P/Q/O 方法恢复图标和边距过渡，并补齐列表与子面板的淡入淡出。不会伪造连接状态或修改滑条数值。属性启用部分仍不依赖相机混淆名；UI 修复使用经过真机验证的类及成员映射，其他版本跳过。

实机检查：
- 后置人像：首次展开能看到关闭、开启、常亮和强度滑条。
- 切换常亮：亮度、色温滑条显示，拖动后滑块位置与色温图标反馈正常。
- 收起、重新展开：顶部图标位置复原，常亮面板再次可见。
- 前置人像：关闭菜单可展开，开启后自动按钮及强度滑条可见。
- 已返回后置相机。
- assembleDebug / lintDebug 通过；临时 UiDiagnostics 已删除。

此轮未验证所有拍摄模式、横屏、旋转和未来相机版本。