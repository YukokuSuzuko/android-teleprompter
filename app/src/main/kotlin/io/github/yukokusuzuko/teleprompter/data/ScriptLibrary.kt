/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal const val MAX_SCRIPT_TITLE_LENGTH = 40

data class ScriptDocument(
    val id: String,
    val title: String,
    val content: String,
    val backupContent: String? = null,
    val pinned: Boolean = false,
    val updatedAtMs: Long
) {
    val displayTitle: String
        get() {
            val trimmedTitle = title.trim()
            if (trimmedTitle.isNotEmpty()) return trimmedTitle
            val firstLine = content.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return "未命名稿件"
            return if (firstLine.length > 18) "${firstLine.take(18)}…" else firstLine
        }

    val previewSnippet: String
        get() {
            val collapsed = content.trim().replace(Regex("\\s+"), " ")
            if (collapsed.isEmpty()) return "空白稿件"
            return if (collapsed.length > 52) "${collapsed.take(52)}…" else collapsed
        }

    val effectiveCharCount: Int
        get() = content.count { !it.isWhitespace() }
}

data class ScriptLibraryState(
    val documents: List<ScriptDocument>,
    val activeDocumentId: String
) {
    init {
        require(documents.isNotEmpty()) { "ScriptLibraryState must contain at least one document" }
    }

    val activeDocument: ScriptDocument
        get() = documents.firstOrNull { it.id == activeDocumentId } ?: sortedDocuments().first()

    fun sortedDocuments(): List<ScriptDocument> =
        documents.sortedWith(
            compareByDescending<ScriptDocument> { it.pinned }
                .thenByDescending { it.updatedAtMs }
                .thenBy { it.id }
        )

    fun filteredDocuments(query: String): List<ScriptDocument> {
        val q = query.trim()
        val sorted = sortedDocuments()
        if (q.isEmpty()) return sorted
        return sorted.filter { doc ->
            doc.displayTitle.contains(q, ignoreCase = true) ||
                doc.title.contains(q, ignoreCase = true) ||
                doc.content.contains(q, ignoreCase = true)
        }
    }

    fun selectDocument(id: String): ScriptLibraryState =
        if (documents.any { it.id == id } && activeDocumentId != id) {
            copy(activeDocumentId = id)
        } else {
            this
        }

    fun createDocument(
        title: String = "",
        content: String = "",
        nowMs: Long,
        id: String = UUID.randomUUID().toString()
    ): ScriptLibraryState {
        val normalizedTitle = title.trim().take(MAX_SCRIPT_TITLE_LENGTH)
        val doc = ScriptDocument(
            id = id,
            title = normalizedTitle,
            content = content,
            backupContent = null,
            pinned = false,
            updatedAtMs = nowMs
        )
        return ScriptLibraryState(documents = documents + doc, activeDocumentId = id)
    }

    fun updateActiveContent(newContent: String, nowMs: Long): ScriptLibraryState {
        val active = activeDocument
        if (active.content == newContent) return this
        val updated = active.copy(content = newContent, updatedAtMs = nowMs)
        return copy(documents = documents.map { if (it.id == active.id) updated else it })
    }

    internal fun transformActiveDraft(
        nowMs: Long,
        action: (StoredDraft) -> StoredDraft
    ): ScriptLibraryState {
        val active = activeDocument
        val current = StoredDraft(active.content, active.backupContent)
        val next = action(current)
        if (next == current) return this
        val updated = active.copy(
            content = next.text,
            backupContent = next.backup,
            updatedAtMs = nowMs
        )
        return copy(documents = documents.map { if (it.id == active.id) updated else it })
    }

    fun renameDocument(id: String, newTitle: String, nowMs: Long): ScriptLibraryState {
        val normalized = newTitle.trim().take(MAX_SCRIPT_TITLE_LENGTH)
        var changed = false
        val nextDocs = documents.map { doc ->
            if (doc.id == id && doc.title != normalized) {
                changed = true
                doc.copy(title = normalized, updatedAtMs = nowMs)
            } else {
                doc
            }
        }
        return if (changed) copy(documents = nextDocs) else this
    }

    fun togglePin(id: String): ScriptLibraryState {
        var changed = false
        val nextDocs = documents.map { doc ->
            if (doc.id == id) {
                changed = true
                doc.copy(pinned = !doc.pinned)
            } else {
                doc
            }
        }
        return if (changed) copy(documents = nextDocs) else this
    }

    fun deleteDocument(
        id: String,
        nowMs: Long,
        replacementId: String = UUID.randomUUID().toString()
    ): ScriptLibraryState {
        val target = documents.firstOrNull { it.id == id } ?: return this
        val remaining = documents.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val replacement = ScriptDocument(
                id = replacementId,
                title = "",
                content = "",
                backupContent = target.content.takeIf { it.isNotBlank() } ?: target.backupContent,
                pinned = false,
                updatedAtMs = nowMs
            )
            return ScriptLibraryState(documents = listOf(replacement), activeDocumentId = replacementId)
        }
        val nextActiveId = if (activeDocumentId == id) {
            ScriptLibraryState(remaining, remaining.first().id).sortedDocuments().first().id
        } else {
            activeDocumentId
        }
        return ScriptLibraryState(documents = remaining, activeDocumentId = nextActiveId)
    }
}

/**
 * Pure-Kotlin JSON codec so JVM unit tests run without Android framework org.json stubs.
 */
object ScriptLibraryCodec {

