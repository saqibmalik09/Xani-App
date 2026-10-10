package app.xani.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.xani.core.net.ShelfCategoryData
import app.xani.core.net.StoreShelfData
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke

/** Which listing a profile box opens. */
@Immutable
internal data class StoreScope(
    /** 0 = everything; otherwise one top-level category and its children. */
    val categoryId: Long,
    val title: String,
    val icon: String,
    /** The Free box: items the user got without paying (signup / role / admin). */
    val free: Boolean = false,
) {
    companion object {
        val All = StoreScope(0L, "", "bag")
    }
}

/** "VIP" → vip, "Frames" → frame, "Entry" → entrance: the box icon when the admin picked none. */
private fun iconFromTitle(title: String): String = when (title.trim().lowercase().trimEnd('s')) {
    "vip" -> "vip"
    "frame" -> "frame"
    "badge" -> "badge"
    "entry", "entrance" -> "entrance"
    "bubble" -> "bubble"
    "crown" -> "crown"
    "gift" -> "gift"
    else -> ""
}

/** A box turned "new" when something landed inside it this recently. */
private const val NEW_WINDOW_MS = 48 * 3_600_000L

/** Days-left threshold under which a box warns that something is about to lapse. */
private const val EXPIRING_SOON_MS = 3 * 86_400_000L

/**
 * The store boxes under the account shortcuts: one per active category in
 * admin order, then Free. Each box says at a glance what the user holds there —
 * worn (Active), bought/granted and parked (Owned), lapsed (Expired), or just
 * arrived (New) — and opens that category's listing with its own "My …" tab.
 * Centered when the boxes fit; scrolls sideways when the admin adds more.
 */
@Composable
internal fun StoreShelfRow(copy: Copy, shelf: StoreShelfData, now: Long, onOpen: (StoreScope) -> Unit) {
    val boxes = ArrayList<@Composable (Modifier) -> Unit>(shelf.categories.size + 1)
    for (c in shelf.categories) {
        // A box with no admin icon and nothing inside to infer a kind from
        // still usually names one in its title ("VIP", "Frames"): a brand-new
        // category draws its own glyph, not the generic bag.
        val icon = c.icon.ifEmpty { c.kind }.ifEmpty { iconFromTitle(c.title) }
        boxes += { m ->
            StoreBox(
                title = c.title,
                icon = icon,
                state = shelfState(c, now),
                modifier = m,
                onClick = { onOpen(StoreScope(c.id, c.title, icon)) },
            )
        }
    }
    // No separate Free box: a free item sits inside its own category's
    // "My …" tab wearing a FREE tag, the same place a bought one lives.
    BoxRow(boxes, top = rdp(14))
}

/**
 * The account shortcuts that used to live in the hamburger — Wallet / Coins,
 * Gifting record, My Live, My Rewards, My roles — as a row of boxes under
 * Edit / Share, same look as the store boxes below them and the same
 * destinations ([onAction] takes the keys the drawer used, so routing is one
 * switch).
 */
