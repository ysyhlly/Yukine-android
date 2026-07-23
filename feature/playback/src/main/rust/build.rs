use std::env;
use std::path::PathBuf;

fn main() {
    println!("cargo:rerun-if-env-changed=ECHO_LIBUSB_LINK_DIR");
    println!("cargo:rerun-if-env-changed=ECHO_LIBUSB_ROOT");

    if env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("android") {
        return;
    }

    let manifest_dir =
        PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR is required"));
    let libusb_root = env::var_os("ECHO_LIBUSB_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| manifest_dir.join("../cpp/third_party/libusb"));
    let link_dir = env::var_os("ECHO_LIBUSB_LINK_DIR")
        .map(PathBuf::from)
        .expect("ECHO_LIBUSB_LINK_DIR must point to the ABI-specific libusb output");

    println!("cargo:rerun-if-changed=shim/libusb_shim.c");
    println!(
        "cargo:rerun-if-changed={}",
        libusb_root.join("libusb/libusb.h").display()
    );
    cc::Build::new()
        .file("shim/libusb_shim.c")
        .include(libusb_root.join("libusb"))
        .warnings(true)
        .extra_warnings(true)
        .flag_if_supported("-Werror")
        .compile("echo_usb_libusb_shim");

    // The shim is static and references both shared libraries, so emit these after cc::Build to
    // preserve linker order when --as-needed is enabled by the Android toolchain.
    println!("cargo:rustc-link-search=native={}", link_dir.display());
    println!("cargo:rustc-link-lib=dylib=usb-1.0");
    println!("cargo:rustc-link-lib=dylib=log");
}
