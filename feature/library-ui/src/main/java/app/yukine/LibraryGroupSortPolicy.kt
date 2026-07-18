package app.yukine

import app.yukine.ui.LibraryGroupSort
import java.text.Collator
import java.util.Locale

internal object LibraryGroupSortPolicy {
    fun <T> sort(
        items: List<T>,
        sort: LibraryGroupSort,
        languageMode: String,
        stableId: (T) -> String,
        title: (T) -> String,
        trackCount: (T) -> Int
    ): List<T> {
        val locale = if (languageMode == AppLanguage.MODE_ENGLISH) {
            Locale.ENGLISH
        } else {
            Locale.SIMPLIFIED_CHINESE
        }
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.SECONDARY
        }
        return items.sortedWith { left, right ->
            val titleOrder = collator.compare(title(left), title(right))
            val countOrder = trackCount(left).compareTo(trackCount(right))
            val primaryOrder = when (sort) {
                LibraryGroupSort.TitleAscending -> titleOrder
                LibraryGroupSort.TitleDescending -> -titleOrder
                LibraryGroupSort.TrackCountDescending -> -countOrder
                LibraryGroupSort.TrackCountAscending -> countOrder
            }
            if (primaryOrder != 0) {
                primaryOrder
            } else {
                val secondaryTitleOrder = if (
                    sort == LibraryGroupSort.TrackCountDescending ||
                    sort == LibraryGroupSort.TrackCountAscending
                ) {
                    titleOrder
                } else {
                    0
                }
                if (secondaryTitleOrder != 0) {
                    secondaryTitleOrder
                } else {
                    stableId(left).compareTo(stableId(right))
                }
            }
        }
    }
}
