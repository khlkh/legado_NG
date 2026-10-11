package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiMessage
import io.legado.app.help.ai.AiTextParams
import io.legado.app.help.config.AiThemeDeckStore
import io.legado.app.ui.config.AiAssistantConfigUi
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@Composable
internal fun AiThemePage(
    night: Boolean,
    textColor: Int,
    backgroundColor: Int,
    accentInk: Int,
    contentColor: Color,
    pageHeight: Dp,
    dayText: Int,
    dayBackground: Int,
    dayAccent: Int,
    nightText: Int,
    nightBackground: Int,
    nightAccent: Int,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onApplyPair: (NgPaperLook, NgPaperLook) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var shift by remember(night) { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(AiThemeDeckStore.loadPairs()) }
    var query by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var modelPick by remember { mutableIntStateOf(0) }
    val themeModel = remember(modelPick) { AiAssistantConfigUi.themeGenerationModel() }
    val listState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val defaults = rotatedDefaultPairs(shift)
    fun generate() {
        val preference = query.trim()
        if (preference.isBlank() || generating) return
        val model = AiAssistantConfigUi.themeGenerationModel()
        if (model == null) {
            context.toastOnUi(context.getString(R.string.ng_paper_need_assistant))
            return
        }
        generating = true
        keyboard?.hide()
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val result = AiManager.generateText(
                        messages = listOf(
                            AiMessage(AiMessage.Role.SYSTEM, AI_THEME_SYSTEM_PROMPT),
                            AiMessage(
                                AiMessage.Role.USER,
                                buildAiThemeUserPrompt(
                                    preference,
                                    readerUiLanguageTagForAiTheme(context),
                                ),
                            ),
                        ),
                        params = AiTextParams(
                            temperature = 0.6f,
                            maxTokens = 2200,
                            jsonResponse = true,
                            disableThinking = true,
                        ),
                        providerId = model.provider.id,
                        modelId = model.model.id,
                    )
                    logAiThemeOutput(result.content)
                    parseAiThemePairs(result.content)
                }
            }
            outcome.exceptionOrNull()?.let { error ->
                if (BuildConfig.DEBUG) AppLog.put("AiTheme generate failed", error)
            }
            val next = outcome.getOrNull().orEmpty()
            if (next.isNotEmpty()) {
                if (BuildConfig.DEBUG) {
                    next.forEach { pair ->
                        AppLog.put(
                            "AiTheme stored ${pair.name}\n" +
                                "day ${aiThemeCodeLine(pair.day.text, pair.day.background, pair.day.accent)}\n" +
                                "night ${aiThemeCodeLine(pair.night.text, pair.night.background, pair.night.accent)}",
                        )
                    }
                }
                saved = AiThemeDeckStore.appendPairs(saved, next)
                query = ""
                listState.scrollTo(0)
            } else {
                val err = outcome.exceptionOrNull()?.localizedMessage
                context.toastOnUi(err ?: context.getString(R.string.ng_paper_generate_empty))
            }
            generating = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pageHeight),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgThemeSheetActionButton(
                onClick = onBack,
                contentDescription = stringResource(R.string.back),
                touchSize = 44.dp,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color(NgTheme.colors.onSurface),
                )
            }
            Text(
                text = stringResource(R.string.read_style_color_ai),
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            NgThemeSheetActionButton(
                onClick = onReset,
                contentDescription = stringResource(R.string.ng_reset_color),
                touchSize = 44.dp,
            ) {
                Icon(
                    Icons.Rounded.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color(NgTheme.colors.onSurface),
                )
            }
        }
        Text(
            text = stringResource(R.string.read_style_ai_hint),
            color = contentColor.copy(alpha = 0.62f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(listState),
        ) {
            if (saved.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ng_paper_saved),
                    modifier = Modifier.padding(bottom = 6.dp),
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                saved.forEachIndexed { index, pair ->
                    key(index, pair.name, pair.day.background, pair.night.background) {
                        SwipeToRemoveGeneratedTheme(
                            onRemove = {
                                val removed = saved.getOrNull(index) ?: return@SwipeToRemoveGeneratedTheme
                                val at = index
                                saved = AiThemeDeckStore.removeAt(saved, at)
                                val message = context.getString(R.string.ng_paper_theme_deleted, removed.name)
                                val undo = context.getString(R.string.rss_editor_undo)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = withTimeoutOrNull(THEME_DELETE_UNDO_MS) {
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = undo,
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    }
                                    if (result == SnackbarResult.ActionPerformed) {
                                        saved = AiThemeDeckStore.restoreAt(saved, at, removed)
                                    }
                                }
                            },
                        ) {
                            AiThemePairRow(
                                pair = pair,
                                dayLabel = stringResource(R.string.day),
                                nightLabel = stringResource(R.string.night),
                                dayText = dayText,
                                dayBackground = dayBackground,
                                dayAccent = dayAccent,
                                nightText = nightText,
                                nightBackground = nightBackground,
                                nightAccent = nightAccent,
                                onApplyPair = onApplyPair,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ng_paper_defaults),
                    modifier = Modifier.weight(1f),
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.ng_paper_shuffle),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { shift += 1 }
                        .padding(6.dp),
                    tint = contentColor,
                )
            }
            defaults.forEach { recipe ->
                val name = stringResource(recipe.nameRes)
                val pair = recipe.toPair(name)
                key(recipe.id) {
                    AiThemePairRow(
                        pair = pair,
                        dayLabel = stringResource(R.string.day),
                        nightLabel = stringResource(R.string.night),
                        dayText = dayText,
                        dayBackground = dayBackground,
                        dayAccent = dayAccent,
                        nightText = nightText,
                        nightBackground = nightBackground,
                        nightAccent = nightAccent,
                        onApplyPair = onApplyPair,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val hint = stringResource(R.string.ng_paper_query_hint)
                val modelLabel = themeModel?.model?.let { model ->
                    model.displayName.ifBlank { model.name.ifBlank { model.id } }
                } ?: stringResource(R.string.ng_paper_need_assistant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = hint,
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .widthIn(max = 168.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(NgTheme.colors.primary).copy(alpha = 0.14f))
                            .border(1.dp, Color(NgTheme.colors.primary).copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) {
                                AiAssistantConfigUi.showModelSelectSheet(
                                    context = context,
                                    scope = scope,
                                    onChanged = { modelPick += 1 },
                                    onDismiss = {},
                                )
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = modelLabel,
                            modifier = Modifier.weight(1f, fill = false),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 2.dp).size(16.dp),
                            tint = Color(NgTheme.colors.primary),
                        )
                    }
                }
                NgFormField(
                    label = "",
                    placeholder = hint,
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { generate() }),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(NgTheme.colors.primary).copy(alpha = if (generating) 0.4f else 1f))
                    .clickable(enabled = query.isNotBlank() && !generating, role = Role.Button, onClick = { generate() })
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ng_paper_generate),
                    color = Color(NgTheme.colors.onPrimary),
                    fontSize = 13.sp,
                )
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) { data ->
            Snackbar(snackbarData = data, actionOnNewLine = false)
        }
    }
}

