package com.teleprompter.dev.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    script: String,
    onScriptChange: (String) -> Unit,
    onLoadSample: () -> Unit,
    onOpenTxtPicker: () -> Unit,
    onStartPrompter: () -> Unit,
    onShowMessage: (String) -> Unit,
    statusMessage: String?,
) {
    val context = LocalContext.current
    val effectiveChars = script.count { !it.isWhitespace() }
    val estimatedMinutes = (effectiveChars / 220f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "提词器dev",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "纯中文离线 AI 实时跟读 · 直视大字版",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp
                )
            }
            Text(
                text = if (effectiveChars == 0) "0 字" else "$effectiveChars 字 · 约 ${"%.1f".format(estimatedMinutes)} 分钟",
                color = Color(0xFFFFE066),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick import toolbar
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
                        onScriptChange(clip)
                        onShowMessage("已从剪贴板导入 ${clip.length} 字")
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("粘贴剪贴板", fontSize = 13.sp)
            }

            FilledTonalButton(
                onClick = onOpenTxtPicker,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("导入 TXT", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    onLoadSample()
                    onShowMessage("已加载示例口播台词")
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("示例台词", color = Color(0xFFD1D1D6), fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = { onScriptChange("") },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("清空", color = Color(0xFFFF6961), fontSize = 13.sp)
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusMessage,
                color = Color(0xFF64D2FF),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Script editor box
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = Color(0xFF151518),
            shape = RoundedCornerShape(14.dp)
        ) {
            BasicTextField(
                value = script,
                onValueChange = onScriptChange,
                textStyle = TextStyle(
                    color = Color(0xFFF2F2F7),
                    fontSize = 18.sp,
                    lineHeight = 28.sp
                ),
                cursorBrush = SolidColor(Color(0xFFFFE066)),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                decorationBox = { innerTextField ->
                    if (script.isEmpty()) {
                        Text(
                            text = "在这里粘贴或输入台词，也可以点击上方「导入 TXT」打开本地文本文件…",
                            color = Color(0xFF636366),
                            fontSize = 16.sp,
                            lineHeight = 26.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Start teleprompter CTA
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
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFE066),
                contentColor = Color.Black
            )
        ) {
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
fun readTxtFileSmart(context: Context, uri: Uri): String? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        // Strip UTF-8 BOM if present
        val contentBytes = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }

        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(contentBytes)).toString()
        } catch (_: Exception) {
            Charset.forName("GB18030").decode(ByteBuffer.wrap(contentBytes)).toString()
        }
    } catch (_: Exception) {
        null
    }
}
