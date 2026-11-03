package app.drivehome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Une tuile placée dans la grille d'une page. */
data class Placed(val type: CardType, val col: Int, val row: Int, val w: Int, val h: Int)

/** Range les tuiles dans l'ordre, page par page, dans une grille cols × rows. */
fun layoutPages(cards: List<CardType>, size: (CardType) -> Pair<Int, Int>, cols: Int, rows: Int): List<List<Placed>> {
    val pages = mutableListOf<MutableList<Placed>>()
    val grids = mutableListOf<Array<BooleanArray>>()

    fun tryPlace(pi: Int, c: CardType, w: Int, h: Int): Boolean {
        val g = grids[pi]
        for (r in 0..rows - h) for (col in 0..cols - w) {
            var free = true
            for (dr in 0 until h) for (dc in 0 until w) if (g[r + dr][col + dc]) free = false
            if (free) {
                for (dr in 0 until h) for (dc in 0 until w) g[r + dr][col + dc] = true
                pages[pi].add(Placed(c, col, r, w, h))
                return true
            }
        }
        return false
    }

    for (c in cards) {
        val (w0, h0) = size(c)
        val w = w0.coerceIn(1, cols)
        val h = h0.coerceIn(1, rows)
        if (pages.isEmpty() || !tryPlace(pages.lastIndex, c, w, h)) {
            pages.add(mutableListOf())
            grids.add(Array(rows) { BooleanArray(cols) })
            tryPlace(pages.lastIndex, c, w, h)
        }
    }
    return pages
}

/** Déplace une tuile active d'un cran parmi les tuiles actives. */
fun AppState.moveCard(c: CardType, delta: Int) {
    val order = prefs.cardOrder.toMutableList()
    val enabled = enabledCards()
    val i = enabled.indexOf(c)
    val j = i + delta
    if (i < 0 || j < 0 || j > enabled.lastIndex) return
    val other = enabled[j]
    val a = order.indexOf(c)
    val b = order.indexOf(other)
    order[a] = other
    order[b] = c
    prefs.setCardOrder(order)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(state: AppState, wide: Boolean, onRequestPermissions: (Array<String>) -> Unit) {
    val p = LocalPalette.current
    val pf = state.prefs
    val cards = state.enabledCards()
    val cols = if (wide) pf.homeCols.coerceIn(2, 6) else 2
    val rows = if (wide) pf.homeRows.coerceIn(1, 4) else 3
    var addDialog by remember { mutableStateOf(false) }

    val pages = layoutPages(cards, { pf.cardSize(it) }, cols, rows).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages[index.coerceIn(0, pages.lastIndex)]
            if (page.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Aucune tuile.\nTouche « ✎ Modifier » puis « + Ajouter une tuile ».",
                        color = p.sub,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                PageGrid(state, page, cols, rows, onRequestPermissions)
            }
        }

        // ---- bas de page : points + bouton d'édition
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.editHome) {
                Pill("+ Ajouter une tuile", false, { addDialog = true })
                Spacer(Modifier.size(8.dp))
                Pill("Terminé", true, { state.editHome = false })
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                if (pages.size > 1) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (i == pager.currentPage) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) p.text else p.sub.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
            if (!state.editHome) {
                Text(
                    "✎ Modifier",
                    color = p.sub,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { state.editHome = true }.padding(8.dp),
                )
            }
        }
    }

    if (addDialog) {
        val off = CardType.entries.filter { it in pf.cardsOff }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Ajouter une tuile") },
            text = {
                if (off.isEmpty()) {
                    Text("Toutes les tuiles sont déjà sur l'accueil.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(off) { c ->
                            Text(
                                c.label,
                                fontSize = 17.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pf.setCardEnabled(c, true)
                                        addDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addDialog = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun PageGrid(
    state: AppState,
    page: List<Placed>,
    cols: Int,
    rows: Int,
    onRequestPermissions: (Array<String>) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val gap = 12.dp
        val cw = (maxWidth - gap * (cols - 1)) / cols
        val ch = (maxHeight - gap * (rows - 1)) / rows
        page.forEach { pl ->
            Box(
                Modifier
                    .offset(x = (cw + gap) * pl.col, y = (ch + gap) * pl.row)
                    .size(width = cw * pl.w + gap * (pl.w - 1), height = ch * pl.h + gap * (pl.h - 1)),
            ) {
                AnyCard(state, pl.type, Modifier.fillMaxSize(), onRequestPermissions)
                if (state.editHome) TileEditor(state, pl, cols, rows)
            }
        }
    }
}

@Composable
private fun TileEditor(state: AppState, pl: Placed, cols: Int, rows: Int) {
    val pf = state.prefs
    val accent = MaterialTheme.colorScheme.primary
    val (w, h) = pf.cardSize(pl.type)

    @Composable
    fun Btn(text: String, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pl.type.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${pl.w} × ${pl.h}", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("◀") { state.moveCard(pl.type, -1) }
                Btn("▶") { state.moveCard(pl.type, 1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("↔ −") { pf.setCardSize(pl.type, (w - 1).coerceAtLeast(1), h) }
                Btn("↔ +") { pf.setCardSize(pl.type, (w + 1).coerceAtMost(cols), h) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("↕ −") { pf.setCardSize(pl.type, w, (h - 1).coerceAtLeast(1)) }
                Btn("↕ +") { pf.setCardSize(pl.type, w, (h + 1).coerceAtMost(rows)) }
            }
            Btn("✕ Retirer") { pf.setCardEnabled(pl.type, false) }
        }
    }
}

@Composable
fun AnyCard(state: AppState, type: CardType, modifier: Modifier, onRequestPermissions: (Array<String>) -> Unit) {
    when (type) {
        CardType.NAV -> NavCard(state, modifier)
        CardType.CAR -> CarCard(state, modifier)
        CardType.MEDIA -> MediaCard(state, modifier)
        CardType.SPEED -> SpeedCard(state, modifier, onRequestPermissions)
        CardType.CLOCK -> ClockCard(state, modifier)
        CardType.WEATHER -> WeatherCard(state, modifier, onRequestPermissions)
        CardType.TRIP -> TripCard(state, modifier)
        CardType.ENGINE -> EngineCard(state, modifier)
        CardType.MESSAGES -> MessagesCard(state, modifier)
        CardType.CONTACTS -> ContactsCard(state, modifier)
        CardType.APPS -> AppsCard(state, modifier)
    }
}
