# NeoNote 安装与功能简报（中文）

1. 我现在应该安装哪个文件？  
- 请安装：`NeoNote-debug-installable-*.apk`  
- 这是当前 CI 明确标记为可直接安装的测试包。

2. unsigned release APK 为什么不能装？  
- 因为它是未签名 release 包，属于预期不可直接安装类型。  
- 文件名里已经标了：`UNSIGNED-NOT-INSTALLABLE`。  
- 这个包应被当作构建中间产物，而不是用户安装包。

3. debug APK 到底有没有 NeoNote 功能？  
- 有核心功能入口：绘图、表格编辑、快捷键、导出、压力/调试信息、Settings 中 Build Info。  
- 但 UI 仍有较强 Cahier 基底外观，容易让人感觉“像旧版本”。

4. debug APK 为什么看起来还是 0.1.10？  
- 高概率是安装了旧 artifact 或打开了旧安装包。  
- 现在版本号逻辑已改为 CI 动态版本（tag + sha + run number）。  
- 请在 Settings 的 Build Info 中核对 `versionName/versionCode/gitSha/githubRunNumber`。

5. 下一步最小修复任务是什么？  
- P0 最小闭环：  
  1) 只保留并强调 `NeoNote-debug-installable-*.apk` 为推荐安装文件；  
  2) 在发布说明里显式写“unsigned release 与 aab 不可直接安装”；  
  3) 增加一个自动校验，若命名/分类不符则 CI 直接失败；  
  4) 让 artifact inventory 报告补齐每个文件的完整字段（版本、签名、sha、安装性结论）。
