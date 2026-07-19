package app.yukine.identity

import java.util.Locale

/**
 * User-facing policy for canonical library deduplication.
 *
 * SAFE is deliberately the fallback for missing, malformed, or future values.
 */
enum class LibraryDedupMode {
    SAFE,
    AGGRESSIVE;

    companion object {
        @JvmStatic
        fun fromStoredValue(value: String?): LibraryDedupMode = runCatching {
            valueOf(value.orEmpty().trim().uppercase(Locale.ROOT))
        }.getOrDefault(SAFE)
    }
}
