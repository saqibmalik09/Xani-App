package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Call and live wire types. Signalling only: media never crosses this layer.
 * Ids are strings on the wire and Longs here.
 */

@Immutable
data class CallerBrief(val id: Long, val username: String, val fullName: String, val avatarUrl: String) {
    companion object {
        fun from(json: JSONObject) = CallerBrief(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
        )
    }
}

@Immutable
data class CallParticipant(val userId: Long, val state: String)

@Immutable
data class CallData(
    val id: Long,
    val conversationId: Long,
    /** audio | video */
    val kind: String,
    /** p2p | sfu */
    val mode: String,
    /** ringing | active | ended */
    val state: String,
    val callerId: Long,
    val caller: CallerBrief,
    val participants: List<CallParticipant>,
    val endReason: String,
    val seq: Long,
) {
    companion object {
        fun from(json: JSONObject) = CallData(
            id = json.id("id"),
            conversationId = json.id("conversation_id"),
            kind = json.str("kind", "audio"),
            mode = json.str("mode", "p2p"),
            state = json.str("state", "ringing"),
            callerId = json.id("caller_id"),
            caller = CallerBrief.from(json.obj("caller") ?: JSONObject()),
            participants = json.objects("participants").map { CallParticipant(it.id("user_id"), it.str("state")) },
            endReason = json.str("end_reason"),
            seq = json.long("seq"),
        )
    }
}

@Immutable
data class IceServerData(val urls: List<String>, val username: String, val credential: String)

@Immutable
data class IceConfigData(
    val servers: List<IceServerData>,
    val ttlSeconds: Int,
    val reconnectGraceSeconds: Int,
    val relayOnly: Boolean,
) {
    companion object {
        val Empty = IceConfigData(emptyList(), 0, 150, false)

        fun from(json: JSONObject) = IceConfigData(
            servers = json.objects("servers").map {
                IceServerData(it.strings("urls"), it.str("username"), it.str("credential"))
            },
            ttlSeconds = json.int("ttl_seconds"),
            reconnectGraceSeconds = json.int("reconnect_grace_seconds", 150),
            relayOnly = json.bool("relay_only"),
        )
    }
}

@Immutable
data class RoomJoinData(val url: String, val token: String, val room: String) {
    companion object {
        fun from(json: JSONObject) = RoomJoinData(json.str("url"), json.str("token"), json.str("room"))
    }
}

@Immutable
data class CallStartData(val call: CallData, val ice: IceConfigData, val calleeOnline: Boolean, val room: RoomJoinData?)

@Immutable
data class CallAcceptData(val call: CallData, val ice: IceConfigData, val room: RoomJoinData?)

@Immutable
data class LiveHostData(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val country: String,
    /** The avatar frame the host is wearing; key or absolute URL. */
    val frameUrl: String = "",
    val frameMime: String = "",
    /** The badge worn next to the name; key or absolute URL. */
    val badgeUrl: String = "",
    val badgeMime: String = "",
    /** Gifting rungs reached; null = none yet (nothing is drawn). */
    val senderLevel: LevelData? = null,
    val receiverLevel: LevelData? = null,
) {
    companion object {
        val Empty = LiveHostData(0L, "", "", "", "")
        fun from(json: JSONObject?) = if (json == null) Empty else LiveHostData(
            id = json.id("id"), username = json.str("username"), fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"), country = json.str("country"),
            frameUrl = json.str("frame_url"), frameMime = json.str("frame_mime"),
            badgeUrl = json.str("badge_url"), badgeMime = json.str("badge_mime"),
            senderLevel = LevelData.fromOrNull(json.obj("sender_level")),
            receiverLevel = LevelData.fromOrNull(json.obj("receiver_level")),
        )
    }
}

@Immutable
data class LiveRoomData(
    val id: Long,
    val hostId: Long,
    val kind: String,
    val title: String,
    val viewers: Long,
    val live: Boolean,
    val host: LiveHostData = LiveHostData.Empty,
    val coverUrl: String = "",
    /** #RRGGBB chosen at go-live; the listing card gradient when there is no cover. */
    val tint: String = "",
    /** Server clock, epoch ms. */
    val startedAtMs: Long = 0L,
    /** Server-computed seconds live at the time of this snapshot. */
    val durationSeconds: Long = 0L,
    val peakViewers: Long = 0L,
    val guests: Int = 0,
    /** public | friends | private. */
    val audience: String = "public",
    /** Private room: joining needs the host's password. */
    val locked: Boolean = false,
    /** Host missed a heartbeat: the picture is frozen on their side. */
    val hostAway: Boolean = false,
    /** Coins gifted into the room so far. */
    val giftCoins: Long = 0L,
    /** Diamonds actually credited to receivers here — the 💎 pill's number. */
    val giftDiamonds: Long = 0L,
    /** Audio seats, host included (0 on a video room). */
    val seats: Int = 0,
    /** Video: viewers may ask to co-host. */
    val requests: Boolean = true,
    /** Active PK battle; null when idle. */
    val pk: PkData? = null,
) {
    companion object {
        fun from(json: JSONObject) = LiveRoomData(
            id = json.id("id"), hostId = json.id("host_id"), kind = json.str("kind", "video"),
            title = json.str("title"), viewers = json.long("viewers"), live = json.bool("live", true),
            host = LiveHostData.from(json.obj("host")), coverUrl = json.str("cover_url"), tint = json.str("tint"),
            startedAtMs = json.long("started_at_ms"), durationSeconds = json.long("duration_seconds"),
            peakViewers = json.long("peak_viewers"), guests = json.int("guests"),
            audience = json.str("audience", "public"), locked = json.bool("locked"),
            hostAway = json.bool("host_away"), giftCoins = json.long("gift_coins"),
            giftDiamonds = json.long("gift_diamonds"),
            seats = json.int("seats"), requests = json.bool("requests", true),
            pk = json.obj("pk")?.let(PkData::from),
        )
    }
}

