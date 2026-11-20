/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.data

import android.content.Context
import android.content.SharedPreferences

enum class FollowMode(val displayName: String, val description: String) {
    ADAPTIVE_SPEED(
        displayName = "语速自适应（推荐）",
        description = "按说话识别速度推进 + 锚点校准，改词也不卡顿"
    ),
    ANCHOR_STRICT(
        displayName = "精准锚点跟读",
        description = "严格匹配台词拼音定位，适合照本宣科"
    ),
    UNIFORM_SCROLL(
        displayName = "纯匀速滚动",
        description = "不监听麦克风，适合嘈杂现场备用"
    )
}

enum class AppThemePalette(val displayName: String, val swatchHex: Long) {
    AMBER("专业琥珀金", 0xFFFFE066),
    BLUE("莫奈动态蓝", 0xFFA8C7FA),
    TEAL("护眼森林青", 0xFF80D9B5),
    LAVENDER("M3 经典紫", 0xFFD0BCFF)
}

data class PrompterSettings(
    val portraitFontSizeSp: Float = 54f,
    val portraitMarginDp: Float = 14f,
    val landscapeFontSizeSp: Float = 58f,
    val landscapeMarginDp: Float = 48f,
    val lineHeightMultiplier: Float = 1.4f,
    val boldBodyText: Boolean = true,
    val themePalette: AppThemePalette = AppThemePalette.AMBER,
    val followMode: FollowMode = FollowMode.ADAPTIVE_SPEED,
    val linesAbove: Int = 1,
    val lineHighlight: Boolean = true,
    val allowRollback: Boolean = true,
    val looseFollow: Boolean = true,
    val stripLineBreaks: Boolean = true,
    val fallbackCharsPerSec: Float = 4.2f,
    val isLandscape: Boolean = false
) {
    val activeFontSizeSp: Float
        get() = if (isLandscape) landscapeFontSizeSp else portraitFontSizeSp

    val activeHorizontalMarginDp: Float
        get() = if (isLandscape) landscapeMarginDp else portraitMarginDp

    fun withActiveFontSize(sizeSp: Float): PrompterSettings {
        val clamped = sizeSp.coerceIn(32f, 80f)
        return if (isLandscape) copy(landscapeFontSizeSp = clamped)
        else copy(portraitFontSizeSp = clamped)
    }

    fun withActiveMargin(marginDp: Float): PrompterSettings =
        if (isLandscape) copy(landscapeMarginDp = marginDp.coerceIn(16f, 220f))
        else copy(portraitMarginDp = marginDp.coerceIn(8f, 96f))
}

class ScriptStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("teleprompter_prefs", Context.MODE_PRIVATE)

    fun loadScript(): String =
        prefs.getString(KEY_SCRIPT, DEFAULT_SAMPLE_SCRIPT) ?: DEFAULT_SAMPLE_SCRIPT

    fun saveScript(script: String) {
        prefs.edit().putString(KEY_SCRIPT, script).apply()
    }

    fun loadSettings(): PrompterSettings {
        val version = prefs.getInt(KEY_PREFS_VERSION, 1)
        val useNewDefaults = version < CURRENT_PREFS_VERSION
        val modeName = prefs.getString(KEY_FOLLOW_MODE, FollowMode.ADAPTIVE_SPEED.name)
        val mode = runCatching { FollowMode.valueOf(modeName ?: "") }
            .getOrDefault(FollowMode.ADAPTIVE_SPEED)
        val paletteName = prefs.getString(KEY_THEME_PALETTE, AppThemePalette.AMBER.name)
        val palette = runCatching { AppThemePalette.valueOf(paletteName ?: "") }
            .getOrDefault(AppThemePalette.AMBER)

        val settings = PrompterSettings(
            portraitFontSizeSp = if (useNewDefaults) 54f else prefs.getFloat(KEY_PORTRAIT_FONT, 54f).coerceIn(32f, 80f),
            portraitMarginDp = if (useNewDefaults) 14f else prefs.getFloat(KEY_PORTRAIT_MARGIN, 14f).coerceIn(8f, 96f),
            landscapeFontSizeSp = if (useNewDefaults) 58f else prefs.getFloat(KEY_LANDSCAPE_FONT, 58f).coerceIn(32f, 80f),
            landscapeMarginDp = if (useNewDefaults) 48f else prefs.getFloat(KEY_LANDSCAPE_MARGIN, 48f).coerceIn(16f, 220f),
            lineHeightMultiplier = prefs.getFloat(KEY_LINE_HEIGHT, 1.4f).coerceIn(1.2f, 1.8f),
            boldBodyText = prefs.getBoolean(KEY_BOLD_BODY, true),
            themePalette = palette,
            followMode = mode,
            linesAbove = prefs.getInt(KEY_LINES_ABOVE, 1).coerceIn(0, 2),
            lineHighlight = prefs.getBoolean(KEY_LINE_HIGHLIGHT, true),
            allowRollback = prefs.getBoolean(KEY_ROLLBACK, true),
            looseFollow = prefs.getBoolean(KEY_LOOSE_FOLLOW, true),
            stripLineBreaks = prefs.getBoolean(KEY_STRIP_BREAKS, true),
            fallbackCharsPerSec = prefs.getFloat(KEY_FALLBACK_SPEED, 4.2f).coerceIn(1.5f, 10f),
            isLandscape = prefs.getBoolean(KEY_LANDSCAPE, false)
        )
        if (useNewDefaults) {
            saveSettings(settings)
        }
        return settings
    }

    fun saveSettings(settings: PrompterSettings) {
        prefs.edit()
            .putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION)
            .putFloat(KEY_PORTRAIT_FONT, settings.portraitFontSizeSp)
            .putFloat(KEY_PORTRAIT_MARGIN, settings.portraitMarginDp)
            .putFloat(KEY_LANDSCAPE_FONT, settings.landscapeFontSizeSp)
            .putFloat(KEY_LANDSCAPE_MARGIN, settings.landscapeMarginDp)
            .putFloat(KEY_LINE_HEIGHT, settings.lineHeightMultiplier)
            .putBoolean(KEY_BOLD_BODY, settings.boldBodyText)
            .putString(KEY_THEME_PALETTE, settings.themePalette.name)
            .putString(KEY_FOLLOW_MODE, settings.followMode.name)
            .putInt(KEY_LINES_ABOVE, settings.linesAbove)
            .putBoolean(KEY_LINE_HIGHLIGHT, settings.lineHighlight)
            .putBoolean(KEY_ROLLBACK, settings.allowRollback)
            .putBoolean(KEY_LOOSE_FOLLOW, settings.looseFollow)
            .putBoolean(KEY_STRIP_BREAKS, settings.stripLineBreaks)
            .putFloat(KEY_FALLBACK_SPEED, settings.fallbackCharsPerSec)
            .putBoolean(KEY_LANDSCAPE, settings.isLandscape)
            .apply()
    }

    companion object {
        private const val CURRENT_PREFS_VERSION = 3
        private const val KEY_PREFS_VERSION = "prefs_version"
        private const val KEY_SCRIPT = "script_text"
        private const val KEY_PORTRAIT_FONT = "portrait_font_sp"
        private const val KEY_PORTRAIT_MARGIN = "portrait_margin_dp"
        private const val KEY_LANDSCAPE_FONT = "landscape_font_sp"
        private const val KEY_LANDSCAPE_MARGIN = "landscape_margin_dp"
        private const val KEY_LINE_HEIGHT = "line_height_multiplier"
        private const val KEY_BOLD_BODY = "bold_body_text"
        private const val KEY_THEME_PALETTE = "theme_palette"
        private const val KEY_FOLLOW_MODE = "follow_mode"
        private const val KEY_LINES_ABOVE = "lines_above"
        private const val KEY_LINE_HIGHLIGHT = "line_highlight"
        private const val KEY_ROLLBACK = "allow_rollback"
        private const val KEY_LOOSE_FOLLOW = "loose_follow"
        private const val KEY_STRIP_BREAKS = "strip_breaks"
        private const val KEY_FALLBACK_SPEED = "fallback_speed"
        private const val KEY_LANDSCAPE = "is_landscape"

        val DEFAULT_SAMPLE_SCRIPT = """
            大家好，欢迎来到今天的拍摄现场。
            今天我们不用死记硬背长篇台词，直接用手机搭配专业相机来进行口播录制。
            现在你看到的画面，是由离线语音引擎实时驱动的。
            当你开口说话，文字会自动跟随你的语速平滑向上推进。
            当你停顿思考，或者因为现场调整突然慢下来，台词也会稳稳地停在原地等你。
            哪怕刚才有一句话不小心念错了，只要倒回去重念一遍，画面就会自动退回到刚才的位置，完全不需要用手去碰手机屏幕。
            无论是竖屏架在相机上方，还是横屏夹在兔笼旁边，都会分别记住最合适的字号和边距，祝你今天的拍摄一条过！
        """.trimIndent()
    }
}
