package app.yukine.together

import app.yukine.junto.mobile.Callback as MobileCallback
import app.yukine.junto.mobile.Mobile
import app.yukine.junto.mobile.Session

internal interface TogetherNativeBridge {
    fun testConnection(configJson: String): String
    fun preview(configJson: String, roomCode: String): String
    fun create(configJson: String, queueJson: String, callback: Callback): NativeSession
    fun join(configJson: String, roomCode: String, localMatchesJson: String, callback: Callback): NativeSession

    interface Callback {
        fun onEvent(eventJson: String)
        fun onCommand(commandJson: String)
    }

    interface NativeSession {
        fun roomCode(): String
        fun notifyPlayback(eventJson: String)
        fun receivedFilePath(fileId: String): String
        fun receivedFileRoot(fileId: String): String
        fun leave()
    }
}

internal class GomobileTogetherNativeBridge : TogetherNativeBridge {
    override fun testConnection(configJson: String): String = Mobile.testConnection(configJson)

    override fun preview(configJson: String, roomCode: String): String =
        Mobile.preview(configJson, roomCode)

    override fun create(
        configJson: String,
        queueJson: String,
        callback: TogetherNativeBridge.Callback
    ): TogetherNativeBridge.NativeSession = GomobileSession(
        Mobile.create(configJson, queueJson, callback.asMobileCallback())
    )

    override fun join(
        configJson: String,
        roomCode: String,
        localMatchesJson: String,
        callback: TogetherNativeBridge.Callback
    ): TogetherNativeBridge.NativeSession = GomobileSession(
        Mobile.join(configJson, roomCode, localMatchesJson, callback.asMobileCallback())
    )

    private fun TogetherNativeBridge.Callback.asMobileCallback(): MobileCallback =
        object : MobileCallback {
            override fun onEvent(eventJson: String) = this@asMobileCallback.onEvent(eventJson)
            override fun onCommand(commandJson: String) = this@asMobileCallback.onCommand(commandJson)
        }

    private class GomobileSession(private val raw: Session) : TogetherNativeBridge.NativeSession {
        override fun roomCode(): String = raw.roomCode()

        override fun notifyPlayback(eventJson: String) = raw.notifyPlayback(eventJson)

        override fun receivedFilePath(fileId: String): String = raw.receivedFilePath(fileId)

        override fun receivedFileRoot(fileId: String): String = raw.receivedFileRoot(fileId)

        override fun leave() = raw.leave()
    }
}
