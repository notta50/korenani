# 実装計画

## Task 1: Foundation — プロジェクト・ビルド基盤

- [ ] 1.1 Androidプロジェクト作成とbuild.gradle.kts設定
  - Android Studio で Kotlin / Empty Activity プロジェクトを新規作成（minSdk=33、targetSdk=35）
  - 依存追加: Jetpack Compose BOM 2025.x、camera-core/camera2/lifecycle/view/compose 1.5.x、OkHttp 4.x、kotlinx-coroutines-android
  - `ndk { abiFilters += listOf("arm64-v8a") }` と `externalNativeBuild { cmake { path; version } }` を設定
  - NDK 29 と CMake 3.31 を SDK Manager からインストール済みであることを前提とする
  - `./gradlew assembleDebug` が成功しAPKビルドが通る
  - _Requirements: なし（基盤タスク）_

- [ ] 1.2 AndroidManifest.xml権限とSDKレベル設定
  - `<uses-permission android:name="android.permission.CAMERA" />` 追加
  - `<uses-permission android:name="android.permission.INTERNET" />` 追加
  - `android:hardwareAccelerated="true"` を Application に設定
  - マニフェストに権限2件が記載されビルドが通る
  - _Requirements: 3.1, 3.3, 4.4_

- [ ] 1.3 CMakeLists.txtとllama.cpp NDKビルド設定
  - llama.cpp をプロジェクトルート配下にサブモジュールまたはローカルパスとして配置
  - `add_subdirectory(${LLAMA_CPP_ROOT} llama-build)` でllama + mtmdをビルド
  - `add_library(llama-jni SHARED llama_jni.cpp)` + `target_link_libraries(llama-jni llama mtmd common android log)`
  - ビルドフラグ: `GGML_BACKEND_DL=ON`、`GGML_CPU_ALL_VARIANTS=ON`、`GGML_OPENMP=OFF`、`LLAMA_BUILD_TESTS=OFF`、`LLAMA_BUILD_EXAMPLES=OFF`
  - CMakeビルドが成功し `libllama-jni.so` が `jniLibs/arm64-v8a/` に生成される
  - _Requirements: なし（基盤タスク）_

- [ ] 1.4 ModelConfig定数クラスの作成
  - `MODEL_URL`、`MMPROJ_URL`（HTTPS必須）を定数として定義
  - `MODEL_FILENAME = "model.gguf"`、`MMPROJ_FILENAME = "mmproj.gguf"` を定義
  - `ModelConfig` オブジェクトがビルドに含まれ、URL定数が参照可能
  - _Requirements: 3.1, 3.4_

---

## Task 2 (P): AppState定義とMainViewModel骨格

- [ ] 2.1 AppState sealed classの実装
  - 全状態を定義: `DownloadRequired`、`Downloading(progress: Int, label: String)`、`DownloadFailed(error: String)`、`ModelLoading`、`ModelReady`、`Inferencing`、`InferenceResult(text: String)`、`InferenceError(message: String)`
  - AppState sealed classがコンパイルに成功し、`when` 式でスマートキャストが機能する
  - _Requirements: 1.1, 1.2, 2.2, 3.1, 3.2, 4.2, 4.3, 4.5_
  - _Boundary: MainViewModel_

- [ ] 2.2 MainViewModelのStateFlow管理骨格実装
  - `_appState: MutableStateFlow<AppState>` と公開用 `appState: StateFlow<AppState>` の定義
  - `onAppStart()`、`onStartDownload()`、`onRetryDownload()`、`onCapture(bitmap: Bitmap)` のスタブメソッドを定義
  - Repository引数は `interface` 型のみ参照（実装未接続のスタブ）
  - `appState` を `collectAsStateWithLifecycle()` でUI側から観察できる
  - _Requirements: 1.1, 2.2, 3.1, 3.5_
  - _Boundary: MainViewModel_

---

## Task 3 (P): UI基盤 — 分割レイアウト

- [ ] 3.1 MainScreen上下分割レイアウトの実装
  - `Column` + `Modifier.weight(0.5f)` で画面を上下均等（50:50）に分割
  - 上半分は `CameraPreviewSection` のスロット（プレースホルダComposable）として定義
  - `AppState.DownloadRequired` / `Downloading` / `DownloadFailed` 時に全面ダウンロードUI（進捗バー・再試行ボタン）を表示
  - `AppState.ModelLoading` 時に全面ローディングインジケータを表示
  - アプリ起動時に画面が上下50:50に分割されて表示される
  - _Requirements: 1.1, 1.2, 3.1, 3.2_
  - _Boundary: MainScreen_