/** One half of a PK battle. */
@Immutable
data class PkSideData(
    val roomId: Long,
    val host: LiveHostData,
    val score: Long,
    val hlsUrl: String,
) {
    companion object {
        fun from(json: JSONObject) = PkSideData(
            roomId = json.id("room_id"),
            host = LiveHostData.from(json.obj("host")),
            score = json.long("score"),
            hlsUrl = json.str("hls_url"),
        )
    }
}

/**
 * Linked-room PK snapshot. phase: fighting | result | ended.
 * Scores are coins gifted to each host during the fight.
 */
@Immutable
data class PkData(
    val id: Long,
    val phase: String,
    /** 1 for the first fight; a rematch bumps it. */
    val round: Int,
    val durationSec: Int,
    val startedAtMs: Long,
    val endsAtMs: Long,
    val resultUntilMs: Long,
    val left: PkSideData,
    val right: PkSideData,
    val winnerId: Long,
    val rematchLeft: Boolean,
    val rematchRight: Boolean,
) {
    val fighting: Boolean get() = phase == "fighting"
    val showingResult: Boolean get() = phase == "result"
    val draw: Boolean get() = showingResult && winnerId == 0L

    /**
     * The opponent closed their own live, so the battle is over but this room
     * is still on air. A local phase, never one the server sends: it holds the
     * arena long enough to say the other side left.
     */
    val opponentLeft: Boolean get() = phase == PhaseLeft

    fun sideFor(hostId: Long): PkSideData? = when (hostId) {
        left.host.id -> left
        right.host.id -> right
        else -> null
    }

    fun opponentOf(hostId: Long): PkSideData? = when (hostId) {
        left.host.id -> right
        right.host.id -> left
        else -> null
    }

    companion object {
        const val PhaseLeft = "left"

        fun from(json: JSONObject) = PkData(
            id = json.id("id"),
            phase = json.str("phase", "fighting"),
            round = json.int("round", 1).coerceAtLeast(1),
            durationSec = json.int("duration_sec"),
            startedAtMs = json.long("started_at_ms"),
            endsAtMs = json.long("ends_at_ms"),
            resultUntilMs = json.long("result_until_ms"),
            left = PkSideData.from(json.obj("left") ?: JSONObject()),
            right = PkSideData.from(json.obj("right") ?: JSONObject()),
            winnerId = json.id("winner_id"),
            rematchLeft = json.bool("rematch_left"),
            rematchRight = json.bool("rematch_right"),
        )
    }
}

@Immutable
data class PkInviteData(
    val id: Long,
    val fromRoomId: Long,
    val fromHost: LiveHostData,
    val durationSec: Int,
    val atMs: Long,
    /** remove | declined | empty = new invite */
    val op: String = "",
) {
    companion object {
        fun from(json: JSONObject) = PkInviteData(
            id = json.id("id"),
            fromRoomId = json.id("from_room_id"),
            fromHost = LiveHostData.from(json.obj("from_host")),
            durationSec = json.int("duration_sec"),
            atMs = json.long("at_ms"),
            op = json.str("op"),
        )
    }
}

@Immutable
data class PkHostHitData(
    val roomId: Long,
    val host: LiveHostData,
    val title: String,
    val kind: String,
    val viewers: Long,
) {
    companion object {
        fun from(json: JSONObject) = PkHostHitData(
            roomId = json.id("room_id"),
            host = LiveHostData.from(json.obj("host")),
            title = json.str("title"),
            kind = json.str("kind", "video"),
            viewers = json.long("viewers"),
        )
    }
}

@Immutable
data class PkStartData(val pk: PkData, val sfu: RoomJoinData) {
    companion object {
        fun from(json: JSONObject) = PkStartData(
            pk = PkData.from(json.obj("pk") ?: json),
            sfu = RoomJoinData.from(json.obj("sfu") ?: JSONObject()),
        )
    }
}

/** One seat on the stage: who, which seat, mic, and what they were gifted up there. */
@Immutable
data class StageMemberData(
    val user: LivePersonData,
    val seat: Int,
    val muted: Boolean,
    val satAtMs: Long,
    val coins: Long,
    /**
     * This person is publishing their camera. Their own choice, never the
     * host's: it stays false until they turn it on, so a tile draws the
     * profile picture and not a stranger's room.
     */
    val cam: Boolean = false,
) {
    companion object {
        fun from(json: JSONObject) = StageMemberData(
            user = LivePersonData.from(json.obj("user") ?: JSONObject()),
            seat = json.int("seat"),
            muted = json.bool("muted"),
            satAtMs = json.long("sat_at_ms"),
            coins = json.long("coins"),
            cam = json.bool("cam"),
        )
    }
}

/**
 * The stage snapshot: seats (host at 0), whether viewers may ask to join,
 * and everyone up right now. Replaced wholesale on every `live_stage` frame.
 */
