mod ffi;

use crate::protocol::{
    decode_frequency_control_access, feedback_rate, first_free_slot, packet_lengths,
    pending_within_limit, should_allow_uac2_pcm_rate_mismatch, FrequencyControlAccess,
    TransferLifecycle, PACKETS_PER_TRANSFER, TRANSFER_RING_SIZE,
};
use jni::objects::{JByteArray, JIntArray, JObject};
use jni::sys::{jboolean, jbyteArray, jint, jintArray, jlong, jlongArray, jstring, JNI_TRUE};
use jni::JNIEnv;
use libc::{c_void, fcntl, F_DUPFD_CLOEXEC};
use std::ffi::{CStr, CString};
use std::ptr;
use std::sync::atomic::{AtomicI64, AtomicU64, Ordering};
use std::sync::{Condvar, Mutex, MutexGuard, OnceLock};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

const QUEUE_WAIT: Duration = Duration::from_millis(500);
const CLOCK_SET_ATTEMPTS: i32 = 3;
const CLOCK_READBACK_ATTEMPTS: i32 = 21;
const CLOCK_READBACK_DELAY: Duration = Duration::from_millis(25);
const CLOCK_REWRITE_DELAY: Duration = Duration::from_millis(50);
const CLOCK_CLAIM_SETTLE_DELAY: Duration = Duration::from_millis(75);
const UAC1_GET_CUR_REQUEST: u8 = 0x81;
const UAC2_CUR_REQUEST: u8 = 0x01;
const SET_CUR_REQUEST: u8 = 0x01;

static LAST_ERROR: OnceLock<Mutex<String>> = OnceLock::new();

fn error_store() -> &'static Mutex<String> {
    LAST_ERROR.get_or_init(|| Mutex::new(String::new()))
}

fn lock_unpoisoned<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn clear_error() {
    lock_unpoisoned(error_store()).clear();
}

fn last_error() -> String {
    lock_unpoisoned(error_store()).clone()
}

fn log_message(priority: i32, message: &str) {
    let clean = message.replace('\0', " ");
    if let Ok(value) = CString::new(clean) {
        unsafe { ffi::echo_usb_log(priority, value.as_ptr()) };
    }
}

fn set_error(message: impl Into<String>) {
    let message = message.into();
    *lock_unpoisoned(error_store()) = message.clone();
    log_message(ffi::LOG_ERROR, &message);
}

fn libusb_error(operation: &str, code: i32) -> String {
    let name = unsafe {
        let raw = ffi::echo_usb_error_name(code);
        if raw.is_null() {
            format!("error {code}")
        } else {
            CStr::from_ptr(raw).to_string_lossy().into_owned()
        }
    };
    format!("{operation}: {name}")
}

#[derive(Debug)]
struct OpenConfig {
    file_descriptor: i32,
    endpoint_address: i32,
    max_packet_size: i32,
    interval: i32,
    sample_rate_hz: i32,
    bytes_per_frame: i32,
    interface_number: i32,
    alternate_setting: i32,
    feedback_endpoint_address: i32,
    feedback_max_packet_size: i32,
    audio_class_version: i32,
    control_interface_number: i32,
    clock_source_entity_ids: Vec<i32>,
    clock_source_frequency_controls: Vec<i32>,
    clock_selector_entity_id: i32,
    clock_selector_control: i32,
    sample_frequency_control: i32,
    allow_uac2_pcm_rate_mismatch: bool,
}

struct TransferSlot {
    owner: *mut IsoState,
    transfer: *mut ffi::LibusbTransfer,
    buffer: Vec<u8>,
    in_flight: bool,
}

unsafe impl Send for TransferSlot {}
unsafe impl Sync for TransferSlot {}

impl Drop for TransferSlot {
    fn drop(&mut self) {
        if !self.transfer.is_null() {
            unsafe { ffi::echo_usb_free_transfer(self.transfer) };
            self.transfer = ptr::null_mut();
        }
    }
}

#[derive(Default)]
struct SyncState {
    frame_remainder: i64,
    pending: Vec<u8>,
    lifecycle: TransferLifecycle,
    stop_events: bool,
}

struct IsoState {
    context: *mut ffi::LibusbContext,
    device_handle: *mut ffi::LibusbDeviceHandle,
    duplicated_fd: i32,
    endpoint_address: i32,
    interface_number: i32,
    alternate_setting: i32,
    control_interface_number: i32,
    streaming_interface_claimed: bool,
    control_interface_claimed: bool,
    max_packet_payload: i32,
    sample_rate_hz: i32,
    bytes_per_frame: i32,
    service_interval_us: i32,
    // libusb keeps each slot address as callback user data, so entries must not move.
    #[allow(clippy::vec_box)]
    slots: Vec<Box<TransferSlot>>,
    feedback_slot: Option<Box<TransferSlot>>,
    event_thread: Option<JoinHandle<()>>,
    sync: Mutex<SyncState>,
    completion: Condvar,
    submitted_packets: AtomicI64,
    completed_packets: AtomicI64,
    failed_packets: AtomicI64,
    feedback_rate_bits: AtomicU64,
}

unsafe impl Send for IsoState {}
unsafe impl Sync for IsoState {}

