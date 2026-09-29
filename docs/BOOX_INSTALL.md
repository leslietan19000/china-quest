# BOOX Nova Air C 安装与首次验收

## 本地试用版

当前推荐的 APK 在 `artifacts/ChinaQuest-0.1.1-pilot.apk`，包含家长报告导出。这是开发签名的家庭试用包，不用于公开分发。Android 8.0 (API 26) 及以上，目标 BOOX Nova Air C。

1. 将 APK 用 USB 复制到 BOOX 的 Download 文件夹。
2. 在 BOOX 文件管理器中打开 APK。若设备提示，给当前文件管理器开启“安装未知应用”权限，再点击安装。具体菜单名称随 BOOX 固件不同。
3. 打开 China Quest。家长设置自己的 6–12 位数字 PIN；没有默认 PIN。
4. 选择 Child A，准备纸笔并和家长一起完成五字课程。再切换 Child B，确认进度独立。
5. 关闭 Wi-Fi 重做上述学习流程，确认不需要登录和网络。
6. 在首页进入“家长空间”，输入 PIN，查看当天完成状态。

建议先使用 BOOX 的普通／清晰刷新模式检查汉字；具体刷新模式名称和最佳效果需实机确定。应用没有动画或自动刷新计时器。

也可使用 ADB（先由家长在 BOOX 开启 USB 调试并确认电脑授权）：

```powershell
.\.toolchain\android-sdk\platform-tools\adb.exe devices
.\.toolchain\android-sdk\platform-tools\adb.exe install -r .\artifacts\ChinaQuest-0.1.1-pilot.apk
```

`-r` 更新同签名安装以保留本地数据；不要通过卸载解决升级问题。请保留 `.toolchain/android-user/debug.keystore`，后续家庭试用更新需要同一签名。PIN 忘记时没有云找回流程，请不要清除应用数据。家长报告只供查看，并不是可恢复的完整备份；正式备份和迁移会在后续阶段实现。

## 实机检查（尚待设备）

- 新安装、首次 PIN 设置、错误 PIN 与连续错误锁定。
- 中文字形、拼音声调、较大系统字体与横竖屏没有遮挡。
- 五字学习、认字选错、朗读自评、纸笔自评、完成印章。
- 中途退出、休眠、重启、断网后进度恢复。
- 两孩子完全独立；家长可改目标和暂停新字。
- 同一天重复打开不重复奖励；没有排行或罚停机制。
- 实测一次亲子课程时长、触控可靠性和残影。

计算机模拟测试、编译和签名检查都不能代替这些实机检查。
