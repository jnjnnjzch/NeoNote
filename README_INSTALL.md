# NeoNote Install Guide / 安装指南

## EN

1. For testing, install the file named:
   `NeoNote-debug-installable-*.apk`
2. Do **not** install `.aab` directly.
3. Do **not** install unsigned release APK:
   `NeoNote-release-unsigned-NOT-INSTALLABLE-*.apk`
4. If GitHub gives you a zip artifact, extract it first.
5. If Android says package invalid, run:
   `adb install -r <apk>`  
   Then copy the exact `INSTALL_*` error.
6. If app does not update, open **Settings** and check Build Info:
   versionName, versionCode, gitSha, githubRunNumber.

Package/signature rules:
- Debug APK can update only debug APK with same applicationId/signature.
- Release APK can update only release APK with same applicationId/signature.
- Switching between debug/release may require uninstall first.

Current app IDs:
- debug applicationId: `com.example.cahier`
- release applicationId: `com.example.cahier`
- They are intentionally the same package name; signature decides update compatibility.

## 中文

1. 测试请安装以下命名文件：
   `NeoNote-debug-installable-*.apk`
2. **不要**直接安装 `.aab`。
3. **不要**安装未签名 release APK：
   `NeoNote-release-unsigned-NOT-INSTALLABLE-*.apk`
4. 如果 GitHub 下载下来是 zip 产物，请先解压再安装。
5. 如果安卓提示“软件包似乎无效”，请执行：
   `adb install -r <apk>`  
   并把完整 `INSTALL_*` 错误信息复制出来。
6. 如果看起来没有更新，请打开 **Settings** 查看 Build Info：
   versionName、versionCode、gitSha、githubRunNumber。

包名/签名更新规则：
- debug APK 只能覆盖“相同 applicationId + 相同签名”的 debug APK。
- release APK 只能覆盖“相同 applicationId + 相同签名”的 release APK。
- debug 与 release 互相切换时，可能需要先卸载旧版本。

当前 applicationId：
- debug：`com.example.cahier`
- release：`com.example.cahier`
- 两者包名相同，能否覆盖安装取决于签名是否一致。
