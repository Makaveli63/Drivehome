package app.drivehome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lit le nom et le numéro d'un contact choisi dans le sélecteur Android. */
fun readPickedContact(ctx: Context, uri: Uri): Contact? = try {
    ctx.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        null,
    )?.use { c ->
        if (c.moveToFirst()) Contact(c.getString(0) ?: "", c.getString(1) ?: "") else null
    }?.takeIf { it.number.isNotBlank() }
} catch (e: Exception) {
    null
}

@Composable
fun rememberContactPicker(onPicked: (Contact) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            val c = readPickedContact(ctx, uri)
            if (c != null) onPicked(c) else Apps.toast(ctx, "Contact sans numéro")
        }
    }
    return {
        try {
            launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        } catch (e: Exception) {
            Apps.toast(ctx, "Aucun carnet de contacts sur cet appareil")
        }
    }
}

@Composable
fun PhoneScreen(state: AppState, onRequestPermissions: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    var number by remember { mutableStateOf("") }
    val pick = rememberContactPicker { c -> pf.setContacts((pf.contacts + c).distinctBy { it.number }) }
    val phonePkg = Apps.firstInstalled(state.apps, Apps.PHONE_PKGS)

    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- Clavier
        CardShell(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    number.ifEmpty { "Composer un numéro" },
                    color = if (number.isEmpty()) p.sub else p.text,
                    fontSize = if (number.isEmpty()) 20.sp else 32.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
                Column(
                    Modifier.weight(1f).widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    keys.chunked(3).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(p.chip)
                                        .clickable { if (number.length < 20) number += k },
                                    contentAlignment = Alignment.Center,
                                ) { Text(k, color = p.text, fontSize = 26.sp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(p.chip)
                            .clickable { number = "" },
                        contentAlignment = Alignment.Center,
                    ) { Text("Effacer", color = p.text, fontSize = 16.sp) }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(OK_GREEN)
                            .clickable {
                                if (number.isNotEmpty()) Apps.call(ctx, number) else Apps.openDialer(ctx)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Call, contentDescription = "Appeler", tint = androidx.compose.ui.graphics.Color.White) }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(p.chip)
                            .clickable { number = number.dropLast(1) },
                        contentAlignment = Alignment.Center,
                    ) { Text("⌫", color = p.text, fontSize = 20.sp) }
                }
            }
        }

        // ---- Favoris
        CardShell(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                CardHeader("Favoris", trailing = {
                    Text(
                        "+ Ajouter",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { pick() }.padding(8.dp),
                    )
                })
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pf.contacts.isEmpty()) {
                        Text(
                            "Aucun favori. Touche « + Ajouter » pour choisir dans tes contacts, " +
                                "ou ajoute-les à la main dans Réglages.",
                            color = p.sub,
                            fontSize = 14.sp,
                        )
                    }
                    pf.contacts.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ContactChip(c, Modifier.weight(1f)) { Apps.call(ctx, c.number) }
                            Text(
                                "✕",
                                color = p.sub,
                                fontSize = 16.sp,
                                modifier = Modifier.clip(CircleShape).clickable {
                                    pf.setContacts(pf.contacts.filter { it != c })
                                }.padding(10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Appli Téléphone", {
                        if (phonePkg != null) state.launch(phonePkg) else Apps.openDialer(ctx)
                    })
                    if (!Apps.hasCallPermission(ctx)) {
                        SmallAction("Appel direct", { onRequestPermissions(MainActivity.PHONE_PERMISSIONS) }, highlight = true)
                    }
                }
            }
        }
    }
}

@Composable
fun MessagesScreen(state: AppState) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val msgs = NotifStore.messages

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Messages", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (msgs.isNotEmpty()) {
                SmallAction("🔊 Tout lire", { msgs.take(10).reversed().forEach { state.speakMessage(it) } })
                SmallAction("Stop", { state.voice.stop() })
                SmallAction("Tout effacer", { NotifStore.clearAll() })
            }
        }
        if (!state.media.info.access) {
            Text("Autorise l'accès aux notifications pour recevoir tes messages ici.", color = p.sub, fontSize = 16.sp)
            SmallAction("Autoriser", { Apps.openNotificationAccess(ctx) }, highlight = true)
        } else if (msgs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun message 🎉", color = p.sub, fontSize = 20.sp)
            }
        } else {
            Text("Touche un message pour l'écouter · appui long pour l'ouvrir", color = p.sub, fontSize = 13.sp)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                msgs.forEach { m -> MessageRow(state, m) }
            }
        }
    }
}
