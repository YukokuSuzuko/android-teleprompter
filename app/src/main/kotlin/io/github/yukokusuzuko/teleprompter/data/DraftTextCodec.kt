package io.github.yukokusuzuko.teleprompter.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal data class StoredDraft(val text: String, val backup: String?) {
    fun replaceWith(value: String): StoredDraft =
        if (value.isBlank()) this else replaceSavingBackup(value)

    fun clear(): StoredDraft = replaceSavingBackup("")

    fun restore(): StoredDraft {
        val saved = backup ?: return this
        return StoredDraft(saved, text.takeIf { it.isNotBlank() && it != saved })
    }

    private fun replaceSavingBackup(value: String): StoredDraft =
        if (text == value) this else StoredDraft(value, text.takeIf { it.isNotBlank() } ?: backup)
}

internal const val MAX_TXT_BYTES = 1024 * 1024

internal fun readBoundedTxt(input: InputStream): String? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val remaining = MAX_TXT_BYTES - output.size()
        val count = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
        if (count == -1) break
        if (count <= 0 || count > remaining) return null
        output.write(buffer, 0, count)
    }

    val bytes = output.toByteArray()
    val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    val start = if (hasBom) 3 else 0
    fun decode(charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, bytes.size - start))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
    val decoded = decode(Charsets.UTF_8)
        ?: if (hasBom) null else decode(Charset.forName("GB18030"))
    return decoded?.takeUnless { it.isBlank() }
}