impl IsoState {
    fn new(config: &OpenConfig, maximum_payload: i32) -> Self {
        Self {
            context: ptr::null_mut(),
            device_handle: ptr::null_mut(),
            duplicated_fd: -1,
            endpoint_address: config.endpoint_address,
            interface_number: config.interface_number,
            alternate_setting: config.alternate_setting,
            control_interface_number: config.control_interface_number,
            streaming_interface_claimed: false,
            control_interface_claimed: false,
            max_packet_payload: maximum_payload,
            sample_rate_hz: config.sample_rate_hz,
            bytes_per_frame: config.bytes_per_frame,
            service_interval_us: 0,
            slots: Vec::with_capacity(TRANSFER_RING_SIZE),
            feedback_slot: None,
            event_thread: None,
            sync: Mutex::new(SyncState::default()),
            completion: Condvar::new(),
            submitted_packets: AtomicI64::new(0),
            completed_packets: AtomicI64::new(0),
            failed_packets: AtomicI64::new(0),
            feedback_rate_bits: AtomicU64::new(0),
        }
    }

    fn cancel_transfers_locked(&self) {
        for slot in &self.slots {
            if slot.in_flight {
                unsafe {
                    ffi::echo_usb_cancel_transfer(slot.transfer);
                }
            }
        }
        if let Some(slot) = self.feedback_slot.as_ref() {
            if slot.in_flight {
                unsafe {
                    ffi::echo_usb_cancel_transfer(slot.transfer);
                }
            }
        }
    }

    fn shutdown(&mut self) {
        {
            let mut sync = lock_unpoisoned(&self.sync);
            sync.lifecycle.cancel();
            self.cancel_transfers_locked();
            while !sync.lifecycle.is_drained()
                || self
                    .feedback_slot
                    .as_ref()
                    .is_some_and(|slot| slot.in_flight)
            {
                sync = self
                    .completion
                    .wait(sync)
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
            }
            sync.stop_events = true;
        }

        if !self.context.is_null() {
            unsafe { ffi::echo_usb_interrupt_events(self.context) };
        }
        if let Some(thread) = self.event_thread.take() {
            let _ = thread.join();
        }

        self.feedback_slot.take();
        self.slots.clear();

        if !self.device_handle.is_null() {
            if self.streaming_interface_claimed {
                let result = unsafe {
                    ffi::echo_usb_set_alt_setting(self.device_handle, self.interface_number, 0)
                };
                if result != ffi::SUCCESS {
                    log_message(
                        ffi::LOG_WARN,
                        &format!(
                            "Could not restore zero-bandwidth alternate setting during close: {}",
                            libusb_error("set_interface_alt_setting", result)
                        ),
                    );
                }
                unsafe {
                    ffi::echo_usb_release_interface(self.device_handle, self.interface_number);
                }
                self.streaming_interface_claimed = false;
            }
            if self.control_interface_claimed {
                unsafe {
                    ffi::echo_usb_release_interface(
                        self.device_handle,
                        self.control_interface_number,
                    );
                }
                self.control_interface_claimed = false;
            }
            unsafe { ffi::echo_usb_close(self.device_handle) };
            self.device_handle = ptr::null_mut();
        }
        if !self.context.is_null() {
            unsafe { ffi::echo_usb_exit(self.context) };
            self.context = ptr::null_mut();
        }
        if self.duplicated_fd >= 0 {
            unsafe { libc::close(self.duplicated_fd) };
            self.duplicated_fd = -1;
        }
    }
}

impl Drop for IsoState {
    fn drop(&mut self) {
        self.shutdown();
    }
}

unsafe extern "C" fn feedback_completed(transfer: *mut ffi::LibusbTransfer) {
    let slot = unsafe { ffi::echo_usb_transfer_user_data(transfer) as *mut TransferSlot };
    if slot.is_null() {
        return;
    }
    let state = unsafe { (*slot).owner };
    if state.is_null() {
        return;
    }

    let status = unsafe { ffi::echo_usb_transfer_status(transfer) };
    let actual_length = unsafe { ffi::echo_usb_transfer_actual_length(transfer) };
    if status == ffi::TRANSFER_COMPLETED && actual_length >= 3 {
        let buffer = unsafe { &(*slot).buffer };
        let rate = feedback_rate(&buffer[..(actual_length as usize).min(buffer.len())]);
        if rate > 0.0 {
            unsafe {
                (*state)
                    .feedback_rate_bits
                    .store(rate.to_bits(), Ordering::SeqCst);
            }
        }
    }

    let sync = unsafe { lock_unpoisoned(&(*state).sync) };
    if !sync.lifecycle.is_closing() && !sync.stop_events {
        let result = unsafe { ffi::echo_usb_submit_transfer(transfer) };
        if result == ffi::SUCCESS {
            return;
        }
        set_error(libusb_error("Could not resubmit feedback transfer", result));
    }
    unsafe {
        (*slot).in_flight = false;
        (*state).completion.notify_all();
    }
}

