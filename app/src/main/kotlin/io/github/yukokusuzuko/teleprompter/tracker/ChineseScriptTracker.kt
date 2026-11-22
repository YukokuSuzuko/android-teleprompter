package io.github.yukokusuzuko.teleprompter.tracker

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val SCRIPT_FILLER_PARTICLES = setOf(
    "a", "o", "e", "en", "na", "ne", "ba", "ma", "ya", "le", "de", "jiu"
)

/**
 * Represents one spoken syllable/unit extracted from the script, mapped back to its
 * exact character index in the raw script string.
 */
data class ScriptToken(
    val rawOffset: Int,
    val rawEndOffset: Int,
    val pinyins: Set<String>
) {
    internal val pinyinArray: Array<String> = pinyins.toTypedArray()
    internal val isFiller: Boolean = pinyinArray.any { it in SCRIPT_FILLER_PARTICLES }
}

/**
 * Pure-Chinese streaming speech-to-script alignment engine.
 *
 * Key safeguards:
 * - Uses fuzzy pinyin normalization (平翘舌/前后鼻音/n-l), polyphone sets, and Chinese numeral
 *   expansion for spoken delivery tolerance.
 * - Bounds local anchor calibration around the current cursor with distance and backward biases
 *   so repeated words/phrases elsewhere cannot yank the prompter out of place.
 * - Supports symmetric multi-line jumps across [-64, +64] tokens using up to 12-token context
 *   disambiguation, high confidence, and 2 consecutive confirming updates.
 */
class ChineseScriptTracker(val rawScript: String) {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    val tokens: List<ScriptToken> = tokenize(rawScript)

    var tokenCursor: Int = 0
        private set

    private var adaptiveCursor: Float = 0f
    private var pendingJumpTarget: Int = -1

    fun reset() {
        tokenCursor = 0
        adaptiveCursor = 0f
        pendingJumpTarget = -1
    }

    fun jumpToRawOffset(rawOffset: Int): Int {
        if (tokens.isEmpty()) return 0
        val idx = tokens.indexOfFirst { it.rawEndOffset > rawOffset }
        return jumpToTokenIndex(if (idx >= 0) idx else tokens.lastIndex)
    }

    fun stepForward(): Int = jumpToTokenIndex(tokenCursor + 1)

    fun jumpToTokenIndex(index: Int): Int {
        tokenCursor = index.coerceIn(0, tokens.lastIndex.coerceAtLeast(0))
        adaptiveCursor = tokenCursor.toFloat()
        pendingJumpTarget = -1
        return currentRawOffset()
    }

    /** Steps backward by approximately [tokenSteps] spoken characters (for quick NG rewind). */
    fun stepBackward(tokenSteps: Int = 15): Int {
        if (tokens.isEmpty()) return 0
        tokenCursor = max(0, tokenCursor - tokenSteps)
        adaptiveCursor = tokenCursor.toFloat()
        pendingJumpTarget = -1
        return currentRawOffset()
    }

    fun currentRawOffset(): Int {
        if (tokens.isEmpty()) return 0
        return tokens[tokenCursor.coerceIn(0, tokens.lastIndex)].rawOffset
    }

    fun currentHighlightEndOffset(): Int {
        if (tokens.isEmpty()) return rawScript.length
        val endIdx = (tokenCursor + 6).coerceIn(0, tokens.lastIndex)
        return tokens[endIdx].rawEndOffset
    }

    fun progressFraction(): Float {
        if (tokens.size <= 1) return 0f
        return tokenCursor.toFloat() / tokens.lastIndex.toFloat()
    }