- [ ] 3.2 ResultSectionテキスト表示エリアの実装
  - `verticalScroll(rememberScrollState())` でスクロール可能なテキストエリアを実装
  - `AppState.Inferencing` 時: `CircularProgressIndicator` を表示
  - `AppState.InferenceResult(text)` 時: 日本語テキストを表示（トークン追加ごとに更新）
  - `AppState.InferenceError(message)` 時: エラーメッセージを表示
  - 各AppState値に応じてUI要素が正しく切り替わる
  - _Requirements: 1.3, 4.2, 4.3, 4.5_
  - _Boundary: ResultSection_

---

## Task 4 (P): ModelRepository — ダウンロードとキャッシュ管理

- [ ] 4.1 モデルファイル存在確認とローカルパス管理の実装
  - `context.filesDir` 内の `model.gguf` と `mmproj.gguf` の両ファイル存在チェック
  - `isModelReady(): Boolean` — 両ファイル存在時に `true`、片方でも欠損で `false`
  - `getModelPath()` / `getMmprojPath()` が `filesDir` 内の絶対パスを返す
  - 両ファイル存在時は `isModelReady() == true`、片方欠損で `false` を返す
  - _Requirements: 3.4, 5.1_
  - _Boundary: ModelRepository_

- [ ] 4.2 OkHttpレジューム対応ダウンロードとFlow<DownloadState>の実装
  - `Range: bytes=N-` ヘッダーと `FileOutputStream(file, true)` のappend modeでレジューム実装
  - `model.gguf` → `mmproj.gguf` の順次ダウンロード（`label` で区別）
  - `Flow<DownloadState>` で `Progress(percent, label)` / `Finished` / `Failed(error)` を通知
  - `contentLength() == -1` の場合は `percent = -1` で「ダウンロード中」として扱う
  - 3GBファイルのダウンロードが進捗付きで完了し、filesDir にファイルが保存される
  - _Requirements: 3.1, 3.2, 3.3, 3.5_
  - _Boundary: ModelRepository_

---

## Task 5 (P): llama.cpp JNIブリッジ基盤（テキストモデル）

- [ ] 5.1 LlamaEngine KotlinクラスとJNI宣言の実装
  - `companion object { init { System.loadLibrary("llama-jni") } }` を実装
  - テキストモデル宣言: `nativeLoad(modelPath: String): Int`、`nativePrepare(nCtx: Int, nThreads: Int): Int`、`nativeSystemInfo(): String`、`nativeGenerateNextToken(): String`、`nativeUnload()`
  - マルチモーダル宣言: `nativeLoadMmproj(mmprojPath: String): Int`、`nativeProcessImageTurn(rgbBytes: ByteArray, width: Int, height: Int, prompt: String): Int`
  - `LlamaEngine()` のインスタンス化でクラッシュなく `System.loadLibrary` が成功する
  - _Requirements: 4.1, 4.4_
  - _Boundary: LlamaEngine_

- [ ] 5.2 llama_jni.cpp テキストモデル初期化・クリーンアップ・システム情報の実装
  - グローバル変数: `g_model`（llama_model*）、`g_ctx`（llama_context*）、`g_sampler`（llama_sampler*）、`g_batch` を定義
  - `nativeLoad`: `llama_model_load_from_file()` でGGUFロード
  - `nativePrepare`: `llama_new_context_with_model()` でコンテキスト作成（`n_ctx`指定）、サンプラー設定
  - `nativeSystemInfo`: `llama_print_system_info()` の文字列を返す
  - `nativeUnload`: `llama_sampler_free`、`llama_free`、`llama_model_free` を順次呼び出し
  - `nativeLoad(modelPath)` が 0 を返し、`nativeSystemInfo()` が空でない文字列を返す
  - _Requirements: 4.1, 5.1_
  - _Boundary: Native_

- [ ] 5.3 llama_jni.cpp トークン生成ループの実装
  - `nativeGenerateNextToken`: `llama_sampler_sample → llama_decode（1トークン）→ token文字列返却`
  - EOS検出時に空文字を返す
  - テキストプロンプトに対して `nativeGenerateNextToken()` が意味のあるトークン列を返し、EOS時に空文字を返す
  - _Requirements: 4.1, 4.3_
  - _Boundary: Native_

---

## Task 6: Gemma 4マルチモーダルJNI拡張

- [ ] 6.1 ImageUtils Bitmap→RGBバイト変換の実装
  - `Bitmap.toRgbByteArray(): ByteArray` 拡張関数を実装（R/G/B各1バイト、`width * height * 3` バイト）
  - 赤単色Bitmapを入力すると `[255, 0, 0, 255, 0, 0, ...]` パターンのバイト列が生成される
  - _Requirements: 4.1_
  - _Boundary: ImageUtils_
  - _Depends: なし（Task 5と並行実施可能）_

