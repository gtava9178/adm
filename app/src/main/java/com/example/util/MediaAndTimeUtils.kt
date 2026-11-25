package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PickedFileData(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String?
)

object MediaAndTimeUtils {

    suspend fun compressImageUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxBytes: Int = 220_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            val scale = minOf(
                maxDimension.toFloat() / original.width,
                maxDimension.toFloat() / original.height,
                1f
            )
            val targetW = (original.width * scale).toInt().coerceAtLeast(1)
            val targetH = (original.height * scale).toInt().coerceAtLeast(1)
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                original
            }
            var quality = 82
            var encodedBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encodedBytes = out.toByteArray()
                quality -= 12
            } while (encodedBytes.size > maxBytes && quality >= 30)

            if (resized != original) resized.recycle()
            original.recycle()
            Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val clean = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun readPickedFile(context: Context, uri: Uri): PickedFileData? = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            var name = "ملف_مرفق"
            var size = 0L
            cr.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                }
            }
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val actualSize = if (size > 0) size else bytes.size.toLong()
            val encoded = if (bytes.size <= 320_000) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                null
            }
            PickedFileData(name = name, mimeType = mime, sizeBytes = actualSize, base64Data = encoded)
        } catch (_: Exception) {
            null
        }
    }

    fun formatFileSize(bytes: Long?): String {
        val b = bytes ?: 0L
        return when {
            b < 1024 -> "$b بايت"
            b < 1024 * 1024 -> "${b / 1024} ك.ب"
            else -> String.format(Locale.forLanguageTag("ar"), "%.1f م.ب", b / (1024f * 1024f))
        }
    }

    fun formatMessageTime(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return ""
        val cal = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("ar"))
        val isSameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (isSameDay) {
            timeFmt.format(date)
        } else {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
            if (isYesterday) "أمس" else SimpleDateFormat("dd/MM", Locale.forLanguageTag("ar")).format(date)
        }
    }

    fun formatLastSeenArabic(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return "غير متصل"
        val diffMs = System.currentTimeMillis() - date.time
        val minutes = diffMs / 60_000L
        val timeStr = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("ar")).format(date)
        return when {
            minutes < 1 -> "آخر ظهور منذ لحظات"
            minutes < 60 -> "آخر ظهور منذ $minutes دقيقة"
            else -> "آخر ظهور الساعة $timeStr"
        }
    }

    fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Boolean {
        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(16000)
            mr.setAudioEncodingBitRate(24000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            startedAtMs = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            cleanup()
            false
        }
    }

    fun stopAndEncode(): Pair<String, Long>? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val file = outputFile ?: return null
            val durationSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
            val bytes = file.readBytes()
            file.delete()
            outputFile = null
            if (bytes.isEmpty() || bytes.size > 450_000) return null
            Base64.encodeToString(bytes, Base64.NO_WRAP) to durationSec
        } catch (_: Exception) {
            cleanup()
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoiceNotePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    var activeMessageId: String? = null
        private set

    fun playBase64(
        messageId: String,
        base64: String,
        onCompletion: () -> Unit
    ): Boolean {
        stop()
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_$messageId.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                start()
            }
            player = mp
            activeMessageId = messageId
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        activeMessageId = null
    }
}