    /**
     * Anchor-driven follow mode: aligns the trailing syllables of [recentAsrText] against
     * a bounded window around [tokenCursor], guarding against false leaps across duplicate words.
     */
    fun updateFromSpeech(
        recentAsrText: String,
        allowRollback: Boolean = true,
        looseFollow: Boolean = true
    ): Int {
        if (tokens.isEmpty()) return 0

        val allSpoken = tokenize(recentAsrText)
        if (allSpoken.size < MIN_SPOKEN_TOKENS) return currentRawOffset()

        val previousCursor = tokenCursor
        val primaryTail = allSpoken.takeLast(TAIL_MAX_TOKENS)
        val backMargin = if (allowRollback) LOCAL_ROLLBACK_TOKENS else 2
        val localMinCursor = max(0, tokenCursor - backMargin)
        val localMaxCursor = min(tokens.lastIndex, tokenCursor + LOCAL_LOOKAHEAD_TOKENS)

        // 1. First look tightly in immediate local window [-backMargin, +18] to stay locked nearby
        val localMatch = alignWindow(
            spokenTokens = primaryTail,
            minAllowedEndCursor = localMinCursor,
            maxEndCursor = localMaxCursor,
            allowRollback = allowRollback,
            looseFollow = looseFollow
        )

        if (localMatch != null) {
            if (!tryLongContextOverride(allSpoken, localMinCursor, localMaxCursor, localMatch, allowRollback, looseFollow)) {
                pendingJumpTarget = -1
                tokenCursor = localMatch
            }
        } else {
            // 2. Short forward recovery strictly within next 12 chars (unstick 1-2 misread words)
            val shortMatch = if (looseFollow && allSpoken.size >= 5) {
                val forwardStart = min(tokens.lastIndex, tokenCursor + 1)
                val forwardEnd = min(tokens.lastIndex, tokenCursor + 12)
                if (forwardStart <= forwardEnd) {
                    alignWindow(
                        spokenTokens = allSpoken.takeLast(5),
                        minAllowedEndCursor = forwardStart,
                        maxEndCursor = forwardEnd,
                        allowRollback = false,
                        looseFollow = true,
                        overrideMinConfidence = 0.72f
                    )
                } else null
            } else null

            if (shortMatch != null) {
                pendingJumpTarget = -1
                tokenCursor = shortMatch
            } else if (allSpoken.size >= FAR_JUMP_MIN_TOKENS) {
                // 3. Far skip/rollback recovery across lines/paragraphs: high confidence + 2-update confirmation
                tryConfirmedFarJump(allSpoken, allowRollback, looseFollow)
            }
        }

        if (tokenCursor > previousCursor) {
            advancePastParagraphBreakIfAtEnd()
        }
        adaptiveCursor = tokenCursor.toFloat()
        return currentRawOffset()
    }

    /**
     * Speed-adaptive follow mode: advances smoothly by newly recognized token count ([deltaSpokenTokens])
     * while speaking so paraphrasing never stalls, calibrates conservatively within the immediate
     * neighborhood ([-10, +12] tokens), and supports confirmed far jumps across [-64, +64] tokens
     * while guarding against duplicate phrases.
     */
    fun updateAdaptive(
        recentAsrText: String,
        deltaSpokenTokens: Int,
        allowRollback: Boolean = true,
        looseFollow: Boolean = true
    ): Int {
        if (tokens.isEmpty()) return 0
        val previousCursor = tokenCursor

        // 1. Advance smoothly by recognized speaking throughput while speaking
        if (deltaSpokenTokens > 0) {
            adaptiveCursor = (adaptiveCursor + deltaSpokenTokens.toFloat())
                .coerceIn(0f, tokens.lastIndex.toFloat())
            tokenCursor = adaptiveCursor.toInt().coerceIn(0, tokens.lastIndex)
        }

        // 2. Conservative local anchor calibration (requires >= 5 spoken tokens, tight +/- range)
        val allSpoken = tokenize(recentAsrText)
        if (allSpoken.size >= ADAPTIVE_ANCHOR_MIN_TOKENS) {
            val backMargin = if (allowRollback) ADAPTIVE_LOCAL_ROLLBACK_TOKENS else 4
            val tail = allSpoken.takeLast(TAIL_MAX_TOKENS)
            val localMinCursor = max(0, tokenCursor - backMargin)
            val localMaxCursor = min(tokens.lastIndex, tokenCursor + 12)
            val localAnchor = alignWindow(
                spokenTokens = tail,
                minAllowedEndCursor = localMinCursor,
                maxEndCursor = localMaxCursor,
                allowRollback = allowRollback,
                looseFollow = looseFollow,
                overrideMinConfidence = if (looseFollow) 0.68f else 0.76f
            )

            if (localAnchor != null) {
                if (!tryLongContextOverride(allSpoken, localMinCursor, localMaxCursor, localAnchor, allowRollback, looseFollow)) {
                    pendingJumpTarget = -1
                    tokenCursor = localAnchor
                    adaptiveCursor = localAnchor.toFloat()
                }
            } else if (allSpoken.size >= FAR_JUMP_MIN_TOKENS) {
                tryConfirmedFarJump(allSpoken, allowRollback, looseFollow)
            }
        }

        if (tokenCursor > previousCursor) {
            advancePastParagraphBreakIfAtEnd()
            adaptiveCursor = tokenCursor.toFloat()
        }

        return currentRawOffset()
    }

