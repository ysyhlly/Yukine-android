#include <jni.h>

#include <android/log.h>
#include <libusb.h>
#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr char kLogTag[] = "EchoUsbIso";
constexpr int kPacketsPerTransfer = 8;
constexpr int kTransferRingSize = 8;
constexpr size_t kMaxPendingBytes = 4 * 1024 * 1024;
constexpr auto kQueueWait = std::chrono::milliseconds(500);

thread_local std::string gLastError;

void SetError(const std::string& message) {
    gLastError = message;
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
}

std::string LibusbError(const char* operation, int code) {
    return std::string(operation) + ": " + libusb_error_name(code);
}

struct IsoState;

struct TransferSlot {
    IsoState* owner = nullptr;
    libusb_transfer* transfer = nullptr;
    std::vector<uint8_t> buffer;
    bool inFlight = false;

    ~TransferSlot() {
        if (transfer != nullptr) libusb_free_transfer(transfer);
    }
};

struct IsoState {
    libusb_context* context = nullptr;
    libusb_device_handle* deviceHandle = nullptr;
    int duplicatedFd = -1;
    int endpointAddress = 0;
    int feedbackEndpointAddress = 0;
    int interfaceNumber = 0;
    int controlInterfaceNumber = -1;
    bool streamingInterfaceClaimed = false;
    bool controlInterfaceClaimed = false;
    int maxPacketPayload = 0;
    int sampleRateHz = 0;
    int bytesPerFrame = 0;
    int serviceIntervalUs = 0;
    int64_t frameRemainder = 0;
    std::vector<uint8_t> pending;
    std::vector<std::unique_ptr<TransferSlot>> slots;
    std::unique_ptr<TransferSlot> feedbackSlot;
    std::thread eventThread;
    std::mutex mutex;
    std::condition_variable completion;
    bool closing = false;
    bool stopEvents = false;
    int inFlight = 0;
    std::atomic<int64_t> submittedPackets{0};
    std::atomic<int64_t> completedPackets{0};
    std::atomic<int64_t> failedPackets{0};
    std::atomic<uint64_t> feedbackRateBits{0};
};

IsoState* FromHandle(jlong handle) {
    return reinterpret_cast<IsoState*>(static_cast<intptr_t>(handle));
}

