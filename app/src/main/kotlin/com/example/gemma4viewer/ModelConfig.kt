package com.example.gemma4viewer

object ModelConfig {
    const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    // インターフェース互換性のため残存（getMmprojPath() の戻り値）、推論には使用しない
    const val MMPROJ_URL = ""
    const val MMPROJ_FILENAME = ""
}
