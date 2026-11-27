package com.meshapp.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit = {}
) {
    val logs by MeshLogger.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Logs") },
                actions = {
                    IconButton(onClick = { MeshLogger.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(logs.reversed(), key = { it.id }) { entry ->
                LogItem(entry)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry) {
    val colors = logTypeColors(entry.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = entry.formatTime(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = colors.first,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = entry.type.name,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.second
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.message,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
        if (entry.details != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.details,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun logTypeColors(type: LogType): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        LogType.PACKET_RECEIVED -> scheme.primaryContainer to scheme.onPrimaryContainer
        LogType.PACKET_SENT -> scheme.secondaryContainer to scheme.onSecondaryContainer
        LogType.MESSAGE_RECEIVED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        LogType.MESSAGE_SENT -> scheme.secondary to scheme.onSecondary
        LogType.MESSAGE_DROPPED -> scheme.error to scheme.onError
        LogType.MESSAGE_QUEUED -> scheme.tertiary to scheme.onTertiary
        LogType.INFO -> scheme.surfaceVariant to scheme.onSurfaceVariant
        LogType.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
}
