package app.xani.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import app.xani.core.device.rememberDeviceClass
import app.xani.core.isolate.FeatureGate
import app.xani.core.net.AppForeground
import app.xani.core.net.LiveFeed
import kotlinx.coroutines.flow.collectLatest
import app.xani.feature.call.CallHost
import app.xani.feature.create.CreateScreen
import app.xani.feature.games.GamesScreen
import app.xani.feature.home.HomeScreen
import app.xani.feature.inbox.InboxController
import app.xani.feature.inbox.InboxScreen
import app.xani.feature.notify.PushRoute
import app.xani.feature.notify.PushRouter
import app.xani.feature.profile.PersonProfileScreen
import app.xani.feature.profile.ProfileScreen
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.PersonRouter
import app.xani.feature.roles.RolesRouter
import app.xani.feature.support.SupportRouter
import app.xani.feature.upload.PostingStrip
import app.xani.i18n.rememberCopy
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.MatchWindowTo
import app.xani.ui.theme.SystemBarIcons

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

    BackHandler(enabled = tab != MainTab.Home && !inboxImmersive && person == null) {
        tab = MainTab.Home
    }

    fun go(next: MainTab) {
        if (tab == next) return
        inboxImmersive = false
        profileImmersive = false
        visited = visited + next
        tab = next
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
                RolesRouter.openJoin(route.code)
            }
            is PushRoute.Live -> {
                PersonRouter.clear()
                go(MainTab.Home)
                PushRouter.liveRequest.value = route.roomId
            }
            PushRoute.Home -> go(MainTab.Home)
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
    val showHome = pinOff || tab == MainTab.Home
    fun keep(t: MainTab) = tab == t || (pinOff && t in visited)

    CompositionLocalProvider(LocalFeedBottomReserve provides reserve) {
        Box(Modifier.fillMaxSize()) {
            if (showHome) {
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
            if (keep(MainTab.Create)) {
                AlivePane(active = tab == MainTab.Create) {
                    FeatureGate(name = "create", light = chromeLight) {
                        CreateScreen(
                            copy = copy,
                            active = tab == MainTab.Create,
                            onClose = { go(MainTab.Home) },
                        )
                    }
                }
            }
            if (keep(MainTab.Inbox)) {
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

            // Calls cover everything, including the person sheet and the bar,
            // and survive tab switches because the controller owns them.
            CallHost(copy = copy, device = device)

            if (!hideBar) {
                MainBottomBar(
                    tab = tab,
                    onTab = { go(it) },
                    copy = copy,
                    inboxUnread = inboxUnread,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f),
                )
            }
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
