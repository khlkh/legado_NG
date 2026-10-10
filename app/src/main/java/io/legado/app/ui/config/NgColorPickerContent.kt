package io.legado.app.ui.config

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorScheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Larger part of a golden-ratio split. The smaller part uses weight 1. */
private const val GOLDEN_LARGER = 1.618034f

/** Which reading color the live value replaces in the WCAG sample. */
internal enum class NgColorPreviewRole {
    TEXT,
    BACKGROUND,
    ACCENT,
}

/** Shared editor only. Its host owns dismissal, sampling, reset and confirmation. */
@Composable
internal fun NgColorPickerContent(
    state: NgColorPickerState,
    originalColor: Int,
    modifier: Modifier = Modifier,
    showAlphaSlider: Boolean = true,
    visualHeight: Dp = 168.dp,
    previewRole: NgColorPreviewRole? = null,
    previewBackground: Int = 0,
    previewForeground: Int = 0,
    previewAccent: Int = 0,
    onColorChanged: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = NgTheme.colors
    val hexLabel = stringResource(R.string.ng_color_picker_hex)
    val currentCallback by rememberUpdatedState(onColorChanged)
    val change: (() -> Unit) -> Unit = { edit ->
        val previous = state.color
        edit()
        if (state.isInputValid && previous != state.color) currentCallback(state.color)
    }
    val modeLabels = listOf(
        stringResource(R.string.ng_color_picker_mode_grid),
        stringResource(R.string.ng_color_picker_mode_spectrum),
        stringResource(R.string.ng_color_picker_mode_wheel),
        stringResource(R.string.ng_color_picker_mode_rgb),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (previewRole != null) Modifier.fillMaxHeight() else Modifier),
    ) {
        NgFlatActionRail(
            items = NgColorPickerMode.entries.mapIndexed { index, mode ->
                NgFlatActionRailItem(label = modeLabels[index], emphasized = state.mode == mode)
            },
            onItemClick = { index ->
                focusManager.clearFocus()
                keyboard?.hide()
                if (state.setMode(NgColorPickerMode.entries[index])) {
                    context.toastOnUi(R.string.ng_color_picker_unfinished)
                }
            },
            variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
        )
        Spacer(Modifier.height(10.dp))
        if (previewRole != null) {
            NgColorEditorBody(
                state = state,
                change = change,
                showAlphaSlider = showAlphaSlider,
                originalColor = originalColor,
                previewRole = previewRole,
                previewBackground = previewBackground,
                previewForeground = previewForeground,
                previewAccent = previewAccent,
                hexLabel = hexLabel,
                colors = colors,
            )
        } else {
            NgColorLooseBody(
                state = state,
                change = change,
                showAlphaSlider = showAlphaSlider,
                visualHeight = visualHeight,
                originalColor = originalColor,
                hexLabel = hexLabel,
                colors = colors,
            )
        }
    }
}

