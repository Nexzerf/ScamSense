# Speech-to-text for live call screening: whisper.cpp (MIT) + a small JNI bridge.
cmake_minimum_required(VERSION 3.22.1)
project(scamsense_whisper)

set(WHISPER_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
# Portable CPU build: one library per ABI, no runtime backend loading.
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)

# Release-grade speed even in debug builds (whisper is unusably slow at -O0).
add_compile_options(-O3)
add_link_options("LINKER:--build-id=none")
add_link_options($<$<NOT:$<CONFIG:Debug>>:-Wl,--strip-all>)

add_subdirectory(${WHISPER_ROOT} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

add_library(scamsense_whisper SHARED scamsense_whisper.cpp)
find_library(log-lib log)
target_link_libraries(scamsense_whisper whisper ${log-lib})
