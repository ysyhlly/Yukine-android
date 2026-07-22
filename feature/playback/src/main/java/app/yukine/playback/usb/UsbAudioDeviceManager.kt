package app.yukine.playback.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import app.yukine.diagnostics.DiagnosticLog

/**
 * Manages USB Audio Class device discovery, permission requests, and connection lifecycle.
 *
 * Responsibilities:
 * - Enumerate connected USB audio devices
 * - Request and cache USB permissions
 * - Monitor device attach/detach events
 * - Manage [UsbDeviceConnection] lifecycle
 */
internal class UsbAudioDeviceManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbAudioDeviceManager"
        private const val ACTION_USB_PERMISSION = "app.yukine.playback.USB_PERMISSION"
    }

    /** Information about a connected USB audio device. */
    data class UsbAudioDeviceInfo(
        val device: UsbDevice,
        val deviceName: String,
        val streamConfig: UsbAudioStreamConfig?
    )

    data class UsbAudioEndpointSelection(
        val endpoint: android.hardware.usb.UsbEndpoint,
        val interfaceNumber: Int,
        val alternateSetting: Int,
        val synchronizationType: Int = 0,
        val audioClassVersion: Int = 1,
        val controlInterfaceNumber: Int = 0,
        val clockSourceEntityId: Int = 0,
        val clockSourceEntityIds: IntArray = intArrayOf(clockSourceEntityId).filter { it > 0 }.toIntArray(),
        val clockSourceFrequencyControls: IntArray = IntArray(clockSourceEntityIds.size) { -1 },
        val clockSelectorEntityId: Int = 0,
        val clockSelectorControl: Int = 0,
        val sampleFrequencyControl: Int = -1,
        val feedbackEndpointAddress: Int = 0,
        val feedbackMaxPacketSize: Int = 0,
        val channelCount: Int = 0,
        val subslotSizeBytes: Int = 0,
        val bitResolution: Int = 0
    )

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    @Volatile
    var activeDevice: UsbAudioDeviceInfo? = null
        private set

    private var connection: UsbDeviceConnection? = null
    private var registered = false

    /** Callback invoked when a USB audio device is attached or detached. */
    var onDeviceChanged: (() -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && isAudioDevice(device)) {
                        DiagnosticLog.d(TAG, "USB audio device attached: ${device.productName}")
                        refreshActiveDevice()
                        onDeviceChanged?.invoke()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && isAudioDevice(device)) {
                        DiagnosticLog.d(TAG, "USB audio device detached: ${device.productName}")
                        if (activeDevice?.device?.deviceId == device.deviceId) {
                            closeConnection()
                            activeDevice = null
                        }
                        onDeviceChanged?.invoke()
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (granted && device != null) {
                        DiagnosticLog.d(TAG, "USB permission granted for: ${device.productName}")
                        refreshActiveDevice()
                        onDeviceChanged?.invoke()
                    } else {
                        DiagnosticLog.w(TAG, "USB permission denied")
                    }
                }
            }
        }
    }

    /**
     * Registers USB broadcast receivers and performs initial device scan.
     */
    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        registered = true
        refreshActiveDevice()
    }

    /**
     * Unregisters receivers and closes any open USB connection.
     */
    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {
        }
        registered = false
        closeConnection()
        activeDevice = null
    }

    /** Opens the permission-backed Android connection. Native owns claim/alt-setting lifecycle. */
    fun openConnection(): UsbDeviceConnection? {
        val info = activeDevice ?: return null
        val manager = usbManager ?: return null

        // Check permission
        if (!manager.hasPermission(info.device)) {
            requestPermission(info.device)
            return null
        }

        closeConnection()

        val conn = manager.openDevice(info.device) ?: run {
            DiagnosticLog.e(TAG, "Failed to open USB device: ${info.deviceName}")
            return null
        }

        connection = conn
        return conn
    }

    /**
     * Opens a USB connection and returns both the connection and the audio endpoint.
     *
     * @return A pair of (UsbDeviceConnection, UsbEndpoint), or null if failed.
     */
    fun openConnectionWithEndpoint(): Pair<UsbDeviceConnection, android.hardware.usb.UsbEndpoint>? {
        val conn = openConnection() ?: return null
        val endpoint = findAudioEndpoint() ?: run {
            closeConnection()
            return null
        }
        return Pair(conn, endpoint)
    }

    /**
     * Finds the audio streaming OUT endpoint from the active device.
     * Scans ALL audio class interfaces for any OUT endpoint (bulk or isochronous),
     * not just the address from streamConfig (which may be null/fallback).
     *
     * @return The USB endpoint for audio output, or null if not found.
     */
    fun findAudioEndpoint(): android.hardware.usb.UsbEndpoint? {
        return findAudioEndpointSelection()?.endpoint
    }

    /** Finds the audio OUT endpoint together with the interface alternate setting that owns it. */
    fun findAudioEndpointSelection(
        sampleRateHz: Int = 0,
        channelCount: Int = 0,
        bitDepth: Int = 0
    ): UsbAudioEndpointSelection? {
        val info = activeDevice ?: return null
        val topology = connection?.let(UsbRawDescriptorParser::readAudioTopology)
        val conn = connection

        // Prefer the raw descriptor path even when Android happens to expose alternate settings.
        // It carries the AS terminal link, UAC2 clock selector path and endpoint controls that the
        // public UsbInterface projection drops.
        if (conn != null) {
            val rawEndpoints = UsbRawDescriptorParser.readAudioStreamingEndpoints(conn)
            val rawEndpoint = rawEndpoints?.let {
                UsbRawDescriptorParser.selectAudioStreamingEndpoint(
                    it,
                    sampleRateHz,
                    channelCount,
                    bitDepth
                )
            }
            if (rawEndpoint != null) {
                // Android usually exposes each alternate UsbInterface even though it cannot
                // select the alternate for isochronous streaming. Reuse that public endpoint
                // object first; hidden-API reflection is blocked on current Android releases.
                val endpoint = findProjectedEndpoint(info.device, rawEndpoint)
                    ?: UsbRawDescriptorParser.createUsbEndpoint(rawEndpoint)
                if (endpoint != null) {
                    return UsbAudioEndpointSelection(
                        endpoint,
                        rawEndpoint.interfaceNumber,
                        rawEndpoint.alternateSetting,
                        synchronizationType = rawEndpoint.synchronizationType,
                        audioClassVersion = rawEndpoint.audioClassVersion,
                        controlInterfaceNumber = rawEndpoint.controlInterfaceNumber,
                        clockSourceEntityId = rawEndpoint.clockSourceEntityId,
                        clockSourceEntityIds = rawEndpoint.clockSourceEntityIds,
                        clockSourceFrequencyControls = rawEndpoint.clockSourceFrequencyControls,
                        clockSelectorEntityId = rawEndpoint.clockSelectorEntityId,
                        clockSelectorControl = rawEndpoint.clockSelectorControl,
                        sampleFrequencyControl = rawEndpoint.sampleFrequencyControl,
                        feedbackEndpointAddress = rawEndpoint.feedbackEndpointAddress,
                        feedbackMaxPacketSize = rawEndpoint.feedbackMaxPacketSize,
                        channelCount = rawEndpoint.channelCount,
                        subslotSizeBytes = rawEndpoint.subslotSizeBytes,
                        bitResolution = rawEndpoint.bitResolution
                    )
                }
                DiagnosticLog.w(TAG, "Selected raw endpoint has no Android projection: " +
                    "iface=${rawEndpoint.interfaceNumber}, alt=${rawEndpoint.alternateSetting}, " +
                    "addr=0x${rawEndpoint.address.toString(16)}")
                return null
            }
            if (rawEndpoints != null && rawEndpoints.any {
                    (it.address and 0x80) == 0 && it.alternateSetting > 0
                }
            ) {
                DiagnosticLog.w(TAG, "USB Audio descriptors contain no endpoint compatible with " +
                    "${sampleRateHz}Hz/${channelCount}ch/${bitDepth}bit")
                return null
            }
        }

        val fallbackClockPath = topology?.clockPath(0) ?: UsbRawDescriptorParser.ClockPath()

        // Compatibility fallback for devices whose configuration descriptor cannot be read.
        for (i in 0 until info.device.interfaceCount) {
            val iface = info.device.getInterface(i)
            if (iface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            if (iface.interfaceSubclass != 0x02) continue
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    (ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                     ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK)
                ) {
                    DiagnosticLog.d(TAG, "Found audio OUT endpoint: 0x${Integer.toHexString(ep.address)}, " +
                        "type=${if (ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) "ISOC" else "BULK"}, " +
                        "maxPacket=${ep.maxPacketSize}")
                    return UsbAudioEndpointSelection(
                        ep,
                        iface.id,
                        iface.alternateSetting,
                        synchronizationType = (ep.attributes shr 2) and 0x3,
                        audioClassVersion = topology?.audioClassVersion
                            ?: if (iface.interfaceProtocol == 0x20) 2 else 1,
                        controlInterfaceNumber = topology?.controlInterfaceNumber ?: 0,
                        clockSourceEntityId = fallbackClockPath.sourceEntityIds.firstOrNull() ?: 0,
                        clockSourceEntityIds = fallbackClockPath.sourceEntityIds,
                        clockSourceFrequencyControls = fallbackClockPath.sourceFrequencyControls,
                        clockSelectorEntityId = fallbackClockPath.selectorEntityId,
                        clockSelectorControl = fallbackClockPath.selectorControl,
                        feedbackEndpointAddress = (0 until iface.endpointCount)
                            .map(iface::getEndpoint)
                            .firstOrNull {
                                it.direction == UsbConstants.USB_DIR_IN &&
                                    it.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                            }?.address ?: 0,
                        feedbackMaxPacketSize = (0 until iface.endpointCount)
                            .map(iface::getEndpoint)
                            .firstOrNull {
                                it.direction == UsbConstants.USB_DIR_IN &&
                                    it.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                            }?.maxPacketSize?.and(0x7ff) ?: 0
                    )
                }
            }
        }

        DiagnosticLog.w(TAG, "No audio OUT endpoint found on device: ${info.deviceName}")
        return null
    }

    private fun findProjectedEndpoint(
        device: UsbDevice,
        raw: UsbRawDescriptorParser.RawEndpointInfo
    ): android.hardware.usb.UsbEndpoint? {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val iface = device.getInterface(interfaceIndex)
            if (iface.id != raw.interfaceNumber ||
                iface.alternateSetting != raw.alternateSetting
            ) continue
            for (endpointIndex in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(endpointIndex)
                if (endpoint.address == raw.address) return endpoint
            }
        }
        return null
    }

    /**
     * Closes the current USB connection.
     */
    fun closeConnection() {
        connection?.let {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        connection = null
    }

    /**
     * Requests USB permission for the given device.
     */
    fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) return

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        manager.requestPermission(device, permissionIntent)
    }

    private fun refreshActiveDevice() {
        val manager = usbManager ?: return
        val audioDevice = findFirstAudioDevice()
        if (audioDevice != null) {
            val config = UsbAudioDescriptorParser.findAudioStreamConfig(audioDevice)
            activeDevice = UsbAudioDeviceInfo(
                device = audioDevice,
                deviceName = audioDevice.productName ?: "USB DAC",
                streamConfig = config
            )
            // Pre-request permission if not granted
            if (!manager.hasPermission(audioDevice)) {
                requestPermission(audioDevice)
            }
        } else {
            activeDevice = null
        }
    }

    private fun findFirstAudioDevice(): UsbDevice? {
        val manager = usbManager ?: return null
        return try {
            manager.deviceList.values.firstOrNull { isAudioDevice(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun isAudioDevice(device: UsbDevice): Boolean {
        return (0 until device.interfaceCount).any { i ->
            device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO
        }
    }

    private fun findAudioStreamingInterface(
        device: UsbDevice,
        interfaceNumber: Int
    ): android.hardware.usb.UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.id == interfaceNumber &&
                iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO
            ) {
                return iface
            }
        }
        return null
    }
}
