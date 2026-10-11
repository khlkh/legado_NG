package io.legado.app.ui.book.read.config

import androidx.annotation.StringRes
import io.legado.app.R

internal data class NgPaperLook(
    val id: String,
    @param:StringRes val nameRes: Int = 0,
    val name: String = "",
    val background: Int,
    val text: Int,
    val accent: Int,
)

/** One reading theme, with a day page and a night page under the same name. */
internal data class AiThemePair(
    val name: String,
    val day: NgPaperLook,
    val night: NgPaperLook,
)

internal data class DefaultPaperPair(
    val id: String,
    @param:StringRes val nameRes: Int,
    val dayBackground: Int,
    val dayText: Int,
    val dayAccent: Int,
    val nightBackground: Int,
    val nightText: Int,
    val nightAccent: Int,
) {
    fun toPair(name: String): AiThemePair = AiThemePair(
        name = name,
        day = look(name, dayBackground, dayText, dayAccent),
        night = look(name, nightBackground, nightText, nightAccent),
    )

    fun side(name: String, night: Boolean): NgPaperLook = if (night) {
        look(name, nightBackground, nightText, nightAccent)
    } else {
        look(name, dayBackground, dayText, dayAccent)
    }

    private fun look(name: String, background: Int, text: Int, accent: Int) = NgPaperLook(
        id = id,
        nameRes = nameRes,
        name = name,
        background = background,
        text = text,
        accent = accent,
    )
}

/** Stable catalog. The entry card uses the first look; the AI page rotates this list. */
internal fun ngPaperLooks(night: Boolean): List<NgPaperLook> =
    defaultPaperPairs.map { it.side(name = "", night = night) }

/** The full default catalog, rotated so the shuffle control changes which theme is first. */
internal fun rotatedDefaultPairs(shift: Int): List<DefaultPaperPair> {
    if (defaultPaperPairs.isEmpty()) return emptyList()
    val start = shift.mod(defaultPaperPairs.size)
    return List(defaultPaperPairs.size) { offset ->
        defaultPaperPairs[(start + offset) % defaultPaperPairs.size]
    }
}

private val defaultPaperPairs = listOf(
    pair("xuan", R.string.ng_paper_xuan, 0xFFF6F1E7, 0xFF2E2A24, 0xFF2F5F7A, 0xFF1E1D1A, 0xFFE8E0D4, 0xFF6FB0C9),
    pair("riverside", R.string.ng_paper_riverside, 0xFFF7EDE2, 0xFF33281F, 0xFF2E5E7A, 0xFF241A14, 0xFFEAD8C5, 0xFF6FB3C9),
    pair("mist", R.string.ng_paper_mist_rain, 0xFFEFF1F0, 0xFF2B3230, 0xFFA8542E, 0xFF1B2022, 0xFFDDE5E4, 0xFFD99A6A),
    pair("dew", R.string.ng_paper_forest_dew, 0xFFF4F7F0, 0xFF2F382C, 0xFFB06A2E, 0xFF1E241C, 0xFFE2E8DC, 0xFFD9A45C),
    pair("sea", R.string.ng_paper_quiet_sea, 0xFFEDF4F2, 0xFF2A3835, 0xFFC25E3A, 0xFF141E24, 0xFFDCE8E8, 0xFF5FC4B8),
    pair("tundra", R.string.ng_paper_tundra, 0xFFF0F2EC, 0xFF323A32, 0xFF7A4A8C, 0xFF1C221C, 0xFFE0E6DE, 0xFFB79AD4),
    pair("dusk", R.string.ng_paper_purple_dusk, 0xFFF3E8E8, 0xFF332B33, 0xFF8A6A1F, 0xFF221C26, 0xFFE6DEE8, 0xFFD9B85C),
    pair("starlit", R.string.ng_paper_starlit, 0xFFF2F6F4, 0xFF2A3330, 0xFFA8443A, 0xFF171F22, 0xFFDEE6E4, 0xFFD97A6A),
    pair("amethyst", R.string.ng_paper_amethyst, 0xFFF3EEF6, 0xFF2E2A33, 0xFF1F6B4A, 0xFF1C1826, 0xFFE4DEF0, 0xFF6FC99A),
    pair("ember", R.string.ng_paper_ember, 0xFFF7EEE2, 0xFF332A22, 0xFF2E4A8C, 0xFF1E1816, 0xFFE8DCD0, 0xFF7A9AD9),
)

private fun pair(
    id: String,
    @StringRes nameRes: Int,
    dayBackground: Long,
    dayText: Long,
    dayAccent: Long,
    nightBackground: Long,
    nightText: Long,
    nightAccent: Long,
) = DefaultPaperPair(
    id = id,
    nameRes = nameRes,
    dayBackground = dayBackground.toInt(),
    dayText = dayText.toInt(),
    dayAccent = dayAccent.toInt(),
    nightBackground = nightBackground.toInt(),
    nightText = nightText.toInt(),
    nightAccent = nightAccent.toInt(),
)
