@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Cand
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.Spotify
import kotlinx.coroutines.launch

/** Apps B-72 can read from. Spotify signs in and lists what you play; the rest arrive through the share sheet. */
@Composable
fun ServicesSheet(repo: Repo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { Spotify.refresh(ctx) }
    var client by remember { mutableStateOf(Spotify.clientId(ctx)) }
    var source by remember { mutableStateOf(Spotify.Source.Recent) }
    var busy by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<Cand>>(emptyList()) }
    val added = remember { mutableStateListOf<String>() }
    LaunchedEffect(Spotify.connected, source) {
        if (!Spotify.connected) return@LaunchedEffect
        busy = true; list = Spotify.load(ctx, source); busy = false
    }
    ModalBottomSheet(onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Connected apps", style = flexStyle(androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp), wght = 720, rond = 40f))
            Text(
                "Share an activity from Strava, Garmin, Komoot or AllTrails, or a song, album or playlist from Spotify, to B-72 and it becomes a card with the numbers. Places from Google Maps and videos from YouTube arrive as link cards.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Text("SPOTIFY", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            if (!Spotify.connected) {
                OutlinedTextField(
                    client, { client = it; Spotify.setClientId(ctx, it) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                    label = { Text("Client id") },
                    supportingText = { Text("Make an app at developer.spotify.com and add ${Spotify.REDIRECT} as a redirect URI. Paste its client id here.") },
                )
                Button({ Spotify.begin(ctx) }, Modifier.padding(top = 8.dp), enabled = client.isNotBlank()) { Text("Sign in with Spotify") }
                if (Spotify.message.isNotEmpty()) Text(Spotify.message, color = cs.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            } else {
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in Spotify.Source.entries) FilterChip(source == s, { source = s }, { Text(s.label) })
                }
                if (busy) LoadingIndicator(Modifier.padding(16.dp).size(40.dp).align(Alignment.CenterHorizontally))
                else if (list.isEmpty()) Text("Nothing here yet.", Modifier.padding(vertical = 16.dp), color = cs.onSurfaceVariant)
                LazyColumn(Modifier.height(300.dp)) {
                    items(list, key = { it.title + it.sub }) { c ->
                        val done = c.title + c.sub in added
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text(c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            FilledTonalIconButton({ if (!done) { added.add(c.title + c.sub); scope.launch { repo.addTrack(c) } } }) {
                                Icon(if (done) Icons.Rounded.Check else Icons.Rounded.Add, if (done) "Added" else "Add as a note")
                            }
                        }
                    }
                }
                OutlinedButton({ Spotify.logout(ctx) }, Modifier.padding(top = 8.dp)) { Text("Sign out of Spotify") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

