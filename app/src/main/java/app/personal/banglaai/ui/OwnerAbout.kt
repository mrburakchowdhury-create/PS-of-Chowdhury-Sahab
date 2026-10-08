package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.banglaai.security.OwnerIdentity

/** About (shown only after the app is unlocked): fixed owner profile, activation status, and the offline activation steps. Nothing here edits the owner profile. */
@Composable
fun OwnerAboutSection() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var token by remember { mutableStateOf("") }
    val res = remember(tick) { OwnerIdentity.status(ctx) }
    val request = remember { runCatching { OwnerIdentity.deviceRequest(OwnerIdentity.devicePublicKeyB64()) }.getOrNull() }
    val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" } }.getOrDefault("?") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("মালিক: ${OwnerIdentity.OWNER_NAME_BN}\nOwner ID: ${OwnerIdentity.OWNER_ID}\nPackage: ${OwnerIdentity.PACKAGE}\nVersion: $ver", style = MaterialTheme.typography.bodySmall)
            Text("Activation: ${res.status} — ${res.detail}", style = MaterialTheme.typography.bodySmall)
            Text("এই তথ্য সেটিংস, চ্যাট, ভয়েস-কমান্ড, ইমপোর্ট করা ফাইল বা ব্যাকআপ-রিস্টোর দিয়ে বদলানো যায় না। সক্রিয় না থাকলেও আপনার ফাইল বা ডেটা মোছা/বন্ধ হয় না।", style = MaterialTheme.typography.bodySmall)
            if (res.status != OwnerIdentity.Status.NotConfigured) {
                request?.let { Text("এই ফোনের অনুরোধ (issuer-কে দিন; এতে গোপন কিছু নেই):", style = MaterialTheme.typography.labelSmall); SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
                OutlinedTextField(token, { token = it }, label = { Text("Activation token") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = token.isNotBlank(), onClick = { OwnerIdentity.saveToken(ctx, token); token = ""; tick++ }) { Text("Activate") }
            }
        }
    }
}
