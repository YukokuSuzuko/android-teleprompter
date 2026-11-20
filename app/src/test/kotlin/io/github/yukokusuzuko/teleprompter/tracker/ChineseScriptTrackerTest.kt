package io.github.yukokusuzuko.teleprompter.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseScriptTrackerTest {

    @Test
    fun exactChineseReading_advancesCursorSmoothly() {
        val script = "大家好，欢迎来到今天的拍摄现场。今天我们不用死记硬背长篇台词。"
        val tracker = ChineseScriptTracker(script)

        val o1 = tracker.updateFromSpeech("大家好欢迎来到")
        assertTrue("Should advance past 大家好", o1 >= script.indexOf("来"))

        val o2 = tracker.updateFromSpeech("欢迎来到今天的拍摄现场")
        assertTrue("Should advance to 拍摄现场", o2 >= script.indexOf("现"))
    }

    @Test
    fun homophonesAndFuzzyPinyin_stillAlignAccurately() {
        val script = "现在测试一下十全十美的视觉效果，完全不用担心平翘舌。"
        val tracker = ChineseScriptTracker(script)

        val offset = tracker.updateFromSpeech("先在测是以下四全四美的是觉效果")
        assertTrue("Should align near 效果 despite fuzzy errors, got $offset", offset >= script.indexOf("效"))
    }

    @Test
    fun paraphrasedOrSkippedWords_doNotGetStuck() {
        val script = "今天我们直接用手机搭配专业相机来进行口播录制。"
        val tracker = ChineseScriptTracker(script)

        tracker.updateFromSpeech("今天我们直接")
        val o1 = tracker.updateFromSpeech("今天我们直接拿个手机配相机来进行口播")
        assertTrue(
            "Should stay unstuck when words differ or are skipped, got $o1",
            o1 >= script.indexOf("口")
        )
    }

    @Test
    fun finishingParagraph_automaticallyAdvancesToNextParagraph() {
        val script = """
            第一段话只有这么长，很快就会念完了。
            第二段从这里正式开始新的话题。
        """.trimIndent()
        val tracker = ChineseScriptTracker(script)

        val o1 = tracker.updateFromSpeech("第一段话只有这么长很快就会念完了")
        assertEquals(
            "Finishing the final word of paragraph 1 should jump onto the start of paragraph 2",
            script.indexOf("第", startIndex = script.indexOf('\n')),
            o1
        )
    }

    @Test
    fun skippingEndOfParagraph_recoversOntoNextParagraphAfterConfirmation() {
        val script = """
            今天天气真不错，我们准备出去拍一组外景人像照片，顺便测一下逆光表现。
            接下来看看这颗镜头在室内的暗光对焦速度到底有多快。
        """.trimIndent()
        val tracker = ChineseScriptTracker(script)

        tracker.updateFromSpeech("准备出去拍一组外景人像")
        // Two streaming ASR updates while reading the new paragraph confirm the cross-paragraph jump
        tracker.updateFromSpeech("接下来看看这颗镜头在室内")
        val o2 = tracker.updateFromSpeech("接下来看看这颗镜头在室内的暗光")
        assertTrue(
            "Should jump across paragraph boundary onto paragraph 2 after confirmation, got $o2",
            o2 >= script.indexOf("暗")
        )
    }

    @Test
    fun repeatedWordsAcrossParagraphs_neverJumpToLaterParagraph() {
        val script = "在贵阳做原木定制二十来年了，熟悉我的老客户都知道，我对品牌和工厂一向挑剔。" +
            "很多人问为什么这么挑剔，其实我们服务过上千家客户，后面还有更多品牌和工厂的案例分享。"
        val tracker = ChineseScriptTracker(script)

        tracker.updateAdaptive("在贵阳做原木定制二十", deltaSpokenTokens = 10)
        tracker.updateAdaptive("来年了熟悉我的老客户都知道", deltaSpokenTokens = 12)
        // Speaker says the repeated phrase "品牌和工厂" in sentence 1; must stay in sentence 1!
        val afterRepeated = tracker.updateAdaptive("我对品牌和工厂", deltaSpokenTokens = 6)
        assertTrue(
            "Repeated phrase '品牌和工厂' must NOT jump to later occurrence, offset=$afterRepeated",
            afterRepeated < script.indexOf("很多人问")
        )

        // Same verification in strict anchor mode after a pause leaving a 3-char snippet "和工厂"
        val strictTracker = ChineseScriptTracker(script)
        strictTracker.updateFromSpeech("熟悉我的老客户都知道我对品牌")
        val afterShortRepeat = strictTracker.updateFromSpeech("和工厂")
        assertTrue(
            "Short repeated phrase must NOT jump to later paragraph, offset=$afterShortRepeat",
            afterShortRepeat < script.indexOf("很多人问")
        )
    }

    @Test
    fun fillerWordsAndNoise_doNotJumpAcrossDuplicatePhrases() {
        val script = "第一段说的是非常推荐这款相机。中间这里有很多介绍外观和重量的话。最后一段总结还是非常推荐这款相机。"
        val tracker = ChineseScriptTracker(script)

        tracker.updateFromSpeech("第一段说的是非常推荐")
        val firstSectionOffset = tracker.currentRawOffset()
        assertTrue(firstSectionOffset < script.indexOf("中间这里"))

        tracker.updateFromSpeech("嗯那个稍微等一下啊")
        assertEquals("Cursor should stay locked during filler words", firstSectionOffset, tracker.currentRawOffset())

        val after = tracker.updateFromSpeech("非常推荐这款相机中间这里有")
        assertTrue("Should advance into section 2, not jump to end", after < script.indexOf("最后一段"))
    }

    @Test
    fun ngRollback_stepsBackWhenReReadingPreviousWords() {
        val script = "当你开口说话，文字会自动跟随你的语速平滑向上推进。"
        val tracker = ChineseScriptTracker(script)

        tracker.updateFromSpeech("当你开口说话文字会自动跟随")
        tracker.updateFromSpeech("文字会自动跟随你的语速平滑向上")
        val farOffset = tracker.currentRawOffset()
        assertTrue("Expected cursor near 向上, got $farOffset", farOffset >= script.indexOf("向"))

        val rolledBack = tracker.updateFromSpeech("文字会自动跟随", allowRollback = true)
        assertTrue("Cursor should roll back when re-reading earlier words ($rolledBack vs $farOffset)", rolledBack < farOffset)
    }

    @Test
    fun numbersInScript_matchChineseSpokenNumerals() {
        val script = "今年是2026年，价格只要100块。"
        val tracker = ChineseScriptTracker(script)

        val o1 = tracker.updateFromSpeech("今年是二零二六年")
        assertTrue("Should match 2026年", o1 >= script.indexOf("年"))

        val o2 = tracker.updateFromSpeech("二零二六年价格只要一百块")
        assertTrue("Should match 100块", o2 >= script.indexOf("块"))
    }

    @Test
    fun formatForPlayback_stripsBlankLinesAndLineBreaksCleanly() {
        val raw = "第一段结束了。\n\n第二段没有句号\n接着说第三行"
        val formatted = ChineseScriptTracker.formatForPlayback(raw, stripLineBreaks = true)
        assertEquals("第一段结束了。第二段没有句号 接着说第三行", formatted)
        assertEquals(raw, ChineseScriptTracker.formatForPlayback(raw, stripLineBreaks = false))
    }

    @Test
    fun adaptiveMode_advancesByRecognizedSpeedDuringTotalParaphraseAndSnapsBack() {
        val script = "今天我们带大家看看这台新相机的外观设计，然后一起测试它的暗光对焦和防抖表现。"
        val tracker = ChineseScriptTracker(script)

        tracker.updateAdaptive("今天我们带大家", deltaSpokenTokens = 7)
        val afterIntro = tracker.currentRawOffset()
        assertTrue(afterIntro >= script.indexOf("家"))

        val duringParaphrase = tracker.updateAdaptive(
            recentAsrText = "带大家一起聊一聊手里这个黑色机身",
            deltaSpokenTokens = 8
        )
        assertTrue(
            "Adaptive mode must advance during paraphrase, before=$afterIntro after=$duringParaphrase",
            duringParaphrase > afterIntro
        )

        val duringSilence = tracker.updateAdaptive("一起聊一聊手里这个黑色机身", deltaSpokenTokens = 0)
        assertEquals("Silence should not advance cursor", duringParaphrase, duringSilence)

        // Two confirming reads back on script calibrate smoothly onto "防抖表现"
        tracker.updateAdaptive("然后一起测试它的暗光对焦", deltaSpokenTokens = 7)
        val snapped = tracker.updateAdaptive(
            recentAsrText = "一起测试它的暗光对焦和防抖表现",
            deltaSpokenTokens = 5
        )
        assertTrue("Should calibrate onto 防抖表现, got $snapped", snapped >= script.indexOf("表"))
    }
}