unsafe extern "C" fn transfer_completed(transfer: *mut ffi::LibusbTransfer) {
    let slot = unsafe { ffi::echo_usb_transfer_user_data(transfer) as *mut TransferSlot };
    if slot.is_null() {
        return;
    }
    let state = unsafe { (*slot).owner };
    if state.is_null() {
        return;
    }

    let status = unsafe { ffi::echo_usb_transfer_status(transfer) };
    let packet_count = unsafe { ffi::echo_usb_transfer_packet_count(transfer) }.max(0);
    let mut completed = 0_i64;
    let mut failed = 0_i64;
    if status == ffi::TRANSFER_COMPLETED {
        for packet in 0..packet_count {
            let packet_status = unsafe { ffi::echo_usb_packet_status(transfer, packet) };
            let actual = unsafe { ffi::echo_usb_packet_actual_length(transfer, packet) };
            let expected = unsafe { ffi::echo_usb_packet_length(transfer, packet) };
            if packet_status == ffi::TRANSFER_COMPLETED && actual == expected {
                completed += 1;
            } else {
                failed += 1;
            }
        }
    } else if status != ffi::TRANSFER_CANCELLED {
        failed = i64::from(packet_count);
        set_error(format!("Isochronous transfer failed with status {status}"));
    }

    unsafe {
        (*state)
            .completed_packets
            .fetch_add(completed, Ordering::SeqCst);
        (*state).failed_packets.fetch_add(failed, Ordering::SeqCst);
        if failed > 0 && status == ffi::TRANSFER_COMPLETED {
            set_error(format!(
                "Isochronous transfer completed with {failed} failed or short packets"
            ));
        }
        let mut sync = lock_unpoisoned(&(*state).sync);
        (*slot).in_flight = false;
        sync.lifecycle.completed(failed > 0);
        (*state).completion.notify_all();
    }
}

fn event_loop(state_address: usize) {
    let state = state_address as *mut IsoState;
    loop {
        let should_stop = unsafe {
            let sync = lock_unpoisoned(&(*state).sync);
            sync.stop_events
                && sync.lifecycle.is_drained()
                && (*state)
                    .feedback_slot
                    .as_ref()
                    .is_none_or(|slot| !slot.in_flight)
        };
        if should_stop {
            return;
        }
        let result = unsafe { ffi::echo_usb_handle_events((*state).context, 100_000) };
        if result != ffi::SUCCESS && result != ffi::ERROR_INTERRUPTED {
            set_error(libusb_error("libusb event loop", result));
            let mut sync = unsafe { lock_unpoisoned(&(*state).sync) };
            sync.lifecycle.cancel();
            unsafe { (*state).completion.notify_all() };
        }
    }
}

fn read_current_sample_rate(
    state: &IsoState,
    uac2: bool,
    control_interface_number: i32,
    entity_or_endpoint: i32,
) -> Option<u32> {
    let mut current = [0_u8; 4];
    let request_type = ffi::ENDPOINT_IN
        | ffi::REQUEST_TYPE_CLASS
        | if uac2 {
            ffi::RECIPIENT_INTERFACE
        } else {
            ffi::RECIPIENT_ENDPOINT
        };
    let index = if uac2 {
        ((entity_or_endpoint << 8) | (control_interface_number & 0xff)) as u16
    } else {
        (entity_or_endpoint & 0xff) as u16
    };
    let expected = if uac2 { 4 } else { 3 };
    let result = unsafe {
        ffi::echo_usb_control_transfer(
            state.device_handle,
            request_type,
            if uac2 {
                UAC2_CUR_REQUEST
            } else {
                UAC1_GET_CUR_REQUEST
            },
            0x0100,
            index,
            current.as_mut_ptr(),
            expected,
            1000,
        )
    };
    if result != i32::from(expected) {
        return None;
    }
    let mut rate = 0_u32;
    for (index, value) in current.iter().take(expected as usize).enumerate() {
        rate |= u32::from(*value) << (8 * index);
    }
    Some(rate)
}

fn resolve_uac2_clock_source(state: &IsoState, config: &OpenConfig) -> Result<usize, String> {
    if config.clock_source_entity_ids.is_empty() {
        return Err(
            "UAC2 clock source entity was not resolved from the streaming terminal".to_string(),
        );
    }
    if config.clock_selector_entity_id <= 0 {
        return Ok(0);
    }

    let mut selected_pin = 0_u8;
    let result = unsafe {
        ffi::echo_usb_control_transfer(
            state.device_handle,
            ffi::ENDPOINT_IN | ffi::REQUEST_TYPE_CLASS | ffi::RECIPIENT_INTERFACE,
            UAC2_CUR_REQUEST,
            0x0100,
            ((config.clock_selector_entity_id << 8) | (config.control_interface_number & 0xff))
                as u16,
            &mut selected_pin,
            1,
            1000,
        )
    };
    if result == 1
        && selected_pin >= 1
        && usize::from(selected_pin) <= config.clock_source_entity_ids.len()
    {
        let selected_index = usize::from(selected_pin - 1);
        log_message(
            ffi::LOG_INFO,
            &format!(
                "UAC2 selector 0x{:02x} chose pin {} -> clock 0x{:02x}",
                config.clock_selector_entity_id,
                selected_pin,
                config.clock_source_entity_ids[selected_index]
            ),
        );
        return Ok(selected_index);
    }
    if config.clock_source_entity_ids.len() == 1 {
        log_message(
            ffi::LOG_WARN,
            &format!(
                "UAC2 selector 0x{:02x} GET_CUR failed ({}, control={}); using its only source 0x{:02x}",
                config.clock_selector_entity_id,
                result,
                config.clock_selector_control,
                config.clock_source_entity_ids[0]
            ),
        );
        return Ok(0);
    }
    Err(format!(
        "UAC2 clock selector 0x{} did not return a valid source pin: {}",
        config.clock_selector_entity_id,
        libusb_error("GET_CUR", result)
    ))
}

