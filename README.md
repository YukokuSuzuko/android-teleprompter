![Android Teleprompter Banner & Adaptive Icons](docs/images/banner_icon.png)

# Android Teleprompter（提词器）

一款简洁好用的 **纯中文离线 AI 实时语音跟读 Android 提词器**，适用于日常口播、相机支架与反光镜提词场景，采用 Material Design 3 设计语言。

## 界面预览

![Android Teleprompter v1.2 UI Showcase](docs/images/ui_showcase.png)

## 核心特性

- **纯离线中文语音跟读**：内置 `Sherpa-onnx` 中文流式 Zipformer 语音识别 + 无声调模糊拼音局部对齐引擎，支持语速自适应、精准锚点与备用匀速三种模式，断网环境依然稳定跟读。
- **防串行、防乱跳与高刷平滑滚屏**：
  - 自动请求设备当前分辨率最高刷新率（90/120/144Hz），帧同步阻尼平滑追赶；
  - 支持上方保留已读行数（0 / 1 / 2 行）、动态缩放居中左侧焦点条、暂停自由拖动、NG 重念自动回退、点击任意文字跳转，以及跟读中拖动自动回位轻提示（带一键暂停）。
- **实用拍摄与反光镜适配**：
  - **镜像显示**：支持 `正常 / 左右镜像 / 上下镜像`，仅镜像正文与焦点条，控制栏与设置面板保持正向可读；
  - **横屏方向锁定**：放进提词器支架或反光镜平放时防止重力意外翻转 180°；
  - **挖孔 / 刘海避让开关**：默认避让挖孔，无挖孔全面屏可关闭实现 100% 满屏显示；
  - **硬件防误触屏幕锁**：同时短按 `音量+` 与 `音量-` 一键锁定/解锁屏幕触控（底部浮层附带 0.8 秒「长按解锁」应急兜底）；单按 `音量+` 暂停/继续、`音量-` 回退一句。
- **排版与个性化**：
  - **横竖屏独立记忆**：字号（32~80 sp，支持 `A-/A+` 与双指捏合）与左右安全边距横竖屏独立保存，支持一键恢复默认排版；
  - **本地字体导入**：支持通过系统文件选择器导入本地 `.ttf` / `.otf` 字体并一键恢复系统默认；
  - **行距四档 & 字体粗细**：`1.2x / 1.4x / 1.6x / 1.8x`、常规/加粗，均配备弹簧滑块动效。
- **Material Design 3 Expressive 视觉与交互**：
  - 台词区恒为 OLED 纯黑 `#000000`，隐藏控制栏后零遮挡（双击屏幕或点击右下角淡透图标恢复），底部贴边 `2dp` 极细进度条可开关；
  - 6 款等宽等高 3×2 MD3 主题色（琥珀金 / 莫奈蓝 / 森林青 / 经典紫 / 珊瑚橙 / 三阶黑白明度「极简白」）；
  - `MD3-C`「叠色几何对话 · 暖琥珀」自适应矢量图标，完整支持 Android 13+ Material You 单色主题图标。

## 下载安装

前往 [Releases 页面](https://github.com/YukokuSuzuko/android-teleprompter/releases/latest) 下载最新的 `android-teleprompter-v1.2-arm64.apk`（已内置离线中文语音识别模型，安装即用，支持 Android 8.0+ / arm64-v8a 设备）。

## 本地编译准备（离线模型未入库）

由于 `encoder.int8.onnx`（153 MB）超过 GitHub 单文件 100 MB 限制，模型文件与 `sherpa-onnx` AAR 已在 `.gitignore` 中排除。本地编译前请准备：

1. 将 `sherpa-onnx-2.0.1.aar` 放入 `app/libs/`
2. 将中文流式 Zipformer ONNX 模型文件（`encoder.int8.onnx`、`decoder.onnx`、`joiner.int8.onnx`，以及已入库的 `tokens.txt`）放入 `app/src/main/assets/asr_zh/`

```bash
./gradlew testDebugUnitTest assembleDebug
```

## 自动测试、签名与发布

- [Android CI](.github/workflows/android-ci.yml)：每次 PR、main 推送或手动运行时，校验并下载固定版本的 sherpa-onnx AAR，执行 `testDebugUnitTest` 和 `compileDebugKotlin`。**这不是包含语音模型的可安装 APK**；CI 不会上传或发布缺少模型的假安装包。
- [Build signed release APK](.github/workflows/signed-release.yml)：在 GitHub Actions 页面**手动触发**，下载并校验完整中文语音模型，使用**你自己的正式发布密钥**进行签名、测试和打包，验证签名与 APK 内模型文件。**默认不上传 APK**，仅执行构建与验证；如需自己试装，请在可信电脑本地打包。只有手动勾选 `upload_public_apk` 才会上传含语音模型的签名 APK。仓库公开，登录 GitHub 且能读取仓库的人可以下载该产物；7 天只是保留期限，不是访问限制。此流程**不会自动覆盖现有 v1.2 Release 或发布到商店**。
- 正式发布前，必须妥善保存独立的正式签名密钥及密码。此前的 v1.2 APK 使用 debug 签名；**首次改成正式密钥签名的 APK 通常不能覆盖安装原有 debug 签名 APK**，用户可能需要卸载旧版（会损失未备份的本地台词和设置）。请先做好数据迁移/备份方案，并在首个正式版开始长期复用同一签名密钥。

### 配置手动签名构建

在 GitHub 仓库的 Actions secrets 中设置如下内容（不要提交进 Git，也不要放到 PR 或 Issue）：

| Secret | 含义 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | **独立的正式 Android keystore 文件**，整文件 Base64 编码（不要使用 debug.keystore） |
| `ANDROID_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | keystore 中的正式签名别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |
| `MODEL_BUNDLE_URL` | 可信 HTTPS 地址，支持官方 `k2-fsa/sherpa-onnx` 的 `.tar.bz2` 模型归档（`sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2`），或自定义 `.tar.gz`（含 `encoder.int8.onnx`、`decoder.onnx`、`joiner.int8.onnx`） |
| `MODEL_BUNDLE_SHA256` | 上述归档文件的 SHA-256（64 位十六进制）；工作流还会进一步逐个校验解压后的 3 个 ONNX 与仓库内 `tokens.txt` 的固定 SHA-256，并拒绝 `CN=Android Debug` 证书签名的产物 |

`tokens.txt` 已在仓库中；`sherpa-onnx-2.0.1.aar` 从上游正式 Release 下载并核对 SHA-256。构建前的校验不通过就会停止，不会发布半成品。

本地打包正式版也需提供四个环境变量：`ANDROID_KEYSTORE_PATH`（keystore 的本地路径）、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。缺失任意变量时，`assembleRelease` 只会产生**未签名产物**，不能作为正式 APK 发布，绝不会自动使用 debug 密钥。

## 致谢与第三方开源组件

- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0，内含 MIT 许可的 ONNX Runtime) — 离线流式语音识别引擎
- [Jetpack Compose & Material 3](https://developer.android.com/compose) (Apache-2.0) — Android 声明式 UI 框架
- [pinyin4j 2.5.1](https://github.com/belerweb/pinyin4j) (Maven POM 声明 BSD；上游源码头声明 GPL-2.0-or-later，均与本项目 GPL-3.0 兼容) — 汉字转拼音工具
- 离线中文语音模型 `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30`：来源、固定 SHA-256 及数据集许可提示详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)

完整版权声明与模型再分发说明请参阅 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

## 开源协议

本项目基于 [GNU General Public License v3.0 (GPL-3.0)](LICENSE) 协议开源。

Copyright © 2026 **YukokuSuzuko**.
