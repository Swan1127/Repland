package com.swan1127.repland.ui.agent

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swan1127.repland.ui.components.PlannerIcons
import java.util.Locale

/**
 * A Doubao-style voice strip: the input row is replaced by a live transcript and a mic,
 * and dragging upward past a threshold arms "cancel", so releasing there throws the
 * utterance away instead of sending it.
 *
 * Uses `SpeechRecognizer` rather than `RecognizerIntent` because the latter opens the
 * system dialog and cannot stream partial words into this bar. If the device has no
 * on-device recogniser the caller is told to fall back.
 */
@Composable
fun VoiceInputBar(
    onFinish: (text: String, cancelled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var partial by remember { mutableStateOf("") }
    var cancelled by remember { mutableStateOf(false) }
    var dragUp by remember { mutableFloatStateOf(0f) }
    var finished by remember { mutableStateOf(false) }

    val recognizer = remember {
        runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    fun complete(text: String, wasCancelled: Boolean) {
        if (finished) return
        finished = true
        onFinish(text, wasCancelled)
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.let { partial = it }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: partial
                complete(text, cancelled)
            }
            override fun onError(error: Int) {
                // No speech / no network / recogniser unavailable: keep any partial words
                // the user already saw rather than discarding them silently.
                complete(partial, partial.isBlank())
            }
        }
        runCatching {
            recognizer?.setRecognitionListener(listener)
            recognizer?.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "说出今天需要安排的事")
                },
            )
        }.onFailure { complete("", true) }
        onDispose { runCatching { recognizer?.destroy() } }
    }

    val cancelThreshold = 84.dp
    val armed = cancelled

    Surface(
        color = if (armed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (armed) {
                            runCatching { recognizer?.cancel() }
                            complete(partial, true)
                        } else {
                            dragUp = 0f
                        }
                    },
                    onVerticalDrag = { _, delta ->
                        dragUp = (dragUp + delta).coerceAtMost(0f)
                        cancelled = -dragUp > cancelThreshold.toPx()
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(PlannerIcons.Voice, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    partial.ifBlank { if (armed) "松开取消" else "正在听…" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (armed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    if (armed) "松手丢弃这段语音" else "上滑取消 · 说完点完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    if (armed) {
                        runCatching { recognizer?.cancel() }
                        complete(partial, true)
                    } else {
                        runCatching { recognizer?.stopListening() }
                    }
                },
            ) { Text(if (armed) "取消" else "完成") }
            TextButton(onClick = { runCatching { recognizer?.cancel() }; complete(partial, true); onDismiss() }) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭语音", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Whether this device can run the in-app recogniser; the caller falls back otherwise. */
fun isInAppVoiceAvailable(context: android.content.Context): Boolean =
    runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
