/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yukokusuzuko.teleprompter.asr.StreamingAsrEngine
import io.github.yukokusuzuko.teleprompter.data.FollowMode
import io.github.yukokusuzuko.teleprompter.data.PrompterSettings
import io.github.yukokusuzuko.teleprompter.data.ScriptDocument
import io.github.yukokusuzuko.teleprompter.data.ScriptLibraryState
import io.github.yukokusuzuko.teleprompter.data.ScriptStore
import io.github.yukokusuzuko.teleprompter.data.StoredDraft
import io.github.yukokusuzuko.teleprompter.tracker.ChineseScriptTracker
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
    val isTouchLocked: Boolean = false,
    val prompterNoticeId: Long = 0L,
    val prompterNoticeText: String = "",
    val isModelReady: Boolean = false,
    val modelStatus: String = "离线语音模型加载中…",
    val heardTailText: String = "",
    val editorMessage: String? = null,
    val hasDraftBackup: Boolean = false,
    val documents: List<ScriptDocument> = emptyList(),
    val activeDocumentId: String = "",
    val activeDocumentTitle: String = "",
    val activeDocumentPinned: Boolean = false
)

class TeleprompterViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ScriptStore(application)
    private val asrEngine = StreamingAsrEngine(application)
    private val trackerLock = Any()
    private var libraryState: ScriptLibraryState = store.loadLibrary()
    private val initialActiveDoc = libraryState.activeDocument
    private val initialSettings = store.loadSettings()
    private val initialPlaybackScript = ChineseScriptTracker.formatForPlayback(
        initialActiveDoc.content,
        initialSettings.stripLineBreaks
    )
    private var tracker = ChineseScriptTracker(initialPlaybackScript)
    private var librarySaveJob: Job? = null
    private var uniformJob: Job? = null
    private var lastSpeechEventMs: Long = 0L
    private var emaCharsPerSec: Float = 0f

    private val _uiState = MutableStateFlow(
        TeleprompterUiState(
            script = initialActiveDoc.content,
            prompterScript = initialPlaybackScript,
            settings = initialSettings,
            hasDraftBackup = initialActiveDoc.backupContent != null,
            documents = libraryState.sortedDocuments(),
            activeDocumentId = initialActiveDoc.id,
            activeDocumentTitle = initialActiveDoc.displayTitle,
            activeDocumentPinned = initialActiveDoc.pinned
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
        val next = libraryState.updateActiveContent(newScript, System.currentTimeMillis())
        libraryState = next
        val active = next.activeDocument
        store.saveScript(newScript)
        _uiState.update {
            it.copy(
                script = newScript,
                documents = next.sortedDocuments(),
                activeDocumentId = active.id,
                activeDocumentTitle = active.displayTitle,
                activeDocumentPinned = active.pinned
            )
        }
        librarySaveJob?.cancel()
        librarySaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L)
            store.saveLibrary(next)
        }
    }

    private fun commitLibraryState(
        next: ScriptLibraryState,
        message: String? = null,
        clearMessage: Boolean = false
    ) {
        librarySaveJob?.cancel()
        libraryState = next
        val active = next.activeDocument
        store.syncLegacyActiveDraft(active)
        _uiState.update {
            it.copy(
                script = active.content,
                hasDraftBackup = active.backupContent != null,
                documents = next.sortedDocuments(),
                activeDocumentId = active.id,
                activeDocumentTitle = active.displayTitle,
                activeDocumentPinned = active.pinned,
                editorMessage = if (clearMessage) null else message ?: it.editorMessage
            )
        }
        librarySaveJob = viewModelScope.launch(Dispatchers.IO) {
            store.saveLibrary(next)
        }
    }

    private fun changeDraft(message: String? = null, action: (StoredDraft) -> StoredDraft) {
        val next = libraryState.transformActiveDraft(System.currentTimeMillis(), action)
        commitLibraryState(next, message = message)
    }

    fun replaceDraft(text: String) = changeDraft { it.replaceWith(text) }

    fun clearDraft() = changeDraft("已清空台词") { it.clear() }

    fun restoreDraft() = changeDraft("已恢复备份台词") { it.restore() }

    fun selectDocument(id: String) {
        val next = libraryState.selectDocument(id)
        commitLibraryState(next, clearMessage = true)
    }

    fun createNewDocument() {
        val next = libraryState.createDocument(
            title = "",
            content = "",
            nowMs = System.currentTimeMillis()
        )
        commitLibraryState(next, message = "已新建空白稿件")
    }

    fun importDocumentAsNew(title: String, content: String) {
        val next = libraryState.createDocument(
            title = title,
            content = content,
            nowMs = System.currentTimeMillis()
        )
        commitLibraryState(next, message = "已导入新稿件（${content.length} 字）")
    }

    fun renameDocument(id: String, newTitle: String) {
        val next = libraryState.renameDocument(id, newTitle, System.currentTimeMillis())
        if (next != libraryState) {
            commitLibraryState(next, message = "已重命名稿件")
        }
    }

    fun togglePinDocument(id: String) {
        val next = libraryState.togglePin(id)
        if (next != libraryState) {
            val nowPinned = next.documents.firstOrNull { it.id == id }?.pinned == true
            commitLibraryState(next, message = if (nowPinned) "已置顶稿件" else "已取消置顶")
        }
    }

    fun deleteDocument(id: String) {
        val next = libraryState.deleteDocument(id, System.currentTimeMillis())
        commitLibraryState(next, message = "已删除稿件")
    }

    fun loadSampleScript() {
        replaceDraft(ScriptStore.DEFAULT_SAMPLE_SCRIPT)
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
                newTracker.jumpToTokenIndex(oldCursor)
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

    fun toggleTouchLock() {
        val nextLocked = !_uiState.value.isTouchLocked
        val notice = if (nextLocked) {
            "已锁定屏幕 · 同时按音量±解锁"
        } else {
            "已解锁屏幕"
        }
        _uiState.update {
            it.copy(
                isTouchLocked = nextLocked,
                prompterNoticeId = System.nanoTime(),
                prompterNoticeText = notice
            )
        }
    }

    fun setTouchLocked(locked: Boolean) {
        if (_uiState.value.isTouchLocked == locked) return
        _uiState.update {
            it.copy(
                isTouchLocked = locked,
                prompterNoticeId = System.nanoTime(),
                prompterNoticeText = if (locked) "已锁定屏幕 · 同时按音量±解锁" else "已解锁屏幕"
            )
        }
    }

    fun showPrompterNotice(message: String) {
        _uiState.update {
            it.copy(
                prompterNoticeId = System.nanoTime(),
                prompterNoticeText = message
            )
        }
    }

    fun applyCustomFont(fileName: String, displayName: String) {
        val updated = _uiState.value.settings.copy(
            customFontFileName = fileName,
            customFontDisplayName = displayName
        )
        updateSettings(updated)
        showPrompterNotice("已应用字体：$displayName")
    }

    fun resetCustomFont() {
        val updated = _uiState.value.settings.copy(
            customFontFileName = null,
            customFontDisplayName = ""
        )
        updateSettings(updated)
        showPrompterNotice("已恢复系统默认字体")
    }

    fun enterPrompter(hasMicPermission: Boolean) {
        librarySaveJob?.cancel()
        store.saveLibrary(libraryState)
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
                isTouchLocked = false,
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
                isTouchLocked = false,
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
                        val nextOffset = tracker.stepForward()
                        Quad(
                            nextOffset,
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
        librarySaveJob?.cancel()
        store.saveLibrary(libraryState)
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
