/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.yukokusuzuko.teleprompter.data.AppThemePalette
import io.github.yukokusuzuko.teleprompter.data.FollowMode
import io.github.yukokusuzuko.teleprompter.data.MirrorMode
import io.github.yukokusuzuko.teleprompter.data.PrompterSettings
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterListeningGreen
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterOledBlack
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterReadDimmed
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterUnreadWhite
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterWhiteThemeActiveBg
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterWhiteThemeReadDimmed
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterWhiteThemeUnreadSilver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val GITHUB_REPO_URL = "https://github.com/YukokuSuzuko/android-teleprompter"

private enum class BannerKind {
    INFO,
    DRAG_FOLLOWING,
    TOUCH_LOCKED
}

private data class PrompterBanner(
    val id: Long,
    val text: String,
    val kind: BannerKind,
    val durationMs: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrompterScreen(
    script: String,
    currentCharOffset: Int,
    highlightEndOffset: Int,
    progressFraction: Float,
    liveCharsPerSec: Float,
    isListening: Boolean,
    isUniformPlaying: Boolean,
    isTouchLocked: Boolean,
    prompterNoticeId: Long,
    prompterNoticeText: String,
    modelStatus: String,
    heardTailText: String,
    settings: PrompterSettings,
    onToggleFollowOrPlay: () -> Unit,
    onSelectMode: (FollowMode) -> Unit,
    onTapCharOffset: (Int) -> Unit,
    onResetToStart: () -> Unit,
    onRewindStep: () -> Unit,
    onToggleOrientation: () -> Unit,
    onUpdateSettings: (PrompterSettings) -> Unit,
    onUnlockTouch: () -> Unit,
    onPickCustomFont: () -> Unit,
    onClearCustomFont: () -> Unit,
    onBackToEditor: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isVoiceMode = settings.followMode != FollowMode.UNIFORM_SCROLL
    val isActivelyFollowing = (isVoiceMode && isListening) || (!isVoiceMode && isUniformPlaying)
    val liveFollowingState by rememberUpdatedState(isActivelyFollowing)
    val latestSettings by rememberUpdatedState(settings)
    val latestOnUpdateSettings by rememberUpdatedState(onUpdateSettings)
    val latestTouchLocked by rememberUpdatedState(isTouchLocked)

    val scriptFonts = remember(context, settings.customFontFileName) {
        resolveScriptFonts(context, settings.customFontFileName)
    }

    val view = LocalView.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    val latestSettingsOpen by rememberUpdatedState(showSettingsSheet)
    var manualScrolledWhilePaused by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettingsSheet) {
        showSettingsSheet = false
    }

    LaunchedEffect(showSettingsSheet) {
        if (showSettingsSheet) {
            sheetExpanded = false
        }
    }

    // Unified non-blocking bottom floating banner state
    var activeBanner by remember { mutableStateOf<PrompterBanner?>(null) }
    var isHoldingUnlock by remember { mutableStateOf(false) }

    fun pushBanner(text: String, kind: BannerKind = BannerKind.INFO, durationMs: Long = 1800L) {
        activeBanner = PrompterBanner(
            id = System.nanoTime(),
            text = text,
            kind = kind,
            durationMs = durationMs
        )
    }

    // Auto-dismiss bottom banner unless user is actively holding the long-press unlock button
    LaunchedEffect(activeBanner?.id, isHoldingUnlock) {
        val banner = activeBanner ?: return@LaunchedEffect
        if (!isHoldingUnlock) {
            delay(banner.durationMs)
            if (activeBanner?.id == banner.id && !isHoldingUnlock) {
                activeBanner = null
            }
        }
    }

    // Forward ViewModel notices (volume± lock/unlock, font import, etc.) to the floating banner
    LaunchedEffect(prompterNoticeId) {
        if (prompterNoticeId > 0L && prompterNoticeText.isNotBlank()) {
            val kind = if (isTouchLocked) BannerKind.TOUCH_LOCKED else BannerKind.INFO
            val duration = if (isTouchLocked) 2600L else 1700L
            pushBanner(prompterNoticeText, kind = kind, durationMs = duration)
            if (isTouchLocked) {
                showSettingsSheet = false
            }
        }
    }

    // Floating font-size feedback badge triggered by A-/A+ or two-finger pinch
    var fontBadgeTrigger by remember { mutableLongStateOf(0L) }
    var showFontBadge by remember { mutableStateOf(false) }
    LaunchedEffect(fontBadgeTrigger) {
        if (fontBadgeTrigger > 0L) {
            showFontBadge = true
            delay(1300L)
            showFontBadge = false
        }
    }

    fun triggerFontChange(newSizeSp: Float) {
        val clamped = newSizeSp.coerceIn(32f, 80f)
        latestOnUpdateSettings(latestSettings.withActiveFontSize(clamped))
        fontBadgeTrigger = System.nanoTime()
    }

    fun toggleControlsVisibility() {
        val next = !controlsVisible
        controlsVisible = next
        if (!next) {
            pushBanner("已隐藏控制栏 · 双击屏幕恢复", kind = BannerKind.INFO, durationMs = 1600L)
        }
    }

    // Keep screen awake + hide system bars for distraction-free direct viewing
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val scrollState = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var targetScrollY by remember { mutableFloatStateOf(0f) }

    // Notify user when toggling play/pause (e.g. via Volume+) while in fullscreen hidden-HUD mode
    var seenInitialFollowState by remember { mutableStateOf(false) }
    LaunchedEffect(isActivelyFollowing) {
        if (!seenInitialFollowState) {
            seenInitialFollowState = true
            return@LaunchedEffect
        }
        if (!controlsVisible) {
            val msg = if (isActivelyFollowing) {
                if (isVoiceMode) "已开始语音跟读" else "已开始自动滚动"
            } else {
                "已暂停"
            }
            pushBanner(msg, kind = BannerKind.INFO, durationMs = 1200L)
        }
    }

    // Notify user when manually dragging while actively following/scrolling
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && liveFollowingState && !latestTouchLocked) {
            val hint = if (isVoiceMode) {
                "正在语音跟读，松手将自动回位 · 点句子跳转，或暂停后拖动"
            } else {
                "正在自动滚动，松手将自动回位 · 点句子跳转，或暂停后拖动"
            }
            pushBanner(hint, kind = BannerKind.DRAG_FOLLOWING, durationMs = 2800L)
        }
    }

    val isWhiteTheme = settings.themePalette == AppThemePalette.WHITE

    // Three-tier script styling with dedicated 3-level monochrome luminance hierarchy for WHITE theme
    val styledScript = remember(
        script,
        currentCharOffset,
        highlightEndOffset,
        textLayout,
        settings.lineHighlight,
        settings.boldBodyText,
        settings.themePalette,
        scriptFonts,
        cs.primary
    ) {
        val safeCursor = currentCharOffset.coerceIn(0, script.length)
        val layout = textLayout
        val (activeStart, activeEnd) = if (settings.lineHighlight && layout != null && script.isNotEmpty()) {
            val lineIdx = layout.getLineForOffset(safeCursor.coerceIn(0, script.lastIndex))
            val ls = layout.getLineStart(lineIdx).coerceIn(0, script.length)
            val le = layout.getLineEnd(lineIdx).coerceIn(ls, script.length)
            ls to le
        } else {
            val we = highlightEndOffset.coerceIn(safeCursor, script.length)
            safeCursor to we
        }

        val bodyWeight = if (settings.boldBodyText) FontWeight.Bold else FontWeight.Normal
        val bodyFont = if (settings.boldBodyText) scriptFonts.bold else scriptFonts.regular
        val readColor = if (isWhiteTheme) PrompterWhiteThemeReadDimmed else PrompterReadDimmed
        val activeColor = if (isWhiteTheme) Color.White else cs.primary
        val unreadColor = if (isWhiteTheme) PrompterWhiteThemeUnreadSilver else PrompterUnreadWhite

        buildAnnotatedString {
            if (activeStart > 0) {
                withStyle(SpanStyle(color = readColor, fontWeight = bodyWeight, fontFamily = bodyFont)) {
                    append(script.substring(0, activeStart))
                }
            }
            if (activeEnd > activeStart) {
                withStyle(SpanStyle(color = activeColor, fontWeight = FontWeight.Bold, fontFamily = scriptFonts.bold)) {
                    append(script.substring(activeStart, activeEnd))
                }
            }
            if (activeEnd < script.length) {
                withStyle(SpanStyle(color = unreadColor, fontWeight = bodyWeight, fontFamily = bodyFont)) {
                    append(script.substring(activeEnd))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PrompterOledBlack)
            .then(if (settings.avoidDisplayCutout) Modifier.displayCutoutPadding() else Modifier)
            // Two-finger pinch-to-zoom font resize (disabled while touch-locked)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pinchBaseFontSp: Float? = null
                    var cumulativeZoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        if (latestTouchLocked || latestSettingsOpen) continue
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (abs(zoomChange - 1f) > 0.005f) {
                                val baseSp = pinchBaseFontSp ?: latestSettings.activeFontSizeSp.also {
                                    pinchBaseFontSp = it
                                    cumulativeZoom = 1f
                                }
                                cumulativeZoom *= zoomChange
                                val nextSp = (baseSp * cumulativeZoom)
                                    .roundToInt()
                                    .toFloat()
                                    .coerceIn(32f, 80f)
                                if (nextSp != latestSettings.activeFontSizeSp) {
                                    triggerFontChange(nextSp)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pinchBaseFontSp != null) {
                            pinchBaseFontSp = null
                            cumulativeZoom = 1f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val density = LocalDensity.current
        val viewportHeightDp = maxHeight
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val fontSize = settings.activeFontSizeSp.sp
        val horizontalMargin = settings.activeHorizontalMarginDp.dp

        // Align reading cursor to visible screen line when resuming after paused manual drag
        LaunchedEffect(isActivelyFollowing) {
            if (isActivelyFollowing && manualScrolledWhilePaused) {
                manualScrolledWhilePaused = false
                val layout = textLayout
                if (layout != null && script.isNotEmpty()) {
                    val approxLineH = (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1f)
                    val visibleLineTop = (scrollState.value.toFloat() + settings.linesAbove * approxLineH)
                        .coerceAtLeast(0f)
                    val targetLine = layout.getLineForVerticalPosition(visibleLineTop)
                    onTapCharOffset(layout.getLineStart(targetLine))
                }
            }
        }

        val topClearanceDp = if (controlsVisible) 68.dp else 12.dp
        val topClearancePx = with(density) { topClearanceDp.toPx() }

        // Continuous intra-line scroll target + configurable already-read lines above
        LaunchedEffect(
            currentCharOffset,
            textLayout,
            script,
            settings.linesAbove,
            settings.lineHeightMultiplier,
            settings.activeFontSizeSp
        ) {
            val layout = textLayout ?: return@LaunchedEffect
            if (script.isEmpty()) return@LaunchedEffect
            val idx = currentCharOffset.coerceIn(0, script.lastIndex)
            val lineIdx = layout.getLineForOffset(idx)
            val lineTop = layout.getLineTop(lineIdx)
            val lineBottom = layout.getLineBottom(lineIdx)
            val lineHeight = (lineBottom - lineTop).coerceAtLeast(1f)
            val lineStart = layout.getLineStart(lineIdx)
            val lineEnd = layout.getLineEnd(lineIdx)
            val lineLen = (lineEnd - lineStart).coerceAtLeast(1)
            val progressInLine = ((idx - lineStart).toFloat() / lineLen.toFloat()).coerceIn(0f, 1f)

            val continuousY = lineTop + lineHeight * progressInLine
            targetScrollY = (continuousY - settings.linesAbove * lineHeight).coerceAtLeast(0f)
        }

        // Smooth silky auto-scroll ONLY while actively following; disabled when paused for free drag
        LaunchedEffect(Unit) {
            var currentY = scrollState.value.toFloat()
            var lastNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (lastNanos != 0L) {
                        if (!liveFollowingState || scrollState.isScrollInProgress) {
                            if (!liveFollowingState && scrollState.isScrollInProgress) {
                                manualScrolledWhilePaused = true
                            }
                            currentY = scrollState.value.toFloat()
                        } else {
                            val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                            val alpha = 1f - exp(-5.0f * dt)
                            val maxScroll = scrollState.maxValue.toFloat().coerceAtLeast(0f)
                            val clampedTarget = targetScrollY.coerceIn(0f, maxScroll)
                            currentY += (clampedTarget - currentY) * alpha
                            val delta = currentY - scrollState.value
                            if (abs(delta) > 0.25f) {
                                scrollState.dispatchRawDelta(delta)
                            }
                        }
                    }
                    lastNanos = frameNanos
                }
            }
        }

        val fadeStops = remember(controlsVisible) {
            if (controlsVisible) {
                arrayOf(
                    0.00f to Color.Transparent,
                    0.06f to Color.Black,
                    0.83f to Color.Black,
                    0.94f to Color.Transparent,
                    1.00f to Color.Transparent
                )
            } else {
                arrayOf(
                    0.00f to Color.Black,
                    0.90f to Color.Black,
                    1.00f to Color.Transparent
                )
            }
        }

        // Compute active line geometry for both dynamic left focus bar & WHITE-theme spotlight band
        val activeLineMetrics = remember(
            textLayout,
            currentCharOffset,
            script,
            scrollState.value,
            topClearancePx,
            settings.activeFontSizeSp,
            settings.lineHeightMultiplier
        ) {
            val layout = textLayout
            val fontPx = with(density) { fontSize.toPx() }
            if (layout == null || script.isEmpty()) {
                val estH = fontPx * settings.lineHeightMultiplier
                Triple(topClearancePx, estH, fontPx)
            } else {
                val idx = currentCharOffset.coerceIn(0, script.lastIndex)
                val lineIdx = layout.getLineForOffset(idx)
                val top = topClearancePx + layout.getLineTop(lineIdx) - scrollState.value.toFloat()
                val height = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx))
                    .coerceAtLeast(fontPx)
                Triple(top, height, fontPx)
            }
        }

        // Auto-balance horizontal CJK line-wrap remainder 50/50 so left & right gaps stay strictly symmetric
        val balancedHorizontalShiftPx = remember(
            textLayout,
            settings.activeFontSizeSp,
            settings.activeHorizontalMarginDp
        ) {
            val layout = textLayout
            if (layout == null || layout.lineCount <= 1) {
                0f
            } else {
                var maxLineW = 0f
                for (i in 0 until layout.lineCount) {
                    val w = layout.getLineRight(i) - layout.getLineLeft(i)
                    if (w > maxLineW) maxLineW = w
                }
                val boxW = layout.size.width.toFloat()
                val fontPx = with(density) { fontSize.toPx() }
                val remainder = (boxW - maxLineW).coerceIn(0f, fontPx * 2.0f)
                remainder * 0.5f
            }
        }

        // ==================== MIRRORABLE SCRIPT + FOCUS MARKER LAYER ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (settings.mirrorMode == MirrorMode.HORIZONTAL) -1f else 1f
                    scaleY = if (settings.mirrorMode == MirrorMode.VERTICAL) -1f else 1f
                }
        ) {
            val (activeTopPx, activeLineHeightPx, fontPx) = activeLineMetrics
            val lineVisible = activeTopPx + activeLineHeightPx >= -20f && activeTopPx <= viewportHeightPx + 20f

            // Subtle monochrome spotlight band behind the active line in WHITE theme
            if (isWhiteTheme && settings.lineHighlight && lineVisible) {
                val bandHeightDp = with(density) { activeLineHeightPx.toDp() }
                val balancedSideDp = horizontalMargin + with(density) { balancedHorizontalShiftPx.toDp() }
                val bandHorizontalPad = (balancedSideDp - 6.dp).coerceAtLeast(6.dp)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, activeTopPx.roundToInt()) }
                        .fillMaxWidth()
                        .padding(horizontal = bandHorizontalPad)
                        .height(bandHeightDp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrompterWhiteThemeActiveBg)
                )
            }

            // Scrollable script text with vertical edge fade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(*fadeStops),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, enabled = !isTouchLocked)
                        .padding(horizontal = horizontalMargin)
                ) {
                    Spacer(modifier = Modifier.height(topClearanceDp))
                    Text(
                        text = styledScript,
                        fontSize = fontSize,
                        lineHeight = fontSize * settings.lineHeightMultiplier,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = balancedHorizontalShiftPx
                            }
                            .pointerInput(script) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        if (latestTouchLocked) return@detectTapGestures
                                        val layout = textLayout
                                        if (layout != null && script.isNotEmpty()) {
                                            manualScrolledWhilePaused = false
                                            val clickedOffset = layout.getOffsetForPosition(tapOffset)
                                            onTapCharOffset(clickedOffset)
                                        }
                                    },
                                    onDoubleTap = {
                                        if (!latestTouchLocked) {
                                            toggleControlsVisibility()
                                        }
                                    }
                                )
                            },
                        onTextLayout = { textLayout = it }
                    )
                    Spacer(modifier = Modifier.height(viewportHeightDp * 0.90f))
                }
            }

            // Dynamic left-edge focus marker scaled, centered, and tracking balanced text left edge
            if (lineVisible) {
                val markerHeightPx = (fontPx * 0.72f)
                    .coerceIn(with(density) { 20.dp.toPx() }, activeLineHeightPx * 0.85f)
                val markerHeightDp = with(density) { markerHeightPx.toDp() }
                val markerWidthDp = (3.5f + ((settings.activeFontSizeSp - 32f) / 48f).coerceIn(0f, 1f) * 2.5f).dp
                val textLeftPx = with(density) { horizontalMargin.toPx() } + balancedHorizontalShiftPx
                val markerMarginPx = (textLeftPx - with(density) { (markerWidthDp + 6.dp).toPx() })
                    .coerceAtLeast(with(density) { 4.dp.toPx() })
                val lineCenterPx = activeTopPx + activeLineHeightPx * 0.5f
                val markerYOffsetPx = (lineCenterPx - markerHeightPx * 0.5f).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(markerMarginPx.roundToInt(), markerYOffsetPx) }
                        .height(markerHeightDp)
                        .width(markerWidthDp)
                        .background(if (isWhiteTheme) Color.White else cs.primary, CircleShape)
                )
            }
        }

        // ==================== TOP MD3 TONAL STATUS PILL ====================
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Surface(
                color = cs.surfaceContainerHigh.copy(alpha = 0.90f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: SecondaryContainer Back Pill
                    Surface(
                        onClick = onBackToEditor,
                        color = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TeleprompterIcons.ChevronLeft,
                                contentDescription = "改稿",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "改稿",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // Center: Live Status Dot + Progress Bar + Heard Tail
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val speedTag = if (settings.followMode == FollowMode.ADAPTIVE_SPEED && liveCharsPerSec > 0.5f) {
                            " · ${"%.1f".format(liveCharsPerSec)}字/秒"
                        } else {
                            ""
                        }
                        val pct = (progressFraction * 100).toInt().coerceIn(0, 100)
                        val statusColor = if (isActivelyFollowing) PrompterListeningGreen else cs.primary
                        val statusText = when (settings.followMode) {
                            FollowMode.ADAPTIVE_SPEED ->
                                if (isListening) "语速自适应$speedTag · $pct%" else "已暂停 · $pct%"
                            FollowMode.ANCHOR_STRICT ->
                                if (isListening) "精准锚点 · $pct%" else "已暂停 · $pct%"
                            FollowMode.UNIFORM_SCROLL ->
                                if (isUniformPlaying) "匀速滚动 · $pct%" else "匀速暂停 · $pct%"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        // Thin MD3 progress indicator track
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }
                        val subtitle = when {
                            isVoiceMode && heardTailText.isNotBlank() -> "听到: $heardTailText"
                            else -> modelStatus
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = cs.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right: Hide HUD Button
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { toggleControlsVisibility() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = TeleprompterIcons.VisibilityOff,
                            contentDescription = "隐藏控制栏",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "隐藏",
                            color = cs.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ==================== FULLSCREEN 2DP BOTTOM PROGRESS BAR, TALLY DOT & RESTORE BUTTON ====================
        if (!controlsVisible) {
            val fullscreenStatusColor = if (isActivelyFollowing) PrompterListeningGreen else cs.primary

            // Ultra-thin 2dp progress indicator hugging the bottom edge (green = live, dim theme = paused)
            if (settings.showFullscreenProgress) {
                val smoothProgress by animateFloatAsState(
                    targetValue = progressFraction.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                    label = "fullscreenProgress"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(smoothProgress)
                            .fillMaxHeight()
                            .background(
                                fullscreenStatusColor.copy(
                                    alpha = if (isActivelyFollowing) 0.62f else 0.34f
                                )
                            )
                    )
                }
            }

            // Bottom-left subtle tally status indicator (shows live/paused state at a glance; tap toggles)
            Surface(
                onClick = onToggleFollowOrPlay,
                color = cs.surfaceContainerHigh.copy(alpha = 0.38f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, bottom = 14.dp)
                    .height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(fullscreenStatusColor)
                    )
                    Text(
                        text = when {
                            isActivelyFollowing && isVoiceMode -> "跟读中"
                            isActivelyFollowing -> "滚动中"
                            else -> "已暂停"
                        },
                        color = cs.onSurface.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Tiny low-opacity circular restore icon in bottom-right fade zone (coexists with double-tap)
            Surface(
                onClick = { controlsVisible = true },
                color = cs.surfaceContainerHigh.copy(alpha = 0.36f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 14.dp)
                    .size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TeleprompterIcons.Visibility,
                        contentDescription = "显示控制栏",
                        tint = cs.onSurface.copy(alpha = 0.60f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ==================== FLOATING MD3 FONT SIZE TOAST BADGE ====================
        AnimatedVisibility(
            visible = showFontBadge,
            enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.90f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 74.dp)
        ) {
            Surface(
                color = cs.primaryContainer,
                contentColor = cs.onPrimaryContainer,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("字号", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${settings.activeFontSizeSp.toInt()} sp",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (settings.isLandscape) "· 横屏" else "· 竖屏",
                        fontSize = 12.sp,
                        color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // ==================== BOTTOM M3 EXPRESSIVE SPLIT DOCK ====================
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: M3 FloatingToolbar Pill
                Surface(
                    modifier = Modifier.weight(1f),
                    color = cs.surfaceContainerHigh.copy(alpha = 0.94f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact A- / A+ Quick Font Pill Group
                        Surface(
                            color = cs.surfaceContainer,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                                        .clickable { triggerFontChange(settings.activeFontSizeSp - 2f) }
                                        .padding(horizontal = 7.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A-",
                                        color = cs.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(14.dp)
                                        .background(Color.White.copy(alpha = 0.14f))
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                                        .clickable { triggerFontChange(settings.activeFontSizeSp + 2f) }
                                        .padding(horizontal = 7.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A+",
                                        color = cs.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        ToolbarIconAction(
                            icon = TeleprompterIcons.Rewind,
                            label = "回退",
                            tint = cs.onSurface,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                manualScrolledWhilePaused = false
                                onRewindStep()
                            }
                        )

                        ToolbarIconAction(
                            icon = TeleprompterIcons.SkipPrevious,
                            label = "重头",
                            tint = cs.onSurface,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                manualScrolledWhilePaused = false
                                onResetToStart()
                            }
                        )

                        ToolbarIconAction(
                            icon = TeleprompterIcons.RotateScreen,
                            label = if (settings.isLandscape) "竖屏" else "横屏",
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleOrientation
                        )

                        ToolbarIconAction(
                            icon = TeleprompterIcons.Tune,
                            label = "设置",
                            tint = cs.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { showSettingsSheet = true }
                        )
                    }
                }

                // Right: M3 Expressive Morphing Primary FAB (25dp full pill <-> 12dp squircle, zero overshoot)
                val fabCornerRadius by animateDpAsState(
                    targetValue = if (isActivelyFollowing) 25.dp else 12.dp,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "fabCornerMorph"
                )
                Button(
                    onClick = onToggleFollowOrPlay,
                    shape = RoundedCornerShape(fabCornerRadius),
                    modifier = Modifier.height(50.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isActivelyFollowing) TeleprompterIcons.Pause else TeleprompterIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActivelyFollowing) "暂停" else "继续",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // ==================== FULL-SCREEN TOUCH LOCK SHIELD ====================
        // Absorbs all accidental screen touches while locked; placed below the floating unlock banner
        if (isTouchLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            pushBanner(
                                text = "已锁定屏幕 · 同时按音量±解锁",
                                kind = BannerKind.TOUCH_LOCKED,
                                durationMs = 2600L
                            )
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }

        // ==================== UNIFIED BOTTOM FLOATING BANNER (TOAST / DRAG HINT / LOCK) ====================
        val bannerBottomPad = if (controlsVisible) 74.dp else 24.dp
        AnimatedVisibility(
            visible = activeBanner != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = bannerBottomPad)
        ) {
            val banner = activeBanner
            if (banner != null) {
                Surface(
                    color = cs.surfaceContainerHighest.copy(alpha = 0.94f),
                    contentColor = cs.onSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.28f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 14.dp,
                            end = if (banner.kind == BannerKind.INFO) 14.dp else 8.dp,
                            top = 7.dp,
                            bottom = 7.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (banner.kind == BannerKind.TOUCH_LOCKED) {
                            Icon(
                                imageVector = TeleprompterIcons.Lock,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = banner.text,
                            fontSize = 12.sp,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        when (banner.kind) {
                            BannerKind.DRAG_FOLLOWING -> {
                                Surface(
                                    onClick = {
                                        onToggleFollowOrPlay()
                                        activeBanner = null
                                    },
                                    color = cs.primary,
                                    contentColor = cs.onPrimary,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "暂停",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            BannerKind.TOUCH_LOCKED -> {
                                LongPressUnlockPill(
                                    onHoldStateChange = { holding -> isHoldingUnlock = holding },
                                    onUnlocked = {
                                        isHoldingUnlock = false
                                        onUnlockTouch()
                                    }
                                )
                            }
                            BannerKind.INFO -> Unit
                        }
                    }
                }
            }
        }

        // ==================== IN-WINDOW MD3 SETTINGS SHEET (ZERO NESTED-SCROLL HIJACKING) ====================
        // Dimmed scrim above the sheet (tap anywhere to dismiss)
        AnimatedVisibility(
            visible = showSettingsSheet,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(9f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSettingsSheet = false }
            )
        }

        AnimatedVisibility(
            visible = showSettingsSheet,
            enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f)
        ) {
            val modeLabel = if (settings.isLandscape) "横屏" else "竖屏"
            val marginRange = if (settings.isLandscape) 16f..220f else 8f..96f
            val halfHeight = (viewportHeightDp * (if (settings.isLandscape) 0.62f else 0.52f))
                .coerceAtLeast(260.dp)
            val fullHeight = (viewportHeightDp * 0.92f).coerceAtLeast(halfHeight)
            val fullHeightPx = with(density) { fullHeight.toPx() }
            val halfHeightPx = with(density) { halfHeight.toPx() }
            val collapsedOffsetPx = (fullHeightPx - halfHeightPx).coerceAtLeast(0f)

            val innerScrollState = rememberScrollState()
            // Pure GPU translationY Animatable: fixed height measured ONCE, 0 re-layouts during animation -> locked 120fps!
            val sheetOffsetY = remember(collapsedOffsetPx) {
                Animatable(if (sheetExpanded) 0f else collapsedOffsetPx)
            }
            var headerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

            Surface(
                color = cs.surfaceContainerLow,
                contentColor = cs.onSurface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullHeight)
                    .graphicsLayer {
                        translationY = sheetOffsetY.value
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Dedicated Drag Handle + Sticky Header zone using window-space coordinates (zero feedback twitch!)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { headerCoords = it }
                            .pointerInput(fullHeightPx, collapsedOffsetPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startCoords = headerCoords ?: return@awaitEachGesture
                                    var prevWindowY = startCoords.localToWindow(down.position).y
                                    var totalDeltaY = 0f
                                    var lastVelocityY = 0f
                                    var lastTimeMs = down.uptimeMillis
                                    var dragStarted = false
                                    val touchSlop = viewConfiguration.touchSlop

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break

                                        val curCoords = headerCoords ?: break
                                        val curWindowY = curCoords.localToWindow(change.position).y
                                        val dy = curWindowY - prevWindowY
                                        prevWindowY = curWindowY
                                        totalDeltaY += dy

                                        if (!dragStarted && abs(totalDeltaY) > touchSlop) {
                                            dragStarted = true
                                        }
                                        if (dragStarted) {
                                            change.consume()
                                            val dtMs = (change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)
                                            lastVelocityY = dy * 1000f / dtMs
                                            lastTimeMs = change.uptimeMillis
                                            val nextOffset = (sheetOffsetY.value + dy).coerceIn(0f, fullHeightPx)
                                            coroutineScope.launch { sheetOffsetY.snapTo(nextOffset) }
                                        }
                                    }

                                    if (dragStarted) {
                                        val current = sheetOffsetY.value
                                        val dismissThreshold = collapsedOffsetPx + (fullHeightPx - collapsedOffsetPx) * 0.35f
                                        when {
                                            lastVelocityY < -500f -> {
                                                sheetExpanded = true
                                                coroutineScope.launch {
                                                    sheetOffsetY.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                                }
                                            }
                                            lastVelocityY > 500f -> {
                                                if (current < collapsedOffsetPx * 0.85f) {
                                                    sheetExpanded = false
                                                    coroutineScope.launch {
                                                        sheetOffsetY.animateTo(collapsedOffsetPx, tween(200, easing = FastOutSlowInEasing))
                                                    }
                                                } else {
                                                    showSettingsSheet = false
                                                }
                                            }
                                            current < collapsedOffsetPx * 0.5f -> {
                                                sheetExpanded = true
                                                coroutineScope.launch {
                                                    sheetOffsetY.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                                }
                                            }
                                            current > dismissThreshold -> {
                                                showSettingsSheet = false
                                            }
                                            else -> {
                                                sheetExpanded = false
                                                coroutineScope.launch {
                                                    sheetOffsetY.animateTo(collapsedOffsetPx, tween(200, easing = FastOutSlowInEasing))
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BottomSheetDefaults.DragHandle(color = cs.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "提词与排版设置",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface
                                )
                                Text(
                                    text = "横屏竖屏独立设置，互不干扰",
                                    fontSize = 12.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = {
                                        val next = !sheetExpanded
                                        sheetExpanded = next
                                        coroutineScope.launch {
                                            if (next) {
                                                sheetOffsetY.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                                            } else {
                                                sheetOffsetY.animateTo(collapsedOffsetPx, tween(220, easing = FastOutSlowInEasing))
                                                innerScrollState.scrollTo(0)
                                            }
                                        }
                                    },
                                    color = cs.secondaryContainer,
                                    contentColor = cs.onSecondaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = if (sheetExpanded) "收起" else "展开",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    onClick = { showSettingsSheet = false },
                                    color = cs.primaryContainer,
                                    contentColor = cs.onPrimaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "完成",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Pure native verticalScroll body: ZERO nested-scroll fighting, ZERO re-layout, 120fps silk!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(innerScrollState)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                // ---------- Group 1: Typography & Layout ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsGroupLabel("字体与排版")
                        Text(
                            text = "恢复默认",
                            color = cs.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = FastWaveIndication
                                ) {
                                    onUpdateSettings(settings.resetActiveTypographyDefaults())
                                    pushBanner("已恢复${modeLabel}默认排版", durationMs = 1600L)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // 1A. Font Size Slider
                    SettingsCard(shape = topCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${modeLabel}字号", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Surface(color = cs.primaryContainer, shape = CircleShape) {
                                Text(
                                    text = "${settings.activeFontSizeSp.toInt()} sp",
                                    color = cs.onPrimaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        TeleprompterSlider(
                            value = settings.activeFontSizeSp,
                            onValueChange = { onUpdateSettings(settings.withActiveFontSize(it)) },
                            valueRange = 32f..80f
                        )
                    }

                    // 1B. Horizontal Margin Slider (placed directly below Font Size for live half-screen preview)
                    SettingsCard(shape = middleCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${modeLabel}边距", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${settings.activeHorizontalMarginDp.toInt()} dp",
                                color = cs.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TeleprompterSlider(
                            value = settings.activeHorizontalMarginDp.coerceIn(marginRange.start, marginRange.endInclusive),
                            onValueChange = { onUpdateSettings(settings.withActiveMargin(it)) },
                            valueRange = marginRange
                        )
                    }

                    // 1C. Line Height Multiplier (spring sliding pill)
                    val lineHeightOptions = listOf(1.2f to "1.2x", 1.4f to "1.4x", 1.6f to "1.6x", 1.8f to "1.8x")
                    val lhDesc = when {
                        settings.lineHeightMultiplier <= 1.25f -> "紧凑"
                        settings.lineHeightMultiplier <= 1.45f -> "标准"
                        settings.lineHeightMultiplier <= 1.65f -> "宽松"
                        else -> "特宽"
                    }
                    SettingsCard(shape = middleCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("行距", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(lhDesc, color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = lineHeightOptions,
                            selectedIndex = lineHeightOptions.indexOfFirst {
                                abs(it.first - settings.lineHeightMultiplier) < 0.05f
                            }.coerceAtLeast(0),
                            label = { it.second },
                            onSelect = { onUpdateSettings(settings.copy(lineHeightMultiplier = it.first)) }
                        )
                    }

                    // 1D. Font Weight (spring sliding pill)
                    val weightOptions = listOf(false to "常规", true to "加粗")
                    SettingsCard(shape = middleCardShape) {
                        Text("字体粗细", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = weightOptions,
                            selectedIndex = if (settings.boldBodyText) 1 else 0,
                            label = { it.second },
                            onSelect = { onUpdateSettings(settings.copy(boldBodyText = it.first)) }
                        )
                    }

                    // 1E. Local Font Import (.ttf / .otf)
                    SettingsCard(shape = middleCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("字体", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = settings.customFontDisplayName.ifBlank { "系统默认无衬线体" },
                                    color = cs.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!settings.customFontFileName.isNullOrBlank()) {
                                    Surface(
                                        onClick = onClearCustomFont,
                                        color = cs.surfaceContainerLow,
                                        contentColor = cs.onSurfaceVariant,
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, cs.outlineVariant)
                                    ) {
                                        Text(
                                            text = "恢复系统",
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = onPickCustomFont,
                                    color = cs.secondaryContainer,
                                    contentColor = cs.onSecondaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "导入字体",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 1F. Mirror Mode (for beam-splitter rigs, spring sliding pill)
                    val mirrorOptions = MirrorMode.entries
                    SettingsCard(shape = middleCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("镜像显示", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "分光镜 / 反光镜拍摄",
                                color = cs.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = mirrorOptions,
                            selectedIndex = mirrorOptions.indexOf(settings.mirrorMode).coerceAtLeast(0),
                            label = { it.displayName },
                            onSelect = { onUpdateSettings(settings.copy(mirrorMode = it)) }
                        )
                    }

                    // 1G. Lines Above (spring sliding pill)
                    val linesOptions = listOf(0 to "0 行", 1 to "1 行", 2 to "2 行")
                    SettingsCard(shape = bottomCardShape) {
                        Text("保留已读", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = linesOptions,
                            selectedIndex = settings.linesAbove.coerceIn(0, 2),
                            label = { it.second },
                            onSelect = { onUpdateSettings(settings.copy(linesAbove = it.first)) }
                        )
                    }
                }

                // ---------- Group 2: 6 Equal-Sized MD3 Themes (Strict 3x2 Grid) ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsGroupLabel("主题配色")
                    SettingsCard(shape = singleCardShape) {
                        val rows = AppThemePalette.entries.chunked(3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { palette ->
                                        val selected = settings.themePalette == palette
                                        val bgColor by animateColorAsState(
                                            targetValue = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
                                            animationSpec = tween(160),
                                            label = "themeChipBg"
                                        )
                                        val borderColor by animateColorAsState(
                                            targetValue = if (selected) cs.primary else cs.outlineVariant,
                                            animationSpec = tween(160),
                                            label = "themeChipBorder"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clip(CircleShape)
                                                .background(bgColor)
                                                .border(
                                                    width = if (selected) 1.5.dp else 1.dp,
                                                    color = borderColor,
                                                    shape = CircleShape
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = FastWaveIndication
                                                ) {
                                                    onUpdateSettings(settings.copy(themePalette = palette))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(11.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(palette.swatchHex))
                                                        .border(
                                                            0.7.dp,
                                                            Color.Black.copy(alpha = 0.25f),
                                                            CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = palette.displayName,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- Group 3: Follow Modes ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsGroupLabel("跟读推进模式")
                    val modes = FollowMode.entries
                    modes.forEachIndexed { index, mode ->
                        val shape = when (index) {
                            0 -> topCardShape
                            modes.lastIndex -> if (!isVoiceMode) middleCardShape else bottomCardShape
                            else -> middleCardShape
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(cs.surfaceContainerHigh)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = FastWaveIndication
                                ) { onSelectMode(mode) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.followMode == mode,
                                    onClick = { onSelectMode(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = cs.primary,
                                        unselectedColor = cs.outline
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mode.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(mode.description, color = cs.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (!isVoiceMode) {
                        SettingsCard(shape = bottomCardShape) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("备用匀速语速", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${"%.1f".format(settings.fallbackCharsPerSec)} 字/秒",
                                    color = cs.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TeleprompterSlider(
                                value = settings.fallbackCharsPerSec,
                                onValueChange = { onUpdateSettings(settings.copy(fallbackCharsPerSec = it)) },
                                valueRange = 2.0f..8.0f
                            )
                        }
                    }
                }

                // ---------- Group 4: Display, Hardware & Tolerance Switches ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsGroupLabel("显示与辅助开关")

                    SettingsSwitchCard(
                        title = "整行高亮（推荐）",
                        subtitle = "高亮当前整行，避免识别延迟导致高亮慢半拍",
                        checked = settings.lineHighlight,
                        shape = topCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(lineHighlight = it)) }
                    )

                    SettingsSwitchCard(
                        title = "宽松模糊匹配（推荐）",
                        subtitle = "容忍口误/跳词/加口癖，避免卡住不滚",
                        checked = settings.looseFollow,
                        shape = middleCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(looseFollow = it)) }
                    )

                    SettingsSwitchCard(
                        title = "播放时去掉空行和换行（推荐）",
                        subtitle = "段落间连续排版，消除换段空屏与卡顿",
                        checked = settings.stripLineBreaks,
                        shape = middleCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(stripLineBreaks = it)) }
                    )

                    SettingsSwitchCard(
                        title = "NG 重念自动回退",
                        subtitle = "念错倒回去重读前一句时自动跟回",
                        checked = settings.allowRollback,
                        shape = middleCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(allowRollback = it)) }
                    )

                    SettingsSwitchCard(
                        title = "全屏显示极细进度条",
                        subtitle = "隐藏控制栏时在屏幕底边显示 2dp 进度线",
                        checked = settings.showFullscreenProgress,
                        shape = middleCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(showFullscreenProgress = it)) }
                    )

                    SettingsSwitchCard(
                        title = "避让挖孔 / 刘海屏",
                        subtitle = "无挖孔全面屏可关闭，实现 100% 满屏显示",
                        checked = settings.avoidDisplayCutout,
                        shape = middleCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(avoidDisplayCutout = it)) }
                    )

                    SettingsSwitchCard(
                        title = "锁定横屏方向",
                        subtitle = "放支架/反光镜平放时不随重力意外翻转 180°",
                        checked = settings.lockLandscapeOrientation,
                        shape = bottomCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(lockLandscapeOrientation = it)) }
                    )
                }

                // Shortcut tips + clickable GitHub footer
                Text(
                    text = "提示：双指捏合缩放字号；点文字跳转，双击隐藏控制栏；音量+ 暂停/继续，音量- 回退一句，同时按音量± 锁定/解锁防误触。",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "提词器 v1.4 · by YukokuSuzuko · GitHub ↗",
                        color = cs.onSurfaceVariant.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = FastWaveIndication
                            ) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Bottom clearance when collapsed to half-screen so every item stays scrollable into view
                if (!sheetExpanded) {
                    Spacer(modifier = Modifier.height((fullHeight - halfHeight).coerceAtLeast(0.dp)))
                }
            }
                }
            }
        }
    }
}

private val topCardShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
private val middleCardShape = RoundedCornerShape(6.dp)
private val bottomCardShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val singleCardShape = RoundedCornerShape(20.dp)

/**
 * Snappy ~150ms expanding radial light-wave ripple for Settings cards & buttons.
 */
private object FastWaveIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FastWaveIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

private class FastWaveIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {

    private var pressOrigin = Offset.Unspecified
    private val radiusFraction = Animatable(0f)
    private val waveAlpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressOrigin = interaction.pressPosition
                        launch {
                            radiusFraction.snapTo(0f)
                            radiusFraction.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 145, easing = FastOutSlowInEasing)
                            )
                        }
                        launch {
                            waveAlpha.snapTo(0.18f)
                        }
                        invalidateDraw()
                    }
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> {
                        launch {
                            waveAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 155, easing = FastOutSlowInEasing)
                            )
                        }
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = waveAlpha.value
        if (alpha > 0.002f) {
            val origin = if (pressOrigin == Offset.Unspecified) center else pressOrigin
            val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 0.95f
            val r = (maxRadius * radiusFraction.value).coerceAtLeast(12f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = origin
            )
        }
    }
}

/**
 * Safety-net hold-to-unlock pill (0.8s press) inside the locked floating banner.
 * Accidental brushes/taps immediately reset without unlocking.
 */
@Composable
private fun LongPressUnlockPill(
    onHoldStateChange: (Boolean) -> Unit,
    onUnlocked: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val holdProgress = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(cs.secondaryContainer)
            .border(1.dp, cs.primary.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHoldStateChange(true)
                    var unlocked = false
                    val animJob = scope.launch {
                        holdProgress.snapTo(0f)
                        holdProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 800, easing = LinearEasing)
                        )
                        unlocked = true
                        onUnlocked()
                    }
                    waitForUpOrCancellation()
                    animJob.cancel()
                    if (!unlocked) {
                        scope.launch {
                            holdProgress.animateTo(0f, tween(120))
                        }
                    }
                    onHoldStateChange(false)
                }
            }
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        // Live horizontal progress fill while holding
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val p = holdProgress.value
                    if (p > 0f) {
                        drawRect(
                            color = cs.primary,
                            size = Size(size.width * p, size.height)
                        )
                    }
                    drawContent()
                }
        )
        Text(
            text = "长按解锁",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (holdProgress.value > 0.55f) cs.onPrimary else cs.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 11.dp)
        )
    }
}

