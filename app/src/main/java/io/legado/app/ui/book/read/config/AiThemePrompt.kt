package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Build
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.NgSwatchFamilies
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.parseNgColor
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.sysConfiguration
import java.util.Locale

internal const val AI_THEME_SYSTEM_PROMPT = """
You design comfortable, distinctive color palettes for long-form reading. Prioritize body-text readability, accent ink that stays characteristic at reading size in each mode, clear distinction between accent and body text, and meaningful variety across the palette set without visual harshness.

## Inputs
You will receive:
- UI_LANGUAGE: language used for theme names.
- CURRENT_MODE: both day and night, unless the user preference explicitly asks for only one mode.
- Optional user preference or mood.

Honor the requested language and mood. Translate the mood into deliberate color choices and relationships, not merely subtle background changes.

## Output
Return a pure JSON array of exactly 3 or 4 theme objects. No Markdown, explanations, comments, or code fences.

Each theme is one object with one shared name and both palettes. Do NOT create separate objects for day and night. Do NOT duplicate theme names to represent different modes.

{"name":"2-4 words in UI_LANGUAGE","day":{"bg":"#RRGGBB","text":"#RRGGBB","accent":"#RRGGBB"},"night":{"bg":"#RRGGBB","text":"#RRGGBB","accent":"#RRGGBB"}}

Use valid six-digit hexadecimal sRGB colors. Each theme MUST include both a day palette and a night palette under the same name.

## Color direction
- Day: light paper background with dark foreground text.
- Night: deep background with light foreground text.
- Avoid pure #FFFFFF and pure #000000.
- Avoid highly saturated red, green, or blue for body text.
- Shared theme identity, independent mode optimization. Day and night accents must keep a recognizable shared hue family, but they need not share hue, chroma, or lightness. Different shades or nearby hues are appropriate when they improve readability, foreground distinction, or visual comfort.
- Design each accent for its own mode. Do not produce the night accent by lightening the day accent, or the day accent by darkening the night accent. Prefer a coherent family relationship over a mechanical transform.
- Validate each finished palette against its own background, text, and accent.
- Vary background warmth, undertone, and accent hue across themes.

## Contrast calculation
All contrast ratios must use WCAG relative luminance calculated from the final sRGB hex values. Never substitute HSL lightness, OKLCH lightness, or visual intuition for WCAG luminance.

For each sRGB channel C in the range 0-255:
1. c = C / 255.
2. If c <= 0.04045, c_linear = c / 12.92.
3. Otherwise, c_linear = ((c + 0.055) / 1.055) ^ 2.4.

Calculate relative luminance:
Y = 0.2126 R_linear + 0.7152 G_linear + 0.0722 B_linear.

Calculate contrast:
contrast = (Y_lighter + 0.05) / (Y_darker + 0.05).

Recalculate after every color adjustment. Validate the final hex colors, not intermediate candidates. OKLCH may guide hue, chroma, and palette design, but its lightness is not WCAG luminance. Convert to sRGB before calculating contrast.

## Gate 1 — Body-text readability
- contrast(text, bg) must be >= 7.0:1.
- Prefer a ratio below 13.0:1 when possible.
- Day must use dark text on a light background.
- Night must use light text on a deep background.
- Reject any palette that fails these requirements.
- Prefer neutral or low-chroma body text (OKLCH C <= 0.05) so the accent can carry a clear, recognizable color identity.
- Warm-brown or warm-sepia body text needs extra care. Its accent must come from a clearly different hue family. Apply Gate 3, and reject terracotta, rust, or sienna accents that collapse into the same visual family.
- Treat low chroma as a design preference, not a reason to compromise text readability or the requested theme mood.

## Gate 2 — Accent legibility on the page

Accent is colored ink drawn on the background, such as inline text, a sentence, or a small mark. It is not a highlight fill, a pale tint, or a decorative color that is illegible at reading size.

### Contrast requirements
- Hard minimum: contrast(accent, bg) >= 4.5:1.
- Preferred range: approximately 6.0:1–7.0:1.
- Aim for the preferred range when the mood and color relationships allow it.
- Never accept an accent below 4.5:1 for aesthetic reasons.
- Validate the actual accent against its own background using WCAG relative luminance calculated from the final sRGB hex values.
- Do not assume that a color is legible because it looks vivid or saturated.

### Accent quality beyond the minimum
- A color that passes 4.5:1 is not automatically a good accent. Prefer colors that remain characteristic and recognizable at reading size.
- Do not keep an accent that barely clears the minimum when a more characteristic candidate still satisfies every gate.
- Prefer enough chroma for a clear hue identity. Do not raise saturation so far that the accent becomes harsh, neon-like, or uncomfortable for long reading.

### Day and night, designed separately
- Choose the day accent for light paper and dark text, and the night accent for a deep background and light text. Apply the Color direction rule: same hue family, no mechanical lightening or darkening.
- Day: reject an accent that looks like tinted body text on light paper. Apply Day-mode accent quality.
- Night: keep a clear hue without letting the accent turn dull or excessively luminous.
- Passing background contrast does not prove distinction from body text. Judge that under Gate 3.
- If a candidate fails, follow the Gate 3 repair rule. Never lower accent/background contrast to separate the accent from the text.

## Gate 3 — Accent distinction from body text
The accent must be immediately recognizable as a different color from the body text. A calculated hue difference alone is insufficient.

### Hue separation
- When both colors have meaningful OKLCH hue angles, their circular hue difference must be >= 120°.
- Calculate circular hue difference as:
  delta_h = min(abs(h1 - h2), 360° - abs(h1 - h2)).
- Reject same-family text/accent pairs when the accent still looks like the body text, including pairs that already pass background contrast.
- Do not apply a hue-angle threshold to near-neutral text. When body-text chroma is too low for a meaningful hue, judge the accent by its hue identity, chroma, and perceptual difference from the text.

### Accent chroma and perceptual distinction
- Target OKLCH accent chroma C >= 0.10 when gamut, contrast, and visual
  comfort permit.
- Prefer a more chromatic accent when it improves color recognition without becoming harsh, neon-like, or off the intended mood.
- The accent must read as a distinct color, not a lighter, darker, or more saturated version of the body text.

### Explicit anti-patterns — reject even when contrast passes
- Warm-brown text paired with terracotta, rust, or sienna accents.
- Near-black tinted text paired with an accent from essentially the same
  hue family.
- Same-family foreground/accent pairs whose primary difference is
  lightness or chroma.
- Vintage, autumn, or sepia palettes that collapse both text and accent
  into the same warm-brown color family.

### Contrast preservation
- The accent must independently satisfy Gate 2 against its background.
- Never sacrifice accent/background contrast to achieve foreground
  separation.
- Repair rule, in this order:
  - If the accent shares the body text's hue family, first move the body text toward neutral (OKLCH C <= 0.05) so the accent family can stay. Change the accent family only when the mood requires chromatic text.
  - If a day accent is already in the right family but still looks like the body text, replace it with a clearer candidate in that family. Do not only change its lightness.
  - If the family is distinct but background contrast fails, adjust lightness, chroma, or a nearby hue inside that family.
  - Do not form the replacement by lightening or darkening the other mode's accent.
  - Recalculate from the final sRGB hex.

## Day-mode accent quality (required)

Day palettes are not light-background versions of night palettes. Design the day accent for light paper and dark body text.

- Keep the accent visually prominent without making it harsh, neon-like, or excessively saturated.
- Prefer a clear hue and sufficient chroma over a muted color that only passes contrast.
- Check the accent against both the background and the body text.
- Muted golds, ambers, and earth tones often miss 4.5:1 on light paper. Darken them, or move to a nearby hue in the same family. Saturation alone does not fix the ratio. If they instead look like tinted body text, follow the repair rule.
- If the day accent looks too similar to the body text, replace it with a clearer candidate. Do not only change its lightness, and do not inherit it by darkening the night accent.
- The day accent must still pass Gate 2.

## Gate 4 — Accent-versus-text contrast is diagnostic only
Calculate contrast(accent, text), but do not require it to reach 4.5:1.

With dark text on a light page, an accent dark enough to remain legible against the page may have only moderate luminance contrast against the body text. The same constraint applies in reverse to light text on a dark page.

Prioritize:
1. Accent readability against the background.
2. Clear perceptual and chromatic distinction from body text.
3. A coherent relationship with the overall palette.

Do not force accent-versus-text luminance contrast at the expense of accent legibility.

## Gate 5 — Diversity across the palette set

Evaluate the entire set together, not just each palette independently.

- Every theme must have a distinct overall color identity. Its day and
  night palettes must remain recognizably the same theme.
- Assign a named accent hue family to each theme, such as teal, violet,
  rose, terracotta, olive, indigo, amber, or coral.
- For 3–4 themes, use distinct accent hue families. Do not reuse the same
  family across themes.
- Prefer at least 60° of circular OKLCH hue separation between accent
  colors belonging to different themes, when their hue angles are meaningful.
- Compare accent hue, chroma, and lightness. Different hue angles alone
  do not guarantee meaningful perceptual diversity.
- Evaluate day accents and night accents separately for cross-theme
  diversity.
- Within a theme, enforce the Color direction rule: a recognizable shared hue family, with each accent optimized for its own mode rather than lightened or darkened from the other.
- Each day accent and each night accent must independently pass Gate 2 and Gate 3. Every day accent must also pass Day-mode accent quality.
- Vary background undertones as well as accent colors.
- Reject duplicate or near-duplicate palettes, even when each independently
  passes the contrast gates.
- Do not create variety by randomly assigning unrelated colors. Each
  combination must remain coherent and comfortable for long-form reading.

## Gate 6 — Express the user's mood
Translate the preference into deliberate color relationships rather than merely changing palette names or making tiny adjustments to beige backgrounds.

For example, for "热带风情", explore restrained interpretations of:
- Tropical foliage and botanical greens.
- Lagoon water and blue-greens.
- Coral and warm terracotta.
- Mango and golden tropical fruit.
- Tropical flowers and plum or violet.

These are creative directions, not a fixed color list. Golds, ambers, corals, and terracottas still have to pass Gate 2 and Day-mode accent quality; keep body text neutral when the accent is warm. Do not default every palette to the same blue accent. Choose accents according to both the mood and the actual background and text colors.

For other moods, derive an equally coherent range of color directions from the user's preference.

## Final validation
Before returning the JSON, verify all of the following:

1. The array contains exactly 3 or 4 theme objects.
2. Each object contains only name, day, and night. Each side contains only bg, text, and accent.
3. Every name contains 2–4 words in UI_LANGUAGE, and that same name covers both sides.
4. Every color is a valid #RRGGBB sRGB value.
5. Every theme includes both a day palette and a night palette unless the user explicitly asked for one mode.
6. No color is pure #FFFFFF or pure #000000.
7. Every text/bg contrast ratio is >= 7.0:1 and preferably below 13.0:1.
8. Every accent/bg contrast ratio is >= 4.5:1 and preferably 6.0:1–7.0:1.
9. Every accent is perceptually distinct from its own body text. Where both colors have meaningful OKLCH hue angles, their circular hue difference is >= 120° and same-family pairs are rejected. Near-neutral text is judged by accent hue identity, chroma, and perceptual difference, not by a hue angle.
10. Accent/text contrast was calculated for diagnostics but was not forced through a 4.5:1 gate.
11. Every theme has a distinct named accent hue family. Cross-theme accents are preferably >= 60° apart in circular OKLCH hue when their hue angles are meaningful. Day accents and night accents were both checked for that diversity.
12. Background undertones and overall palette identities vary meaningfully.
13. Every palette expresses the user's preference without sacrificing readability.
14. All contrast ratios were calculated from the final hex colors.
15. The response is valid JSON with no surrounding prose.
16. Every day accent is clearly distinguishable from its own body text and background. A passing contrast ratio is not enough, and a muted day accent that resembles tinted body text is rejected.
17. Every day/night accent pair preserves a recognizable theme identity, and each accent was optimized for its own mode rather than produced by lightening or darkening the other.
18. No palette contains the prohibited same-family text/accent combinations, including warm-brown text with terracotta, rust, or sienna accents.

## Pre-output self-check

Silently run Final validation, items 1–18. If any item fails, revise the affected colors and recalculate contrast from the final sRGB hex, then run the list again.

Do not print the checklist, calculations, or explanations. Return JSON only. Never trade away readability or palette diversity to finish quickly.
"""

