package com.b72.keep_canvas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Watermarks: a Noto Emoji (the one-colour font) drawn big and faint behind a page or a tile, chosen from the words in a title. Only some notes get one,
 * picked by their id, so it stays a surprise; and nothing is drawn where the build has no font, since the coloured system emoji would look wrong.
 */
object Glyphs {
    private val table: List<Pair<List<String>, String>> = listOf(
        listOf("flower", "wildflower", "bloom", "garden", "bouquet", "florist", "petal") to "\uD83C\uDF37",
        listOf("plant", "leaf", "leaves", "nature", "hike", "hiking", "trail", "trek", "park", "walk") to "\uD83C\uDF3F",
        listOf("tree", "forest", "woods") to "\uD83C\uDF32",
        listOf("coffee", "cafe", "espresso", "latte", "tea", "chai") to "\u2615",
        listOf("pasta", "noodle", "ramen", "soup") to "\uD83C\uDF5C",
        listOf("cake", "bake", "baking", "dessert", "cookie", "pastry", "birthday") to "\uD83C\uDF70",
        listOf("pizza") to "\uD83C\uDF55",
        listOf("burger") to "\uD83C\uDF54",
        listOf("recipe", "cook", "cooking", "dinner", "lunch", "breakfast", "kitchen", "food", "meal") to "\uD83C\uDF73",
        listOf("fruit", "apple", "grocery", "groceries", "shopping", "shop", "market") to "\uD83D\uDED2",
        listOf("wine", "beer", "drinks", "cocktail", "bar", "pub") to "\uD83C\uDF77",
        listOf("flight", "airport", "plane", "fly", "travel", "trip", "holiday", "vacation", "visa", "passport") to "\u2708",
        listOf("train", "rail", "metro", "railway") to "\uD83D\uDE86",
        listOf("bus", "commute") to "\uD83D\uDE8C",
        listOf("car", "drive", "driving", "road", "roadtrip") to "\uD83D\uDE97",
        listOf("hotel", "stay", "hostel", "booking") to "\uD83C\uDFE8",
        listOf("beach", "sea", "ocean", "island", "surf", "swim", "swimming") to "\uD83C\uDF0A",
        listOf("mountain", "peak", "summit") to "\u26F0",
        listOf("camp", "camping", "tent") to "\u26FA",
        listOf("map", "maps", "route", "places") to "\uD83D\uDDFA",
        listOf("movie", "film", "cinema", "series", "show", "watch") to "\uD83C\uDFAC",
        listOf("music", "song", "songs", "playlist", "album", "lyrics", "concert", "band", "gig") to "\uD83C\uDFB5",
        listOf("book", "books", "read", "reading", "novel", "library", "author") to "\uD83D\uDCD6",
        listOf("gym", "workout", "run", "running", "fitness", "yoga", "exercise", "training") to "\uD83C\uDFC3",
        listOf("bike", "cycle", "cycling") to "\uD83D\uDEB2",
        listOf("football", "soccer", "match", "goal") to "\u26BD",
        listOf("cricket") to "\uD83C\uDFCF",
        listOf("idea", "ideas", "brainstorm", "thought", "thoughts", "concept") to "\uD83D\uDCA1",
        listOf("money", "budget", "expense", "expenses", "salary", "invoice", "bill", "bills", "rent", "tax", "finance", "savings") to "\uD83D\uDCB0",
        listOf("party", "celebrate", "wedding", "anniversary", "celebration") to "\uD83C\uDF89",
        listOf("gift", "gifts", "present") to "\uD83C\uDF81",
        listOf("doctor", "health", "medicine", "hospital", "pharmacy", "dentist", "symptoms") to "\uD83D\uDC8A",
        listOf("sleep", "dream", "dreams", "night", "moon") to "\uD83C\uDF19",
        listOf("work", "meeting", "project", "office", "presentation", "deadline", "client", "report") to "\uD83D\uDCBC",
        listOf("code", "coding", "dev", "bug", "app", "software", "website") to "\uD83D\uDCBB",
        listOf("study", "exam", "school", "lecture", "class", "learn", "learning", "college", "course", "homework") to "\uD83C\uDF93",
        listOf("dog", "puppy", "walkies") to "\uD83D\uDC15",
        listOf("cat", "kitten") to "\uD83D\uDC08",
        listOf("bird", "birds") to "\uD83D\uDC26",
        listOf("home", "house", "apartment", "flat", "move", "moving", "decor") to "\uD83C\uDFE0",
        listOf("photo", "photos", "camera", "photography") to "\uD83D\uDCF7",
        listOf("art", "draw", "drawing", "sketch", "paint", "painting", "design") to "\uD83C\uDFA8",
        listOf("rain", "weather", "umbrella", "monsoon") to "\u2614",
        listOf("sun", "summer", "sunny") to "\u2600",
        listOf("winter", "snow", "cold") to "\u2744",
        listOf("game", "games", "gaming", "play") to "\uD83C\uDFAE",
        listOf("phone", "call", "calls") to "\uD83D\uDCDE",
        listOf("email", "mail", "letter", "letters") to "\u2709",
        listOf("love", "date", "dating", "valentine") to "\uD83D\uDC97",
    )

    /** The glyph for some words (an artifact's name, say), or null when none of them says anything. */
    fun forName(text: String): String? {
        val tokens = text.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 }
        for ((keys, g) in table) if (tokens.any { t -> keys.any { k -> t == k || (k.length >= 4 && t.startsWith(k)) } }) return g
        return null
    }

    /** A note's glyph: about half of the notes that have a fitting one get it, decided by the note's id. */
    fun forNote(id: String, title: String): String? {
        val h = id.hashCode() and 0x7fffffff
        return if (h % 100 < 55) forName(title) else null
    }
}

/** The glyph at [size], in [color] (faint, for a watermark). Draws nothing when the font is not in the build. */
@Composable
fun GlyphMark(glyph: String, size: Dp, color: Color, modifier: Modifier = Modifier, weight: Int = 300) {
    val ctx = LocalContext.current
    val fam = remember { EmojiFont.family(ctx, weight) } ?: return
    Text(glyph, modifier, color = color, fontFamily = fam, fontSize = size.value.sp, lineHeight = size.value.sp)
}

/** A sheet of dotted paper behind whatever is drawn on it. */
fun Modifier.dotGrid(color: Color, step: Dp = 14.dp): Modifier = drawBehind {
    val s = step.toPx()
    val r = 1.1.dp.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) { drawCircle(color, r, Offset(x, y)); x += s }
        y += s
    }
}