@Immutable
data class StageData(
    val roomId: Long,
    val kind: String,
    val seats: Int,
    val requests: Boolean,
    val members: List<StageMemberData>,
    val rev: Long,
    /** Video: co-hosts may turn their camera on. Host's choice, default off. */
    val guestCam: Boolean = false,
) {
    /** Everyone but the host, by seat. */
    val guests: List<StageMemberData> get() = members.filter { it.seat != 0 }

    fun member(userId: Long): StageMemberData? = members.firstOrNull { it.user.id == userId }
    fun at(seat: Int): StageMemberData? = members.firstOrNull { it.seat == seat }

    companion object {
        /** Audio rooms: host is seat 0, then guests 1…; cap includes the host. */
        const val AudioSeatMax = 45
        val Empty = StageData(0L, "video", 0, true, emptyList(), 0L)
        fun from(json: JSONObject?) = if (json == null) Empty else StageData(
            roomId = json.id("room_id"),
            kind = json.str("kind", "video"),
            seats = json.int("seats"),
            requests = json.bool("requests", true),
            members = json.objects("members").map(StageMemberData::from).sortedBy { it.seat },
            rev = json.long("rev"),
            guestCam = json.bool("guest_cam"),
        )
    }
}

/** A viewer waiting for the host's yes. */
@Immutable
data class StageRequestData(val user: LivePersonData, val atMs: Long) {
    companion object {
        fun from(json: JSONObject) = StageRequestData(
            user = LivePersonData.from(json.obj("user") ?: JSONObject()),
            atMs = json.long("at_ms"),
        )
    }
}

/** Sitting down: the seat taken and the publishing door. */
@Immutable
data class StageSeatData(val seat: Int, val sfu: RoomJoinData) {
    companion object {
        fun from(json: JSONObject) = StageSeatData(json.int("seat"), RoomJoinData.from(json.obj("sfu") ?: JSONObject()))
    }
}

/** Someone in the room: host, guest on stage, or viewer. */
@Immutable
data class LivePersonData(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    /** host | guest | viewer */
    val role: String,
    val joinedAtMs: Long = 0L,
    /** The avatar frame this person is wearing; key or absolute URL. */
    val frameUrl: String = "",
    val frameMime: String = "",
    /** The badge worn next to the name; key or absolute URL. */
    val badgeUrl: String = "",
    val badgeMime: String = "",
    /** Gifting rungs reached; null = none yet (nothing is drawn). */
    val senderLevel: LevelData? = null,
    val receiverLevel: LevelData? = null,
    /** One of the host's moderators. */
    val mod: Boolean = false,
) {
    companion object {
        fun from(json: JSONObject) = LivePersonData(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
            role = json.str("role", "viewer"),
            joinedAtMs = json.long("joined_at_ms"),
            frameUrl = json.str("frame_url"),
            frameMime = json.str("frame_mime"),
            badgeUrl = json.str("badge_url"),
            badgeMime = json.str("badge_mime"),
            senderLevel = LevelData.fromOrNull(json.obj("sender_level")),
            receiverLevel = LevelData.fromOrNull(json.obj("receiver_level")),
            mod = json.bool("mod"),
        )
    }
}

/** One of a host's moderators and when the appointment lapses. */
@Immutable
data class LiveModeratorData(val user: LivePersonData, val expiresMs: Long) {
    companion object {
        fun from(json: JSONObject) = LiveModeratorData(
            user = LivePersonData.from(json.obj("user") ?: JSONObject()),
            expiresMs = json.long("expires_ms"),
        )
    }
}

/**
 * One "removed this live" row: who was blocked or kicked in the current
 * broadcast. The record is the room's own memory — it dies with the live, so
 * a host's block is never a standing sentence. untilMs 0 = the whole session.
 */
data class LiveBannedData(
    val user: LivePersonData,
    val reason: String,
    val untilMs: Long,
    val atMs: Long,
    val active: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = LiveBannedData(
            user = LivePersonData.from(json.obj("user") ?: JSONObject()),
            reason = json.str("reason"),
            untilMs = json.long("until_ms"),
            atMs = json.long("at_ms"),
            active = json.bool("active"),
        )
    }
}

@Immutable
data class LivePeopleData(val items: List<LivePersonData>, val viewers: Long) {
    companion object {
        fun from(json: JSONObject) = LivePeopleData(
            items = json.objects("items").map(LivePersonData::from),
            viewers = json.long("viewers"),
        )
    }
}

/** One ephemeral chat line in a live room. */
@Immutable
data class LiveChatData(
    val id: Long,
    val roomId: Long,
    /** Sender's optimistic id; empty on lines that arrived only on the stream. */
    val clientId: String,
    val user: LivePersonData,
    val text: String,
    val atMs: Long,
    /** A picture from the sticker tray; text may be empty on such a line. */
    val sticker: StickerData? = null,
    /** Who the line is aimed at ("@name" above the sticker); null = the room. */
    val to: LivePersonData? = null,
    /** Emoji reactions on this line so far (emoji → count), from room frames. */
    val reactions: Map<String, Int> = emptyMap(),
    /** The emoji I put on this line, or empty. */
    val myReaction: String = "",
    /** A system line the device words itself ("like": sent hearts); empty for typed lines. */
    val event: String = "",
) {
    companion object {
        fun from(json: JSONObject) = LiveChatData(
            id = json.id("id"),
            roomId = json.id("room_id"),
            clientId = json.str("client_id"),
            user = LivePersonData.from(json.obj("user") ?: JSONObject()),
            text = json.str("text"),
            atMs = json.long("at_ms"),
            sticker = json.obj("sticker")?.let(StickerData::from)?.takeIf { it.id != 0L },
            to = json.obj("to")?.let(LivePersonData::from)?.takeIf { it.id != 0L },
            event = json.str("event"),
        )
    }
}

