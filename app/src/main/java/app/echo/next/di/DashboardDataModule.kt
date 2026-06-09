package app.echo.next.di

import app.echo.next.StreamingGatewaySettingsStore
import app.echo.next.dashboard.DashboardGateway
import app.echo.next.dashboard.DashboardRepository
import app.echo.next.dashboard.RemoteDashboardGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DashboardDataModule {

    @Provides
    @Singleton
    fun provideDashboardGateway(
        settingsStore: StreamingGatewaySettingsStore
    ): DashboardGateway {
        return RemoteDashboardGateway(settingsStore)
    }

    @Provides
    @Singleton
    fun provideDashboardRepository(
        gateway: DashboardGateway,
        settingsStore: StreamingGatewaySettingsStore
    ): DashboardRepository {
        return DashboardRepository(gateway, settingsStore)
    }
}
