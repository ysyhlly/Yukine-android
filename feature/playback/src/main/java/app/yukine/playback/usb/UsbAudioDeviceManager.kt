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
import android.util.Log

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
                        Log.d(TAG, "USB audio device attached: ${device.productName}")
                        refreshActiveDevice()
                        onDeviceChanged?.invoke()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && isAudioDevice(device)) {
                        Log.d(TAG, "USB audio device detached: ${device.productName}")
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
                        Log.d(TAG, "USB permission granted for: ${device.productName}")
                        refreshActiveDevice()
                        onDeviceChanged?.invoke()
                    } else {
                        Log.w(TAG, "USB permission denied")
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
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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

    /**
     * Opens a USB connection to the active device and claims ALL audio interfaces
     * with force=true (HiBy-style exclusive). This forcibly disconnects the device
     * from Android's USB audio class driver (AudioFlinger), preventing the system
     * from routing any audio to this USB device.
     *
     * @return An open [UsbDeviceConnection], or null if connection failed.
     */
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
            Log.e(TAG, "Failed to open USB device: ${info.deviceName}")
            return null
        }

        // HiBy-style: claim ALL USB Audio Class interfaces with force=true.
        // This disconnects the device from Android's USB audio driver entirely,
        // so AudioFlinger can no longer route system audio to this DAC.
        var claimedCount = 0
        for (i in 0 until info.device.interfaceCount) {
            val usbInterface = info.device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                if (conn.claimInterface(usbInterface, true)) {
                    claimedCount++
                    conn.setInterface(usbInterface)
                    // For Audio Streaming interfaces with no endpoints on alt 0,
                    // try to activate alternate setting 1 which has the audio endpoints.
                    if (usbInterface.endpointCount == 0 &&
                        usbInterface.interfaceSubclass == 0x02) {
                        val altResult: Int = conn.controlTransfer(
                            0x01, // bmRequestType: Host-to-Device, Standard, Interface
                            0x0B, // bRequest: SET_INTERFACE
                            1,    // wValue: alternate setting 1
                            usbInterface.id, // wIndex: interface number
                            null, 0, 500
                        )
                        Log.d(TAG, "Alt setting 1 on interface #${usbInterface.id}: " +
                            if (altResult >= 0) "OK" else "failed ($altResult)")
                    }
                } else {
                    Log.w(TAG, "Failed to force-claim audio interface #${usbInterface.id}")
                }
            }
        }

        if (claimedCount == 0) {
            Log.e(TAG, "No audio interfaces could be claimed on: ${info.deviceName}")
            conn.close()
            return null
        }

        Log.d(TAG, "USB exclusive: force-claimed $claimedCount audio interface(s) on ${info.deviceName}")
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
        val info = activeDevice ?: return null

        // Phase 1: Scan all audio interfaces for any OUT endpoint.
        for (i in 0 until info.device.interfaceCount) {
            val iface = info.device.getInterface(i)
            if (iface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    (ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                     ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK)
                ) {
                    Log.d(TAG, "Found audio OUT endpoint: 0x${Integer.toHexString(ep.address)}, " +
                        "type=${if (ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) "ISOC" else "BULK"}, " +
                        "maxPacket=${ep.maxPacketSize}")
                    return ep
                }
            }
        }

        // Phase 2: No endpoints on alt 0 — try selecting alternate setting 1.
        // Most UAC devices expose audio endpoints only on alt setting 1.
        val conn = connection
        if (conn != null) {
            for (i in 0 until info.device.interfaceCount) {
                val iface = info.device.getInterface(i)
                if (iface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
                if (iface.interfaceSubclass != 0x02) continue // Audio Streaming subclass
                // SET_INTERFACE control transfer to select alternate setting 1
                val result: Int = conn.controlTransfer(
                    0x01, // bmRequestType: Host-to-Device, Standard, Interface
                    0x0B, // bRequest: SET_INTERFACE
                    1,    // wValue: alternate setting 1
                    iface.id, // wIndex: interface number
                    null, 0, 500
                )
                if (result >= 0) {
                    Log.d(TAG, "Selected alternate setting 1 on audio interface #${iface.id}")
                    // Re-scan endpoints after alt setting change
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            return ep
                        }
                    }
                }
            }

            // Phase 3: Parse raw USB configuration descriptor to find endpoints on alt 1.
            // Android's Java API doesn't expose alt setting 1 endpoints, so we read
            // the raw descriptor and create UsbEndpoint via reflection.
            val rawEndpoint = UsbRawDescriptorParser.findAudioStreamingEndpoint(conn)
            if (rawEndpoint != null) {
                // Select the correct alternate setting for this endpoint
                val setAltResult: Int = conn.controlTransfer(
                    0x01, 0x0B,
                    rawEndpoint.alternateSetting,
                    rawEndpoint.interfaceNumber,
                    null, 0, 500
                )
                Log.d(TAG, "SET_INTERFACE alt=${rawEndpoint.alternateSetting} " +
                    "iface=#${rawEndpoint.interfaceNumber}: " +
                    if (setAltResult >= 0) "OK" else "failed")

                val endpoint = UsbRawDescriptorParser.createUsbEndpoint(rawEndpoint)
                if (endpoint != null) return endpoint
            }
        }

        Log.w(TAG, "No audio OUT endpoint found on device: ${info.deviceName}")
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