/** The host's end card: the server's clock and counters, never the phone's. */
@Immutable
data class LiveSummaryData(
    val roomId: Long,
    val durationSeconds: Int,
    val peakViewers: Int,
    val totalJoins: Int,
    val uniqueViewers: Int,
    val reason: String,
    val giftCoins: Long = 0L,
    /** Diamonds credited to receivers after the split — the end card's 💎. */
    val giftDiamonds: Long = 0L,
    val giftCount: Int = 0,
    /**
     * The ledger's number for this room — what the host is credited. Wall
     * time ([durationSeconds]) may be larger: silence inside the room
     * ([pausedMs]) is never counted. -1 when the server did not answer.
     */
    val verifiedMs: Long = -1L,
    val pausedMs: Long = 0L,
    /** The host's accounting-day total after this room; -1 when unknown. */
    val todayMs: Long = -1L,
) {
    companion object {
        fun from(json: JSONObject?) = LiveSummaryData(
            roomId = json?.id("room_id") ?: 0L,
            durationSeconds = json?.int("duration_seconds") ?: 0,
            peakViewers = json?.int("peak_viewers") ?: 0,
            totalJoins = json?.int("total_joins") ?: 0,
            uniqueViewers = json?.int("unique_viewers") ?: 0,
            reason = json?.str("reason") ?: "",
            giftCoins = json?.long("gift_coins") ?: 0L,
            giftDiamonds = json?.long("gift_diamonds") ?: 0L,
            giftCount = json?.int("gift_count") ?: 0,
            verifiedMs = json?.long("verified_ms", -1L) ?: -1L,
            pausedMs = json?.long("paused_ms") ?: 0L,
            todayMs = json?.long("today_ms", -1L) ?: -1L,
        )
    }
}

/**
 * The verified-time HUD contract, carried on start and on every host /
 * co-host heartbeat. The device shows this and only this: [verifiedMs] is the
 * ledger's figure as of [serverNowMs]; the screen advances it locally while
 * [counting] and stops once the last ack is older than [allowanceSeconds] —
 * exactly when the server stops too.
 */
@Immutable
data class LiveTimeView(
    val verifiedMs: Long,
    val counting: Boolean,
    val pausedMs: Long,
    val segmentNo: Int,
    val serverNowMs: Long,
    val allowanceSeconds: Int,
) {
    companion object {
        fun from(json: JSONObject?): LiveTimeView? {
            json ?: return null
            return LiveTimeView(
                verifiedMs = json.long("verified_ms"),
                counting = json.bool("counting"),
                pausedMs = json.long("paused_ms"),
                segmentNo = json.int("segment_no"),
                serverNowMs = json.long("server_now_ms"),
                allowanceSeconds = json.int("allowance_seconds", 30).coerceAtLeast(1),
            )
        }
    }
}

@Immutable
data class LiveStartData(
    val room: LiveRoomData,
    val sfu: RoomJoinData,
    val hlsUrl: String,
    val reconnectGraceSeconds: Int,
    val heartbeatSeconds: Int,
    /** A stale room this start replaced (the previous End never arrived). */
    val replacedRoomId: Long,
    val stage: StageData = StageData.Empty,
    /** The HUD's starting point: the ledger's zero, or its figure on a resume. */
    val time: LiveTimeView? = null,
    /** The same go-live (client_id) was already open; this is that room, not a new one. */
    val resumed: Boolean = false,
)

@Immutable
data class LiveJoinData(
    val room: LiveRoomData,
    /** sfu | hls */
    val mode: String,
    val sfu: RoomJoinData?,
    val hlsUrl: String,
    val reconnectGraceSeconds: Int,
    val heartbeatSeconds: Int,
    val stage: StageData = StageData.Empty,
    /** I am one of the host's moderators here. */
    val mod: Boolean = false,
    /** Hearts tapped in the room so far. */
    val likes: Long = 0L,
)

@Immutable
data class LiveHeartbeatData(
    val room: LiveRoomData,
    val live: Boolean,
    /** Host / co-host: the ledger's running figure. Null for viewers. */
    val time: LiveTimeView? = null,
)

