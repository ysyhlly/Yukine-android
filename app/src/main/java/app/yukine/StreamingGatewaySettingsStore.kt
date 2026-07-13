package app.yukine

import android.content.Context

class StreamingGatewaySettingsStore(context: Context) : StreamingGatewayEndpointStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentEndpoint: String = normalize(preferences.getString(KEY_ENDPOINT, UNCONFIGURED_ENDPOINT))

    override fun endpoint(): String = currentEndpoint

    override fun configured(): Boolean = StreamingGatewayEndpoint.isConfigured(currentEndpoint)

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
        const val UNCONFIGURED_ENDPOINT: String = StreamingGatewayEndpoint.UNCONFIGURED
        const val LOCALHOST_ENDPOINT: String = StreamingGatewayEndpoint.LOCALHOST
        const val EMULATOR_HOST_ENDPOINT: String = StreamingGatewayEndpoint.EMULATOR_HOST

        private const val PREFS_NAME: String = "streaming_gateway"
        private const val KEY_ENDPOINT: String = "endpoint"

        @JvmStatic
        fun normalize(value: String?): String {
            return StreamingGatewayEndpoint.normalize(value)
        }
    }
}
