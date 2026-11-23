package com.b72.keep_canvas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Profile
import com.b72.keep_canvas.data.Profiles
import com.b72.keep_canvas.data.Repo

/** The flat tonal colour of a profile's avatar: its [Profile.hue] picks one of six from the theme, so every profile follows the wallpaper colours. */
@Composable
fun profileColors(hue: Int): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (hue.mod(6)) {
        0 -> cs.primaryContainer to cs.onPrimaryContainer
        1 -> cs.tertiaryContainer to cs.onTertiaryContainer
        2 -> cs.secondaryContainer to cs.onSecondaryContainer
        3 -> cs.errorContainer to cs.onErrorContainer
        4 -> cs.primary.copy(alpha = .3f).compositeOver(cs.surface) to cs.onSurface
        else -> cs.tertiary.copy(alpha = .3f).compositeOver(cs.surface) to cs.onSurface
    }
}

@Composable
fun ProfileAvatar(p: Profile, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val (bg, fg) = profileColors(p.hue)
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(p.name.trim().take(1).uppercase().ifEmpty { "?" }, color = fg, fontWeight = FontWeight.SemiBold, fontSize = (size.value * .42f).sp)
    }
}

/** Sits at the top of the navigation rail: the profile in use, and a tap opens the list to switch, add or edit. */
@Composable
fun ProfileSwitcher(repo: Repo, onSwitch: (String) -> Unit, onCreate: (String, Int) -> Unit, onDelete: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    val active = Profiles.all.firstOrNull { it.id == repo.profileId } ?: Profiles.active
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Switch profile") { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(active)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(active.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Profile", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Rounded.UnfoldMore, null, tint = cs.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(bottom = 6.dp)) {
                for (p in Profiles.all) {
                    val on = p.id == repo.profileId
                    Row(
                        Modifier.fillMaxWidth().clickable { open = false; if (!on) onSwitch(p.id) }.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(p, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (on) Icon(Icons.Rounded.Check, "In use", tint = cs.primary)
                        IconButton({ edit = p }) { Icon(Icons.Rounded.Edit, "Edit ${p.name}", tint = cs.onSurfaceVariant) }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { adding = true }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
                    Spacer(Modifier.width(12.dp))
                    Text("New profile", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                }
            }
        }
    }
    if (adding) ProfileDialog(null, onDismiss = { adding = false }, onSave = { name, hue -> adding = false; open = false; onCreate(name, hue) }, onDelete = null)
    edit?.let { p ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        ProfileDialog(p, onDismiss = { edit = null }, onSave = { name, hue -> Profiles.rename(ctx, p.id, name, hue); edit = null }, onDelete = if (p.id == Profiles.MAIN) null else ({ edit = null; onDelete(p.id) }))
    }
}

@Composable
private fun ProfileDialog(p: Profile?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(p?.name.orEmpty()) }
    var hue by remember { mutableStateOf(p?.hue ?: Profiles.all.size % 6) }
    var confirm by remember { mutableStateOf(false) }
    if (confirm && p != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Every note, tag, canvas and artifact in this profile is deleted with it. This cannot be undone.") },
            confirmButton = { TextButton({ confirm = false; onDelete?.invoke() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p == null) "New profile" else "Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (h in 0 until 6) Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Colour ${h + 1}") { hue = h }) {
                        ProfileAvatar(Profile("", name.ifBlank { "?" }, h), 36.dp)
                        if (h == hue) Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Color.White) }
                    }
                }
                if (onDelete != null) TextButton({ confirm = true }, Modifier.height(40.dp)) { Text("Delete this profile", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton({ onSave(name, hue) }, enabled = name.isNotBlank()) { Text(if (p == null) "Create" else "Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
