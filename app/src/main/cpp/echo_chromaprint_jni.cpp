#include <jni.h>

#include <cstdint>
#include <algorithm>
#include <cmath>
#include <memory>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

#include "chromaprint.h"

namespace {

struct FingerprintSession {
    static constexpr int kTargetSampleRate = 11025;
    static constexpr int kMaxBufferedSeconds = 45;

    ChromaprintContext* context = nullptr;
    bool started = false;
    int input_sample_rate = 0;
    int channels = 0;
    std::vector<int16_t> input;

    FingerprintSession() : context(chromaprint_new(CHROMAPRINT_ALGORITHM_DEFAULT)) {}

    ~FingerprintSession() {
        if (context != nullptr) {
            chromaprint_free(context);
        }
    }
};

bool feed_resampled(FingerprintSession* value) {
    if (value == nullptr || value->input.empty() || value->channels <= 0 ||
            value->input_sample_rate <= 0) {
        return false;
    }
    if (value->input_sample_rate == FingerprintSession::kTargetSampleRate) {
        return chromaprint_feed(
                value->context, value->input.data(), static_cast<int>(value->input.size())) == 1;
    }
    const size_t input_frames = value->input.size() / static_cast<size_t>(value->channels);
    const size_t output_frames = static_cast<size_t>(
            std::floor(static_cast<double>(input_frames) *
                    FingerprintSession::kTargetSampleRate / value->input_sample_rate));
    if (input_frames == 0 || output_frames == 0) {
        return false;
    }
    std::vector<int16_t> output(output_frames * static_cast<size_t>(value->channels));
    const double step = static_cast<double>(value->input_sample_rate) /
            FingerprintSession::kTargetSampleRate;
    for (size_t frame = 0; frame < output_frames; ++frame) {
        const double source_position = frame * step;
        const size_t first = std::min(
                static_cast<size_t>(source_position), input_frames - 1);
        const size_t second = std::min(first + 1, input_frames - 1);
        const double fraction = source_position - first;
        for (int channel = 0; channel < value->channels; ++channel) {
            const double a = value->input[first * value->channels + channel];
            const double b = value->input[second * value->channels + channel];
            const double sample = a + (b - a) * fraction;
            output[frame * value->channels + channel] = static_cast<int16_t>(
                    std::clamp(sample, -32768.0, 32767.0));
        }
    }
    return chromaprint_feed(
            value->context, output.data(), static_cast<int>(output.size())) == 1;
}

FingerprintSession* session(jlong handle) {
    return reinterpret_cast<FingerprintSession*>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_yukine_fingerprint_ChromaprintNative_nativeCreate(JNIEnv*, jobject) {
    auto value = std::make_unique<FingerprintSession>();
    if (value->context == nullptr) {
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(value.release()));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_yukine_fingerprint_ChromaprintNative_nativeStart(
        JNIEnv*, jobject, jlong handle, jint sample_rate, jint channels) {
    auto* value = session(handle);
    if (value == nullptr || value->context == nullptr || sample_rate <= 0 || channels <= 0) {
        return JNI_FALSE;
    }
    value->input_sample_rate = sample_rate;
    value->channels = channels;
    value->input.clear();
    value->input.reserve(static_cast<size_t>(sample_rate) * channels * 12);
    value->started = chromaprint_start(
            value->context, FingerprintSession::kTargetSampleRate, channels) == 1;
    return value->started ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_yukine_fingerprint_ChromaprintNative_nativeFeed(
        JNIEnv* env, jobject, jlong handle, jobject pcm_buffer, jint sample_count) {
    auto* value = session(handle);
    if (value == nullptr || !value->started || pcm_buffer == nullptr || sample_count <= 0) {
        return JNI_FALSE;
    }
    auto* samples = static_cast<int16_t*>(env->GetDirectBufferAddress(pcm_buffer));
    const jlong capacity = env->GetDirectBufferCapacity(pcm_buffer);
    if (samples == nullptr || capacity < static_cast<jlong>(sample_count) * sizeof(int16_t)) {
        return JNI_FALSE;
    }
    const size_t maximum = static_cast<size_t>(value->input_sample_rate) * value->channels *
            FingerprintSession::kMaxBufferedSeconds;
    if (value->input.size() + static_cast<size_t>(sample_count) > maximum) {
        return JNI_FALSE;
    }
    value->input.insert(value->input.end(), samples, samples + sample_count);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_yukine_fingerprint_ChromaprintNative_nativeFinish(JNIEnv* env, jobject, jlong handle) {
    auto* value = session(handle);
    if (value == nullptr || !value->started || !feed_resampled(value) ||
            chromaprint_finish(value->context) != 1) {
        return nullptr;
    }
    char* encoded = nullptr;
    if (chromaprint_get_fingerprint(value->context, &encoded) != 1 || encoded == nullptr) {
        return nullptr;
    }
    uint32_t* raw = nullptr;
    int raw_size = 0;
    if (chromaprint_get_raw_fingerprint(value->context, &raw, &raw_size) != 1 ||
            raw == nullptr || raw_size <= 0) {
        chromaprint_dealloc(encoded);
        return nullptr;
    }
    std::ostringstream payload;
    payload << encoded << '|';
    payload << std::hex << std::setfill('0');
    for (int index = 0; index < raw_size; ++index) {
        payload << std::setw(8) << raw[index];
    }
    const std::string serialized = payload.str();
    jstring result = env->NewStringUTF(serialized.c_str());
    chromaprint_dealloc(raw);
    chromaprint_dealloc(encoded);
    value->started = false;
    value->input.clear();
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_app_yukine_fingerprint_ChromaprintNative_nativeRelease(JNIEnv*, jobject, jlong handle) {
    delete session(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_yukine_fingerprint_ChromaprintNative_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(chromaprint_get_version());
}
