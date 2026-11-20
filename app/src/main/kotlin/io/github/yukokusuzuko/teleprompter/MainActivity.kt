/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
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
import io.github.yukokusuzuko.teleprompter.ui.EditorScreen
import io.github.yukokusuzuko.teleprompter.ui.PrompterScreen
import io.github.yukokusuzuko.teleprompter.ui.TeleprompterViewModel
import io.github.yukokusuzuko.teleprompter.ui.readTxtFileSmart
import io.github.yukokusuzuko.teleprompter.ui.theme.TeleprompterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TeleprompterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                if (uri != null) {
                    val text = readTxtFileSmart(this, uri)
                    if (text != null) {
                        viewModel.updateScript(text.trim())
                        viewModel.showEditorMessage("已导入文件（${text.trim().length} 字）")
                    } else {
                        viewModel.showEditorMessage("文件读取失败，请确认是纯文本 .txt 文件")
                    }
                }
            }

            // Apply orientation lock only while inside PrompterScreen
            LaunchedEffect(state.isPrompterActive, state.settings.isLandscape) {
                requestedOrientation = if (!state.isPrompterActive) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else if (state.settings.isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                            viewModel.exitPrompter()
                        }
                        PrompterScreen(
                            script = state.prompterScript,
                            currentCharOffset = state.currentCharOffset,
                            highlightEndOffset = state.highlightEndOffset,
                            progressFraction = state.progressFraction,
                            liveCharsPerSec = state.liveCharsPerSec,
                            isListening = state.isListening,
                            isUniformPlaying = state.isUniformPlaying,
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
                            onBackToEditor = viewModel::exitPrompter
                        )
                    } else {
                        EditorScreen(
                            script = state.script,
                            onScriptChange = viewModel::updateScript,
                            onLoadSample = viewModel::loadSampleScript,
                            onOpenTxtPicker = {
                                txtPickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            },
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.uiState.value.isPrompterActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    viewModel.toggleFollowOrPlay(hasMicPermission())
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_PAGE_UP -> {
                    viewModel.rewindStep()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
