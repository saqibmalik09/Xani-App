package app.xani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.xani.ui.system.systemContentInsets
import app.xani.ui.theme.ContentMaxWidthDp
import app.xani.ui.theme.Dark
import app.xani.ui.theme.MatchWindowTo

/**
 * Shared frame for every auth screen. The colour fills the display, including
 * under the status bar and gesture / button nav. Interactive content is inset
 * so it does not sit under the bars. Width is capped on tablets and foldables.
 *
 * `scrollable` screens must not use `Modifier.weight`, since a scrolling column has
 * no bounded height to distribute.
 */
@Composable
fun AuthScaffold(
    background: Color,
    modifier: Modifier = Modifier,
    glow: Brush? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    MatchWindowTo(background)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter,
    ) {
        glow?.let {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(it),
            )
        }

        val width = Modifier
            .systemContentInsets()
            .fillMaxWidth()
            .widthIn(max = ContentMaxWidthDp.dp)

        if (scrollable) {
            Column(
                modifier = width.verticalScroll(rememberScrollState()),
                content = content,
            )
        } else {
            Column(
                modifier = width.fillMaxHeight(),
                content = content,
            )
        }
    }
}

val DarkGlow: Brush = Brush.radialGradient(
    colors = listOf(Dark.Primary.copy(alpha = 0.18f), Color.Transparent),
    radius = 900f,
)
