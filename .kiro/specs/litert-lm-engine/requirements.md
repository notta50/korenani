# Requirements Document

## Introduction
Google AI Edge LiteRT-LM SDK を使用して、既存の llama.cpp + JNI 推論スタックを完全に置き換え、
Gemma 4 E2B マルチモーダルモデル（`.litertlm` 形式）をオンデバイスで推論する。
GPU バックエンドを優先した高速・低メモリ推論を実現し、
カメラ撮影画像に対する日本語説明テキストをストリーミング出力するアプリ「korenani?」のエンジンを刷新する。

## Boundary Context
- **In scope**:
  - Gemma 4 E2B `.litertlm` 単一ファイルのダウンロードと存在確認
  - LiteRT-LM `Engine` の初期化（GPU 優先、CPU フォールバック）
  - カメラ撮影画像 + プロンプトによるマルチモーダル推論（ストリーミング）
  - llama.cpp/JNI/CMake/NDK に関わるビルド成果物の完全削除
  - `InferenceRepository` インターフェースの維持（ViewModel 層への影響なし）
- **Out of scope**:
  - UI・カメラ操作・AppState・MainViewModel の変更
  - ダウンロード UI・プログレス表示の変更
  - OkHttp による HTTP ダウンロード実装の変更
  - モデルの量子化・変換処理
- **Adjacent expectations**:
  - `DownloadState`・`ModelRepository` インターフェースは変更しない
  - `MainViewModel.onCapture()` が呼び出す `InferenceRepository.infer()` のシグネチャは不変
  - `InferenceRepository.initialize(modelPath, mmprojPath)` のシグネチャは保持（mmprojPath は LiteRT-LM では使用されないが引数として受け取る）

## Requirements

### 1. モデルファイル管理
**Objective:** アプリユーザーとして、Gemma 4 E2B モデルを端末にダウンロードし、以降のセッションで再ダウンロードなしに利用したい。

#### Acceptance Criteria
1. The korenani app shall manage a single model file (`gemma-4-E2B-it.litertlm`) stored in the application's private files directory.
2. When the app checks model readiness, the korenani app shall return ready only when the `.litertlm` file exists and has a non-zero size.
3. When a download is initiated, the korenani app shall emit Progress状態（ダウンロード済みバイト数を全体サイズで割ったパーセンテージ）を定期的に更新し続ける。
4. When a download completes successfully, the korenani app shall emit `DownloadState.Finished` を一度だけ出力する。
5. If a network error or HTTP error occurs during download, the korenani app shall emit `DownloadState.Failed` とエラー内容を含めて出力する。
6. When a previous download was partially completed, the korenani app shall resume from the interrupted byte position using HTTP Range requests.
7. The korenani app shall provide the stored model file path to the inference engine upon initialization request.

### 2. 推論エンジン初期化
**Objective:** アプリユーザーとして、モデルが速やかに使用可能な状態になり、カメラ撮影後すぐに解析を開始できるようにしたい。

#### Acceptance Criteria
1. When `initialize` is called with a valid model path, the korenani app shall GPU バックエンドを優先してエンジンを初期化する。
2. If GPU backend initialization fails, the korenani app shall CPU バックエンドにフォールバックしてエンジン初期化を完了する。
3. When engine initialization succeeds, the korenani app shall 推論可能な状態（`infer` を受け付けられる状態）に遷移する。
4. If engine initialization fails on all available backends, the korenani app shall エラーを例外としてスローし、呼び出し元に伝播する。
5. When `release` is called, the korenani app shall エンジンリソースを解放し、以降の `infer` 呼び出しが不正な状態を引き起こさないようにする。

### 3. マルチモーダル推論（画像 + テキスト）
**Objective:** アプリユーザーとして、撮影した画像の内容をモデルが解析し、日本語テキストとして段階的に表示されるようにしたい。

#### Acceptance Criteria
1. When `infer(bitmap, prompt)` is called, the korenani app shall 画像を一時ファイルとして保存した後、画像とプロンプトをエンジンに送信する。
2. While inference is running, the korenani app shall 生成されたトークンを受信次第 `Flow<String>` として逐次 emit し続ける。
3. When the model signals end-of-generation, the korenani app shall `Flow` を正常完了させる。
4. If the inference engine returns an error during processing, the korenani app shall `Flow` を例外で終了させ、呼び出し元がエラー状態に遷移できるようにする。
5. The korenani app shall 画像の前処理（リサイズ等）を行い、エンジンが要求するサイズ範囲内の画像を渡す。
6. When inference completes or fails, the korenani app shall 一時保存した画像ファイルを削除する。

### 4. ビルドシステム整理
**Objective:** 開発者として、NDK/CMake/JNI に起因するビルドの複雑さを排除し、標準 Gradle ビルドで完結するようにしたい。

#### Acceptance Criteria
1. The korenani app shall LiteRT-LM SDK を Gradle 依存として宣言するだけで、NDK・CMake・JNI ブリッジなしにビルドできる。
2. The korenani app shall `app/src/main/cpp/` ディレクトリ（JNI ソースおよびサブモジュール参照）を含まないビルド構成となる。
3. When the project is built from a clean state, the korenani app shall カスタム C++ コンパイルステップなしに APK が生成される。

### 5. インターフェース互換性
**Objective:** 開発者として、推論エンジンの置き換えにより ViewModel 層や UI 層を変更しなくて済むようにしたい。

#### Acceptance Criteria
1. The korenani app shall `InferenceRepository` インターフェース（`initialize`, `infer`, `release` の各メソッドシグネチャ）を変更しない。
2. The korenani app shall `DownloadState`（`Progress`, `Finished`, `Failed`）の定義と意味を変更しない。
3. When `initialize(modelPath, mmprojPath)` is called, the korenani app shall `mmprojPath` 引数を受け取るが、LiteRT-LM が単一ファイルで完結するため内部では使用しない。
4. The korenani app shall `ModelRepository` インターフェース（`isModelReady`, `downloadModels`, `getModelPath`, `getMmprojPath`）の各メソッドシグネチャを変更しない。