/** Editor page: the graphic fills leftover space. The sample bar, opacity, and saved colors stay one line each. */
@Composable
private fun NgColorEditorBody(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    showAlphaSlider: Boolean,
    originalColor: Int,
    previewRole: NgColorPreviewRole,
    previewBackground: Int,
    previewForeground: Int,
    previewAccent: Int,
    hexLabel: String,
    colors: NgColorScheme,
) {
    Column(Modifier.fillMaxSize()) {
        val graphicModifier = Modifier.fillMaxWidth().weight(1f)
        if (state.mode == NgColorPickerMode.RGB) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                NgPickerSliders(state, change, Modifier.fillMaxWidth())
                if (showAlphaSlider) {
                    Spacer(Modifier.height(4.dp))
                    NgOpacitySlider(state, change, colors)
                }
            }
        } else {
            when (state.mode) {
                NgColorPickerMode.GRID -> NgPickerGrid(state, change, graphicModifier)
                NgColorPickerMode.SPECTRUM -> NgPickerSpectrum(state, change, graphicModifier)
                NgColorPickerMode.WHEEL -> NgPickerWheel(state, change, graphicModifier)
                NgColorPickerMode.RGB -> Unit
            }
            if (showAlphaSlider) {
                Spacer(Modifier.height(6.dp))
                NgOpacitySlider(state, change, colors)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgColorContrastPreview(
                color = state.color,
                role = previewRole,
                background = previewBackground,
                foreground = previewForeground,
                accent = previewAccent,
                modifier = Modifier.weight(GOLDEN_LARGER).fillMaxHeight(),
            )
            Spacer(Modifier.width(16.dp))
            NgContrastColorBox(
                originalColor = originalColor,
                selectedColor = state.color,
                onRestoreOriginal = { change { state.setColor(originalColor) } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(16.dp))
            NgPickerHexField(
                label = hexLabel,
                value = state.hexInput,
                isError = state.isHexInputError,
                onValueChange = { text ->
                    if (text.length <= 9) change { state.editHex(text) }
                },
                modifier = Modifier.weight(GOLDEN_LARGER),
            )
        }
        if (!state.isInputValid) {
            Text(
                stringResource(R.string.ng_color_picker_input_error),
                modifier = Modifier.padding(top = 2.dp),
                color = Color(colors.error),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        NgPickerSavedColors(state, change)
    }
}

/** Sheets without a reading-color sample keep the graphic at its own height. */
@Composable
private fun NgColorLooseBody(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    showAlphaSlider: Boolean,
    visualHeight: Dp,
    originalColor: Int,
    hexLabel: String,
    colors: NgColorScheme,
) {
    when (state.mode) {
        NgColorPickerMode.GRID -> NgPickerGrid(state, change, Modifier.fillMaxWidth().height(visualHeight))
        NgColorPickerMode.SPECTRUM -> NgPickerSpectrum(state, change, Modifier.fillMaxWidth().height(visualHeight))
        NgColorPickerMode.WHEEL -> NgPickerWheel(state, change, Modifier.fillMaxWidth().height(visualHeight))
        NgColorPickerMode.RGB -> NgPickerSliders(
            state,
            change,
            Modifier.fillMaxWidth().height(visualHeight).verticalScroll(rememberScrollState()),
        )
    }
    Spacer(Modifier.height(10.dp))
    if (showAlphaSlider) {
        NgOpacitySlider(state, change, colors)
        Spacer(Modifier.height(12.dp))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NgPickerPreview(
            color = originalColor,
            label = stringResource(R.string.ng_color_picker_original),
            modifier = Modifier
                .size(width = 48.dp, height = 44.dp)
                .clip(RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.ng_color_picker_restore_original),
                ) { change { state.setColor(originalColor) } },
        )
        NgPickerPreview(
            color = state.color,
            label = stringResource(R.string.ng_color_picker_current),
            modifier = Modifier
                .size(width = 48.dp, height = 44.dp)
                .clip(RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp)),
        )
        Spacer(Modifier.width(14.dp))
        NgPickerHexField(
            label = hexLabel,
            value = state.hexInput,
            isError = state.isHexInputError,
            onValueChange = { text ->
                if (text.length <= 9) change { state.editHex(text) }
            },
            modifier = Modifier.weight(1f),
        )
    }
    if (!state.isInputValid) {
        Text(
            stringResource(R.string.ng_color_picker_input_error),
            modifier = Modifier.padding(top = 5.dp),
            color = Color(colors.error),
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
    Spacer(Modifier.height(16.dp))
    NgPickerSavedColors(state, change)
}

@Composable
private fun NgOpacitySlider(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    colors: NgColorScheme,
) {
    val percent = "${(state.alpha / 255f * 100).roundToInt()}%"
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.ng_color_picker_opacity),
            color = Color(colors.onSurfaceVariant),
            fontSize = 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        NgPickerSlider(
            value = state.alpha.toFloat(),
            max = 255f,
            description = stringResource(R.string.ng_color_picker_opacity),
            valueDescription = percent,
            gradient = listOf(
                Color(state.color or (0xFF shl 24)).copy(alpha = 0f),
                Color(state.color or (0xFF shl 24)),
            ),
            checkerboard = true,
            trackHeight = 28.dp,
            modifier = Modifier.weight(1f),
            onValueChange = { change { state.setAlpha(it.roundToInt()) } },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            percent,
            modifier = Modifier.width(40.dp),
            color = Color(colors.onSurface),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun NgContrastColorBox(
    originalColor: Int,
    selectedColor: Int,
    onRestoreOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .border(1.dp, Color(NgTheme.colors.outlineVariant), shape),
    ) {
        NgPickerPreview(
            color = originalColor,
            label = stringResource(R.string.ng_color_picker_original),
            embeddedLabel = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.ng_color_picker_restore_original),
                ) { onRestoreOriginal() },
        )
        NgPickerPreview(
            color = selectedColor,
            label = stringResource(R.string.ng_color_picker_current),
            embeddedLabel = true,
            modifier = Modifier.weight(GOLDEN_LARGER).fillMaxHeight(),
        )
    }
}

