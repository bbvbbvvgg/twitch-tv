package su.expensive.twitchtv.ui.classic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Design
import su.expensive.twitchtv.ui.core.DesignPreview
import su.expensive.twitchtv.ui.core.Setting

/**
 * Classic settings: one flat list, OK changes the value in place (toggles flip, choices cycle).
 * Same look as 1.x; the design picker opens inline under its row, like the sign-in panel.
 */
@UnstableApi
@Composable
fun SettingsScreen(app: AppState) {
    val m = app.settings
    var loggingIn by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(app.openSettingsOnce) }
    val rows = m.groups.flatMap { it.items }

    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxWidth(0.75f)) {
        items(rows, key = { it.id }) { s ->
            m.version
            when (s) {
                m.account -> {
                    val who = Account.login
                    Row("Аккаунт Twitch", who ?: if (loggingIn) "вход…" else "не выполнен",
                        if (who != null) "Нажмите, чтобы выйти (токен будет отозван)" else s.hint) {
                        if (who != null) m.signOut() else loggingIn = !loggingIn
                    }
                    if (loggingIn && who == null) LoginPanel(onFinished = { loggingIn = false; m.signedIn() })
                }
                m.design -> {
                    Row(s.title, m.valueOf(s), s.hint) { picking = !picking }
                    if (picking) DesignList(app)
                }
                is Setting.Toggle -> Row(s.title, m.valueOf(s), s.hint) { s.flip() }
                is Setting.Choice -> Row(s.title, m.valueOf(s), s.hint) { s.step(1) }
                is Setting.Action -> Row(s.title, m.valueOf(s), s.hint) { s.run() }
                is Setting.Screen -> Row(s.title, m.valueOf(s), s.hint) {}
            }
        }
        item {
            Column(Modifier.padding(top = 20.dp, start = 16.dp)) {
                m.aboutLines.forEachIndexed { i, line ->
                    Text(line, style = if (i == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = Palette.dim, modifier = Modifier.padding(top = if (i == 2) 8.dp else 0.dp))
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun DesignList(app: AppState) {
    Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Design.entries.forEach { d ->
            ListItem(
                selected = app.design == d,
                onClick = { app.applyDesign(d) },
                leadingContent = { DesignPreview(d, Modifier.width(128.dp).height(72.dp)) },
                headlineContent = { Text(d.title) },
                supportingContent = { Text(d.tagline, color = Palette.dim) },
                trailingContent = { if (app.design == d) Text("Выбран", color = Palette.accent) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Row(title: String, value: String, hint: String? = null, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it, color = Palette.dim) } },
        trailingContent = { Text(value, color = Palette.accent) },
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