private const val THEME_DELETE_UNDO_MS = 3_000L

private fun logAiThemeOutput(content: String) {
    if (!BuildConfig.DEBUG) return
    AppLog.put("AiTheme output\n${content.ifBlank { "(empty)" }}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemoveGeneratedTheme(
    onRemove: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val currentRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { true },
        positionalThreshold = { distance -> distance * 0.4f },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            currentRemove()
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {},
        content = content,
    )
}

internal fun aiThemeHex(color: Int, includeAlpha: Boolean): String {
    return if (includeAlpha) {
        "#%08X".format(Locale.US, color)
    } else {
        "#%06X".format(Locale.US, color and 0xFFFFFF)
    }
}

internal fun aiThemeCodeLine(text: Int, background: Int, accent: Int): String =
    "FG: ${aiThemeHex(text, includeAlpha = false)} / BG: ${aiThemeHex(background, includeAlpha = false)} / AC: ${aiThemeHex(accent, includeAlpha = false)}"

private fun sameLook(look: NgPaperLook, text: Int, background: Int, accent: Int): Boolean =
    look.text == text && look.background == background && look.accent == accent

@Composable
private fun AiThemePairRow(
    pair: AiThemePair,
    dayLabel: String,
    nightLabel: String,
    dayText: Int,
    dayBackground: Int,
    dayAccent: Int,
    nightText: Int,
    nightBackground: Int,
    nightAccent: Int,
    onApplyPair: (NgPaperLook, NgPaperLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AiThemeHalf(
            name = pair.name,
            modeLabel = dayLabel,
            look = pair.day,
            selected = sameLook(pair.day, dayText, dayBackground, dayAccent),
            modifier = Modifier.weight(1f),
            onClick = { onApplyPair(pair.day, pair.night) },
        )
        AiThemeHalf(
            name = pair.name,
            modeLabel = nightLabel,
            look = pair.night,
            selected = sameLook(pair.night, nightText, nightBackground, nightAccent),
            modifier = Modifier.weight(1f),
            onClick = { onApplyPair(pair.day, pair.night) },
        )
    }
}

@Composable
private fun AiThemeHalf(
    name: String,
    modeLabel: String,
    look: NgPaperLook,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(look.background))
            .border(
                width = if (selected) 1.5.dp else 0.7.dp,
                color = Color(look.text).copy(alpha = if (selected) 0.85f else 0.28f),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = Color(look.text),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "($modeLabel)",
                color = Color(look.accent),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Text(
            text = aiThemeCodeLine(look.text, look.background, look.accent),
            modifier = Modifier.padding(top = 4.dp),
            color = Color(look.text),
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

