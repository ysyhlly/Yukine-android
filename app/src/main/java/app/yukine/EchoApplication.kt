package app.yukine

import android.app.Application
import app.yukine.diagnostics.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        StreamingSessionMaintenanceScheduler.schedule(this)
    }
}
