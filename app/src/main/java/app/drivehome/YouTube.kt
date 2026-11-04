package app.drivehome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val YT_RED = Color(0xFFFF0033)

/** Tuile YouTube : lecture en cours + raccourcis (recherche, abonnements, YouTube Music…). */
@Composable
fun YouTubeCard(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val yt = Apps.firstInstalled(state.apps, Apps.YOUTUBE_PKGS)
    val ytMusic = Apps.firstInstalled(state.apps, listOf("com.google.android.apps.youtube.music"))
    val m = state.media.info
    val playingYt = m.hasSession && m.pkg != null && (m.pkg in Apps.YOUTUBE_PKGS || m.pkg == ytMusic)
    var search by remember { mutableStateOf(false) }

    CardShell(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                if (yt != null) {
                    AppIcon(yt, 32.dp)
                } else {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(YT_RED),
                        contentAlignment = Alignment.Center,
                    ) { Text("▶", color = Color.White, fontSize = 16.sp) }
                }
                Spacer(Modifier.width(10.dp))
                Text("YouTube", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }

            if (playingYt) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title ?: "Lecture en cours", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!m.artist.isNullOrEmpty()) Text(m.artist, color = p.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RoundButton(onClick = { state.media.previous() }, size = 44.dp) { PrevGlyph(p.text, 18.dp) }
                    Spacer(Modifier.width(6.dp))
                    RoundButton(onClick = { state.media.playPause() }, size = 52.dp, filled = true) {
                        val on = MaterialTheme.colorScheme.onPrimary
                        if (m.playing) PauseGlyph(on, 20.dp) else PlayGlyph(on, 20.dp)
                    }
                    Spacer(Modifier.width(6.dp))
                    RoundButton(onClick = { state.media.next() }, size = 44.dp) { NextGlyph(p.text, 18.dp) }
                }
            } else if (yt == null) {
                Text("YouTube n'est pas installé sur l'autoradio.", color = p.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
            } else {
                Text("Vidéos, musique, podcasts : touche un raccourci.", color = p.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                YtChip("▶ Ouvrir", highlight = true) {
                    if (yt != null) state.launch(yt) else Apps.openYouTube(ctx, null, "https://www.youtube.com")
                }
                YtChip("🔍 Rechercher") { search = true }
                YtChip("📺 Abonnements") { Apps.openYouTube(ctx, yt, "https://www.youtube.com/feed/subscriptions") }
                YtChip("🔥 Tendances") { Apps.openYouTube(ctx, yt, "https://www.youtube.com/feed/trending") }
                YtChip("🕘 Historique") { Apps.openYouTube(ctx, yt, "https://www.youtube.com/feed/history") }
                if (ytMusic != null) YtChip("🎵 YouTube Music") { state.launch(ytMusic) }
            }
        }
    }

    if (search) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { search = false },
            title = { Text("Rechercher sur YouTube") },
            text = {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    placeholder = { Text("Vidéo, chaîne, musique…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (q.isNotBlank()) Apps.searchYouTube(ctx, yt, q.trim())
                    search = false
                }) { Text("Rechercher") }
            },
            dismissButton = { TextButton(onClick = { search = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun YtChip(text: String, highlight: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlight) YT_RED else p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(text, color = if (highlight) Color.White else p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}
