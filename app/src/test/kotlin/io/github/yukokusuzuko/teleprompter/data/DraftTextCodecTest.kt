package io.github.yukokusuzuko.teleprompter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftTextCodecTest {
    @Test
    fun blankSameOrMalformedImportsDoNotReplaceDraft() {
        val original = StoredDraft("current", "older")
        assertEquals(original, original.replaceWith("  "))
        assertEquals(original, original.replaceWith("current"))
        assertNull(readBoundedTxt(byteArrayOf(0xFF.toByte()).inputStream()))
        assertNull(readBoundedTxt(" \n ".byteInputStream()))
        assertNull(readBoundedTxt(ByteArray(MAX_TXT_BYTES + 1) { 0x41 }.inputStream()))
    }

    @Test
    fun replacementAndRestorePreserveBothVersionsAcrossTyping() {
        val old = StoredDraft("old draft", null)
        val replacement = old.replaceWith("new draft").copy(text = "typed")
        assertEquals(StoredDraft("old draft", "typed"), replacement.restore())
        assertEquals(StoredDraft("", "old draft"), old.clear())
        assertEquals(StoredDraft("old draft", null), old.clear().restore())
    }

    @Test
    fun utf8ImportPreservesLineBreaksAndWhitespace() {
        val text = "  line\n\n"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(text, readBoundedTxt((bom + text.toByteArray()).inputStream()))
    }
}
