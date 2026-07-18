package app.yukine.ui

enum class HomeDashboardLayout(val storageValue: String) {
    Classic("classic"),
    Content("content");

    companion object {
        @JvmStatic
        fun normalize(value: String?): HomeDashboardLayout =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: Classic
    }
}
