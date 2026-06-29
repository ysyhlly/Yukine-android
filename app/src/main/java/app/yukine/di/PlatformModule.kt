package app.yukine.di

import android.os.Handler
import android.os.Looper
import app.yukine.MainBackgroundImagePickerListener
import app.yukine.MainBackgroundImagePickerListenerFactory
import app.yukine.MainDocumentPickerListener
import app.yukine.MainDocumentPickerListenerFactory
import app.yukine.MainPermissionListener
import app.yukine.MainPermissionListenerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal object PlatformModule {
    @Provides
    @ActivityScoped
    fun provideMainHandler(): Handler = Handler(Looper.getMainLooper())

    @Provides
    @ActivityScoped
    fun provideMainDocumentPickerListenerFactory(): MainDocumentPickerListenerFactory =
        MainDocumentPickerListenerFactory {
                audioUrisImporter,
                audioFolderImporter,
                downloadFolderChooser,
                streamM3uImporter,
                playlistExporter,
                playlistM3uImporter,
                luoxueSourceUrisImporter ->
            MainDocumentPickerListener(
                audioUrisImporter,
                audioFolderImporter,
                downloadFolderChooser,
                streamM3uImporter,
                playlistExporter,
                playlistM3uImporter,
                luoxueSourceUrisImporter
            )
        }

    @Provides
    @ActivityScoped
    fun provideMainBackgroundImagePickerListenerFactory(): MainBackgroundImagePickerListenerFactory =
        MainBackgroundImagePickerListenerFactory { pageImageApplier, copyFailedStatusSink ->
            MainBackgroundImagePickerListener(pageImageApplier, copyFailedStatusSink)
        }

    @Provides
    @ActivityScoped
    fun provideMainPermissionListenerFactory(): MainPermissionListenerFactory =
        MainPermissionListenerFactory {
                audioPermissionStatusSource,
                libraryLoader,
                onboardingVisibilitySource,
                navHostMounter ->
            MainPermissionListener(
                audioPermissionStatusSource,
                libraryLoader,
                onboardingVisibilitySource,
                navHostMounter
            )
        }
}
