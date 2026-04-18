# Brief: litert-lm-engine

## Problem
現在の llama.cpp + カスタム JNI 実装では以下の問題が発生している：
- `mtmd_helper_eval_chunks` が数分〜無限にブロックする（CPU のみ、GPU 未使用）
- メモリ使用量が約 5 GB に達し Android デバイスで OOM が頻発する
- NDK/CMake ビルドが複雑で、バックエンドロードやページサイズ対応など多くのデバッグコストが発生している
- Gemma 3 世代モデルを使用しており、Gemma 4 ネイティブサポートがない

## Current State
- `app/src/main/cpp/` に llama.cpp サブモジュール + llama_jni.cpp（JNI ブリッジ）
- `engine/LlamaEngine.kt` が JNI 宣言を保持
- `repository/InferenceRepositoryImpl.kt` が LlamaEngine 経由で推論
- モデル: Gemma 3 4B Q4_K_M (2.4 GB) + mmproj-F16.gguf (851 MB) の 2 ファイル
- GPU 加速なし、Decode 速度 < 1 tok/s（推定）

## Desired Outcome
- Google 公式 LiteRT-LM Kotlin SDK でオンデバイス推論が動作する
- Gemma 4 E2B (`.litertlm` 単一ファイル 2.58 GB) をダウンロード・実行できる
- GPU バックエンドを優先し、Samsung S26 Ultra 等で 52 tok/s 以上を実現する
- JNI / NDK / CMake を完全に排除し、純 Kotlin スタックとなる
- カメラ撮影 → 画像解析 → 日本語テキスト出力の E2E フローが動作する

## Approach
**完全置き換え（別ブランチ）**
llama.cpp スタックを削除し、LiteRT-LM Kotlin SDK に一本化する。
- Gradle dependency: `com.google.ai.edge.litertlm:litertlm-android:latest.release`
- `Backend.GPU()` を優先し、失敗時は `Backend.CPU()` にフォールバック
- `Content.ImageFile(tempFilePath)` + `Content.Text(prompt)` でマルチモーダル推論
- 画像は撮影後に一時ファイルとして保存してから SDK に渡す

## Scope
- **In**:
  - llama.cpp サブモジュール・JNI・CMake の完全削除
  - `LlamaEngine.kt` を削除し LiteRT-LM `Engine`/`Conversation` ラッパーに置き換え
  - `InferenceRepositoryImpl` を LiteRT-LM API に対応させる
  - `ModelConfig` を `.litertlm` ファイルの URL/ファイル名に更新
  - `ModelRepositoryImpl` を単一ファイルダウンロードに簡略化
  - `build.gradle.kts` から NDK/CMake 設定を削除し Gradle 依存に変更
  - 画像一時ファイル保存ユーティリティ（`Content.ImageFile` が path を要求するため）
- **Out**:
  - UI コンポーネントの変更（CameraPreviewSection, MainScreen 等）
  - AppState / MainViewModel のロジック変更
  - ダウンロード UI の変更
  - テスト以外のビジネスロジック変更

## Boundary Candidates
- **エンジン層**: `LlamaEngine` → LiteRT-LM `Engine` + `Conversation` ラッパークラス
- **リポジトリ層**: `InferenceRepositoryImpl` の API 呼び出し部分
- **モデル管理層**: `ModelConfig` + `ModelRepositoryImpl`（URL・ファイル数の変更）
- **ビルド設定**: `build.gradle.kts` + `CMakeLists.txt` の変更

## Out of Boundary
- カメラ操作・UI・パーミッション処理
- `AppState` 定義・`MainViewModel` ロジック
- テーマ・アイコン・アプリ名
- ネットワーク層（OkHttp ダウンロードは継続使用）

## Upstream / Downstream
- **Upstream**: HuggingFace `litert-community/gemma-4-E2B-it-litert-lm` モデルホスティング
- **Downstream**: カメラ撮影フロー（`onCapture` → `infer()` インターフェースは不変）
- **Interface boundary**: `InferenceRepository` インターフェースは変更しない（ViewModel への影響なし）

## Existing Spec Touchpoints
- **Extends**: `gemma4-camera-viewer`（同一機能の推論エンジン置き換え）
- **Adjacent**: 既存スペックのタスク 5.x〜9.x（エンジン実装部分）が本スペックで上書きされる

## Constraints
- LiteRT-LM Android SDK は Kotlin Stable。ただし `latest.release` の具体バージョンは実装時に確認が必要
- `Content.ImageFile(path)` は**ファイルパス**を要求するため、Bitmap を一時ファイルに保存する処理が必要
- GPU バックエンドはデバイス依存。初期化失敗時は CPU へのフォールバックが必要
- モデルダウンロード URL: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`
- ファイルサイズ: 2.58 GB（現在の model.gguf + mmproj.gguf 合計 3.25 GB より小さい）
- 別ブランチで作業する（`main` ブランチには影響しない）
