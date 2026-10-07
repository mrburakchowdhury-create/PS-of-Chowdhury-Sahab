package app.personal.banglaai.security

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Static checks on the SOURCE manifest/backup rules. The merged-manifest check (libraries can add permissions) runs in CI and in tools/sign_release.sh. */
class ManifestSecurityTest {
    private val main = File("src/main").takeIf { it.exists() } ?: File("app/src/main")
    private val manifest by lazy { File(main, "AndroidManifest.xml").readText() }

    @Test fun noInternetOrBroadPermissionsDeclared() {
        val perms = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertFalse("INTERNET must never be declared", "android.permission.INTERNET" in perms)
        val banned = listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_CONTACTS", "READ_SMS", "SEND_SMS", "ACCESS_NETWORK_STATE", "CAMERA", "BIND_ACCESSIBILITY_SERVICE", "BIND_DEVICE_ADMIN")
        banned.forEach { b -> assertTrue("$b must not be requested", perms.none { it.endsWith(b) }) }
        assertEquals(setOf("android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "android.permission.POST_NOTIFICATIONS"), perms)   // mic (press-to-talk) + read-aloud service only
    }

    @Test fun backupAndTransferAreDisabled() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        listOf("xml/backup_rules.xml", "xml/data_extraction_rules.xml").forEach { f ->
            val x = File(main, "res/$f").readText()
            assertTrue("$f must exclude everything", x.contains("path=\".\"") && x.contains("exclude"))
        }
    }

    @Test fun onlyLauncherActivityIsExportedAndNoServicesReceiversProviders() {
        assertEquals(1, Regex("android:exported=\"true\"").findAll(manifest).count())
        assertFalse("<receiver must not be declared by the app", manifest.contains("<receiver"))
        val providers = Regex("<provider[^>]*>").findAll(manifest).map { it.value }.toList()
        assertEquals(1, providers.size)   // only the camera-photo FileProvider
        assertTrue(providers[0].contains("androidx.core.content.FileProvider") && providers[0].contains("android:exported=\"false\""))
        assertEquals(1, Regex("<cache-path").findAll(File(main, "res/xml/file_paths.xml").readText()).count())   // one shareable folder, nothing else
        val services = Regex("<service[^>]*>").findAll(manifest).map { it.value }.toList()
        assertEquals(1, services.size)   // only the read-aloud service
        assertTrue(services[0].contains("ReadAloudService") && services[0].contains("android:exported=\"false\"") && services[0].contains("mediaPlayback"))
        assertFalse(manifest.contains("android:debuggable"))
    }
}
