package app.xani.feature.inbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke

/** Figma chat glyphs drawn as paths — no icon font, no emoji stand-ins. */
object ChatIco {
    @Composable
    fun Emoji(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Circle(11f, 11f, 9f, stroke(tint, 1.5f)),
            VShape.D("M7.5 13c.8 1.2 2 2 3.5 2s2.7-0.8 3.5-2", stroke(tint, 1.4f)),
            VShape.Circle(8f, 9f, 1.1f, fill(tint)),
            VShape.Circle(14f, 9f, 1.1f, fill(tint)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Paperclip(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D(
                "M18 11.5L10 19.5C7.8 21.7 4.3 21.7 2 19.5C-0.2 17.3-0.2 13.8 2 11.5L11 2.5C12.5 1 14.8 1 16.3 2.5C17.8 4 17.8 6.3 16.3 7.8L8.3 15.8C7.5 16.6 6.3 16.6 5.5 15.8C4.7 15 4.7 13.8 5.5 13L12.5 6",
                stroke(tint, 1.5f),
            ),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Camera(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Box(2f, 5f, 18f, 14f, 2.5f, stroke(tint, 1.6f)),
            VShape.Circle(11f, 13f, 4f, stroke(tint, 1.6f)),
            VShape.D("M7.5 5l1.8-2.2h3.4L14.5 5", stroke(tint, 1.6f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Mic(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Box(8f, 2f, 6f, 12f, 3f, fill(tint)),
            VShape.D("M4 11C4 15.4 7.1 19 11 19C14.9 19 18 15.4 18 11", stroke(tint, 1.8f)),
            VShape.Line(11f, 19f, 11f, 21f, stroke(tint, 1.8f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Send(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(VShape.D("M2 9L16 2l-4.5 13.5L9 9 2 9z", fill(tint))),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun SendTri(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(VShape.D("M5 3L19 11 5 19z", fill(tint))),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Play(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(VShape.D("M4 2L20 11 4 20z", fill(tint))),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun ForwardFill(tint: Color, size: Dp = rdp(17)) = VectorCanvas(
        listOf(VShape.D("M2.5 8.5L7 4v3c5 0 7.5 3 7.5 7.5C12.5 11 10 9.5 7 9.5v3L2.5 8.5z", fill(tint))),
        17f,
        Modifier.size(size),
    )

    @Composable
    fun Restrict(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 9f, 7f, stroke(tint, 1.4f)),
            VShape.Line(3f, 3f, 15f, 15f, stroke(tint, 1.4f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun BlockUser(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 9f, 7f, stroke(tint, 1.4f)),
            VShape.D("M6 6h6M6 9h6M6 12h4", stroke(tint, 1.3f)),
            VShape.Line(3f, 3f, 15f, 15f, stroke(tint, 1.4f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Export(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Box(2f, 3f, 14f, 12f, 2f, stroke(tint, 1.4f)),
            VShape.D("M6 7h6M6 10h4", stroke(tint, 1.3f)),
            VShape.D("M12 10v4M10 12l2 2 2-2", stroke(tint, 1.3f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Phone(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.D(
                "M3.5 3.5C5 2 6.5 3.5 6.5 3.5L8 5c0.7 0.7 0.7 1.8 0 2.5L7 8.5C7.8 9.8 9.2 11.2 10.5 12l1-0.9c0.7-0.7 1.8-0.7 2.5 0l1.5 1.5c1.5 1.5 0 3 0 3C8 19.5-0.5 11 3.5 3.5z",
                fill(tint),
            ),
        ),
        19f,
        Modifier.size(size),
    )

    @Composable
    fun Video(tint: Color, size: Dp = rdp(26), off: Boolean = false) = VectorCanvas(
        buildList {
            add(VShape.Box(2f, 7f, 15f, 12f, 3f, fill(tint, if (off) 0.35f else 1f)))
            add(VShape.D("M17 11l7-4v12l-7-4V11Z", fill(tint, if (off) 0.35f else 1f)))
            if (off) add(VShape.Line(2f, 24f, 24f, 2f, stroke(tint, 2f)))
        },
        26f,
        Modifier.size(size),
    )

    @Composable
    fun Hangup(tint: Color = Color.White, size: Dp = rdp(28)) = VectorCanvas(
        listOf(
            VShape.D(
                "M2 16.5C5.5 10 11 6 14 6C17 6 22.5 10 26 16.5L23 19C22.5 19.5 21.5 19.5 21 18.5L19.5 15.5C19 14.5 19.5 13.5 20.5 13C19 11 16.5 9.5 14 9.5C11.5 9.5 9 11 7.5 13C8.5 13.5 9 14.5 8.5 15.5L7 18.5C6.5 19.5 5.5 19.5 5 19L2 16.5Z",
                fill(tint),
            ),
        ),
        28f,
        Modifier.size(size),
    )

    @Composable
    fun Speaker(tint: Color, size: Dp = rdp(26), dim: Boolean = false) {
        val a = if (dim) 0.6f else 1f
        VectorCanvas(
            listOf(
                VShape.D("M4 9H8L14 4v18L8 17H4V9Z", fill(tint, a)),
                VShape.D("M17 9C18.5 10.5 18.5 15.5 17 17", stroke(tint, 1.8f, if (dim) 0.5f else 1f)),
                VShape.D("M20 6C23 8.5 23 17.5 20 20", stroke(tint, 1.8f, if (dim) 0.35f else 0.85f)),
            ),
            26f,
            Modifier.size(size),
        )
    }

    @Composable
    fun Mute(tint: Color, size: Dp = rdp(26), muted: Boolean = false) = VectorCanvas(
        buildList {
            add(VShape.Box(9f, 2f, 8f, 14f, 4f, fill(tint, if (muted) 0.4f else 1f)))
            add(VShape.D("M5 13C5 17.4 8.6 21 13 21C17.4 21 21 17.4 21 13", stroke(tint, 2f, if (muted) 0.4f else 1f)))
            add(VShape.Line(13f, 21f, 13f, 24f, stroke(tint, 2f, if (muted) 0.4f else 1f)))
            if (muted) add(VShape.Line(3f, 3f, 23f, 23f, stroke(tint, 2.2f)))
        },
        26f,
        Modifier.size(size),
    )

    @Composable
    fun MoreH(tint: Color, size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Circle(7f, 13f, 2.2f, fill(tint, 0.7f)),
            VShape.Circle(13f, 13f, 2.2f, fill(tint, 0.7f)),
            VShape.Circle(19f, 13f, 2.2f, fill(tint, 0.7f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun MoreV(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 3.5f, 1.5f, fill(tint)),
            VShape.Circle(9f, 9f, 1.5f, fill(tint)),
            VShape.Circle(9f, 14.5f, 1.5f, fill(tint)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Share(tint: Color, size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.D("M13 3V17M8 8l5-5 5 5", stroke(tint, 2f, 0.75f)),
            VShape.D("M5 14V21H21V14", stroke(tint, 2f, 0.75f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun Person(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Circle(11f, 8f, 4f, stroke(tint, 1.6f)),
            VShape.D("M3 20C3 16.5 6.6 14 11 14s8 2.5 8 6", stroke(tint, 1.6f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Search(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 9f, 6f, stroke(tint, 1.6f)),
            VShape.D("M15 15L20 20", stroke(tint, 1.6f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Bell(tint: Color, size: Dp = rdp(22), muted: Boolean = false) = VectorCanvas(
        buildList {
            add(VShape.D("M11 3C7 3 4 5.7 4 9v5l-2 2.5h18L18 14V9C18 5.7 15 3 11 3z", stroke(tint, 1.6f)))
            add(VShape.D("M9 19a2 2 0 004 0", stroke(tint, 1.6f)))
            if (muted) add(VShape.Line(3f, 3f, 19f, 19f, stroke(tint, 1.6f)))
        },
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Trash(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Line(4f, 6f, 18f, 6f, stroke(tint, 1.8f)),
            VShape.Box(8.5f, 3f, 5f, 3f, 1f, stroke(tint, 1.6f)),
            VShape.Box(5.5f, 6f, 11f, 13f, 2f, stroke(tint, 1.6f)),
            VShape.Line(9f, 9f, 9f, 16f, stroke(tint, 1.5f)),
            VShape.Line(13f, 9f, 13f, 16f, stroke(tint, 1.5f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Unsend(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.D("M7 6H3v4", stroke(tint, 1.7f)),
            VShape.D("M3 10c2.2-4.2 9.5-4.6 12 1.2 2 4.6-2.4 8.8-7.2 8", stroke(tint, 1.7f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Pause(tint: Color, size: Dp = rdp(20)) = VectorCanvas(
        listOf(
            VShape.Box(4f, 3f, 4f, 14f, 1.5f, fill(tint)),
            VShape.Box(12f, 3f, 4f, 14f, 1.5f, fill(tint)),
        ),
        20f,
        Modifier.size(size),
    )

    @Composable
    fun Close(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        listOf(VShape.D("M4 4l8 8M12 4l-8 8", stroke(tint, 1.5f))),
        16f,
        Modifier.size(size),
    )

    @Composable
    fun DoubleTick(read: Boolean, size: Dp = rdp(13)) {
        val c = if (read) Color(0xFF3B82F6) else Color(0xFF9CA3AF)
        VectorCanvas(
            listOf(
                VShape.D("M2.5 10.2l3.4 3.3 6.6-8.2", stroke(c, 1.45f)),
                VShape.D("M7.2 10.2l3.4 3.3 6.6-8.2", stroke(c, 1.45f)),
            ),
            18f,
            Modifier.size(size),
        )
    }

    /**
     * WhatsApp-grade receipt mark: clock while the server has not acked,
     * one tick once stored, two grey once the other phone has it, two blue
     * once it has been read.
     */
    @Composable
    fun DeliveryMark(delivery: Delivery, size: Dp = rdp(13)) {
        when (delivery) {
            Delivery.Sending -> VectorCanvas(
                listOf(
                    VShape.Circle(9f, 9f, 6f, stroke(Color(0xFF9CA3AF), 1.4f)),
                    VShape.D("M9 5.5v3.5l2.2 2", stroke(Color(0xFF9CA3AF), 1.4f)),
                ),
                18f,
                Modifier.size(size),
            )
            Delivery.Sent -> VectorCanvas(
                listOf(VShape.D("M4.5 10.2l3.4 3.3 6.6-8.2", stroke(Color(0xFF9CA3AF), 1.45f))),
                18f,
                Modifier.size(size),
            )
            Delivery.Delivered -> DoubleTick(read = false, size = size)
            Delivery.Seen -> DoubleTick(read = true, size = size)
        }
    }

    @Composable
    fun Clock(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 9f, 7f, stroke(tint, 1.4f)),
            VShape.D("M9 5v4l2.5 2.5", stroke(tint, 1.4f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Pencil(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.D("M13 3l2 2-8 8H5v-2L13 3z", stroke(tint, 1.4f)),
            VShape.D("M2 15h14", stroke(tint, 1.3f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun ChatBubble(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M4 5.5h14c1.4 0 2.5 1.1 2.5 2.5v7c0 1.4-1.1 2.5-2.5 2.5H9.2L4 21.2V8c0-1.4 1.1-2.5 2.5-2.5H4z", stroke(tint, 1.6f)),
            VShape.D("M8 10.2h8M8 13.2h5", stroke(tint, 1.4f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun MediaVis(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Box(2f, 4f, 14f, 11f, 2f, stroke(tint, 1.4f)),
            VShape.D("M2 8l5 4 3-3 4 4", stroke(tint, 1.3f)),
            VShape.Circle(6f, 7f, 1.2f, fill(tint)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun CreateGroup(tint: Color, size: Dp = rdp(20)) = VectorCanvas(
        listOf(
            VShape.Circle(7f, 7f, 3f, stroke(tint, 1.5f)),
            VShape.Circle(14f, 7f, 2.5f, stroke(tint, 1.3f)),
            VShape.D("M1 17c0-3 2.7-5 6-5s6 2 6 5", stroke(tint, 1.5f)),
            VShape.D("M14 10c2 0.5 4 1.8 4 4.5", stroke(tint, 1.3f)),
            VShape.D("M17 7v4M15 9h4", stroke(tint, 1.3f)),
        ),
        20f,
        Modifier.size(size),
    )

    @Composable
    fun Chevron(tint: Color, size: Dp = rdp(14)) = VectorCanvas(
        listOf(VShape.D("M5 3l4 4-4 4", stroke(tint, 1.5f))),
        14f,
        Modifier.size(size),
    )

    @Composable
    fun AttachGallery(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(2f, 4f, 22f, 18f, 3f, fill(Color(0xFF4F46E5))),
            VShape.D("M2 12l7 5 5-4 5 5 5-3", stroke(Color.White, 1.6f)),
            VShape.Circle(8f, 9f, 2f, fill(Color.White, 0.8f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachCamera(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(2f, 5f, 22f, 17f, 3f, fill(Color(0xFFEC4899))),
            VShape.Circle(13f, 13.5f, 4.5f, stroke(Color.White, 1.8f)),
            VShape.Circle(13f, 13.5f, 2f, fill(Color.White, 0.5f)),
            VShape.D("M8 6l2-2h6l2 2", stroke(Color.White, 1.5f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachContact(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Circle(13f, 13f, 11f, fill(Color(0xFF10B981))),
            VShape.Circle(13f, 10f, 3.5f, stroke(Color.White, 1.5f)),
            VShape.D("M6 22c0-3.5 3.1-6 7-6s7 2.5 7 6", stroke(Color.White, 1.5f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachLocation(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Circle(13f, 11f, 7f, stroke(Color(0xFFEF4444), 1.8f)),
            VShape.Circle(13f, 11f, 2.5f, fill(Color(0xFFEF4444))),
            VShape.D("M13 18l-3 5h6l-3-5z", fill(Color(0xFFEF4444))),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachDocument(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(4f, 3f, 18f, 20f, 3f, fill(Color(0xFFF97316))),
            VShape.D("M8 9h10M8 13h10M8 17h6", stroke(Color.White, 1.6f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachPoll(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 5f, 20f, 16f, 3f, fill(Color(0xFF8B5CF6))),
            VShape.D("M8 11h10M8 15h6", stroke(Color.White, 1.6f)),
            VShape.Circle(6f, 11f, 1.5f, fill(Color.White)),
            VShape.Circle(6f, 15f, 1.5f, fill(Color.White)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachEvent(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 3f, 20f, 20f, 3f, fill(Color(0xFFEF4444))),
            VShape.Box(7f, 6f, 12f, 3f, 1f, fill(Color.White)),
            VShape.Circle(9f, 5f, 1.5f, fill(Color.White)),
            VShape.Circle(17f, 5f, 1.5f, fill(Color.White)),
            VShape.D("M7 13h12M7 17h8", stroke(Color.White, 1.5f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun AttachAi(size: Dp = rdp(26)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 3f, 20f, 20f, 3f, fill(Color(0xFF06B6D4))),
            VShape.D("M8 13l3 3 7-7", stroke(Color.White, 2f)),
        ),
        26f,
        Modifier.size(size),
    )

    @Composable
    fun FlipCam(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M4 11C4 7.7 7.1 5 11 5C14.9 5 18 7.7 18 11", stroke(tint, 1.6f, 0.85f)),
            VShape.D("M18 11C18 14.3 14.9 17 11 17C7.1 17 4 14.3 4 11", stroke(tint, 1.6f, 0.85f)),
            VShape.D("M15 2l3 3-3 3", stroke(tint, 1.7f)),
            VShape.D("M7 20l-3-3 3-3", stroke(tint, 1.7f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun AddPerson(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 8f, 4f, stroke(tint, 1.6f, 0.85f)),
            VShape.D("M2 19c0-3.3 3.1-6 7-6", stroke(tint, 1.6f, 0.85f)),
            VShape.D("M17 12v8M13 16h8", stroke(tint, 2f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun Plus(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Line(9f, 3f, 9f, 15f, stroke(tint, 2f)),
            VShape.Line(3f, 9f, 15f, 9f, stroke(tint, 2f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Minus(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(VShape.Line(3f, 9f, 15f, 9f, stroke(tint, 2f))),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Reply(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.D("M3 14l1.5-4L12 3l3 3-7.5 7.5L3 14z", stroke(tint, 1.4f)),
            VShape.D("M10 5l3 3", stroke(tint, 1.4f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Forward(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(VShape.D("M2 9l5-5v3c4 0 8 2.5 9 8-2-3.5-5-5-9-5v3L2 9z", stroke(tint, 1.4f))),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun CopyLines(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 3f, 12f, 12f, 2f, stroke(tint, 1.4f)),
            VShape.D("M6 7h6M6 10h4", stroke(tint, 1.3f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Pin(tint: Color, size: Dp = rdp(18), filled: Boolean = false) = VectorCanvas(
        listOf(
            VShape.Box(8f, 2f, 2f, 8f, 1f, if (filled) fill(tint) else stroke(tint, 1.4f)),
            VShape.D("M5 10h8l-1 3H6L5 10z", if (filled) fill(tint) else stroke(tint, 1.4f)),
            VShape.Line(9f, 13f, 9f, 17f, stroke(tint, 1.6f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun ImagesGrid(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        listOf(
            VShape.Box(1f, 1f, 6f, 6f, 1f, stroke(tint, 1.3f)),
            VShape.Box(9f, 1f, 6f, 6f, 1f, stroke(tint, 1.3f)),
            VShape.Box(1f, 9f, 6f, 6f, 1f, stroke(tint, 1.3f)),
            VShape.Box(9f, 9f, 6f, 6f, 1f, stroke(tint, 1.3f)),
        ),
        16f,
        Modifier.size(size),
    )

    @Composable
    fun Doc(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        listOf(
            VShape.Box(3f, 1f, 10f, 14f, 1.5f, stroke(tint, 1.3f)),
            VShape.Line(6f, 6f, 10f, 6f, stroke(tint, 1.2f)),
            VShape.Line(6f, 9f, 10f, 9f, stroke(tint, 1.2f)),
        ),
        16f,
        Modifier.size(size),
    )

    @Composable
    fun Link(tint: Color, size: Dp = rdp(16)) = VectorCanvas(
        listOf(
            VShape.D("M7 9H5C3.3 9 2 7.7 2 6C2 4.3 3.3 3 5 3h3", stroke(tint, 1.4f)),
            VShape.D("M9 7h2c1.7 0 3 1.3 3 3s-1.3 3-3 3H9", stroke(tint, 1.4f)),
        ),
        16f,
        Modifier.size(size),
    )

    @Composable
    fun Info(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Circle(9f, 9f, 7f, stroke(tint, 1.4f)),
            VShape.D("M9 8v5M9 6v0.5", stroke(tint, 1.6f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Translate(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.Box(2f, 3f, 14f, 12f, 2f, stroke(tint, 1.4f)),
            VShape.D("M6 8h2l1.5 4L11 6l1.5 6H15", stroke(tint, 1.2f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Report(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.D("M3.5 15.5h11l-5.5-12-5.5 12z", stroke(tint, 1.4f)),
            VShape.D("M9 5v5M9 13v0.5", stroke(tint, 1.8f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Download(tint: Color, size: Dp = rdp(18)) = VectorCanvas(
        listOf(
            VShape.D("M9 3v9M6 9l3 3 3-3", stroke(tint, 1.4f)),
            VShape.D("M3 15h12", stroke(tint, 1.4f)),
        ),
        18f,
        Modifier.size(size),
    )

    @Composable
    fun Tick(tint: Color, size: Dp = rdp(10)) = VectorCanvas(
        listOf(VShape.D("M1 4l3 3 5-6", stroke(tint, 1.5f))),
        10f,
        Modifier.size(size),
    )

    @Composable
    fun ShareStory(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Box(4f, 3f, 14f, 16f, 3.5f, stroke(tint, 1.6f)),
            VShape.D("M11 8v6M8 11h6", stroke(tint, 1.7f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun ShareWhatsApp(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D(
                "M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38c1.45.79 3.08 1.21 4.74 1.21 5.46 0 9.91-4.45 9.91-9.91 0-2.65-1.03-5.14-2.9-7.01A9.82 9.82 0 0012.04 2zm5.16 14.24c-.22.62-1.28 1.18-1.78 1.26-.46.07-1.05.1-1.69-.1-1.02-.33-2.34-.96-3.8-2.35-1.75-1.66-2.89-3.72-3.15-4.37-.26-.64-.53-1.68-.2-2.4.16-.36.5-.7.86-.82.18-.06.38-.07.54 0 .16.06.3.22.43.48l.86 1.86c.11.24.05.42-.08.58l-.42.5c-.1.12-.12.26-.03.4.28.48.86 1.16 1.58 1.78.76.64 1.6 1.08 2.12 1.28.16.06.34.02.46-.12l.48-.58c.12-.14.3-.18.48-.1l1.92.92c.2.1.3.24.28.44-.02.18-.08.4-.26.9z",
                fill(tint),
            ),
        ),
        24f,
        Modifier.size(size),
    )

    @Composable
    fun ShareStatus(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M11 3.6a7.4 7.4 0 1 1-6.5 3.8", stroke(tint, 1.6f)),
            VShape.Circle(11f, 11f, 3.2f, fill(tint)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun ShareSnap(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M11 4c2.8 0 4.8 2.2 4.8 5.1 0 2.2-.8 3.4-1.2 4.2.9.4 1.9 1 1.9 1.8 0 .7-.8 1.1-1.8 1.1-.6 0-1.1-.1-1.6-.3-.5 1.2-1.3 1.8-2.1 1.8s-1.6-.6-2.1-1.8c-.5.2-1 .3-1.6.3-1 0-1.8-.4-1.8-1.1 0-.8 1-1.4 1.9-1.8-.4-.8-1.2-2-1.2-4.2C6.2 6.2 8.2 4 11 4z", fill(tint)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun ShareMessenger(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M11 3.5C6.8 3.5 3.5 6.5 3.5 10.2c0 2.2 1.1 4.1 2.8 5.4V19l2.6-1.4c.7.2 1.4.3 2.1.3 4.2 0 7.5-3 7.5-6.7S15.2 3.5 11 3.5z", stroke(tint, 1.5f)),
            VShape.D("M7.5 11.5L10 9l2 2 2.5-2.5", stroke(tint, 1.5f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun ShareSms(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.Box(3.5f, 4.5f, 15f, 10f, 2.5f, stroke(tint, 1.55f)),
            VShape.D("M8 14.5L6 18.2l4-2.4", stroke(tint, 1.5f)),
            VShape.D("M8 9.5h6", stroke(tint, 1.4f)),
        ),
        22f,
        Modifier.size(size),
    )

    @Composable
    fun ShareFacebook(tint: Color, size: Dp = rdp(22)) = VectorCanvas(
        listOf(
            VShape.D("M13.2 4H11c-2 0-3.4 1.4-3.4 3.5V10H5.8v2.8h1.8V20h3V12.8h2.4l.5-2.8h-2.9V7.6c0-.6.4-1 1-1h2.6V4z", fill(tint)),
        ),
        22f,
        Modifier.size(size),
    )
}

@Composable
fun VanishProgressRing(
    progress: Float,
    vanish: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (vanish) Color(0xFFEC4899) else Color(0xFF8B5CF6)
    val track = if (vanish) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f)
    val glyph = if (progress > 0f) accent else track
    Canvas(modifier.size(rdp(40))) {
        val strokeW = size.minDimension * 3f / 40f
        val r = size.minDimension * 16f / 40f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = track, radius = r, center = c, style = Stroke(width = strokeW))
        if (progress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
        }
        val k = size.minDimension / 40f
        if (progress >= 1f) {
            val check = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x - 7f * k, c.y)
                lineTo(c.x - 2.5f * k, c.y + 4.5f * k)
                lineTo(c.x + 8f * k, c.y - 5f * k)
            }
            drawPath(check, accent, style = Stroke(width = 2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val arrow = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x, c.y + 4f * k)
                lineTo(c.x, c.y - 4f * k)
                moveTo(c.x - 4f * k, c.y)
                lineTo(c.x, c.y - 4f * k)
                lineTo(c.x + 4f * k, c.y)
            }
            drawPath(arrow, glyph, style = Stroke(width = 1.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
