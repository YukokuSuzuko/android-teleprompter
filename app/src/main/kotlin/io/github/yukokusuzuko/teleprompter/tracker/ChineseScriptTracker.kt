package io.github.yukokusuzuko.teleprompter.tracker

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Represents one spoken syllable/unit extracted from the script, mapped back to its
 * exact character index in the raw script string.
 */
data class ScriptToken(
    val rawOffset: Int,
    val rawEndOffset: Int,
    val pinyins: Set<String>
)

/**
 * Pure-Chinese streaming speech-to-script alignment engine.
 *
 * Key safeguards:
 * - Uses fuzzy pinyin normalization (平翘舌/前后鼻音/n-l), polyphone sets, and Chinese numeral
 *   expansion for spoken delivery tolerance.
 * - Bounds daily anchor calibration closely around the current cursor with a distance bias
 *   so repeated words/phrases in later paragraphs cannot yank the prompter out of place.
 * - Requires >= 6 spoken tokens, high confidence, and 2 consecutive confirming updates before
 *   allowing any large cross-paragraph jump.
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
        val backMargin = if (allowRollback) MAX_ROLLBACK_TOKENS else 2

        // 1. First look tightly in immediate local window [-backMargin, +18] to stay locked nearby
        val localMatch = alignWindow(
            spokenTokens = primaryTail,
            minAllowedEndCursor = max(0, tokenCursor - backMargin),
            maxEndCursor = min(tokens.lastIndex, tokenCursor + LOCAL_LOOKAHEAD_TOKENS),
            allowRollback = allowRollback,
            looseFollow = looseFollow
        )

        if (localMatch != null) {
            pendingJumpTarget = -1
            tokenCursor = localMatch
        } else if (looseFollow && allSpoken.size >= 5) {
            // 2. Short forward recovery strictly within next 12 chars (unstick 1-2 misread words)
            val forwardStart = min(tokens.lastIndex, tokenCursor + 1)
            val forwardEnd = min(tokens.lastIndex, tokenCursor + 12)
            val shortMatch = if (forwardStart <= forwardEnd) {
                alignWindow(
                    spokenTokens = allSpoken.takeLast(5),
                    minAllowedEndCursor = forwardStart,
                    maxEndCursor = forwardEnd,
                    allowRollback = false,
                    looseFollow = true,
                    overrideMinConfidence = 0.72f
                )
            } else null

            if (shortMatch != null) {
                pendingJumpTarget = -1
                tokenCursor = shortMatch
            } else if (allSpoken.size >= FAR_JUMP_MIN_TOKENS) {
                // 3. Far skip recovery across paragraphs: require high confidence + 2-update confirmation
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
     * while speaking so paraphrasing never stalls, and calibrates conservatively within the immediate
     * neighborhood ([-8, +12] tokens) so repeated words elsewhere never cause paragraph jumps.
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
            val backMargin = if (allowRollback) 10 else 4
            val tail = allSpoken.takeLast(TAIL_MAX_TOKENS)
            val localAnchor = alignWindow(
                spokenTokens = tail,
                minAllowedEndCursor = max(0, tokenCursor - backMargin),
                maxEndCursor = min(tokens.lastIndex, tokenCursor + 12),
                allowRollback = allowRollback,
                looseFollow = looseFollow,
                overrideMinConfidence = if (looseFollow) 0.68f else 0.76f
            )

            if (localAnchor != null) {
                pendingJumpTarget = -1
                tokenCursor = localAnchor
                adaptiveCursor = localAnchor.toFloat()
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

    private fun tryConfirmedFarJump(
        allSpoken: List<ScriptToken>,
        allowRollback: Boolean,
        looseFollow: Boolean
    ) {
        val backLimit = if (allowRollback) MAX_ROLLBACK_TOKENS else 0
        val farCandidate = alignWindow(
            spokenTokens = allSpoken.takeLast(TAIL_MAX_TOKENS),
            minAllowedEndCursor = max(0, tokenCursor - backLimit),
            maxEndCursor = min(tokens.lastIndex, tokenCursor + FAR_LOOKAHEAD_TOKENS),
            allowRollback = allowRollback,
            looseFollow = looseFollow,
            overrideMinConfidence = 0.82f
        ) ?: return

        val step = farCandidate - tokenCursor
        if (abs(step) <= 12) {
            pendingJumpTarget = -1
            tokenCursor = farCandidate
            adaptiveCursor = farCandidate.toFloat()
            return
        }

        if (pendingJumpTarget >= 0 && abs(farCandidate - pendingJumpTarget) <= 4) {
            tokenCursor = farCandidate
            adaptiveCursor = farCandidate.toFloat()
            pendingJumpTarget = -1
        } else {
            pendingJumpTarget = farCandidate
        }
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
        overrideMinConfidence: Float? = null
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

        val dp = Array(m + 1) { FloatArray(n + 1) }
        for (i in 1..m) {
            val spokenStepCost = if (looseFollow && isFillerParticle(spokenTokens[i - 1].pinyins)) {
                0.35f
            } else {
                baseSkipSpokenCost
            }
            dp[i][0] = dp[i - 1][0] + spokenStepCost
        }

        for (i in 1..m) {
            val spokenSet = spokenTokens[i - 1].pinyins
            val spokenSkipCost = if (looseFollow && isFillerParticle(spokenSet)) 0.35f else baseSkipSpokenCost
            for (j in 1..n) {
                val targetSet = window[j - 1].pinyins
                val scriptSkipCost = if (looseFollow && isFillerParticle(targetSet)) 0.35f else baseSkipScriptCost
                val subCost = phoneticSubstitutionCost(spokenSet, targetSet, looseFollow)
                dp[i][j] = minOf(
                    dp[i - 1][j - 1] + subCost,
                    dp[i - 1][j] + spokenSkipCost,
                    dp[i][j - 1] + scriptSkipCost
                )
            }
        }

        val minAllowedJ = (minAllowedEndCursor - winStart + 1).coerceIn(1, n)
        var bestJ = -1
        var bestRawCost = Float.MAX_VALUE
        var bestBiasedCost = Float.MAX_VALUE

        for (j in minAllowedJ..n) {
            val rawCost = dp[m][j]
            val candidateIdx = winStart + j - 1
            // Mild distance bias prevents duplicate words further away from beating the local match
            val distanceBias = abs(candidateIdx - tokenCursor) * 0.025f
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

    private fun phoneticSubstitutionCost(
        a: Set<String>,
        b: Set<String>,
        looseFollow: Boolean
    ): Float {
        if (a.any { it in b }) return 0f
        if (!looseFollow) return 1f

        for (pa in a) {
            for (pb in b) {
                if (editDistanceAtMostOne(pa, pb)) return 0.45f
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

    private fun isFillerParticle(pinyins: Set<String>): Boolean =
        pinyins.any { it in FILLER_PARTICLES }

    private fun isCjk(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF

    companion object {
        private const val TAIL_MAX_TOKENS = 8
        private const val MIN_SPOKEN_TOKENS = 3
        private const val ADAPTIVE_ANCHOR_MIN_TOKENS = 5
        private const val FAR_JUMP_MIN_TOKENS = 6
        private const val MAX_ROLLBACK_TOKENS = 16
        private const val LOCAL_LOOKAHEAD_TOKENS = 18
        private const val FAR_LOOKAHEAD_TOKENS = 42

        private val FILLER_PARTICLES = setOf(
            "a", "o", "e", "en", "na", "ne", "ba", "ma", "ya", "le", "de", "jiu"
        )

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
