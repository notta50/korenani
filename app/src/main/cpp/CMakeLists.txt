# CMakeLists.txt — placeholder for task 1.1
# Full llama.cpp build configuration will be added in task 1.3
cmake_minimum_required(VERSION 3.22.1)
project("gemma4viewer")

# Placeholder shared library — will be replaced with full llama.cpp JNI bridge in task 1.3
add_library(
    llama-jni
    SHARED
    llama_jni_placeholder.cpp
)

find_library(log-lib log)

target_link_libraries(
    llama-jni
    ${log-lib}
)