uint64_t DoubleBits(double value) {
    uint64_t bits = 0;
    static_assert(sizeof(bits) == sizeof(value), "double must be 64-bit");
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

double FeedbackRate(const uint8_t* data, int length) {
    if (length == 3) {
        const uint32_t raw = static_cast<uint32_t>(data[0]) |
            (static_cast<uint32_t>(data[1]) << 8U) |
            (static_cast<uint32_t>(data[2]) << 16U);
        return static_cast<double>(raw) * 1000.0 / 16384.0;
    }
    if (length >= 4) {
        const uint32_t raw = static_cast<uint32_t>(data[0]) |
            (static_cast<uint32_t>(data[1]) << 8U) |
            (static_cast<uint32_t>(data[2]) << 16U) |
            (static_cast<uint32_t>(data[3]) << 24U);
        return static_cast<double>(raw) * 8000.0 / 65536.0;
    }
    return 0.0;
}

void LIBUSB_CALL FeedbackCompleted(libusb_transfer* transfer) {
    auto* slot = static_cast<TransferSlot*>(transfer->user_data);
    IsoState* state = slot == nullptr ? nullptr : slot->owner;
    if (state == nullptr) return;
    if (transfer->status == LIBUSB_TRANSFER_COMPLETED && transfer->actual_length >= 3) {
        const double rate = FeedbackRate(slot->buffer.data(), transfer->actual_length);
        if (rate > 0.0) state->feedbackRateBits.store(DoubleBits(rate));
    }
    std::lock_guard<std::mutex> lock(state->mutex);
    if (!state->closing && !state->stopEvents) {
        const int result = libusb_submit_transfer(transfer);
        if (result == LIBUSB_SUCCESS) return;
        SetError(LibusbError("Could not resubmit feedback transfer", result));
    }
    slot->inFlight = false;
    state->completion.notify_all();
}

void LIBUSB_CALL TransferCompleted(libusb_transfer* transfer) {
    auto* slot = static_cast<TransferSlot*>(transfer->user_data);
    IsoState* state = slot == nullptr ? nullptr : slot->owner;
    if (state == nullptr) return;
    int64_t completed = 0;
    int64_t failed = 0;
    if (transfer->status == LIBUSB_TRANSFER_COMPLETED) {
        for (int i = 0; i < transfer->num_iso_packets; ++i) {
            const libusb_iso_packet_descriptor& packet = transfer->iso_packet_desc[i];
            if (packet.status == LIBUSB_TRANSFER_COMPLETED && packet.actual_length == packet.length) {
                ++completed;
            } else {
                ++failed;
            }
        }
    } else if (transfer->status != LIBUSB_TRANSFER_CANCELLED) {
        failed = transfer->num_iso_packets;
        SetError("Isochronous transfer failed with status " + std::to_string(transfer->status));
    }
    state->completedPackets.fetch_add(completed);
    state->failedPackets.fetch_add(failed);
    std::lock_guard<std::mutex> lock(state->mutex);
    slot->inFlight = false;
    state->inFlight = std::max(0, state->inFlight - 1);
    state->completion.notify_all();
}

void EventLoop(IsoState* state) {
    while (true) {
        {
            std::lock_guard<std::mutex> lock(state->mutex);
            if (state->stopEvents && state->inFlight == 0 &&
                (state->feedbackSlot == nullptr || !state->feedbackSlot->inFlight)) {
                return;
            }
        }
        timeval timeout{0, 100000};
        const int result = libusb_handle_events_timeout_completed(state->context, &timeout, nullptr);
        if (result != LIBUSB_SUCCESS && result != LIBUSB_ERROR_INTERRUPTED) {
            SetError(LibusbError("libusb event loop", result));
            std::lock_guard<std::mutex> lock(state->mutex);
            state->closing = true;
        }
    }
}

TransferSlot* WaitForFreeSlot(IsoState* state, std::unique_lock<std::mutex>& lock) {
    const auto hasSlot = [state]() {
        return state->closing || std::any_of(
            state->slots.begin(), state->slots.end(),
            [](const std::unique_ptr<TransferSlot>& slot) { return !slot->inFlight; }
        );
    };
    if (!state->completion.wait_for(lock, kQueueWait, hasSlot) || state->closing) return nullptr;
    for (const auto& slot : state->slots) {
        if (!slot->inFlight) return slot.get();
    }
    return nullptr;
}

void CancelTransfers(IsoState* state) {
    for (const auto& slot : state->slots) {
        if (slot->inFlight) libusb_cancel_transfer(slot->transfer);
    }
    if (state->feedbackSlot != nullptr && state->feedbackSlot->inFlight) {
        libusb_cancel_transfer(state->feedbackSlot->transfer);
    }
}

uint32_t LittleEndianRate(const unsigned char* data, int length) {
    uint32_t value = 0;
    for (int i = 0; i < std::min(length, 4); ++i) {
        value |= static_cast<uint32_t>(data[i]) << (8U * i);
    }
    return value;
}

bool ReadCurrentSampleRate(
    IsoState* state,
    bool uac2,
    int controlInterfaceNumber,
    int entityOrEndpoint,
    uint32_t* rate
) {
    unsigned char current[4] = {0, 0, 0, 0};
    const uint8_t requestType = static_cast<uint8_t>(
        LIBUSB_ENDPOINT_IN | LIBUSB_REQUEST_TYPE_CLASS |
        (uac2 ? LIBUSB_RECIPIENT_INTERFACE : LIBUSB_RECIPIENT_ENDPOINT)
    );
    const uint16_t index = uac2
        ? static_cast<uint16_t>((entityOrEndpoint << 8) | (controlInterfaceNumber & 0xff))
        : static_cast<uint16_t>(entityOrEndpoint & 0xff);
    const int expected = uac2 ? 4 : 3;
    const int result = libusb_control_transfer(
        state->deviceHandle,
        requestType,
        uac2 ? 0x01 : 0x81,
        0x0100,
        index,
        current,
        expected,
        1000
    );
    if (result != expected) return false;
    *rate = LittleEndianRate(current, expected);
    return true;
}

bool ResolveUac2ClockSource(
    IsoState* state,
    int controlInterfaceNumber,
    const std::vector<int>& clockSourceEntityIds,
    int clockSelectorEntityId,
    int clockSelectorControl,
    size_t* selectedIndex
) {
    if (clockSourceEntityIds.empty()) {
        SetError("UAC2 clock source entity was not resolved from the streaming terminal");
        return false;
    }
    *selectedIndex = 0;
    if (clockSelectorEntityId <= 0) return true;

    unsigned char selectedPin = 0;
    const int result = libusb_control_transfer(
        state->deviceHandle,
        LIBUSB_ENDPOINT_IN | LIBUSB_REQUEST_TYPE_CLASS | LIBUSB_RECIPIENT_INTERFACE,
        0x01,
        0x0100,
        static_cast<uint16_t>((clockSelectorEntityId << 8) | (controlInterfaceNumber & 0xff)),
        &selectedPin,
        1,
        1000
    );
    if (result == 1 && selectedPin >= 1 && selectedPin <= clockSourceEntityIds.size()) {
        *selectedIndex = static_cast<size_t>(selectedPin - 1);
        __android_log_print(
            ANDROID_LOG_INFO, kLogTag,
            "UAC2 selector 0x%02x chose pin %u -> clock 0x%02x",
            clockSelectorEntityId, selectedPin, clockSourceEntityIds[*selectedIndex]
        );
        return true;
    }
    if (clockSourceEntityIds.size() == 1) {
        __android_log_print(
            ANDROID_LOG_WARN, kLogTag,
            "UAC2 selector 0x%02x GET_CUR failed (%d, control=%d); using its only source 0x%02x",
            clockSelectorEntityId, result, clockSelectorControl, clockSourceEntityIds[0]
        );
        return true;
    }
    SetError(
        "UAC2 clock selector 0x" + std::to_string(clockSelectorEntityId) +
        " did not return a valid source pin: " + LibusbError("GET_CUR", result)
    );
    return false;
}

bool ConfigureSampleRate(
    IsoState* state,
    int audioClassVersion,
    int controlInterfaceNumber,
    const std::vector<int>& clockSourceEntityIds,
    const std::vector<int>& clockSourceFrequencyControls,
    int clockSelectorEntityId,
    int clockSelectorControl,
    int sampleFrequencyControl
) {
    unsigned char data[4] = {
        static_cast<unsigned char>(state->sampleRateHz & 0xff),
        static_cast<unsigned char>((state->sampleRateHz >> 8) & 0xff),
        static_cast<unsigned char>((state->sampleRateHz >> 16) & 0xff),
        static_cast<unsigned char>((state->sampleRateHz >> 24) & 0xff)
    };
    const bool uac2 = audioClassVersion >= 2;
    int entityOrEndpoint = state->endpointAddress;
    int frequencyControl = sampleFrequencyControl;
    if (uac2) {
        size_t selectedIndex = 0;
        if (!ResolveUac2ClockSource(
                state,
                controlInterfaceNumber,
                clockSourceEntityIds,
                clockSelectorEntityId,
                clockSelectorControl,
                &selectedIndex)) {
            return false;
        }
        entityOrEndpoint = clockSourceEntityIds[selectedIndex];
        frequencyControl = selectedIndex < clockSourceFrequencyControls.size()
            ? clockSourceFrequencyControls[selectedIndex]
            : -1;
    }

    if ((!uac2 && frequencyControl == 0) || (uac2 && frequencyControl == 0)) {
        uint32_t currentRate = 0;
        if (ReadCurrentSampleRate(
                state, uac2, controlInterfaceNumber, entityOrEndpoint, &currentRate) &&
            currentRate == static_cast<uint32_t>(state->sampleRateHz)) {
            __android_log_print(
                ANDROID_LOG_INFO, kLogTag,
                "USB Audio fixed clock already runs at %d Hz", state->sampleRateHz
            );
            return true;
        }
        if (!uac2) {
            // UAC1 fixed-frequency endpoints do not expose this control. Their alternate
            // setting defines the rate, so a missing control is not a negotiation failure.
            __android_log_print(
                ANDROID_LOG_INFO, kLogTag,
                "UAC1 endpoint has no Sampling Frequency Control; using descriptor-defined rate"
            );
            return true;
        }
        SetError(
            "UAC2 clock 0x" + std::to_string(entityOrEndpoint) +
            " has no writable frequency control for " + std::to_string(state->sampleRateHz) + " Hz"
        );
        return false;
    }

    if (uac2 && frequencyControl == 1) {
        uint32_t currentRate = 0;
        if (ReadCurrentSampleRate(
                state, true, controlInterfaceNumber, entityOrEndpoint, &currentRate) &&
            currentRate == static_cast<uint32_t>(state->sampleRateHz)) {
            __android_log_print(
                ANDROID_LOG_INFO, kLogTag,
                "UAC2 read-only clock 0x%02x already runs at %d Hz",
                entityOrEndpoint, state->sampleRateHz
            );
            return true;
        }
        SetError(
            "UAC2 read-only clock 0x" + std::to_string(entityOrEndpoint) +
            " cannot provide " + std::to_string(state->sampleRateHz) + " Hz"
        );
        return false;
    }
    const uint8_t requestType = static_cast<uint8_t>(
        LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_CLASS |
        (uac2 ? LIBUSB_RECIPIENT_INTERFACE : LIBUSB_RECIPIENT_ENDPOINT)
    );
    const uint16_t index = uac2
        ? static_cast<uint16_t>((entityOrEndpoint << 8) | (controlInterfaceNumber & 0xff))
        : static_cast<uint16_t>(state->endpointAddress & 0xff);
    const int result = libusb_control_transfer(
        state->deviceHandle,
        requestType,
        0x01,
        0x0100,
        index,
        data,
        uac2 ? 4 : 3,
        1000
    );
    if (result != (uac2 ? 4 : 3)) {
        uint32_t currentRate = 0;
        if (ReadCurrentSampleRate(
                state, uac2, controlInterfaceNumber, entityOrEndpoint, &currentRate) &&
            currentRate == static_cast<uint32_t>(state->sampleRateHz)) {
            __android_log_print(
                ANDROID_LOG_WARN, kLogTag,
                "SET_CUR was rejected but clock already reports %d Hz", state->sampleRateHz
            );
            return true;
        }
        SetError(
            "USB Audio SET_CUR " + std::to_string(state->sampleRateHz) +
            " Hz on " + (uac2 ? "clock " : "endpoint ") +
            std::to_string(entityOrEndpoint) + " failed: " + libusb_error_name(result)
        );
        return false;
    }
    uint32_t negotiatedRate = 0;
    bool rateRead = false;
    bool anyRateRead = false;
    bool rateMatched = false;
    for (int attempt = 0; attempt <= 20; ++attempt) {
        rateRead = ReadCurrentSampleRate(
            state, uac2, controlInterfaceNumber, entityOrEndpoint, &negotiatedRate
        );
        anyRateRead = anyRateRead || rateRead;
        if (rateRead && negotiatedRate == static_cast<uint32_t>(state->sampleRateHz)) {
            rateMatched = true;
            break;
        }
        if (attempt < 20) std::this_thread::sleep_for(std::chrono::milliseconds(25));
    }
    if (anyRateRead && !rateMatched) {
        SetError(
            "USB Audio clock accepted SET_CUR but reports " + std::to_string(negotiatedRate) +
            " Hz instead of " + std::to_string(state->sampleRateHz) + " Hz"
        );
        return false;
    }
    if (!anyRateRead) {
        __android_log_print(
            ANDROID_LOG_WARN, kLogTag,
            "USB Audio accepted SET_CUR %d Hz but GET_CUR was unavailable",
            state->sampleRateHz
        );
    }
    __android_log_print(
        ANDROID_LOG_INFO, kLogTag,
        "USB Audio negotiated %d Hz on %s 0x%02x",
        state->sampleRateHz, uac2 ? "clock" : "endpoint", entityOrEndpoint
    );
    return true;
}

void ReleaseState(IsoState* state) {
    if (state == nullptr) return;
    {
        std::unique_lock<std::mutex> lock(state->mutex);
        state->closing = true;
        CancelTransfers(state);
        state->completion.wait(lock, [state]() {
            return state->inFlight == 0 &&
                (state->feedbackSlot == nullptr || !state->feedbackSlot->inFlight);
        });
        state->stopEvents = true;
    }
    libusb_interrupt_event_handler(state->context);
    if (state->eventThread.joinable()) state->eventThread.join();
    state->feedbackSlot.reset();
    state->slots.clear();
    if (state->deviceHandle != nullptr) {
        if (state->streamingInterfaceClaimed) {
            libusb_release_interface(state->deviceHandle, state->interfaceNumber);
        }
        if (state->controlInterfaceClaimed) {
            libusb_release_interface(state->deviceHandle, state->controlInterfaceNumber);
        }
        libusb_close(state->deviceHandle);
    }
    if (state->context != nullptr) libusb_exit(state->context);
    if (state->duplicatedFd >= 0) close(state->duplicatedFd);
    delete state;
}

std::vector<int> CopyIntArray(JNIEnv* env, jintArray values) {
    if (values == nullptr) return {};
    const jsize length = env->GetArrayLength(values);
    std::vector<int> result(static_cast<size_t>(length));
    if (length > 0) {
        env->GetIntArrayRegion(values, 0, length, reinterpret_cast<jint*>(result.data()));
        if (env->ExceptionCheck()) return {};
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_open(
    JNIEnv* env, jobject, jint fileDescriptor, jint endpointAddress, jint maxPacketSize,
    jint interval, jint sampleRateHz, jint bytesPerFrame, jint interfaceNumber,
    jint alternateSetting, jint feedbackEndpointAddress, jint feedbackMaxPacketSize,
    jint audioClassVersion,
    jint controlInterfaceNumber, jintArray clockSourceEntityIds,
    jintArray clockSourceFrequencyControls, jint clockSelectorEntityId,
    jint clockSelectorControl, jint sampleFrequencyControl
) {
    gLastError.clear();
    if (fileDescriptor < 0 || (endpointAddress & LIBUSB_ENDPOINT_IN) != 0 ||
        sampleRateHz <= 0 || bytesPerFrame <= 0 || interval < 1 || interval > 16) {
        SetError("Invalid isochronous endpoint or PCM configuration");
        return 0;
    }
    const int transactions = ((maxPacketSize >> 11) & 0x3) + 1;
    const int maximumPayload = (maxPacketSize & 0x7ff) * transactions;
    if (maximumPayload <= 0) {
        SetError("USB endpoint reports an invalid maximum packet size");
        return 0;
    }

    auto state = std::make_unique<IsoState>();
    state->duplicatedFd = fcntl(fileDescriptor, F_DUPFD_CLOEXEC, 0);
    if (state->duplicatedFd < 0) {
        SetError("Could not duplicate USB file descriptor");
        return 0;
    }
    state->endpointAddress = endpointAddress;
    state->feedbackEndpointAddress = feedbackEndpointAddress;
    state->interfaceNumber = interfaceNumber;
    state->controlInterfaceNumber = controlInterfaceNumber;
    state->maxPacketPayload = maximumPayload;
    state->sampleRateHz = sampleRateHz;
    state->bytesPerFrame = bytesPerFrame;
    const std::vector<int> sourceEntityIds = CopyIntArray(env, clockSourceEntityIds);
    const std::vector<int> sourceFrequencyControls =
        CopyIntArray(env, clockSourceFrequencyControls);

    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    int result = libusb_init(&state->context);
    if (result != LIBUSB_SUCCESS) {
        SetError(LibusbError("libusb_init", result));
        ReleaseState(state.release());
        return 0;
    }
    result = libusb_wrap_sys_device(
        state->context,
        static_cast<intptr_t>(state->duplicatedFd),
        &state->deviceHandle
    );
    if (result != LIBUSB_SUCCESS) {
        SetError(LibusbError("libusb_wrap_sys_device", result));
        ReleaseState(state.release());
        return 0;
    }
    // Android's USB Audio HAL normally leaves snd-usb-audio bound to the streaming
    // interface. Ask the Linux usbfs backend to atomically disconnect that kernel
    // driver and claim the interface for this session; otherwise claim_interface()
    // reports BUSY even though UsbManager granted this app access to the device FD.
    result = libusb_set_auto_detach_kernel_driver(state->deviceHandle, 1);
    if (result != LIBUSB_SUCCESS) {
        SetError(LibusbError("libusb_set_auto_detach_kernel_driver", result));
        ReleaseState(state.release());
        return 0;
    }
    const int usbSpeed = libusb_get_device_speed(libusb_get_device(state->deviceHandle));
    const int baseIntervalUs = usbSpeed == LIBUSB_SPEED_FULL ? 1000 : 125;
    state->serviceIntervalUs = baseIntervalUs << (interval - 1);
    if (controlInterfaceNumber >= 0 && controlInterfaceNumber != interfaceNumber) {
        result = libusb_claim_interface(state->deviceHandle, controlInterfaceNumber);
        if (result != LIBUSB_SUCCESS) {
            SetError(LibusbError("libusb_claim_interface(AudioControl)", result));
            ReleaseState(state.release());
            return 0;
        }
        state->controlInterfaceClaimed = true;
    }
    result = libusb_claim_interface(state->deviceHandle, interfaceNumber);
    if (result != LIBUSB_SUCCESS) {
        SetError(LibusbError("libusb_claim_interface(AudioStreaming)", result));
        ReleaseState(state.release());
        return 0;
    }
    state->streamingInterfaceClaimed = true;
    result = libusb_set_interface_alt_setting(
        state->deviceHandle,
        interfaceNumber,
        0
    );
    if (result != LIBUSB_SUCCESS) {
        SetError(LibusbError("Could not select zero-bandwidth USB Audio alternate setting", result));
        ReleaseState(state.release());
        return 0;
    }
    if (!ConfigureSampleRate(
            state.get(),
            audioClassVersion,
            controlInterfaceNumber,
            sourceEntityIds,
            sourceFrequencyControls,
            clockSelectorEntityId,
            clockSelectorControl,
            sampleFrequencyControl)) {
        ReleaseState(state.release());
        return 0;
    }
    result = libusb_set_interface_alt_setting(
        state->deviceHandle,
        interfaceNumber,
        alternateSetting
    );
    if (result != LIBUSB_SUCCESS) {
        SetError(LibusbError("Could not activate USB Audio streaming alternate setting", result));
        ReleaseState(state.release());
        return 0;
    }

    for (int i = 0; i < kTransferRingSize; ++i) {
        auto slot = std::make_unique<TransferSlot>();
        slot->owner = state.get();
        slot->transfer = libusb_alloc_transfer(kPacketsPerTransfer);
        if (slot->transfer == nullptr) {
            SetError("Could not allocate isochronous transfer ring");
            ReleaseState(state.release());
            return 0;
        }
        slot->buffer.resize(static_cast<size_t>(maximumPayload) * kPacketsPerTransfer);
        state->slots.push_back(std::move(slot));
    }

    if (feedbackEndpointAddress != 0) {
        auto feedback = std::make_unique<TransferSlot>();
        feedback->owner = state.get();
        feedback->transfer = libusb_alloc_transfer(1);
        feedback->buffer.resize(std::clamp(feedbackMaxPacketSize, 3, 4));
        if (feedback->transfer == nullptr) {
            SetError("Could not allocate USB feedback transfer");
            ReleaseState(state.release());
            return 0;
        }
        libusb_fill_iso_transfer(
            feedback->transfer,
            state->deviceHandle,
            static_cast<unsigned char>(feedbackEndpointAddress),
            feedback->buffer.data(),
            static_cast<int>(feedback->buffer.size()),
            1,
            FeedbackCompleted,
            feedback.get(),
            0
        );
        feedback->transfer->iso_packet_desc[0].length = feedback->buffer.size();
        feedback->inFlight = true;
        result = libusb_submit_transfer(feedback->transfer);
        if (result != LIBUSB_SUCCESS) {
            SetError(LibusbError("Could not submit USB feedback transfer", result));
            feedback->inFlight = false;
            ReleaseState(state.release());
            return 0;
        }
        state->feedbackSlot = std::move(feedback);
    }
    state->eventThread = std::thread(EventLoop, state.get());
    return static_cast<jlong>(reinterpret_cast<intptr_t>(state.release()));
}

extern "C" JNIEXPORT jint JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_write(
    JNIEnv* env, jobject, jlong handle, jbyteArray pcmData
) {
    IsoState* state = FromHandle(handle);
    if (state == nullptr || pcmData == nullptr) return -1;
    const jsize inputLength = env->GetArrayLength(pcmData);
    if (inputLength == 0) return 0;

    std::unique_lock<std::mutex> lock(state->mutex);
    if (state->closing) return -1;
    if (state->pending.size() + static_cast<size_t>(inputLength) > kMaxPendingBytes) {
        SetError("USB isochronous staging buffer exceeded safety limit");
        return -1;
    }
    const size_t oldSize = state->pending.size();
    state->pending.resize(oldSize + static_cast<size_t>(inputLength));
    env->GetByteArrayRegion(
        pcmData, 0, inputLength,
        reinterpret_cast<jbyte*>(state->pending.data() + oldSize)
    );
    if (env->ExceptionCheck()) {
        state->pending.resize(oldSize);
        return -1;
    }

    while (!state->closing) {
        std::vector<int> packetLengths;
        size_t consumed = 0;
        int64_t remainder = state->frameRemainder;
        const uint64_t feedbackBits = state->feedbackRateBits.load();
        double feedbackRate = 0.0;
        std::memcpy(&feedbackRate, &feedbackBits, sizeof(feedbackRate));
        const int effectiveRate = feedbackRate > 0.0
            ? static_cast<int>(feedbackRate + 0.5)
            : state->sampleRateHz;
        while (packetLengths.size() < kPacketsPerTransfer) {
            const int64_t scaledFrames =
                static_cast<int64_t>(effectiveRate) * state->serviceIntervalUs + remainder;
            const int64_t frames = scaledFrames / 1000000;
            const int64_t nextRemainder = scaledFrames % 1000000;
            const int64_t bytes = frames * state->bytesPerFrame;
            if (bytes <= 0 || bytes > state->maxPacketPayload ||
                consumed + static_cast<size_t>(bytes) > state->pending.size()) {
                break;
            }
            packetLengths.push_back(static_cast<int>(bytes));
            consumed += static_cast<size_t>(bytes);
            remainder = nextRemainder;
        }
        if (packetLengths.empty()) break;
        TransferSlot* slot = WaitForFreeSlot(state, lock);
        if (slot == nullptr) {
            SetError("USB isochronous transfer ring is full");
            state->failedPackets.fetch_add(packetLengths.size());
            return -1;
        }
        std::copy_n(state->pending.data(), consumed, slot->buffer.data());
        libusb_fill_iso_transfer(
            slot->transfer,
            state->deviceHandle,
            static_cast<unsigned char>(state->endpointAddress),
            slot->buffer.data(),
            static_cast<int>(consumed),
            static_cast<int>(packetLengths.size()),
            TransferCompleted,
            slot,
            0
        );
        for (size_t i = 0; i < packetLengths.size(); ++i) {
            slot->transfer->iso_packet_desc[i].length = packetLengths[i];
        }
        slot->inFlight = true;
        ++state->inFlight;
        const int result = libusb_submit_transfer(slot->transfer);
        if (result != LIBUSB_SUCCESS) {
            slot->inFlight = false;
            --state->inFlight;
            state->failedPackets.fetch_add(packetLengths.size());
            SetError(LibusbError("libusb_submit_transfer", result));
            return -1;
        }
        state->submittedPackets.fetch_add(packetLengths.size());
        state->pending.erase(
            state->pending.begin(),
            state->pending.begin() + static_cast<ptrdiff_t>(consumed)
        );
        state->frameRemainder = remainder;
    }
    return inputLength;
}

extern "C" JNIEXPORT void JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_reset(
    JNIEnv*, jobject, jlong handle
) {
    IsoState* state = FromHandle(handle);
    if (state == nullptr) return;
    std::lock_guard<std::mutex> lock(state->mutex);
    state->pending.clear();
    state->frameRemainder = 0;
}

extern "C" JNIEXPORT void JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_cancel(
    JNIEnv*, jobject, jlong handle
) {
    IsoState* state = FromHandle(handle);
    if (state == nullptr) return;
    std::lock_guard<std::mutex> lock(state->mutex);
    state->closing = true;
    CancelTransfers(state);
}

extern "C" JNIEXPORT void JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_close(
    JNIEnv*, jobject, jlong handle
) {
    ReleaseState(FromHandle(handle));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_metrics(
    JNIEnv* env, jobject, jlong handle
) {
    IsoState* state = FromHandle(handle);
    jlong values[4] = {0, 0, 0, 0};
    if (state != nullptr) {
        values[0] = static_cast<jlong>(state->submittedPackets.load());
        values[1] = static_cast<jlong>(state->completedPackets.load());
        values[2] = static_cast<jlong>(state->failedPackets.load());
        values[3] = static_cast<jlong>(state->feedbackRateBits.load());
    }
    jlongArray result = env->NewLongArray(4);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_yukine_playback_usb_NativeUsbIsoBridge_lastError(
    JNIEnv* env, jobject
) {
    return env->NewStringUTF(gLastError.c_str());
}
