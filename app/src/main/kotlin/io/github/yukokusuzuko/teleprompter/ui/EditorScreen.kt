/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yukokusuzuko.teleprompter.ui.theme.PrompterDangerRed
import io.github.yukokusuzuko.teleprompter.data.readBoundedTxt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    script: String,
    onScriptChange: (String) -> Unit,
    onPasteScript: (String) -> Unit,
    onClearDraft: () -> Unit,
    hasDraftBackup: Boolean,
    onRestoreDraft: () -> Unit,
    onLoadSample: () -> Unit,
    onOpenTxtPicker: () -> Unit,
    onStartPrompter: () -> Unit,
    onShowMessage: (String) -> Unit,
    statusMessage: String?,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val effectiveChars = script.count { !it.isWhitespace() }
    val estimatedMinutes = effectiveChars / 220f
    val confirmClear = remember(script) { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
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

        Spacer(modifier = Modifier.height(14.dp))

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
                    if (clip.isNullOrEmpty()) {
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
                Text("清空", color = PrompterDangerRed, fontSize = 13.sp)
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
