package com.example.simrelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simrelay.ui.theme.SimRelayColors
import com.example.simrelay.ui.theme.SimrelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimrelayTheme {
                val vm: SimRelayViewModel = viewModel()
                val state by vm.uiState.collectAsState()
                SimRelayApp(state = state, onAction = vm)
            }
        }
    }
}

@Composable
fun SimRelayApp(state: SimRelayUiState, onAction: SimRelayViewModel, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SimRelayColors.Background,
        bottomBar = {
            SimRelayBottomBar(
                selectedTab = state.selectedTab,
                onTabSelected = onAction::selectTab,
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(SimRelayColors.Background, SimRelayColors.SurfaceDeep)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SimRelayHeader()
                ServerStatusCard(state = state, onStart = onAction::startServer, onStop = onAction::stopServer)
                ApiKeyCard(apiKey = state.apiKey)
                SmsComposeCard(
                    state = state,
                    onToChange = onAction::setSmsTo,
                    onMessageChange = onAction::setSmsMessage,
                    onSend = onAction::sendSms,
                )
                RecentLogsCard(state = state)
                state.errorMessage?.let { InfoBanner(text = it, color = SimRelayColors.Error) }
                state.smsStatus?.let { InfoBanner(text = it, color = SimRelayColors.Success) }
            }
        }
    }
}

@Composable
private fun SimRelayHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderBadge("<>")
        Text(
            text = "SimRelay",
            style = MaterialTheme.typography.headlineMedium,
            color = SimRelayColors.Accent,
            fontWeight = FontWeight.SemiBold,
        )
        HeaderBadge("⚙")
    }
}

@Composable
private fun HeaderBadge(label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = SimRelayColors.Surface, border = BorderStroke(1.dp, SimRelayColors.Border)) {
        Text(label, color = SimRelayColors.Accent, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ServerStatusCard(state: SimRelayUiState, onStart: () -> Unit, onStop: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SimRelayColors.Surface),
        border = BorderStroke(1.dp, SimRelayColors.Border),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(SimRelayColors.Pulse, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("▣", color = SimRelayColors.Success, style = MaterialTheme.typography.displaySmall)
            }
            Text(
                text = if (state.serverRunning) "Server Running" else if (state.serverStarting) "Starting Server" else "Server Offline",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.serverRunning) SimRelayColors.Success else SimRelayColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.lastActionMessage,
                style = MaterialTheme.typography.titleMedium,
                color = SimRelayColors.TextSecondary,
            )
            AddressCard(host = state.serverHost, port = state.serverPort)
            Button(
                onClick = if (state.serverRunning) onStop else onStart,
                enabled = !state.serverStarting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.serverRunning) "Stop Server" else "Start Server")
            }
        }
    }
}

@Composable
private fun AddressCard(host: String, port: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = SimRelayColors.SurfaceElevated), border = BorderStroke(1.dp, SimRelayColors.Border), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("LOCAL ADDRESS", color = SimRelayColors.TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("$host : $port", color = SimRelayColors.Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = {}) { Text("⧉", color = SimRelayColors.TextPrimary) }
        }
    }
}

@Composable
private fun ApiKeyCard(apiKey: String) {
    Card(colors = CardDefaults.cardColors(containerColor = SimRelayColors.Surface), border = BorderStroke(1.dp, SimRelayColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌁", color = SimRelayColors.TextSecondary)
                Spacer(Modifier.width(10.dp))
                Text("API KEY", color = SimRelayColors.TextSecondary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Card(colors = CardDefaults.cardColors(containerColor = SimRelayColors.SurfaceElevated), border = BorderStroke(1.dp, SimRelayColors.Border), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(apiKey, color = SimRelayColors.TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {}) { Text("⧉", color = SimRelayColors.TextPrimary) }
                }
            }
        }
    }
}

@Composable
private fun SmsComposeCard(
    state: SimRelayUiState,
    onToChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = SimRelayColors.Surface), border = BorderStroke(1.dp, SimRelayColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("SMS RELAY", color = SimRelayColors.TextSecondary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = state.smsTo, onValueChange = onToChange, label = { Text("Recipient") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.smsMessage, onValueChange = onMessageChange, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) { Text("Send SMS") }
        }
    }
}

@Composable
private fun RecentLogsCard(state: SimRelayUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = SimRelayColors.Surface), border = BorderStroke(1.dp, SimRelayColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▣", color = SimRelayColors.TextSecondary)
                    Spacer(Modifier.width(10.dp))
                    Text("RECENT LOGS", color = SimRelayColors.TextSecondary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("LIVE", color = SimRelayColors.Cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            state.recentLogs.forEachIndexed { index, log ->
                if (index > 0) {
                    Spacer(Modifier.height(6.dp))
                }
                LogRow(log)
            }
        }
    }
}

@Composable
private fun LogRow(log: RecentLog) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(log.code, color = SimRelayColors.Success, fontWeight = FontWeight.SemiBold)
        Text("${log.method}  ${log.path}", color = SimRelayColors.TextSecondary)
    }
}

@Composable
private fun InfoBanner(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = color, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SimRelayBottomBar(selectedTab: SimRelayTab, onTabSelected: (SimRelayTab) -> Unit) {
    NavigationBar(containerColor = SimRelayColors.NavBar, tonalElevation = 0.dp) {
        bottomTabs().forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab.tab,
                onClick = { onTabSelected(tab.tab) },
                icon = { Text(tab.label.take(1)) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = SimRelayColors.Accent, selectedTextColor = SimRelayColors.Accent, unselectedIconColor = SimRelayColors.TextSecondary, unselectedTextColor = SimRelayColors.TextSecondary),
            )
        }
    }
}

private data class BottomTab(val tab: SimRelayTab, val label: String)

private fun bottomTabs() = listOf(
    BottomTab(SimRelayTab.Console, "Console"),
    BottomTab(SimRelayTab.Messages, "Messages"),
    BottomTab(SimRelayTab.Devices, "Devices"),
    BottomTab(SimRelayTab.Logs, "Logs"),
)

@Preview(showBackground = true)
@Composable
private fun SimRelayPreview() {
    SimrelayTheme {
        SimRelayApp(state = SimRelayUiState(), onAction = SimRelayViewModel())
    }
}