fn configure_sample_rate(state: &mut IsoState, config: &OpenConfig) -> Result<(), String> {
    let mut data = config.sample_rate_hz.to_le_bytes();
    let uac2 = config.audio_class_version >= 2;
    let mut entity_or_endpoint = state.endpoint_address;
    let mut frequency_control = config.sample_frequency_control;
    if uac2 {
        let selected_index = resolve_uac2_clock_source(state, config)?;
        entity_or_endpoint = config.clock_source_entity_ids[selected_index];
        frequency_control = config
            .clock_source_frequency_controls
            .get(selected_index)
            .copied()
            .unwrap_or(-1);
    }

    let access = decode_frequency_control_access(uac2, frequency_control);
    let mut negotiation_context = format!(
        " [rate={}, uac={}, controlInterface={}, streamInterface={}, alt={}, endpoint={}, clockSource={}, clockSelector={}, selectorControl={}, rawFrequencyControl={}]",
        state.sample_rate_hz,
        config.audio_class_version,
        config.control_interface_number,
        state.interface_number,
        state.alternate_setting,
        state.endpoint_address,
        entity_or_endpoint,
        config.clock_selector_entity_id,
        config.clock_selector_control,
        frequency_control
    );

    match access {
        FrequencyControlAccess::Unknown if !uac2 => {
            log_message(
                ffi::LOG_WARN,
                "UAC1 Sampling Frequency Control is unknown; using descriptor-defined rate",
            );
            return Ok(());
        }
        FrequencyControlAccess::Unknown => {
            return Err(format!(
                "UAC2 clock frequency control is unknown for {} Hz{}",
                state.sample_rate_hz, negotiation_context
            ));
        }
        FrequencyControlAccess::Absent if !uac2 => {
            log_message(
                ffi::LOG_INFO,
                "UAC1 endpoint has no Sampling Frequency Control; using descriptor-defined rate",
            );
            return Ok(());
        }
        FrequencyControlAccess::Absent => {
            return Err(format!(
                "UAC2 clock 0x{} has no Sampling Frequency Control for {} Hz{}",
                entity_or_endpoint, state.sample_rate_hz, negotiation_context
            ));
        }
        FrequencyControlAccess::ReadOnly => {
            if read_current_sample_rate(
                state,
                uac2,
                config.control_interface_number,
                entity_or_endpoint,
            ) == Some(state.sample_rate_hz as u32)
            {
                log_message(
                    ffi::LOG_INFO,
                    &format!(
                        "USB Audio read-only {} 0x{:02x} already runs at {} Hz",
                        if uac2 { "clock" } else { "endpoint" },
                        entity_or_endpoint,
                        state.sample_rate_hz
                    ),
                );
                return Ok(());
            }
            return Err(format!(
                "USB Audio read-only {} 0x{} cannot provide {} Hz{}",
                if uac2 { "clock" } else { "endpoint" },
                entity_or_endpoint,
                state.sample_rate_hz,
                negotiation_context
            ));
        }
        FrequencyControlAccess::Invalid => {
            return Err(format!(
                "USB Audio frequency control mode {} {}{}",
                frequency_control,
                if uac2 {
                    "is reserved/invalid for UAC2"
                } else {
                    "is invalid for UAC1"
                },
                negotiation_context
            ));
        }
        FrequencyControlAccess::ReadWrite => {}
    }

    let request_type = ffi::ENDPOINT_OUT
        | ffi::REQUEST_TYPE_CLASS
        | if uac2 {
            ffi::RECIPIENT_INTERFACE
        } else {
            ffi::RECIPIENT_ENDPOINT
        };
    let request_index = if uac2 {
        ((entity_or_endpoint << 8) | (config.control_interface_number & 0xff)) as u16
    } else {
        (state.endpoint_address & 0xff) as u16
    };
    let expected_length = if uac2 { 4_u16 } else { 3_u16 };
    let mut negotiated_rate = 0_u32;
    let mut any_rate_read = false;
    let mut any_set_accepted = false;
    let mut active_alt_retry = false;
    let mut last_set_result = -99;

    for set_attempt in 1..=CLOCK_SET_ATTEMPTS {
        last_set_result = unsafe {
            ffi::echo_usb_control_transfer(
                state.device_handle,
                request_type,
                SET_CUR_REQUEST,
                0x0100,
                request_index,
                data.as_mut_ptr(),
                expected_length,
                1000,
            )
        };
        any_set_accepted |= last_set_result == i32::from(expected_length);

        for read_attempt in 0..CLOCK_READBACK_ATTEMPTS {
            if let Some(rate) = read_current_sample_rate(
                state,
                uac2,
                config.control_interface_number,
                entity_or_endpoint,
            ) {
                any_rate_read = true;
                negotiated_rate = rate;
                if rate == state.sample_rate_hz as u32 {
                    if last_set_result != i32::from(expected_length) {
                        log_message(
                            ffi::LOG_WARN,
                            &format!(
                                "SET_CUR was rejected but clock already reports {} Hz",
                                state.sample_rate_hz
                            ),
                        );
                    }
                    log_message(
                        ffi::LOG_INFO,
                        &format!(
                            "USB Audio negotiated {} Hz on {} 0x{:02x} after SET_CUR attempt {}",
                            state.sample_rate_hz,
                            if uac2 { "clock" } else { "endpoint" },
                            entity_or_endpoint,
                            set_attempt
                        ),
                    );
                    return Ok(());
                }
            }
            if read_attempt + 1 < CLOCK_READBACK_ATTEMPTS {
                thread::sleep(CLOCK_READBACK_DELAY);
            }
        }

        if set_attempt < CLOCK_SET_ATTEMPTS {
            if uac2 && !active_alt_retry && state.alternate_setting > 0 {
                let alt_result = unsafe {
                    ffi::echo_usb_set_alt_setting(
                        state.device_handle,
                        state.interface_number,
                        state.alternate_setting,
                    )
                };
                if alt_result == ffi::SUCCESS {
                    active_alt_retry = true;
                    if let Some(index) = negotiation_context.rfind(']') {
                        negotiation_context.insert_str(index, ", clockWriteMode=active-alt-retry");
                    }
                    log_message(
                        ffi::LOG_WARN,
                        &format!(
                            "USB Audio clock did not latch at alt 0; activated streaming alt {} before SET_CUR retry",
                            state.alternate_setting
                        ),
                    );
                } else {
                    log_message(
                        ffi::LOG_WARN,
                        &format!(
                            "Could not activate streaming alt {} for clock retry: {}",
                            state.alternate_setting,
                            libusb_error("set_interface_alt_setting", alt_result)
                        ),
                    );
                }
            }
            log_message(
                ffi::LOG_WARN,
                &format!(
                    "USB Audio SET_CUR attempt {}/{} did not latch {} Hz (last read {} Hz); retrying",
                    set_attempt,
                    CLOCK_SET_ATTEMPTS,
                    state.sample_rate_hz,
                    negotiated_rate
                ),
            );
            thread::sleep(CLOCK_REWRITE_DELAY);
        }
    }

    if !any_set_accepted {
        return Err(format!(
            "USB Audio SET_CUR {} Hz on {} {} failed: {}{}",
            state.sample_rate_hz,
            if uac2 { "clock" } else { "endpoint" },
            entity_or_endpoint,
            libusb_error("SET_CUR", last_set_result),
            negotiation_context
        ));
    }
    if any_rate_read {
        if should_allow_uac2_pcm_rate_mismatch(
            config.allow_uac2_pcm_rate_mismatch,
            uac2,
            access,
            any_set_accepted,
            any_rate_read,
            false,
        ) {
            log_message(
                ffi::LOG_WARN,
                &format!(
                    "USB Audio clock mismatch override: requestedRate={} reportedRate={} clockMismatchOverride=true{}",
                    state.sample_rate_hz, negotiated_rate, negotiation_context
                ),
            );
            return Ok(());
        }
        return Err(format!(
            "USB Audio clock accepted SET_CUR but reports {} Hz instead of {} Hz{}",
            negotiated_rate, state.sample_rate_hz, negotiation_context
        ));
    }

    log_message(
        ffi::LOG_WARN,
        &format!(
            "USB Audio accepted SET_CUR {} Hz but GET_CUR was unavailable",
            state.sample_rate_hz
        ),
    );
    log_message(
        ffi::LOG_INFO,
        &format!(
            "USB Audio accepted unverified {} Hz on {} 0x{:02x}",
            state.sample_rate_hz,
            if uac2 { "clock" } else { "endpoint" },
            entity_or_endpoint
        ),
    );
    Ok(())
}

