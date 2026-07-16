LOCAL_PATH := $(call my-dir)

CHROMAPRINT_PATH := vendor/chromaprint

include $(CLEAR_VARS)
LOCAL_MODULE := chromaprint
LOCAL_CPPFLAGS := -std=c++17 -O3 -fexceptions -fvisibility=hidden -DCHROMAPRINT_API_EXPORTS -DUSE_KISSFFT
LOCAL_CFLAGS := -O3 -fPIC
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/$(CHROMAPRINT_PATH) \
    $(LOCAL_PATH)/$(CHROMAPRINT_PATH)/kissfft
LOCAL_SRC_FILES := \
    $(CHROMAPRINT_PATH)/audio_processor.cpp \
    $(CHROMAPRINT_PATH)/chroma.cpp \
    $(CHROMAPRINT_PATH)/chroma_resampler.cpp \
    $(CHROMAPRINT_PATH)/chroma_filter.cpp \
    $(CHROMAPRINT_PATH)/spectrum.cpp \
    $(CHROMAPRINT_PATH)/fft.cpp \
    $(CHROMAPRINT_PATH)/fft_lib_kissfft.cpp \
    $(CHROMAPRINT_PATH)/fingerprinter.cpp \
    $(CHROMAPRINT_PATH)/image_builder.cpp \
    $(CHROMAPRINT_PATH)/simhash.cpp \
    $(CHROMAPRINT_PATH)/silence_remover.cpp \
    $(CHROMAPRINT_PATH)/fingerprint_calculator.cpp \
    $(CHROMAPRINT_PATH)/fingerprint_compressor.cpp \
    $(CHROMAPRINT_PATH)/fingerprint_decompressor.cpp \
    $(CHROMAPRINT_PATH)/fingerprinter_configuration.cpp \
    $(CHROMAPRINT_PATH)/fingerprint_matcher.cpp \
    $(CHROMAPRINT_PATH)/utils/base64.cpp \
    $(CHROMAPRINT_PATH)/chromaprint.cpp \
    $(CHROMAPRINT_PATH)/kissfft/kiss_fft.c \
    $(CHROMAPRINT_PATH)/kissfft/kiss_fftr.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := echo_chromaprint_jni
LOCAL_CPPFLAGS := -std=c++17 -O3 -fexceptions -fvisibility=hidden
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(CHROMAPRINT_PATH)
LOCAL_SRC_FILES := echo_chromaprint_jni.cpp
LOCAL_SHARED_LIBRARIES := chromaprint
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
