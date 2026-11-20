/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File

data class CustomFontImportResult(
    val savedFileName: String,
    val displayName: String
)

data class ResolvedScriptFonts(
    val regular: FontFamily,
    val bold: FontFamily
)

/**
 * Safely copies and validates a user-selected .ttf / .otf / .ttc font file into app-private storage.
 * Uses a timestamped filename so Skia/Typeface caches never return a stale font after replacement.
 */
fun importCustomFontFile(context: Context, uri: Uri): CustomFontImportResult? {
    val rawName = queryDisplayName(context, uri) ?: "自定义字体.ttf"
    val lower = rawName.lowercase()
    val ext = when {
        lower.endsWith(".otf") -> "otf"
        lower.endsWith(".ttc") -> "ttc"
        else -> "ttf"
    }
    val tmpFile = File(context.filesDir, "custom_font_tmp.$ext")
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (bytes.size < 12 || !hasValidFontMagic(bytes)) {
            return null
        }
        tmpFile.writeBytes(bytes)
        // Validate that Android's font engine can actually parse this file
        Typeface.createFromFile(tmpFile)

        // Remove older custom font files
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("custom_font_") && file != tmpFile) {
                file.delete()
            }
        }
        val finalName = "custom_font_${System.currentTimeMillis()}.$ext"
        val finalFile = File(context.filesDir, finalName)
        if (!tmpFile.renameTo(finalFile)) {
            finalFile.writeBytes(bytes)
            tmpFile.delete()
        }
        val cleanDisplayName = rawName.substringBeforeLast('.').ifBlank { rawName }
        CustomFontImportResult(savedFileName = finalName, displayName = cleanDisplayName)
    } catch (_: Throwable) {
        tmpFile.delete()
        null
    }
}

fun clearCustomFontFiles(context: Context) {
    context.filesDir.listFiles()?.forEach { file ->
        if (file.name.startsWith("custom_font_")) {
            file.delete()
        }
    }
}

fun resolveScriptFonts(context: Context, customFontFileName: String?): ResolvedScriptFonts {
    if (!customFontFileName.isNullOrBlank()) {
        val file = File(context.filesDir, customFontFileName)
        if (file.exists() && file.length() > 12L) {
            val loaded = runCatching {
                val base = Typeface.createFromFile(file)
                val bold = Typeface.create(base, Typeface.BOLD)
                ResolvedScriptFonts(
                    regular = FontFamily(base),
                    bold = FontFamily(bold)
                )
            }.getOrNull()
            if (loaded != null) return loaded
        }
    }
    return ResolvedScriptFonts(
        regular = FontFamily.SansSerif,
        bold = FontFamily.SansSerif
    )
}

private fun hasValidFontMagic(bytes: ByteArray): Boolean {
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    // TrueType (00 01 00 00)
    if (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) return true
    // OpenType CFF ("OTTO")
    if (b0 == 'O'.code && b1 == 'T'.code && b2 == 'T'.code && b3 == 'O'.code) return true
    // Apple TrueType ("true")
    if (b0 == 't'.code && b1 == 'r'.code && b2 == 'u'.code && b3 == 'e'.code) return true
    // TrueType Collection ("ttcf")
    if (b0 == 't'.code && b1 == 't'.code && b2 == 'c'.code && b3 == 'f'.code) return true
    return false
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
    }.getOrNull()
}
