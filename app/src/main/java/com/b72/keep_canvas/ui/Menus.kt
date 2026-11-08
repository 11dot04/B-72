@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** One row of a menu: a title, an optional second line, an optional icon, and an optional trailing icon (a tick for the chosen one). */
class MenuEntry(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val sub: String? = null,
    val trailing: ImageVector? = null,
    val tint: Color = Color.Unspecified,
)

/** A labelled cluster of rows; each group is its own rounded container, with a small gap between groups. */
class MenuGroup(val label: String?, val items: List<MenuEntry>)

/**
 * The expressive menu: [DropdownMenuPopup] holding one [DropdownMenuGroup] per group, so every menu in the app is built the same way.
 * Empty groups are skipped, so callers can pass conditional groups without checking.
 */
@Composable
fun GroupedMenu(expanded: Boolean, onDismiss: () -> Unit, groups: List<MenuGroup>) {
    val shown = groups.filter { it.items.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        shown.forEachIndexed { gi, g ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(gi, shown.size)) {
                if (g.label != null) {
                    MenuLabel(g.label)
                    HorizontalDivider(Modifier.padding(MenuDefaults.HorizontalDividerPadding))
                }
                g.items.forEach { e ->
                    DropdownMenuItem(
                        text = {
                            if (e.sub == null) Text(e.text, color = e.tint)
                            else Column {
                                Text(e.text, color = e.tint)
                                Text(e.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = e.onClick,
                        leadingIcon = e.icon?.let { ic -> { Icon(ic, null, tint = e.tint) } },
                        trailingIcon = e.trailing?.let { ic -> { Icon(ic, null) } },
                    )
                }
            }
            if (gi != shown.lastIndex) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        }
    }
}

/** The small caption above a group of rows. */
@Composable
fun MenuLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp))
}