fn open_state(config: OpenConfig) -> Result<*mut IsoState, String> {
    if config.file_descriptor < 0
        || (config.endpoint_address & i32::from(ffi::ENDPOINT_IN)) != 0
        || config.sample_rate_hz <= 0
        || config.bytes_per_frame <= 0
        || !(1..=16).contains(&config.interval)
    {
        return Err("Invalid isochronous endpoint or PCM configuration".to_string());
    }
    let transactions = ((config.max_packet_size >> 11) & 0x3) + 1;
    let maximum_payload = (config.max_packet_size & 0x7ff) * transactions;
    if maximum_payload <= 0 {
        return Err("USB endpoint reports an invalid maximum packet size".to_string());
    }

    let session_context = format!(
        " [rate={}, uac={}, controlInterface={}, streamInterface={}, alt={}, endpoint={}, clockSelector={}, selectorControl={}, endpointFrequencyControl={}, allowUac2PcmRateMismatch={}]",
        config.sample_rate_hz,
        config.audio_class_version,
        config.control_interface_number,
        config.interface_number,
        config.alternate_setting,
        config.endpoint_address,
        config.clock_selector_entity_id,
        config.clock_selector_control,
        config.sample_frequency_control,
        config.allow_uac2_pcm_rate_mismatch
    );
    let mut state = Box::new(IsoState::new(&config, maximum_payload));
    state.duplicated_fd = unsafe { fcntl(config.file_descriptor, F_DUPFD_CLOEXEC, 0) };
    if state.duplicated_fd < 0 {
        return Err("Could not duplicate USB file descriptor".to_string());
    }

    unsafe {
        ffi::echo_usb_set_no_device_discovery();
    }
    let mut result = unsafe { ffi::echo_usb_init(&mut state.context) };
    if result != ffi::SUCCESS {
        return Err(format!(
            "{}{}",
            libusb_error("libusb_init", result),
            session_context
        ));
    }
    result = unsafe {
        ffi::echo_usb_wrap_sys_device(
            state.context,
            state.duplicated_fd as isize,
            &mut state.device_handle,
        )
    };
    if result != ffi::SUCCESS {
        return Err(format!(
            "{}{}",
            libusb_error("libusb_wrap_sys_device", result),
            session_context
        ));
    }
    result = unsafe { ffi::echo_usb_set_auto_detach(state.device_handle, 1) };
    if result != ffi::SUCCESS {
        return Err(format!(
            "{}{}",
            libusb_error("libusb_set_auto_detach_kernel_driver", result),
            session_context
        ));
    }
    let usb_speed = unsafe { ffi::echo_usb_get_device_speed(state.device_handle) };
    let base_interval_us = if usb_speed == ffi::SPEED_FULL {
        1000
    } else {
        125
    };
    state.service_interval_us = base_interval_us << (config.interval - 1);

    if config.control_interface_number >= 0
        && config.control_interface_number != config.interface_number
    {
        result = unsafe {
            ffi::echo_usb_claim_interface(state.device_handle, config.control_interface_number)
        };
        if result != ffi::SUCCESS {
            return Err(format!(
                "{}{}",
                libusb_error("libusb_claim_interface(AudioControl)", result),
                session_context
            ));
        }
        state.control_interface_claimed = true;
    }
    result = unsafe { ffi::echo_usb_claim_interface(state.device_handle, config.interface_number) };
    if result != ffi::SUCCESS {
        return Err(format!(
            "{}{}",
            libusb_error("libusb_claim_interface(AudioStreaming)", result),
            session_context
        ));
    }
    state.streaming_interface_claimed = true;
    result =
        unsafe { ffi::echo_usb_set_alt_setting(state.device_handle, config.interface_number, 0) };
    if result != ffi::SUCCESS {
        return Err(format!(
            "{}{}",
            libusb_error(
                "Could not select zero-bandwidth USB Audio alternate setting",
                result
            ),
            session_context
        ));
    }
    thread::sleep(CLOCK_CLAIM_SETTLE_DELAY);
    configure_sample_rate(&mut state, &config)?;
    result = unsafe {
        ffi::echo_usb_set_alt_setting(
            state.device_handle,
            config.interface_number,
            config.alternate_setting,
        )
    };
    if result != ffi::SUCCESS {
        return Err(format!(
            "{}{}",
            libusb_error(
                "Could not activate USB Audio streaming alternate setting",
                result
            ),
            session_context
        ));
    }

    let state_pointer = &mut *state as *mut IsoState;
    for _ in 0..TRANSFER_RING_SIZE {
        let transfer = unsafe { ffi::echo_usb_alloc_transfer(PACKETS_PER_TRANSFER as i32) };
        if transfer.is_null() {
            return Err("Could not allocate isochronous transfer ring".to_string());
        }
        state.slots.push(Box::new(TransferSlot {
            owner: state_pointer,
            transfer,
            buffer: vec![0; maximum_payload as usize * PACKETS_PER_TRANSFER],
            in_flight: false,
        }));
    }

    let event_state = state_pointer as usize;
    state.event_thread = Some(
        thread::Builder::new()
            .name("echo-usb-iso-rust".to_string())
            .spawn(move || event_loop(event_state))
            .map_err(|error| format!("Could not start libusb event thread: {error}"))?,
    );

    if config.feedback_endpoint_address != 0 {
        let transfer = unsafe { ffi::echo_usb_alloc_transfer(1) };
        if transfer.is_null() {
            return Err("Could not allocate USB feedback transfer".to_string());
        }
        let feedback_size = config.feedback_max_packet_size.clamp(3, 4) as usize;
        state.feedback_slot = Some(Box::new(TransferSlot {
            owner: state_pointer,
            transfer,
            buffer: vec![0; feedback_size],
            in_flight: false,
        }));
        let feedback = state
            .feedback_slot
            .as_mut()
            .expect("feedback slot installed");
        unsafe {
            ffi::echo_usb_fill_iso_transfer(
                feedback.transfer,
                state.device_handle,
                config.feedback_endpoint_address as u8,
                feedback.buffer.as_mut_ptr(),
                feedback.buffer.len() as i32,
                1,
                Some(feedback_completed),
                (&mut **feedback as *mut TransferSlot).cast::<c_void>(),
                0,
            );
            ffi::echo_usb_set_packet_length(feedback.transfer, 0, feedback.buffer.len() as u32);
        }
        feedback.in_flight = true;
        result = unsafe { ffi::echo_usb_submit_transfer(feedback.transfer) };
        if result != ffi::SUCCESS {
            feedback.in_flight = false;
            return Err(libusb_error(
                "Could not submit USB feedback transfer",
                result,
            ));
        }
    }

    Ok(Box::into_raw(state))
}

