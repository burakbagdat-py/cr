package com.kriptoradar.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.ConnState
import com.kriptoradar.ui.components.UpdateBanner
import com.kriptoradar.ui.screens.AlertsScreen
import com.kriptoradar.ui.screens.CoinDetailScreen
import com.kriptoradar.ui.screens.MarketScreen
import com.kriptoradar.ui.screens.SettingsScreen
import com.kriptoradar.ui.screens.SignalsScreen
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.KriptoTheme
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openSymbol = mutableStateOf<String?>(null)

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        handleIntent(intent)
        setContent { KriptoTheme { App(vm, openSymbol.value) { openSymbol.value = it } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(EXTRA_SYMBOL)?.let { openSymbol.value = it }
        if (i?.getBooleanExtra(EXTRA_UPDATE, false) == true) vm.checkUpdate()
    }

    companion object {
        const val EXTRA_SYMBOL = "symbol"
        const val EXTRA_UPDATE = "update"
    }
}

private enum class Tab(val label: String) { MARKET("Piyasa"), SIGNALS("Sinyaller"), ALERTS("Alarmlar"), SETTINGS("Ayarlar") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel, detail: String?, setDetail: (String?) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.MARKET) }
    BackHandler(enabled = detail != null) { setDetail(null) }

    if (detail != null) {
        CoinDetailScreen(vm, detail, onBack = { setDetail(null) })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kripto Radar", fontWeight = FontWeight.Bold) },
                actions = { ConnBadge(vm) },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = {
                            Icon(when (t) {
                                Tab.MARKET -> Icons.Default.Home
                                Tab.SIGNALS -> Icons.Default.Star
                                Tab.ALERTS -> Icons.Default.Notifications
                                Tab.SETTINGS -> Icons.Default.Settings
                            }, null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val upd by vm.update.collectAsStateWithLifecycle()
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (tab != Tab.SETTINGS) UpdateBanner(upd, vm::downloadUpdate, vm::installUpdate)
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (tab) {
                    Tab.MARKET -> MarketScreen(vm, onOpen = setDetail)
                    Tab.SIGNALS -> SignalsScreen(vm, onOpen = setDetail)
                    Tab.ALERTS -> AlertsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun ConnBadge(vm: MainViewModel) {
    val st by vm.conn.collectAsStateWithLifecycle()
    val last by vm.lastMsg.collectAsStateWithLifecycle()
    val tick by produceState(0L) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    val age = if (last > 0) (tick - last) / 1000 else -1
    val (col, text) = when (st) {
        ConnState.LIVE -> (if (age in 0..5) Up else Accent) to (if (age >= 0) "Canlı · ${age}sn" else "Canlı")
        ConnState.CONNECTING -> Accent to "Bağlanıyor"
        ConnState.RECONNECTING -> Accent to "Yeniden bağlanıyor"
        ConnState.DISCONNECTED -> Down to "Çevrimdışı"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