    private fun tryLongContextOverride(
        allSpoken: List<ScriptToken>,
        localMinCursor: Int,
        localMaxCursor: Int,
        localCandidate: Int,
        allowRollback: Boolean,
        looseFollow: Boolean
    ): Boolean {
        if (allSpoken.size < FAR_JUMP_CONTEXT_TOKENS) return false
        val backLimit = if (allowRollback) FAR_ROLLBACK_TOKENS else 0
        val farMin = max(0, tokenCursor - backLimit)
        val farMax = min(tokens.lastIndex, tokenCursor + FAR_LOOKAHEAD_TOKENS)

        val lastSpoken = allSpoken[allSpoken.lastIndex].pinyinArray
        val prevSpoken = allSpoken[allSpoken.lastIndex - 1].pinyinArray
        var hasExternalTailMatch = false
        for (k in max(1, farMin)..farMax) {
            if (k in localMinCursor..localMaxCursor) continue
            if (phoneticSubstitutionCost(tokens[k].pinyinArray, lastSpoken, looseFollow) <= 0.45f &&
                phoneticSubstitutionCost(tokens[k - 1].pinyinArray, prevSpoken, looseFollow) <= 0.45f
            ) {
                hasExternalTailMatch = true
                break
            }
        }
        if (!hasExternalTailMatch) return false

        val longTail = allSpoken.takeLast(FAR_JUMP_CONTEXT_TOKENS)
        val localLongMatch = alignWindow(
            spokenTokens = longTail,
            minAllowedEndCursor = localMinCursor,
            maxEndCursor = localMaxCursor,
            allowRollback = allowRollback,
            looseFollow = looseFollow,
            overrideMinConfidence = 0.78f
        )
        if (localLongMatch != null) return false

        val farCandidate = alignWindow(
            spokenTokens = longTail,
            minAllowedEndCursor = farMin,
            maxEndCursor = farMax,
            allowRollback = allowRollback,
            looseFollow = looseFollow,
            overrideMinConfidence = 0.92f,
            distanceBiasPerToken = FAR_DISTANCE_BIAS_PER_TOKEN,
            backwardBias = BACKWARD_BIAS,
            filterBeforeSelect = true
        ) ?: return false

        if (abs(farCandidate - localCandidate) <= 12) return false

        val snapped = applyConfirmedFarCandidate(farCandidate)
        if (!snapped) {
            // Keep forward responsiveness active while waiting for the 2nd confirmation update.
            tokenCursor = localCandidate
            adaptiveCursor = localCandidate.toFloat()
        }
        return true
    }

    private fun tryConfirmedFarJump(
        allSpoken: List<ScriptToken>,
        allowRollback: Boolean,
        looseFollow: Boolean
    ) {
        val backLimit = if (allowRollback) FAR_ROLLBACK_TOKENS else 0
        val minCursor = max(0, tokenCursor - backLimit)
        val maxCursor = min(tokens.lastIndex, tokenCursor + FAR_LOOKAHEAD_TOKENS)

        val farCandidate =
            (if (allSpoken.size >= 10) {
                alignWindow(
                    spokenTokens = allSpoken.takeLast(FAR_JUMP_CONTEXT_TOKENS),
                    minAllowedEndCursor = minCursor,
                    maxEndCursor = maxCursor,
                    allowRollback = allowRollback,
                    looseFollow = looseFollow,
                    overrideMinConfidence = 0.80f,
                    distanceBiasPerToken = FAR_DISTANCE_BIAS_PER_TOKEN,
                    backwardBias = BACKWARD_BIAS,
                    filterBeforeSelect = true
                )
            } else null)
                ?: (if (allSpoken.size >= TAIL_MAX_TOKENS) {
                    alignWindow(
                        spokenTokens = allSpoken.takeLast(TAIL_MAX_TOKENS),
                        minAllowedEndCursor = minCursor,
                        maxEndCursor = maxCursor,
                        allowRollback = allowRollback,
                        looseFollow = looseFollow,
                        overrideMinConfidence = 0.82f,
                        distanceBiasPerToken = FAR_DISTANCE_BIAS_PER_TOKEN,
                        backwardBias = BACKWARD_BIAS,
                        filterBeforeSelect = true
                    )
                } else null)
                ?: (if (allSpoken.size >= FAR_JUMP_SHORT_TAIL_TOKENS) {
                    alignWindow(
                        spokenTokens = allSpoken.takeLast(FAR_JUMP_SHORT_TAIL_TOKENS),
                        minAllowedEndCursor = minCursor,
                        maxEndCursor = maxCursor,
                        allowRollback = allowRollback,
                        looseFollow = looseFollow,
                        overrideMinConfidence = 0.92f,
                        distanceBiasPerToken = FAR_DISTANCE_BIAS_PER_TOKEN,
                        backwardBias = BACKWARD_BIAS,
                        filterBeforeSelect = true
                    )
                } else null)
                ?: return

        applyConfirmedFarCandidate(farCandidate)
    }

