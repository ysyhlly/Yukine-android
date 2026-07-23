use std::ffi::{c_char, c_int, c_long, c_uchar, c_uint, c_void};

#[repr(C)]
pub(crate) struct LibusbContext {
    _private: [u8; 0],
}

#[repr(C)]
pub(crate) struct LibusbDeviceHandle {
    _private: [u8; 0],
}

#[repr(C)]
pub(crate) struct LibusbTransfer {
    _private: [u8; 0],
}

pub(crate) type TransferCallback = Option<unsafe extern "C" fn(*mut LibusbTransfer)>;

pub(crate) const SUCCESS: c_int = 0;
pub(crate) const ERROR_INTERRUPTED: c_int = -10;
pub(crate) const TRANSFER_COMPLETED: c_int = 0;
pub(crate) const TRANSFER_CANCELLED: c_int = 3;
pub(crate) const SPEED_FULL: c_int = 2;

pub(crate) const ENDPOINT_IN: u8 = 0x80;
pub(crate) const ENDPOINT_OUT: u8 = 0x00;
pub(crate) const REQUEST_TYPE_CLASS: u8 = 0x20;
pub(crate) const RECIPIENT_INTERFACE: u8 = 0x01;
pub(crate) const RECIPIENT_ENDPOINT: u8 = 0x02;

pub(crate) const LOG_INFO: c_int = 4;
pub(crate) const LOG_WARN: c_int = 5;
pub(crate) const LOG_ERROR: c_int = 6;

extern "C" {
    pub(crate) fn echo_usb_set_no_device_discovery() -> c_int;
    pub(crate) fn echo_usb_init(context: *mut *mut LibusbContext) -> c_int;
    pub(crate) fn echo_usb_exit(context: *mut LibusbContext);
    pub(crate) fn echo_usb_wrap_sys_device(
        context: *mut LibusbContext,
        fd: isize,
        handle: *mut *mut LibusbDeviceHandle,
    ) -> c_int;
    pub(crate) fn echo_usb_close(handle: *mut LibusbDeviceHandle);
    pub(crate) fn echo_usb_set_auto_detach(
        handle: *mut LibusbDeviceHandle,
        enabled: c_int,
    ) -> c_int;
    pub(crate) fn echo_usb_get_device_speed(handle: *mut LibusbDeviceHandle) -> c_int;
    pub(crate) fn echo_usb_claim_interface(
        handle: *mut LibusbDeviceHandle,
        interface_number: c_int,
    ) -> c_int;
    pub(crate) fn echo_usb_release_interface(
        handle: *mut LibusbDeviceHandle,
        interface_number: c_int,
    ) -> c_int;
    pub(crate) fn echo_usb_set_alt_setting(
        handle: *mut LibusbDeviceHandle,
        interface_number: c_int,
        alternate_setting: c_int,
    ) -> c_int;
    pub(crate) fn echo_usb_control_transfer(
        handle: *mut LibusbDeviceHandle,
        request_type: u8,
        request: u8,
        value: u16,
        index: u16,
        data: *mut c_uchar,
        length: u16,
        timeout: c_uint,
    ) -> c_int;
    pub(crate) fn echo_usb_alloc_transfer(packets: c_int) -> *mut LibusbTransfer;
    pub(crate) fn echo_usb_free_transfer(transfer: *mut LibusbTransfer);
    pub(crate) fn echo_usb_fill_iso_transfer(
        transfer: *mut LibusbTransfer,
        handle: *mut LibusbDeviceHandle,
        endpoint: c_uchar,
        buffer: *mut c_uchar,
        length: c_int,
        packets: c_int,
        callback: TransferCallback,
        user_data: *mut c_void,
        timeout: c_uint,
    );
    pub(crate) fn echo_usb_transfer_user_data(transfer: *mut LibusbTransfer) -> *mut c_void;
    pub(crate) fn echo_usb_transfer_status(transfer: *mut LibusbTransfer) -> c_int;
    pub(crate) fn echo_usb_transfer_actual_length(transfer: *mut LibusbTransfer) -> c_int;
    pub(crate) fn echo_usb_transfer_packet_count(transfer: *mut LibusbTransfer) -> c_int;
    pub(crate) fn echo_usb_set_packet_length(
        transfer: *mut LibusbTransfer,
        packet: c_int,
        length: c_uint,
    );
    pub(crate) fn echo_usb_packet_length(transfer: *mut LibusbTransfer, packet: c_int) -> c_uint;
    pub(crate) fn echo_usb_packet_actual_length(
        transfer: *mut LibusbTransfer,
        packet: c_int,
    ) -> c_uint;
    pub(crate) fn echo_usb_packet_status(transfer: *mut LibusbTransfer, packet: c_int) -> c_int;
    pub(crate) fn echo_usb_submit_transfer(transfer: *mut LibusbTransfer) -> c_int;
    pub(crate) fn echo_usb_cancel_transfer(transfer: *mut LibusbTransfer) -> c_int;
    pub(crate) fn echo_usb_handle_events(
        context: *mut LibusbContext,
        timeout_microseconds: c_long,
    ) -> c_int;
    pub(crate) fn echo_usb_interrupt_events(context: *mut LibusbContext);
    pub(crate) fn echo_usb_error_name(code: c_int) -> *const c_char;
    pub(crate) fn echo_usb_log(priority: c_int, message: *const c_char);
}
