# Android Teleprompter（提词器）

面向专业拍摄设备与直视口播场景的 **纯中文离线 AI 实时语音跟读 Android 提词器**，采用 **Material Design 3 (MD3 / M3 Expressive)** 设计语言打造。

## 核心特性

- **纯离线中文语音跟读**：内置 `Sherpa-onnx` 中文流式 Zipformer 语音识别 + 无声调模糊拼音局部对齐引擎，支持语速自适应、精准锚点与备用匀速三种模式，断网片场依然稳定跟读。
- **防串行、防乱跳与平滑滚屏**：帧同步阻尼平滑追赶，支持上方保留已读行数（0 / 1 / 2 行）、暂停自由拖动、NG 重念自动回退及点击任意文字跳转。
- **专为远机位与强棚灯优化的排版控制**：
  - **字号调节（32~80 sp）**：横屏 / 竖屏独立记忆，支持底部悬浮条 **`A- / A+` 快捷微调** 与 **台词区双指捏合实时缩放**；
  - **行距四档调节**：`1.2x` / `1.4x` / `1.6x` / `1.8x` 一键切换；
  - **正文字重开关**：支持常规体 / 加粗切换，大幅提升远距离清晰度；
  - **安全边距调节**：横竖屏独立记忆，收窄视线范围防眼神飘移。
- **Material Design 3 Expressive 视觉与交互**：
  - 台词区恒为 OLED 纯黑 `#000000`（防止反光灰雾），控制面板采用 MD3 Tonal Surface 色阶容器；
  - 底部拆分式 **FloatingToolbar + 平滑变形主控 FAB**；
  - `28dp` 圆角分组设置抽屉，内置 **4 套 MD3 主题强调色**（专业琥珀金 / 莫奈动态蓝 / 护眼森林青 / M3 经典紫）；
  - 方案 C「镜头取景框」纯矢量自适应图标，完整支持 Android 13+ Material You 莫奈单色图标。

## 设计预览

- UI 交互预览：[`docs/md3_ui_preview.html`](docs/md3_ui_preview.html)
- 图标方案预览：[`docs/app_icon_preview.html`](docs/app_icon_preview.html)

## 本地编译准备（离线模型未入库）

由于 `encoder.int8.onnx`（153 MB）超过 GitHub 单文件 100 MB 限制，模型文件与 `sherpa-onnx` AAR 已在 `.gitignore` 中排除。本地编译前请准备：

1. 将 `sherpa-onnx-2.0.1.aar` 放入 `app/libs/`
2. 将中文流式 Zipformer ONNX 模型文件（`encoder.int8.onnx`、`decoder.onnx`、`joiner.int8.onnx`，以及已入库的 `tokens.txt`）放入 `app/src/main/assets/asr_zh/`

```bash
./gradlew testDebugUnitTest assembleDebug
```

## 致谢与第三方开源组件

- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0) — 离线流式语音识别引擎
- [Jetpack Compose & Material 3](https://developer.android.com/compose) (Apache-2.0) — Android 声明式 UI 框架
- [pinyin4j](https://github.com/belerweb/pinyin4j) (GPL-2.0) — 汉字转拼音工具

## 开源协议

本项目基于 [GNU General Public License v3.0 (GPL-3.0)](LICENSE) 协议开源。

Copyright © 2026 **YukokuSuzuko**.
