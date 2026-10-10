package io.legado.app.ui.book.read.config

import androidx.annotation.StringRes
import io.legado.app.R

internal data class NgPaperLook(
    @param:StringRes val nameRes: Int,
    val background: Int,
    val text: Int,
    val accent: Int,
)

/** Local paper looks for the AI theme page. Day accent stays the locked blue. */
internal fun ngPaperLooks(night: Boolean): List<NgPaperLook> = if (night) nightPaperLooks else dayPaperLooks

private val dayAccent = 0xFF2B579A.toInt()

private val dayPaperLooks = listOf(
    NgPaperLook(R.string.read_style_ai_cream, 0xFFFAF9F5.toInt(), 0xFF333333.toInt(), dayAccent),
    NgPaperLook(R.string.read_style_ai_warm, 0xFFF4ECD8.toInt(), 0xFF3A3226.toInt(), dayAccent),
    NgPaperLook(R.string.read_style_ai_cool, 0xFFF7FAFC.toInt(), 0xFF26303A.toInt(), dayAccent),
)

private val nightPaperLooks = listOf(
    NgPaperLook(R.string.read_style_ai_ink, 0xFF1A1A1A.toInt(), 0xFFD6D6D6.toInt(), 0xFF9BB6D6.toInt()),
    NgPaperLook(R.string.read_style_ai_umber, 0xFF231C14.toInt(), 0xFFE6D5BE.toInt(), 0xFFC4A574.toInt()),
    NgPaperLook(R.string.read_style_ai_slate, 0xFF1C2430.toInt(), 0xFFD5DCE6.toInt(), 0xFF9BB6D6.toInt()),
)
