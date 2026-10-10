package app.xani.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.system.LocalSystemNav
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke
import app.xani.ui.vector.strokedFill

val LocalFeedBottomReserve = compositionLocalOf { 70.dp }

@Composable
fun feedBottomReserve(): Dp {
    val density = LocalDensity.current
    val floor = LocalSystemNav.current.bottomFloorPx
    val nav = WindowInsets.navigationBars.getBottom(density)
    val inset = with(density) { maxOf(nav, floor).toDp() }
    return rdp(70) + inset
}

@Composable
fun MainBottomBar(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    copy: Copy,
    modifier: Modifier = Modifier,
    showHome: Boolean = true,
    showCreate: Boolean = true,
    showInbox: Boolean = true,
    inboxUnread: Int = 0,
) {
    val density = LocalDensity.current
    val floor = LocalSystemNav.current.bottomFloorPx
    val nav = WindowInsets.navigationBars.getBottom(density)
    val inset = with(density) { maxOf(nav, floor).toDp() }
    val light = if (tab.cinematic) false else !Colors.isDark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (light) Colors.Background else Dark.Background.copy(alpha = 0.96f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (light) Colors.Divider else Color.White.copy(alpha = 0.07f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rdp(70))
                .padding(start = rdp(6), end = rdp(6), bottom = rdp(2)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            if (showHome) {
                NavItem(
                    label = copy.t(Copy.TabHome),
                    active = tab == MainTab.Home,
                    light = light,
                    onClick = { onTab(MainTab.Home) },
                ) { tint, on -> HomeGlyph(tint, on) }
            }

            NavItem(
                label = copy.t(Copy.TabGames),
                active = tab == MainTab.Games,
                light = light,
                onClick = { onTab(MainTab.Games) },
            ) { tint, _ -> GamesGlyph(tint) }

            if (showCreate) {
                CreateItem(
                    label = copy.t(Copy.TabCreate),
                    active = tab == MainTab.Create,
                    light = light,
                    onClick = { onTab(MainTab.Create) },
                )
            }

            if (showInbox) {
                NavItem(
                    label = copy.t(Copy.TabInbox),
                    active = tab == MainTab.Inbox,
                    light = light,
                    onClick = { onTab(MainTab.Inbox) },
                    badge = inboxUnread,
                ) { tint, _ -> InboxGlyph(tint) }
            }

            NavItem(
                label = copy.t(Copy.TabProfile),
                active = tab == MainTab.Profile,
                light = light,
                onClick = { onTab(MainTab.Profile) },
            ) { tint, _ -> ProfileGlyph(tint) }
        }
        Spacer(Modifier.height(inset))
    }
}

@Composable
private fun NavItem(
    label: String,
    active: Boolean,
    light: Boolean,
    onClick: () -> Unit,
    badge: Int = 0,
    icon: @Composable (Color, Boolean) -> Unit,
) {
    val on = if (light) Colors.Text else Color.White
    val tint = if (active) on else on.copy(alpha = 0.4f)
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .pressable(role = Role.Tab, pressedScale = 1f, onClick = onClick)
            .padding(vertical = rdp(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rdp(3)),
    ) {
        Box(Modifier.size(rdp(27)), contentAlignment = Alignment.Center) {
            icon(tint, active)
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(rdp(14))
                        .clip(RoundedCornerShape(rdp(7)))
                        .background(Dark.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(
                        text = if (badge > 9) "9+" else badge.toString(),
                        style = Type.LabelBold,
                        color = Color.White,
                        fontSizeOverride = 8f,
                    )
                }
            }
        }
        XaniText(
            text = label,
            style = if (active) Type.LabelBold else Type.Label,
            color = tint,
            fontSizeOverride = 12f,
        )
    }
}

@Composable
private fun CreateItem(
    label: String,
    active: Boolean,
    light: Boolean,
    onClick: () -> Unit,
) {
    val on = if (light) Colors.Text else Color.White
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .pressable(role = Role.Tab, pressedScale = 1f, onClick = onClick)
            .padding(vertical = rdp(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rdp(3)),
    ) {
        Box(
            modifier = Modifier
                .width(rdp(52))
                .height(rdp(36)),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(end = rdp(5))
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(Dark.Primary),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .padding(start = rdp(5))
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(Dark.Accent),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = rdp(3))
                    .clip(RoundedCornerShape(rdp(8)))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(
                    text = "+",
                    style = Type.H1,
                    color = Color(0xFF111111),
                    fontSizeOverride = 22f,
                )
            }
        }
        XaniText(
            text = label,
            style = if (active) Type.LabelBold else Type.Label,
            color = if (active) on else on.copy(alpha = 0.4f),
            fontSizeOverride = 12f,
        )
    }
}

@Composable
private fun HomeGlyph(tint: Color, filled: Boolean) {
    val paint = if (filled) strokedFill(tint, tint, 2f) else stroke(tint, 1.5f)
    VectorCanvas(
        shapes = listOf(VShape.D("M3 12L12 4L21 12V21H15V16H9V21H3V12Z", paint)),
        viewport = 24f,
        modifier = Modifier.size(rdp(27)),
    )
}

@Composable
private fun GamesGlyph(tint: Color) {
    VectorCanvas(
        shapes = listOf(
            VShape.Box(2f, 7f, 20f, 12f, 4f, stroke(tint, 1.5f)),
            VShape.D("M9 11v4M7 13h4", stroke(tint, 1.5f)),
            VShape.Circle(15f, 13f, 1.2f, fill(tint)),
            VShape.Circle(17.5f, 11f, 1f, fill(tint)),
        ),
        viewport = 24f,
        modifier = Modifier.size(rdp(27)),
    )
}

@Composable
private fun InboxGlyph(tint: Color) {
    VectorCanvas(
        shapes = listOf(
            VShape.D("M4 4h16v12H4z", stroke(tint, 1.5f)),
            VShape.D("M4 4l8 8 8-8", stroke(tint, 1.5f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(rdp(27)),
    )
}

@Composable
private fun ProfileGlyph(tint: Color) {
    VectorCanvas(
        shapes = listOf(
            VShape.Circle(12f, 8f, 4f, stroke(tint, 1.5f)),
            VShape.D("M4 20C4 16.7 7.6 14 12 14s8 2.7 8 6", stroke(tint, 1.5f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(rdp(27)),
    )
}

@Composable
internal fun GamesMark(size: Dp, tint: Color) {
    VectorCanvas(
        shapes = listOf(
            VShape.Box(2f, 7f, 20f, 12f, 4f, stroke(tint, 1.5f)),
            VShape.D("M9 11v4M7 13h4", stroke(tint, 1.5f)),
            VShape.Circle(15f, 13f, 1.2f, fill(tint)),
            VShape.Circle(17.5f, 11f, 1f, fill(tint)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun CreateMark(size: Dp, tint: Color) {
    VectorCanvas(
        shapes = listOf(
            VShape.D("M12 5v14M5 12h14", stroke(tint, 1.8f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun InboxMark(size: Dp, tint: Color) {
    VectorCanvas(
        shapes = listOf(
            VShape.D("M4 4h16v12H4z", stroke(tint, 1.5f)),
            VShape.D("M4 4l8 8 8-8", stroke(tint, 1.5f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )
}
