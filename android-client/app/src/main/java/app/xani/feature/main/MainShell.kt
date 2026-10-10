package app.xani.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.xani.core.device.rememberDeviceClass
import app.xani.core.isolate.FeatureGate
import app.xani.core.isolate.runFeature
import app.xani.core.net.AppForeground
import app.xani.core.net.LiveFeed
import app.xani.core.net.Perm
import app.xani.core.net.Xani
import app.xani.core.net.can
import kotlinx.coroutines.flow.collectLatest
import app.xani.feature.call.CallHost
import app.xani.feature.call.LiveRole
import app.xani.feature.call.LiveSession
import app.xani.feature.create.CreateScreen
import app.xani.feature.games.GamesScreen
import app.xani.feature.home.HomeScreen
import app.xani.feature.home.compactCount
import app.xani.feature.inbox.InboxController
import app.xani.feature.inbox.InboxScreen
import app.xani.feature.notify.PushRoute
import app.xani.feature.notify.PushRouter
import app.xani.feature.profile.PersonProfileScreen
import app.xani.feature.profile.ProfileScreen
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.feature.roles.RolesRouter
import app.xani.feature.support.SupportRouter
import app.xani.feature.upload.PostingStrip
import app.xani.i18n.Copy
import app.xani.i18n.rememberCopy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.MatchWindowTo
import app.xani.ui.theme.SystemBarIcons
import kotlin.math.roundToInt

/**
 * Five-tab shell. Visited tabs stay composed on mid/high devices so switching
 * is a layer change, not a remount. A failure in one slot cannot dispose Home.
 */
