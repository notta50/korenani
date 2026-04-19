# Research & Design Decisions

---
**Purpose**: 設計判断の根拠となる調査記録・アーキテクチャ評価・リスクを記録する。

---

## Summary
- **Feature**: `litert-lm-engine`
- **Discovery Scope**: Complex Integration（既存 llama.cpp + JNI スタックを LiteRT-LM SDK に完全置換）
- **Key Findings**:
  - LiteRT-LM Kotlin SDK は `com.google.ai.edge.litertlm:litertlm-android` として Maven 公開済み（Kotlin Stable）
  - `Content.ImageFile` はファイルパスを必要とし Bitmap は直接渡せないため、Bitmap → 一時 JPEG 変換が必須
  - GPU 初期化失敗（OpenCL 非対応デバイス）は例外キャッチ → CPU フォールバックで対処する

## Research Log

### LiteRT-LM API 調査

- **Context**: llama.cpp JNI を LiteRT-LM SDK に置き換えるにあたり、正確な API シグネチャと制約を把握する必要がある
- **Sources Consulted**:
  - https://ai.google.dev/edge/litert-lm/android
  - https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
  - https://mvnrepository.com/artifact/com.google.ai.edge.litertlm/litertlm-android
- **Findings**:
  - Maven 座標: `com.google.ai.edge.litertlm:litertlm-android:latest.release`（`latest.release` で最新版を取得可能）
  - 初期化: `EngineConfig(modelPath, Backend.GPU())` → `Engine.create(config)` → `engine.initialize()`（10 秒以上ブロック → `Dispatchers.IO` 必須）
  - Conversation: `engine.createConversation(ConversationConfig(...))` で作成
  - マルチモーダル: `Contents.of(Content.ImageFile(path), Content.Text(prompt))` を `conversation.sendMessageAsync()` に渡す
  - ストリーミング: `sendMessageAsync()` は Kotlin Coroutine Flow に対応
  - GPU: `Backend.GPU()` (OpenCL/OpenGL 経由)、失敗時は `Backend.CPU()` にフォールバック
  - 既知の問題: v0.10.0 で GPU decode に不具合あり、v0.10.1 で修正済み
  - Bitmap 非対応: `Content.ImageFile` はファイルパスのみ受け付ける（Bitmap は直接渡せない）
- **Implications**:
  - `LiteRtLmEngine` 内で GPU 初期化を try/catch し、失敗時に CPU で再試行するパターンを採用
  - `InferenceRepositoryImpl` で Bitmap → JPEG 一時ファイル変換ロジックを担う
  - 実装時は `latest.release` の具体バージョンを確認し、GPU 不具合バージョン（0.10.0）を避ける

### 既存コードベース分析

- **Context**: 移行対象の既存インターフェース・実装パターンを把握する
- **Findings**:
  - `InferenceRepository` インターフェース: `initialize(modelPath, mmprojPath)`, `infer(bitmap, prompt): Flow<String>`, `release()`
  - `ModelRepository` インターフェース: `isModelReady()`, `downloadModels(): Flow<DownloadState>`, `getModelPath()`, `getMmprojPath()`
  - `DownloadState`: `Progress(percent, label)`, `Finished`, `Failed(error)` のシールドクラス
  - 現 ModelConfig: Gemma 3 GGUF URL 2 本（model.gguf + mmproj.gguf）
  - 現 build.gradle.kts: `externalNativeBuild`（CMakeLists.txt 参照）+ `ndk` ブロック（NDK r30）
  - 既存パターン: Flow ストリーミング、`Dispatchers.IO`、OkHttp Range Resume、Bitmap 336px リサイズ
- **Implications**:
  - インターフェース・DownloadState は一切変更しない
  - `mmprojPath` 引数は `initialize()` で受け取るが LiteRT-LM では不使用（単一ファイル）
  - `getMmprojPath()` は空文字 `""` を返す（インターフェース互換維持）
  - 既存の Bitmap リサイズ・OkHttp Resume パターンはそのまま維持する

## Architecture Pattern Evaluation

