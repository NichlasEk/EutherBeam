package se.euther.eutherbeam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.euther.eutherbeam.discovery.SamsungTvDevice
import se.euther.eutherbeam.discovery.SsdpSamsungDiscovery
import se.euther.eutherbeam.protocol.SamsungIdentity
import se.euther.eutherbeam.protocol.SamsungPairingClient
import se.euther.eutherbeam.protocol.SamsungRemoteSession
import se.euther.eutherbeam.protocol.SamsungIdentityStore
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EutherBeamApp() }
    }
}

private val BeamDark = Color(0xFF1D2021)
private val BeamPanel = Color(0xFF282828)
private val BeamRaised = Color(0xFF32302F)
private val BeamOrange = Color(0xFFFE8019)
private val BeamMint = Color(0xFF8EC07C)
private val BeamYellow = Color(0xFFFABD2F)
private val BeamText = Color(0xFFEBDBB2)
private val BeamMuted = Color(0xFFA89984)

@Composable
private fun EutherBeamApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val discovery = remember { SsdpSamsungDiscovery(context) }
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<SamsungTvDevice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanGeneration by remember { mutableStateOf(0) }
    var awaitingPinFor by remember { mutableStateOf<SamsungTvDevice?>(null) }
    var pin by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var identity by remember { mutableStateOf<SamsungIdentity?>(null) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("eutherbeam", android.content.Context.MODE_PRIVATE) }
    val identityStore = remember { SamsungIdentityStore(context) }
    val clientId = remember {
        preferences.getString("client_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("client_id", it).apply()
        }
    }

    LaunchedEffect(scanGeneration) {
        scanning = true
        error = null
        runCatching { discovery.discover() }
            .onSuccess { devices = it }
            .onFailure { error = it.message ?: "Nätverkssökningen misslyckades" }
        scanning = false
    }

    LaunchedEffect(devices) {
        val device = devices.firstOrNull() ?: return@LaunchedEffect
        identity = identityStore.load(device.deviceId)
    }

    MaterialTheme {
        Surface(color = BeamDark, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(9.dp).background(BeamOrange, CircleShape))
                    Text("EUTHERBEAM // LAN CONTROL", color = BeamMint, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Text("Din TV. Ett tryck bort.", color = BeamText, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text("Lokal signal. Krypterad länk. Ingen molntjänst.", color = BeamMuted, fontSize = 15.sp)

                when {
                    scanning -> ScanningCard()
                    devices.isNotEmpty() -> DeviceCard(
                        device = devices.first(),
                        paired = identity != null,
                        working = working,
                        onPair = {
                            val device = devices.first()
                            working = true
                            actionStatus = "Ber TV:n visa en PIN…"
                            scope.launch {
                                runCatching {
                                    SamsungPairingClient(device.address, "12345", clientId, "654321").requestPin()
                                }.onSuccess {
                                    awaitingPinFor = device
                                    actionStatus = "Skriv in PIN-koden från TV:n"
                                }.onFailure { actionStatus = it.message ?: "Kunde inte starta parning" }
                                working = false
                            }
                        },
                    )
                    else -> EmptyCard(error)
                }

                identity?.let { savedIdentity ->
                    val sendKey: (String) -> Unit = { key ->
                        val device = devices.firstOrNull()
                        if (device != null) {
                            working = true
                            actionStatus = "Skickar $key…"
                            scope.launch {
                                runCatching {
                                    SamsungRemoteSession(device.address, savedIdentity, device.deviceId).sendKey(key)
                                }
                                    .onSuccess { actionStatus = "$key skickad" }
                                    .onFailure { actionStatus = it.message ?: "Kommandot misslyckades" }
                                working = false
                            }
                        }
                    }
                    NavigationCard(working = working, onKey = sendKey)
                    RemoteCard(working = working, onKey = sendKey)
                }
                actionStatus?.let { Text(it, color = BeamMuted, fontSize = 13.sp) }

                Button(
                    onClick = { scanGeneration++ },
                    enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = BeamMint, contentColor = BeamDark),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(if (scanning) "Söker…" else "Sök igen", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Text("Lokal anslutning • Ingen molntjänst", color = BeamMuted, fontSize = 12.sp)
            }
        }
    }

    awaitingPinFor?.let { device ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!working) awaitingPinFor = null },
            title = { Text("Para ${device.friendlyName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Skriv in den fyrsiffriga koden som visas på TV:n.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value -> pin = value.filter(Char::isDigit).take(4) },
                        label = { Text("PIN") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pin.length == 4 && !working,
                    onClick = {
                        working = true
                        actionStatus = "Verifierar PIN…"
                        scope.launch {
                            runCatching {
                                SamsungPairingClient(device.address, "12345", clientId, "654321").confirmPin(pin)
                            }.onSuccess { paired ->
                                identity = paired
                                identityStore.save(device.deviceId, paired)
                                awaitingPinFor = null
                                pin = ""
                                actionStatus = "TV:n är parad"
                            }.onFailure { actionStatus = it.message ?: "Parningen misslyckades" }
                            working = false
                        }
                    },
                ) { Text(if (working) "Parar…" else "Bekräfta") }
            },
            dismissButton = {
                Button(enabled = !working, onClick = { awaitingPinFor = null }) { Text("Avbryt") }
            },
        )
    }
}

