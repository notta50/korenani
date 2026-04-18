cmake_minimum_required(VERSION 3.22.1)
project(gemma4viewer)

set(LLAMA_CPP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp)

# Build llama.cpp (llama, ggml, llama-common targets)
add_subdirectory(${LLAMA_CPP_ROOT} llama-build)

# mtmd requires LLAMA_INSTALL_VERSION which is set inside the llama.cpp subdirectory scope.
# Define it here so the mtmd subdirectory can use it.
if (NOT DEFINED LLAMA_INSTALL_VERSION)
    set(LLAMA_INSTALL_VERSION "0.0.0")
endif()

# Build mtmd library directly (multimodal support for Gemma 4)
add_subdirectory(${LLAMA_CPP_ROOT}/tools/mtmd ${CMAKE_CURRENT_BINARY_DIR}/mtmd-build)

# JNI bridge library
add_library(llama-jni SHARED llama_jni.cpp)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_CPP_ROOT}/include
    ${LLAMA_CPP_ROOT}/tools/mtmd
)

target_link_libraries(llama-jni
    llama
    mtmd
    llama-common
    android
    log
)