- [ ] 6.2 llama_jni.cpp mmprojロードとmtmd初期化の実装
  - `nativeLoadMmproj`（Kotlin宣言はTask 5.1で完了済み）のC実装
  - `mtmd_context_params` に `image_min_tokens = 0`（Gemma 4クラッシュ回避）を設定
  - `mtmd_init_from_file(mmprojPath, g_model, params)` で `g_mtmd_ctx` を初期化
  - `nativeLoadMmproj(mmprojPath)` が 0 を返し `g_mtmd_ctx` が非nullとなる
  - _Requirements: 4.1_
  - _Boundary: Native_
  - _Depends: 5_

- [ ] 6.3 llama_jni.cpp 画像入力ターン処理の実装（M-RoPE含む）
  - `nativeProcessImageTurn` の実装: `mtmd_bitmap_init → mtmd_tokenize → mtmd_encode_chunk（画像チャンク）→ llama_decode（テキストチャンク）`
  - `mtmd_decode_use_mrope()` が `true` の場合、`mtmd_image_tokens_get_decoder_pos()` でM-RoPE 3Dポジションを設定
  - 使用後: `mtmd_bitmap_free`、`mtmd_input_chunks_free` でクリーンアップ
  - テスト画像 + 日本語プロンプトで `nativeGenerateNextToken()` が日本語テキストを生成する
  - _Requirements: 4.1, 4.3_
  - _Boundary: Native_
  - _Depends: 6.2_

---

## Task 7 (P): CameraXカメラ機能

- [ ] 7.1 カメラ権限要求フローの実装
  - `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` でCAMERA権限要求
  - 権限未取得時に権限要求ダイアログを自動表示
  - 権限拒否時に「設定から権限を許可してください」の説明UIを表示
  - 権限承認時にカメラプレビューが表示され、拒否時には表示されない
  - _Requirements: 1.2_
  - _Boundary: MainActivity, CameraPreviewSection_
  - _Depends: 2_

- [ ] 7.2 CameraXプレビューとImageCapture設定の実装
  - `ProcessCameraProvider.getInstance()` + `bindToLifecycle(owner, CameraSelector.BACK_CAMERA, preview, imageCapture)` を実装
  - `Preview.Builder().build().also { it.setSurfaceProvider { req -> _surfaceRequest.value = req } }` でSurfaceRequest管理
  - `CameraXViewfinder(surfaceRequest)` でCompose nativeプレビュー（AndroidView不使用）
  - `ImageCapture.Builder().setCaptureMode(MINIMIZE_LATENCY).build()`
  - アプリ起動・権限承認後にカメラプレビューがリアルタイムで表示される
  - _Requirements: 1.2, 2.1_
  - _Boundary: CameraPreviewSection_

- [ ] 7.3 撮影・Bitmap取得・ViewModelコールバックの実装
  - `imageCapture.takePicture(executor, OnImageCapturedCallback)` で撮影
  - コールバック内で `imageProxy.toBitmap()` 後に必ず `imageProxy.close()` を呼び出す
  - 撮影完了で `mainViewModel.onCapture(bitmap)` を呼び出し
  - `AppState.Inferencing` の間、撮影ボタンを `enabled = false` で無効化
  - 撮影ボタン押下でBitmapが取得されViewModel.onCapture()が呼ばれ、ボタンが即座に無効化される
  - _Requirements: 1.4, 2.1, 2.2, 2.3_
  - _Boundary: CameraPreviewSection_

---

## Task 8: InferenceRepositoryとモデル推論フロー

- [ ] 8.1 InferenceRepository初期化・解放ロジックの実装
  - `initialize(modelPath, mmprojPath)` を `Dispatchers.IO` で実装: `nativeLoad → nativePrepare(nCtx=4096, nThreads) → nativeLoadMmproj`
  - `release()`: `nativeUnload()` の呼び出し
  - `nThreads = min(Runtime.getRuntime().availableProcessors(), 8)`
  - `initialize()` 完了後に `nativeSystemInfo()` が空でない文字列を返す
  - _Requirements: 4.1, 5.1_
  - _Boundary: InferenceRepository_
  - _Depends: 5, 6_

- [ ] 8.2 Flow<String>トークンストリーミング推論とGemma 4プロンプトテンプレートの実装
  - `infer(bitmap, prompt)`: `bitmap.toRgbByteArray() → nativeProcessImageTurn(rgb, w, h, fullPrompt) → generateNextTokenループ`
  - Gemma 4プロンプトテンプレート組み込み: `"<start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n"`
  - `Flow<String>` でトークンを emit、空文字受信で `complete`
  - `flowOn(Dispatchers.IO)` を適用
  - テスト画像に対して `infer()` が複数トークンの `Flow<String>` を返し、収束して日本語テキストが生成される
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.2_
  - _Boundary: InferenceRepository_