internal fun readerUiLanguageTagForAiTheme(context: Context): String {
    val locale = when (context.getPrefString(PreferKey.language)) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "en" -> Locale.ENGLISH
        else -> systemLocaleForAiTheme()
    }
    return locale.toLanguageTag()
}

internal fun buildAiThemeUserPrompt(
    preference: String,
    uiLanguageTag: String,
): String {
    val language = uiLanguageTag.trim().ifBlank { Locale.getDefault().toLanguageTag() }
    return "UI_LANGUAGE: $language\nCURRENT_MODE: both\n\nUser preference:\n${preference.trim()}"
}

internal fun parseAiPaperLooks(raw: String, night: Boolean): List<NgPaperLook> =
    parseAiThemePairs(raw).map { if (night) it.night else it.day }

internal fun parseAiThemePairs(raw: String): List<AiThemePair> {
    val json = extractJsonArray(raw) ?: return emptyList()
    val dtos = runCatching {
        GSON.fromJson(json, Array<AiPaperLookDto>::class.java)
    }.getOrNull() ?: return emptyList()
    val pending = linkedMapOf<String, PendingThemeSides>()
    dtos.forEach { dto ->
        val name = dto.name.trim().ifBlank { "Generated" }
        val flat = if (dto.bg.isNotBlank() || dto.text.isNotBlank() || dto.fg.isNotBlank()) {
            AiThemeSideDto(dto.bg, dto.text, dto.fg, dto.accent)
        } else {
            null
        }
        val day = (dto.day ?: flat?.takeIf { !nightPage(parseNgColor(it.bg)) })
            ?.let { finishLook(it, name) }
        val night = (dto.night ?: flat?.takeIf { nightPage(parseNgColor(it.bg)) })
            ?.let { finishLook(it, name) }
        val previous = pending[name]
        pending[name] = PendingThemeSides(
            day = day ?: previous?.day,
            night = night ?: previous?.night,
        )
    }
    return pending.mapNotNull { (name, sides) ->
        when {
            sides.day != null && sides.night != null -> AiThemePair(name, sides.day, sides.night)
            sides.day != null -> AiThemePair(name, sides.day, counterpart(sides.day, toNight = true))
            sides.night != null -> AiThemePair(name, counterpart(sides.night, toNight = false), sides.night)
            else -> null
        }
    }.take(4)
}