/** One call or live frame off the stream, already typed. */
sealed class CallEvent {
    data class Invite(val call: CallData) : CallEvent()
    data class Ringing(val callId: Long, val userId: Long) : CallEvent()
    data class Accept(val callId: Long, val userId: Long, val deviceId: Long) : CallEvent()
    data class Reject(val callId: Long, val userId: Long, val reason: String) : CallEvent()
    data class Taken(val callId: Long, val deviceId: Long) : CallEvent()
    data class End(val callId: Long, val reason: String) : CallEvent()
    data class Signal(
        val callId: Long,
        val fromUser: Long,
        val fromDevice: Long,
        val toDevice: Long,
        val type: String,
        val payload: JSONObject,
    ) : CallEvent()
    data class Participant(val callId: Long, val userId: Long, val state: String) : CallEvent()
    data class Room(val room: RoomJoinData) : CallEvent()
    data class LiveViewers(val roomId: Long, val viewers: Long) : CallEvent()
    /** [regrant]: already on stage — a fresh door because the host changed the camera rule. */
    data class LiveGuest(
        val roomId: Long,
        val sfu: RoomJoinData,
        val seat: Int = 0,
        val regrant: Boolean = false,
        val guestCam: Boolean = false,
    ) : CallEvent()
    data class LiveEnded(val roomId: Long, val reason: String, val summary: LiveSummaryData?) : CallEvent()
    /** Host only: someone asked to co-host (`add`) or is no longer asking (`remove`). */
    data class LiveStageRequest(val roomId: Long, val op: String, val user: LivePersonData, val atMs: Long) : CallEvent()
    /** The host declined my co-host request. */
    data class LiveStageDenied(val roomId: Long) : CallEvent()
    /** I am off the stage (dropped, or left from another device); [sfu] is a viewer seat, or null. */
    data class LiveStageOff(val roomId: Long, val reason: String, val sfu: RoomJoinData?) : CallEvent()
    /** Someone invited me to a PK battle (or withdrew / declined). */
    data class LivePkInvite(val invite: PkInviteData) : CallEvent()
    /** Host-only: PK started / rematch — open a publish seat in the opponent's SFU. */
    data class LivePkStart(val start: PkStartData) : CallEvent()
    /** Live tab: a room appeared, changed audience, or went away. */
    data class LiveDirectory(val op: String, val room: LiveRoomData) : CallEvent()
    /** The host's link went quiet (`away=true`) or came back. */
    data class LiveHost(val roomId: Long, val away: Boolean) : CallEvent()
    /** One chat line in a room this device is in. */
    data class LiveChat(val message: LiveChatData) : CallEvent()
    /** A moderator removed me from the room; I may come back at [untilMs]. */
    data class LiveKicked(val roomId: Long, val reason: String, val untilMs: Long) : CallEvent()
    /** My chat is muted until [untilMs] (0 = lifted). */
    data class LiveMuted(val roomId: Long, val untilMs: Long) : CallEvent()
    /** Someone became (or stopped being) one of the host's moderators. */
    data class LiveMods(val roomId: Long, val user: LivePersonData, val mod: Boolean, val expiresMs: Long) : CallEvent()
    /**
     * Someone with an entrance effect walked into a room this device is in.
     * Rides the per-user stream (the room fanout targets each person's own
     * channel), so the enterer sees their own entrance too.
     */
    data class LiveEnter(val enter: LiveEnterEvent) : CallEvent()

    companion object {
        fun from(name: String, data: JSONObject): CallEvent? = when (name) {
            "call_invite" -> Invite(CallData.from(data))
            "call_ringing" -> Ringing(data.id("call_id"), data.id("user_id"))
            "call_accept" -> Accept(data.id("call_id"), data.id("user_id"), data.id("device_id"))
            "call_reject" -> Reject(data.id("call_id"), data.id("user_id"), data.str("reason"))
            "call_taken" -> Taken(data.id("call_id"), data.id("device_id"))
            "call_end" -> End(data.id("call_id"), data.str("reason"))
            "call_signal" -> Signal(
                callId = data.id("call_id"),
                fromUser = data.id("from_user"),
                fromDevice = data.id("from_device"),
                toDevice = data.id("to_device"),
                type = data.str("type"),
                payload = data.obj("payload") ?: JSONObject(),
            )
            "call_participant" -> Participant(data.id("call_id"), data.id("user_id"), data.str("state"))
            "call_room" -> Room(RoomJoinData.from(data))
            "live_viewers" -> LiveViewers(data.id("room_id"), data.long("viewers"))
            "live_guest" -> LiveGuest(data.id("room_id"), RoomJoinData.from(data.obj("sfu") ?: JSONObject()), data.int("seat"), data.bool("regrant"), data.bool("guest_cam"))
            "live_stage_request" -> LiveStageRequest(
                data.id("room_id"), data.str("op", "add"),
                LivePersonData.from(data.obj("user") ?: JSONObject()), data.long("at_ms"),
            )
            "live_stage_denied" -> LiveStageDenied(data.id("room_id"))
            "live_stage_off" -> LiveStageOff(
                data.id("room_id"), data.str("reason"),
                data.obj("sfu")?.let(RoomJoinData::from)?.takeIf { it.token.isNotBlank() },
            )
            "live_pk_invite" -> LivePkInvite(PkInviteData.from(data))
            "live_pk_start" -> LivePkStart(PkStartData.from(data))
            "live_ended" -> LiveEnded(data.id("room_id"), data.str("reason"), data.obj("summary")?.let(LiveSummaryData::from))
            "live_directory" -> data.obj("room")?.let { LiveDirectory(data.str("op", "upsert"), LiveRoomData.from(it)) }
            "live_host" -> LiveHost(data.id("room_id"), data.bool("away"))
            "live_chat" -> LiveChat(LiveChatData.from(data))
            "live_kicked" -> LiveKicked(data.id("room_id"), data.str("reason"), data.long("until_ms"))
            "live_muted" -> LiveMuted(data.id("room_id"), data.long("until_ms"))
            "live_mods" -> LiveMods(
                data.id("room_id"), LivePersonData.from(data.obj("user") ?: JSONObject()),
                data.bool("mod"), data.long("expires_ms"),
            )
            "live_enter" -> LiveEnter(LiveEnterEvent.from(data))
            else -> null
        }
    }
}

