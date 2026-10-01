package app.xani.feature.profile

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xani.core.device.DeviceClass
import app.xani.core.media.RemoteImage
import app.xani.core.net.AuthState
import app.xani.core.net.PostData
import app.xani.core.net.Xani
import app.xani.feature.social.FrameStore
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.home.PostViewer
import app.xani.feature.inbox.InboxController
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/**
 * UpdatedFigma Profile — home grid + right drawer + edit/settings/stats overlays.
 * Snapshot comes from [ProfileStore]; overlays never remount the grid while open.
 */
@Composable
fun ProfileScreen(
    copy: Copy,
    device: DeviceClass,
    onImmersive: (Boolean) -> Unit = {},
) {
    // One cache the whole app reads: edits made here, on another screen, or on
    // another device all arrive through this flow.
    val snapshot by ProfileStore.state.collectAsState()
    val bottom = LocalFeedBottomReserve.current
    val handle = "@${snapshot.username}"

    val authState by Xani.session.state.collectAsState()
    // Keyed on the account, not the session: a silent token renew must not re-read
    // the profile or drop the live stream.
    val signedIn = authState as? AuthState.SignedIn
    LaunchedEffect(signedIn?.user?.id ?: 0L) {
        val user = signedIn?.user
        if (user == null) {
            if (authState is AuthState.SignedOut) {
                ProfileStore.signedOut()
                MyPosts.signedOut()
                ReactedPosts.signedOut()
                AvatarStore.signedOut()
                InboxController.shared.signedOut()
            }
            return@LaunchedEffect
        }
        MyPosts.bind(user.id)
        ProfileStore.signedIn(
            id = user.id,
            fullName = user.fullName,
            username = user.username,
            email = user.email,
            bio = user.bio,
        )
        // Live profile and balance updates, held only while this screen is on screen.
        ProfileStore.observeLive()
        // The frame and badge this account wears, so the header survives a restart.
        Xani.frames.mine().value?.let { owned ->
            val active = owned.firstOrNull { it.kind == "frame" && it.active && !it.expired }
            FrameStore.learn(user.id, active?.assetKey.orEmpty(), active?.assetMime.orEmpty())
            val badge = owned.firstOrNull { it.kind == "badge" && it.active && !it.expired }
            FrameStore.learnBadge(user.id, badge?.assetKey.orEmpty(), badge?.assetMime.orEmpty())
        }
    }

    var filter by rememberSaveable { mutableStateOf(ProfileFilter.Posts) }
    var sort by rememberSaveable { mutableStateOf(ProfileSort.Latest) }
    var statusSheet by rememberSaveable { mutableStateOf(false) }
    /** Camera-or-gallery for the profile picture. */
    var photoSheet by rememberSaveable { mutableStateOf(false) }
    /** Held the picture: showing it large. */
    var pictureOpen by rememberSaveable { mutableStateOf(false) }

    var drawer by rememberSaveable { mutableStateOf(false) }
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(ProfileRoute.Home) }
    var statsKind by rememberSaveable { mutableStateOf(ProfileStatsKind.Following) }
    var helpTicketId by rememberSaveable { mutableStateOf(0L) }
    var roleRequestId by rememberSaveable { mutableStateOf(0L) }
    var toast by remember { mutableStateOf("") }
    val switchError by Xani.session.switchError.collectAsState()
    val supportTicket by app.xani.feature.support.SupportRouter.pending.collectAsState()
    LaunchedEffect(supportTicket) {
        val id = app.xani.feature.support.SupportRouter.consume() ?: return@LaunchedEffect
        if (id <= 0L) return@LaunchedEffect
        drawer = false
        accountSheet = false
        helpTicketId = id
        route = ProfileRoute.Help
    }
    // Role request / decision notices: open the roles screen (on that request
    // when there is one). While the screen is already up it consumes the
    // router itself, so only act when it is not.
    val roleRoute by app.xani.feature.roles.RolesRouter.pending.collectAsState()
    LaunchedEffect(roleRoute) {
        if (roleRoute == null || route == ProfileRoute.Roles) return@LaunchedEffect
        val id = app.xani.feature.roles.RolesRouter.consume() ?: return@LaunchedEffect
        drawer = false
        accountSheet = false
        roleRequestId = id
        route = ProfileRoute.Roles
    }
    LaunchedEffect(switchError) {
        if (switchError.isNotEmpty()) {
            toast = switchError
            Xani.session.clearSwitchError()
        }
    }
    /** A grid cell opened full-screen. */
    var viewing by remember { mutableStateOf<PostData?>(null) }
    /** Long-pressed cell: the author's own edit / delete actions. */
    var acting by remember { mutableStateOf<PostData?>(null) }
    /** The post whose caption and audience are being edited. */
    var editing by remember { mutableStateOf<PostData?>(null) }

    LaunchedEffect(toast) {
        if (toast.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(1800)
        toast = ""
    }

    // Saves are optimistic, so the only thing left to say is when one was refused.
    LaunchedEffect(Unit) {
        ProfileStore.notices.collect { notice ->
            toast = when (notice) {
                is ProfileNotice.UsernameLocked ->
                    copy.t(Copy.ProfileUsernameAgainIn, "n" to notice.daysLeft.toString())

                is ProfileNotice.UsernameTaken ->
                    copy.t(Copy.ProfileUsernameTaken, "u" to notice.username)

                ProfileNotice.SaveFailed -> copy.t(Copy.ProfileSaveFailed)
            }
        }
    }

    LaunchedEffect(drawer, route, statusSheet, photoSheet, viewing, acting, editing) {
        onImmersive(
            drawer || route != ProfileRoute.Home || statusSheet || photoSheet ||
                viewing != null || acting != null || editing != null,
        )
    }

    val mine by MyPosts.state.collectAsState()
    val liked by ReactedPosts.Liked.state.collectAsState()
    val saved by ReactedPosts.Saved.state.collectAsState()
    // The reaction tabs are separate lists on the server; they are read when
    // the user asks for them, not on every profile open.
    LaunchedEffect(filter) {
        when (filter) {
            ProfileFilter.Liked -> ReactedPosts.Liked.open()
            ProfileFilter.Saved -> ReactedPosts.Saved.open()
            else -> Unit
        }
    }
    val tab = when (filter) {
        ProfileFilter.Liked -> liked
        ProfileFilter.Saved -> saved
        else -> null
    }

    // A post can go away while the app is showing it — deleted here, or on the
    // user's other device. Close whatever is pointing at it rather than leave a
    // viewer playing a post that no longer exists.
    LaunchedEffect(mine.raw) {
        // Only the owner's own posts leave through this grid; a liked or saved
        // cell belongs to somebody else and is closed by the post bus instead.
        viewing?.let { if (it.authorId == ProfileStore.currentUserId && it.id !in mine.raw) viewing = null }
        acting?.let { if (it.id !in mine.raw) acting = null }
        editing?.let { if (it.id !in mine.raw) editing = null }
    }
    val posts = remember(mine.items, liked.items, saved.items, sort, filter) {
        val raw = when (filter) {
            ProfileFilter.Posts -> mine.items.filter { !it.isPrivate }
            ProfileFilter.Private -> mine.items.filter { it.isPrivate }
            ProfileFilter.Liked -> liked.items
            ProfileFilter.Saved -> saved.items
        }
        if (sort == ProfileSort.Popular) raw.sortedByDescending { it.rank } else raw
    }
    // Which list a tapped cell comes from, so the viewer opens with no re-read.
    val cells = tab?.raw ?: mine.raw

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottom + rdp(8)),
            horizontalArrangement = Arrangement.spacedBy(rdp(2)),
            verticalArrangement = Arrangement.spacedBy(rdp(2)),
        ) {
            item(span = { GridItemSpan(3) }, key = "header") {
                ProfileHeader(
                    copy = copy,
                    handle = handle,
                    // Live memberships from the session: the role title(s) this
                    // account holds, refreshed with the access token.
                    roleTitles = signedIn?.access?.memberships
                        ?.map { it.roleTitle.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.distinct()
                        .orEmpty(),
                    onRoles = { route = ProfileRoute.Roles },
                    snapshot = snapshot,
                    onVisits = { route = ProfileRoute.Visits },
                    status = snapshot.status,
                    whatsOnMind = copy.t(Copy.ProfileWhatsOnMind),
                    onOpenStatus = { statusSheet = true },
                    onPickPhoto = { photoSheet = true },
                    onHoldPhoto = { pictureOpen = true },
                    filter = filter,
                    onFilter = { filter = it },
                    sort = sort,
                    onSort = { sort = it },
                    onDrawer = { drawer = true },
                    onEdit = { route = ProfileRoute.Edit },
                    onShare = { route = ProfileRoute.Share },
                    onSwitchAccounts = { accountSheet = true },
                    onCopied = { toast = copy.t(Copy.ProfileCopied) },
                    onStats = {
                        statsKind = it
                        route = ProfileRoute.Stats
                    },
                )
            }
            val loaded = tab?.loaded ?: mine.loaded
            if (posts.isEmpty() && !loaded) {
                item(span = { GridItemSpan(3) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = rdp(40)), contentAlignment = Alignment.Center) {
                        Spinner(rdp(22), ProfileTokens.Muted)
                    }
                }
            } else if (posts.isEmpty()) {
                item(span = { GridItemSpan(3) }, key = "empty") {
                    XaniText(
                        copy.t(
                            when (filter) {
                                ProfileFilter.Liked -> Copy.ProfileNoLiked
                                ProfileFilter.Saved -> Copy.ProfileNoSaved
                                ProfileFilter.Private -> Copy.ProfileNoPrivate
                                ProfileFilter.Posts -> Copy.ProfileNoPosts
                            },
                        ),
                        Type.Caption,
                        color = ProfileTokens.Muted,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(40)),
                    )
                }
            }
            items(posts, key = { it.id }) { post ->
                // Ask for the next page while the last row is still off-screen.
                if (post.id == posts.lastOrNull()?.id) {
                    LaunchedEffect(post.id, filter) {
                        when (filter) {
                            ProfileFilter.Liked -> ReactedPosts.Liked.loadMore()
                            ProfileFilter.Saved -> ReactedPosts.Saved.loadMore()
                            else -> MyPosts.loadMore()
                        }
                    }
                }
                PostCell(
                    post = post,
                    device = device,
                    copy = copy,
                    onOpen = { if (post.state == "live") viewing = cells[post.id] },
                    // Edit and delete are the author's, so a liked or saved
                    // post that belongs to somebody else has no hold menu.
                    onActions = { cells[post.id]?.let { if (it.authorId == ProfileStore.currentUserId) acting = it } },
                )
            }
        }

        ProfileDrawer(
            copy = copy,
            snapshot = snapshot,
            visible = drawer,
            onClose = { drawer = false },
            onSettings = {
                drawer = false
                route = ProfileRoute.Settings
            },
            onAction = { action ->
                drawer = false
                when (action) {
                    "logout" -> Xani.session.signOut()
                    "frames" -> route = ProfileRoute.Frames
                    "gifting" -> route = ProfileRoute.GiftRecord
                    "txn" -> route = ProfileRoute.Transactions
                    "diamonds", "wallet" -> route = ProfileRoute.Withdraw
                    "help" -> {
                        helpTicketId = 0L
                        route = ProfileRoute.Help
                    }
                    "report" -> route = ProfileRoute.Report
                    "terms" -> route = ProfileRoute.Terms
                    "roles" -> {
                        roleRequestId = 0L
                        route = ProfileRoute.Roles
                    }
                }
            },
        )

        when (route) {
            ProfileRoute.Edit -> ProfileEditScreen(
                copy = copy,
                snapshot = snapshot,
                onBack = { route = ProfileRoute.Home },
                onSaved = { route = ProfileRoute.Home },
            )
            ProfileRoute.Settings -> ProfileSettingsScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
                passwordLoginActive = Xani.session.lastKnownUser?.passwordLoginActive == true,
                onAction = { action ->
                    when (action) {
                        "logout" -> Xani.session.signOut()
                        "password" -> route = ProfileRoute.Password
                        "txn" -> route = ProfileRoute.Transactions
                        "withdraw" -> route = ProfileRoute.Withdraw
                        "notifications" -> route = ProfileRoute.Notifications
                        "blocked" -> route = ProfileRoute.Blocked
                        "help" -> {
                            helpTicketId = 0L
                            route = ProfileRoute.Help
                        }
                        "terms" -> route = ProfileRoute.Terms
                        "switch" -> {
                            route = ProfileRoute.Home
                            accountSheet = true
                        }
                    }
                },
            )
            ProfileRoute.Blocked -> BlockedAccountsScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Settings },
            )
            ProfileRoute.Notifications -> NotificationSettingsScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Settings },
            )
            ProfileRoute.Password -> ProfilePasswordScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Settings },
            )
            ProfileRoute.Stats -> ProfileStatsScreen(
                copy = copy,
                kind = statsKind,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.Visits -> ProfileVisitsScreen(
                copy = copy,
                snapshot = snapshot,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.Frames -> FramesScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.GiftRecord -> GiftRecordScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.Transactions -> TransactionsScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.Withdraw -> WithdrawScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.Share -> ProfileShareScreen(
                copy = copy,
                snapshot = snapshot,
                onBack = { route = ProfileRoute.Home },
                onToast = { toast = it },
            )
            ProfileRoute.Help -> HelpCentreScreen(
                copy = copy,
                onBack = {
                    helpTicketId = 0L
                    route = ProfileRoute.Home
                },
                startTicketId = helpTicketId,
            )
            ProfileRoute.Report -> HelpCentreScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
                startReport = true,
            )
            ProfileRoute.Terms -> TermsPoliciesScreen(
                copy = copy,
                onBack = { route = ProfileRoute.Home },
            )
            ProfileRoute.Roles -> app.xani.feature.roles.RolesScreen(
                copy = copy,
                onBack = {
                    roleRequestId = 0L
                    route = ProfileRoute.Home
                },
                startRequestId = roleRequestId,
            )
            ProfileRoute.Home -> Unit
        }

        viewing?.let { post ->
            PostViewer(post = post, device = device, copy = copy, onClose = { viewing = null })
        }

        acting?.let { post ->
            ProfilePostActionsSheet(
                copy = copy,
                post = post,
                onEdit = { editing = post },
                onDeleted = {
                    // The grid drops the cell from the post bus; close anything
                    // still pointing at it.
                    acting = null
                    if (viewing?.id == post.id) viewing = null
                },
                onClose = { acting = null },
            )
        }

        editing?.let { post ->
            ProfilePostEditSheet(copy = copy, post = post, onClose = { editing = null })
        }

        if (statusSheet) {
            ProfileStatusSheet(
                copy = copy,
                displayName = snapshot.displayName,
                initial = snapshot.status,
                onClose = { statusSheet = false },
            )
        }

        if (photoSheet) {
            AvatarPickerSheet(copy = copy, onClose = { photoSheet = false })
        }

        if (accountSheet) {
            AccountSwitcherSheet(
                copy = copy,
                onClose = { accountSheet = false },
                onCreateAccount = {
                    accountSheet = false
                    Xani.session.beginAddAccount(startSignup = true)
                },
                onAddAccount = {
                    accountSheet = false
                    Xani.session.beginAddAccount(startSignup = false)
                },
            )
        }

        if (pictureOpen) {
            MyPicturePane(handle = handle, onClose = { pictureOpen = false })
        }

        if (toast.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom + rdp(24))
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color(0xE6111111))
                    .padding(horizontal = rdp(16), vertical = rdp(8)),
            ) {
                XaniText(toast, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
            }
        }
    }
}

