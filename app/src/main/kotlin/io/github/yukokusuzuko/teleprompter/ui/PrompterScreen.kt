/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.yukokusuzuko.teleprompter.data.AppThemePalette
import io.github.yukokusuzuko.teleprompter.data.FollowMode
import io.github.yukokusuzuko.teleprompter.data.PrompterSettings
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterListeningGreen
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterOledBlack
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterReadDimmed
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterUnreadWhite
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrompterScreen(
    script: String,
    currentCharOffset: Int,
    highlightEndOffset: Int,
    progressFraction: Float,
    liveCharsPerSec: Float,
    isListening: Boolean,
    isUniformPlaying: Boolean,
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
    onBackToEditor: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isVoiceMode = settings.followMode != FollowMode.UNIFORM_SCROLL
    val isActivelyFollowing = (isVoiceMode && isListening) || (!isVoiceMode && isUniformPlaying)
    val liveFollowingState by rememberUpdatedState(isActivelyFollowing)
    val latestSettings by rememberUpdatedState(settings)
    val latestOnUpdateSettings by rememberUpdatedState(onUpdateSettings)

    val view = LocalView.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var manualScrolledWhilePaused by remember { mutableStateOf(false) }

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

    // Three-tier script styling respecting lineHighlight, boldBodyText, and active MD3 primary color
    val styledScript = remember(
        script,
        currentCharOffset,
        highlightEndOffset,
        textLayout,
        settings.lineHighlight,
        settings.boldBodyText,
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

        buildAnnotatedString {
            if (activeStart > 0) {
                withStyle(SpanStyle(color = PrompterReadDimmed, fontWeight = bodyWeight)) {
                    append(script.substring(0, activeStart))
                }
            }
            if (activeEnd > activeStart) {
                withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold)) {
                    append(script.substring(activeStart, activeEnd))
                }
            }
            if (activeEnd < script.length) {
                withStyle(SpanStyle(color = PrompterUnreadWhite, fontWeight = bodyWeight)) {
                    append(script.substring(activeEnd))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PrompterOledBlack)
            .displayCutoutPadding()
            // Two-finger pinch-to-zoom font resize (only consumes when >= 2 fingers pressed,
            // so 1-finger vertical scroll, tap-to-jump, and double-tap are completely unaffected)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pinchBaseFontSp: Float? = null
                    var cumulativeZoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (abs(zoomChange - 1f) > 0.005f) {
                                if (pinchBaseFontSp == null) {
                                    pinchBaseFontSp = latestSettings.activeFontSizeSp
                                    cumulativeZoom = 1f
                                }
                                cumulativeZoom *= zoomChange
                                val nextSp = (pinchBaseFontSp!! * cumulativeZoom)
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalMargin)
            ) {
                Spacer(modifier = Modifier.height(topClearanceDp))
                Text(
                    text = styledScript,
                    fontSize = fontSize,
                    lineHeight = fontSize * settings.lineHeightMultiplier,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(script) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val layout = textLayout
                                    if (layout != null && script.isNotEmpty()) {
                                        manualScrolledWhilePaused = false
                                        val clickedOffset = layout.getOffsetForPosition(tapOffset)
                                        onTapCharOffset(clickedOffset)
                                    }
                                },
                                onDoubleTap = {
                                    controlsVisible = !controlsVisible
                                }
                            )
                        },
                    onTextLayout = { textLayout = it }
                )
                Spacer(modifier = Modifier.height(viewportHeightDp * 0.90f))
            }
        }

        // Left-edge focus marker pill smoothly tracking the active visual line
        val activeLineTopOnScreenPx = remember(
            textLayout,
            currentCharOffset,
            script,
            scrollState.value,
            topClearancePx
        ) {
            val layout = textLayout
            if (layout == null || script.isEmpty()) {
                topClearancePx
            } else {
                val idx = currentCharOffset.coerceIn(0, script.lastIndex)
                val lineIdx = layout.getLineForOffset(idx)
                topClearancePx + layout.getLineTop(lineIdx) - scrollState.value.toFloat()
            }
        }
        val markerMarginPx = with(density) { 5.dp.toPx() }
        val markerYOffsetPx = (activeLineTopOnScreenPx + with(density) { 10.dp.toPx() }).roundToInt()
        if (activeLineTopOnScreenPx >= -40f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(markerMarginPx.roundToInt(), markerYOffsetPx) }
                    .height(30.dp)
                    .width(4.5.dp)
                    .background(cs.primary, CircleShape)
            )
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
                            .clickable { controlsVisible = false }
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

        // Restore HUD minimal pill when hidden
        if (!controlsVisible) {
            Surface(
                color = cs.surfaceContainerHigh.copy(alpha = 0.65f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { controlsVisible = true }
            ) {
                Text(
                    text = "显示控制",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
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
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                                        .padding(horizontal = 9.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A-",
                                        color = cs.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
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
                                        .padding(horizontal = 9.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A+",
                                        color = cs.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        ToolbarIconAction(
                            icon = TeleprompterIcons.Rewind,
                            label = "回退",
                            tint = cs.onSurface,
                            onClick = {
                                manualScrolledWhilePaused = false
                                onRewindStep()
                            }
                        )

                        ToolbarIconAction(
                            icon = TeleprompterIcons.SkipPrevious,
                            label = "重头",
                            tint = cs.onSurface,
                            onClick = {
                                manualScrolledWhilePaused = false
                                onResetToStart()
                            }
                        )

                        ToolbarIconAction(
                            icon = TeleprompterIcons.RotateScreen,
                            label = if (settings.isLandscape) "竖屏" else "横屏",
                            tint = cs.onSecondaryContainer,
                            onClick = onToggleOrientation
                        )

                        ToolbarIconAction(
                            icon = TeleprompterIcons.Tune,
                            label = "设置",
                            tint = cs.primary,
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
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
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
                        maxLines = 1
                    )
                }
            }
        }
    }

    // ==================== MD3 MODAL BOTTOM SHEET (28dp TOP RADIUS + GROUPED CARDS) ====================
    if (showSettingsSheet) {
        val modeLabel = if (settings.isLandscape) "横屏" else "竖屏"
        val marginRange = if (settings.isLandscape) 16f..220f else 8f..96f

        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = cs.surfaceContainerLow,
            contentColor = cs.onSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = cs.outline)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sheet Title Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "提词与排版设置",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            text = "当前为「${modeLabel}」独立记忆参数",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
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

                // ---------- Group 1: Typography & Layout ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsGroupLabel("字体与排版")

                    // 1A. Font Size Slider
                    SettingsCard(shape = topCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${modeLabel}字号（支持双指捏合）", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                        Slider(
                            value = settings.activeFontSizeSp,
                            onValueChange = { onUpdateSettings(settings.withActiveFontSize(it)) },
                            valueRange = 32f..80f,
                            colors = SliderDefaults.colors(
                                thumbColor = cs.primary,
                                activeTrackColor = cs.primary,
                                inactiveTrackColor = cs.surfaceVariant
                            )
                        )
                    }

                    // 1B. Line Height Multiplier (4-segment pill)
                    val lineHeightOptions = listOf(1.2f to "1.2x", 1.4f to "1.4x", 1.6f to "1.6x", 1.8f to "1.8x")
                    val lhDesc = when {
                        settings.lineHeightMultiplier <= 1.25f -> "1.2x 紧凑"
                        settings.lineHeightMultiplier <= 1.45f -> "1.4x 标准"
                        settings.lineHeightMultiplier <= 1.65f -> "1.6x 宽松"
                        else -> "1.8x 特宽"
                    }
                    SettingsCard(shape = middleCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("行距倍数（防看串行）", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(lhDesc, color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = lineHeightOptions,
                            selectedPredicate = { abs(it.first - settings.lineHeightMultiplier) < 0.05f },
                            label = { it.second },
                            onSelect = { onUpdateSettings(settings.copy(lineHeightMultiplier = it.first)) }
                        )
                    }

                    // 1C. Body Font Weight (2-segment pill)
                    SettingsCard(shape = middleCardShape) {
                        Text("正文字重", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "远机位 / 强灯光拍摄推荐开启「加粗」",
                            color = cs.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = listOf(false to "常规体", true to "加粗（清晰）"),
                            selectedPredicate = { it.first == settings.boldBodyText },
                            label = { it.second },
                            onSelect = { onUpdateSettings(settings.copy(boldBodyText = it.first)) }
                        )
                    }

                    // 1D. Horizontal Margin Slider
                    SettingsCard(shape = middleCardShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${modeLabel}左右安全边距（防眼神飘）", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${settings.activeHorizontalMarginDp.toInt()} dp",
                                color = cs.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = settings.activeHorizontalMarginDp.coerceIn(marginRange.start, marginRange.endInclusive),
                            onValueChange = { onUpdateSettings(settings.withActiveMargin(it)) },
                            valueRange = marginRange,
                            colors = SliderDefaults.colors(
                                thumbColor = cs.primary,
                                activeTrackColor = cs.primary,
                                inactiveTrackColor = cs.surfaceVariant
                            )
                        )
                    }

                    // 1E. Lines Above Segmented Pill
                    SettingsCard(shape = bottomCardShape) {
                        Text("上方保留已读行数", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        SegmentedPillRow(
                            options = listOf(0 to "0 行", 1 to "1 行", 2 to "2 行"),
                            selectedPredicate = { it.first == settings.linesAbove },
                            label = { it.second },
                            onSelect = { onUpdateSettings(settings.copy(linesAbove = it.first)) }
                        )
                    }
                }

                // ---------- Group 2: MD3 Theme Palette ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsGroupLabel("MD3 主题强调色（台词区恒为 OLED 纯黑）")
                    SettingsCard(shape = singleCardShape) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemePalette.entries.forEach { palette ->
                                val selected = settings.themePalette == palette
                                Surface(
                                    onClick = { onUpdateSettings(settings.copy(themePalette = palette)) },
                                    color = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) cs.primary else cs.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(palette.swatchHex))
                                        )
                                        Text(
                                            text = palette.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) cs.onPrimaryContainer else cs.onSurface
                                        )
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
                        Surface(
                            onClick = { onSelectMode(mode) },
                            color = cs.surfaceContainerHigh,
                            shape = shape,
                            modifier = Modifier.fillMaxWidth()
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
                            Slider(
                                value = settings.fallbackCharsPerSec,
                                onValueChange = { onUpdateSettings(settings.copy(fallbackCharsPerSec = it)) },
                                valueRange = 2.0f..8.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = cs.primary,
                                    activeTrackColor = cs.primary,
                                    inactiveTrackColor = cs.surfaceVariant
                                )
                            )
                        }
                    }
                }

                // ---------- Group 4: Smart Switches ----------
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsGroupLabel("辅助与容错开关")

                    SettingsSwitchCard(
                        title = "整行高亮（推荐）",
                        subtitle = "高亮当前整行，避免识别延迟导致黄字慢半拍",
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
                        shape = bottomCardShape,
                        onCheckedChange = { onUpdateSettings(settings.copy(allowRollback = it)) }
                    )
                }

                // Shortcut tips + subtle footer attribution
                Text(
                    text = "提示：双指捏合可直接缩放字号；点击任意文字跳转，双击文本隐藏控制栏；音量+ 暂停/继续，音量- 回退一句。",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Text(
                    text = "提词器 v1.1 · by YukokuSuzuko",
                    color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 14.dp)
                )
            }
        }
    }
}

private val topCardShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
private val middleCardShape = RoundedCornerShape(6.dp)
private val bottomCardShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val singleCardShape = RoundedCornerShape(20.dp)

@Composable
private fun ToolbarIconAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
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
            maxLines = 1
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

@Composable
private fun <T> SegmentedPillRow(
    options: List<T>,
    selectedPredicate: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerLow)
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { item ->
            val selected = selectedPredicate(item)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp),
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
                            tint = cs.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = label(item),
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
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
    Surface(
        color = cs.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
