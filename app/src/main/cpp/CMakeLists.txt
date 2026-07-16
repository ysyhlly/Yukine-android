cmake_minimum_required(VERSION 3.22.1)
project(echo_chromaprint LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CHROMAPRINT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/vendor/chromaprint)

add_library(
    chromaprint
    SHARED
    ${CHROMAPRINT_PATH}/audio_processor.cpp
    ${CHROMAPRINT_PATH}/chroma.cpp
    ${CHROMAPRINT_PATH}/chroma_resampler.cpp
    ${CHROMAPRINT_PATH}/chroma_filter.cpp
    ${CHROMAPRINT_PATH}/spectrum.cpp
    ${CHROMAPRINT_PATH}/fft.cpp
    ${CHROMAPRINT_PATH}/fft_lib_kissfft.cpp
    ${CHROMAPRINT_PATH}/fingerprinter.cpp
    ${CHROMAPRINT_PATH}/image_builder.cpp
    ${CHROMAPRINT_PATH}/simhash.cpp
    ${CHROMAPRINT_PATH}/silence_remover.cpp
    ${CHROMAPRINT_PATH}/fingerprint_calculator.cpp
    ${CHROMAPRINT_PATH}/fingerprint_compressor.cpp
    ${CHROMAPRINT_PATH}/fingerprint_decompressor.cpp
    ${CHROMAPRINT_PATH}/fingerprinter_configuration.cpp
    ${CHROMAPRINT_PATH}/fingerprint_matcher.cpp
    ${CHROMAPRINT_PATH}/utils/base64.cpp
    ${CHROMAPRINT_PATH}/chromaprint.cpp
    ${CHROMAPRINT_PATH}/kissfft/kiss_fft.c
    ${CHROMAPRINT_PATH}/kissfft/kiss_fftr.c
)

target_include_directories(
    chromaprint
    PRIVATE
    ${CHROMAPRINT_PATH}
    ${CHROMAPRINT_PATH}/kissfft
)
target_compile_definitions(chromaprint PRIVATE CHROMAPRINT_API_EXPORTS USE_KISSFFT)
target_compile_options(chromaprint PRIVATE -O3 -fvisibility=hidden)

add_library(echo_chromaprint_jni SHARED echo_chromaprint_jni.cpp)
target_include_directories(echo_chromaprint_jni PRIVATE ${CHROMAPRINT_PATH})
target_compile_options(echo_chromaprint_jni PRIVATE -O3 -fvisibility=hidden)
target_link_libraries(echo_chromaprint_jni PRIVATE chromaprint log)
