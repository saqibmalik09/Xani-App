package app.xani.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke
import kotlinx.coroutines.launch

private val GridSeeds = listOf(
    0xFF7C3AED, 0xFFEC4899, 0xFF0EA5E9, 0xFFF59E0B, 0xFF10B981,
    0xFF6366F1, 0xFFF472B6, 0xFFEF4444, 0xFF34D399,
)
private val GridViews = listOf("847K", "523K", "312K", "289K", "204K", "187K", "156K", "121K", "98K")

/** Figma sound page: slide-up white page with sound info, grid, Use-sound CTA. */
@Composable
internal fun FeedSoundPage(
    post: FeedPost,
    copy: Copy,
    onClose: () -> Unit,
) {
    val enter = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var closing = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { enter.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
    fun dismiss() {
        if (closing.value) return
        closing.value = true
        scope.launch {
            enter.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler(onBack = ::dismiss)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = size.height * enter.value }
            .background(Color.White),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(14), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                Box(Modifier.size(rdp(32)).pressable(onClick = ::dismiss), contentAlignment = Alignment.Center) {
                    BackChevron(Color(0xFF111111))
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Color(0xFFF3F3F3))
                        .padding(horizontal = rdp(14), vertical = rdp(9)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                ) {
                    SearchGlyph(Color(0xFF888888), rdp(15))
                    XaniText(copy.t(Copy.HomeSoundFind), Type.Caption, color = Color(0xFF888888), fontSizeOverride = 14f)
                }
                Box(Modifier.size(rdp(32)), contentAlignment = Alignment.Center) {
                    VectorCanvas(
                        shapes = listOf(
                            VShape.Circle(18f, 5f, 3f, stroke(Color(0xFF111111), 1.8f)),
                            VShape.Circle(6f, 12f, 3f, stroke(Color(0xFF111111), 1.8f)),
                            VShape.Circle(18f, 19f, 3f, stroke(Color(0xFF111111), 1.8f)),
                            VShape.D("M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98", stroke(Color(0xFF111111), 1.8f)),
                        ),
                        viewport = 24f,
                        modifier = Modifier.size(rdp(20)),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(Color.Black.copy(alpha = 0.08f)))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(rdp(2)),
                verticalArrangement = Arrangement.spacedBy(rdp(2)),
                contentPadding = PaddingValues(bottom = rdp(120)),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Column {
                        // Sound header
                        Row(
                            Modifier.padding(horizontal = rdp(18), vertical = rdp(20)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(16)),
                        ) {
                            Box(
                                Modifier
                                    .size(rdp(80))
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))))
                                    .border(rdp(3), Color(0xFF8B5CF6), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                XaniText(post.creator.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 22f)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                                ) {
                                    XaniText(copy.t(Copy.HomeSoundOriginal), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 18f)
                                    Box(
                                        Modifier
                                            .size(rdp(28))
                                            .clip(CircleShape)
                                            .background(Color(0xFF7C3AED)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        VectorCanvas(
                                            shapes = listOf(VShape.D("M3 1L11 6L3 11V1Z", fill(Color.White))),
                                            viewport = 12f,
                                            modifier = Modifier.size(rdp(12)),
                                        )
                                    }
                                }
                                XaniText(
                                    post.creator.handle,
                                    Type.LabelBold,
                                    color = Color(0xFF111111),
                                    fontSizeOverride = 14f,
                                    modifier = Modifier.padding(top = rdp(4), bottom = rdp(4)),
                                )
                                XaniText(copy.t(Copy.HomeSoundPosts, "n" to "20"), Type.Caption, color = Color(0xFF888888), fontSizeOverride = 13f)
                            }
                        }
                        // Save
                        Row(
                            Modifier
                                .padding(horizontal = rdp(18))
                                .padding(bottom = rdp(20))
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(rdp(12)))
                                .background(Color(0xFFF3F3F3))
                                .pressable(onClick = { })
                                .padding(vertical = rdp(13)),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VectorCanvas(
                                shapes = listOf(
                                    VShape.D(
                                        "M19 21l-7-5-7 5V5a2 2 0 012-2h10a2 2 0 012 2z",
                                        stroke(Color(0xFF111111), 2f),
                                    ),
                                ),
                                viewport = 24f,
                                modifier = Modifier.size(rdp(18)),
                            )
                            Spacer(Modifier.width(rdp(8)))
                            XaniText(copy.t(Copy.HomeSoundSave), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 15f)
                        }
                    }
                }
                items(GridSeeds.size, key = { it }) { i ->
                    SoundGridTile(GridSeeds[i], GridViews[i], original = i == 0)
                }
            }
        }

        // Bottom CTA
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = rdp(16), vertical = rdp(12))
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color(0xFFF3F3F3))
                    .pressable(onClick = { })
                    .padding(vertical = rdp(13)),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(copy.t(Copy.HomeSoundStory), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 14f)
            }
            Box(
                Modifier
                    .weight(2f)
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))))
                    .pressable(onClick = { })
                    .padding(vertical = rdp(13)),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(copy.t(Copy.HomeSoundUse), Type.LabelBold, color = Color.White, fontSizeOverride = 15f)
            }
        }
    }
}

@Composable
private fun SoundGridTile(seed: Long, views: String, original: Boolean) {
    val color = Color(seed)
    Box(
        Modifier
            .aspectRatio(9f / 16f)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(color.copy(alpha = 0.8f), Color(0xFF111111)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind { drawRect(brush) }
            }
            .pressable(onClick = { }),
    ) {
        if (original) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(rdp(6))
                    .clip(RoundedCornerShape(rdp(6)))
                    .background(Color(0xFFFF3B30))
                    .padding(horizontal = rdp(7), vertical = rdp(2)),
            ) {
                XaniText("Original", Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
            }
        }
        XaniText(
            views,
            Type.LabelBold,
            color = Color.White,
            fontSizeOverride = 11f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(rdp(6)),
        )
    }
}

@Composable
internal fun BackChevron(tint: Color) {
    VectorCanvas(
        shapes = listOf(VShape.D("M14 4L7 11l7 7", stroke(tint, 2f))),
        viewport = 22f,
        modifier = Modifier.size(rdp(22)),
    )
}

@Composable
internal fun SearchGlyph(tint: Color, size: androidx.compose.ui.unit.Dp) {
    VectorCanvas(
        shapes = listOf(
            VShape.Circle(9.5f, 9.5f, 7f, stroke(tint, 1.8f)),
            VShape.D("M15 15L20 20", stroke(tint, 1.8f)),
        ),
        viewport = 22f,
        modifier = Modifier.size(size),
    )
}
