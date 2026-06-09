package app.echo.next

import android.content.Context

class StreamingGatewaySettingsStore(context: Context) : StreamingGatewayEndpointStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentEndpoint: String = normalize(preferences.getString(KEY_ENDPOINT, UNCONFIGURED_ENDPOINT))

    override fun endpoint(): String = currentEndpoint

    override fun configured(): Boolean = isHttpEndpoint(currentEndpoint)

    override fun setEndpoint(nextEndpoint: String?) {
        currentEndpoint = normalize(nextEndpoint)
        preferences.edit().putString(KEY_ENDPOINT, currentEndpoint).apply()
    }

    fun useLocalhost() {
        setEndpoint(LOCALHOST_ENDPOINT)
    }

    fun useEmulatorHost() {
        setEndpoint(EMULATOR_HOST_ENDPOINT)
    }

    fun clear() {
        setEndpoint(UNCONFIGURED_ENDPOINT)
    }

    companion object {
        const val UNCONFIGURED_ENDPOINT: String = "gateway://unconfigured"
        const val LOCALHOST_ENDPOINT: String = "http://127.0.0.1:43990"
        const val EMULATOR_HOST_ENDPOINT: String = "http://10.0.2.2:43990"

        private const val PREFS_NAME: String = "streaming_gateway"
        private const val KEY_ENDPOINT: String = "endpoint"

        @JvmStatic
        fun normalize(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            if (isHttpEndpoint(trimmed)) {
                return trimTrailingSlash(trimmed)
            }
            return UNCONFIGURED_ENDPOINT
        }

        private fun isHttpEndpoint(value: String?): Boolean {
            return value != null && (value.startsWith("http://") || value.startsWith("https://"))
        }

        private fun trimTrailingSlash(value: String): String {
            var result = value
            while (result.endsWith("/") && result.length > "https://x".length) {
                result = result.substring(0, result.length - 1)
            }
            return result
        }
    }
}

interface StreamingGatewayEndpointStore {
    fun endpoint(): String

    fun configured(): Boolean

    fun setEndpoint(nextEndpoint: String?)
}
