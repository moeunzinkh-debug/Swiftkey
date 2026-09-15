cmake_minimum_required(VERSION 3.22.1)
project(swiftkey_offline_voice LANGUAGES C CXX)

# whisper.cpp v1.9.4. Fetch source at build time, never model weights or prebuilt executables.
# Commit pin prevents a moving branch/tag from silently changing the bundled native engine.
include(FetchContent)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_FFMPEG OFF CACHE BOOL "" FORCE)
set(WHISPER_COREML OFF CACHE BOOL "" FORCE)
set(WHISPER_METAL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG 927cfce34f31707e17f2bff35c349632fb9e2c3a
    GIT_SUBMODULES ""
)
FetchContent_MakeAvailable(whisper)

add_library(swiftkey_whisper SHARED whisper_jni.cpp)
target_compile_features(swiftkey_whisper PRIVATE cxx_std_17)
target_link_libraries(swiftkey_whisper PRIVATE whisper android log)
# Native Android 16 KiB page-size devices; do not depend on a second APK's C++ runtime.
target_link_options(swiftkey_whisper PRIVATE "-Wl,-z,max-page-size=16384")