/** Typed call + live signalling endpoints. */
class CallApi internal constructor(private val api: ApiClient) {

    suspend fun start(conversationId: Long, kind: String, clientId: String, relayOnly: Boolean): ApiResponse<CallStartData> =
        api.post(
            "/v1/calls",
            jsonOf("conversation_id" to conversationId.toString(), "kind" to kind, "client_id" to clientId, "relay_only" to relayOnly),
            authenticated = true,
        ) { d ->
            CallStartData(
                call = CallData.from(d.obj("call") ?: JSONObject()),
                ice = IceConfigData.from(d.obj("ice") ?: JSONObject()),
                calleeOnline = d.bool("callee_online"),
                room = d.obj("room")?.let(RoomJoinData::from),
            )
        }

    suspend fun get(callId: Long): ApiResponse<CallData> =
        api.get("/v1/calls/$callId", authenticated = true) { CallData.from(it.obj("call") ?: JSONObject()) }

    /** The call ringing for this account right now, or null. */
    suspend fun incoming(): ApiResponse<CallData?> =
        api.get("/v1/calls/incoming", authenticated = true) { d -> d.obj("call")?.let(CallData::from) }

    suspend fun ring(callId: Long): ApiResponse<Unit> =
        api.post("/v1/calls/$callId/ring", JSONObject(), authenticated = true)

    suspend fun accept(callId: Long, relayOnly: Boolean): ApiResponse<CallAcceptData> =
        api.post("/v1/calls/$callId/accept", jsonOf("relay_only" to relayOnly), authenticated = true) { d ->
            CallAcceptData(
                call = CallData.from(d.obj("call") ?: JSONObject()),
                ice = IceConfigData.from(d.obj("ice") ?: JSONObject()),
                room = d.obj("room")?.let(RoomJoinData::from),
            )
        }

    suspend fun reject(callId: Long, reason: String): ApiResponse<Unit> =
        api.post("/v1/calls/$callId/reject", jsonOf("reason" to reason), authenticated = true)

    suspend fun end(callId: Long, reason: String): ApiResponse<Unit> =
        api.post("/v1/calls/$callId/end", jsonOf("reason" to reason), authenticated = true)

    suspend fun signal(callId: Long, toUser: Long, toDevice: Long, type: String, payload: JSONObject): ApiResponse<Unit> =
        api.post(
            "/v1/calls/$callId/signal",
            jsonOf("to_user" to toUser.toString(), "to_device" to toDevice.toString(), "type" to type, "payload" to payload),
            authenticated = true,
        )

    suspend fun add(callId: Long, userId: Long): ApiResponse<RoomJoinData?> =
        api.post("/v1/calls/$callId/add", jsonOf("user_id" to userId.toString()), authenticated = true) { d ->
            d.obj("room")?.let(RoomJoinData::from)
        }

    suspend fun ice(relayOnly: Boolean): ApiResponse<IceConfigData> =
        api.get("/v1/calls/ice", mapOf("relay_only" to if (relayOnly) "1" else "0"), authenticated = true) {
            IceConfigData.from(it.obj("ice") ?: JSONObject())
        }

    // ─── live ────────────────────────────────────────────────────────────────

    /**
     * Uploads the card picture for the next live (raw JPEG, already cut to 3:4
     * by the picker). Returns the storage key to pass as `coverUrl` in [liveStart].
     */
    suspend fun liveCover(jpeg: ByteArray): ApiResponse<String> =
        api.putBytes("/v1/live/cover", bytes = jpeg, contentType = "image/jpeg") { it.str("cover_url") }

    suspend fun liveStart(
        kind: String,
        title: String,
        tint: String = "",
        coverUrl: String = "",
        audience: String = "public",
        password: String = "",
        /** Audio: seats including the host (2..45). Video: 0. */
        seats: Int = 0,
        /** Video: may viewers ask to co-host. */
        requests: Boolean = true,
        /**
         * This device's key for this one go-live. A retry after a transport
         * failure sends the same key and gets the same room back — the time
         * already verified is kept, not restarted.
         */
        clientId: String = "",
    ): ApiResponse<LiveStartData> =
        api.post(
            "/v1/live/rooms",
            jsonOf(
                "kind" to kind, "title" to title, "tint" to tint, "cover_url" to coverUrl,
                "audience" to audience, "password" to password,
                "seats" to seats, "requests" to requests,
                "client_id" to clientId.ifBlank { null },
            ),
            authenticated = true,
        ) { d ->
            val room = d.obj("room") ?: JSONObject()
            LiveStartData(
                room = LiveRoomData.from(room),
                sfu = RoomJoinData.from(d.obj("sfu") ?: JSONObject()),
                hlsUrl = d.str("hls_url"),
                reconnectGraceSeconds = d.int("reconnect_grace_seconds", 180),
                heartbeatSeconds = d.int("heartbeat_seconds", 15),
                replacedRoomId = room.id("replaced_room_id"),
                stage = StageData.from(d.obj("stage")),
                time = LiveTimeView.from(d.obj("time")),
                resumed = d.bool("resumed"),
            )
        }

    // ─── stage: co-hosts (video) and seats (audio) ───────────────────────────

    suspend fun stage(roomId: Long): ApiResponse<StageData> =
        api.get("/v1/live/rooms/$roomId/stage", authenticated = true) { StageData.from(it.obj("stage")) }

