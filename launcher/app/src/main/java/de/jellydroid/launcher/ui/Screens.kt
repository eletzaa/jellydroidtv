package de.jellydroid.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import de.jellydroid.launcher.data.AppEntry
import java.util.Calendar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.view.KeyEvent as AKey
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged

@Composable
private fun RowTitle(text: String, source: String? = null) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(text, style = LauncherType.Row, color = Ink.copy(alpha = .92f))
        if (source != null) {
            Text(
                "  $source",
                style = LauncherType.Body.copy(fontSize = 10.sp()),
                color = Ink.copy(alpha = .55f),
            )
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)


/** Seitenränder des Inhalts. Reihen laufen bis an den Bildschirmrand und blenden rechts weich aus. */
val ScreenStart = 48.dp
val ScreenEnd = 40.dp

/** Blendet den rechten Rand weich aus statt hart abzuschneiden. */
fun Modifier.fadeRightEdge(width: androidx.compose.ui.unit.Dp = 96.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
            blendMode = BlendMode.DstIn,
        )
    }

/** Rechter und (optional) oberer Rand in einer einzigen Ebene weich ausblenden. */
fun Modifier.fadeEdges(right: androidx.compose.ui.unit.Dp = 96.dp, top: androidx.compose.ui.unit.Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = right.toPx()
        drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width), blendMode = BlendMode.DstIn)
        if (top > 0.dp) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = top.toPx()), blendMode = BlendMode.DstIn)
    }

/** Blendet den oberen Rand einer Scrollfläche weich aus (Reste der vorigen Reihe). */
fun Modifier.fadeTopEdge(height: androidx.compose.ui.unit.Dp = 56.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = height.toPx()),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun StartScreen(
    favorites: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    movingPkg: String?,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    onAdd: () -> Unit,
    refocus: Pair<String, Int>?,
    hero: @Composable () -> Unit = {},
    continueWatching: @Composable () -> Unit = {},
    onSection: (Int) -> Unit = {}, // -1 = nichts unten fokussiert, 0 = Weiterschauen, 1 = Favoriten
) {
  // Banner oben steht fest (wie auf Entdecken), darunter scrollen Weiterschauen und Favoriten.
  Column(Modifier.fillMaxSize()) {
    hero()
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .onFocusChanged { if (!it.hasFocus) onSection(-1) }
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.onFocusChanged { if (it.hasFocus) onSection(0) }) { continueWatching() }
        Spacer(Modifier.height(6.dp))
      Column(Modifier.onFocusChanged { if (it.hasFocus) onSection(1) }) {
        androidx.compose.foundation.layout.Row(Modifier.padding(start = ScreenStart), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RowTitle("Favoriten", "Apps")
            if (movingPkg != null) {
                Text("   Mit ‹ und › verschieben, OK oder Zurück zum Beenden.", style = LauncherType.Body, color = InkDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().fadeRightEdge(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            // unten genug Platz, damit der Schatten der fokussierten App nicht abgeschnitten wird
            contentPadding = PaddingValues(start = ScreenStart, end = ScreenEnd + 96.dp, top = 12.dp, bottom = 30.dp),
        ) {
            items(favorites, key = { it.packageName }) { app ->
                val moving = app.packageName == movingPkg
                Box(
                    Modifier.animateItem().onPreviewKeyEvent { e ->
                        if (!moving) return@onPreviewKeyEvent false
                        val code = e.nativeKeyEvent.keyCode
                        if (e.type == KeyEventType.KeyDown) {
                            when (code) {
                                AKey.KEYCODE_DPAD_LEFT -> onMove(-1)
                                AKey.KEYCODE_DPAD_RIGHT -> onMove(1)
                            }
                            true
                        } else {
                            if (code == AKey.KEYCODE_DPAD_CENTER || code == AKey.KEYCODE_ENTER || code == AKey.KEYCODE_BACK) onMoveDone()
                            true
                        }
                    },
                ) {
                    AppTile(app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }, moving = moving, refocus = refocus?.takeIf { it.first == app.packageName })
                }
            }
            if (movingPkg == null) item(key = "_add") { AddTile(onClick = onAdd) }
        }
      }
        Spacer(Modifier.height(30.dp))
    }
  }
}

@Composable
fun AppsScreen(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onLongPress: (AppEntry) -> Unit, onScrolledDown: (Boolean) -> Unit, refocus: Pair<String, Int>? = null) {
    val state = rememberLazyGridState()
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex >= 7 || (state.firstVisibleItemIndex > 0 && state.firstVisibleItemScrollOffset > 0) } }
    LaunchedEffect(scrolled) { onScrolledDown(scrolled) }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.padding(start = ScreenStart)) { RowTitle("Alle Apps", "${apps.size} Apps") }
        Spacer(Modifier.height(6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            state = state,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp, start = ScreenStart, end = ScreenEnd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(app, onClick = { onLaunch(app) }, width = AppTileWidth, onLongClick = { onLongPress(app) }, refocus = refocus?.takeIf { it.first == app.packageName })
            }
        }
    }
}
