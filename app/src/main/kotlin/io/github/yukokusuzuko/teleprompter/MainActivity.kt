/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.yukokusuzuko.teleprompter.ui.EditorScreen
import io.github.yukokusuzuko.teleprompter.ui.PrompterScreen
import io.github.yukokusuzuko.teleprompter.ui.TeleprompterViewModel
import io.github.yukokusuzuko.teleprompter.ui.clearCustomFontFiles
import io.github.yukokusuzuko.teleprompter.ui.importCustomFontFile
import io.github.yukokusuzuko.teleprompter.ui.readTxtFileSmart
import io.github.yukokusuzuko.teleprompter.ui.theme.TeleprompterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: TeleprompterViewModel by viewModels()
    private var scriptImportRequest = 0L
    private var importTxtAsNewDocument = false

    // Volume-up + volume-down chord tracking for touch-lock toggle
    private var isVolumeUpPressed = false
    private var isVolumeDownPressed = false
    private var volumeChordConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureCutoutMode()
        requestHighRefreshRate()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.enterPrompter(hasMicPermission = granted)
            }

            val txtPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                val request = ++scriptImportRequest
                val asNew = importTxtAsNewDocument
                importTxtAsNewDocument = false
                if (uri != null) {
                    val draftAtSelection = viewModel.uiState.value.script
                    lifecycleScope.launch {
                        val (text, title) = withContext(Dispatchers.IO) {
                            val content = readTxtFileSmart(applicationContext, uri)
                            val docTitle = if (asNew && content != null) {
                                queryTxtFileTitle(applicationContext, uri)
                            } else {
                                ""
                            }
                            content to docTitle
                        }
                        if (request != scriptImportRequest) return@launch
                        val current = viewModel.uiState.value
                        when {
                            text == null -> viewModel.showEditorMessage(
                                if (asNew) {
                                    "未导入新稿件。请选择可读取的非空 UTF-8 或 GB18030 文本（最多 1 MiB）"
                                } else {
                                    "未导入，原台词未更改。请选择可读取的非空 UTF-8 或 GB18030 文本（最多 1 MiB）"
                                }
                            )
                            asNew -> {
                                if (current.isPrompterActive) {
                                    viewModel.showEditorMessage("已退出编辑页，未导入新稿件")
                                } else {
                                    viewModel.importDocumentAsNew(title, text)
                                }
                            }
                            current.isPrompterActive || current.script != draftAtSelection ->
                                viewModel.showEditorMessage("编辑状态已改变，未替换台词。请重新导入文件")
                            else -> {
                                viewModel.replaceDraft(text)
                                viewModel.showEditorMessage("已导入文件（${text.length} 字）")
                            }
                        }
                    }
                }
            }

            val fontPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    lifecycleScope.launch {
                        val imported = withContext(Dispatchers.IO) {
                            importCustomFontFile(applicationContext, uri)
                        }
                        if (imported != null) {
                            viewModel.applyCustomFont(imported.savedFileName, imported.displayName)
                        } else {
                            viewModel.showPrompterNotice("字体文件无效，请选择 .ttf 或 .otf 字体")
                        }
                    }
                }
            }

            // Apply orientation & optional landscape rotation lock while inside PrompterScreen
            LaunchedEffect(
                state.isPrompterActive,
                state.settings.isLandscape,
                state.settings.lockLandscapeOrientation
            ) {
                requestedOrientation = if (!state.isPrompterActive) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else if (state.settings.isLandscape) {
                    if (state.settings.lockLandscapeOrientation) {
                        val rot = currentDisplayRotation()
                        if (rot == Surface.ROTATION_270) {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            TeleprompterTheme(palette = state.settings.themePalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (state.isPrompterActive) {
                        BackHandler {
                            if (state.isTouchLocked) {
                                viewModel.showPrompterNotice("已锁定屏幕 · 同时按音量±解锁")
                            } else {
                                viewModel.exitPrompter()
                            }
                        }
                        PrompterScreen(
                            script = state.prompterScript,
                            currentCharOffset = state.currentCharOffset,
                            highlightEndOffset = state.highlightEndOffset,
                            progressFraction = state.progressFraction,
                            liveCharsPerSec = state.liveCharsPerSec,
                            isListening = state.isListening,
                            isUniformPlaying = state.isUniformPlaying,
                            isTouchLocked = state.isTouchLocked,
                            prompterNoticeId = state.prompterNoticeId,
                            prompterNoticeText = state.prompterNoticeText,
                            modelStatus = state.modelStatus,
                            heardTailText = state.heardTailText,
                            settings = state.settings,
                            onToggleFollowOrPlay = {
                                viewModel.toggleFollowOrPlay(hasMicPermission())
                            },
                            onSelectMode = { mode ->
                                viewModel.switchMode(mode, hasMicPermission())
                            },
                            onTapCharOffset = viewModel::jumpToCharOffset,
                            onResetToStart = viewModel::resetToStart,
                            onRewindStep = viewModel::rewindStep,
                            onToggleOrientation = viewModel::toggleOrientation,
                            onUpdateSettings = viewModel::updateSettings,
                            onUnlockTouch = { viewModel.setTouchLocked(false) },
                            onPickCustomFont = {
                                fontPickerLauncher.launch(
                                    arrayOf(
                                        "font/ttf",
                                        "font/otf",
                                        "application/x-font-ttf",
                                        "application/x-font-opentype",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            onClearCustomFont = {
                                clearCustomFontFiles(applicationContext)
                                viewModel.resetCustomFont()
                            },
                            onBackToEditor = viewModel::exitPrompter
                        )
                    } else {
                        EditorScreen(
                            script = state.script,
                            documents = state.documents,
                            activeDocumentId = state.activeDocumentId,
                            activeDocumentTitle = state.activeDocumentTitle,
                            activeDocumentPinned = state.activeDocumentPinned,
                            onScriptChange = viewModel::updateScript,
                            onPasteScript = viewModel::replaceDraft,
                            onClearDraft = viewModel::clearDraft,
                            hasDraftBackup = state.hasDraftBackup,
                            onRestoreDraft = viewModel::restoreDraft,
                            onLoadSample = viewModel::loadSampleScript,
                            onOpenTxtPicker = {
                                importTxtAsNewDocument = false
                                txtPickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            },
                            onOpenTxtPickerAsNew = {
                                importTxtAsNewDocument = true
                                txtPickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            },
                            onSelectDocument = viewModel::selectDocument,
                            onCreateDocument = viewModel::createNewDocument,
                            onRenameDocument = viewModel::renameDocument,
                            onTogglePinDocument = viewModel::togglePinDocument,
                            onDeleteDocument = viewModel::deleteDocument,
                            onStartPrompter = {
                                if (hasMicPermission()) {
                                    viewModel.enterPrompter(hasMicPermission = true)
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onShowMessage = viewModel::showEditorMessage,
                            statusMessage = state.editorMessage
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
    }

    override fun onPause() {
        super.onPause()
        resetVolumeKeyState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) resetVolumeKeyState()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.uiState.value.isPrompterActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event?.repeatCount == 0) {
                        isVolumeUpPressed = true
                        if (isVolumeDownPressed && !volumeChordConsumed) {
                            volumeChordConsumed = true
                            viewModel.toggleTouchLock()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event?.repeatCount == 0) {
                        isVolumeDownPressed = true
                        if (isVolumeUpPressed && !volumeChordConsumed) {
                            volumeChordConsumed = true
                            viewModel.toggleTouchLock()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (event?.repeatCount == 0) {
                        viewModel.toggleFollowOrPlay(hasMicPermission())
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_PAGE_UP -> {
                    if (event?.repeatCount == 0) {
                        viewModel.rewindStep()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.uiState.value.isPrompterActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val wasPressed = isVolumeUpPressed
                    isVolumeUpPressed = false
                    if (!volumeChordConsumed && wasPressed) {
                        viewModel.toggleFollowOrPlay(hasMicPermission())
                    }
                    if (!isVolumeUpPressed && !isVolumeDownPressed) {
                        volumeChordConsumed = false
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val wasPressed = isVolumeDownPressed
                    isVolumeDownPressed = false
                    if (!volumeChordConsumed && wasPressed) {
                        viewModel.rewindStep()
                    }
                    if (!isVolumeUpPressed && !isVolumeDownPressed) {
                        volumeChordConsumed = false
                    }
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun resetVolumeKeyState() {
        isVolumeUpPressed = false
        isVolumeDownPressed = false
        volumeChordConsumed = false
    }

    private fun configureCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun requestHighRefreshRate() {
        runCatching {
            val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val currentMode = activeDisplay?.mode ?: return
            val bestMode = activeDisplay.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: return
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = bestMode.modeId
                preferredRefreshRate = bestMode.refreshRate
            }
        }
    }

    private fun currentDisplayRotation(): Int {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }
        }.getOrDefault(Surface.ROTATION_0)
    }

    private fun queryTxtFileTitle(context: Context, uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull() ?: return ""
        val stem = raw.substringBeforeLast('.').ifBlank { raw }.trim()
        return stem.take(40)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