| オプション | 説明 | 強み | リスク / 制限 | 備考 |
|---|---|---|---|---|
| LiteRT-LM SDK を直接採用（採用） | `LiteRtLmEngine` ラッパーで SDK をラップし Repository から分離 | GPU 推論 52 tok/s・676 MB、NDK/JNI 不要 | GPU 対応はデバイス依存 | 本スペックの主目的 |
| llama.cpp 継続 + GPU 対応 | llama.cpp の OpenCL バックエンドを有効化 | 既存資産維持 | ビルド複雑性は解消しない、GPU 対応が不安定 | 却下 |
| 両方共存（A/B） | llama.cpp と LiteRT-LM を実行時切り替え | リスク分散 | 維持コスト倍増、本スペックの目的外 | 却下 |

## Design Decisions

### Decision: `LiteRtLmEngine` を独立クラスとして実装

- **Context**: GPU/CPU フォールバックロジックと Conversation ライフサイクル管理を Repository から分離する必要がある
- **Alternatives Considered**:
  1. InferenceRepositoryImpl 内に SDK 呼び出しを直接埋め込む
  2. 専用 `LiteRtLmEngine` クラスを作成してラップする（採用）
- **Selected Approach**: `LiteRtLmEngine` クラスとして独立させ、`InferenceRepositoryImpl` から依存注入する
- **Rationale**: Engine ライフサイクル（initialize/release）・バックエンド選択・Conversation 管理の責務を Repository から分離することでテスト容易性が向上する
- **Trade-offs**: クラスが 1 つ増えるが、現在の JNI 相当の責務分離を維持しテスト可能性が向上する
- **Follow-up**: 実装時に SDK の正確な API シグネチャを公式ドキュメントで確認する

### Decision: Bitmap → 一時ファイル変換は InferenceRepositoryImpl で担う

- **Context**: `Content.ImageFile` はファイルパスを要求するが、既存の `infer(bitmap, prompt)` インターフェースは Bitmap を渡す
- **Selected Approach**: `InferenceRepositoryImpl.infer()` で Bitmap をリサイズ後 JPEG として `cacheDir` に保存し、パスを `LiteRtLmEngine.infer()` に渡す
- **Rationale**: `LiteRtLmEngine` を Android `Context` から切り離せるため、Engine 層のテスト容易性が高まる
- **Trade-offs**: Repository に一時ファイル管理ロジックが入るが、量は少なく許容範囲内
- **Follow-up**: `finally` ブロックで確実にファイル削除されることをユニットテストで検証する

### Synthesis: 設計シンプル化の決定

- **Generalization**: 推論ストリーミングパターン（Flow emit）は既存実装と同一。新規抽象化不要。
- **Build vs Adopt**: LiteRT-LM SDK を採用（ゼロから構築しない）。OkHttp ダウンロードも既存実装を継続採用。
- **Simplification**: `mmprojPath` 引数は削除せずシグネチャ維持（インターフェース互換）。ModelConfig の MMPROJ 定数は空文字定数として維持（getMmprojPath() の戻り値として参照可能）。

## Risks & Mitigations

- GPU 対応がデバイス依存 — CPU フォールバックで全デバイス対応を保証する
- LiteRT-LM SDK の正確な API シグネチャが実装時に変わる可能性 — `LiteRtLmEngine` のコメントに「exact API は公式ドキュメント要確認」と明示する
- GPU decode の既知バグ (v0.10.0) — `latest.release` 使用時は 0.10.1 以降を確認する
- `.litertlm` モデルが 2.58 GB のため初回ダウンロードに時間がかかる — 既存の Resume ダウンロードで対応済み
- `engine.initialize()` が 10 秒以上ブロック — `Dispatchers.IO` で実行・`ModelLoading` AppState で UI をブロッキング表示する

## References
- [LiteRT-LM Android Getting Started](https://ai.google.dev/edge/litert-lm/android) — SDK 初期化・API リファレンス
- [LiteRT-LM GitHub](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) — Kotlin API 詳細
- [Maven Repository](https://mvnrepository.com/artifact/com.google.ai.edge.litertlm/litertlm-android) — 最新バージョン確認
- [Gemma 4 E2B on HuggingFace](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) — モデルファイル URL 確認