    /** Host policy: pass only what changes. */
    suspend fun stageSettings(roomId: Long, seats: Int? = null, requests: Boolean? = null, guestCam: Boolean? = null): ApiResponse<StageData> =
        api.post(
            "/v1/live/rooms/$roomId/stage/settings",
            JSONObject().apply {
                if (seats != null) put("seats", seats)
                if (requests != null) put("requests", requests)
                if (guestCam != null) put("guest_cam", guestCam)
            },
            authenticated = true,
        ) { StageData.from(it.obj("stage")) }

    /** Viewer asks to co-host ([cancel] withdraws). */
    suspend fun stageRequest(roomId: Long, cancel: Boolean = false): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/stage/request", jsonOf("cancel" to cancel), authenticated = true)

    suspend fun stageRequests(roomId: Long): ApiResponse<List<StageRequestData>> =
        api.get("/v1/live/rooms/$roomId/stage/requests", authenticated = true) { d ->
            d.objects("items").map(StageRequestData::from)
        }

    /** Host: [op] accept | reject | drop. */
    suspend fun stageDecide(roomId: Long, userId: Long, op: String): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/stage/decide", jsonOf("user_id" to userId.toString(), "op" to op), authenticated = true)

    /** Audio: sit on [seat] (0 = first free). The reply carries the publishing door. */
    suspend fun stageTake(roomId: Long, seat: Int): ApiResponse<StageSeatData> =
        api.post("/v1/live/rooms/$roomId/stage/take", jsonOf("seat" to seat), authenticated = true) { StageSeatData.from(it) }

    /** Step down and keep watching; the reply is a viewer seat (may be empty). */
    suspend fun stageLeave(roomId: Long): ApiResponse<RoomJoinData?> =
        api.post("/v1/live/rooms/$roomId/stage/leave", JSONObject(), authenticated = true) { d ->
            d.obj("sfu")?.let(RoomJoinData::from)?.takeIf { it.token.isNotBlank() }
        }

    suspend fun stageMute(roomId: Long, muted: Boolean): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/stage/mute", jsonOf("muted" to muted), authenticated = true)

    /**
     * My own camera state for my co-host tile. Refused while the host keeps
     * co-host cameras closed, so the tile and the SFU never disagree.
     */
    suspend fun stageCam(roomId: Long, on: Boolean): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/stage/cam", jsonOf("on" to on), authenticated = true)

    // ─── hearts · reactions · moderation ─────────────────────────────────────

    /** [n] hearts tapped since the last call; the reply is the room total. */
    suspend fun like(roomId: Long, n: Int): ApiResponse<Long> =
        api.post("/v1/live/rooms/$roomId/like", jsonOf("n" to n), authenticated = true) { it.long("likes") }

    /** Put [emoji] on chat line [lineId] for the room (or take it off). */
    suspend fun reactChat(roomId: Long, lineId: Long, emoji: String, remove: Boolean = false): ApiResponse<Unit> =
        api.post(
            "/v1/live/rooms/$roomId/chat/react",
            jsonOf("line_id" to lineId.toString(), "emoji" to emoji, "remove" to remove),
            authenticated = true,
        )

    /** The host's moderators (host and moderators may read). */
    suspend fun moderators(roomId: Long): ApiResponse<List<LiveModeratorData>> =
        api.get("/v1/live/rooms/$roomId/mods", authenticated = true) { d -> d.objects("items").map(LiveModeratorData::from) }

    /** Host: appoint [userId] for [days] (≤ 0 removes). */
    suspend fun setModerator(roomId: Long, userId: Long, days: Int): ApiResponse<LiveModeratorData> =
        api.post(
            "/v1/live/rooms/$roomId/mods",
            jsonOf("user_id" to userId.toString(), "days" to days),
            authenticated = true,
        ) { LiveModeratorData.from(it.obj("moderator") ?: JSONObject()) }

    /** Host or moderator: who was blocked or removed in this broadcast. */
    suspend fun sessionBans(roomId: Long): ApiResponse<List<LiveBannedData>> =
        api.get("/v1/live/rooms/$roomId/banned", authenticated = true) { d -> d.objects("items").map(LiveBannedData::from) }

    /**
     * Host or moderator: [action] mute | unmute | block | unblock | kick on
     * [userId]; [minutes] bounds mute — a block is always for this session.
     * Reply: when it lifts (ms), 0 = untimed / the rest of the session.
     */
    suspend fun moderate(roomId: Long, userId: Long, action: String, minutes: Int = 0): ApiResponse<Long> =
        api.post(
            "/v1/live/rooms/$roomId/moderate",
            jsonOf("user_id" to userId.toString(), "action" to action, "minutes" to minutes),
            authenticated = true,
        ) { it.long("until_ms") }

    // ─── PK battles ──────────────────────────────────────────────────────────

    suspend fun pkHosts(roomId: Long, q: String, limit: Int = 20): ApiResponse<List<PkHostHitData>> =
        api.get(
            "/v1/live/rooms/$roomId/pk/hosts",
            buildMap {
                if (q.isNotBlank()) put("q", q)
                put("limit", limit.toString())
            },
            authenticated = true,
        ) { d -> d.objects("items").map(PkHostHitData::from) }

    suspend fun pkInvite(roomId: Long, userId: Long, durationSec: Int): ApiResponse<PkInviteData> =
        api.post(
            "/v1/live/rooms/$roomId/pk/invite",
            jsonOf("user_id" to userId.toString(), "duration_sec" to durationSec),
            authenticated = true,
        ) { PkInviteData.from(it.obj("invite") ?: it) }

