package nl.msvos.karoodrinkwaterpunten

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val repository by lazy { WaterPointRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val uriHandler = LocalUriHandler.current
                var status by remember { mutableStateOf<CacheStatus?>(null) }
                var syncing by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    runCatching { repository.status() }
                        .onSuccess { status = it }
                        .onFailure { message = it.message ?: "De lokale gegevens konden niet worden gelezen." }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Drinkwaterpunten for Karoo", style = MaterialTheme.typography.headlineMedium)
                        Text("Publieke tappunten op de Karoo-kaart.")
                        HorizontalDivider()
                        StatusRow("Punten", status?.pointCount?.toString() ?: "Laden…")
                        StatusRow("Bronbestand", status?.sourceTime ?: "Onbekend")
                        StatusRow("Laatste sync", formatSync(status?.lastSyncMillis))
                        Button(
                            enabled = !syncing,
                            onClick = {
                                syncing = true
                                message = null
                                lifecycleScope.launch {
                                    runCatching { repository.sync() }
                                        .onSuccess {
                                            status = it.status
                                            message = if (it is WaterPointRepository.SyncResult.Updated) {
                                                "De punten zijn bijgewerkt."
                                            } else {
                                                "Je had al de nieuwste gegevens."
                                            }
                                            sendBroadcast(Intent(WaterPointRepository.CACHE_UPDATED_ACTION).setPackage(packageName))
                                        }
                                        .onFailure { message = it.message ?: "Sync mislukt." }
                                    syncing = false
                                }
                            },
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (syncing) "Bezig…" else "Sync nu")
                        }
                        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.weight(1f))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Bron", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Data: drinkwaterpunten.nl, gebaseerd op © OpenStreetMap-bijdragers (ODbL 1.0). Dit is geen officiële app van drinkwaterpunten.nl.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row {
                                    TextButton(onClick = { uriHandler.openUri("https://drinkwaterpunten.nl") }) {
                                        Text("Drinkwaterpunten.nl")
                                    }
                                    TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) {
                                        Text("OpenStreetMap")
                                    }
                                }
                            }
                        }
                        Text(
                            "Zet de laag aan of uit via de kaartlagen in de Ride-app.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelLarge)
            Text(value, modifier = Modifier.weight(0.6f))
        }
    }

    private fun formatSync(value: Long?): String = value?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
    } ?: "Nog niet handmatig gesynchroniseerd"
}