@Composable
fun MainShell() {
    val device = rememberDeviceClass()
    val copy = rememberCopy()
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var visited by remember { mutableStateOf(setOf(MainTab.Home)) }
    var inboxImmersive by rememberSaveable { mutableStateOf(false) }
    var profileImmersive by rememberSaveable { mutableStateOf(false) }
    // Games/Home go full-bleed too: an open game (Games tab) and a live broadcast
    // (Home tab) each own the whole screen, so the bottom nav steps aside.
    var gamesImmersive by rememberSaveable { mutableStateOf(false) }
    var homeImmersive by rememberSaveable { mutableStateOf(false) }
    // Read straight off the controller (attached below at startup), so the
    // badge counts chats + notices before the Inbox tab is ever opened.
    val inboxUnread = InboxController.shared.unreadTotal
    val reserve = feedBottomReserve()
    val pinOff = device.pinHomeOffTab

    // Each bottom-nav surface is its own permission, so an admin can hide any of
    // them per role from the dashboard. live.view is NOT one of these — it gates
    // only the Live lane inside Home (HomeScreen), so turning Live off never
    // takes the Home tab away. Games and Profile are always present as anchors,
    // and are the fallback when the current tab is revoked while the app is open.
    val canHome = can(Perm.HomeView)
    val canInbox = can(Perm.InboxView)
    // `can` is a @Composable (it reads the session state), so every one of these
    // must be called on every recomposition, in the same order. Folding them
    // into a `&&`/`||` expression would short-circuit the later calls the moment
    // an earlier permission turns false — which is exactly what happens when the
    // last account logs out and access drops to none. That skipped call corrupts
    // the composition slot table ("Boolean cannot be cast to MutableState") and
    // takes the app down instead of landing on the sign-in screen. Read each
    // permission into its own value first, then combine the plain booleans.
    val canCreateTab = can(Perm.CreateUse)
    val canPost = can(Perm.PostCreate)
    val canGoLive = can(Perm.GoLive)
    // Create needs at least one usable lane, or the tab would open empty.
    val canCreate = canCreateTab && (canPost || canGoLive)
    val fallbackTab = if (canHome) MainTab.Home else MainTab.Games
    LaunchedEffect(canHome, canCreate, canInbox) {
        val blocked = (tab == MainTab.Home && !canHome) ||
            (tab == MainTab.Create && !canCreate) ||
            (tab == MainTab.Inbox && !canInbox)
        if (blocked) tab = fallbackTab
    }

    // Somebody else's profile, opened from a feed card, a search hit or a list.
    // It lives here rather than inside a tab because every tab can raise it and
    // it has to cover the bottom bar while it is up.
    val people by PersonRouter.stack.collectAsState()
    val person = people.lastOrNull()

    // One realtime socket for the whole signed-in session, owned by the shell
    // rather than by whichever screen happens to want freshness.
    //
    // This is what makes chat behave: a message, a tick or "online" has to
    // arrive while the user is on Home, and the other side has to see them as
    // online for as long as the app is in front of them — not only while the
    // inbox is the visible tab. collectLatest drops the hold the moment the
    // app is backgrounded, so a phone in a pocket holds no stream.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        InboxController.shared.attach(context)
        AppForeground.visible.collectLatest { visible -> if (visible) LiveFeed.hold() }
    }

    val chromeLight = if (tab.cinematic) false else !Colors.isDark
    SystemBarIcons(lightBackground = if (person != null) true else chromeLight)
    MatchWindowTo(if (person != null || chromeLight) Colors.Background else Dark.Background)

    BackHandler(enabled = tab != fallbackTab && !inboxImmersive && person == null) {
        tab = fallbackTab
    }

    fun go(next: MainTab) {
        // Never land on a hidden surface (push route, deep link): clamp to the
        // fallback so a revoked tab can't strand the user on a blank pane.
        val target = when {
            next == MainTab.Home && !canHome -> fallbackTab
            next == MainTab.Create && !canCreate -> fallbackTab
            next == MainTab.Inbox && !canInbox -> fallbackTab
            else -> next
        }
        if (tab == target) return
        inboxImmersive = false
        profileImmersive = false
        visited = visited + target
        tab = target
    }

    // "Message" on a profile: the shell owns the tab, so it is the one that can
    // move to Inbox. The inbox itself opens the thread and clears the request.
    val chatRequest by InboxController.shared.openRequest
    LaunchedEffect(chatRequest) {
        if (chatRequest != null) {
            PersonRouter.clear()
            go(MainTab.Inbox)
        }
    }

    // A tapped notification, from any app state: the router parsed and
    // validated it; the shell only decides which tab owns the screen.
    val pushRoute by PushRouter.pending.collectAsState()
    LaunchedEffect(pushRoute) {
        val route = PushRouter.consume() ?: return@LaunchedEffect
        val inbox = InboxController.shared
        when (route) {
            is PushRoute.Conversation -> {
                PersonRouter.clear()
                go(MainTab.Inbox)
                inbox.requestConversation(route.convId)
            }
            is PushRoute.Post -> {
                PersonRouter.clear()
                go(MainTab.Inbox)
                inbox.postRequest.value = route.postId
            }
            PushRoute.Notices -> {
                PersonRouter.clear()
                go(MainTab.Inbox)
                inbox.noticesRequest.value = true
            }
            is PushRoute.Profile -> PersonRouter.open(route.userId)
            is PushRoute.ProfileUsername -> {
                val id = runFeature(0L) {
                    Xani.people.resolveUsername(route.username).value?.takeIf { it > 0L } ?: 0L
                }
                if (id > 0L) PersonRouter.open(id)
            }
            is PushRoute.SupportTicket -> {
                PersonRouter.clear()
                go(MainTab.Profile)
                SupportRouter.openTicket(route.ticketId)
            }
            is PushRoute.RoleRequest -> {
                PersonRouter.clear()
                go(MainTab.Profile)
                RolesRouter.openRequest(route.requestId)
            }
            is PushRoute.Join -> {
                PersonRouter.clear()
                go(MainTab.Profile)
                RolesRouter.openJoin(route.code, route.roleSlug)
            }
            is PushRoute.Live -> {
                PersonRouter.clear()
                go(fallbackTab)
                PushRouter.liveRequest.value = route.roomId
            }
            PushRoute.Home -> go(fallbackTab)
        }
    }

    // Inbox notice tap (and any other SupportRouter.openTicket): land on Profile.
    val supportTicket by SupportRouter.pending.collectAsState()
    LaunchedEffect(supportTicket) {
        if ((supportTicket ?: 0L) > 0L) {
            PersonRouter.clear()
            go(MainTab.Profile)
        }
    }

    // Role request / decision notices open the roles screen, which lives under Profile.
    val roleRequest by RolesRouter.pending.collectAsState()
    LaunchedEffect(roleRequest) {
        if (roleRequest != null) {
            PersonRouter.clear()
            go(MainTab.Profile)
        }
    }

    // A tap on my own name anywhere is my own profile, with my own controls —
    // not a visitor's view of myself.
    LaunchedEffect(person) {
        if (person != null && person == ProfileStore.currentUserId) {
            PersonRouter.clear()
            go(MainTab.Profile)
        }
    }

    val hideBar = (tab == MainTab.Inbox && inboxImmersive) ||
        tab == MainTab.Create ||
        (tab == MainTab.Profile && profileImmersive) ||
        (tab == MainTab.Games && gamesImmersive) ||
        (tab == MainTab.Home && homeImmersive) ||
        person != null
    val homeAlive = canHome && (pinOff || tab == MainTab.Home)
    // A host who minimized their broadcast is still that broadcast: the live
    // room disposes with the Create pane (and a disposed room ends the live
    // for everyone), so while hosting the pane stays composed on every device
    // class and the shell floats a return card over the other tabs.
    val liveUi by LiveSession.state.collectAsState()
    val hosting = liveUi.let { it != null && it.role == LiveRole.Host && !it.over }
    fun keep(t: MainTab) = tab == t || (pinOff && t in visited) || (t == MainTab.Create && hosting)

    CompositionLocalProvider(LocalFeedBottomReserve provides reserve) {
        Box(Modifier.fillMaxSize()) {
            if (homeAlive) {
                AlivePane(active = tab == MainTab.Home) {
                    FeatureGate(name = "home") {
                        HomeScreen(
                            active = tab == MainTab.Home,
                            device = device,
                            copy = copy,
                            onImmersive = { homeImmersive = it },
                        )
                    }
                }
            }
            if (keep(MainTab.Games)) {
                AlivePane(active = tab == MainTab.Games) {
                    FeatureGate(name = "games", light = chromeLight) {
                        GamesScreen(copy, onImmersive = { gamesImmersive = it })
                    }
                }
            }
            if (keep(MainTab.Create) && canCreate) {
                AlivePane(active = tab == MainTab.Create) {
                    FeatureGate(name = "create", light = chromeLight) {
                        CreateScreen(
                            copy = copy,
                            active = tab == MainTab.Create,
                            onClose = { go(fallbackTab) },
                            // Minimize keeps the user in the app: the broadcast
                            // stays composed in this pane and the card below
                            // brings them back — backgrounding the whole task
                            // read as "the app closed" to hosts.
                            onMinimize = { go(fallbackTab) },
                        )
                    }
                }
            }
            if (keep(MainTab.Inbox) && canInbox) {
                AlivePane(active = tab == MainTab.Inbox) {
                    FeatureGate(name = "inbox", light = chromeLight) {
                        InboxScreen(
                            copy = copy,
                            device = device,
                            onImmersive = { inboxImmersive = it },
                            onUnread = {},
                        )
                    }
                }
            }
            if (keep(MainTab.Profile)) {
                AlivePane(active = tab == MainTab.Profile) {
                    FeatureGate(name = "profile", light = chromeLight) {
                        ProfileScreen(
                            copy = copy,
                            device = device,
                            onImmersive = { profileImmersive = it },
                        )
                    }
                }
            }

            person?.let { id ->
                Box(Modifier.fillMaxSize().zIndex(4f)) {
                    FeatureGate(name = "person", light = true) {
                        PersonProfileScreen(
                            userId = id,
                            copy = copy,
                            device = device,
                            onClose = { PersonRouter.back() },
                        )
                    }
                }
            }

            // Posting / Posted strip follows the user across tabs; hidden while
            // the camera or a full-screen surface owns the top edge.
            if (tab != MainTab.Create && person == null) {
                PostingStrip(copy = copy, modifier = Modifier.align(Alignment.TopCenter).zIndex(3f))
            }

            // The minimized broadcast's way back: one tap returns to the live
            // room, which never left composition. Hidden on the Create tab
            // (the room itself is on screen) and under a person sheet.
            if (hosting && tab != MainTab.Create && person == null) {
                HostLiveCard(
                    viewers = liveUi?.viewers ?: 0L,
                    copy = copy,
                    onClick = { go(MainTab.Create) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = rdp(14), bottom = rdp(96))
                        .zIndex(3f),
                )
            }

            // Calls cover everything, including the person sheet and the bar,
            // and survive tab switches because the controller owns them.
            CallHost(copy = copy, device = device)

            if (!hideBar) {
                MainBottomBar(
                    tab = tab,
                    onTab = { go(it) },
                    copy = copy,
                    showHome = canHome,
                    showCreate = canCreate,
                    showInbox = canInbox,
                    inboxUnread = inboxUnread,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f),
                )
            }
        }
    }
}

