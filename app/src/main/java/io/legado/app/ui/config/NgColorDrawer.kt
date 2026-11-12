package io.legado.app.ui.config

/** How the color drawer spends the space under the screen-height cap. */
internal data class ColorDrawerMeasure(
    val visualPx: Int,
    val scroll: Boolean,
    val heightPx: Int,
)

internal fun colorDrawerHeightFraction(portrait: Boolean): Float = if (portrait) 0.5f else 0.7f

/**
 * Reading preset color editing hides the session bar and needs enough room for
 * a 1.5-inch swatch matrix. Short/landscape screens scroll rather than shrink it.
 */
internal fun colorDrawerExpandedHeightFraction(portrait: Boolean): Float =
    if (portrait) 0.78f else 0.92f

/**
 * A fixed graphic keeps the swatch matrix at its target size. The chrome scrolls
 * inside the drawer cap when it cannot fit, instead of squeezing the graphic.
 */
internal fun measureFixedVisualDrawer(
    capPx: Int,
    headerPx: Int,
    naturalBodyPx: Int,
    fixedVisualPx: Int,
): ColorDrawerMeasure {
    val cap = capPx.coerceAtLeast(1)
    val natural = headerPx + naturalBodyPx
    return if (natural <= cap) {
        ColorDrawerMeasure(fixedVisualPx, scroll = false, heightPx = natural)
    } else {
        ColorDrawerMeasure(fixedVisualPx, scroll = true, heightPx = cap)
    }
}

/**
 * Shrink the grid, spectrum, wheel, or slider block until WCAG and HEX fit.
 * When even the minimum graphic does not fit, scroll inside the cap instead of clipping.
 * Pass graphic = false to keep a block at its natural height and scroll the whole drawer.
 */
internal fun measureColorDrawer(
    capPx: Int,
    headerPx: Int,
    naturalBodyPx: Int,
    preferredVisualPx: Int,
    minVisualPx: Int,
    graphic: Boolean,
): ColorDrawerMeasure {
    val cap = capPx.coerceAtLeast(1)
    val natural = headerPx + naturalBodyPx
    if (natural <= cap) {
        return ColorDrawerMeasure(preferredVisualPx, scroll = false, heightPx = natural)
    }
    if (!graphic) {
        return ColorDrawerMeasure(preferredVisualPx, scroll = true, heightPx = cap)
    }
    val chrome = (naturalBodyPx - preferredVisualPx).coerceAtLeast(0)
    val room = cap - headerPx - chrome
    return if (room >= minVisualPx) {
        ColorDrawerMeasure(room, scroll = false, heightPx = headerPx + chrome + room)
    } else {
        ColorDrawerMeasure(minVisualPx, scroll = true, heightPx = cap)
    }
}
