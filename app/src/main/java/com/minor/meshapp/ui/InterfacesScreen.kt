package com.minor.meshapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minor.meshapp.viewmodel.NetworkViewModel

@Composable
fun StatusRow(label: String, supported: Boolean) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "$label:", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (supported) "Supported" else "Not Supported",
            color = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun InterfacesScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel = viewModel(),
) {
    val interfaces = viewModel.interfaces
    val isStaApSupported = viewModel.isStaApSupported
    val isLikelySupported = viewModel.isLikelySupported

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "Concurrency Support",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    
                    StatusRow("STA + AP (Official)", isStaApSupported)
                    
                    if (!isStaApSupported && isLikelySupported) {
                        Text(
                            text = "Legacy Support: Likely available via hardware",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Network interfaces (${interfaces.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(interfaces) { target ->
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text(target.interfaceName, style = MaterialTheme.typography.titleSmall)
                    Text("local: ${target.localAddress.hostAddress}")
                    Text("broadcast: ${target.broadcastAddress.hostAddress}")
                }
            }
        }
    }
}
