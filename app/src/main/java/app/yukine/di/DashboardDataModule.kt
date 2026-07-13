package app.yukine.di

import app.yukine.StreamingGatewaySettingsStore
import app.yukine.HomeDashboardRepository
import app.yukine.dashboard.DashboardGateway
import app.yukine.dashboard.DashboardRepository
import app.yukine.dashboard.RemoteDashboardGateway
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
    ): HomeDashboardRepository {
        return DashboardRepository(gateway, settingsStore)
    }
}
