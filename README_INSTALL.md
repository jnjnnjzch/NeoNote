# NeoNote Install Guide / NeoNote 安装指南

## Recommended package (current)

The only recommended package for current testing is:

`NeoNote-debug-installable-*.apk`

## EN

1. Install `NeoNote-debug-installable-*.apk`.
2. Do not install `*UNSIGNED-NOT-INSTALLABLE*.apk`.
3. Do not install `.aab` directly.
4. If GitHub download is a zip artifact, extract it first.
5. If Android reports package invalid, run:
   `adb install -r <apk>`
   and copy the exact `INSTALL_*` error.
6. If app seems not updated, check Home build badge or Settings Build Info:
   versionName, versionCode, gitSha, githubRunNumber.

## 中文

1. 当前只建议安装：`NeoNote-debug-installable-*.apk`
2. 不要安装：`*UNSIGNED-NOT-INSTALLABLE*.apk`
3. `.aab` 不能直接安装到安卓设备。
4. 如果下载的是 GitHub 的 zip 产物，请先解压再安装 APK。
5. 如果系统提示“软件包似乎无效”，请执行：
   `adb install -r <apk>`
   并提供完整 `INSTALL_*` 错误。
6. 如果看起来没有更新，请查看首页 Build Badge 或 Settings Build Info：
   versionName、versionCode、gitSha、githubRunNumber。

## Package/signature rules

- Debug APK can update only debug APK with same applicationId and signature.
- Release APK can update only release APK with same applicationId and signature.
- Switching between debug and release may require uninstall first.

Current applicationId:
- debug: `com.example.cahier`
- release: `com.example.cahier`
