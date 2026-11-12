package io.legado.app.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.annotation.WordAnnotationConfig
import io.legado.app.help.annotation.WordAnnotationDensity
import io.legado.app.help.annotation.WordAnnotationLevels
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem

/**
 * Word Gloss settings sheet: model, five-level vocabulary level, and density.
 * Display density is word-count based; the per-book on/off switch stays in the
 * reader.
 */
@Composable
internal fun WordAnnotationSettingsSheet(
    level: Int,
    density: Int,
    modelSummary: String,
    bookActive: Boolean,
    onSelectModel: () -> Unit,
    onLevelSelected: (Int) -> Unit,
    onDensitySelected: (Int) -> Unit,
) {
    var showLevelPicker by rememberSaveable { mutableStateOf(false) }
    var showDensityPicker by rememberSaveable { mutableStateOf(false) }
    val normalizedLevel = WordAnnotationLevels.normalize(level)
    val normalizedDensity = WordAnnotationDensity.fromStorage(density)

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
            ),
            enabled = bookActive,
            onClick = { showLevelPicker = true },
        )
        NgSettingsItem(
            title = stringResource(R.string.ai_word_annotation_density),
            summary = stringResource(
                R.string.ai_word_annotation_density_summary,
                stringResource(densityLabelRes(normalizedDensity)),
            ),
            enabled = bookActive,
            onClick = { showDensityPicker = true },
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
    if (showDensityPicker) {
        ConfigChoiceDialog(
            title = stringResource(R.string.ai_word_annotation_density),
            options = WordAnnotationDensity.entries.map { value ->
                ConfigChoiceOption(
                    label = stringResource(densityLabelRes(value)),
                    value = value.storageValue.toString(),
                )
            },
            selectedValue = normalizedDensity.storageValue.toString(),
            onDismissRequest = { showDensityPicker = false },
            onSelected = { value ->
                showDensityPicker = false
                value.toIntOrNull()?.let(onDensitySelected)
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

private fun densityLabelRes(density: WordAnnotationDensity): Int = when (density) {
    WordAnnotationDensity.SPARSE -> R.string.ai_word_annotation_density_sparse
    WordAnnotationDensity.LIGHT -> R.string.ai_word_annotation_density_light
    WordAnnotationDensity.STANDARD -> R.string.ai_word_annotation_density_standard
    WordAnnotationDensity.DENSE -> R.string.ai_word_annotation_density_dense
    WordAnnotationDensity.RICH -> R.string.ai_word_annotation_density_rich
}
