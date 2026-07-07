package com.minor.logger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LogType {
    PACKET_RECEIVED,
    PACKET_SENT,
    MESSAGE_RECEIVED,
    MESSAGE_SENT,
    MESSAGE_DROPPED,
    MESSAGE_QUEUED,
    INFO,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val type: LogType,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    fun formatTime(): String = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
}

object MeshLogger {
    private var nextId = 0L
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(type: LogType, tag: String, message: String, details: String? = null) {
        val entry = LogEntry(id = nextId++, type = type, tag = tag, message = message, details = details)
        _logs.value += entry
    }

    fun packetReceived(tag: String, message: String, details: String? = null) = log(LogType.PACKET_RECEIVED, tag, message, details)
    fun packetSent(tag: String, message: String, details: String? = null) = log(LogType.PACKET_SENT, tag, message, details)
    fun messageReceived(tag: String, message: String, details: String? = null) = log(LogType.MESSAGE_RECEIVED, tag, message, details)
    fun messageSent(tag: String, message: String, details: String? = null) = log(LogType.MESSAGE_SENT, tag, message, details)
    fun messageDropped(tag: String, message: String, details: String? = null) = log(LogType.MESSAGE_DROPPED, tag, message, details)
    fun messageQueued(tag: String, message: String, details: String? = null) = log(LogType.MESSAGE_QUEUED, tag, message, details)
    fun info(tag: String, message: String, details: String? = null) = log(LogType.INFO, tag, message, details)
    fun error(tag: String, message: String, details: String? = null) = log(LogType.ERROR, tag, message, details)

    fun clear() {
        _logs.value = emptyList()
    }
}
