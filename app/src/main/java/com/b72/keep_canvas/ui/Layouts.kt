package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block

/** Page layouts for plain text and photo notes (the smart extraction is for notes built around one fact). */
class PageLayout(val id: String, val name: String, val blurb: String)

val LAYOUTS = listOf(
    PageLayout("", "Plain", "Title, photo, words"),
    PageLayout("cover", "Cover", "The title rides across an offset photo"),
    PageLayout("beside", "Beside", "The photo sits tilted next to the first paragraph"),
    PageLayout("script", "Script", "A cursive heading over serif text with first-line indents"),
    PageLayout("ledger", "Ledger", "Serif text with line numbers in the margin, every fifth"),
)

/** The "Cover" layout: a tall photo pushed to the right, tilted a touch, and the title set big across its lower left. */
@Composable
fun CoverBlock(title: String, photo: Block, pal: NotePalette, seed: String, tint: Boolean, onTitle: () -> Unit, onView: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Box(
            Modifier.fillMaxWidth(.84f).align(Alignment.TopEnd).aspectRatio(.8f).rotate(1.6f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 44.dp, bottomEnd = 12.dp, bottomStart = 120.dp)),
        ) {
            PhotoImage(photo.path!!, 1200, tint, pal.primary, Modifier.fillMaxSize().clickable(onClickLabel = "View photo", onClick = onView))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to Color.Black.copy(alpha = .72f))))
        }
        Box(Modifier.fillMaxWidth(.92f).align(Alignment.BottomStart).offset(y = 6.dp).padding(start = 4.dp, bottom = 22.dp).clickable(onClickLabel = "Edit title", onClick = onTitle)) {
            FitTitle(title, Color.White, seed, maxLines = 3)
        }
    }
}

/** The "Script" heading: a large cursive title. */
@Composable
fun ScriptTitle(title: String, pal: NotePalette, modifier: Modifier = Modifier) {
    Text(title, style = TextStyle(fontFamily = FontFamily.Cursive, fontSize = 58.sp, lineHeight = 56.sp, color = pal.fg), modifier = modifier.padding(bottom = 6.dp))
}

/** The "Beside" layout's photo: small, tilted, taped to the margin of the first paragraph. */
@Composable
fun BesidePhoto(photo: Block, pal: NotePalette, tint: Boolean, onView: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(end = 14.dp, top = 6.dp).rotate(-3f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp))) {
        PhotoImage(photo.path!!, 700, tint, pal.primary, Modifier.fillMaxWidth().aspectRatio((1 / photo.aspect).toFloat().coerceIn(.7f, 1.3f)).clickable(onClickLabel = "View photo", onClick = onView))
    }
}
