package app.yukine

interface StreamingGatewayEndpointStore {
    fun endpoint(): String
    fun configured(): Boolean
    fun setEndpoint(nextEndpoint: String?)
}

object StreamingGatewayEndpoint {
    const val UNCONFIGURED: String = "gateway://unconfigured"
    const val LOCALHOST: String = "http://127.0.0.1:43990"
    const val EMULATOR_HOST: String = "http://10.0.2.2:43990"

    @JvmStatic
    fun normalize(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (!isConfigured(trimmed)) return UNCONFIGURED
        var result = trimmed
        while (result.endsWith("/") && result.length > "https://x".length) {
            result = result.dropLast(1)
        }
        return result
    }

    @JvmStatic
    fun isConfigured(value: String?): Boolean {
        val normalized = value?.trim().orEmpty()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }
}