/**
 * The minimized broadcast, floating over every tab as the same draggable
 * mini-card a viewer gets: the host's picture under a pulsing LIVE chip,
 * their name and the room's size below. The broadcast itself never stopped —
 * its pane is still composed — so the tap is a tab switch, not a rebuild.
 */
@Composable
private fun HostLiveCard(
    viewers: Long,
    copy: Copy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val me by ProfileStore.state.collectAsState()
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    val pulse by rememberInfiniteTransition(label = "live-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "live-dot",
    )
    Column(
        modifier
            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            .width(rdp(140))
            .clip(RoundedCornerShape(rdp(14)))
            .background(Brush.linearGradient(listOf(Color(0xFF1A0836), Color(0xFF2D0F5C))))
            .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.55f), RoundedCornerShape(rdp(14)))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offX += drag.x
                    offY += drag.y
                }
            }
            .pressable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rdp(80))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x8C8B5CF6), Color(0x66EC4899)),
                    ),
                ),
        ) {
            Box(Modifier.align(Alignment.Center)) {
                PersonAvatar(ProfileStore.currentUserId, me.displayName, me.avatarUrl, rdp(38))
            }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = rdp(7), top = rdp(6))
                    .clip(RoundedCornerShape(rdp(8)))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = rdp(6), vertical = rdp(2)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(4)),
            ) {
                Box(
                    Modifier
                        .size(rdp(7))
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = pulse)),
                )
                XaniText(copy.t(Copy.LiveBadge), Type.LabelBold, color = Color.White, fontSizeOverride = 9f)
            }
        }
        Column(Modifier.padding(horizontal = rdp(8), vertical = rdp(6))) {
            XaniText(
                me.displayName.ifBlank { me.username },
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 11f,
                maxLines = 1,
            )
            XaniText(
                copy.t(Copy.LiveWatching, "n" to compactCount(viewers)),
                Type.Caption,
                color = Color.White.copy(alpha = 0.5f),
                fontSizeOverride = 10f,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AlivePane(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else 0f)
            .then(if (active) Modifier else Modifier.offset { IntOffset(-10_000, 0) })
            .graphicsLayer { alpha = if (active) 1f else 0f },
    ) {
        content()
    }
}
