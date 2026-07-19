package app.yukine.streaming

import android.content.Context

data class KugouExperimentalSyncStatus(
    val userEnabled: Boolean,
    val contractVerified: Boolean,
    val writeSuspended: Boolean,
    val lastSuccessfulSyncAtMs: Long?,
    val lastResult: String?,
    val degradationReason: String?
) {
    fun writeEnabled(accountConnected: Boolean): Boolean =
        userEnabled && contractVerified && !writeSuspended && accountConnected
}

/**
 * Release gate for private Kugou account writes.
 *
 * Public search/playback/playlist import never depend on this store. Enabling the setting only
 * authorizes use of a contract that has already passed the disposable-account verification suite.
 */
class KugouExperimentalSyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun status(accountConnected: Boolean = false): KugouExperimentalSyncStatus {
        val userEnabled = prefs.getBoolean(KEY_USER_ENABLED, false)
        val suspended = prefs.getBoolean(KEY_WRITE_SUSPENDED, false)
        val storedReason = prefs.getString(KEY_DEGRADATION_REASON, null)
        val reason = when {
            !userEnabled -> "用户尚未启用酷狗实验同步"
            !KugouPrivateApiContract.writeContractVerified ->
                "酷狗私有写接口尚未通过一次性测试账号契约验证，当前仅只读"
            !accountConnected -> "酷狗账号未验证，当前仅只读"
            suspended -> storedReason ?: "连续接口异常，写能力已自动降级为只读"
            else -> storedReason
        }
        return KugouExperimentalSyncStatus(
            userEnabled = userEnabled,
            contractVerified = KugouPrivateApiContract.writeContractVerified,
            writeSuspended = suspended,
            lastSuccessfulSyncAtMs = prefs.getLong(KEY_LAST_SUCCESS_MS, 0L).takeIf { it > 0L },
            lastResult = prefs.getString(KEY_LAST_RESULT, null),
            degradationReason = reason
        )
    }

    fun setUserEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_USER_ENABLED, enabled)
            .putString(
                KEY_LAST_RESULT,
                if (enabled) "已启用；等待账号与接口契约门禁" else "已关闭"
            )
            .apply()
    }

    fun recordSyncSuccess(result: String, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_LAST_SUCCESS_MS, nowMs)
            .putString(KEY_LAST_RESULT, result.take(MAX_STATUS_LENGTH))
            .putInt(KEY_CONTRACT_FAILURES, 0)
            .remove(KEY_DEGRADATION_REASON)
            .apply()
    }

    fun recordReadOnlyResult(result: String) {
        prefs.edit()
            .putString(KEY_LAST_RESULT, result.take(MAX_STATUS_LENGTH))
            .apply()
    }

    fun recordContractFailure(reason: String) {
        val failures = prefs.getInt(KEY_CONTRACT_FAILURES, 0) + 1
        prefs.edit()
            .putInt(KEY_CONTRACT_FAILURES, failures)
            .putString(KEY_LAST_RESULT, "同步失败")
            .putString(KEY_DEGRADATION_REASON, reason.take(MAX_STATUS_LENGTH))
            .putBoolean(KEY_WRITE_SUSPENDED, failures >= FAILURE_SUSPEND_THRESHOLD)
            .apply()
    }

    /** A successful explicit health check or an app upgrade may release an automatic suspension. */
    fun recordHealthyContractCheck() {
        if (!KugouPrivateApiContract.writeContractVerified) return
        prefs.edit()
            .putInt(KEY_CONTRACT_FAILURES, 0)
            .putBoolean(KEY_WRITE_SUSPENDED, false)
            .remove(KEY_DEGRADATION_REASON)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "kugou_experimental_sync"
        const val KEY_USER_ENABLED = "user_enabled"
        const val KEY_WRITE_SUSPENDED = "write_suspended"
        const val KEY_CONTRACT_FAILURES = "contract_failures"
        const val KEY_LAST_SUCCESS_MS = "last_success_ms"
        const val KEY_LAST_RESULT = "last_result"
        const val KEY_DEGRADATION_REASON = "degradation_reason"
        const val FAILURE_SUSPEND_THRESHOLD = 3
        const val MAX_STATUS_LENGTH = 500
    }
}

object KugouPrivateApiContract {
    /**
     * Flip only after every destructive account operation passes the disposable-account suite
     * against captured, redacted contracts. This repository intentionally ships read-only today.
     */
    const val writeContractVerified: Boolean = false
}