@Composable
private fun ScanningCard() = BeamCard {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(color = BeamMint, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
        Column {
            Text("Söker på nätverket", color = BeamText, fontWeight = FontWeight.Bold)
            Text("Lyssnar efter Samsung-TV…", color = BeamMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DeviceCard(
    device: SamsungTvDevice,
    paired: Boolean,
    working: Boolean,
    onPair: () -> Unit,
) = BeamCard {
    Text("TV HITTAD", color = BeamMint, fontWeight = FontWeight.Black, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Text(device.friendlyName, color = BeamText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    Text("${device.modelName}  •  ${device.address}", color = BeamMuted, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))
    Button(
        onClick = onPair,
        enabled = !paired && !working,
        colors = ButtonDefaults.buttonColors(containerColor = BeamMint, contentColor = BeamDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (paired) "Parad" else "Para TV", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RemoteCard(working: Boolean, onKey: (String) -> Unit) = BeamCard {
    Text("SNABBKONTROLLER", color = BeamOrange, fontWeight = FontWeight.Black, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton("−", "KEY_VOLDOWN", working, Modifier.weight(1f), onKey)
        RemoteButton("Mute", "KEY_MUTE", working, Modifier.weight(1f), onKey)
        RemoteButton("+", "KEY_VOLUP", working, Modifier.weight(1f), onKey)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton("Källa", "KEY_SOURCE", working, Modifier.weight(1f), onKey)
        RemoteButton("Meny", "KEY_MENU", working, Modifier.weight(1f), onKey)
        RemoteButton("Stäng av", "KEY_POWEROFF", working, Modifier.weight(1f), onKey)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton("Kanal −", "KEY_CHDOWN", working, Modifier.weight(1f), onKey)
        RemoteButton("Guide", "KEY_GUIDE", working, Modifier.weight(1f), onKey)
        RemoteButton("Kanal +", "KEY_CHUP", working, Modifier.weight(1f), onKey)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton("⏪", "KEY_REWIND", working, Modifier.weight(1f), onKey)
        RemoteButton("▶", "KEY_PLAY", working, Modifier.weight(1f), onKey)
        RemoteButton("Ⅱ", "KEY_PAUSE", working, Modifier.weight(1f), onKey)
        RemoteButton("⏩", "KEY_FF", working, Modifier.weight(1f), onKey)
    }
}

@Composable
private fun NavigationCard(working: Boolean, onKey: (String) -> Unit) = BeamCard {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("NAVIGATION", color = BeamOrange, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text("H-SERIES", color = BeamMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
    Spacer(Modifier.height(16.dp))
    Box(
        modifier = Modifier
            .size(252.dp)
            .align(Alignment.CenterHorizontally)
            .background(BeamRaised, CircleShape)
            .border(BorderStroke(2.dp, BeamOrange.copy(alpha = 0.72f)), CircleShape),
    ) {
        DpadButton("↑", "KEY_UP", working, Modifier.align(Alignment.TopCenter).offset(y = 8.dp), onKey)
        DpadButton("↓", "KEY_DOWN", working, Modifier.align(Alignment.BottomCenter).offset(y = (-8).dp), onKey)
        DpadButton("←", "KEY_LEFT", working, Modifier.align(Alignment.CenterStart).offset(x = 8.dp), onKey)
        DpadButton("→", "KEY_RIGHT", working, Modifier.align(Alignment.CenterEnd).offset(x = (-8).dp), onKey)
        Button(
            onClick = { onKey("KEY_ENTER") },
            enabled = !working,
            modifier = Modifier.align(Alignment.Center).size(86.dp),
            shape = CircleShape,
            border = BorderStroke(2.dp, BeamYellow),
            colors = ButtonDefaults.buttonColors(
                containerColor = BeamOrange,
                contentColor = BeamDark,
                disabledContainerColor = BeamOrange.copy(alpha = 0.42f),
            ),
        ) { Text("OK", fontWeight = FontWeight.Black, fontSize = 18.sp) }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton("Tillbaka", "KEY_RETURN", working, Modifier.weight(1f), onKey)
        RemoteButton("Info", "KEY_INFO", working, Modifier.weight(1f), onKey)
        RemoteButton("Avsluta", "KEY_EXIT", working, Modifier.weight(1f), onKey)
    }
}

@Composable
private fun DpadButton(
    label: String,
    key: String,
    working: Boolean,
    modifier: Modifier,
    onKey: (String) -> Unit,
) {
    Button(
        onClick = { onKey(key) },
        enabled = !working,
        modifier = modifier.width(70.dp).height(66.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BeamMint.copy(alpha = 0.7f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = BeamPanel,
            contentColor = BeamText,
            disabledContainerColor = BeamPanel.copy(alpha = 0.5f),
        ),
    ) { Text(label, fontSize = 27.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun RemoteButton(
    label: String,
    key: String,
    working: Boolean,
    modifier: Modifier,
    onKey: (String) -> Unit,
) {
    Button(
        onClick = { onKey(key) },
        enabled = !working,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, BeamMint.copy(alpha = 0.45f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = BeamRaised,
            contentColor = BeamText,
            disabledContainerColor = BeamRaised.copy(alpha = 0.5f),
        ),
    ) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun EmptyCard(error: String?) = BeamCard {
    Text("Ingen TV hittades", color = BeamText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    Text(error ?: "Kontrollera att TV:n och telefonen är på samma nätverk.", color = BeamMuted, fontSize = 14.sp)
}

@Composable
private fun BeamCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BeamPanel, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, BeamMint.copy(alpha = 0.2f)), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}