unsafe fn state_from_handle(handle: jlong) -> Option<&'static mut IsoState> {
    let pointer = handle as isize as *mut IsoState;
    unsafe { pointer.as_mut() }
}

fn write_state(state: &mut IsoState, input: &[u8]) -> Result<i32, String> {
    if input.is_empty() {
        return Ok(0);
    }
    let mut sync = lock_unpoisoned(&state.sync);
    if !sync.lifecycle.accepts_writes() {
        return Err(String::new());
    }
    if !pending_within_limit(sync.pending.len(), input.len()) {
        return Err("USB isochronous staging buffer exceeded safety limit".to_string());
    }
    sync.pending.extend_from_slice(input);

    while !sync.lifecycle.is_closing() {
        let feedback_rate = f64::from_bits(state.feedback_rate_bits.load(Ordering::SeqCst));
        let effective_rate = if feedback_rate > 0.0 {
            feedback_rate.round() as i32
        } else {
            state.sample_rate_hz
        };
        let (lengths, consumed, remainder) = packet_lengths(
            sync.pending.len(),
            effective_rate,
            state.service_interval_us,
            state.bytes_per_frame,
            state.max_packet_payload,
            sync.frame_remainder,
        );
        if lengths.is_empty() {
            break;
        }

        let deadline = Instant::now() + QUEUE_WAIT;
        let slot_index = loop {
            if sync.lifecycle.is_closing() {
                return Err(String::new());
            }
            if let Some(index) = first_free_slot(state.slots.iter().map(|slot| slot.in_flight)) {
                break index;
            }
            let now = Instant::now();
            if now >= deadline {
                state
                    .failed_packets
                    .fetch_add(lengths.len() as i64, Ordering::SeqCst);
                return Err("USB isochronous transfer ring is full".to_string());
            }
            let (next_sync, wait_result) = state
                .completion
                .wait_timeout(sync, deadline.saturating_duration_since(now))
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            sync = next_sync;
            if wait_result.timed_out() && !state.slots.iter().any(|slot| !slot.in_flight) {
                state
                    .failed_packets
                    .fetch_add(lengths.len() as i64, Ordering::SeqCst);
                return Err("USB isochronous transfer ring is full".to_string());
            }
        };

        let slot = &mut state.slots[slot_index];
        slot.buffer[..consumed].copy_from_slice(&sync.pending[..consumed]);
        unsafe {
            ffi::echo_usb_fill_iso_transfer(
                slot.transfer,
                state.device_handle,
                state.endpoint_address as u8,
                slot.buffer.as_mut_ptr(),
                consumed as i32,
                lengths.len() as i32,
                Some(transfer_completed),
                (&mut **slot as *mut TransferSlot).cast::<c_void>(),
                0,
            );
            for (index, length) in lengths.iter().enumerate() {
                ffi::echo_usb_set_packet_length(slot.transfer, index as i32, *length as u32);
            }
        }
        slot.in_flight = true;
        sync.lifecycle.submitted();
        let result = unsafe { ffi::echo_usb_submit_transfer(slot.transfer) };
        if result != ffi::SUCCESS {
            slot.in_flight = false;
            sync.lifecycle.submit_failed();
            state
                .failed_packets
                .fetch_add(lengths.len() as i64, Ordering::SeqCst);
            return Err(libusb_error("libusb_submit_transfer", result));
        }
        state
            .submitted_packets
            .fetch_add(lengths.len() as i64, Ordering::SeqCst);
        sync.pending.drain(..consumed);
        sync.frame_remainder = remainder;
    }
    Ok(input.len() as i32)
}

