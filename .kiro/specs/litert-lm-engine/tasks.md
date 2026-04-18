# Implementation Plan

- [ ] 1. Foundation: ビルド設定と定数の準備
- [x] 1.1 build.gradle.kts から NDK/CMake 設定を削除し LiteRT-LM Gradle 依存を追加する
  - `externalNativeBuild`（CMakeLists.txt 参照）ブロックを削除する
  - `ndk { abiFilters }` ブロックを削除する
  - cmake arguments ブロックを削除する
  - `implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")` を dependencies に追加する（実装時は v0.10.1 以降を確認すること）
  - Gradle sync が成功し dependencies に LiteRT-LM SDK が表示されること
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 1.2 ModelConfig 定数クラスを Gemma 4 E2B .litertlm の URL・ファイル名に更新しユニットテストを実装する
  - `MODEL_URL` を `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm` に変更する
  - `MODEL_FILENAME` を `gemma-4-E2B-it.litertlm` に変更する
  - `MMPROJ_URL`・`MMPROJ_FILENAME` は空文字定数として維持する（`getMmprojPath()` 互換性）
  - ModelConfig 定数が HTTPS URL・`.litertlm` 拡張子・空文字 MMPROJ を持つユニットテスト 3 件が通ること
  - _Requirements: 1.1, 5.2_

- [ ] 2. Core: LiteRtLmEngine の新規実装
- [ ] 2.1 LiteRtLmEngine クラスを新規作成し GPU 優先・CPU フォールバック初期化とリソース解放を実装する
  - `engine/LiteRtLmEngine.kt` を新規作成する
  - `initialize(modelPath: String)` を suspend 関数として実装する。`Backend.GPU()` で初期化を試み、例外発生時に `Backend.CPU()` でリトライする
  - `release()` で Engine・Conversation リソースを解放し、以降の `infer` 呼び出しを不正状態から保護する
  - 全バックエンド失敗時は例外をスローして呼び出し元に伝播する
  - 実装時は LiteRT-LM SDK の正確な API シグネチャを公式ドキュメントで確認すること
  - GPU モックが例外をスローしたとき CPU 初期化が完了するユニットテストが通ること
  - _Requirements: 2.1, 2.2, 2.4, 2.5_
  - _Boundary: LiteRtLmEngine_

- [ ] 2.2 infer メソッドでマルチモーダル推論と Flow ストリーミングを実装する
  - `infer(imagePath: String, prompt: String): Flow<String>` を実装する
  - `Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))` を構築して `conversation.sendMessageAsync()` に渡す
  - 生成トークンを受信次第 `Flow<String>` として逐次 emit する
  - 生成完了時に Flow が正常終了し、エラー時に Flow が例外で終了することを確認する
  - `release()` 後の `infer()` 呼び出しが `IllegalStateException` をスローするユニットテストが通ること
  - _Requirements: 2.3, 3.2, 3.3, 3.4_
  - _Boundary: LiteRtLmEngine_

- [ ] 3. (P) Core: ModelRepositoryImpl を単一ファイルダウンロードに対応させる
- [ ] 3.1 (P) isModelReady() を .litertlm 単一ファイル確認に変更し getMmprojPath() を空文字返却にする
  - `isModelReady()` が `MODEL_FILENAME`（.litertlm）の存在かつ `length() > 0` を確認するよう変更する
  - `getMmprojPath()` が `""` を返すよう変更する（LiteRT-LM は単一ファイルで完結）
  - `downloadModels()` が `.litertlm` 1 件のみをダウンロードし mmproj のダウンロード処理を削除する
  - ファイル不在→ false・size=0→ false・size>0→ true の 3 ケースで `isModelReady()` が正しく動作するユニットテストが通ること
  - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 5.4_
  - _Boundary: ModelRepositoryImpl_
  - _Depends: 1.2_

- [ ] 4. Core: InferenceRepositoryImpl を LiteRT-LM API に置き換え
- [ ] 4.1 LlamaEngine 参照を LiteRtLmEngine に置き換え Bitmap→一時ファイル変換と削除を実装する
  - `LlamaEngine` のすべての参照を `LiteRtLmEngine` に置き換える
  - コンストラクタを `InferenceRepositoryImpl(engine: LiteRtLmEngine, cacheDir: File)` に変更する（`nativeLibDir` を削除）
  - `infer(bitmap, prompt)` 内で Bitmap を最大 336px にリサイズし JPEG 一時ファイルとして `cacheDir` に保存する
  - 一時ファイルパスを `engine.infer(imagePath, prompt)` に渡す
  - `finally` ブロックで一時ファイルを確実に削除する（正常完了・例外発生の両方）
  - `initialize(modelPath, mmprojPath)` が `mmprojPath` を受け取るが `engine.initialize(modelPath)` のみを呼ぶことを確認する
  - InferenceRepositoryImpl を生成するコード（`MainActivity` 等の構成ルート）を新コンストラクタに合わせて更新する
  - 正常完了・例外発生時の両方で一時ファイルが削除されるユニットテストが通ること
  - _Requirements: 3.1, 3.5, 3.6, 5.1, 5.3_
  - _Boundary: InferenceRepositoryImpl_
  - _Depends: 2.2_

- [ ] 5. Integration: llama.cpp スタックの完全削除
- [ ] 5.1 LlamaEngine.kt を削除し app/src/main/cpp/ ディレクトリと llama.cpp サブモジュールを除去する
  - `engine/LlamaEngine.kt` を削除する（InferenceRepositoryImpl が参照していないことを確認後）
  - `app/src/main/cpp/` ディレクトリをすべて削除する（JNI ソース・llama.cpp サブモジュール）
  - `.gitmodules` から llama.cpp エントリを削除し `git submodule deinit` を実行する
  - `./gradlew assembleDebug` がコンパイルエラーなしに成功すること
  - _Requirements: 4.2, 4.3_
  - _Depends: 4.1_

- [ ] 6. Validation: ビルド確認とテスト完備
- [ ] 6.1 Gradle ビルドが C++ コンパイルステップなしに成功することを確認する
  - `./gradlew assembleDebug` を実行し、ビルドログに `cmake`・`ninja` などの C++ ビルドステップが含まれないことを確認する
  - `./gradlew test` が全ユニットテストをパスすること（ModelConfig・ModelRepositoryImpl・LiteRtLmEngine・InferenceRepositoryImpl）
  - 生成された APK に `libllamajni.so` などの JNI ライブラリが含まれないことを確認する
  - _Requirements: 4.1, 4.2, 4.3_
  - _Depends: 5.1_

## Implementation Notes
- LiteRT-LM SDK の正確な API シグネチャは公式ドキュメント（https://ai.google.dev/edge/litert-lm/android）で確認すること。`latest.release` は v0.10.1 以降を使用（v0.10.0 は GPU decode バグあり）
- `engine.initialize()` は 10 秒以上ブロックするため `Dispatchers.IO` コンテキストで実行すること
- GPU バックエンドはデバイス依存（OpenCL/OpenGL 対応デバイスのみ）。CPU フォールバックで全デバイス対応を保証する
