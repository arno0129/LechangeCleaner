# LechangeCleaner

乐橙界面净化模块，基于 LSPosed Modern API。当前包名：
`io.github.arno0129.lechangecleaner`。

## 功能

- 去除开屏广告与监控页优惠弹窗。
- 可分别隐藏产品、AI、社区底部导航，并自动均分剩余入口。
- 去除首页广告横幅及设备卡云服务入口。
- 去除“我的”页签到、广告、兑换横幅与服务推荐。
- 去除监控画面的服务栏和服务推荐页签。
- 隐藏后同步收缩布局占位；模块自身不保存或输出运行日志。
- 设置首页可直接打开乐橙。

目标应用：`com.mm.android.lc`。

## 构建

要求 JDK 17 与 Android SDK 37：

```powershell
.\gradlew.bat assembleRelease
```

版本只在 [version.properties](version.properties) 中维护，构建时自动同步到 APK 与 LSPosed `module.prop`：

```powershell
.\scripts\set-version.ps1 -VersionName 1.0.1 -VersionCode 10001
```

正式签名文件应放在 `keystore/lechangecleaner.keystore` 和
`keystore/signing.properties`，两者均被 Git 忽略。缺少私钥时 Release 会回退到 debug 签名，仅适合测试。

## 发布与 GitHub 同步

应提交：源代码、Gradle 配置与 wrapper、资源、文档、`version.properties`、GitHub Actions。

禁止提交：签名私钥和密码、`local.properties`、`.gradle/`、`build/`、APK、逆向目录、ADB 截图、设备导出文件及日志。

推荐发布流程：更新版本 → 构建与真机验证 → 提交 → 创建同名标签（如 `v1.0.1`）→ 在 GitHub Release 上传本机正式签名 APK。

## 测试声明

应用启动时会显示内部测试声明。请仅在你有权测试的设备和应用中使用，测试完成后在 24 小时内删除程序及相关安装包。

## 版权

Copyright 2026 arno0129。项目以 Apache License 2.0 发布，详情见
[LICENSE](LICENSE) 与 [NOTICE](NOTICE)。
