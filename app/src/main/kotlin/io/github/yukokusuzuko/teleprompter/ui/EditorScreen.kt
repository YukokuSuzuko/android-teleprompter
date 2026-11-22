/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yukokusuzuko.teleprompter.data.MAX_SCRIPT_TITLE_LENGTH
import io.github.yukokusuzuko.teleprompter.data.ScriptDocument
import io.github.yukokusuzuko.teleprompter.data.ScriptLibraryState
import io.github.yukokusuzuko.teleprompter.data.readBoundedTxt
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterDangerRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    script: String,
    documents: List<ScriptDocument>,
    activeDocumentId: String,
    activeDocumentTitle: String,
    activeDocumentPinned: Boolean,
    onScriptChange: (String) -> Unit,
    onPasteScript: (String) -> Unit,
    onClearDraft: () -> Unit,
    hasDraftBackup: Boolean,
    onRestoreDraft: () -> Unit,
    onLoadSample: () -> Unit,
    onOpenTxtPicker: () -> Unit,
    onOpenTxtPickerAsNew: () -> Unit,
    onSelectDocument: (String) -> Unit,
    onCreateDocument: () -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onTogglePinDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onStartPrompter: () -> Unit,
    onShowMessage: (String) -> Unit,
    statusMessage: String?,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val effectiveChars = script.count { !it.isWhitespace() }
    val estimatedMinutes = effectiveChars / 220f
    val confirmClear = remember(script) { mutableStateOf(false) }

    var showLibrarySheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingDoc by remember { mutableStateOf<ScriptDocument?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deletingDoc by remember { mutableStateOf<ScriptDocument?>(null) }

    BackHandler(enabled = showLibrarySheet) {
        showLibrarySheet = false
    }

    if (confirmClear.value) {
        AlertDialog(
            onDismissRequest = { confirmClear.value = false },
            title = { Text("清空当前台词？") },
            text = { Text("清空前会备份当前非空白台词，替换上一份备份。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear.value = false
                    onClearDraft()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear.value = false }) { Text("取消") }
            }
        )
    }

    val currentRenaming = renamingDoc
    if (currentRenaming != null) {
        AlertDialog(
            onDismissRequest = { renamingDoc = null },
            title = { Text("重命名稿件") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it.take(MAX_SCRIPT_TITLE_LENGTH) },
                    singleLine = true,
                    placeholder = { Text("留空则自动使用首句作为标题") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameDocument(currentRenaming.id, renameInput)
                    renamingDoc = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renamingDoc = null }) { Text("取消") }
            }
        )
    }

    val currentDeleting = deletingDoc
    if (currentDeleting != null) {
        AlertDialog(
            onDismissRequest = { deletingDoc = null },
            title = { Text("删除稿件？") },
            text = {
                val hint = if (documents.size <= 1) {
                    "「${currentDeleting.displayTitle}」是当前仅剩的一篇稿件，删除后将保留一份备份并切换为空白稿件。"
                } else {
                    "确定删除「${currentDeleting.displayTitle}」？删除后无法恢复。"
                }
                Text(hint)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDocument(currentDeleting.id)
                    deletingDoc = null
                }) { Text("删除", color = PrompterDangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { deletingDoc = null }) { Text("取消") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // MD3 Medium Header + Stats Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "提词器",
                        color = cs.onBackground,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "纯中文离线 AI 实时跟读 · 直视大字版",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Surface(
                    color = cs.secondaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = if (effectiveChars == 0) "0 字" else "$effectiveChars 字 · 约 ${"%.1f".format(estimatedMinutes)} 分钟",
                        color = cs.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MD3 Tonal Active Script Selector Bar (opens Multi-Script Library)
            Surface(
                onClick = { showLibrarySheet = true },
                color = cs.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = TeleprompterIcons.Library,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    if (activeDocumentPinned) {
                        Icon(
                            imageVector = TeleprompterIcons.Pin,
                            contentDescription = "已置顶",
                            tint = cs.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = activeDocumentTitle.ifBlank { "未命名稿件" },
                        color = cs.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = cs.surfaceContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "共 ${documents.size} 篇",
                            color = cs.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "稿件库 ▾",
                        color = cs.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        onClick = onCreateDocument,
                        color = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = TeleprompterIcons.Add,
                                contentDescription = "新建稿件",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MD3 Tonal Action Chips Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                        if (clip.isNullOrBlank()) {
                            onShowMessage("剪贴板里没有文字")
                        } else {
                            onPasteScript(clip)
                            onShowMessage("已从剪贴板导入 ${clip.length} 字")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("粘贴剪贴板", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                FilledTonalButton(
                    onClick = onOpenTxtPicker,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("导入 TXT", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = {
                        onLoadSample()
                        onShowMessage("已加载示例口播台词")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("示例台词", color = cs.onSurface, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { confirmClear.value = true },
                    enabled = script.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "清空",
                        color = if (script.isNotEmpty()) PrompterDangerRed else PrompterDangerRed.copy(alpha = 0.38f),
                        fontSize = 13.sp
                    )
                }

                if (hasDraftBackup) {
                    OutlinedButton(
                        onClick = onRestoreDraft,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("恢复备份", fontSize = 13.sp)
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusMessage,
                    color = cs.primary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MD3 SurfaceContainerHigh 20dp rounded editor card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = cs.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f))
            ) {
                BasicTextField(
                    value = script,
                    onValueChange = onScriptChange,
                    textStyle = TextStyle(
                        color = cs.onSurface,
                        fontSize = 18.sp,
                        lineHeight = 29.sp
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    decorationBox = { innerTextField ->
                        if (script.isEmpty()) {
                            Text(
                                text = "在这里粘贴或输入台词，也可以点击上方「导入 TXT」打开本地文本文件…",
                                color = cs.onSurfaceVariant,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MD3 Expressive Full-Width Pill Primary CTA (56dp)
            Button(
                onClick = {
                    if (script.isBlank()) {
                        onShowMessage("请先输入或导入一段台词")
                    } else {
                        onStartPrompter()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary
                )
            ) {
                Icon(
                    imageVector = TeleprompterIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "进入提词（AI 实时跟读）",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==================== IN-WINDOW MD3 SCRIPT LIBRARY SHEET ====================
        AnimatedVisibility(
            visible = showLibrarySheet,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showLibrarySheet = false }
            )
        }

        AnimatedVisibility(
            visible = showLibrarySheet,
            enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val filteredDocs = remember(documents, activeDocumentId, searchQuery) {
                if (documents.isEmpty()) {
                    emptyList()
                } else {
                    ScriptLibraryState(documents, activeDocumentId).filteredDocuments(searchQuery)
                }
            }

            Surface(
                color = cs.surfaceContainerLow,
                contentColor = cs.onSurface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "稿件库",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Text(
                                text = "共 ${documents.size} 篇 · 置顶优先",
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
                                    onOpenTxtPickerAsNew()
                                    showLibrarySheet = false
                                },
                                color = cs.secondaryContainer,
                                contentColor = cs.onSecondaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "导入新稿",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    onCreateDocument()
                                    showLibrarySheet = false
                                },
                                color = cs.primaryContainer,
                                contentColor = cs.onPrimaryContainer,
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = TeleprompterIcons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "新建",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Surface(
                                onClick = { showLibrarySheet = false },
                                color = cs.surfaceContainerHigh,
                                contentColor = cs.onSurfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = TeleprompterIcons.Close,
                                        contentDescription = "关闭稿件库",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search bar pill
                    Surface(
                        color = cs.surfaceContainerHighest,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = TeleprompterIcons.Search,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = cs.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(cs.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "搜索标题或正文内容…",
                                                color = cs.onSurfaceVariant,
                                                fontSize = 14.sp
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable { searchQuery = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = TeleprompterIcons.Close,
                                        contentDescription = "清空搜索",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredDocs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "未找到包含「$searchQuery」的稿件",
                                    color = cs.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                TextButton(onClick = { searchQuery = "" }) {
                                    Text("清空搜索", fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(items = filteredDocs, key = { it.id }) { doc ->
                                val isActive = doc.id == activeDocumentId
                                Surface(
                                    onClick = {
                                        onSelectDocument(doc.id)
                                        showLibrarySheet = false
                                    },
                                    color = cs.surfaceContainerHigh,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = if (isActive) cs.primary else cs.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (doc.pinned) {
                                                Icon(
                                                    imageVector = TeleprompterIcons.Pin,
                                                    contentDescription = "已置顶",
                                                    tint = cs.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Text(
                                                text = doc.displayTitle,
                                                color = cs.onSurface,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isActive) {
                                                Surface(
                                                    color = cs.primaryContainer,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = "当前",
                                                        color = cs.onPrimaryContainer,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = doc.previewSnippet,
                                            color = cs.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${doc.effectiveCharCount} 字 · ${formatRelativeTime(doc.updatedAtMs)}",
                                                color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                                                fontSize = 11.sp
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                LibraryItemActionIcon(
                                                    icon = TeleprompterIcons.Pin,
                                                    contentDescription = if (doc.pinned) "取消置顶" else "置顶稿件",
                                                    tint = if (doc.pinned) cs.primary else cs.onSurfaceVariant,
                                                    onClick = { onTogglePinDocument(doc.id) }
                                                )
                                                LibraryItemActionIcon(
                                                    icon = TeleprompterIcons.Edit,
                                                    contentDescription = "重命名稿件",
                                                    tint = cs.onSurfaceVariant,
                                                    onClick = {
                                                        renameInput = doc.title.ifBlank { doc.displayTitle }
                                                        renamingDoc = doc
                                                    }
                                                )
                                                LibraryItemActionIcon(
                                                    icon = TeleprompterIcons.Delete,
                                                    contentDescription = "删除稿件",
                                                    tint = PrompterDangerRed.copy(alpha = 0.85f),
                                                    onClick = { deletingDoc = doc }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItemActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}

private fun formatRelativeTime(updatedAtMs: Long): String {
    if (updatedAtMs <= 0L) return ""
    val diffMs = (System.currentTimeMillis() - updatedAtMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> "刚刚"
        diffMs < 3_600_000L -> "${diffMs / 60_000L} 分钟前"
        diffMs < 86_400_000L -> "${diffMs / 3_600_000L} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(updatedAtMs))
    }
}

/**
 * Reads a user-picked text file supporting UTF-8 (with optional BOM) and GB18030/GBK fallback.
 */
fun readTxtFileSmart(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { readBoundedTxt(it) }
} catch (_: java.io.IOException) {
    null
} catch (_: SecurityException) {
    null
}