    private fun applyConfirmedFarCandidate(farCandidate: Int): Boolean {
        val step = farCandidate - tokenCursor
        if (step >= 0) {
            if (step <= 12) {
                pendingJumpTarget = -1
                tokenCursor = farCandidate
                adaptiveCursor = farCandidate.toFloat()
                return true
            }

            if (pendingJumpTarget >= 0 && (farCandidate - pendingJumpTarget) in -2..6) {
                tokenCursor = farCandidate
                adaptiveCursor = farCandidate.toFloat()
                pendingJumpTarget = -1
                return true
            }

            pendingJumpTarget = farCandidate
            return false
        }

        // Backward far-jump: ignore tiny 1-4 char fluctuations and always require 2-update
        // confirmation with positive forward progress (in 1..6 tokens) before rolling back.
        if (step > -5) return false

        if (pendingJumpTarget >= 0 && (farCandidate - pendingJumpTarget) in 1..6) {
            tokenCursor = farCandidate
            adaptiveCursor = farCandidate.toFloat()
            pendingJumpTarget = -1
            return true
        }

        pendingJumpTarget = farCandidate
        return false
    }

    private fun advancePastParagraphBreakIfAtEnd() {
        while (tokenCursor < tokens.lastIndex) {
            val curEnd = tokens[tokenCursor].rawEndOffset
            val nextStart = tokens[tokenCursor + 1].rawOffset
            if (nextStart > curEnd && rawScript.substring(curEnd, nextStart).contains('\n')) {
                tokenCursor++
            } else {
                break
            }
        }
    }

    private fun alignWindow(
        spokenTokens: List<ScriptToken>,
        minAllowedEndCursor: Int,
        maxEndCursor: Int,
        allowRollback: Boolean,
        looseFollow: Boolean,
        overrideMinConfidence: Float? = null,
        distanceBiasPerToken: Float = LOCAL_DISTANCE_BIAS_PER_TOKEN,
        backwardBias: Float = 0f,
        filterBeforeSelect: Boolean = false
    ): Int? {
        val m = spokenTokens.size
        if (m < MIN_SPOKEN_TOKENS) return null

        val winStart = max(0, minAllowedEndCursor - (m - 1))
        val winEnd = maxEndCursor
        if (winEnd < winStart) return null

        val window = tokens.subList(winStart, winEnd + 1)
        val n = window.size

        val baseSkipSpokenCost = if (looseFollow) 0.70f else 1.0f
        val baseSkipScriptCost = if (looseFollow) 0.60f else 1.0f

        var prevRow = FloatArray(n + 1)
        var currRow = FloatArray(n + 1)
        for (i in 0 until m) {
            val spokenToken = spokenTokens[i]
            val spokenPinyins = spokenToken.pinyinArray
            val spokenSkipCost = if (looseFollow && spokenToken.isFiller) 0.35f else baseSkipSpokenCost
            currRow[0] = prevRow[0] + spokenSkipCost
            for (j in 1..n) {
                val targetToken = window[j - 1]
                val scriptSkipCost = if (looseFollow && targetToken.isFiller) 0.35f else baseSkipScriptCost
                val subCost = phoneticSubstitutionCost(spokenPinyins, targetToken.pinyinArray, looseFollow)
                currRow[j] = minOf(
                    prevRow[j - 1] + subCost,
                    prevRow[j] + spokenSkipCost,
                    currRow[j - 1] + scriptSkipCost
                )
            }
            val tmp = prevRow
            prevRow = currRow
            currRow = tmp
        }

        val minAllowedJ = (minAllowedEndCursor - winStart + 1).coerceIn(1, n)
        if (!filterBeforeSelect) {
            var bestJ = -1
            var bestRawCost = Float.MAX_VALUE
            var bestBiasedCost = Float.MAX_VALUE

            for (j in minAllowedJ..n) {
                val rawCost = prevRow[j]
                val candidateIdx = winStart + j - 1
                // Mild distance bias prevents duplicate words further away from beating the local match
                val distanceBias = abs(candidateIdx - tokenCursor) * distanceBiasPerToken
                val biasedCost = rawCost + distanceBias
                if (biasedCost < bestBiasedCost) {
                    bestBiasedCost = biasedCost
                    bestRawCost = rawCost
                    bestJ = j
                }
            }

            if (bestJ <= 0) return null

            val confidence = 1f - (bestRawCost / m.toFloat())
            val candidateCursor = (winStart + bestJ - 1).coerceIn(0, tokens.lastIndex)
            val step = candidateCursor - tokenCursor

            if (!allowRollback && step < 0) return null

            val threshold = overrideMinConfidence ?: when {
                step > 10 || step < -4 -> if (looseFollow) 0.74f else 0.82f
                m <= 4 -> 0.72f
                else -> if (looseFollow) 0.58f else 0.66f
            }

            return if (confidence >= threshold) candidateCursor else null
        }

        var bestCandidateCursor = -1
        var bestRawCost = Float.MAX_VALUE
        var bestBiasedCost = Float.MAX_VALUE

        for (j in minAllowedJ..n) {
            val candidateCursor = (winStart + j - 1).coerceIn(0, tokens.lastIndex)
            val step = candidateCursor - tokenCursor
            if (!allowRollback && step < 0) continue

            val rawCost = prevRow[j]
            val confidence = 1f - (rawCost / m.toFloat())
            val defaultThreshold = when {
                step > 10 || step < -4 -> if (looseFollow) 0.74f else 0.82f
                m <= 4 -> 0.72f
                else -> if (looseFollow) 0.58f else 0.66f
            }
            val baseThreshold = overrideMinConfidence ?: defaultThreshold
            val threshold = if (step < -4) {
                max(baseThreshold, if (looseFollow) 0.78f else 0.84f)
            } else {
                baseThreshold
            }
            if (confidence < threshold) continue

            // Mild distance bias + backward bias prevents distant/earlier duplicates from beating nearer matches
            val distanceBias = abs(step) * distanceBiasPerToken + if (step < 0) backwardBias else 0f
            val biasedCost = rawCost + distanceBias
            if (biasedCost < bestBiasedCost || (biasedCost == bestBiasedCost && rawCost < bestRawCost)) {
                bestBiasedCost = biasedCost
                bestRawCost = rawCost
                bestCandidateCursor = candidateCursor
            }
        }

        return if (bestCandidateCursor >= 0) bestCandidateCursor else null
    }

