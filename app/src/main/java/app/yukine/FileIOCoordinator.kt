package app.yukine

import android.net.Uri
import androidx.activity.ComponentActivity
import java.util.ArrayList

internal class FileIOCoordinator(
    val documentPickerController: DocumentPickerController,
    val backgroundImagePickerController: BackgroundImagePickerController,
    val backupRestoreLauncher: BackupRestoreLauncher
)