fn read_int_array(env: &mut JNIEnv<'_>, array: jintArray) -> jni::errors::Result<Vec<i32>> {
    if array.is_null() {
        return Ok(Vec::new());
    }
    let array = unsafe { JIntArray::from_raw(array) };
    let length = env.get_array_length(&array)?;
    let mut values = vec![0_i32; length as usize];
    if length > 0 {
        env.get_int_array_region(&array, 0, &mut values)?;
    }
    Ok(values)
}

fn panic_message(payload: Box<dyn std::any::Any + Send>) -> String {
    if let Some(message) = payload.downcast_ref::<&str>() {
        (*message).to_string()
    } else if let Some(message) = payload.downcast_ref::<String>() {
        message.clone()
    } else {
        "unknown Rust panic".to_string()
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_open(
    mut env: JNIEnv<'_>,
    _object: JObject<'_>,
    file_descriptor: jint,
    endpoint_address: jint,
    max_packet_size: jint,
    interval: jint,
    sample_rate_hz: jint,
    bytes_per_frame: jint,
    interface_number: jint,
    alternate_setting: jint,
    feedback_endpoint_address: jint,
    feedback_max_packet_size: jint,
    audio_class_version: jint,
    control_interface_number: jint,
    clock_source_entity_ids: jintArray,
    clock_source_frequency_controls: jintArray,
    clock_selector_entity_id: jint,
    clock_selector_control: jint,
    sample_frequency_control: jint,
    allow_uac2_pcm_rate_mismatch: jboolean,
) -> jlong {
    clear_error();
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let source_ids = read_int_array(&mut env, clock_source_entity_ids)
            .map_err(|error| format!("Could not read UAC2 clock source IDs: {error}"))?;
        let source_controls = read_int_array(&mut env, clock_source_frequency_controls)
            .map_err(|error| format!("Could not read UAC2 clock controls: {error}"))?;
        open_state(OpenConfig {
            file_descriptor,
            endpoint_address,
            max_packet_size,
            interval,
            sample_rate_hz,
            bytes_per_frame,
            interface_number,
            alternate_setting,
            feedback_endpoint_address,
            feedback_max_packet_size,
            audio_class_version,
            control_interface_number,
            clock_source_entity_ids: source_ids,
            clock_source_frequency_controls: source_controls,
            clock_selector_entity_id,
            clock_selector_control,
            sample_frequency_control,
            allow_uac2_pcm_rate_mismatch: allow_uac2_pcm_rate_mismatch == JNI_TRUE,
        })
        .map(|state| state as isize as jlong)
    }));
    match result {
        Ok(Ok(handle)) => handle,
        Ok(Err(error)) => {
            set_error(error);
            0
        }
        Err(payload) => {
            set_error(format!(
                "Rust panic during USB open: {}",
                panic_message(payload)
            ));
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_write(
    env: JNIEnv<'_>,
    _object: JObject<'_>,
    handle: jlong,
    pcm_data: jbyteArray,
) -> jint {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if pcm_data.is_null() {
            return Err(String::new());
        }
        let array = unsafe { JByteArray::from_raw(pcm_data) };
        let data = env
            .convert_byte_array(&array)
            .map_err(|error| format!("Could not read PCM byte array: {error}"))?;
        let state = unsafe { state_from_handle(handle) }.ok_or_else(String::new)?;
        write_state(state, &data)
    }));
    match result {
        Ok(Ok(written)) => written,
        Ok(Err(error)) => {
            if !error.is_empty() {
                set_error(error);
            }
            -1
        }
        Err(payload) => {
            set_error(format!(
                "Rust panic during USB write: {}",
                panic_message(payload)
            ));
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_reset(
    _env: JNIEnv<'_>,
    _object: JObject<'_>,
    handle: jlong,
) {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = unsafe { state_from_handle(handle) } {
            let mut sync = lock_unpoisoned(&state.sync);
            sync.pending.clear();
            sync.frame_remainder = 0;
        }
    }));
    if let Err(payload) = result {
        set_error(format!(
            "Rust panic during USB reset: {}",
            panic_message(payload)
        ));
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_cancel(
    _env: JNIEnv<'_>,
    _object: JObject<'_>,
    handle: jlong,
) {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = unsafe { state_from_handle(handle) } {
            let mut sync = lock_unpoisoned(&state.sync);
            sync.lifecycle.cancel();
            state.cancel_transfers_locked();
            state.completion.notify_all();
        }
    }));
    if let Err(payload) = result {
        set_error(format!(
            "Rust panic during USB cancel: {}",
            panic_message(payload)
        ));
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_close(
    _env: JNIEnv<'_>,
    _object: JObject<'_>,
    handle: jlong,
) {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let pointer = handle as isize as *mut IsoState;
        if !pointer.is_null() {
            unsafe { drop(Box::from_raw(pointer)) };
        }
    }));
    if let Err(payload) = result {
        set_error(format!(
            "Rust panic during USB close: {}",
            panic_message(payload)
        ));
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_metrics(
    env: JNIEnv<'_>,
    _object: JObject<'_>,
    handle: jlong,
) -> jlongArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let mut values = [0_i64; 4];
        if let Some(state) = unsafe { state_from_handle(handle) } {
            values[0] = state.submitted_packets.load(Ordering::SeqCst);
            values[1] = state.completed_packets.load(Ordering::SeqCst);
            values[2] = state.failed_packets.load(Ordering::SeqCst);
            values[3] = state.feedback_rate_bits.load(Ordering::SeqCst) as i64;
        }
        let array = env.new_long_array(4)?;
        env.set_long_array_region(&array, 0, &values)?;
        Ok::<jlongArray, jni::errors::Error>(array.into_raw())
    }));
    match result {
        Ok(Ok(array)) => array,
        Ok(Err(error)) => {
            set_error(format!("Could not create USB metrics array: {error}"));
            ptr::null_mut()
        }
        Err(payload) => {
            set_error(format!(
                "Rust panic during USB metrics: {}",
                panic_message(payload)
            ));
            ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_yukine_playback_usb_RustUsbIsoBridge_lastError(
    env: JNIEnv<'_>,
    _object: JObject<'_>,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        env.new_string(last_error()).map(|value| value.into_raw())
    }));
    match result {
        Ok(Ok(value)) => value,
        _ => ptr::null_mut(),
    }
}
