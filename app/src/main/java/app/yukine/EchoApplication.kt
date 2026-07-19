package app.yukine

import android.app.Application
import android.util.Log
import app.yukine.backup.BackupManager
import app.yukine.diagnostics.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BackupManager.applyPendingRestore(this)) {
            Log.e("EchoApplication", "Pending backup restore could not be applied safely")
        }
        CrashLogger.install(this)
        StreamingSessionMaintenanceScheduler.schedule(this)
        FavoriteSyncBackgroundScheduler.restore(this)
        KugouPlaylistSyncScheduler.schedule(this)
        IdentityEnhancementScheduler.schedule(this)
        IdentityBackfillScheduler.scheduleAutomatic(this)
    }
}
