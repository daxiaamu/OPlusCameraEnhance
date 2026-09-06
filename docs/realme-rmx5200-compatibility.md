# 真我 GT8 Pro（RMX5200）静态兼容性检查

后续变更：0.3.9 已移除品牌限制，以下为移除前的静态检查记录；真机验证仍待完成。

检查 ROM：RealmeUI RMX5200_16.0.10.501(CN01)，A.73。日期：2026-09-07。

## 结论

现有 Hook 的静态适配度高，但当前品牌保护只允许 OnePlus，尚未放开真我，也未进行真我硬件验证。

- 设备空间 com.heytap.mydevices 17.4.10：与已验证的一加 APK 的 SHA-256 完全一致。
- 相机 com.oplus.camera 6.070.215：APK 与一加不同，但使用当前 DexKit 方法匹配规则对真实 ROM APK 进行只读解析，全部目标方法唯一命中。
- 系统设置 com.android.settings 16.1.0：所需分组、设置项、assignment、源码箭头、分割线资源存在；小布颜色 selector_nx_summary_color 存在。尚未在真我系统中运行加载验证。

## DexKit 唯一命中的方法

```
Lcom/oplus/camera/ui/newsetting/container/CameraMenuContainerExpandTopSingle;->B()V
Lcom/oplus/camera/ui/newsetting/container/CameraMenuContainerExpandTopSingle;->A(ZZ)V
Lcom/oplus/camera/ui/newsetting/container/CameraMenuContainerExpandTopSingle;->O(ZZ)V
Lcom/oplus/camera/ui/newsetting/container/CameraMenuContainerExpandTopSingle;->P(Z)V
Lcom/oplus/camera/ui/newsetting/container/CameraMenuContainerExpandTopSingle;->Q(Z)V
Lt6/x;->d(ILandroid/app/Activity;Ljava/lang/String;Z)V
Lsa/p;->d(Ljava/lang/String;Ljava/lang/String;)Z
```

展开方法仍只处理内置闪光灯的相应分支，符合当前 UI 修复的前提。蓝牙发现入口保留已配对设备跳过弹窗的分支，与当前开机弹窗修复针对的逻辑一致。

设备空间 APPFeatureUtils.r(Context) 使用 AppFeature 与 ro.oplus.camera.out.flash.support 属性组合判断能力；蓝牙适配器的 isSupportedFlash 缓存使用该结果。

## APK SHA-256

- 相机：EFFF8224C25A405E22182E8BCD7AE4DCD31730371C7A35693C000C7912777DDD
- 设备空间：E0CD2DDB48836E0C1A88F5CF5D34BFAFCCCE4D953EE76C820A51C1EC6AE93A35
- 系统设置：E18CA66C9B7D47C21978368050103607EFC8BFDD50B0D29C8058F1E2C08B635F

## 尚待验证

品牌保护调整需保持原生支持设备自动跳过，并核对真机实际 Build.BRAND / MANUFACTURER。APK 方法定位通过不能证明蓝牙权限、NFC 路由、相机 HAL 或曝光同步的运行行为。还需验证开机弹窗、碰一碰、自动/手动闪光、强度及常亮色温、拍照同步、断连重连和官方 UI 加载。

本次只读解析真我 APK，未安装到一加替换系统应用，未执行真我目标 Hook；未修改产品代码或对外宣称已支持真我。
