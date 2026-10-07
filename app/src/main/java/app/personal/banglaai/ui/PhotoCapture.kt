package app.personal.banglaai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

/**
 * Takes a picture with the phone's own camera app (so this app never needs the CAMERA permission). The photo goes to a private cache file; [onPhoto] OWNS it and must delete it when finished (leftovers are wiped at the next app start). Returns the "open camera" action.
 */
@Composable
fun rememberPhotoCapture(onPhoto: (File) -> Unit): () -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending; pending = null
        if (ok && f != null && f.exists()) onPhoto(f) else f?.delete()   // the receiver deletes the file when it is done with it
    }
    return {
        val f = File(File(ctx.cacheDir, "photos").apply { mkdirs() }, "ছবি-${System.currentTimeMillis()}.jpg".replace("ছবি", "photo"))
        pending = f
        launcher.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.photos", f))
    }
}
