# Brief: gemma4-camera-viewer

## Problem
Gemma 4 E2B（Effective 2B）のマルチモーダル推論能力をAndroid実機で検証したい。
カメラで撮影した画像をオンデバイスのモデルに入力し、物体認識と解説を日本語で表示することで、
オフライン環境でのGemma 4の実用性を評価する。

## Current State
- プロジェクト新規作成（既存コードなし）
- Gemma 4 E2B の GGUF モデル（unsloth/gemma-4-E2B-it-GGUF、Q4_K_M、約3.1 GB）はHuggingFaceで公開済み
- llama.cpp の Android JNI バインディングが利用可能

## Desired Outcome
- Androidアプリを起動し、上半分にカメラプレビュー、下半分にテキスト表示エリアが表示される
- 撮影ボタンを押すと、写真をGemma 4 E2B に入力し「何が写っているか＋日本語解説」が表示される
- 初回起動時にモデルをダウンロードし、以降はオフラインで動作する
- Pixel 10（RAM 12GB、Android 15）での動作を確認できる

## Approach
**GGUF + llama.cpp Android JNI**

`unsloth/gemma-4-E2B-it-GGUF`（Q4_K_M）をllama.cppのAndroid JNIバインディング経由で呼び出す。
Android NDK + CMakeでllama.cppをネイティブライブラリとしてビルドし、KotlinからJNI経由でマルチモーダル推論を呼び出す。

理由：
- Gemma 4 E2B対応のマルチモーダルGGUFが今すぐ利用可能
- Pixel 10（12GB RAM）でQ4量子化モデルが動作する十分なスペック
- Windows + Android Studio + NDKで完結するビルド環境

## Scope
- **In**:
  - KotlinによるAndroidアプリ（シングルActivity構成）
  - カメラプレビュー（上半分）＋テキスト表示エリア（下半分）のUI
  - CameraX APIによる撮影機能
  - llama.cpp JNIブリッジによるGemma 4 E2Bマルチモーダル推論
  - 初回起動時のモデルダウンロード（HuggingFace or CDN）＋進捗表示
  - 日本語プロンプトによる物体認識＋解説テキスト生成
  - モデルファイルのローカルキャッシュ（filesDir）
- **Out**:
  - 音声入力・出力
  - 複数画像の比較
  - 会話履歴の保持
  - Google Play配布・署名
  - iOS対応

## Boundary Candidates
- **UI層**: カメラ表示・撮影ボタン・テキスト表示（CameraX + Jetpack Compose）
- **推論層**: llama.cpp JNIブリッジ、プロンプト構築、テキスト生成
- **モデル管理層**: ダウンロード、キャッシュ確認、ファイル管理

## Out of Boundary
- オーディオエンコーダ推論（Gemma 4 E2Bは音声対応だが本アプリでは使用しない）
- モデルのファインチューニング・量子化
- バックエンドサーバー・クラウド推論

## Upstream / Downstream
- **Upstream**: unsloth/gemma-4-E2B-it-GGUF（HuggingFace）、llama.cpp（GitHub）
- **Downstream**: Gemma 4の有効性検証結果をもとにした本格アプリ開発

## Existing Spec Touchpoints
- **Extends**: なし（新規）
- **Adjacent**: なし

## Constraints
- ターゲット端末: Pixel 10（RAM 12GB、Android 15相当、API 35）
- 言語: Kotlin（Android Studio / Windows環境でビルド）
- モデル: Gemma 4 E2B Q4_K_M GGUF（約3.1 GB）
- ビルド環境: Android NDK + CMake（Android Studio SDK Manager経由）
- 表示言語: 日本語
- ストレージ: モデル保存に約4 GBの空き必要
