# Third-Party Open-Source Notices & Model Provenance

Android Teleprompter is licensed under the **GNU General Public License v3.0 (GPL-3.0)** (see [`LICENSE`](LICENSE)).  
This document records the third-party software libraries and speech model assets distributed with the application APK.

---

## 1. Software Dependencies

### k2-fsa/sherpa-onnx (`2.0.1`)
- **Project**: https://github.com/k2-fsa/sherpa-onnx
- **Artifact**: `sherpa-onnx-2.0.1.aar`  
  SHA-256: `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`
- **License**: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
- **Copyright**: Copyright (c) Xiaomi Corporation / k2-fsa contributors.

### Microsoft ONNX Runtime (bundled via `sherpa-onnx`)
- **Project**: https://github.com/microsoft/onnxruntime
- **Included native library**: `libonnxruntime.so` (arm64-v8a)
- **License**: MIT License
- **Copyright**: Copyright (c) Microsoft Corporation.

### AndroidX, Jetpack Compose & Material 3
- **Project**: https://developer.android.com/jetpack
- **Artifacts**: `androidx.core`, `androidx.activity`, `androidx.lifecycle`, `androidx.compose.*` (BOM `2026.03.01`)
- **License**: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
- **Copyright**: Copyright (c) The Android Open Source Project.

### pinyin4j (`com.belerweb:pinyin4j:2.5.1`)
- **Project**: https://github.com/belerweb/pinyin4j (originating from https://sourceforge.net/projects/pinyin4j/)
- **License Note**:
  - The upstream Maven POM for `com.belerweb:pinyin4j:2.5.1` declares **BSD** (`http://opensource.org/licenses/bsd-license.html`).
  - Upstream Java source file headers under `net.sourceforge.pinyin4j.*` declare **GNU General Public License v2 or later (GPL-2.0-or-later)**.
  - The JAR also bundles `com.hp.hpl.sparta` (Sparta XML parser), originally released under **LGPL-2.1**.
  - All of the above are compatible with this project's **GNU GPL-3.0** license.

---

## 2. Offline Chinese Streaming ASR Model

The offline speech recognition feature uses the pre-trained Chinese streaming Zipformer2 (int8) model distributed by the k2-fsa/sherpa-onnx project:

- **Model Release**: `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30`
- **Official Archive URL**:  
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2`  
  Archive SHA-256: `5a2832047ea1f97dd0dc595b816c230c4bafad65cfc0341fa57517cadc50afd0`
- **Hugging Face Mirror**: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30
- **Pinned Model Files & SHA-256**:
  - `encoder.int8.onnx` (`161,141,793` bytes): `5ac51e27981bb4dab01bb9be4958453ba50c3b61c063ddda0eab23fd3671aa4f`
  - `decoder.onnx` (`5,165,083` bytes): `06522ad63cec0fdf6809f4e1db9bb4f7d710c34582e3b35db62ac60eccafac7e`
  - `joiner.int8.onnx` (`1,033,416` bytes): `b34584dc6f561089e1d747fedebb3765f2caa72c927ef54d7ca55e5ae40a814b`
  - `tokens.txt` (`18,626` bytes LF in Git): `6722bd1585f46f84456b29c3550a343a3cc375b971645773c02ed8e0b4e2405c`
- **Model License & Redistribution Notice**:
  - Although the `sherpa-onnx` inference engine is Apache-2.0, this specific model repository does **not** include a standalone model `LICENSE` file.
  - Its upstream checkpoint (`yuekai/icefall-asr-multi-zh-hans-zipformer-large`) is access-restricted on Hugging Face and was trained via `k2-fsa/icefall/egs/multi_zh-hans` on multiple Chinese speech corpora (including AISHELL-1/2/4, WenetSpeech, AliMeeting, MagicData, KeSpeech), some of which carry **non-commercial or academic-only** dataset license terms.
  - Accordingly, this model is **not** represented as Apache-2.0. Anyone redistributing pre-built APKs containing these weights—especially for commercial use—must independently verify compliance with the upstream model and dataset terms, or substitute a model whose license suits their distribution needs.
