package app.xani.feature.profile

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke

internal object ProfileIco {
    @Composable
    fun Foot(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        shapes = listOf(
            VShape.Ellipse(12f, 19f, 5.5f, 3.5f, fill(tint.copy(alpha = 0.85f))),
            VShape.Ellipse(7.5f, 11.5f, 2.2f, 2.8f, fill(tint.copy(alpha = 0.85f))),
            VShape.Ellipse(10.8f, 9.5f, 1.7f, 2.4f, fill(tint.copy(alpha = 0.85f))),
            VShape.Ellipse(13.8f, 9.8f, 1.5f, 2.1f, fill(tint.copy(alpha = 0.85f))),
            VShape.Ellipse(16.4f, 11f, 1.3f, 1.9f, fill(tint.copy(alpha = 0.85f))),
            VShape.Ellipse(18.2f, 12.8f, 1.1f, 1.6f, fill(tint.copy(alpha = 0.85f))),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Hamburger(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        shapes = listOf(
            VShape.Box(2f, 5f, 18f, 2f, 1f, fill(tint)),
            VShape.Box(2f, 10f, 18f, 2f, 1f, fill(tint)),
            VShape.Box(2f, 15f, 18f, 2f, 1f, fill(tint)),
        ),
        viewport = 22f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Share(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.Circle(18f, 5f, 3f, stroke(tint, 1.8f)),
            VShape.Circle(6f, 12f, 3f, stroke(tint, 1.8f)),
            VShape.Circle(18f, 19f, 3f, stroke(tint, 1.8f)),
            VShape.D("M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98", stroke(tint, 1.8f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun ShareMini(tint: Color, size: Dp = rdp(14)) = VectorCanvas(
        shapes = listOf(
            VShape.Circle(10f, 2f, 2f, stroke(tint, 1.3f)),
            VShape.Circle(2f, 7f, 2f, stroke(tint, 1.3f)),
            VShape.Circle(10f, 12f, 2f, stroke(tint, 1.3f)),
            VShape.D("M4 6l4-3M4 8l4 3", stroke(tint, 1.3f)),
        ),
        viewport = 14f,
        modifier = Modifier.size(size),
    )

    /** Keep the share card: arrow into a tray. */
    @Composable
    fun Download(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M12 3v11m0 0l-4.2-4.2M12 14l4.2-4.2", stroke(tint, 1.8f)),
            VShape.D("M4 16v2.5A2.5 2.5 0 006.5 21h11a2.5 2.5 0 002.5-2.5V16", stroke(tint, 1.8f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Verified(size: Dp = rdp(11)) = VectorCanvas(
        shapes = listOf(
            VShape.Circle(5.5f, 5.5f, 5.5f, fill(ProfileTokens.Accent)),
            VShape.D("M3 5.5l1.8 1.8 3.2-3.6", stroke(Color.White, 1.5f)),
        ),
        viewport = 11f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Chevron(tint: Color, size: Dp = rdp(15)) = VectorCanvas(
        shapes = listOf(VShape.D("M5 3l5 4.5L5 12", stroke(tint, 1.6f))),
        viewport = 15f,
        modifier = Modifier.size(size),
    )

    /**
     * Wallet row: a stack of coins. Plural reads as a balance, and three flat
     * ellipses stay crisp where a medallion's engraved glyph turns to mush.
     */
    @Composable
    fun Coin(tint: Color, size: Dp = rdp(20)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M4.4 8.4v7.2c0 1.5 3.4 2.7 7.6 2.7s7.6-1.2 7.6-2.7V8.4z", fill(tint.copy(alpha = 0.16f))),
            VShape.D("M4.4 8.4v7.2c0 1.5 3.4 2.7 7.6 2.7s7.6-1.2 7.6-2.7V8.4", stroke(tint, 1.7f)),
            VShape.Ellipse(12f, 8.4f, 7.6f, 2.7f, fill(tint.copy(alpha = 0.22f))),
            VShape.Ellipse(12f, 8.4f, 7.6f, 2.7f, stroke(tint, 1.7f)),
            VShape.D("M4.4 12c0 1.5 3.4 2.7 7.6 2.7s7.6-1.2 7.6-2.7", stroke(tint, 1.4f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** Wallet row: brilliant-cut gem — crown, girdle, pavilion to a point. */
    @Composable
    fun Diamond(tint: Color, size: Dp = rdp(20)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M7.6 4.4h8.8L20 9.6 12 20.4 4 9.6z", fill(tint.copy(alpha = 0.16f))),
            VShape.D("M7.6 4.4h8.8L20 9.6 12 20.4 4 9.6z", stroke(tint, 1.7f)),
            VShape.D("M4 9.6h16", stroke(tint, 1.5f)),
            VShape.D("M7.6 4.4l2.2 5.2L12 20.4l2.2-10.8 2.2-5.2", stroke(tint, 1.3f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** Store / My Bag: shopping bag with simple geometry (no nested arcs). */
    @Composable
    fun Store(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.Box(5f, 9f, 14f, 11f, 2f, stroke(tint, 1.6f)),
            VShape.D("M9 9V7.2C9 5.5 10.3 4.2 12 4.2S15 5.5 15 7.2V9", stroke(tint, 1.6f)),
            VShape.D("M5 12.5h14", stroke(tint, 1.4f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Back(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        shapes = listOf(VShape.D("M14 4L7 11l7 7", stroke(tint, 2f))),
        viewport = 22f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Close(tint: Color, size: Dp = rdp(20)) = VectorCanvas(
        shapes = listOf(VShape.D("M4 4l12 12M16 4L4 16", stroke(tint, 1.8f))),
        viewport = 20f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Edit(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.D(
                "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z",
                stroke(tint, 1.7f),
            ),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Camera(tint: Color, size: Dp = rdp(14)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z", stroke(tint, 1.8f)),
            VShape.Circle(12f, 13f, 4f, stroke(tint, 1.8f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Check(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(VShape.D("M5 13l4 4L19 7", stroke(tint, 2.2f))),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun CopyLink(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        shapes = listOf(
            VShape.Box(9f, 9f, 13f, 13f, 2f, stroke(tint, 1.7f)),
            VShape.D("M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1", stroke(tint, 1.7f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Logout(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.D(
                "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1",
                stroke(tint, 1.7f),
            ),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Play(tint: Color, size: Dp = rdp(10)) = VectorCanvas(
        shapes = listOf(VShape.D("M2 1L9 5 2 9z", fill(tint))),
        viewport = 10f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun HeartSmall(tint: Color, size: Dp = rdp(10)) = VectorCanvas(
        shapes = listOf(VShape.D(HeartPath, fill(tint))),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** Profile visits: who has been looking. */
    @Composable
    fun Eye(tint: Color, size: Dp = rdp(20)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12z", stroke(tint, 1.7f)),
            VShape.Circle(12f, 12f, 2.8f, stroke(tint, 1.7f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Message(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M21 11.5a8.4 8.4 0 01-9 8.4 9.5 9.5 0 01-2.7-.4L4 21l1.5-4.2A8.2 8.2 0 013 11.5C3 6.8 7 3 12 3s9 3.8 9 8.5z", stroke(tint, 1.7f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun Path(d: String, tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(VShape.D(d, stroke(tint, 1.7f))),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** History: a clock face wound back by an arrow — "what happened before". */
    @Composable
    fun History(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", stroke(tint, 1.7f)),
            VShape.D("M3 3v5h5", stroke(tint, 1.7f)),
            VShape.D("M12 7v5l4 2", stroke(tint, 1.7f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** Transaction history: a receipt — rounded slip, zigzag foot, three lines. */
    @Composable
    fun Receipt(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.D("M6 3h12v18l-2-1.6-2 1.6-2-1.6-2 1.6-2-1.6L6 21z", stroke(tint, 1.6f)),
            VShape.Line(9f, 8f, 15f, 8f, stroke(tint, 1.5f)),
            VShape.Line(9f, 11.5f, 15f, 11.5f, stroke(tint, 1.5f)),
            VShape.Line(9f, 15f, 13f, 15f, stroke(tint, 1.5f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** Roles: an org chart — one node over two, joined by a bracket. */
    @Composable
    fun Roles(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.Box(9f, 3f, 6f, 5f, 1.5f, stroke(tint, 1.6f)),
            VShape.Box(3f, 16f, 6f, 5f, 1.5f, stroke(tint, 1.6f)),
            VShape.Box(15f, 16f, 6f, 5f, 1.5f, stroke(tint, 1.6f)),
            VShape.D("M12 8v4M6 16v-4h12v4", stroke(tint, 1.6f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    /** Find friends: two people — a near head and shoulders, a second behind. */
    @Composable
    fun People(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        shapes = listOf(
            VShape.Circle(9f, 8f, 3.4f, stroke(tint, 1.6f)),
            VShape.D("M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6", stroke(tint, 1.6f)),
            VShape.D("M15.5 4.9c1.6.4 2.6 1.7 2.6 3.1s-1 2.7-2.6 3.1", stroke(tint, 1.6f)),
            VShape.D("M17.3 14.3c2.5.6 4.2 2.7 4.2 5.7", stroke(tint, 1.6f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun GridTab(tint: Color) = VectorCanvas(
        shapes = listOf(
            VShape.Box(2f, 2f, 6f, 6f, 1f, stroke(tint, 1.4f)),
            VShape.Box(10f, 2f, 6f, 6f, 1f, stroke(tint, 1.4f)),
            VShape.Box(2f, 10f, 6f, 6f, 1f, stroke(tint, 1.4f)),
            VShape.Box(10f, 10f, 6f, 6f, 1f, stroke(tint, 1.4f)),
        ),
        viewport = 18f,
        modifier = Modifier.size(rdp(18)),
    )

    @Composable
    fun LockTab(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        shapes = listOf(
            VShape.Box(7f, 11f, 10f, 9f, 1.8f, stroke(tint, 1.6f)),
            VShape.D("M9.2 11V8.4C9.2 6.9 10.4 5.6 12 5.6C13.6 5.6 14.8 6.9 14.8 8.4V11", stroke(tint, 1.6f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )

    @Composable
    fun SaveTab(tint: Color) = VectorCanvas(
        shapes = listOf(VShape.D("M6 3h12v18l-6-4-6 4V3z", stroke(tint, 1.6f))),
        viewport = 24f,
        modifier = Modifier.size(rdp(22)),
    )

    @Composable
    fun HeartTab(tint: Color) = VectorCanvas(
        shapes = listOf(VShape.D(HeartPath, stroke(tint, 1.6f))),
        viewport = 24f,
        modifier = Modifier.size(rdp(22)),
    )
}

private const val HeartPath =
    "M12 20.2C6.4 15.6 3.8 12.9 3.8 9.4C3.8 6.9 5.7 5.1 8.1 5.1C9.6 5.1 11 5.9 12 7.3C13 5.9 14.4 5.1 15.9 5.1C18.3 5.1 20.2 6.9 20.2 9.4C20.2 12.9 17.6 15.6 12 20.2Z"
