# Research Log: gemma4-camera-viewer

## Discovery Scope

複雑な新規フィーチャー（グリーンフィールド）のためフルディスカバリーを実施。
llama.cpp Android JNI統合、Gemma 4 E2B GGUFマルチモーダル、CameraX、大容量ファイルダウンロードの4領域を調査。

---

## 調査結果

### 1. llama.cpp Android JNI API

**出典**: ggml-org/llama.cpp `examples/llama.android`、mtmd.h、HuggingFace Gemma 4ブログ

- 公式Androidサンプル（`examples/llama.android`）はテキスト専用。マルチモーダルJNI関数は含まれていないため、カスタム実装が必要
- マルチモーダルは `tools/mtmd/mtmd.h`（libmtmd）で提供される。旧 `llava.h` / `clip.h` アプローチは廃止
- **Gemma 4固有の重要制約**:
  - `image_min_tokens = 0` 必須（設定するとアサーション失敗）
  - `mtmd_decode_use_mrope()` が `true` を返す → デコードループでM-RoPE 3Dポジション設定が必須
  - Gemma 4サポートはApril 2025以降のllama.cppコミットから
- 2ファイル構成: テキストモデルGGUF + `mmproj-BF16.gguf`（ビジョンエンコーダ）
- BF16 mmprojはAndroid CPUでネイティブ非対応だが、`GGML_CPU_ALL_VARIANTS=ON` でエミュレーション自動処理

### 2. CameraX API

**出典**: Android Developers公式ドキュメント、CameraX 1.4/1.5リリースノート

- CameraX 1.5でJetpack Compose nativeサポートが安定版に（`camera-compose` アーティファクト）
- `CameraXViewfinder(surfaceRequest)` でAndroidView不要のネイティブCompose実装可能
- `ImageProxy.toBitmap()`（CameraX 1.3+）が最も安全なBitmap変換手段
- **重要**: `OnImageCapturedCallback` 内で必ず `imageProxy.close()` を呼ぶ。呼ばないとカメラパイプラインがブロックされる

### 3. 大容量ファイルダウンロード（OkHttp vs DownloadManager）

**出典**: Android開発者コミュニティ、Stack Overflow

- 3GBファイルの場合、OkHttp + `Range: bytes=N-` ヘッダーによるレジュームが推奨
- `DownloadManager` はAndroid 10+で7日後にファイルを削除する可能性があり不適
- `response.body.bytes()` は絶対禁止（OOMクラッシュ）。ストリーミング読み取り必須
- `contentLength()` が `-1` を返すケースへの対応が必要（chunked transfer encoding）
- ファイルサイズが大きいためForeground ServiceまたはWorkManagerでのラップを検討

### 4. Gemma 4 E2B GGUFモデルファイル

**出典**: HuggingFace `unsloth/gemma-4-E2B-it-GGUF`、`google/gemma-4-E2B-it` モデルカード

- Q4_K_M GGUF: 約2.5GB（メインモデル）
- mmproj-BF16: 約300MB（ビジョンエンコーダ）
- 合計約2.8GBのストレージ必要
- 1画像あたり約768トークンを消費（SigLIP 14×14パッチ）→ `nCtx = 4096` が最低必要

---

## 設計決定

### D1: 推論フレームワーク選択

**決定**: GGUF + llama.cpp（libmtmd）を採用

**比較した選択肢**:
| フレームワーク | 状況 | 採用/不採用 |
|--------------|------|-----------|
| llama.cpp + libmtmd | Gemma 4 E2B GGUF公開済み、マルチモーダル対応 | **採用** |
| MediaPipe LLM Inference | Gemma 4未対応（Gemma 3まで） | 不採用 |
| LiteRT (Google公式) | アクセス制限中（gated） | 不採用 |
| MLC-LLM | Gemma 4 E2B動作未確認 | 不採用 |

**理由**: 今すぐGemma 4 E2Bマルチモーダルが動作する唯一の実用的選択肢

### D2: CameraXバージョン選択

**決定**: CameraX 1.5.x（camera-compose）を採用

**理由**: Jetpack Compose nativeサポートが安定版。`AndroidView` ラッパー不要でコードがシンプルになる

### D3: ダウンロード実装選択

**決定**: OkHttp + Flow<DownloadState> を採用

**理由**: レジューム制御・進捗Flow・filesDir保存の組み合わせで最もKotlin-friendly

### D4: シングルViewModelアーキテクチャ

**決定**: ViewModelを1つに統合（分割しない）

**理由**: アプリの状態がシンプル（ダウンロード or 推論の排他的フロー）。複数ViewModelへの分割はオーバーエンジニアリング

### D5: 推論毎のKVキャッシュリセット

**決定**: 推論ごとにコンテキストをクリアし、会話履歴を保持しない

**理由**: 要件5.2。検証目的のアプリであり、累積コンテキストによるメモリ枯渇を避ける

---

## リスクと対策

| リスク | 重要度 | 対策 |
|--------|-------|------|
| mmproj URLが未確定 | 高 | 実装フェーズでUnslothリポジトリのファイル一覧を確認し `ModelConfig` に記載 |
| llama.cpp API変更 | 中 | llama.cppをgitサブモジュールで固定バージョン管理 |
| Pixel 10以外の端末でのクラッシュ | 低 | minSdk=33・arm64-v8a限定・RAM要件をREADMEに明記 |
| 初回DL中のプロセス死 | 中 | OkHttpのレジューム機能で再開可能 |
| M-RoPEポジション実装ミス | 高 | llama.cppのGemma 4テスト事例を参照して実装・動作確認必須 |
