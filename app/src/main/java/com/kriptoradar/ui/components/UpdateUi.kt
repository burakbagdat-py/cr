package com.kriptoradar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoradar.update.UpdateInfo
import com.kriptoradar.update.UpdateManager
import com.kriptoradar.update.UpdateState
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted

/** Yeni sürüm olduğunda ekranın üstünde görünen bant. */
@Composable
fun UpdateBanner(state: UpdateState, onDownload: (UpdateInfo) -> Unit, onInstall: () -> Unit) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.ReadyToInstall -> state.info
        is UpdateState.Error -> state.info
        else -> null
    } ?: return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("⬆️ Yeni sürüm: ${info.versionName}", fontWeight = FontWeight.Bold, color = Accent)
            if (info.notes.isNotBlank()) Text(info.notes, fontSize = 12.sp, maxLines = 3)
            Spacer(Modifier.padding(top = 6.dp))
            UpdateActions(state, info, onDownload, onInstall)
        }
    }
}

@Composable
fun UpdateActions(state: UpdateState, info: UpdateInfo, onDownload: (UpdateInfo) -> Unit, onInstall: () -> Unit) {
    val ctx = LocalContext.current
    when (state) {
        is UpdateState.Downloading -> Column {
            Text("İndiriliyor… %${(state.progress * 100).toInt()}", fontSize = 12.sp)
            LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        is UpdateState.ReadyToInstall -> Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onInstall) { Text("Kur") }
            Spacer(Modifier.width(8.dp))
            if (!UpdateManager.canInstall(ctx)) Text("Önce \"bu kaynağa izin ver\"i açın, sonra Kur'a tekrar dokunun", fontSize = 11.sp, color = Muted)
        }
        is UpdateState.Error -> Column {
            Text(state.message, fontSize = 12.sp, color = Down)
            OutlinedButton(onClick = { onDownload(info) }) { Text("Tekrar dene") }
        }
        else -> Button(onClick = { onDownload(info) }) { Text("Şimdi güncelle") }
    }
}

/** Ayarlar ekranındaki güncelleme bölümü içeriği. */
@Composable
fun UpdateSection(state: UpdateState, onCheck: () -> Unit, onDownload: (UpdateInfo) -> Unit, onInstall: () -> Unit) {
    Text("Yüklü sürüm: ${UpdateManager.currentVersionName} (${UpdateManager.currentVersionCode})", fontSize = 13.sp)
    when (state) {
        UpdateState.Checking -> Text("Kontrol ediliyor…", fontSize = 12.sp, color = Muted)
        UpdateState.UpToDate -> Text("✓ En güncel sürümü kullanıyorsunuz", fontSize = 12.sp, color = Color(0xFF2EBD85))
        is UpdateState.Available -> {
            Text("Yeni sürüm: ${state.info.versionName}", fontWeight = FontWeight.SemiBold, color = Accent)
            if (state.info.notes.isNotBlank()) Text(state.info.notes, fontSize = 12.sp, color = Muted)
            UpdateActions(state, state.info, onDownload, onInstall)
        }
        is UpdateState.Downloading -> UpdateActions(state, state.info, onDownload, onInstall)
        is UpdateState.ReadyToInstall -> UpdateActions(state, state.info, onDownload, onInstall)
        is UpdateState.Error -> {
            Text(state.message, fontSize = 12.sp, color = Down)
            state.info?.let { UpdateActions(state, it, onDownload, onInstall) }
        }
        UpdateState.Idle -> {}
    }
    if (state !is UpdateState.Downloading && state !is UpdateState.Checking) {
        OutlinedButton(onClick = onCheck) { Text("Güncellemeleri denetle") }
    }
    Text("Uygulama birkaç saatte bir otomatik kontrol eder ve yeni sürümde bildirim gönderir.", fontSize = 11.sp, color = Muted)
}
