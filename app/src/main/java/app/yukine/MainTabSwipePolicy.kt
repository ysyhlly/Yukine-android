package app.yukine

internal object MainTabSwipePolicy {
    @JvmStatic
    fun adjacentTab(tabRoutes: List<String>, selectedTab: String, next: Boolean): String? {
        if (tabRoutes.isEmpty()) {
            return null
        }
        val currentIndex = tabRoutes.indexOf(selectedTab)
        if (currentIndex < 0) {
            return null
        }
        val nextIndex = if (next) currentIndex + 1 else currentIndex - 1
        return tabRoutes.getOrNull(nextIndex)
    }
}
