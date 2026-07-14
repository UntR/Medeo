package com.untr.medeo.data.diagnostics

import android.content.Context
import android.os.Build
import com.untr.medeo.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticLogStatus(
    val enabled: Boolean = false,
    val sizeBytes: Long = 0L,
    val hasLog: Boolean = false,
    val lastModified: Long? = null
)

@Singleton
class DiagnosticLogger @Inject constructor(
    @ApplicationContext context: Context
) {
    private val enabled = AtomicBoolean(false)
    private val store = DiagnosticLogStore(File(context.cacheDir, DIAGNOSTIC_DIRECTORY))
    private val commands = Channel<LogCommand>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStatus = MutableStateFlow(store.status(enabled = false))

    val status: StateFlow<DiagnosticLogStatus> = mutableStatus.asStateFlow()
    val isEnabled: Boolean
        get() = enabled.get()

    init {
        scope.launch {
            store.deleteExpired()
            publishStatus()
            for (command in commands) {
                when (command) {
                    is LogCommand.Start -> store.start(command.line)
                    is LogCommand.Append -> store.append(command.line)
                    is LogCommand.Stop -> store.append(command.line)
                    is LogCommand.Clear -> {
                        store.clear()
                        command.completed.complete(Unit)
                    }
                    is LogCommand.Export -> command.completed.complete(store.export())
                }
                publishStatus()
            }
        }
    }

    fun start() {
        if (!enabled.compareAndSet(false, true)) return
        mutableStatus.value = mutableStatus.value.copy(enabled = true)
        commands.trySend(
            LogCommand.Start(
                diagnosticLine(
                    timestamp = timestamp(),
                    event = "session_start",
                    fields = mapOf(
                        "app_version" to BuildConfig.VERSION_NAME,
                        "version_code" to BuildConfig.VERSION_CODE,
                        "sdk_int" to Build.VERSION.SDK_INT
                    )
                )
            )
        )
    }

    fun stop() {
        if (!enabled.compareAndSet(true, false)) return
        mutableStatus.value = mutableStatus.value.copy(enabled = false)
        commands.trySend(
            LogCommand.Stop(
                diagnosticLine(timestamp(), "session_stop")
            )
        )
    }

    fun log(event: String, fields: Map<String, Any?> = emptyMap()) {
        if (!enabled.get()) return
        commands.trySend(LogCommand.Append(diagnosticLine(timestamp(), event, fields)))
    }

    suspend fun clear() {
        val completed = CompletableDeferred<Unit>()
        commands.send(LogCommand.Clear(completed))
        completed.await()
    }

    suspend fun export(): File? {
        val completed = CompletableDeferred<File?>()
        commands.send(LogCommand.Export(completed))
        return completed.await()
    }

    private fun publishStatus() {
        mutableStatus.value = store.status(enabled.get())
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private sealed interface LogCommand {
        data class Start(val line: String) : LogCommand
        data class Append(val line: String) : LogCommand
        data class Stop(val line: String) : LogCommand
        data class Clear(val completed: CompletableDeferred<Unit>) : LogCommand
        data class Export(val completed: CompletableDeferred<File?>) : LogCommand
    }

    companion object {
        private const val DIAGNOSTIC_DIRECTORY = "diagnostics"
    }
}

internal class DiagnosticLogStore(
    private val directory: File,
    private val maxBytes: Long = MAX_DIAGNOSTIC_LOG_BYTES,
    private val expiryMs: Long = DIAGNOSTIC_LOG_EXPIRY_MS,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val logFile = File(directory, "medeo-diagnostic.log")
    private val exportFile = File(directory, "medeo-diagnostic.txt")
    private var capped = false

    fun start(firstLine: String) {
        directory.mkdirs()
        exportFile.delete()
        capped = false
        FileOutputStream(logFile, false).use { output ->
            output.write("$firstLine\n".toByteArray(Charsets.UTF_8))
        }
    }

    fun append(line: String) {
        if (capped) return
        directory.mkdirs()
        val bytes = "$line\n".toByteArray(Charsets.UTF_8)
        if (logFile.length() + bytes.size > maxBytes) {
            appendLimitMarker()
            capped = true
            return
        }
        FileOutputStream(logFile, true).use { output -> output.write(bytes) }
    }

    fun export(): File? {
        if (!logFile.isFile || logFile.length() == 0L) return null
        directory.mkdirs()
        logFile.inputStream().use { input ->
            exportFile.outputStream().use { output -> input.copyTo(output) }
        }
        return exportFile
    }

    fun clear() {
        logFile.delete()
        exportFile.delete()
        capped = false
        directory.delete()
    }

    fun deleteExpired() {
        listOf(logFile, exportFile).forEach { file ->
            if (file.isFile && nowMs() - file.lastModified() > expiryMs) {
                file.delete()
            }
        }
        if (!logFile.exists() && !exportFile.exists()) directory.delete()
    }

    fun status(enabled: Boolean): DiagnosticLogStatus =
        DiagnosticLogStatus(
            enabled = enabled,
            sizeBytes = logFile.takeIf { it.isFile }?.length() ?: 0L,
            hasLog = logFile.isFile && logFile.length() > 0L,
            lastModified = logFile.takeIf { it.isFile }?.lastModified()
        )

    private fun appendLimitMarker() {
        val marker = "event=log_limit_reached max_bytes=$maxBytes\n".toByteArray(Charsets.UTF_8)
        if (logFile.length() + marker.size <= maxBytes) {
            FileOutputStream(logFile, true).use { output -> output.write(marker) }
        }
    }
}

internal fun diagnosticLine(
    timestamp: String,
    event: String,
    fields: Map<String, Any?> = emptyMap()
): String = buildString {
    append("time=")
    append(sanitizeDiagnosticValue(timestamp))
    append(" event=")
    append(sanitizeDiagnosticKey(event))
    fields.forEach { (key, value) ->
        if (value != null) {
            append(' ')
            append(sanitizeDiagnosticKey(key))
            append('=')
            append(sanitizeDiagnosticValue(value.toString()))
        }
    }
}

private fun sanitizeDiagnosticKey(value: String): String =
    value.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .take(MAX_DIAGNOSTIC_FIELD_LENGTH)
        .ifBlank { "unknown" }

internal fun sanitizeDiagnosticValue(value: String): String =
    value.replace(URL_PATTERN, "<redacted-url>")
        .replace(IPV4_PATTERN, "<redacted-ip>")
        .replace(SECRET_PATTERN) { match -> "${match.groupValues[1]}=<redacted>" }
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .take(MAX_DIAGNOSTIC_FIELD_LENGTH)
        .ifBlank { "empty" }

internal const val MAX_DIAGNOSTIC_LOG_BYTES = 2L * 1024L * 1024L
internal const val DIAGNOSTIC_LOG_EXPIRY_MS = 7L * 24L * 60L * 60L * 1000L
private const val MAX_DIAGNOSTIC_FIELD_LENGTH = 160
private val URL_PATTERN = Regex("(?i)https?://\\S+")
private val IPV4_PATTERN = Regex("(?<![A-Za-z0-9])(?:\\d{1,3}\\.){3}\\d{1,3}(?![A-Za-z0-9])")
private val SECRET_PATTERN = Regex("(?i)\\b(token|sign|signature|auth|key)=([^&\\s]+)")
