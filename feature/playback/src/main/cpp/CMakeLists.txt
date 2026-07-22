cmake_minimum_required(VERSION 3.22.1)
project(echo_usb_iso LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBUSB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb)
add_library(usb-1.0 SHARED
    ${LIBUSB_ROOT}/libusb/core.c
    ${LIBUSB_ROOT}/libusb/descriptor.c
    ${LIBUSB_ROOT}/libusb/hotplug.c
    ${LIBUSB_ROOT}/libusb/io.c
    ${LIBUSB_ROOT}/libusb/sync.c
    ${LIBUSB_ROOT}/libusb/strerror.c
    ${LIBUSB_ROOT}/libusb/os/linux_usbfs.c
    ${LIBUSB_ROOT}/libusb/os/events_posix.c
    ${LIBUSB_ROOT}/libusb/os/threads_posix.c
    ${LIBUSB_ROOT}/libusb/os/linux_netlink.c
)
target_include_directories(usb-1.0
    PUBLIC ${LIBUSB_ROOT}/libusb
    PRIVATE ${LIBUSB_ROOT} ${LIBUSB_ROOT}/libusb/os
)
target_compile_options(usb-1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb-1.0 PRIVATE log)

add_library(echo_usb_iso SHARED usb_iso_jni.cpp)
target_compile_options(echo_usb_iso PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_libraries(echo_usb_iso PRIVATE usb-1.0 log)
