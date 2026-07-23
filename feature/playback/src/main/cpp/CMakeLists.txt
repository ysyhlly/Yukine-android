cmake_minimum_required(VERSION 3.22.1)
project(echo_usb_iso LANGUAGES C)

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

set(RUST_CRATE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../rust)
find_program(CARGO_EXECUTABLE cargo
    HINTS
        "$ENV{CARGO_HOME}/bin"
        "$ENV{USERPROFILE}/.cargo/bin"
        "$ENV{HOME}/.cargo/bin"
    REQUIRED
)
execute_process(
    COMMAND ${CARGO_EXECUTABLE} ndk --version
    RESULT_VARIABLE CARGO_NDK_RESULT
    OUTPUT_VARIABLE CARGO_NDK_VERSION
    ERROR_VARIABLE CARGO_NDK_ERROR
    OUTPUT_STRIP_TRAILING_WHITESPACE
)
if(NOT CARGO_NDK_RESULT EQUAL 0)
    message(FATAL_ERROR
        "cargo-ndk 4.1.2 is required. Install it with: "
        "cargo install cargo-ndk --version 4.1.2 --locked. ${CARGO_NDK_ERROR}"
    )
endif()
if(NOT CARGO_NDK_VERSION MATCHES "cargo-ndk 4\\.1\\.2")
    message(FATAL_ERROR
        "cargo-ndk 4.1.2 is required, found: ${CARGO_NDK_VERSION}"
    )
endif()

set(RUST_PROFILE_ARGS)
if(CMAKE_BUILD_TYPE MATCHES "^(Release|RelWithDebInfo|MinSizeRel)$")
    set(RUST_VARIANT "release")
    list(APPEND RUST_PROFILE_ARGS --release)
else()
    set(RUST_VARIANT "debug")
endif()
set(RUST_JNI_ROOT
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/rust/${RUST_VARIANT}/jniLibs
)
set(RUST_LIBRARY
    ${RUST_JNI_ROOT}/${ANDROID_ABI}/libecho_usb_iso_rust.so
)
file(GLOB_RECURSE RUST_INPUTS CONFIGURE_DEPENDS
    ${RUST_CRATE_DIR}/Cargo.toml
    ${RUST_CRATE_DIR}/Cargo.lock
    ${RUST_CRATE_DIR}/rust-toolchain.toml
    ${RUST_CRATE_DIR}/build.rs
    ${RUST_CRATE_DIR}/src/*.rs
    ${RUST_CRATE_DIR}/shim/*.c
)
add_custom_command(
    OUTPUT ${RUST_LIBRARY}
    COMMAND ${CMAKE_COMMAND} -E env
        "ANDROID_NDK_HOME=${ANDROID_NDK}"
        "ANDROID_NDK_ROOT=${ANDROID_NDK}"
        "ECHO_LIBUSB_ROOT=${LIBUSB_ROOT}"
        "ECHO_LIBUSB_LINK_DIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
        "CARGO_TARGET_DIR=${CMAKE_CURRENT_BINARY_DIR}/cargo-target"
        ${CARGO_EXECUTABLE} ndk
            -t ${ANDROID_ABI}
            -P 23
            -o ${RUST_JNI_ROOT}
            build
            --manifest-path ${RUST_CRATE_DIR}/Cargo.toml
            --locked
            ${RUST_PROFILE_ARGS}
    DEPENDS usb-1.0 ${RUST_INPUTS}
    WORKING_DIRECTORY ${RUST_CRATE_DIR}
    COMMENT "Building Rust USB isochronous backend for ${ANDROID_ABI}"
    VERBATIM
)
add_custom_target(echo_usb_iso_rust ALL DEPENDS ${RUST_LIBRARY})
add_dependencies(echo_usb_iso_rust usb-1.0)
