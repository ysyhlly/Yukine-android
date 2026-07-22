package app.yukine

import android.app.Application
import app.yukine.backup.BackupManager
import app.yukine.diagnostics.CrashLogger
import app.yukine.diagnostics.DiagnosticLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.install(this)
        CrashLogger.install(this)
        if (!BackupManager.applyPendingRestore(this)) {
            DiagnosticLog.e("EchoApplication", "Pending backup restore could not be applied safely")
        }
        StreamingSessionMaintenanceScheduler.schedule(this)
        FavoriteSyncBackgroundScheduler.restore(this)
        KugouPlaylistSyncScheduler.schedule(this)
        IdentityEnhancementScheduler.schedule(this)
        IdentityBackfillScheduler.scheduleAutomatic(this)
    }
}
