package com.b72.keep_canvas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.b72.keep_canvas.data.Note

private val LINKS = Regex("""\[\[([^\]]{1,80})\]\]|https?://[^\s]+""")

/**
 * Text with live links: web addresses open in the browser, and [[Another note's title]] jumps to that note, which is how
 * notes get tied together by hand (the target also shows the link as a backlink under "Related").
 */
@Composable
fun rememberLinked(text: String, notes: List<Note>, accent: Color, onOpen: (String) -> Unit): AnnotatedString {
    val open by androidx.compose.runtime.rememberUpdatedState(onOpen)
    return remember(text, notes, accent) {
        buildAnnotatedString {
            var last = 0
            for (m in LINKS.findAll(text)) {
                append(text.substring(last, m.range.first))
                val wiki = m.groupValues[1]
                if (wiki.isNotEmpty()) {
                    val target = notes.firstOrNull { it.title.equals(wiki.trim(), ignoreCase = true) }
                    if (target != null) {
                        withLink(
                            LinkAnnotation.Clickable(
                                "note:${target.id}",
                                TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)),
                            ) { open(target.id) },
                        ) { append(wiki) }
                    } else append(m.value)
                } else {
                    withLink(LinkAnnotation.Url(m.value, TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)))) { append(m.value) }
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}
