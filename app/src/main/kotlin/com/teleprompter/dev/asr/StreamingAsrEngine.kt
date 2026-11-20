package com.teleprompter.dev.asr

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Offline streaming Chinese ASR wrapper backed by sherpa-onnx Zipformer2.
 */
class StreamingAsrEngine(private val context: Context) {

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    @Volatile
    private var stream: OnlineStream? = null

    private var recordJob: Job? = null

    @Volatile
    private var pendingResetSession: Boolean = false

    val isInitialized: Boolean
        get() = recognizer != null

    fun init(modelDir: String = "asr_zh") {
        if (recognizer != null) return
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder.int8.onnx",
                    decoder = "$modelDir/decoder.onnx",
                    joiner = "$modelDir/joiner.int8.onnx"
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 4,
                debug = false,
                provider = "cpu",
                modelType = "zipformer2"
            ),
            enableEndpoint = true
        )
        val rec = OnlineRecognizer(assetManager = context.assets, config = config)
        recognizer = rec
        stream = rec.createStream("")
    }

    /** Clears accumulated utterance context (e.g., after user manually jumps in the script). */
    fun resetSession() {
        pendingResetSession = true
    }

    @SuppressLint("MissingPermission")
    fun startListening(
        scope: CoroutineScope,
        onTailUpdated: (recentText: String, deltaTokens: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (recordJob?.isActive == true) return
        val rec = recognizer
        if (rec == null) {
            onError("语音模型尚未初始化完成")
            return
        }

        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes <= 0) {
            onError("无法初始化麦克风录音缓冲区")
            return
        }

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufBytes * 2
            )
        } catch (e: Exception) {
            onError("麦克风启动失败: ${e.message}")
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("麦克风未能成功初始化，请检查权限")
            return
        }

        pendingResetSession = true

        recordJob = scope.launch(Dispatchers.IO) {
            val shortBuf = ShortArray(CHUNK_SAMPLES)
            val floatBuf = FloatArray(CHUNK_SAMPLES)
            var committedTail = ""
            var lastEmitted = ""
            var peakPartialTokens = 0

            try {
                audioRecord.startRecording()
                while (isActive) {
                    if (pendingResetSession) {
                        pendingResetSession = false
                        committedTail = ""
                        lastEmitted = ""
                        peakPartialTokens = 0
                        stream?.let { rec.reset(it) }
                    }

                    val read = audioRecord.read(shortBuf, 0, shortBuf.size)
                    if (read <= 0) continue

                    for (i in 0 until read) {
                        floatBuf[i] = shortBuf[i] / 32768.0f
                    }
                    val samples = if (read == floatBuf.size) floatBuf else floatBuf.copyOf(read)

                    val activeStream = stream ?: break
                    activeStream.acceptWaveform(samples, SAMPLE_RATE)

                    while (rec.isReady(activeStream)) {
                        rec.decode(activeStream)
                    }

                    val partial = rec.getResult(activeStream).text
                    val partialTokens = countSpokenUnits(partial)
                    val deltaTokens = (partialTokens - peakPartialTokens).coerceAtLeast(0)
                    if (partialTokens > peakPartialTokens) {
                        peakPartialTokens = partialTokens
                    }

                    val combined = (committedTail + partial).takeLast(20)
                    if (deltaTokens > 0 || (combined.isNotBlank() && combined != lastEmitted)) {
                        lastEmitted = combined
                        onTailUpdated(combined, deltaTokens)
                    }

                    if (rec.isEndpoint(activeStream)) {
                        // Keep only a tiny 2-char bridge so pauses between paragraphs don't
                        // drown out the opening words of the next paragraph.
                        committedTail = combined.takeLast(2)
                        peakPartialTokens = 0
                        rec.reset(activeStream)
                    }
                }
            } catch (e: Exception) {
                onError("语音识别异常: ${e.message}")
            } finally {
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
            }
        }
    }

    fun stopListening() {
        recordJob?.cancel()
        recordJob = null
    }

    fun release() {
        stopListening()
        stream?.release()
        recognizer?.release()
        stream = null
        recognizer = null
    }

    private fun countSpokenUnits(text: String): Int =
        text.count { it.code in 0x4E00..0x9FFF || it.isLetterOrDigit() }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 800 // 50ms per read for snappy tracking
    }
}
