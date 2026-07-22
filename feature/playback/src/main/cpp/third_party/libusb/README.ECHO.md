# Bundled libusb

- Upstream: `https://github.com/libusb/libusb`
- Version: `v1.0.29`
- Commit: `15a7ebb4d426c5ce196684347d2b7cafad862626`
- License: LGPL-2.1-or-later; see `COPYING`.

ECHO builds this source as the replaceable `libusb-1.0.so` shared library for
`arm64-v8a` and `x86_64`. The JNI transport links dynamically to it. To replace
the library, replace this directory with a compatible upstream source release,
retain the license and attribution files, then run:

```text
gradlew :feature:playback:externalNativeBuildDebug
```

Java owns Android USB enumeration, permission, and the `UsbDeviceConnection`.
Native code receives its file descriptor and wraps it with
`libusb_wrap_sys_device`; no rooted device-node discovery is used.
