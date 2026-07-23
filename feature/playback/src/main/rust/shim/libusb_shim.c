#include <android/log.h>
#include <libusb.h>
#include <stdint.h>
#include <sys/time.h>

typedef void (*echo_usb_transfer_callback)(struct libusb_transfer *);

int echo_usb_set_no_device_discovery(void) {
    return libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
}

int echo_usb_init(libusb_context **context) {
    return libusb_init(context);
}

void echo_usb_exit(libusb_context *context) {
    libusb_exit(context);
}

int echo_usb_wrap_sys_device(
    libusb_context *context,
    intptr_t fd,
    libusb_device_handle **handle
) {
    return libusb_wrap_sys_device(context, fd, handle);
}

void echo_usb_close(libusb_device_handle *handle) {
    libusb_close(handle);
}

int echo_usb_set_auto_detach(libusb_device_handle *handle, int enabled) {
    return libusb_set_auto_detach_kernel_driver(handle, enabled);
}

int echo_usb_get_device_speed(libusb_device_handle *handle) {
    return libusb_get_device_speed(libusb_get_device(handle));
}

int echo_usb_claim_interface(libusb_device_handle *handle, int interface_number) {
    return libusb_claim_interface(handle, interface_number);
}

int echo_usb_release_interface(libusb_device_handle *handle, int interface_number) {
    return libusb_release_interface(handle, interface_number);
}

int echo_usb_set_alt_setting(
    libusb_device_handle *handle,
    int interface_number,
    int alternate_setting
) {
    return libusb_set_interface_alt_setting(handle, interface_number, alternate_setting);
}

int echo_usb_control_transfer(
    libusb_device_handle *handle,
    uint8_t request_type,
    uint8_t request,
    uint16_t value,
    uint16_t index,
    unsigned char *data,
    uint16_t length,
    unsigned int timeout
) {
    return libusb_control_transfer(
        handle,
        request_type,
        request,
        value,
        index,
        data,
        length,
        timeout
    );
}

struct libusb_transfer *echo_usb_alloc_transfer(int packets) {
    return libusb_alloc_transfer(packets);
}

void echo_usb_free_transfer(struct libusb_transfer *transfer) {
    libusb_free_transfer(transfer);
}

void echo_usb_fill_iso_transfer(
    struct libusb_transfer *transfer,
    libusb_device_handle *handle,
    unsigned char endpoint,
    unsigned char *buffer,
    int length,
    int packets,
    echo_usb_transfer_callback callback,
    void *user_data,
    unsigned int timeout
) {
    libusb_fill_iso_transfer(
        transfer,
        handle,
        endpoint,
        buffer,
        length,
        packets,
        callback,
        user_data,
        timeout
    );
}

void *echo_usb_transfer_user_data(struct libusb_transfer *transfer) {
    return transfer->user_data;
}

int echo_usb_transfer_status(struct libusb_transfer *transfer) {
    return transfer->status;
}

int echo_usb_transfer_actual_length(struct libusb_transfer *transfer) {
    return transfer->actual_length;
}

int echo_usb_transfer_packet_count(struct libusb_transfer *transfer) {
    return transfer->num_iso_packets;
}

void echo_usb_set_packet_length(
    struct libusb_transfer *transfer,
    int packet,
    unsigned int length
) {
    transfer->iso_packet_desc[packet].length = length;
}

unsigned int echo_usb_packet_length(struct libusb_transfer *transfer, int packet) {
    return transfer->iso_packet_desc[packet].length;
}

unsigned int echo_usb_packet_actual_length(struct libusb_transfer *transfer, int packet) {
    return transfer->iso_packet_desc[packet].actual_length;
}

int echo_usb_packet_status(struct libusb_transfer *transfer, int packet) {
    return transfer->iso_packet_desc[packet].status;
}

int echo_usb_submit_transfer(struct libusb_transfer *transfer) {
    return libusb_submit_transfer(transfer);
}

int echo_usb_cancel_transfer(struct libusb_transfer *transfer) {
    return libusb_cancel_transfer(transfer);
}

int echo_usb_handle_events(libusb_context *context, long timeout_microseconds) {
    struct timeval timeout;
    timeout.tv_sec = timeout_microseconds / 1000000L;
    timeout.tv_usec = timeout_microseconds % 1000000L;
    return libusb_handle_events_timeout_completed(context, &timeout, NULL);
}

void echo_usb_interrupt_events(libusb_context *context) {
    libusb_interrupt_event_handler(context);
}

const char *echo_usb_error_name(int code) {
    return libusb_error_name(code);
}

void echo_usb_log(int priority, const char *message) {
    __android_log_write(priority, "EchoUsbIsoRust", message);
}

