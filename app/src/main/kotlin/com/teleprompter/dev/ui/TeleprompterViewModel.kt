package com.teleprompter.dev.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teleprompter.dev.asr.StreamingAsrEngine
import com.teleprompter.dev.data.FollowMode
import com.teleprompter.dev.data.PrompterSettings
import com.teleprompter.dev.data.ScriptStore
import com.teleprompter.dev.tracker.ChineseScriptTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TeleprompterUiState(
    val script: String = "",
    val prompterScript: String = "",
    val settings: PrompterSettings = PrompterSettings(),
    val isPrompterActive: Boolean = false,
    val currentCharOffset: Int = 0,
    val highlightEndOffset: Int = 0,
    val progressFraction: Float = 0f,
    val liveCharsPerSec: Float = 0f,
    val isListening: Boolean = false,
    val isUniformPlaying: Boolean = false,
    val isModelReady: Boolean = false,
    val modelStatus: String = "离线语音模型加载中…",
    val heardTailText: String = "",
    val editorMessage: String? = null
)

class TeleprompterViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ScriptStore(application)
    private val asrEngine = StreamingAsrEngine(application)
    private val trackerLock = Any()
    private val initialRawScript = store.loadScript()
    private val initialSettings = store.loadSettings()
    private val initialPlaybackScript = ChineseScriptTracker.formatForPlayback(
        initialRawScript,
        initialSettings.stripLineBreaks
    )
    private var tracker = ChineseScriptTracker(initialPlaybackScript)
    private var uniformJob: Job? = null
    private var lastSpeechEventMs: Long = 0L
    private var emaCharsPerSec: Float = 0f

    private val _uiState = MutableStateFlow(
        TeleprompterUiState(
            script = initialRawScript,
            prompterScript = initialPlaybackScript,
            settings = initialSettings
        )
    )
    val uiState: StateFlow<TeleprompterUiState> = _uiState.asStateFlow()

    init {
        prewarmAsrModel()
    }

    private fun prewarmAsrModel() {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    asrEngine.init()
                    null
                } catch (e: Throwable) {
                    e.message ?: "模型加载失败"
                }
            }
            if (error == null) {
                _uiState.update {
                    it.copy(isModelReady = true, modelStatus = "离线中文模型已就绪")
                }
                // Auto-start listening if user already entered prompter while model was loading
                val state = _uiState.value
                if (state.isPrompterActive && state.settings.followMode != FollowMode.UNIFORM_SCROLL && state.isListening) {
                    startAsrListeningInternal()
                }
            } else {
                _uiState.update {
                    it.copy(
                        isModelReady = false,
                        modelStatus = "模型加载失败($error)，已切至匀速模式",
                        settings = it.settings.copy(followMode = FollowMode.UNIFORM_SCROLL)
                    )
                }
            }
        }
    }

    fun updateScript(newScript: String) {
        store.saveScript(newScript)
        _uiState.update { it.copy(script = newScript) }
    }

    fun loadSampleScript() {
        val sample = ScriptStore.DEFAULT_SAMPLE_SCRIPT
        updateScript(sample)
    }

    fun showEditorMessage(message: String?) {
        _uiState.update { it.copy(editorMessage = message) }
    }

    fun updateSettings(newSettings: PrompterSettings) {
        val oldStrip = _uiState.value.settings.stripLineBreaks
        store.saveSettings(newSettings)
        if (oldStrip != newSettings.stripLineBreaks && _uiState.value.isPrompterActive) {
            val formatted = ChineseScriptTracker.formatForPlayback(
                _uiState.value.script,
                newSettings.stripLineBreaks
            )
            val (offset, endOffset, progress) = synchronized(trackerLock) {
                val oldCursor = tracker.tokenCursor
                val newTracker = ChineseScriptTracker(formatted)
                if (newTracker.tokens.isNotEmpty()) {
                    val targetIdx = oldCursor.coerceIn(0, newTracker.tokens.lastIndex)
                    newTracker.jumpToRawOffset(newTracker.tokens[targetIdx].rawOffset)
                }
                tracker = newTracker
                Triple(
                    tracker.currentRawOffset(),
                    tracker.currentHighlightEndOffset(),
                    tracker.progressFraction()
                )
            }
            _uiState.update {
                it.copy(
                    settings = newSettings,
                    prompterScript = formatted,
                    currentCharOffset = offset,
                    highlightEndOffset = endOffset,
                    progressFraction = progress
                )
            }
        } else {
            _uiState.update { it.copy(settings = newSettings) }
        }
    }

    fun toggleOrientation() {
        val current = _uiState.value.settings
        updateSettings(current.copy(isLandscape = !current.isLandscape))
    }

    fun enterPrompter(hasMicPermission: Boolean) {
        val state = _uiState.value
        val formatted = ChineseScriptTracker.formatForPlayback(
            state.script,
            state.settings.stripLineBreaks
        )
        synchronized(trackerLock) {
            tracker = ChineseScriptTracker(formatted)
        }
        lastSpeechEventMs = 0L
        emaCharsPerSec = 0f
        val requestedFollow = state.settings.followMode != FollowMode.UNIFORM_SCROLL
        val useVoice = hasMicPermission && requestedFollow
        val effectiveMode = if (!hasMicPermission && requestedFollow) {
            FollowMode.UNIFORM_SCROLL
        } else {
            state.settings.followMode
        }
        val nextSettings = state.settings.copy(followMode = effectiveMode)
        if (nextSettings != state.settings) store.saveSettings(nextSettings)

        _uiState.update {
            it.copy(
                settings = nextSettings,
                prompterScript = formatted,
                isPrompterActive = true,
                currentCharOffset = 0,
                highlightEndOffset = synchronized(trackerLock) { tracker.currentHighlightEndOffset() },
                progressFraction = 0f,
                liveCharsPerSec = 0f,
                isListening = useVoice,
                isUniformPlaying = !useVoice,
                heardTailText = "",
                modelStatus = if (!hasMicPermission) "未授权麦克风，已切至匀速模式" else it.modelStatus
            )
        }
        if (useVoice && _uiState.value.isModelReady) {
            startAsrListeningInternal()
        } else if (!useVoice) {
            startUniformTicker()
        }
    }

    fun exitPrompter() {
        asrEngine.stopListening()
        uniformJob?.cancel()
        uniformJob = null
        _uiState.update {
            it.copy(
                isPrompterActive = false,
                isListening = false,
                isUniformPlaying = false,
                liveCharsPerSec = 0f
            )
        }
    }

    fun toggleFollowOrPlay(hasMicPermission: Boolean) {
        val state = _uiState.value
        val isVoiceMode = state.settings.followMode != FollowMode.UNIFORM_SCROLL
        if (isVoiceMode) {
            if (!hasMicPermission) {
                switchMode(FollowMode.UNIFORM_SCROLL, hasMicPermission = false)
                return
            }
            if (state.isListening) {
                asrEngine.stopListening()
                lastSpeechEventMs = 0L
                emaCharsPerSec = 0f
                _uiState.update { it.copy(isListening = false, liveCharsPerSec = 0f) }
            } else {
                _uiState.update { it.copy(isListening = true) }
                if (state.isModelReady) startAsrListeningInternal()
            }
        } else {
            val nextPlay = !state.isUniformPlaying
            _uiState.update { it.copy(isUniformPlaying = nextPlay) }
            if (nextPlay) startUniformTicker() else uniformJob?.cancel()
        }
    }

    fun switchMode(targetMode: FollowMode, hasMicPermission: Boolean) {
        val needsMic = targetMode != FollowMode.UNIFORM_SCROLL
        if (needsMic && !hasMicPermission) {
            val fallbackSettings = _uiState.value.settings.copy(followMode = FollowMode.UNIFORM_SCROLL)
            store.saveSettings(fallbackSettings)
            _uiState.update {
                it.copy(
                    settings = fallbackSettings,
                    isListening = false,
                    modelStatus = "需要麦克风权限才能开启语音跟读"
                )
            }
            return
        }
        if (needsMic && !_uiState.value.isModelReady) {
            _uiState.update {
                it.copy(modelStatus = "语音模型尚未就绪")
            }
            return
        }

        val updatedSettings = _uiState.value.settings.copy(followMode = targetMode)
        store.saveSettings(updatedSettings)

        if (needsMic) {
            uniformJob?.cancel()
            val wasListening = _uiState.value.isListening
            _uiState.update {
                it.copy(settings = updatedSettings, isUniformPlaying = false, isListening = true)
            }
            if (!wasListening) startAsrListeningInternal()
        } else {
            asrEngine.stopListening()
            lastSpeechEventMs = 0L
            emaCharsPerSec = 0f
            _uiState.update {
                it.copy(
                    settings = updatedSettings,
                    isListening = false,
                    isUniformPlaying = true,
                    liveCharsPerSec = 0f
                )
            }
            startUniformTicker()
        }
    }

    fun jumpToCharOffset(rawOffset: Int) {
        val (offset, endOffset, progress) = synchronized(trackerLock) {
            val o = tracker.jumpToRawOffset(rawOffset)
            Triple(o, tracker.currentHighlightEndOffset(), tracker.progressFraction())
        }
        asrEngine.resetSession()
        _uiState.update {
            it.copy(
                currentCharOffset = offset,
                highlightEndOffset = endOffset,
                progressFraction = progress,
                heardTailText = ""
            )
        }
    }

    fun rewindStep() {
        val (offset, endOffset, progress) = synchronized(trackerLock) {
            val o = tracker.stepBackward(15)
            Triple(o, tracker.currentHighlightEndOffset(), tracker.progressFraction())
        }
        asrEngine.resetSession()
        _uiState.update {
            it.copy(
                currentCharOffset = offset,
                highlightEndOffset = endOffset,
                progressFraction = progress,
                heardTailText = ""
            )
        }
    }

    fun resetToStart() {
        val (offset, endOffset, progress) = synchronized(trackerLock) {
            tracker.reset()
            Triple(
                tracker.currentRawOffset(),
                tracker.currentHighlightEndOffset(),
                tracker.progressFraction()
            )
        }
        asrEngine.resetSession()
        _uiState.update {
            it.copy(
                currentCharOffset = offset,
                highlightEndOffset = endOffset,
                progressFraction = progress,
                heardTailText = ""
            )
        }
    }

    private fun startAsrListeningInternal() {
        asrEngine.startListening(
            scope = viewModelScope,
            onTailUpdated = { recentText, deltaTokens ->
                if (deltaTokens > 0) {
                    val now = System.currentTimeMillis()
                    if (lastSpeechEventMs > 0L) {
                        val dtMs = now - lastSpeechEventMs
                        if (dtMs in 40L..2500L) {
                            val instantRate = (deltaTokens * 1000f / dtMs).coerceIn(1.5f, 9.0f)
                            emaCharsPerSec = if (emaCharsPerSec <= 0f) {
                                instantRate
                            } else {
                                emaCharsPerSec * 0.7f + instantRate * 0.3f
                            }
                        }
                    }
                    lastSpeechEventMs = now
                }

                val curSettings = _uiState.value.settings
                val (offset, endOffset, progress) = synchronized(trackerLock) {
                    val o = when (curSettings.followMode) {
                        FollowMode.ADAPTIVE_SPEED -> tracker.updateAdaptive(
                            recentAsrText = recentText,
                            deltaSpokenTokens = deltaTokens,
                            allowRollback = curSettings.allowRollback,
                            looseFollow = curSettings.looseFollow
                        )
                        FollowMode.ANCHOR_STRICT -> tracker.updateFromSpeech(
                            recentAsrText = recentText,
                            allowRollback = curSettings.allowRollback,
                            looseFollow = curSettings.looseFollow
                        )
                        FollowMode.UNIFORM_SCROLL -> tracker.currentRawOffset()
                    }
                    Triple(o, tracker.currentHighlightEndOffset(), tracker.progressFraction())
                }
                _uiState.update {
                    it.copy(
                        currentCharOffset = offset,
                        highlightEndOffset = endOffset,
                        progressFraction = progress,
                        liveCharsPerSec = emaCharsPerSec,
                        heardTailText = recentText.takeLast(14)
                    )
                }
            },
            onError = { errMsg ->
                _uiState.update {
                    it.copy(isListening = false, modelStatus = errMsg)
                }
            }
        )
    }

    private fun startUniformTicker() {
        uniformJob?.cancel()
        uniformJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (!state.isPrompterActive ||
                    state.settings.followMode != FollowMode.UNIFORM_SCROLL ||
                    !state.isUniformPlaying
                ) break

                val speed = state.settings.fallbackCharsPerSec.coerceAtLeast(1f)
                val stepMs = (1000f / speed).toLong().coerceIn(80L, 600L)
                delay(stepMs)

                val (offset, endOffset, progress, reachedEnd) = synchronized(trackerLock) {
                    val tokens = tracker.tokens
                    if (tokens.isEmpty() || tracker.tokenCursor >= tokens.lastIndex) {
                        Quad(
                            tracker.currentRawOffset(),
                            tracker.currentHighlightEndOffset(),
                            1f,
                            true
                        )
                    } else {
                        val nextOffset = tokens[tracker.tokenCursor + 1].rawOffset
                        tracker.jumpToRawOffset(nextOffset)
                        Quad(
                            tracker.currentRawOffset(),
                            tracker.currentHighlightEndOffset(),
                            tracker.progressFraction(),
                            tracker.tokenCursor >= tokens.lastIndex
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        currentCharOffset = offset,
                        highlightEndOffset = endOffset,
                        progressFraction = progress,
                        isUniformPlaying = !reachedEnd
                    )
                }
                if (reachedEnd) break
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        uniformJob?.cancel()
        asrEngine.release()
    }

    private data class Quad(
        val offset: Int,
        val endOffset: Int,
        val progress: Float,
        val reachedEnd: Boolean
    )
}