@Composable
private fun NgColorContrastPreview(
    color: Int,
    role: NgColorPreviewRole,
    background: Int,
    foreground: Int,
    accent: Int,
    modifier: Modifier = Modifier,
) {
    val previewBg = if (role == NgColorPreviewRole.BACKGROUND) color else background
    val previewFg = if (role == NgColorPreviewRole.BACKGROUND) foreground else color
    val previewMark = if (role == NgColorPreviewRole.ACCENT) color else accent
    val sample = stringResource(R.string.ng_paper_preview_sample)
    val splitIndex = sample.indexOfFirst { it == '，' || it == ',' }.takeIf { it >= 0 } ?: (sample.length / 2)
    val plain = sample.substring(0, splitIndex).trimEnd(',', '，', ' ')
    val marked = sample.substring(splitIndex).trimStart(',', '，', ' ')
    val ratioLabel = NgColorMath.wcagContrastLabel(NgColorMath.displayedContrast(previewFg, previewBg))
    val shape = RoundedCornerShape(14.dp)
    val measurer = rememberTextMeasurer()
    val fontFamily = NgTheme.fontFamily
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Color(NgColorMath.opaque(previewBg)))
            .border(1.dp, Color(NgTheme.colors.outlineVariant), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        val sizes = contrastFontSizes(
            measurer = measurer,
            fontFamily = fontFamily,
            plain = plain,
            marked = marked,
            grade = ratioLabel,
            maxWidthPx = constraints.maxWidth,
            maxHeightPx = constraints.maxHeight,
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plain,
                    modifier = Modifier.weight(1f),
                    color = Color(previewFg),
                    style = tightTextStyle(sizes.first.value, fontFamily),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = marked,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(NgColorMath.opaque(previewMark)))
                        .padding(horizontal = 2.dp),
                    color = Color(previewFg),
                    style = tightTextStyle(sizes.second.value, fontFamily),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            Text(
                text = ratioLabel,
                color = Color(previewFg).copy(alpha = 0.72f),
                style = tightTextStyle(sizes.third.value, fontFamily),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun tightTextStyle(sizeSp: Float, fontFamily: FontFamily?): TextStyle = TextStyle(
    fontSize = sizeSp.sp,
    lineHeight = sizeSp.sp,
    fontFamily = fontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Shrink the sample and the WCAG line together until both fit the text box. */
private fun contrastFontSizes(
    measurer: TextMeasurer,
    fontFamily: FontFamily?,
    plain: String,
    marked: String,
    grade: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Triple<TextUnit, TextUnit, TextUnit> {
    val width = maxWidthPx.coerceAtLeast(1)
    val height = maxHeightPx.coerceAtLeast(1)
    val half = (width / 2).coerceAtLeast(1)
    var sample = 14f
    var gradeSize = 11f
    repeat(12) {
        val sampleStyle = tightTextStyle(sample, fontFamily)
        val gradeStyle = tightTextStyle(gradeSize, fontFamily)
        val plainPx = measurer.measure(plain, sampleStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip).size
        val markedPx = measurer.measure(marked, sampleStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip).size
        val gradePx = measurer.measure(grade, gradeStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip).size
        val fits = plainPx.width <= half && markedPx.width <= half && gradePx.width <= width &&
            maxOf(plainPx.height, markedPx.height) + gradePx.height <= height
        if (fits || sample <= 8f) return Triple(sample.sp, sample.sp, gradeSize.sp)
        sample -= 0.5f
        gradeSize = (sample * 11f / 14f).coerceAtLeast(7f)
    }
    return Triple(sample.sp, sample.sp, gradeSize.sp)
}

@Composable
private fun NgPickerHexField(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(colors.onSurfaceVariant), fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        NgFormField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            isError = isError,
            variant = NgFormFieldVariant.DIALOG_UNDERLINE,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboard?.hide()
            }),
        )
    }
}

