/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScriptLibraryTest {

    @Test
    fun legacyMigrationPreservesScriptAndBackupAndLabelsDefaultSample() {
        val tempDir = Files.createTempDirectory("script-lib-migrate").toFile()
        try {
            val sampleRepo = ScriptLibraryRepository(File(tempDir, "sample.json"))
            val sampleState = sampleRepo.loadOrMigrate(
                legacyScript = ScriptStore.DEFAULT_SAMPLE_SCRIPT,
                legacyBackup = "旧备份",
                defaultSampleScript = ScriptStore.DEFAULT_SAMPLE_SCRIPT,
                nowMs = 1000L
            )
            assertEquals(1, sampleState.documents.size)
            assertEquals("示例口播台词", sampleState.activeDocument.title)
            assertEquals("示例口播台词", sampleState.activeDocument.displayTitle)
            assertEquals(ScriptStore.DEFAULT_SAMPLE_SCRIPT, sampleState.activeDocument.content)
            assertEquals("旧备份", sampleState.activeDocument.backupContent)

            val customRepo = ScriptLibraryRepository(File(tempDir, "custom.json"))
            val customState = customRepo.loadOrMigrate(
                legacyScript = "第一行口播台词内容\n第二行",
                legacyBackup = null,
                defaultSampleScript = ScriptStore.DEFAULT_SAMPLE_SCRIPT,
                nowMs = 2000L
            )
            assertEquals("", customState.activeDocument.title)
            assertEquals("第一行口播台词内容", customState.activeDocument.displayTitle)
            assertNull(customState.activeDocument.backupContent)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun codecRoundTripHandlesCjkEscapesNullsAndRejectsInvalidJson() {
        val docs = listOf(
            ScriptDocument(
                id = "doc-1",
                title = "开场白 \"v1\" \\ 测试",
                content = "第一段\t缩进\n第二段\r\n结尾",
                backupContent = null,
                pinned = true,
                updatedAtMs = 1700000000123L
            ),
            ScriptDocument(
                id = "doc-2",
                title = "",
                content = "普通台词",
                backupContent = "更早的\"备份\"\\内容",
                pinned = false,
                updatedAtMs = 1699999999000L
            )
        )
        val original = ScriptLibraryState(documents = docs, activeDocumentId = "doc-2")
        val encoded = ScriptLibraryCodec.encode(original)
        val decoded = ScriptLibraryCodec.decode(encoded)
        assertEquals(original, decoded)

        assertNull(ScriptLibraryCodec.decode(""))
        assertNull(ScriptLibraryCodec.decode("not json"))
        assertNull(ScriptLibraryCodec.decode("{\"version\":1,\"activeId\":\"a\",\"documents\":[]}"))
        assertNull(ScriptLibraryCodec.decode("{\"version\":1,\"documents\":[{\"id\":\"\"}]}"))
    }

    @Test
    fun sortingPrioritizesPinnedDocumentsAndRestoresRecencyOnUnpin() {
        val docs = listOf(
            ScriptDocument(id = "older", title = "旧稿", content = "A", pinned = true, updatedAtMs = 100L),
            ScriptDocument(id = "newest", title = "最新", content = "B", pinned = false, updatedAtMs = 300L),
            ScriptDocument(id = "mid", title = "中等", content = "C", pinned = false, updatedAtMs = 200L)
        )
        val state = ScriptLibraryState(documents = docs, activeDocumentId = "newest")

        assertEquals(listOf("older", "newest", "mid"), state.sortedDocuments().map { it.id })

        val unpinned = state.togglePin("older")
        assertEquals(listOf("newest", "mid", "older"), unpinned.sortedDocuments().map { it.id })
        // Toggling pin does not alter updatedAtMs
        assertEquals(100L, unpinned.documents.first { it.id == "older" }.updatedAtMs)
    }

    @Test
    fun searchFiltersByExplicitTitleDerivedTitleAndBodyCaseInsensitively() {
        val docs = listOf(
            ScriptDocument(id = "1", title = "iPhone 评测", content = "欢迎收看本期视频", updatedAtMs = 100L),
            ScriptDocument(id = "2", title = "", content = "Camera Setup Guide\n第二行正文", updatedAtMs = 200L),
            ScriptDocument(id = "3", title = "口播结尾", content = "记得一键三连支持相机频道", updatedAtMs = 300L)
        )
        val state = ScriptLibraryState(documents = docs, activeDocumentId = "1")

        assertEquals(listOf("3", "2", "1"), state.filteredDocuments("   ").map { it.id })
        assertEquals(listOf("1"), state.filteredDocuments("IPHONE").map { it.id })
        assertEquals(listOf("2"), state.filteredDocuments("camera").map { it.id })
        assertEquals(listOf("3"), state.filteredDocuments("一键三连").map { it.id })
        assertTrue(state.filteredDocuments("不存在的词").isEmpty())
    }

    @Test
    fun crudUpdateDraftTransformAndDeleteBehaveSafely() {
        val initial = ScriptLibraryState(
            documents = listOf(
                ScriptDocument(id = "d1", title = "第一篇", content = "初始台词", backupContent = "旧草稿", updatedAtMs = 100L)
            ),
            activeDocumentId = "d1"
        )

        // Create and select
        val withSecond = initial.createDocument(title = "第二篇", content = "新内容", nowMs = 200L, id = "d2")
        assertEquals("d2", withSecond.activeDocumentId)
        assertEquals(2, withSecond.documents.size)

        // Typing updates active content without losing backup
        val backToFirst = withSecond.selectDocument("d1")
        val typedFirst = backToFirst.updateActiveContent("修改中", nowMs = 250L)
        assertEquals("修改中", typedFirst.activeDocument.content)
        assertEquals("旧草稿", typedFirst.activeDocument.backupContent)

        // Per-document transformActiveDraft (replace, clear, restore) without affecting d2
        val replaced = typedFirst.transformActiveDraft(300L) { it.replaceWith("替换后") }
        assertEquals("替换后", replaced.activeDocument.content)
        assertEquals("修改中", replaced.activeDocument.backupContent)
        assertEquals("新内容", replaced.documents.first { it.id == "d2" }.content)

        val cleared = replaced.transformActiveDraft(350L) { it.clear() }
        assertEquals("", cleared.activeDocument.content)
        assertEquals("替换后", cleared.activeDocument.backupContent)

        val restored = cleared.transformActiveDraft(400L) { it.restore() }
        assertEquals("替换后", restored.activeDocument.content)
        assertNull(restored.documents.first { it.id == "d2" }.backupContent)

        // Rename & delete active switches to next top document
        val renamed = restored.renameDocument("d1", "  重命名标题  ", nowMs = 450L)
        assertEquals("重命名标题", renamed.activeDocument.title)
        val afterDeleteActive = renamed.deleteDocument("d1", nowMs = 500L)
        assertEquals(listOf("d2"), afterDeleteActive.documents.map { it.id })
        assertEquals("d2", afterDeleteActive.activeDocumentId)

        // Deleting last remaining document yields blank doc keeping backup
        val afterDeleteLast = afterDeleteActive.deleteDocument("d2", nowMs = 600L, replacementId = "d3")
        assertEquals(listOf("d3"), afterDeleteLast.documents.map { it.id })
        assertEquals("", afterDeleteLast.activeDocument.content)
        assertEquals("新内容", afterDeleteLast.activeDocument.backupContent)
        assertFalse(afterDeleteLast.activeDocument.pinned)
    }

    @Test
    fun repositoryAtomicallySavesAndReloadsLibrary() {
        val tempDir = Files.createTempDirectory("script-lib-repo").toFile()
        try {
            val file = File(tempDir, "scripts_library.json")
            val repo = ScriptLibraryRepository(file)
            val state = ScriptLibraryState(
                documents = listOf(
                    ScriptDocument(id = "a", title = "标题A", content = "内容A", pinned = true, updatedAtMs = 500L),
                    ScriptDocument(id = "b", title = "标题B", content = "内容B", backupContent = "备份B", pinned = false, updatedAtMs = 400L)
                ),
                activeDocumentId = "b"
            )
            repo.save(state)
            assertTrue(file.isFile)
            assertFalse(File(tempDir, "scripts_library.json.tmp").exists())

            val reloaded = repo.loadOrMigrate(
                legacyScript = "ignored",
                legacyBackup = null,
                defaultSampleScript = "ignored",
                nowMs = 999L
            )
            assertNotNull(reloaded)
            assertEquals(state, reloaded)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