/**
 * The signed-in account's own picture, large. A circle on a dimmed screen —
 * the same thing holding anyone else's picture does, so the gesture means one
 * thing everywhere.
 */
@Composable
private fun MyPicturePane(handle: String, onClose: () -> Unit) {
    val grow = remember { Animatable(0.82f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .pressable(pressedScale = 1f, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = grow.value; scaleY = grow.value },
                contentAlignment = Alignment.Center,
            ) {
                MyAvatar(size = rdp(240))
            }
            XaniText(
                handle,
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 15f,
                modifier = Modifier.padding(top = rdp(18)),
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    copy: Copy,
    handle: String,
    roleTitles: List<String>,
    onRoles: () -> Unit,
    snapshot: ProfileSnapshot,
    onVisits: () -> Unit,
    status: String,
    whatsOnMind: String,
    onOpenStatus: () -> Unit,
    onPickPhoto: () -> Unit,
    onHoldPhoto: () -> Unit,
    filter: ProfileFilter,
    onFilter: (ProfileFilter) -> Unit,
    sort: ProfileSort,
    onSort: (ProfileSort) -> Unit,
    onDrawer: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onSwitchAccounts: () -> Unit,
    onCopied: () -> Unit,
    onStats: (ProfileStatsKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // Figma: foot · @handle · share + hamburger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = rdp(20), end = rdp(20), top = rdp(14)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Top-left is who has been looking at this profile: the eye and the
            // count, straight into the visits list. (It used to open a search
            // field over the user's own grid, which is not a thing anybody was
            // looking for on their own profile.)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(ProfileTokens.AccentSoft)
                    .pressable(onClick = onVisits)
                    .padding(horizontal = rdp(10), vertical = rdp(5)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(5)),
            ) {
                ProfileIco.Eye(ProfileTokens.AccentDeep, rdp(17))
                XaniText(
                    snapshot.profileViews,
                    Type.LabelBold,
                    color = ProfileTokens.AccentDeep,
                    fontSizeOverride = 13f,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(8)))
                    .pressable(onClick = onSwitchAccounts)
                    .padding(horizontal = rdp(6), vertical = rdp(4)),
            ) {
                XaniText(handle, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(rdp(34))
                    .clip(CircleShape)
                    .background(ProfileTokens.Bg2)
                    .border(rdp(1), ProfileTokens.Border, CircleShape)
                    .pressable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { ProfileIco.Share(ProfileTokens.Text, rdp(18)) }
            Spacer(Modifier.width(rdp(8)))
            Hit(onClick = onDrawer) { ProfileIco.Hamburger(ProfileTokens.Text) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = rdp(24), end = rdp(24), top = rdp(18)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centred: the tooltip above is wider than the picture, and a Box
            // sizes to its widest child, so without this the avatar sits at
            // the left edge of the tooltip rather than under the middle of it.
            Box(contentAlignment = Alignment.Center) {
                // Room for the camera badge: the ring used to be only 3dp wider
                // than the face, which clipped half the badge.
                Box(
                    modifier = Modifier
                        .size(rdp(108))
                        .background(ProfileTokens.AvatarRing, CircleShape)
                        .padding(rdp(3)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Tap anywhere on the picture (or the camera badge) to change
                    // it; the new one is on screen before the upload finishes.
                    // Hold to see it full size, as on anyone else's profile.
                    MyAvatar(size = rdp(84), onPick = onPickPhoto, onHold = onHoldPhoto)
                }
                // Status tooltip floats over the avatar — tap opens the write sheet.
                StatusTooltip(
                    text = status.ifBlank { whatsOnMind },
                    muted = status.isBlank(),
                    onClick = onOpenStatus,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-32).dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                XaniText(snapshot.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 20f)
                if (snapshot.verified) {
                    ProfileIco.Verified(rdp(16))
                }
                // The badge this account wears — same store the live surfaces read,
                // persisted, so it is on the header from the first frame.
                val wornBadges by FrameStore.badges.collectAsState()
                wornBadges[ProfileStore.currentUserId]?.takeIf { it.url.isNotEmpty() }?.let { b ->
                    NameBadge(b.url, b.mime, rdp(20))
                }
                LevelMark(snapshot.senderLevel, rdp(20))
                LevelMark(snapshot.receiverLevel, rdp(20))
            }

            var handleFlash by remember { mutableStateOf(false) }
            LaunchedEffect(handleFlash) {
                if (!handleFlash) return@LaunchedEffect
                kotlinx.coroutines.delay(700)
                handleFlash = false
            }
            Box(
                modifier = Modifier
                    .padding(top = rdp(4))
                    .clip(RoundedCornerShape(rdp(8)))
                    .background(Color.Transparent)
                    .pressable(onClick = onSwitchAccounts)
                    .padding(horizontal = rdp(8), vertical = rdp(2)),
            ) {
                XaniText(handle, Type.Caption, color = ProfileTokens.Sub)
            }
            if (roleTitles.isNotEmpty()) {
                // Current role title(s); tap opens the roles workspace.
                Box(
                    modifier = Modifier
                        .padding(top = rdp(6))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(ProfileTokens.AccentSoft)
                        .pressable(onClick = onRoles)
                        .padding(horizontal = rdp(10), vertical = rdp(4)),
                ) {
                    XaniText(
                        roleTitles.joinToString(" · "),
                        Type.LabelBold,
                        color = ProfileTokens.AccentDeep,
                        fontSizeOverride = 12f,
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(12))
                    .border(width = rdp(1), color = ProfileTokens.Border, shape = RoundedCornerShape(0)),
            ) {
                Stat(snapshot.following, copy.t(Copy.ProfileFollowing), true) { onStats(ProfileStatsKind.Following) }
                Stat(snapshot.followers, copy.t(Copy.ProfileFollowers), true) { onStats(ProfileStatsKind.Followers) }
                Stat(snapshot.receiving, copy.t(Copy.ProfileReceiving), false) { onStats(ProfileStatsKind.Receiving) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(10)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                Wallet(snapshot.coins, copy.t(Copy.ProfileCoins)) {
                    ProfileIco.Coin(ProfileTokens.AccentDeep, rdp(20))
                }
                Wallet(snapshot.diamonds, copy.t(Copy.ProfileDiamonds)) {
                    ProfileIco.Diamond(ProfileTokens.AccentDeep, rdp(20))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(10))
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(ProfileTokens.Bg2)
                    .border(rdp(1), ProfileTokens.Border, RoundedCornerShape(rdp(12)))
                    .padding(horizontal = rdp(14), vertical = rdp(10)),
            ) {
                XaniText(snapshot.bio, Type.Caption, color = ProfileTokens.Sub)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(10), bottom = rdp(4)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                Pill(copy.t(Copy.ProfileEdit), Modifier.weight(1f), onClick = onEdit)
                Pill(
                    copy.t(Copy.ProfileShare),
                    Modifier.weight(1f),
                    onClick = onShare,
                    leading = { ProfileIco.ShareMini(ProfileTokens.Text) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = rdp(14))
                .border(width = rdp(1), color = ProfileTokens.Border, shape = RoundedCornerShape(0)),
        ) {
            FilterTab(filter == ProfileFilter.Posts, { onFilter(ProfileFilter.Posts) }) { ProfileIco.GridTab(it) }
            FilterTab(filter == ProfileFilter.Private, { onFilter(ProfileFilter.Private) }) { ProfileIco.LockTab(it) }
            FilterTab(filter == ProfileFilter.Saved, { onFilter(ProfileFilter.Saved) }) { ProfileIco.SaveTab(it) }
            FilterTab(filter == ProfileFilter.Liked, { onFilter(ProfileFilter.Liked) }) { ProfileIco.HeartTab(it) }
        }

        if (filter == ProfileFilter.Posts) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(16), vertical = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                SortChip(copy.t(Copy.ProfileLatest), sort == ProfileSort.Latest) { onSort(ProfileSort.Latest) }
                SortChip(copy.t(Copy.ProfilePopular), sort == ProfileSort.Popular) { onSort(ProfileSort.Popular) }
            }
        }
    }
}

/**
 * One grid tile. The thumbnail paints over the author gradient (the gradient is
 * what a text post — or a still-processing upload — shows); the like count sits
 * bottom-left where the Figma tile keeps its counter.
 */
@Composable
private fun PostCell(
    post: ProfilePost,
    device: DeviceClass,
    copy: Copy,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
    val color = Color(post.color)
    Box(
        modifier = Modifier
            .aspectRatio(9f / 14f)
            .drawWithCache {
                if (device == DeviceClass.Low) {
                    onDrawBehind { drawRect(color) }
                } else {
                    val brush = Brush.linearGradient(
                        listOf(color, color.copy(alpha = 0.55f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(brush) }
                }
            }
            // Tap opens it full-screen; hold is where the author's own edit and
            // delete live, as it does on every grid this app's users already know.
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onLongClick = onActions,
                onClick = onOpen,
            ),
    ) {
        if (post.thumbUrl.isNotEmpty()) {
            RemoteImage(
                url = post.thumbUrl,
                modifier = Modifier.fillMaxSize(),
                maxEdge = 360,
                fallback = Color.Transparent,
            )
        } else if (post.kind == "text" && post.caption.isNotEmpty()) {
            XaniText(
                post.caption,
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 11f,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                align = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(rdp(8)),
            )
        }
        when (post.state) {
            "processing" -> StateBadge(copy.t(Copy.HomePosting), Modifier.align(Alignment.Center))
            "failed" -> StateBadge(copy.t(Copy.HomeFailed), Modifier.align(Alignment.Center))
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(rdp(4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(3)),
        ) {
            // Video cells lead with plays, the way every grid of clips does;
            // a still or a text post has no watch time, so it leads with likes.
            if (post.kind == "video") {
                ProfileIco.Play(Color.White, rdp(10))
                XaniText(post.views, Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
            } else {
                ProfileIco.HeartSmall(Color.White, rdp(10))
                XaniText(post.likes, Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
            }
        }
        if (post.kind == "video" && post.durationMs > 0) {
            XaniText(
                formatClip(post.durationMs),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 9f,
                modifier = Modifier.align(Alignment.BottomEnd).padding(rdp(4)),
            )
        }
        if (post.isPrivate) {
            Box(Modifier.align(Alignment.TopEnd).padding(rdp(4))) { ProfileIco.LockTab(Color.White, rdp(12)) }
        }
        if (post.isRestricted) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(rdp(4))
                    .clip(RoundedCornerShape(rdp(6)))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = rdp(5), vertical = rdp(2)),
            ) {
                XaniText(copy.t(Copy.ProfileRestricted), Type.LabelBold, color = Color.White, fontSizeOverride = 8f)
            }
        }
    }
}

@Composable
private fun StateBadge(label: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(rdp(8)))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = rdp(8), vertical = rdp(4)),
    ) {
        XaniText(label, Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
    }
}

private fun formatClip(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun StatusTooltip(
    text: String,
    muted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .sizeIn(maxWidth = rdp(200))
                .clip(RoundedCornerShape(rdp(14)))
                .background(ProfileTokens.Text)
                .pressable(onClick = onClick)
                .padding(horizontal = rdp(12), vertical = rdp(7)),
        ) {
            XaniText(
                text,
                Type.Caption,
                color = Color.White.copy(alpha = if (muted) 0.72f else 1f),
                fontSizeOverride = 12f,
                align = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Speech-bubble tail pointing toward the avatar.
        Box(
            Modifier
                .size(rdp(10))
                .offset(y = (-3).dp)
                .drawWithCache {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2f, size.height)
                        lineTo(0f, 0f)
                        lineTo(size.width, 0f)
                        close()
                    }
                    onDrawBehind {
                        drawPath(path, ProfileTokens.Text)
                    }
                },
        )
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String, divider: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (divider) Modifier.border(rdp(1), ProfileTokens.Border, RoundedCornerShape(0)) else Modifier)
            .pressable(onClick = onClick)
            .padding(vertical = rdp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 18f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(2)),
        ) {
            XaniText(label, Type.Caption, color = ProfileTokens.AccentDeep, fontSizeOverride = 11f)
            ProfileIco.Chevron(ProfileTokens.AccentDeep, rdp(11))
        }
    }
}

@Composable
private fun RowScope.Wallet(value: String, label: String, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(rdp(12)))
            .background(ProfileTokens.Bg2)
            .border(rdp(1), ProfileTokens.Border, RoundedCornerShape(rdp(12)))
            .padding(vertical = rdp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(rdp(4)))
        XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
        XaniText(label, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 10f)
    }
}

