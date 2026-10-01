package app.xani.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * The bottom sheet used over a broadcast: springs up from the bottom edge,
 * dims the room behind it, follows the finger anywhere on its surface (the
 * handle, the header, the bar — and its list once that is at the top) and
 * slides away before it unmounts, so closing never pops.
 *
 * Motion is a single `translationY` on a graphics layer, read from two
 * states: the resting/animated [Animatable] and the finger's live drag. The
 * drag writes a plain float per move event — no coroutine per frame, no
 * layout pass — and the scrim is drawn, not composed.
 *
 * [content] receives `close`, which animates out and then calls [onClose].
 */
/**
 * A drag surface for the sheet's own horizontal rows (receivers, tabs): it
 * claims a vertical pull in the Initial pass, before the row's own scroll sees
 * it, so the sheet follows the thumb from anywhere on those rows. A sideways
 * swipe is left alone and scrolls the row as usual. [LiveSheet] provides it;
 * outside a sheet it is a no-op.
 */
val LocalLiveSheetDrag = compositionLocalOf<Modifier> { Modifier }

@Composable
fun LiveSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Slide the sheet off-screen without unmounting it: state survives (the
     * selected gift, a combo in progress) while the room shows what was sent.
     * Taps fall through to the broadcast while hidden.
     */
    hidden: Boolean = false,
    background: Brush = Brush.verticalGradient(listOf(Color(0xFF1B1430), Color(0xFF110C21))),
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 1 = fully below the screen, 0 = resting. Fraction of the sheet's height.
    val offset = remember { Animatable(1f) }
    val state = remember { SheetMotion() }
    // Gesture lambdas below are built once; they read the live flag from here.
    state.hidden = hidden
    // The finger's live displacement, in px, on top of [offset].
    var dragPx by remember { mutableFloatStateOf(0f) }

    fun fraction(): Float {
        val h = state.heightPx
        return if (h <= 0f) offset.value else (offset.value + dragPx / h).coerceIn(0f, 1f)
    }
    fun close() {
        if (state.closing) return
        state.closing = true
        scope.launch {
            offset.snapTo(fraction()); dragPx = 0f
            offset.animateTo(1f, tween(170, easing = FastOutLinearInEasing))
            onClose()
        }
    }
    fun settle(velocityPxPerSec: Float) {
        state.dragging = false
        if (state.closing || state.hidden) return
        val at = fraction()
        // Generous close: a quarter of the sheet's height, or any real
        // downward fling, dismisses. Closing must never feel like a fight.
        val flingDown = velocityPxPerSec > 800f
        val flingUp = velocityPxPerSec < -900f
        if (!flingUp && (at > 0.25f || (flingDown && at > 0.04f))) {
            close()
        } else {
            scope.launch {
                offset.snapTo(at); dragPx = 0f
                offset.animateTo(
                    0f,
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    initialVelocity = if (velocityPxPerSec < 0f && state.heightPx > 0f) velocityPxPerSec / state.heightPx else 0f,
                )
            }
        }
    }
    fun dragBy(dyPx: Float) {
        if (state.closing || state.hidden) return
        val h = state.heightPx
        if (h <= 0f) return
        // Resist above the resting position so an upward pull feels anchored.
        val raw = dragPx + dyPx
        dragPx = if (offset.value * h + raw < 0f) raw * 0.25f else raw
    }

    // Enter on mount; slide away and back as [hidden] flips. One animatable,
    // so a re-show mid-slide simply reverses — no jump, no double sheet.
    LaunchedEffect(hidden) {
        if (state.closing) return@LaunchedEffect
        if (hidden) {
            offset.snapTo(fraction()); dragPx = 0f
            offset.animateTo(1f, tween(220, easing = FastOutLinearInEasing))
        } else {
            offset.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 340f))
        }
    }
    BackHandler(enabled = !hidden) { close() }

    // Priority drag for horizontal rows: claims a vertical pull in the Initial
    // pass (parent before child) so the row's LazyRow never starts a scroll
    // that would swallow the gesture. A sideways swipe is not consumed.
    val handleDrag = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            tracker.resetTracking()
            // Accumulated finger travel in a frame that does not move. The raw
            // positions here are relative to this row — which slides with the
            // sheet the drag itself moves — so a delta measured across two
            // events would include the sheet's own motion and feed back as a
            // fast up-down wobble. positionChange() maps both endpoints with
            // the same transform, so it is pure finger motion.
            var total = Offset.Zero
            tracker.addPosition(down.uptimeMillis, total)
            var dragging = false
            var acc = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging) settle(tracker.calculateVelocity().y)
                    break
                }
                val delta = change.positionChange()
                total += delta
                tracker.addPosition(change.uptimeMillis, total)
                if (!dragging) {
                    acc += delta
                    if (abs(acc.x) > slop && abs(acc.x) >= abs(acc.y)) break // the row's own scroll
                    if (abs(acc.y) > slop) {
                        dragging = true
                        state.dragging = true
                        change.consume()
                        dragBy(acc.y - slop * sign(acc.y))
                    }
                } else {
                    change.consume()
                    dragBy(delta.y)
                }
            }
            if (dragging && state.dragging) settle(0f)
        }
    }

    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // List is at its top and the finger keeps pulling down: the sheet follows.
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    state.dragging = true
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Sheet is pulled down and the finger goes back up: retract first.
                if (available.y < 0f && dragPx > 0f && source == NestedScrollSource.UserInput) {
                    val take = maxOf(available.y, -dragPx)
                    dragBy(take)
                    return Offset(0f, take)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.dragging) { settle(available.y); return available }
                return Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (state.dragging) settle(available.y)
                return Velocity.Zero
            }
        }
    }
    // Whole-surface drag. Children that scroll vertically win their own
    // gesture (they consume first); everything else — handle, header rows,
    // horizontal lists, the bottom bar — hands the vertical pull to the sheet.
    val surfaceDrag = Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            tracker.resetTracking()
            var pointer = down.id
            val start = awaitVerticalTouchSlopOrCancellation(pointer) { change, over ->
                change.consume()
                state.dragging = true
                tracker.addPosition(change.uptimeMillis, change.position)
                dragBy(over)
            } ?: return@awaitEachGesture
            pointer = start.id
            val done = verticalDrag(pointer) { change ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                dragBy(change.positionChange().y)
            }
            settle(if (done) tracker.calculateVelocity().y else 0f)
        }
    }

    val shape = RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24))
    Box(modifier.fillMaxSize()) {
        // The scrim only exists while the sheet is up: hidden, every tap on the
        // room (the host, the chat, the floating combo button) reaches it.
        if (!hidden) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f * (1f - fraction()))) }
                    .pressable(pressedScale = 1f, onClick = { close() }),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { state.heightPx = it.height.toFloat() }
                .graphicsLayer { translationY = fraction() * size.height }
                .clip(shape)
                .background(background)
                .border(1.dp, Color(0x408B5CF6), shape)
                .navigationBarsPadding()
                // A sheet with a field in it (PK search, a custom amount) rides
                // up with the keyboard; the background stays under both.
                .imePadding()
                .then(surfaceDrag)
                .nestedScroll(nested),
        ) {
            // A tall grab strip: the whole band is drag surface, not just the
            // painted pill, so a thumb landing anywhere near the top works.
            Box(
                Modifier.fillMaxWidth().height(rdp(30)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = rdp(44), height = rdp(5))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
                )
            }
            CompositionLocalProvider(LocalLiveSheetDrag provides handleDrag) {
                content(::close)
            }
        }
    }
}

private class SheetMotion {
    var heightPx = 0f
    var dragging = false
    var closing = false
    var hidden = false
}
