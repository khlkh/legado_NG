package io.legado.app.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.annotation.WordAnnotationConfig
import io.legado.app.help.annotation.WordAnnotationLevels
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem

/**
 * Word Wise settings sheet: model, five-level vocabulary slider, and the
 * prefetch-chapters window. The per-book on/off switch stays in the reader.
 */
@Composable
internal fun WordAnnotationSettingsSheet(
    level: Int,
    hintCap: Int,
    prefetchChapters: Int,
    modelSummary: String,
    bookActive: Boolean,
    onSelectModel: () -> Unit,
    onLevelSelected: (Int) -> Unit,
    onHintCapSelected: (Int) -> Unit,
    onPrefetchSelected: (Int) -> Unit,
) {
    var showLevelPicker by rememberSaveable { mutableStateOf(false) }
    var showHintCapPicker by rememberSaveable { mutableStateOf(false) }
    var showPrefetchPicker by rememberSaveable { mutableStateOf(false) }
    val normalizedLevel = WordAnnotationLevels.normalize(level)
    val normalizedCap = hintCap.coerceIn(
        WordAnnotationConfig.MIN_WORD_ANNOTATION_HINT_CAP,
        WordAnnotationConfig.MAX_WORD_ANNOTATION_HINT_CAP,
    )

    NgSettingsGroup {
        NgSettingsItem(
            title = stringResource(R.string.ai_model_select),
            summary = modelSummary,
            onClick = onSelectModel,
        )
        NgSettingsItem(
            title = stringResource(R.string.ai_word_annotation_level),
            summary = stringResource(
                R.string.ai_word_annotation_level_summary,
                WordAnnotationLevels.floorFor(normalizedLevel),
                normalizedCap,
            ),
            enabled = bookActive,
            onClick = { showLevelPicker = true },
        )
        NgSettingsItem(
            title = stringResource(R.string.ai_word_annotation_hint_cap),
            summary = normalizedCap.toString(),
            enabled = bookActive,
            onClick = { showHintCapPicker = true },
        )
        NgSettingsItem(
            title = stringResource(R.string.ai_word_annotation_prefetch_chapters),
            summary = if (prefetchChapters <= 0) {
                stringResource(R.string.ai_word_annotation_prefetch_off)
            } else {
                stringResource(R.string.ai_word_annotation_prefetch_summary, prefetchChapters)
            },
            onClick = { showPrefetchPicker = true },
        )
    }

    if (showLevelPicker) {
        ConfigChoiceDialog(
            title = stringResource(R.string.ai_word_annotation_level),
            options = (WordAnnotationConfig.MIN_WORD_ANNOTATION_LEVEL..
                WordAnnotationConfig.MAX_WORD_ANNOTATION_LEVEL).map { value ->
                ConfigChoiceOption(
                    label = stringResource(levelLabelRes(value)),
                    value = value.toString(),
                )
            },
            selectedValue = normalizedLevel.toString(),
            onDismissRequest = { showLevelPicker = false },
            onSelected = { value ->
                showLevelPicker = false
                value.toIntOrNull()?.let(onLevelSelected)
            },
        )
    }
    if (showHintCapPicker) {
        ConfigChoiceDialog(
            title = stringResource(R.string.ai_word_annotation_hint_cap),
            options = listOf(5, 8, 13, 21, 34, 55).map { value ->
                ConfigChoiceOption(label = value.toString(), value = value.toString())
            },
            selectedValue = normalizedCap.toString(),
            onDismissRequest = { showHintCapPicker = false },
            onSelected = { value ->
                showHintCapPicker = false
                value.toIntOrNull()?.let(onHintCapSelected)
            },
        )
    }
    if (showPrefetchPicker) {
        ConfigChoiceDialog(
            title = stringResource(R.string.ai_word_annotation_prefetch_chapters),
            options = (0..3).map { value ->
                ConfigChoiceOption(
                    label = if (value == 0) {
                        stringResource(R.string.ai_word_annotation_prefetch_off)
                    } else {
                        stringResource(R.string.ai_word_annotation_prefetch_summary, value)
                    },
                    value = value.toString(),
                )
            },
            selectedValue = prefetchChapters.coerceIn(0, 3).toString(),
            onDismissRequest = { showPrefetchPicker = false },
            onSelected = { value ->
                showPrefetchPicker = false
                value.toIntOrNull()?.let(onPrefetchSelected)
            },
        )
    }
}

private fun levelLabelRes(level: Int): Int = when (level) {
    1 -> R.string.ai_word_annotation_level_1
    2 -> R.string.ai_word_annotation_level_2
    3 -> R.string.ai_word_annotation_level_3
    4 -> R.string.ai_word_annotation_level_4
    else -> R.string.ai_word_annotation_level_5
}
