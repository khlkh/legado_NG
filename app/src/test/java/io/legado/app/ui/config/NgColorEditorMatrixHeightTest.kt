package io.legado.app.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class NgColorEditorMatrixHeightTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun fixedEditorCanvasKeepsTheOneAndAHalfInchMatrixHeight() {
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                Box(Modifier.height(600.dp)) {
                    val state = remember {
                        NgColorPickerState(0xFF222222.toInt(), forceOpaque = true)
                    }
                    NgColorPickerContent(
                        state = state,
                        originalColor = 0xFF222222.toInt(),
                        fixedGraphicHeight = 240.dp,
                        previewRole = NgColorPreviewRole.TEXT,
                        previewBackground = 0xFFF7F3EA.toInt(),
                        previewForeground = 0xFF222222.toInt(),
                        previewAccent = 0xFF2B579A.toInt(),
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Grid")
            .assertHeightIsEqualTo(240.dp)
    }

    @Test
    fun fixedLooseCanvasKeepsTheSameHeightForHighlightColors() {
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                Box(Modifier.height(600.dp)) {
                    val state = remember {
                        NgColorPickerState(0xFF222222.toInt(), forceOpaque = true)
                    }
                    NgColorPickerContent(
                        state = state,
                        originalColor = 0xFF222222.toInt(),
                        fixedGraphicHeight = 240.dp,
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Grid")
            .assertHeightIsEqualTo(240.dp)
    }

    private fun testTheme() = NgThemeResolver.resolve(
        NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(),
            accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(),
            bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(),
            isDark = false,
            isEInk = false,
        )
    )
}