@Composable
internal fun ProfileQuickRow(copy: Copy, onAction: (String) -> Unit) {
    val accent = ProfileTokens.Accent
    val boxes = listOf<@Composable (Modifier) -> Unit>(
        { m ->
            ShelfBox(copy.t(Copy.ProfileWallet), Color(0xFF16A34A), Color(0xFFE7F7EC), ShelfState.None, m, { onAction("wallet") }) { tint, size ->
                // A wallet with a coin in its card pocket: money kept, not a card reader.
                VectorCanvas(
                    shapes = listOf(
                        VShape.Box(3f, 6f, 18f, 13f, 3.2f, fill(tint, 0.16f)),
                        VShape.Box(3f, 6f, 18f, 13f, 3.2f, stroke(tint, 1.8f)),
                        VShape.D("M3 9.6h12.5", stroke(tint, 1.5f)),
                        VShape.Box(13.5f, 10.6f, 7.5f, 4.6f, 1.8f, stroke(tint, 1.6f)),
                        VShape.Circle(17.2f, 12.9f, 1.2f, fill(tint)),
                    ),
                    viewport = 24f, modifier = Modifier.size(size),
                )
            }
        },
        { m ->
            ShelfBox(copy.t(Copy.GiftRecordTitle), Color(0xFFDB2777), Color(0xFFFFEAF3), ShelfState.None, m, { onAction("gifting") }) { tint, size ->
                ProfileIco.Path("M20 12v10H4V12M2 7h20v5H2zM12 22V7m0 0H7.5a2.5 2.5 0 010-5C11 2 12 7 12 7zm0 0h4.5a2.5 2.5 0 000-5C13 2 12 7 12 7z", tint, size)
            }
        },
        { m ->
            ShelfBox(copy.t(Copy.MyLiveTitle), accent, ProfileTokens.AccentSoft, ShelfState.None, m, { onAction("live") }) { tint, size ->
                // Camera body + lens wedge as plain geometry: the arc'd version
                // of this path collapsed at box size.
                VectorCanvas(
                    shapes = listOf(
                        VShape.Box(2.8f, 6.4f, 12.4f, 11.2f, 2.6f, fill(tint, 0.16f)),
                        VShape.Box(2.8f, 6.4f, 12.4f, 11.2f, 2.6f, stroke(tint, 1.8f)),
                        VShape.D("M15.2 10.2l5.4-2.9v9.4l-5.4-2.9z", fill(tint, 0.16f)),
                        VShape.D("M15.2 10.2l5.4-2.9v9.4l-5.4-2.9z", stroke(tint, 1.8f)),
                        VShape.Circle(6.4f, 12f, 1.5f, fill(tint)),
                    ),
                    viewport = 24f, modifier = Modifier.size(size),
                )
            }
        },
        { m ->
            ShelfBox(copy.t(Copy.RewardsTitle), Color(0xFFD97706), Color(0xFFFFF4E0), ShelfState.None, m, { onAction("rewards") }) { tint, size ->
                ProfileIco.Path("M12 15l-3.5 2 .9-4L6 10.3l4.1-.4L12 6l1.9 3.9 4.1.4-3.4 2.7.9 4zM4 4h16v3H4zM5 7h14v13H5z", tint, size)
            }
        },
        { m ->
            ShelfBox(copy.t(Copy.RolesTitle), Color(0xFF2563EB), Color(0xFFEAF1FF), ShelfState.None, m, { onAction("roles") }) { tint, size ->
                ProfileIco.Roles(tint, size)
            }
        },
    )
    BoxRow(boxes, top = rdp(12))
}

/**
 * Up to five boxes share the width evenly (so five never overflow a 360dp
 * phone); more than five scroll sideways at their natural width.
 */
@Composable
private fun BoxRow(boxes: List<@Composable (Modifier) -> Unit>, top: Dp) {
    if (boxes.isEmpty()) return
    val gap = rdp(8)
    if (boxes.size <= 5) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = top),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
        ) {
            for (b in boxes) b(Modifier.weight(1f, fill = false).widthIn(max = rdp(84)))
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = top)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Top,
        ) {
            for (b in boxes) b(Modifier.width(rdp(66)))
        }
    }
}

internal enum class ShelfState { None, Active, Owned, Expiring, Expired, New, Free }

private fun shelfState(c: ShelfCategoryData, now: Long): ShelfState = when {
    c.latestMs > 0L && now - c.latestMs < NEW_WINDOW_MS && c.owned > 0 -> ShelfState.New
    c.expiringMs > 0L && c.expiringMs - now < EXPIRING_SOON_MS -> ShelfState.Expiring
    c.active > 0 -> ShelfState.Active
    c.owned > 0 -> ShelfState.Owned
    c.expired > 0 -> ShelfState.Expired
    else -> ShelfState.None
}

@Composable
private fun StoreBox(title: String, icon: String, state: ShelfState, modifier: Modifier, onClick: () -> Unit) {
    val look = iconLook(icon)
    ShelfBox(title, look.tint, look.bg, state, modifier, onClick) { tint, size -> StoreBoxIcon(icon, tint, size) }
}