/**
 * Sleek custom Material 3 Slider with compact 4dp x 22dp pill thumb and 8dp rounded track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeleprompterSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    val cs = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        thumb = {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(cs.primary)
            )
        },
        track = { sliderState ->
            val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                .coerceAtLeast(0.0001f)
            val fraction = ((sliderState.value - sliderState.valueRange.start) / span)
                .coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                val gap = 4.dp.toPx()
                val activeW = (size.width * fraction - gap).coerceAtLeast(0f)
                val inactiveStart = (size.width * fraction + gap).coerceAtMost(size.width)
                val inactiveW = (size.width - inactiveStart).coerceAtLeast(0f)

                if (activeW > 0f) {
                    drawRoundRect(
                        color = cs.primary,
                        topLeft = Offset.Zero,
                        size = Size(activeW, size.height),
                        cornerRadius = radius
                    )
                }
                if (inactiveW > 0f) {
                    drawRoundRect(
                        color = cs.surfaceVariant,
                        topLeft = Offset(inactiveStart, 0f),
                        size = Size(inactiveW, size.height),
                        cornerRadius = radius
                    )
                }
            }
        }
    )
}

@Composable
private fun ToolbarIconAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SettingsGroupLabel(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    shape: Shape,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

/**
 * Spring-animated sliding pill segmented control.
 */
