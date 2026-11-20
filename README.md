# Teleprompter（提词器）

纯中文离线 AI 实时语音跟读 Android 提词器（面向专业拍摄设备 + 手机直视提词场景）。

## 当前状态

- **核心工程**：已完成可编译运行的离线语音跟读 MVP（`Sherpa-onnx` 中文流式 ASR + 无声调模糊拼音局部对齐 + 平滑阻尼滚动 + 横竖屏独立字号/边距记忆）。
- **MD3 Expressive UI 预览版**：位于 [`docs/md3_ui_preview.html`](docs/md3_ui_preview.html)，包含新版 Material Design 3 界面交互原型与新增的 3 项排版能力（行距调节、正文字重加粗、快捷 `A-/A+` 与双指捏合调字号），待确认后合入安卓工程。

## 离线模型与二进制说明（未入库大文件）

由于 `encoder.int8.onnx`（153 MB）超过 GitHub 单文件 100 MB 限制，模型文件与 `sherpa-onnx` AAR 已写入 `.gitignore`。本地编译前请准备以下文件：

1. 将 `sherpa-onnx-2.0.1.aar` 放入 `app/libs/`
2. 将中文流式 Zipformer ONNX 模型文件（`encoder.int8.onnx`、`decoder.onnx`、`joiner.int8.onnx`，以及已入库的 `tokens.txt`）放入 `app/src/main/assets/asr_zh/`

## 构建

```bash
./gradlew assembleDebug
```
