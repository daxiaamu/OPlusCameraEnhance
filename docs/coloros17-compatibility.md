# ColorOS 17 native camera compatibility

Target verified on 2026-10-08: OnePlus 15 / PLK110_17.0.0.102(CN01), Android API 37.

- Camera: 7.006.100 / 60000, SHA-256 `6284a1924ed1997ed9f3bf5b6e7c0af0c8d8af4bfe2be9b4c9eef144a344e760`.
- MyDevices: 17.25.10 / 1725010, SHA-256 `d9cb014fc6ee52f947fa35f327e807c89cac77e299e36205813e4f25b9d9c68a`.

## Failure and change

Camera 7 removed the `expandAnim, debug, mbUseSharedIcon:` string used to locate menu expansion. Module 0.3.10 consequently reported `unsupported` even though the feature property and discovery entry still existed.

The resolver retains the Camera 6 lookup. Only when it has no candidate does it require both Camera 7 recovery messages in one non-static, zero-argument void method. Collapse/spacing must remain in that same class. Existing shared-call, animation, View field, resource-presence and visible-panel guards remain in force. Ambiguous or incomplete mappings still fail closed. The cache schema changes to `flash-ui-v3`.

No camera class or obfuscated method names are hardcoded by this change. Native-support protection and MyDevices property handling remain unchanged. This branch contains native enhancement only, not the universal compatibility experiment.

## Validation

- Direct DEX structural probe: Camera 7.006.100 passes; Camera 6.070.215 and 6.070.192 still pass. MyDevices APK fails the camera probe as expected.
- Native support policy: 10 tests pass.
- Release build: R8 and resource shrinking enabled; assembleRelease succeeds.
- Installed local test build: 0.3.11 / 19, signed with the same certificate as the previous installed release.
- Device-side resolver cache identifies expansion `H()`, collapse `G(boolean,boolean)`, siblings `m0(boolean)`, icon `n0(boolean)`, spacing `l0(boolean,boolean)` and View field `P` on CameraMenuContainerExpandTopSingle. The actual Android/DexKit lookup agrees with the offline probe.
- Current-boot receipts report camera `compatible` and MyDevices `injected`, both from module code 19; LSPosed logs confirm enhancement enabled.
- User functional retest: connection succeeded after power-cycling the flash; external-flash menu expansion/collapse, button interaction and actual photo flash confirmed normal. Cold boot, long-idle recovery and every shooting mode were not tested.

## Offline probe

Use JDK 17 or newer and a local JADX all JAR containing dexlib2:

```text
java -cp <jadx-all.jar> scripts/StaticHookProbe.java <Camera.apk>
```

The probe reads DEX references directly; it does not emulate Android class loading or replace device testing. An unrelated class in Camera 7 has the same collapse log message, so the probe and runtime resolver constrain collapse and spacing by the expansion owner rather than assuming global uniqueness.

## Accessory connection retest

The first attempt displayed Connecting and then Connection failed. Bluetooth diagnostics recorded an approximately 30-second connection timeout and bonding returning to NONE; the system displayed a PIN/key error. This was not counted as a successful functional test.

After the user power-cycled the flash and was asked to disconnect other phones, the user confirmed connection success. The subsequent Bluetooth dump showed OPPO-FLASH bonded, LE ACL connected, and encryption active with a 16-byte key. No additional Hook, Bluetooth reset, or pairing-record deletion was performed between these attempts. The precise initial pairing-failure cause is unproven.
