package app.xani.feature.create

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

/**
 * Create icons — UpdatedFigma paths, single paint pass, no emoji stand-ins.
 */
object CreateIco {
    @Composable
    fun Close(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(VShape.D("M2 2l14 14M16 2L2 16", stroke(tint, 2.2f))),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Music(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        listOf(
            VShape.D("M6 12V4l8-2v8", stroke(tint, 1.6f)),
            VShape.Circle(4f, 12f, 2f, fill(tint)),
            VShape.Circle(12f, 10f, 2f, fill(tint)),
        ),
        16f,
        Modifier.size(size),
    )

    @Composable
    fun FlipCam(tint: Color, size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 9f, 18f, 13f, 3.5f, stroke(tint, 1.8f)),
            VShape.Circle(12f, 15.5f, 3.8f, stroke(tint, 1.8f)),
            VShape.Circle(12f, 15.5f, 1.4f, fill(tint.copy(alpha = 0.6f))),
            VShape.D("M8 9V7.5C8 7 8.6 6.5 9.2 6.5h4.6c.6 0 1.2.5 1.2 1V9", stroke(tint, 1.6f)),
            VShape.D("M22 8l2 3h-3l1.5-3 1.5 3", stroke(tint, 1.5f)),
            VShape.D("M24 18l-2-3h3l-1.5 3-1.5-3", stroke(tint, 1.5f)),
        ),
        30f,
        Modifier.size(size),
    )

    @Composable
    fun Flash(tint: Color, size: Dp = rdp(28), off: Boolean = false) = VectorCanvas(
        buildList {
            if (off) {
                add(VShape.D("M16 3L8 16h7l-3 9 11-13h-7L16 3Z", stroke(tint, 1.8f)))
                add(VShape.Line(4f, 24f, 24f, 4f, stroke(tint, 1.8f)))
            } else {
                add(VShape.D("M16 3L8 16h7l-3 9 11-13h-7L16 3Z", fill(tint)))
            }
        },
        28f,
        Modifier.size(size),
    )

    @Composable
    fun Timer(tint: Color, size: Dp = rdp(28)) = VectorCanvas(
        listOf(
            VShape.Circle(14f, 15f, 9f, stroke(tint, 1.8f)),
            VShape.D("M14 10v5l3 3", stroke(tint, 1.8f)),
            VShape.D("M11 4h6M14 4v2", stroke(tint, 1.8f)),
        ),
        28f,
        Modifier.size(size),
    )

    @Composable
    fun Grid(tint: Color, size: Dp = rdp(28)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 3f, 22f, 22f, 2f, stroke(tint, 1.6f)),
            VShape.Line(10.3f, 3f, 10.3f, 25f, stroke(tint, 1.2f)),
            VShape.Line(17.7f, 3f, 17.7f, 25f, stroke(tint, 1.2f)),
            VShape.Line(3f, 10.3f, 25f, 10.3f, stroke(tint, 1.2f)),
            VShape.Line(3f, 17.7f, 25f, 17.7f, stroke(tint, 1.2f)),
        ),
        28f,
        Modifier.size(size),
    )

    @Composable
    fun Speed(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M4 16a8 8 0 1 1 14 0", stroke(tint, 1.6f)),
            VShape.D("M11 16L16 9", stroke(tint, 1.7f)),
            VShape.Circle(11f, 16f, 1.4f, fill(tint)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Beauty(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Circle(11f, 11f, 7f, stroke(tint, 1.5f)),
            VShape.D("M8 10.5c.5-.8 1.5-1.3 3-1.3s2.5.5 3 1.3", stroke(tint, 1.4f)),
            VShape.Circle(8.5f, 9f, 0.9f, fill(tint)),
            VShape.Circle(13.5f, 9f, 0.9f, fill(tint)),
            VShape.D("M9 14c1 .8 3 .8 4 0", stroke(tint, 1.4f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Users(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Circle(8f, 8f, 3.2f, stroke(tint, 1.5f)),
            VShape.D("M3 18c0-2.6 2.2-4.5 5-4.5s5 1.9 5 4.5", stroke(tint, 1.5f)),
            VShape.Circle(15.5f, 8.5f, 2.4f, stroke(tint, 1.4f)),
            VShape.D("M14 18c.4-2 2-3.4 4.2-3.4 1.2 0 2.2.4 3 1", stroke(tint, 1.4f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Photo(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 5.5f, 16f, 12f, 2.2f, stroke(tint, 1.5f)),
            VShape.Circle(11f, 11.5f, 3.2f, stroke(tint, 1.5f)),
            VShape.D("M7 5.5l1.6-2h4.8L15 5.5", stroke(tint, 1.5f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Mic(tint: Color, size: Dp = rdp(18), off: Boolean = false) = VectorCanvas(
        buildList {
            add(VShape.Box(9f, 2f, 6f, 12f, 3f, stroke(tint, 1.6f)))
            add(VShape.D("M5 11c0 4 3.1 7 7 7s7-3 7-7", stroke(tint, 1.6f)))
            add(VShape.Line(12f, 18f, 12f, 22f, stroke(tint, 1.6f)))
            if (off) add(VShape.Line(4f, 4f, 20f, 20f, stroke(tint, 1.8f)))
        },
        24f,
        Modifier.size(size),
    )

    @Composable
    fun Cam(tint: Color, size: Dp = rdp(18), off: Boolean = false) = VectorCanvas(
        buildList {
            add(VShape.Box(2f, 6f, 14f, 12f, 2f, stroke(tint, 1.6f)))
            add(VShape.D("M16 10l5-3v10l-5-3V10z", stroke(tint, 1.6f)))
            if (off) add(VShape.Line(3f, 4f, 21f, 20f, stroke(tint, 1.8f)))
        },
        24f,
        Modifier.size(size),
    )

    @Composable
    fun Chevron(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(VShape.D("M11 4L5 10l6 6", stroke(tint, 2f))),
        16f,
        Modifier.size(size),
    )

    @Composable
    fun SoftMic(tint: Color, size: Dp = rdp(24)) = VectorCanvas(
        listOf(
            VShape.Box(9f, 2f, 8f, 14f, 4f, stroke(tint, 1.8f)),
            VShape.D("M4 13c0 5 4 9 9 9s9-4 9-9", stroke(tint, 1.8f)),
            VShape.Line(13f, 22f, 13f, 25f, stroke(tint, 1.8f)),
        ),
        26f,
        Modifier.size(size),
    )
}
