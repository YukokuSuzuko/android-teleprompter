package com.teleprompter.dev.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.teleprompter.dev.data.FollowMode
import com.teleprompter.dev.data.PrompterSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

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
    val isVoiceMode = settings.followMode != FollowMode.UNIFORM_SCROLL
    val isActivelyFollowing = (isVoiceMode && isListening) || (!isVoiceMode && isUniformPlaying)
    val liveFollowingState by rememberUpdatedState(isActivelyFollowing)
    val view = LocalView.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var manualScrolledWhilePaused by remember { mutableStateOf(false) }

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

    // Highlight either the whole active visual line (Suflyor-style: avoids looking behind voice)
    // or a narrow trailing span from currentCharOffset.
    val styledScript = remember(
        script,
        currentCharOffset,
        highlightEndOffset,
        textLayout,
        settings.lineHighlight
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

        buildAnnotatedString {
            if (activeStart > 0) {
                withStyle(SpanStyle(color = Color(0xFF3E3E44))) {
                    append(script.substring(0, activeStart))
                }
            }
            if (activeEnd > activeStart) {
                withStyle(SpanStyle(color = Color(0xFFFFE066), fontWeight = FontWeight.Bold)) {
                    append(script.substring(activeStart, activeEnd))
                }
            }
            if (activeEnd < script.length) {
                withStyle(SpanStyle(color = Color(0xFFF2F2F7))) {
                    append(script.substring(activeEnd))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .displayCutoutPadding()
    ) {
        val density = LocalDensity.current
        val viewportHeightDp = maxHeight
        val fontSize = settings.activeFontSizeSp.sp
        val horizontalMargin = settings.activeHorizontalMarginDp.dp

        // When resuming after dragging while paused (via button or Volume+ remote),
        // align the reading cursor to the line currently visible on screen instead of snapping back.
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

        // Minimal top clearance; when paused/visible clears top bar cleanly.
        val topClearanceDp = if (controlsVisible) 62.dp else 12.dp
        val topClearancePx = with(density) { topClearanceDp.toPx() }

        // Smooth continuous intra-line scroll target + configurable already-read lines above.
        LaunchedEffect(currentCharOffset, textLayout, script, settings.linesAbove) {
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

        // Smooth silky auto-scroll ONLY while actively playing; completely disabled when paused
        // so the user can freely drag and inspect any part of the script without snapping back.
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

        // Gentle edge fade only near HUD bars; keep full screen bright and usable in clean mode.
        val fadeStops = remember(controlsVisible) {
            if (controlsVisible) {
                arrayOf(
                    0.00f to Color.Transparent,
                    0.06f to Color.Black,
                    0.84f to Color.Black,
                    0.95f to Color.Transparent,
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
                    lineHeight = fontSize * 1.38f,
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

        // Left-edge focus marker tracks the actual visual position of the active line smoothly
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
                    .height(28.dp)
                    .width(4.dp)
                    .background(Color(0xFFFFE066), RoundedCornerShape(4.dp))
            )
        }

        // Top mini status pill
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Surface(
                color = Color(0xCC16161A),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onBackToEditor,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("改稿", color = Color(0xFFD1D1D6), fontSize = 13.sp, maxLines = 1)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val speedTag = if (settings.followMode == FollowMode.ADAPTIVE_SPEED && liveCharsPerSec > 0.5f) {
                            " · ${"%.1f".format(liveCharsPerSec)}字/秒"
                        } else {
                            ""
                        }
                        val pct = (progressFraction * 100).toInt()
                        Text(
                            text = when (settings.followMode) {
                                FollowMode.ADAPTIVE_SPEED ->
                                    if (isListening) "● 语速自适应$speedTag · $pct%" else "○ 已暂停 · $pct%"
                                FollowMode.ANCHOR_STRICT ->
                                    if (isListening) "● 精准锚点 · $pct%" else "○ 已暂停 · $pct%"
                                FollowMode.UNIFORM_SCROLL ->
                                    if (isUniformPlaying) "▶ 匀速滚动 · $pct%" else "⏸ 匀速暂停 · $pct%"
                            },
                            color = if ((isVoiceMode && isListening) || (!isVoiceMode && isUniformPlaying)) {
                                Color(0xFF30D158)
                            } else {
                                Color(0xFFFFD60A)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        val subtitle = when {
                            isVoiceMode && heardTailText.isNotBlank() -> "听到: $heardTailText"
                            else -> modelStatus
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(
                        onClick = { controlsVisible = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("隐藏", color = Color(0xFF8E8E93), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        if (!controlsVisible) {
            Surface(
                color = Color(0x661C1C1E),
                shape = RoundedCornerShape(20.dp),
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
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom quick control dock (compact padding so all 5 controls fit cleanly in narrow portrait)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Surface(
                color = Color(0xD916161A),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleFollowOrPlay,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFE066),
                            contentColor = Color.Black
                        )
                    ) {
                        val active = if (isVoiceMode) isListening else isUniformPlaying
                        Text(
                            text = if (active) "暂停" else "继续",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    val btnPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)

                    TextButton(
                        onClick = {
                            manualScrolledWhilePaused = false
                            onRewindStep()
                        },
                        contentPadding = btnPadding
                    ) {
                        Text("回退", color = Color.White, fontSize = 13.sp, maxLines = 1)
                    }

                    TextButton(
                        onClick = {
                            manualScrolledWhilePaused = false
                            onResetToStart()
                        },
                        contentPadding = btnPadding
                    ) {
                        Text("重头", color = Color.White, fontSize = 13.sp, maxLines = 1)
                    }

                    TextButton(onClick = onToggleOrientation, contentPadding = btnPadding) {
                        Text(
                            text = if (settings.isLandscape) "竖屏" else "横屏",
                            color = Color(0xFF64D2FF),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    TextButton(onClick = { showSettingsSheet = true }, contentPadding = btnPadding) {
                        Text("设置", color = Color(0xFFFFE066), fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        val modeLabel = if (settings.isLandscape) "横屏" else "竖屏"
        val marginRange = if (settings.isLandscape) 16f..220f else 8f..96f

        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF1C1C1E),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "提词设置（当前为${modeLabel}独立参数）",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text("跟读推进模式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FollowMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMode(mode) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.followMode == mode,
                                onClick = { onSelectMode(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFE066))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(mode.description, color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Column {
                    Text(
                        text = "${modeLabel}字号：${settings.activeFontSizeSp.toInt()} sp",
                        fontSize = 14.sp
                    )
                    Slider(
                        value = settings.activeFontSizeSp,
                        onValueChange = { onUpdateSettings(settings.withActiveFontSize(it)) },
                        valueRange = 32f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFE066),
                            activeTrackColor = Color(0xFFFFE066)
                        )
                    )
                }

                Column {
                    Text(
                        text = "${modeLabel}左右边距（防眼神飘）：${settings.activeHorizontalMarginDp.toInt()} dp",
                        fontSize = 14.sp
                    )
                    Slider(
                        value = settings.activeHorizontalMarginDp.coerceIn(marginRange.start, marginRange.endInclusive),
                        onValueChange = { onUpdateSettings(settings.withActiveMargin(it)) },
                        valueRange = marginRange,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFE066),
                            activeTrackColor = Color(0xFFFFE066)
                        )
                    )
                }

                Column {
                    Text(
                        text = "上方保留已读行数：${settings.linesAbove} 行",
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0, 1, 2).forEach { count ->
                            val selected = settings.linesAbove == count
                            Button(
                                onClick = { onUpdateSettings(settings.copy(linesAbove = count)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFFFFE066) else Color(0xFF2C2C2E),
                                    contentColor = if (selected) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("${count} 行", fontSize = 13.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("整行高亮（推荐）", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "高亮当前整行，避免识别延迟导致黄字慢半拍",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.lineHighlight,
                        onCheckedChange = { onUpdateSettings(settings.copy(lineHighlight = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("宽松模糊匹配（推荐）", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "容忍口误/跳词/加口癖，避免卡住不滚",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.looseFollow,
                        onCheckedChange = { onUpdateSettings(settings.copy(looseFollow = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("播放时去掉空行和换行（推荐）", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "段落间连续排版，消除换段空屏与卡顿",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.stripLineBreaks,
                        onCheckedChange = { onUpdateSettings(settings.copy(stripLineBreaks = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NG 重念自动回退", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "念错倒回去重读前一句时自动跟回",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.allowRollback,
                        onCheckedChange = { onUpdateSettings(settings.copy(allowRollback = it)) }
                    )
                }

                if (!isVoiceMode) {
                    Column {
                        Text(
                            text = "备用匀速语速：${"%.1f".format(settings.fallbackCharsPerSec)} 字/秒",
                            fontSize = 14.sp
                        )
                        Slider(
                            value = settings.fallbackCharsPerSec,
                            onValueChange = { onUpdateSettings(settings.copy(fallbackCharsPerSec = it)) },
                            valueRange = 2.0f..8.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFE066),
                                activeTrackColor = Color(0xFFFFE066)
                            )
                        )
                    }
                }

                Text(
                    text = "提示：横屏/竖屏会各自记忆字号与边距；点击任意文字跳转，双击文本隐藏控制栏；音量+ 暂停/继续，音量- 回退一句。",
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
