package io.github.yukokusuzuko.teleprompter.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseScriptTrackerTest {

    @Test
    fun uniformStepAdvancesAcrossSharedDigitOffsetsAndStopsAtEnd() {
        val tracker = ChineseScriptTracker("100 A")
        val firstOffset = tracker.currentRawOffset()
        tracker.stepForward()
        assertEquals(firstOffset, tracker.currentRawOffset())
        assertEquals(1, tracker.tokenCursor)
        repeat(tracker.tokens.size) { tracker.stepForward() }
        assertEquals(tracker.tokens.lastIndex, tracker.tokenCursor)
        assertEquals(4, tracker.currentRawOffset())
    }

    @Test
    fun restoringTokenIndexKeepsPositionWithinNumeral() {
        val tracker = ChineseScriptTracker("100 A")
        tracker.jumpToTokenIndex(1)
        assertEquals(1, tracker.tokenCursor)
        assertEquals(0, tracker.currentRawOffset())
    }

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

    @Test
    fun adaptiveMode_immediateOneLineRollbackWithStaleAsrPrefix_rollsBack() {
        val script = "第一行先介绍一下背景情况。第二行开始讲解核心参数配置。第三行总结实际拍摄出来的画质表现。"
        val tracker = ChineseScriptTracker(script)

        tracker.updateAdaptive("第一行先介绍一下背景情况", deltaSpokenTokens = 12)
        tracker.updateAdaptive("介绍背景情况第二行开始讲解核心", deltaSpokenTokens = 10)
        val line3Offset = tracker.updateAdaptive("核心参数配置第三行总结实际", deltaSpokenTokens = 10)
        assertTrue("Should reach line 3 before retake, got $line3Offset", line3Offset >= script.indexOf("第三行"))

        // Immediate retake onto line 2 while StreamingAsrEngine combined buffer still retains stale tail from line 3
        tracker.updateAdaptive("总结实际第二", deltaSpokenTokens = 2)
        tracker.updateAdaptive("总结实际第二行开", deltaSpokenTokens = 2)
        tracker.updateAdaptive("总结实际第二行开始讲", deltaSpokenTokens = 2)
        val rolledBack = tracker.updateAdaptive("实际第二行开始讲解核心", deltaSpokenTokens = 2)

        assertTrue(
            "Adaptive mode must roll back onto line 2 despite deltaSpokenTokens and stale ASR prefix, got $rolledBack",
            rolledBack in script.indexOf("第二行") until script.indexOf("第三行")
        )
    }

    @Test
    fun adaptiveMode_multiLineRollbackAcrossParagraphs_rollsBackAfterConfirmation() {
        val script = """
            第一段今天先带大家看看室外自然光环境下的人像肤色表现如何。
            第二段再来对比不同焦段的虚化过渡效果和边缘锐度变化。
            第三段最后聊聊续航发热以及户外长时间录制的稳定性表现。
        """.trimIndent()
        val tracker = ChineseScriptTracker(script)

        tracker.updateAdaptive("第一段今天先带大家看看室外", deltaSpokenTokens = 13)
        tracker.updateAdaptive("然光环境下的人像肤色表现如何", deltaSpokenTokens = 14)
        tracker.updateAdaptive("第二段再来对比不同焦段的虚化过渡效果和边缘锐度变化", deltaSpokenTokens = 25)
        val deepOffset = tracker.updateAdaptive("第三段最后聊聊续航发热以及户外", deltaSpokenTokens = 15)
        assertTrue("Should advance into paragraph 3, got $deepOffset", deepOffset >= script.indexOf("第三段"))

        // Jump ~45 chars back to paragraph 1 with streaming confirmation updates
        tracker.updateAdaptive("以及户外看看室外自然光环境", deltaSpokenTokens = 4)
        val rolledBack = tracker.updateAdaptive("看看室外自然光环境下的人像", deltaSpokenTokens = 4)

        assertTrue(
            "Adaptive mode should jump back across paragraphs onto paragraph 1, got $rolledBack",
            rolledBack in script.indexOf("室外") until script.indexOf("第二段")
        )
    }

    @Test
    fun strictMode_multiLineRollbackWithStalePrefix_worksWithLooseFollowOnAndOff() {
        val script = """
            第一段今天先带大家看看室外自然光环境下的人像肤色表现如何。
            第二段再来对比不同焦段的虚化过渡效果和边缘锐度变化。
            第三段最后聊聊续航发热以及户外长时间录制的稳定性表现。
        """.trimIndent()

        for (loose in listOf(true, false)) {
            val tracker = ChineseScriptTracker(script)
            tracker.updateFromSpeech("第一段今天先带大家看看室外", looseFollow = loose)
            tracker.updateFromSpeech("然光环境下的人像肤色表现如何", looseFollow = loose)
            tracker.updateFromSpeech("第二段再来对比不同焦段的虚化过渡效果", looseFollow = loose)
            tracker.updateFromSpeech("边缘锐度变化第三段最后聊聊续航发热", looseFollow = loose)
            val deepOffset = tracker.updateFromSpeech("最后聊聊续航发热以及户外长时间", looseFollow = loose)
            assertTrue("loose=$loose should advance into paragraph 3", deepOffset >= script.indexOf("第三段"))

            tracker.updateFromSpeech("外长时间看看室外自然光环境", looseFollow = loose)
            val rolledBack = tracker.updateFromSpeech("看看室外自然光环境下的人像肤色", looseFollow = loose)
            assertTrue(
                "loose=$loose strict mode should roll back >25 chars to paragraph 1, got $rolledBack",
                rolledBack < script.indexOf("第二段")
            )
        }
    }

    @Test
    fun duplicateSentence_longContextDisambiguatesRollbackAndProtectsForwardReading() {
        val script = "开头部分我们先看室外风景评测，这个版本非常稳定，接下来对比白天清晰度、逆光宽容度和夜间噪点表现，最后来到总结阶段，这个版本非常稳定，适合日常直接开机录制。"
        val tracker = ChineseScriptTracker(script)
        val lateStart = script.indexOf("最后来到")

        tracker.updateFromSpeech("开头部分我们先看室外风景评测")
        tracker.updateFromSpeech("评测这个版本非常稳定接下来对比")
        tracker.updateFromSpeech("接下来对比白天清晰度逆光宽容度")
        tracker.updateFromSpeech("宽容度和夜间噪点表现最后来到总结")
        val forwardInSecondDuplicate = tracker.updateFromSpeech("总结阶段这个版本非常稳定适合")
        assertTrue(
            "Forward reading through second duplicate must stay at occurrence 2, got $forwardInSecondDuplicate",
            forwardInSecondDuplicate >= lateStart
        )

        // Now re-read occurrence 1 with its distinct preceding/following context (~12 tokens)
        tracker.updateFromSpeech("我们先看室外风景评测这个版本非常稳定")
        val rolledBackToFirst = tracker.updateFromSpeech("室外风景评测这个版本非常稳定接下")
        assertTrue(
            "12-token context should disambiguate and roll back to occurrence 1, got $rolledBackToFirst",
            rolledBackToFirst in script.indexOf("这个版本非常稳定")..script.indexOf("来对比")
        )
    }

    @Test
    fun rollbackDisabled_neverStepsBackwardInAdaptiveOrStrictMode() {
        val script = "第一句话介绍相机外观设计。第二句话测试自动对焦速度。第三句话总结夜间画质与续航能力。"

        val adaptiveTracker = ChineseScriptTracker(script)
        adaptiveTracker.updateAdaptive("第一句话介绍相机外观设计", deltaSpokenTokens = 12, allowRollback = false)
        adaptiveTracker.updateAdaptive("第二句话测试自动对焦速度", deltaSpokenTokens = 11, allowRollback = false)
        val adaptiveBefore = adaptiveTracker.updateAdaptive("第三句话总结夜间画质", deltaSpokenTokens = 9, allowRollback = false)
        val adaptiveAfter = adaptiveTracker.updateAdaptive("第一句话介绍相机外观设计", deltaSpokenTokens = 0, allowRollback = false)
        assertTrue("Adaptive with allowRollback=false must not step backward", adaptiveAfter >= adaptiveBefore)

        val strictTracker = ChineseScriptTracker(script)
        strictTracker.updateFromSpeech("第一句话介绍相机外观设计", allowRollback = false)
        strictTracker.updateFromSpeech("第二句话测试自动对焦速度", allowRollback = false)
        val strictBefore = strictTracker.updateFromSpeech("第三句话总结夜间画质", allowRollback = false)
        strictTracker.updateFromSpeech("第一句话介绍相机外观设计", allowRollback = false)
        val strictAfter = strictTracker.updateFromSpeech("介绍相机外观设计第二句话", allowRollback = false)
        assertTrue("Strict with allowRollback=false must not step backward", strictAfter >= strictBefore)
    }

    @Test
    fun farJump_lowConfidenceNearbyTextDoesNotShadowValidFarRollback() {
        val script = "清晨阳光洒在湖面上波光粼粼。" +
            "岸边微风轻轻吹过树梢，远处传来几声清脆鸟鸣，行人慢慢走在石板小路上。" +
            "啊波那面光啊，就那几个零散词。"
        val tracker = ChineseScriptTracker(script)

        tracker.jumpToRawOffset(script.indexOf("就那几个"))
        val beforeOffset = tracker.currentRawOffset()
        assertTrue(beforeOffset >= script.indexOf("啊波那面"))

        // Nearby text shares scattered syllables ("波/面/光"), which must not shadow exact far rollback match
        tracker.updateFromSpeech("清晨阳光洒在湖面上")
        val jumpedBack = tracker.updateFromSpeech("光洒在湖面上波光粼粼")

        assertTrue(
            "Valid far rollback must not be shadowed by low-confidence nearby partial syllable match, got $jumpedBack",
            jumpedBack < script.indexOf("岸边微风")
        )
    }
}
