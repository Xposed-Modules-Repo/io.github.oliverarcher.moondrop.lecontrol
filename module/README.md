# MOONDROP LE Control

**让 MOONDROP 官方 Android App 在 LE Audio 连接期间继续管理支持 GAIA BLE 的水月雨耳机。** 修正 LE Audio 与 App 管理连接路径的兼容问题，并恢复 LC3 状态查询。

> 本模块不修改或替换官方 MOONDROP App。它通过 Xposed hook 官方 App 的连接选择和 GAIA GATT 写入队列。

## 功能

- 当目标耳机地址与系统当前 LE Audio 输出设备匹配时，让官方 App 使用已有的 BLE/GAIA 管理通道。
- 按 GAIA GATT 特征的写入属性修正写入类型，避免内部请求队列错序。
- 不按耳机名称或型号设置白名单；兼容性取决于耳机是否提供 App 可用的 GAIA BLE 管理服务。

## 兼容性

| 环境 | 状态 |
| --- | --- |
| Samsung SM-F9710，Android 17 / One UI 9，MOONDROP Dots | 已实测：双耳 LE Audio 保持活动，官方 App 可管理耳机；GAIA 返回 LC3 开启，App 原生回调确认 `lc3 = true` |
| 其他 Android 12+ 设备，具备 LE Audio 支持且运行兼容版本的官方 MOONDROP App | 设计上可适用，尚未逐机验证 |
| 其他水月雨耳机 | 不限型号名；需其管理固件与官方 App 暴露兼容的 GAIA BLE 服务，尚未逐型号验证 |

实际兼容性还取决于手机蓝牙栈、耳机固件及官方 App 版本。已验证的手机仅为上表列出的三星设备；“其他 Android”不代表已确认支持。

## 要求

- Android 12 / API 31 或更高版本，并且手机、系统和耳机均支持并已建立 LE Audio 连接。
- 官方 App：`com.moondroplab.moondrop.moondrop_app`。
- LSPosed 或兼容 Xposed API 82 的框架；仅将本模块作用域设为官方 MOONDROP App。
- 安装或更新模块后，重新启动官方 App 即可；不需要重启手机。

本模块不启用 LE Audio、不切换音频 codec，也不提供耳机协议本身不具备的功能。它修复的是官方 App 的 BLE 管理连接与 GATT 写入队列。

## 安装

1. 安装发布页提供的模块 APK。
2. 在 LSPosed 中启用 **MOONDROP LE Control**，作用域仅选择官方 MOONDROP App。
3. 重新启动官方 App，并在耳机通过 LE Audio 连接时打开设备管理页面。

## 与其他项目的关系

[MoonEar（原 FxxkMoondrop）](https://github.com/bqj6666/FxxkMoondrop) 是面向水月雨耳机的独立助手，提供 GAIA BLE 控制、Fast Pair 集成和多种耳机功能。本模块针对官方 MOONDROP App 在 LE Audio 活跃时的管理兼容问题；两者功能有交集，但目标流程不同。若同时启用涉及同一耳机连接或 GAIA 通道的功能，可能互相影响，建议逐个启用排查。

[PuddingPods](https://modules.lsposed.org/module/rongyi.puddingpods/) 面向小米 HyperOS 上的水月雨 PUDDING 控制场景，目标和平台范围不同。

## Release

See [release notes](RELEASE_NOTES-0.7.md) for version 0.7. The module is verified on one Samsung Android 17 device with MOONDROP Dots; support for other phones and models remains unverified.

## 隐私与限制

模块只在官方 MOONDROP App 进程内运行，不发起网络请求，不读取或更改官方 App 的账号、社区数据，也不修改官方 APK。模块需要 Xposed 框架将 hook 注入该 App。

公开版不记录 GAIA 原始收发报文。

模块依赖官方 App 的 Flutter 接口及 GAIA SDK 内部实现。官方 App 更新后，内部类或协议变化可能导致模块失效，需要重新适配。其他耳机型号、Android 厂商系统、主动切换 codec、固件升级及长期稳定性尚未全面测试。

## 构建

`build.ps1` 当前使用开发者本机 Android SDK/JDK 路径。发布 APK 使用开发者的长期受控密钥签名。不要提交签名密钥、密码文件或构建目录。

## 版本

当前版本：**0.7**（versionCode 7）。

## License

尚未指定。公开源代码前请在源码仓库明确许可证；提交到 Xposed-Modules-Repo 的索引仓库不应直接放入源码，按其提交说明提供 README 与发布 APK。