---

## Task 9: 統合フェーズ — 全フロー接続

- [ ] 9.1 MainViewModelへのRepository接続とダウンロード→モデルロードフロー統合
  - `MainViewModel` に `ModelRepository` と `InferenceRepository` をコンストラクタ注入（ViewModelFactory使用）
  - `onAppStart()`: `isModelReady()` チェック → `false` なら `AppState.DownloadRequired`、`true` なら `AppState.ModelLoading` → `initialize()` → `AppState.ModelReady`
  - `onStartDownload()`: `downloadModels()` Flow を collect → `AppState.Downloading` 更新 → `Finished` 後に `AppState.ModelLoading` → `initialize()` → `AppState.ModelReady`
  - `onRetryDownload()`: `AppState.DownloadFailed` 状態から `onStartDownload()` を再実行
  - アプリ起動→ダウンロード進捗表示→完了→`AppState.ModelReady`の状態遷移がUIに反映される
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 5.1_
  - _Boundary: MainViewModel_
  - _Depends: 2, 4_

- [ ] 9.2 撮影→推論→テキスト表示の全フロー統合
  - `onCapture(bitmap)`: `AppState.Inferencing` セット → `inferenceRepo.infer()` をcollect → 各トークンで `AppState.InferenceResult(text)` 更新 → 完了後 `AppState.ModelReady`
  - CameraPreviewSectionの撮影ボタンが推論中に無効化されることを統合確認
  - 撮影ボタン押下から日本語テキスト表示まで動作し、完了後にボタンが再有効化される
  - _Requirements: 2.1, 2.2, 2.3, 4.1, 4.2, 4.3, 5.2_
  - _Boundary: MainViewModel_
  - _Depends: 7, 8_

- [ ] 9.3 ダウンロード失敗・再試行UIの統合
  - `onStartDownload()` 内で `DownloadState.Failed` 受信時に `AppState.DownloadFailed(error)` へ遷移
  - MainScreenの `AppState.DownloadFailed` 時にエラーメッセージと再試行ボタンを表示
  - 再試行ボタン押下で `onRetryDownload()` が呼ばれダウンロードが再開する
  - ネットワーク切断シミュレーション後に再試行ボタンが表示され、押下で再ダウンロードが再開する
  - _Requirements: 3.5_
  - _Boundary: MainViewModel, MainScreen_

- [ ] 9.4 推論エラーと権限拒否のエラーUI統合
  - `onCapture()` 内でJNIエラー発生時に `AppState.InferenceError(message)` へ遷移し、ResultSectionにエラーメッセージを表示
  - カメラ権限拒否時の説明ダイアログと設定画面誘導をMainActivityに統合
  - 撮影失敗（ImageCaptureException）時のToastメッセージ表示
  - 推論エラー発生後に `AppState.InferenceError` がUIに反映され、次の撮影が可能な状態に復帰する
  - _Requirements: 4.5_
  - _Boundary: MainViewModel, CameraPreviewSection, ResultSection_

---

## Task 10: テストと検証

- [ ] 10.1 ModelRepositoryユニットテスト
  - MockWebServerを使った正常ダウンロード完了・ネットワーク失敗・Range再開のテスト
  - `isModelReady()` の両ファイル存在/片方欠損/両方欠損の3ケーステスト
  - テスト3ケース以上がすべてパスする
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 10.2 MainViewModel AppState遷移ユニットテスト
  - `DownloadRequired → Downloading → ModelLoading → ModelReady` の遷移テスト
  - `ModelReady → Inferencing → InferenceResult → ModelReady` の遷移テスト
  - Turbineライブラリを使ったFlow検証テストがすべてパスする
  - _Requirements: 1.1, 2.2, 3.1, 4.2_

- [ ] 10.3 実機E2Eテスト（Pixel 10）
  - 初回起動→ダウンロード進捗表示→完了→カメラプレビュー表示の確認
  - 撮影ボタン押下→ローディング表示→日本語テキスト生成→ボタン再有効化の確認
  - 機内モード有効状態での撮影→推論→テキスト表示が成功することの確認
  - 上記3シナリオが実機で動作確認できる
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.4, 4.1, 4.2, 4.3, 4.4, 5.1, 5.2_

- [ ]* 10.4 ImageUtils変換精度テスト（オプション）
  - 既知ピクセル値（赤単色、緑単色）を持つBitmapから期待するRGBバイト列を検証
  - 変換結果が期待バイト列と完全一致する
  - _Requirements: 4.1_