@Composable
private fun Pill(label: String, modifier: Modifier, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .height(rdp(38))
            .clip(RoundedCornerShape(rdp(10)))
            .background(ProfileTokens.Bg2)
            .border(rdp(1), ProfileTokens.BorderStrong, RoundedCornerShape(rdp(10)))
            .pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(rdp(5)))
        }
        XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
    }
}

@Composable
private fun RowScope.FilterTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (selected) ProfileTokens.Accent else ProfileTokens.Muted
    Box(
        modifier = Modifier
            .weight(1f)
            .height(rdp(40))
            .pressable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon(tint)
            Box(
                Modifier
                    .padding(top = rdp(6))
                    .height(rdp(2))
                    .fillMaxWidth()
                    .background(if (selected) ProfileTokens.Accent else Color.Transparent),
            )
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(if (selected) ProfileTokens.AccentSoft else Color.Transparent)
            .border(
                rdp(1),
                if (selected) ProfileTokens.Accent else ProfileTokens.BorderStrong,
                RoundedCornerShape(rdp(20)),
            )
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(4)),
    ) {
        XaniText(
            label,
            Type.LabelBold,
            color = if (selected) ProfileTokens.AccentDeep else ProfileTokens.Sub,
            fontSizeOverride = 12f,
        )
    }
}

@Composable
private fun Hit(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