    private fun phoneticSubstitutionCost(
        a: Array<String>,
        b: Array<String>,
        looseFollow: Boolean
    ): Float {
        for (i in a.indices) {
            val pa = a[i]
            for (j in b.indices) {
                if (pa == b[j]) return 0f
            }
        }
        if (!looseFollow) return 1f

        for (i in a.indices) {
            val pa = a[i]
            for (j in b.indices) {
                if (editDistanceAtMostOne(pa, b[j])) return 0.45f
            }
        }
        return 1f
    }

    private fun editDistanceAtMostOne(s1: String, s2: String): Boolean {
        val len1 = s1.length
        val len2 = s2.length
        if (abs(len1 - len2) > 1) return false
        if (len1 == len2) {
            var diffs = 0
            for (k in 0 until len1) {
                if (s1[k] != s2[k] && ++diffs > 1) return false
            }
            return diffs <= 1
        }
        val shorter = if (len1 < len2) s1 else s2
        val longer = if (len1 < len2) s2 else s1
        var i = 0
        var j = 0
        var skipped = false
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] != longer[j]) {
                if (skipped) return false
                skipped = true
                j++
            } else {
                i++
                j++
            }
        }
        return true
    }

    private fun tokenize(text: String): List<ScriptToken> {
        val result = ArrayList<ScriptToken>(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch in '0'..'9' -> {
                    var end = i + 1
                    while (end < text.length && text[end] in '0'..'9') {
                        end++
                    }
                    val digits = text.substring(i, end)
                    val isYearLike = digits.length == 4 &&
                        (digits.startsWith("19") || digits.startsWith("20")) &&
                        end < text.length && text[end] == '年'
                    val expanded = if (isYearLike) {
                        expandDigitByDigit(digits)
                    } else {
                        expandNumberToChinesePinyin(digits)
                    }
                    for (py in expanded) {
                        result.add(ScriptToken(i, end, setOf(normalizeFuzzyPinyin(py))))
                    }
                    i = end
                }

                isCjk(ch) -> {
                    val rawArray = PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)
                    val pinyins = rawArray
                        ?.map { normalizeFuzzyPinyin(it) }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        .orEmpty()
                    if (pinyins.isNotEmpty()) {
                        result.add(ScriptToken(i, i + 1, pinyins))
                    }
                    i++
                }

                ch.isLetter() -> {
                    result.add(ScriptToken(i, i + 1, setOf(ch.lowercaseChar().toString())))
                    i++
                }

                else -> i++
            }
        }
        return result
    }

    private fun expandDigitByDigit(digits: String): List<String> =
        digits.map { DIGIT_PINYIN[it - '0'] }

    private fun expandNumberToChinesePinyin(digits: String): List<String> {
        if (digits.length > 4) return expandDigitByDigit(digits)
        val value = digits.toIntOrNull() ?: return expandDigitByDigit(digits)
        if (value == 0) return listOf(DIGIT_PINYIN[0])

        val out = mutableListOf<String>()
        val thousands = value / 1000
        val hundreds = (value % 1000) / 100
        val tens = (value % 100) / 10
        val ones = value % 10
        var needZero = false

        if (thousands > 0) {
            out.add(if (thousands == 2) "liang" else DIGIT_PINYIN[thousands])
            out.add("qian")
        }
        if (hundreds > 0) {
            out.add(DIGIT_PINYIN[hundreds])
            out.add("bai")
        } else if (thousands > 0 && (tens > 0 || ones > 0)) {
            needZero = true
        }
        if (tens > 0) {
            if (needZero) {
                out.add("ling")
                needZero = false
            }
            if (!(tens == 1 && thousands == 0 && hundreds == 0)) {
                out.add(DIGIT_PINYIN[tens])
            }
            out.add("shi")
        } else if (hundreds > 0 && ones > 0) {
            needZero = true
        }
        if (ones > 0) {
            if (needZero) out.add("ling")
            out.add(DIGIT_PINYIN[ones])
        }
        return out
    }

    private fun normalizeFuzzyPinyin(py: String): String {
        var s = py.lowercase()
        if (s.startsWith("zh")) s = "z" + s.substring(2)
        else if (s.startsWith("ch")) s = "c" + s.substring(2)
        else if (s.startsWith("sh")) s = "s" + s.substring(2)
        if (s.startsWith("l")) s = "n" + s.substring(1)

        s = s.replace("ing", "in")
            .replace("eng", "en")
            .replace("ang", "an")
        return s
    }

    private fun isCjk(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF

    companion object {
        private const val FAR_JUMP_CONTEXT_TOKENS = 12
        private const val TAIL_MAX_TOKENS = 8
        private const val FAR_JUMP_SHORT_TAIL_TOKENS = 6
        private const val MIN_SPOKEN_TOKENS = 3
        private const val ADAPTIVE_ANCHOR_MIN_TOKENS = 5
        private const val FAR_JUMP_MIN_TOKENS = 6
        private const val LOCAL_ROLLBACK_TOKENS = 16
        private const val ADAPTIVE_LOCAL_ROLLBACK_TOKENS = 10
        private const val LOCAL_LOOKAHEAD_TOKENS = 18
        private const val FAR_ROLLBACK_TOKENS = 64
        private const val FAR_LOOKAHEAD_TOKENS = 64
        private const val LOCAL_DISTANCE_BIAS_PER_TOKEN = 0.025f
        private const val FAR_DISTANCE_BIAS_PER_TOKEN = 0.015f
        private const val BACKWARD_BIAS = 0.20f

        private val DIGIT_PINYIN = arrayOf(
            "ling", "yi", "er", "san", "si", "wu", "liu", "qi", "ba", "jiu"
        )

        private val END_PUNCTUATION = setOf(
            '。', '！', '？', '；', '，', '：', '、', '…', '—', '”', '’', '）', '》', '】',
            '.', '!', '?', ';', ',', ':', ')', '"'
        )

        /**
         * Strips blank lines and line breaks for continuous large-font playback without
         * losing natural pauses between unpunctuated lines.
         */
        fun formatForPlayback(rawScript: String, stripLineBreaks: Boolean): String {
            if (!stripLineBreaks) return rawScript
            val lines = rawScript.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (lines.isEmpty()) return ""

            val sb = StringBuilder(rawScript.length)
            for (line in lines) {
                if (sb.isNotEmpty()) {
                    val lastChar = sb[sb.length - 1]
                    if (lastChar !in END_PUNCTUATION) {
                        sb.append(' ')
                    }
                }
                sb.append(line)
            }
            return sb.toString()
        }
    }
}