/** One box: tinted rounded square with a glyph, a state badge on the corner, a label under. */
@Composable
internal fun ShelfBox(
    title: String,
    tint: Color,
    bg: Color,
    state: ShelfState,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable (tint: Color, size: Dp) -> Unit,
) {
    Column(
        modifier = modifier.pressable(pressedScale = 0.95f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(rdp(52))
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(bg)
                    .border(rdp(1), tint.copy(alpha = 0.22f), RoundedCornerShape(rdp(16))),
                contentAlignment = Alignment.Center,
            ) {
                icon(tint, rdp(26))
            }
            // The state sits on the corner so the label underneath stays short.
            ShelfBadge(state, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(rdp(5)))
        XaniText(
            title,
            Type.LabelBold,
            color = ProfileTokens.Text,
            fontSizeOverride = 10.5f,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShelfBadge(state: ShelfState, modifier: Modifier) {
    val (label, bg) = when (state) {
        ShelfState.None -> return
        ShelfState.Active -> "ON" to Color(0xFF16A34A)
        ShelfState.Owned -> null to Color(0xFF8B5CF6)
        ShelfState.Expiring -> "!" to Color(0xFFF59E0B)
        ShelfState.Expired -> null to Color(0xFFBBBBBB)
        ShelfState.New -> "NEW" to Color(0xFFEF4444)
        ShelfState.Free -> "FREE" to Color(0xFF0EA5E9)
    }
    if (label == null) {
        Box(
            modifier
                .padding(top = rdp(3), end = rdp(3))
                .size(rdp(9))
                .clip(CircleShape)
                .background(Color.White)
                .padding(rdp(1.5f))
                .clip(CircleShape)
                .background(bg),
        )
        return
    }
    Box(
        modifier
            .offset(x = rdp(6), y = rdp(-4))
            .clip(RoundedCornerShape(rdp(7)))
            .background(Color.White)
            .padding(rdp(1.5f))
            .clip(RoundedCornerShape(rdp(6)))
            .background(bg)
            .padding(horizontal = rdp(4), vertical = rdp(1)),
    ) {
        XaniText(label, Type.Micro, color = Color.White, fontSizeOverride = 8f, maxLines = 1)
    }
}

private class IconLook(val tint: Color, val bg: Color)

/** Each category gets its own hue so the row reads as four things, not one. */
private fun iconLook(icon: String): IconLook = when (icon) {
    "frame" -> IconLook(Color(0xFF7C3AED), Color(0xFFF3EEFF))
    "badge" -> IconLook(Color(0xFF2563EB), Color(0xFFEAF1FF))
    "entrance" -> IconLook(Color(0xFFDB2777), Color(0xFFFFEAF3))
    "vip", "crown" -> IconLook(Color(0xFFD97706), Color(0xFFFFF4E0))
    "bubble" -> IconLook(Color(0xFF0D9488), Color(0xFFE3F7F4))
    "star" -> IconLook(Color(0xFFCA8A04), Color(0xFFFFF8DB))
    "gift", "free" -> IconLook(Color(0xFF0EA5E9), Color(0xFFE4F5FE))
    "diamond" -> IconLook(Color(0xFF0891B2), Color(0xFFE0F6FA))
    else -> IconLook(Color(0xFF555555), Color(0xFFF2F2F2))
}

/**
 * The glyph for one category box. Each is a different silhouette at the same
 * optical weight: a frame rings an avatar, a badge is a ribboned shield, entry
 * is a door with someone stepping through, VIP is a crown, a bubble speaks,
 * free is a gift. Unknown icon names fall back to the bag.
 */
@Composable
internal fun StoreBoxIcon(icon: String, tint: Color, size: Dp) {
    val m = Modifier.size(size)
    when (icon) {
        "frame" -> VectorCanvas(
            shapes = listOf(
                VShape.Circle(12f, 12f, 9.2f, stroke(tint, 1.9f)),
                VShape.D("M12 2.8l1.1 1.6M21.2 12l-1.6 1.1M12 21.2l-1.1-1.6M2.8 12l1.6-1.1", stroke(tint, 1.9f)),
                VShape.Circle(12f, 10.3f, 2.6f, fill(tint)),
                VShape.D("M7.4 17.2c.9-2.2 2.6-3.3 4.6-3.3s3.7 1.1 4.6 3.3", fill(tint)),
            ),
            viewport = 24f, modifier = m,
        )
        "badge" -> VectorCanvas(
            shapes = listOf(
                VShape.D("M12 3l7 2.6v5.2c0 4.3-2.9 8-7 9.4-4.1-1.4-7-5.1-7-9.4V5.6z", fill(tint, 0.16f)),
                VShape.D("M12 3l7 2.6v5.2c0 4.3-2.9 8-7 9.4-4.1-1.4-7-5.1-7-9.4V5.6z", stroke(tint, 1.8f)),
                VShape.D("M12 7.4l1.3 2.7 2.9.4-2.1 2 .5 2.9-2.6-1.4-2.6 1.4.5-2.9-2.1-2 2.9-.4z", fill(tint)),
            ),
            viewport = 24f, modifier = m,
        )
        "entrance" -> VectorCanvas(
            shapes = listOf(
                VShape.D("M13 3h6a1 1 0 011 1v16a1 1 0 01-1 1h-6", stroke(tint, 1.8f)),
                VShape.D("M13 3v18", stroke(tint, 1.8f)),
                VShape.Circle(16.4f, 12.2f, 0.9f, fill(tint)),
                VShape.D("M3 12h7M7.4 8.6L10.8 12l-3.4 3.4", stroke(tint, 2f)),
                VShape.D("M5 5.5l.7-1.5.7 1.5M4.2 18.5l.7-1.4.7 1.4", stroke(tint, 1.2f)),
            ),
            viewport = 24f, modifier = m,
        )
        "vip", "crown" -> VectorCanvas(
            shapes = listOf(
                VShape.D("M4 8.5l4.2 3.6L12 6l3.8 6.1L20 8.5l-1.6 9H5.6z", fill(tint, 0.18f)),
                VShape.D("M4 8.5l4.2 3.6L12 6l3.8 6.1L20 8.5l-1.6 9H5.6z", stroke(tint, 1.8f)),
                VShape.D("M5.4 20h13.2", stroke(tint, 2.2f)),
                VShape.Circle(12f, 14.2f, 1.3f, fill(tint)),
            ),
            viewport = 24f, modifier = m,
        )
        "bubble" -> VectorCanvas(
            shapes = listOf(
                VShape.D("M4 6.5A2.5 2.5 0 016.5 4h11A2.5 2.5 0 0120 6.5v7a2.5 2.5 0 01-2.5 2.5H10l-4.4 3.4V16H6.5A2.5 2.5 0 014 13.5z", fill(tint, 0.16f)),
                VShape.D("M4 6.5A2.5 2.5 0 016.5 4h11A2.5 2.5 0 0120 6.5v7a2.5 2.5 0 01-2.5 2.5H10l-4.4 3.4V16H6.5A2.5 2.5 0 014 13.5z", stroke(tint, 1.8f)),
                VShape.Circle(8.6f, 10f, 1.1f, fill(tint)),
                VShape.Circle(12f, 10f, 1.1f, fill(tint)),
                VShape.Circle(15.4f, 10f, 1.1f, fill(tint)),
            ),
            viewport = 24f, modifier = m,
        )
        "star" -> VectorCanvas(
            shapes = listOf(
                VShape.D("M12 3.2l2.7 5.6 6.1.8-4.5 4.3 1.1 6.1L12 17.1 6.6 20l1.1-6.1L3.2 9.6l6.1-.8z", fill(tint, 0.18f)),
                VShape.D("M12 3.2l2.7 5.6 6.1.8-4.5 4.3 1.1 6.1L12 17.1 6.6 20l1.1-6.1L3.2 9.6l6.1-.8z", stroke(tint, 1.8f)),
            ),
            viewport = 24f, modifier = m,
        )
        "gift", "free" -> VectorCanvas(
            shapes = listOf(
                VShape.Box(4f, 10f, 16f, 10.5f, 1.6f, fill(tint, 0.16f)),
                VShape.Box(3f, 7f, 18f, 4f, 1.4f, stroke(tint, 1.8f)),
                VShape.D("M5 11v8.5a1 1 0 001 1h12a1 1 0 001-1V11", stroke(tint, 1.8f)),
                VShape.D("M12 7v13.5", stroke(tint, 1.8f)),
                VShape.D("M12 7c-1.4-2.8-3.6-3.9-5-2.6-1.2 1.1.1 2.6 5 2.6zM12 7c1.4-2.8 3.6-3.9 5-2.6 1.2 1.1-.1 2.6-5 2.6z", stroke(tint, 1.6f)),
            ),
            viewport = 24f, modifier = m,
        )
        "diamond" -> ProfileIco.Diamond(tint, size)
        else -> ProfileIco.Store(tint, size)
    }
}
