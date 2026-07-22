# 音频输出可靠化基线（2026-07-22）

本轮开始时工作区已有未提交音频改动，实施采用增量合并，未回退这些文件：

- `feature/playback/build.gradle`
- `feature/playback/src/main/cpp/CMakeLists.txt`
- `feature/playback/src/main/cpp/usb_iso_jni.cpp`
- `feature/playback/src/main/java/app/yukine/playback/usb/UsbPcmTransport.kt`
- `UsbAudioDescriptorParser.kt`、`UsbAudioDeviceManager.kt`、`UsbAudioStreamConfig.kt`
- `UsbExclusiveAudioSink.kt`、`UsbPcmWriter.kt`
- `app/src/main/java/app/yukine/playback/PlaybackServiceRuntime.java`

## 可信状态约束

1. USB 请求不等于 USB 已激活；native library、权限、端点、时钟和 transfer ring 均成功后才能发布 `ACTIVE`。
2. `UsbExclusiveAudioSink` 不得内部创建 `DefaultAudioSink` 隐藏失败。
3. 音频焦点、Bit-Perfect 和 USB 独占互不隐式修改；USB 开关不得静音系统音乐流。
4. Native DSD 仅允许 `verified=true` 的精确 VID/PID profile；XMOS reference 默认保持门控。
5. 未完整缓存的远程 DSD、DST、无 DoP 能力或无 profile 必须返回 typed fallback，不转换为 PCM。

## 构建与证据

- libusb 固定为 1.0.29，源码、LGPL、commit 与替换步骤位于 `feature/playback/src/main/cpp/third_party/libusb/`。
- APK ABI 仅构建 `arm64-v8a` 与 `x86_64`。
- host 单测覆盖 DSF/DFF header/seek、DoP marker、DSD 位序、UAC2 clock topology、high-bandwidth 位、writer 短写与输出决策。
- 真机 USB/DSD 结论必须填写 `PLAYBACK_SERVICE_STABILITY_MATRIX.md`，host 构建不能替代零售 DAC 验收。