@Composable
private fun NgPickerGrid(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var familyId by rememberSaveable { mutableStateOf(NgSwatchFamilies.SPECTRUM_ID) }
    val palette = remember(familyId, state.alpha) { NgSwatchFamilies.cells(familyId, state.alpha).toList() }
    val selectedIndex = palette.indexOfFirst { (it and 0x00FFFFFF) == (state.color and 0x00FFFFFF) }
    val description = stringResource(R.string.ng_color_picker_mode_grid)
    val border = Color(NgTheme.colors.outlineVariant)
    val columns = NgSwatchFamilies.COLS
    val rows = NgSwatchFamilies.ROWS
    fun select(index: Int) {
        val cell = palette[index.coerceIn(0, palette.lastIndex)]
        change { state.setColor((cell and 0x00FFFFFF) or (state.alpha shl 24)) }
    }
    Column(modifier) {
        NgSwatchFamilyBar(familyId) { familyId = it }
        Spacer(Modifier.height(6.dp))
        NgPickerGridCanvas(
            palette = palette,
            selectedIndex = selectedIndex,
            columns = columns,
            rows = rows,
            description = description,
            colorLabel = NgColorPickerColors.format(state.color),
            border = border,
            onSelect = ::select,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun NgSwatchFamilyBar(selectedId: String, onSelected: (String) -> Unit) {
    val colors = NgTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NgSwatchFamilies.families.forEach { family ->
            val selected = family.id == selectedId
            Text(
                text = stringResource(family.labelRes),
                color = Color(if (selected) colors.onPrimary else colors.onSurface),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(if (selected) colors.primary else colors.surfaceVariant))
                    .clickable(role = Role.Tab) { onSelected(family.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NgPickerGridCanvas(
    palette: List<Int>,
    selectedIndex: Int,
    columns: Int,
    rows: Int,
    description: String,
    colorLabel: String,
    border: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .pickerGesture { position, size ->
                if (size.width > 0 && size.height > 0) {
                    val column = floor(position.x / size.width * columns).toInt().coerceIn(0, columns - 1)
                    val row = floor(position.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                    onSelect(row * columns + column)
                }
            }
            .semantics {
                contentDescription = description
                stateDescription = colorLabel
                val last = (palette.size - 1).coerceAtLeast(0).toFloat()
                progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.coerceAtLeast(0).toFloat(), 0f..last, (palette.size - 2).coerceAtLeast(0))
                setProgress { value ->
                    if (value.isFinite()) {
                        onSelect(value.roundToInt())
                        true
                    } else false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else {
                    val step = when (event.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        Key.DirectionUp -> -columns
                        Key.DirectionDown -> columns
                        else -> 0
                    }
                    if (step != 0) onSelect(selectedIndex.coerceAtLeast(0) + step)
                    step != 0
                }
            }
            .focusable(),
    ) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        palette.forEachIndexed { index, color ->
            drawRect(
                Color(color),
                topLeft = Offset(index % columns * cellWidth, index / columns * cellHeight),
                size = Size(cellWidth + 0.5f, cellHeight + 0.5f),
            )
        }
        if (selectedIndex >= 0) {
            val row = selectedIndex / columns
            val column = selectedIndex % columns
            val inset = 3.dp.toPx()
            val position = Offset(column * cellWidth + inset, row * cellHeight + inset)
            val cellSize = Size((cellWidth - 2 * inset).coerceAtLeast(0f), (cellHeight - 2 * inset).coerceAtLeast(0f))
            val innerCorner = CornerRadius(2.dp.toPx())
            val outerCorner = CornerRadius((12.dp.toPx() - inset).coerceAtLeast(0f))
            val outline = Path().apply {
                addRoundRect(RoundRect(
                    left = position.x,
                    top = position.y,
                    right = position.x + cellSize.width,
                    bottom = position.y + cellSize.height,
                    topLeftCornerRadius = if (row == 0 && column == 0) outerCorner else innerCorner,
                    topRightCornerRadius = if (row == 0 && column == columns - 1) outerCorner else innerCorner,
                    bottomRightCornerRadius = if (row == rows - 1 && column == columns - 1) outerCorner else innerCorner,
                    bottomLeftCornerRadius = if (row == rows - 1 && column == 0) outerCorner else innerCorner,
                ))
            }
            drawPath(outline, Color.Black.copy(alpha = 0.65f), style = Stroke(4.dp.toPx()))
            drawPath(outline, Color.White, style = Stroke(2.dp.toPx()))
        }
        drawRoundRect(border.copy(alpha = 0.4f), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun NgPickerSpectrum(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 13dp outer ring + half its 2dp stroke, plus one physical pixel for antialiasing.
    val cursorMargin = with(LocalDensity.current) { 14.dp.toPx() } + 1f
    fun inset(width: Float, height: Float) = minOf(cursorMargin, width / 2f, height / 2f)
    val hueColor = Color(NgColorPickerColors.hsvToColor(state.hue, 1f, 1f))
    val description = stringResource(R.string.ng_color_picker_mode_spectrum)
    val valueDescription = stringResource(
        R.string.ng_color_picker_spectrum_description,
        (state.saturation * 100).roundToInt(),
        (state.value * 100).roundToInt(),
    )
    val increaseSaturation = stringResource(R.string.ng_color_picker_increase_saturation)
    val decreaseSaturation = stringResource(R.string.ng_color_picker_decrease_saturation)
    val increaseBrightness = stringResource(R.string.ng_color_picker_increase_brightness)
    val decreaseBrightness = stringResource(R.string.ng_color_picker_decrease_brightness)
    fun move(saturation: Float = state.saturation, value: Float = state.value): Boolean {
        change { state.setSaturationValue(saturation, value) }
        return true
    }
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .pickerGesture { position, size ->
                    if (size.width > 0 && size.height > 0) {
                        val margin = inset(size.width.toFloat(), size.height.toFloat())
                        move(
                            (position.x - margin) / (size.width - margin * 2f).coerceAtLeast(1f),
                            1f - (position.y - margin) / (size.height - margin * 2f).coerceAtLeast(1f),
                        )
                    }
                }
                .semantics {
                    contentDescription = description
                    stateDescription = valueDescription
                    customActions = listOf(
                        CustomAccessibilityAction(increaseSaturation) { move(saturation = state.saturation + 0.05f) },
                        CustomAccessibilityAction(decreaseSaturation) { move(saturation = state.saturation - 0.05f) },
                        CustomAccessibilityAction(increaseBrightness) { move(value = state.value + 0.05f) },
                        CustomAccessibilityAction(decreaseBrightness) { move(value = state.value - 0.05f) },
                    )
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft -> move(saturation = state.saturation - 0.01f)
                        Key.DirectionRight -> move(saturation = state.saturation + 0.01f)
                        Key.DirectionUp -> move(value = state.value + 0.01f)
                        Key.DirectionDown -> move(value = state.value - 0.01f)
                        else -> false
                    }
                }
                .focusable(),
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val margin = inset(size.width, size.height)
            val colorWidth = (size.width - margin * 2f).coerceAtLeast(1f)
            val colorHeight = (size.height - margin * 2f).coerceAtLeast(1f)
            val panel = Path().apply {
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(12.dp.toPx())))
            }
            clipPath(panel) {
                drawRect(Brush.horizontalGradient(
                    listOf(Color.White, hueColor), startX = margin, endX = margin + colorWidth,
                ))
                drawRect(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black), startY = margin, endY = margin + colorHeight,
                ))
            }
            // Use the same bounds for the gradient, pointer mapping and cursor: even at white /
            // black corners the visible center still identifies the selected color precisely.
            val cursor = Offset(
                (margin + state.saturation * colorWidth).coerceAtMost(size.width - margin),
                (margin + (1f - state.value) * colorHeight).coerceAtMost(size.height - margin),
            )
            val cursorScale = margin / cursorMargin
            drawCircle(Color.Black.copy(alpha = 0.35f), 13.dp.toPx() * cursorScale, cursor, style = Stroke(2.dp.toPx() * cursorScale))
            drawCircle(Color.White, 11.dp.toPx() * cursorScale, cursor, style = Stroke(3.dp.toPx() * cursorScale))
        }
        Spacer(Modifier.height(10.dp))
        NgPickerSlider(
            value = state.hue,
            max = 360f,
            description = stringResource(R.string.ng_color_picker_hue),
            valueDescription = "${state.hue.roundToInt()}°",
            gradient = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
            onValueChange = { change { state.setHue(it) } },
        )
    }
}

@Composable
private fun NgPickerWheel(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color(NgColorPickerColors.hsvToColor(state.hue, 1f, 1f))
    val surface = Color(NgTheme.colors.surface)
    val outline = Color(NgTheme.colors.outline)
    Canvas(
        modifier.pickerGesture { position, size ->
            if (size.width <= 0 || size.height <= 0) return@pickerGesture
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minSide = minOf(size.width, size.height).toFloat()
            val outer = minSide * 0.48f
            val inner = minSide * 0.32f
            val dx = position.x - cx
            val dy = position.y - cy
            val distance = hypot(dx, dy)
            if (distance in inner..outer) {
                var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (degrees < 0f) degrees += 360f
                change { state.setHue(degrees) }
            } else {
                val box = inner * 1.25f
                val left = cx - box / 2f
                val top = cy - box / 2f
                change {
                    state.setSaturationValue(
                        (position.x - left) / box,
                        1f - (position.y - top) / box,
                    )
                }
            }
        },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minSide = minOf(size.width, size.height)
        val outer = minSide * 0.48f
        val inner = minSide * 0.32f
        val hues = List(13) { index -> Color(NgColorPickerColors.hsvToColor(index * 30f, 1f, 1f)) }
        drawCircle(brush = Brush.sweepGradient(hues), radius = outer, center = Offset(cx, cy))
        drawCircle(color = surface, radius = inner, center = Offset(cx, cy))
        val box = inner * 1.25f
        val left = cx - box / 2f
        val top = cy - box / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
            topLeft = Offset(left, top),
            size = Size(box, box),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = Offset(left, top),
            size = Size(box, box),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        val handle = Offset(left + state.saturation * box, top + (1f - state.value) * box)
        drawCircle(Color.White, 7.dp.toPx(), handle)
        drawCircle(outline, 7.dp.toPx(), handle, style = Stroke(1.5.dp.toPx()))
        val ringAngle = Math.toRadians(state.hue.toDouble())
        val ringPoint = Offset(
            cx + cos(ringAngle).toFloat() * ((inner + outer) / 2f),
            cy + sin(ringAngle).toFloat() * ((inner + outer) / 2f),
        )
        drawCircle(Color.White, 6.dp.toPx(), ringPoint)
        drawCircle(outline, 6.dp.toPx(), ringPoint, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun NgPickerSliders(
    state: NgColorPickerState,
    change: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hsv by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxWidth()) {
        NgFlatActionRail(
            items = listOf(
                NgFlatActionRailItem(label = stringResource(R.string.ng_color_picker_hsv), emphasized = hsv),
                NgFlatActionRailItem(label = stringResource(R.string.ng_color_picker_rgb), emphasized = !hsv),
            ),
            onItemClick = { index -> hsv = index == 0 },
            variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
        )
        Spacer(Modifier.height(8.dp))
        if (hsv) NgPickerHsv(state, change) else NgPickerRgb(state, change)
    }
}

@Composable
private fun NgPickerHsv(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    var hueText by remember { mutableStateOf<String?>(null) }
    var satText by remember { mutableStateOf<String?>(null) }
    var valueText by remember { mutableStateOf<String?>(null) }
    val hueDraft = hueText
    val satDraft = satText
    val valueDraft = valueText
    val hueShown = state.hue.roundToInt().toString()
    val satShown = (state.saturation * 100).roundToInt().toString()
    val valueShown = (state.value * 100).roundToInt().toString()
    val hueColor = NgColorPickerColors.hsvToColor(state.hue, 1f, 1f)
    NgPickerNumberRow(
        label = stringResource(R.string.ng_color_picker_hue),
        text = hueDraft ?: hueShown,
        isError = hueDraft != null && hueDraft.toIntOrNull() !in 0..360,
        onText = { text ->
            hueText = text
            val parsed = text.toIntOrNull()
            if (parsed != null && parsed in 0..360) {
                change { state.setHue(parsed.toFloat()) }
                hueText = null
            }
        },
        sliderValue = state.hue,
        sliderMax = 360f,
        gradient = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
        onSlider = {
            hueText = null
            change { state.setHue(it) }
        },
    )
    Spacer(Modifier.height(4.dp))
    NgPickerNumberRow(
        label = stringResource(R.string.ng_color_picker_saturation),
        text = satDraft ?: satShown,
        isError = satDraft != null && satDraft.toIntOrNull() !in 0..100,
        onText = { text ->
            satText = text
            val parsed = text.toIntOrNull()
            if (parsed != null && parsed in 0..100) {
                change { state.setSaturationValue(parsed / 100f, state.value) }
                satText = null
            }
        },
        sliderValue = state.saturation * 100f,
        sliderMax = 100f,
        gradient = listOf(
            Color(NgColorPickerColors.hsvToColor(state.hue, 0f, state.value)),
            Color(NgColorPickerColors.hsvToColor(state.hue, 1f, state.value)),
        ),
        onSlider = {
            satText = null
            change { state.setSaturationValue(it / 100f, state.value) }
        },
    )
    Spacer(Modifier.height(4.dp))
    NgPickerNumberRow(
        label = stringResource(R.string.ng_color_picker_value),
        text = valueDraft ?: valueShown,
        isError = valueDraft != null && valueDraft.toIntOrNull() !in 0..100,
        onText = { text ->
            valueText = text
            val parsed = text.toIntOrNull()
            if (parsed != null && parsed in 0..100) {
                change { state.setSaturationValue(state.saturation, parsed / 100f) }
                valueText = null
            }
        },
        sliderValue = state.value * 100f,
        sliderMax = 100f,
        gradient = listOf(Color.Black, Color(hueColor)),
        onSlider = {
            valueText = null
            change { state.setSaturationValue(state.saturation, it / 100f) }
        },
    )
}

@Composable
private fun NgPickerRgb(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    val labels = listOf(
        stringResource(R.string.ng_color_picker_red),
        stringResource(R.string.ng_color_picker_green),
        stringResource(R.string.ng_color_picker_blue),
    )
    val values = listOf(state.red, state.green, state.blue)
    labels.forEachIndexed { channel, label ->
        if (channel > 0) Spacer(Modifier.height(4.dp))
        val shift = (2 - channel) * 8
        val start = (state.color or (0xFF shl 24)) and (0xFF shl shift).inv()
        NgPickerNumberRow(
            label = label,
            text = state.rgbInputs[channel],
            isError = state.rgbInputErrors[channel],
            onText = { text ->
                if (text.length <= 3) change { state.editRgbChannel(channel, text) }
            },
            sliderValue = values[channel].toFloat(),
            sliderMax = 255f,
            gradient = listOf(Color(start), Color(start or (0xFF shl shift))),
            onSlider = { change { state.setRgbChannel(channel, it.roundToInt()) } },
        )
    }
}

@Composable
private fun NgPickerNumberRow(
    label: String,
    text: String,
    isError: Boolean,
    onText: (String) -> Unit,
    sliderValue: Float,
    sliderMax: Float,
    gradient: List<Color>,
    onSlider: (Float) -> Unit,
) {
    val colors = NgTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(52.dp),
            color = Color(colors.onSurface),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_reduce,
            contentDescription = stringResource(R.string.reduce),
            enabled = sliderValue > 0.5f,
            onClick = { onSlider((sliderValue - 1f).coerceAtLeast(0f)) },
        )
        NgPickerSlider(
            value = sliderValue,
            max = sliderMax,
            description = label,
            valueDescription = text,
            gradient = gradient,
            trackHeight = 32.dp,
            modifier = Modifier.weight(1f),
            onValueChange = onSlider,
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_add,
            contentDescription = stringResource(R.string.add),
            enabled = sliderValue < sliderMax - 0.5f,
            onClick = { onSlider((sliderValue + 1f).coerceAtMost(sliderMax)) },
        )
        BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 3 && it.all { char -> char in '0'..'9' }) onText(it) },
            modifier = Modifier.width(52.dp).height(32.dp).semantics { contentDescription = label },
            singleLine = true,
            textStyle = TextStyle(
                color = Color(if (isError) colors.error else colors.onSurface),
                fontSize = 15.sp,
                fontFamily = NgTheme.fontFamily,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(Color(colors.primary)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color(colors.inputContainer), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(if (isError) colors.error else colors.outlineVariant), RoundedCornerShape(8.dp))
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center,
                ) { innerTextField() }
            },
        )
    }
}