    fun encode(state: ScriptLibraryState): String {
        val sb = StringBuilder(512)
        sb.append("{\"version\":1,\"activeId\":")
        appendJsonString(sb, state.activeDocumentId)
        sb.append(",\"documents\":[")
        state.documents.forEachIndexed { index, doc ->
            if (index > 0) sb.append(',')
            sb.append("{\"id\":")
            appendJsonString(sb, doc.id)
            sb.append(",\"title\":")
            appendJsonString(sb, doc.title)
            sb.append(",\"content\":")
            appendJsonString(sb, doc.content)
            sb.append(",\"backupContent\":")
            val backup = doc.backupContent
            if (backup == null) sb.append("null") else appendJsonString(sb, backup)
            sb.append(",\"pinned\":").append(doc.pinned)
            sb.append(",\"updatedAtMs\":").append(doc.updatedAtMs)
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decode(json: String): ScriptLibraryState? {
        return try {
            val parser = JsonParser(json)
            val root = parser.parseRootObject() ?: return null
            val docsRaw = root["documents"] as? List<*> ?: return null
            val docs = ArrayList<ScriptDocument>(docsRaw.size)
            val seenIds = HashSet<String>(docsRaw.size)
            for (item in docsRaw) {
                val map = item as? Map<*, *> ?: return null
                val id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
                if (!seenIds.add(id)) continue
                val title = map["title"] as? String ?: ""
                val content = map["content"] as? String ?: ""
                val backupContent = map["backupContent"] as? String
                val pinned = map["pinned"] as? Boolean ?: false
                val updatedAtMs = (map["updatedAtMs"] as? Number)?.toLong() ?: 0L
                docs.add(
                    ScriptDocument(
                        id = id,
                        title = title,
                        content = content,
                        backupContent = backupContent,
                        pinned = pinned,
                        updatedAtMs = updatedAtMs
                    )
                )
            }
            if (docs.isEmpty()) return null
            val rawActiveId = root["activeId"] as? String
            val normalizedActiveId = if (rawActiveId != null && docs.any { it.id == rawActiveId }) {
                rawActiveId
            } else {
                ScriptLibraryState(docs, docs.first().id).sortedDocuments().first().id
            }
            ScriptLibraryState(documents = docs, activeDocumentId = normalizedActiveId)
        } catch (_: Throwable) {
            null
        }
    }

    private fun appendJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u")
                        sb.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    private class JsonParser(private val src: String) {
        private var pos = 0

        fun parseRootObject(): Map<String, Any?>? {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '{') return null
            val result = parseObject()
            skipWhitespace()
            if (pos != src.length) return null
            return result
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) error("Unexpected end of JSON")
            return when (val ch = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected char '$ch' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when {
                    peek(',') -> pos++
                    peek('}') -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek(']')) {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when {
                    peek(',') -> pos++
                    peek(']') -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) error("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) error("Invalid unicode escape")
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("Invalid escape '\\$esc'")
                        }
                    }
                    else -> {
                        if (ch.code < 0x20) error("Unescaped control char")
                        sb.append(ch)
                    }
                }
            }
            error("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            if (pos >= src.length || src[pos] !in '0'..'9') error("Invalid number at $start")
            while (pos < src.length && src[pos] in '0'..'9') pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                if (pos >= src.length || src[pos] !in '0'..'9') error("Invalid fraction at $start")
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                if (pos >= src.length || src[pos] !in '0'..'9') error("Invalid exponent at $start")
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            return if (isFloat) token.toDouble() else token.toLong()
        }

        private fun <T> parseLiteral(literal: String, value: T): T {
            if (!src.startsWith(literal, pos)) error("Expected $literal at $pos")
            pos += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < src.length) {
                when (src[pos]) {
                    ' ', '\n', '\r', '\t' -> pos++
                    else -> return
                }
            }
        }

        private fun peek(expected: Char): Boolean = pos < src.length && src[pos] == expected

        private fun expect(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                error("Expected '$expected' at $pos")
            }
            pos++
        }
    }
}

class ScriptLibraryRepository(private val libraryFile: File) {

    fun loadOrMigrate(
        legacyScript: String,
        legacyBackup: String?,
        defaultSampleScript: String,
        nowMs: Long
    ): ScriptLibraryState {
        if (libraryFile.isFile) {
            val decoded = runCatching {
                ScriptLibraryCodec.decode(libraryFile.readText(Charsets.UTF_8))
            }.getOrNull()
            if (decoded != null) return decoded
        }

        val initialDoc = ScriptDocument(
            id = UUID.randomUUID().toString(),
            title = if (legacyScript == defaultSampleScript) "示例口播台词" else "",
            content = legacyScript,
            backupContent = legacyBackup,
            pinned = false,
            updatedAtMs = nowMs
        )
        val migrated = ScriptLibraryState(
            documents = listOf(initialDoc),
            activeDocumentId = initialDoc.id
        )
        save(migrated)
        return migrated
    }

    @Synchronized
    fun save(state: ScriptLibraryState) {
        val parent = libraryFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tmpFile = File(parent ?: File("."), "${libraryFile.name}.tmp")
        val json = ScriptLibraryCodec.encode(state)
        tmpFile.writeText(json, Charsets.UTF_8)
        try {
            Files.move(tmpFile.toPath(), libraryFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Throwable) {
            if (libraryFile.exists()) {
                libraryFile.delete()
            }
            if (!tmpFile.renameTo(libraryFile)) {
                libraryFile.writeText(json, Charsets.UTF_8)
                tmpFile.delete()
            }
        }
    }
}
