package su.expensive.twitchtv.ui.classic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import su.expensive.twitchtv.ui.core.LOGIN_PRIVACY
import su.expensive.twitchtv.ui.core.rememberDeviceLogin

/** Classic inline sign-in panel (Device Code Flow from ui/core). */
@Composable
fun LoginPanel(onFinished: () -> Unit) {
    val st = rememberDeviceLogin(onFinished)
    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)) {
        st.code?.let {
            Text("На телефоне откройте", style = MaterialTheme.typography.bodyLarge)
            Text(st.site, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
            Text("и введите код", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Text(it.userCode, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = Palette.accent)
        }
        Text(st.message, color = Palette.dim, modifier = Modifier.padding(top = 8.dp))
        Text(LOGIN_PRIVACY, style = MaterialTheme.typography.bodySmall, color = Palette.dim, modifier = Modifier.padding(top = 4.dp))
    }
}