    suspend fun pkCancel(roomId: Long, inviteId: Long): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/pk/cancel", jsonOf("invite_id" to inviteId.toString()), authenticated = true)

    suspend fun pkAccept(inviteId: Long): ApiResponse<PkStartData> =
        api.post("/v1/live/pk/accept", jsonOf("invite_id" to inviteId.toString()), authenticated = true) { PkStartData.from(it) }

    suspend fun pkDecline(inviteId: Long): ApiResponse<Unit> =
        api.post("/v1/live/pk/decline", jsonOf("invite_id" to inviteId.toString()), authenticated = true)

    /**
     * A subscribe-only seat in the opponent's SFU room, for anyone inside
     * [roomId] while a battle is on. This is how the other half of a PK
     * reaches a phone: one extra subscription, no second camera capture.
     */
    suspend fun pkWatchSeat(roomId: Long): ApiResponse<RoomJoinData> =
        api.post("/v1/live/rooms/$roomId/pk/seat", JSONObject(), authenticated = true) { RoomJoinData.from(it) }

    suspend fun pkEnd(roomId: Long): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/pk/end", JSONObject(), authenticated = true)

    suspend fun pkRematch(roomId: Long, durationSec: Int = 0): ApiResponse<Unit> =
        api.post(
            "/v1/live/rooms/$roomId/pk/rematch",
            if (durationSec > 0) jsonOf("duration_sec" to durationSec) else JSONObject(),
            authenticated = true,
        )

    suspend fun liveHeartbeat(roomId: Long, mode: String): ApiResponse<LiveHeartbeatData> =
        api.post("/v1/live/rooms/$roomId/heartbeat", jsonOf("mode" to mode), authenticated = true) { d ->
            LiveHeartbeatData(
                room = LiveRoomData.from(d.obj("room") ?: JSONObject()),
                live = d.bool("live", false),
                time = LiveTimeView.from(d.obj("time")),
            )
        }

    suspend fun liveOpen(limit: Int = 50): ApiResponse<List<LiveRoomData>> =
        api.get("/v1/live/rooms", mapOf("limit" to limit.toString()), authenticated = true) { d ->
            d.objects("items").map(LiveRoomData::from)
        }

    suspend fun liveGet(roomId: Long): ApiResponse<LiveRoomData> =
        api.get("/v1/live/rooms/$roomId", authenticated = true) { LiveRoomData.from(it.obj("room") ?: JSONObject()) }

    /** 403 `password_required` / `wrong_password` for a locked room. */
    suspend fun liveJoin(roomId: Long, password: String = ""): ApiResponse<LiveJoinData> =
        api.post(
            "/v1/live/rooms/$roomId/join",
            if (password.isEmpty()) JSONObject() else jsonOf("password" to password),
            authenticated = true,
        ) { d ->
            LiveJoinData(
                room = LiveRoomData.from(d.obj("room") ?: JSONObject()),
                mode = d.str("mode", "hls"),
                sfu = d.obj("sfu")?.let(RoomJoinData::from),
                hlsUrl = d.str("hls_url"),
                reconnectGraceSeconds = d.int("reconnect_grace_seconds", 150),
                heartbeatSeconds = d.int("heartbeat_seconds", 20),
                stage = StageData.from(d.obj("stage")),
                mod = d.bool("mod"),
                likes = d.long("likes"),
            )
        }

    suspend fun liveLeave(roomId: Long, mode: String): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/leave", jsonOf("mode" to mode), authenticated = true)

    suspend fun liveEnd(roomId: Long, reason: String = "host_ended"): ApiResponse<LiveSummaryData> =
        api.post("/v1/live/rooms/$roomId/end", jsonOf("reason" to reason), authenticated = true) { d ->
            LiveSummaryData.from(d.obj("summary"))
        }

    suspend fun liveGuest(roomId: Long, userId: Long): ApiResponse<Unit> =
        api.post("/v1/live/rooms/$roomId/guest", jsonOf("user_id" to userId.toString()), authenticated = true)

    /**
     * One line into the room: text, a sticker ([stickerId] != 0), or both,
     * optionally aimed at [toUserId] (0 = the room).
     */
    suspend fun liveChat(
        roomId: Long,
        text: String,
        clientId: String,
        stickerId: Long = 0L,
        toUserId: Long = 0L,
    ): ApiResponse<LiveChatData> =
        api.post(
            "/v1/live/rooms/$roomId/chat",
            jsonOf(
                "text" to text,
                "client_id" to clientId,
                "sticker_id" to (if (stickerId != 0L) stickerId.toString() else ""),
                "to_user_id" to (if (toUserId != 0L) toUserId.toString() else ""),
            ),
            authenticated = true,
        ) { LiveChatData.from(it.obj("message") ?: JSONObject()) }

    /** Who is in the room. [cursor] is the last viewer's joined_at_ms from the previous page. */
    suspend fun livePeople(roomId: Long, limit: Int = 50, cursor: Long = 0L): ApiResponse<LivePeopleData> =
        api.get(
            "/v1/live/rooms/$roomId/people",
            buildMap {
                put("limit", limit.toString())
                if (cursor > 0L) put("cursor", cursor.toString())
            },
            authenticated = true,
        ) { LivePeopleData.from(it) }
}