@Composable
private fun NgPickerSlider(
    value: Float,
    max: Float,
    description: String,
    valueDescription: String,
    gradient: List<Color>,
    checkerboard: Boolean = false,
    trackHeight: Dp = 44.dp,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onValueChange)
    val outline = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.4f)
    Canvas(
        modifier.fillMaxWidth().height(trackHeight)
            .pickerGesture { position, size ->
                // Match the visible thumb travel, including its radius at both ends.
                val radius = size.height * 13f / 44f
                val fraction = ((position.x - radius) / (size.width - radius * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                currentCallback(fraction * max)
            }
            .semantics {
                contentDescription = description
                stateDescription = valueDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, max), 0f..max)
                setProgress { next ->
                    if (next.isFinite()) {
                        currentCallback(next.coerceIn(0f, max))
                        true
                    } else false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> { currentCallback((value - 1f).coerceAtLeast(0f)); true }
                    Key.DirectionRight, Key.DirectionUp -> { currentCallback((value + 1f).coerceAtMost(max)); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val trackHeight = 24.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(0f, trackTop, size.width, trackTop + trackHeight), CornerRadius(trackHeight / 2)))
        }
        val radius = 13.dp.toPx()
        clipPath(path) {
            if (checkerboard) drawPickerCheckerboard()
            drawRect(
                Brush.horizontalGradient(gradient, startX = radius, endX = (size.width - radius).coerceAtLeast(radius + 1f)),
                Offset(0f, trackTop),
                Size(size.width, trackHeight),
            )
        }
        drawRoundRect(outline, Offset(0f, trackTop), Size(size.width, trackHeight), CornerRadius(trackHeight / 2), style = Stroke(1.dp.toPx()))
        val thumb = Offset(radius + (size.width - radius * 2).coerceAtLeast(0f) * (value / max), size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius + 1.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius - 1.dp.toPx(), thumb, style = Stroke(3.dp.toPx()))
    }
}

