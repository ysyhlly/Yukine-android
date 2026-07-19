package app.yukine

import android.content.Context
import app.yukine.data.enrichment.MetadataGatewayRequestQuota
import java.time.Instant
import java.time.ZoneId

internal class PersistentMetadataGatewayRequestQuota(
    context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val maximum: Int = DAILY_REQUEST_LIMIT
) : MetadataGatewayRequestQuota {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun tryAcquire(now: Long): Boolean = synchronized(LOCK) {
        val day = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().toString()
        val storedDay = preferences.getString(KEY_DAY, "").orEmpty()
        val used = if (storedDay == day) preferences.getInt(KEY_COUNT, 0) else 0
        if (used >= maximum) {
            false
        } else {
            preferences.edit()
                .putString(KEY_DAY, day)
                .putInt(KEY_COUNT, used + 1)
                .commit()
        }
    }

    companion object {
        const val DAILY_REQUEST_LIMIT = 5_000
        internal const val PREFS_NAME = "metadata_gateway_request_quota"
        private const val KEY_DAY = "local_day"
        private const val KEY_COUNT = "request_count"
        private val LOCK = Any()
    }
}