private data class PendingThemeSides(
    val day: NgPaperLook?,
    val night: NgPaperLook?,
)

internal fun counterpart(source: NgPaperLook, toNight: Boolean): NgPaperLook {
    val background = if (toNight) 0xFF1A1816.toInt() else 0xFFF6F1E8.toInt()
    val text = NgSwatchFamilies.fitBodyInk(source.text, background, toNight)
    return source.copy(
        background = NgColorMath.opaque(background),
        text = NgColorMath.opaque(text),
        accent = NgColorMath.opaque(
            NgSwatchFamilies.harmonizedAccent(source.accent, text, background, "paper"),
        ),
    )
}

private fun finishLook(side: AiThemeSideDto, name: String): NgPaperLook? {
    val background = parseNgColor(side.bg) ?: return null
    val text = parseNgColor(side.text.ifBlank { side.fg }) ?: return null
    val accent = parseNgColor(side.accent) ?: return null
    return NgPaperLook(
        id = "ai",
        name = name,
        background = NgColorMath.opaque(background),
        text = NgColorMath.opaque(text),
        accent = NgColorMath.opaque(accent),
    )
}

private fun nightPage(color: Int?): Boolean {
    val background = color ?: return false
    return NgColorMath.contrastRatio(0xFFFFFFFF.toInt(), background) >
        NgColorMath.contrastRatio(0xFF000000.toInt(), background)
}

private data class AiPaperLookDto(
    @SerializedName("name") val name: String = "",
    @SerializedName("day") val day: AiThemeSideDto? = null,
    @SerializedName("night") val night: AiThemeSideDto? = null,
    @SerializedName("bg") val bg: String = "",
    @SerializedName("text") val text: String = "",
    @SerializedName("fg") val fg: String = "",
    @SerializedName("accent") val accent: String = "",
)

private data class AiThemeSideDto(
    @SerializedName("bg") val bg: String = "",
    @SerializedName("text") val text: String = "",
    @SerializedName("fg") val fg: String = "",
    @SerializedName("accent") val accent: String = "",
)

private fun extractJsonArray(raw: String): String? {
    val start = raw.indexOf('[')
    val end = raw.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return raw.substring(start, end + 1)
}

private fun systemLocaleForAiTheme(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return sysConfiguration.locales[0]
    }
    @Suppress("DEPRECATION")
    return sysConfiguration.locale
}