/** One gesture coroutine survives every color/frame update; it reads the newest callback. */
@Composable
private fun Modifier.pickerGesture(onPosition: (Offset, IntSize) -> Unit): Modifier {
    val currentCallback by rememberUpdatedState(onPosition)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            currentCallback(down.position, size)
            do {
                val event = awaitPointerEvent()
                val pointer = event.changes.firstOrNull { it.id == down.id }
                if (pointer?.pressed == true) currentCallback(pointer.position, size)
                pointer?.consume()
            } while (pointer?.pressed == true)
        }
    }
}

@Composable
private fun NgPickerPreview(
    color: Int,
    label: String,
    modifier: Modifier = Modifier,
    embeddedLabel: Boolean = false,
) {
    val ink = if (embeddedLabel) highContrastInk(color) else Color.White
    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label ${NgColorPickerColors.format(color)}"
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPickerCheckerboard()
            drawRect(Color(color))
        }
        Text(
            label,
            modifier = if (embeddedLabel) {
                Modifier.align(Alignment.Center).padding(horizontal = 2.dp)
            } else {
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.3f))
            },
            color = ink,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (embeddedLabel) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** White or black, whichever reads more clearly on this swatch. */
private fun highContrastInk(color: Int): Color {
    val surface = NgColorMath.opaque(color)
    val white = NgColorMath.displayedContrast(0xFFFFFFFF.toInt(), surface)
    val black = NgColorMath.displayedContrast(0xFF000000.toInt(), surface)
    return if (white >= black) Color.White else Color.Black
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NgPickerSavedColors(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val colors = NgTheme.colors
    var savedColors by remember(context) { mutableStateOf(NgSavedColors.load(context)) }
    var deletingColor by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                stringResource(R.string.ng_color_picker_saved_colors),
                color = Color(colors.onSurfaceVariant),
                style = tightTextStyle(13f, NgTheme.fontFamily).copy(color = Color(colors.onSurfaceVariant)),
                maxLines = 1,
            )
            Text(
                stringResource(R.string.ng_color_picker_long_press_delete),
                color = Color(colors.onSurfaceVariant),
                style = tightTextStyle(9f, NgTheme.fontFamily).copy(color = Color(colors.onSurfaceVariant)),
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        savedColors.forEach { color ->
            val description = stringResource(R.string.ng_color_picker_saved_color_description, NgColorPickerColors.format(color))
            val selected = state.color == color
            Box(
                modifier = Modifier.size(32.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { change { state.setColor(color) } },
                        onLongClickLabel = stringResource(R.string.ng_color_picker_delete_saved),
                        onLongClick = { deletingColor = color },
                    )
                    .semantics {
                        contentDescription = description
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier.size(26.dp).clip(CircleShape)
                        .border(1.dp, Color(colors.outlineVariant).copy(alpha = 0.5f), CircleShape),
                ) {
                    drawPickerCheckerboard()
                    drawRect(Color(color))
                }
                if (selected) {
                    Box(Modifier.size(30.dp).border(1.5.dp, Color(colors.primary), CircleShape))
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                }
            }
        }
        Box(
            modifier = Modifier.size(32.dp)
                .clickable(enabled = state.isInputValid, role = Role.Button) {
                    val result = NgSavedColors.add(context, state.color)
                    savedColors = NgSavedColors.load(context)
                    context.toastOnUi(when (result) {
                        NgSavedColors.AddResult.ADDED -> R.string.ng_color_picker_saved_added
                        NgSavedColors.AddResult.ALREADY_SAVED -> R.string.ng_color_picker_already_saved
                        NgSavedColors.AddResult.FULL -> R.string.ng_color_picker_saved_full
                    })
                }
                .semantics { contentDescription = context.getString(R.string.ng_color_picker_add_saved) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF1F1F23).copy(alpha = if (state.isInputValid) 1f else 0.4f),
                )
            }
        }
        }
    }
    deletingColor?.let { color ->
        Dialog(onDismissRequest = { deletingColor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            NgDialog(
                title = stringResource(R.string.ng_color_picker_delete_saved),
                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp),
                variant = NgDialogVariant.COMPACT_CONFIRMATION,
                actions = {
                    NgDialogTextActionButton(stringResource(R.string.cancel), onClick = { deletingColor = null }, secondary = true)
                    NgDialogTextActionButton(stringResource(R.string.delete), onClick = {
                        savedColors = NgSavedColors.remove(context, color)
                        deletingColor = null
                        context.toastOnUi(R.string.ng_color_picker_saved_deleted)
                    }, danger = true)
                },
            ) {
                Text(NgColorPickerColors.format(color), color = Color(colors.onSurface), fontSize = 16.sp)
            }
        }
    }
}

private fun DrawScope.drawPickerCheckerboard() {
    val edge = 6.dp.toPx()
    val columns = (size.width / edge).toInt() + 1
    val rows = (size.height / edge).toInt() + 1
    repeat(rows) { row ->
        repeat(columns) { column ->
            drawRect(
                if ((row + column) % 2 == 0) Color.White else Color(0xFFD9DED7),
                Offset(column * edge, row * edge),
                Size(edge, edge),
            )
        }
    }
}
