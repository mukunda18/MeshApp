package com.minor.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
                .background(Color(0xFF121212))
        ) {
            items(logs.reversed(), key = { it.id }) { entry ->
                LogItem(entry)
                HorizontalDivider(color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry) {
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
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = getLogTypeColor(entry.type),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = entry.type.name,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.tag,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 14.sp
        )
        if (entry.details != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.details,
                color = Color.Cyan.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

fun getLogTypeColor(type: LogType): Color {
    return when (type) {
        LogType.PACKET_RECEIVED -> Color(0xFF4CAF50)
        LogType.PACKET_SENT -> Color(0xFF2196F3)
        LogType.MESSAGE_RECEIVED -> Color(0xFF8BC34A)
        LogType.MESSAGE_SENT -> Color(0xFF03A9F4)
        LogType.MESSAGE_DROPPED -> Color(0xFFF44336)
        LogType.MESSAGE_QUEUED -> Color(0xFFFF9800)
        LogType.INFO -> Color(0xFF9E9E9E)
        LogType.ERROR -> Color(0xFFD32F2F)
    }
}