@Composable
private fun <T> SegmentedPillRow(
    options: List<T>,
    selectedIndex: Int,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val gap = 4.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerLow)
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), CircleShape)
            .padding(4.dp)
    ) {
        val count = options.size.coerceAtLeast(1)
        val segmentWidth = (maxWidth - gap * (count - 1)) / count
        val indicatorOffsetX by animateDpAsState(
            targetValue = (segmentWidth + gap) * safeIndex,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMedium
            ),
            label = "segmentedPillSlide"
        )

        // Sliding active pill background
        Box(
            modifier = Modifier
                .offset(x = indicatorOffsetX)
                .width(segmentWidth)
                .height(34.dp)
                .clip(CircleShape)
                .background(cs.primary)
        )

        // Foreground labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            options.forEachIndexed { index, item ->
                val selected = index == safeIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    animationSpec = tween(160),
                    label = "segmentedTextColor"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (selected && options.size <= 2) {
                            Icon(
                                imageVector = TeleprompterIcons.Check,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = label(item),
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = FastWaveIndication
            ) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
                Text(subtitle, color = cs.onSurfaceVariant, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = TeleprompterIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = cs.onPrimary,
                    checkedTrackColor = cs.primary,
                    checkedIconColor = cs.primary,
                    uncheckedThumbColor = cs.outline,
                    uncheckedTrackColor = cs.surfaceContainerLow,
                    uncheckedBorderColor = cs.outline
                )
            )
        }
    }
}
