package de.elias.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.elias.launcher.data.AppEntry

/** Breite der App-Kacheln (im Mockup 9,6 em). */
val AppTileWidth = 124.dp

@Composable
fun AppTile(
    app: AppEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = AppTileWidth,
    onLongClick: () -> Unit = {},
    moving: Boolean = false,
    refocus: Any? = null,
) {
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(refocus) { if (refocus != null) runCatching { fr.requestFocus() } }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (moving) 1.16f else if (focused) 1.1f else 1f, appleSpring(), label = "tile")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 16.dp else 0.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2A3042), Color(0xFF4A5470))))
            .border(if (focused || moving) 2.dp else 1.dp, if (moving) LauncherColors.Accent else Ink.copy(alpha = if (focused) .9f else .14f), shape)
            .focusRequester(fr)
            .okKey(onClick = onClick, onLongClick = onLongClick)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val banner = app.banner
        if (banner != null) {
            Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp)) }
                Text(
                    app.label,
                    style = LauncherType.Tile,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        // Glanzkante oben links, wie bei den Glasflächen
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), end = androidx.compose.ui.geometry.Offset(160f, 140f))),
        )
        if (moving) {
            Text(
                "‹   ›",
                style = LauncherType.Headline.copy(fontSize = 28.sp),
                color = Color.White,
                modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 10.dp),
            )
        }
    }
}

/** Letzte Kachel der Favoriten: führt zu allen Apps, um weitere hinzuzufügen. */
@Composable
fun AddTile(onClick: () -> Unit, width: Dp = AppTileWidth) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, appleSpring(), label = "add")
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Ink.copy(alpha = if (focused) .9f else .08f))
            .border(1.dp, Ink.copy(alpha = .22f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = LauncherType.Headline.copy(fontSize = 30.sp), color = if (focused) InkInverse else Ink)
    }
}
