package app.xani.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Compact in-process catalogue. Resource XML would split by App Bundle language
 * later; until then one table is smaller than nine XML files and has no I/O on
 * first frame. Unknown locales fall back to English.
 */
@Immutable
class Copy(private val lang: String) {
    fun t(key: String, vararg args: Pair<String, String>): String {
        val raw = table(lang)[key] ?: En[key] ?: key
        if (args.isEmpty()) return raw
        var out = raw
        for ((k, v) in args) out = out.replace("{$k}", v)
        return out
    }

    companion object {
        const val TabHome = "tab.home"
        const val TabGames = "tab.games"
        const val TabCreate = "tab.create"
        const val TabInbox = "tab.inbox"
        const val TabProfile = "tab.profile"
        const val HomeForYou = "home.foryou"
        const val HomeFollowing = "home.following"
        const val HomeLive = "home.live"
        const val HomeFollowEmpty = "home.follow_empty"
        const val LiveBadge = "home.live_badge"
        const val Home2x = "home.speed_2x"
        const val HomeCommentsCount = "home.comments_count"
        const val HomeReply = "home.reply"
        const val HomeReplyingTo = "home.replying_to"
        const val HomeViewReplies = "home.view_replies"
        const val HomeAddComment = "home.add_comment"
        const val HomeSortTop = "home.sort_top"
        const val HomeSortNewest = "home.sort_newest"
        const val HomeSortMedia = "home.sort_media"
        const val HomeSoundOriginal = "home.sound_original"
        const val HomeSoundPosts = "home.sound_posts"
        const val HomeSoundSave = "home.sound_save"
        const val HomeSoundUse = "home.sound_use"
        const val HomeSoundStory = "home.sound_story"
        const val HomeSoundFind = "home.sound_find"
        const val HomeSearchHint = "home.search_hint"
        const val HomeSearchGo = "home.search_go"
        const val HomeSearchRecent = "home.search_recent"
        const val HomeSearchSeeMore = "home.search_see_more"
        const val HomeSearchYouMayLike = "home.search_you_may_like"
        const val HomeSearchRefresh = "home.search_refresh"
        const val LiveExplore = "live.explore"
        const val LivePkBattle = "live.pk_battle"
        const val LiveAudioTab = "live.audio_tab"
        const val LiveAll = "live.all"
        const val LiveVideoPill = "live.video_pill"
        const val LiveAudioPill = "live.audio_pill"
        const val LiveSearchCountry = "live.search_country"
        const val LiveNoCountries = "live.no_countries"
        const val LiveEvent = "live.event"
        const val LiveFeatured = "live.featured"
        const val LiveFeaturedSub = "live.featured_sub"
        const val LiveTrendingNow = "live.trending_now"
        const val LivePkBattles = "live.pk_battles"
        const val LiveNow = "live.live_now"
        const val LiveCount = "live.count"
        const val LiveFollow = "live.follow"
        const val LiveFollowing = "live.following"
        const val LiveHostMessage = "live.host_message"
        const val LiveHostDiamonds = "live.host_diamonds"
        const val LiveHostWatching = "live.host_watching"
        const val LiveHostFollowers = "live.host_followers"
        const val FramesTitle = "frames.title"
        const val FramesMine = "frames.mine"
        const val FramesShop = "frames.shop"
        const val FramesBuy = "frames.buy"
        const val FramesActivate = "frames.activate"
        const val FramesActive = "frames.active"
        const val FramesExpired = "frames.expired"
        const val FramesPermanent = "frames.permanent"
        const val FramesDays = "frames.days"
        const val FramesDay1 = "frames.day1"
        const val FramesWeek1 = "frames.week1"
        const val FramesMonth1 = "frames.month1"
        const val FramesMonths3 = "frames.months3"
        const val FramesDaysLeft = "frames.days_left"
        const val FramesInsufficient = "frames.insufficient"
        const val FramesEmpty = "frames.empty"
        const val FramesOwnedEmpty = "frames.owned_empty"
        const val FramesPickDuration = "frames.pick_duration"
        const val StoreTitle = "store.title"
        const val StoreMine = "store.mine"
        const val StoreKindFrames = "store.kind_frames"
        const val StoreKindBadges = "store.kind_badges"
        const val StoreKindEntrance = "store.kind_entrance"
        const val StoreKindVip = "store.kind_vip"
        const val StoreKindBubbles = "store.kind_bubbles"
        const val LiveEntranceJoined = "live.entrance_joined"
        const val LiveSendGift = "live.send_gift"
        const val LiveGiftsTitle = "live.gifts_title"
        const val LiveGiftSend = "live.gift_send"
        const val LiveGiftSendTo = "live.gift_send_to"
        const val LiveGiftBalance = "live.gift_balance"
        const val LiveGiftRecharge = "live.gift_recharge"
        const val LiveGiftNotEnough = "live.gift_not_enough"
        const val LiveGiftNotEnoughTitle = "live.gift_not_enough_title"
        const val LiveGiftNotEnoughBody = "live.gift_not_enough_body"
        const val LiveGiftFailed = "live.gift_failed"
        const val LiveGiftFailedTitle = "live.gift_failed_title"
        const val LiveGiftDismiss = "live.gift_dismiss"
        const val LiveGiftRetry = "live.gift_retry"
        const val LiveGiftTooFast = "live.gift_too_fast"
        const val LiveChatSend = "live.chat_send"
        const val LiveGiftEmpty = "live.gift_empty"
        const val LiveGiftLoading = "live.gift_loading"
        const val LiveGiftSentLine = "live.gift_sent_line"
        const val LiveGiftHost = "live.gift_host"
        const val LiveGiftCombo = "live.gift_combo"
        const val LiveGiftLuckyHint = "live.gift_lucky_hint"
        const val LiveStickers = "live.stickers"
        const val LiveStickerTo = "live.sticker_to"
        const val LiveStickerEveryone = "live.sticker_everyone"
        const val LiveStickerEmpty = "live.sticker_empty"
        const val LiveStickerLoading = "live.sticker_loading"
        const val GiftRecordTitle = "gift_record.title"
        const val GiftRecordToday = "gift_record.today"
        const val GiftRecordWeek = "gift_record.week"
        const val GiftRecordMonth = "gift_record.month"
        const val GiftRecordCustom = "gift_record.custom"
        const val GiftRecordSent = "gift_record.sent"
        const val GiftRecordReceived = "gift_record.received"
        const val GiftRecordGifts = "gift_record.gifts"
        const val GiftRecordSenderLevel = "gift_record.sender_level"
        const val GiftRecordReceiverLevel = "gift_record.receiver_level"
        const val GiftRecordNoLevel = "gift_record.no_level"
        const val GiftRecordLevelN = "gift_record.level_n"
        const val GiftRecordToNext = "gift_record.to_next"
        const val GiftRecordTop = "gift_record.top"
        const val GiftRecordRange = "gift_record.range"
        const val GiftRecordByDay = "gift_record.by_day"
        const val GiftRecordEmpty = "gift_record.empty"
        const val GiftRecordFrom = "gift_record.from"
        const val GiftRecordTo = "gift_record.to"
        const val GiftRecordApply = "gift_record.apply"
        const val GiftRecordBadDate = "gift_record.bad_date"
        const val ProfileShareTitle = "profile.share_title"
        const val ProfileShareTagline = "profile.share_tagline"
        const val ProfileShareCopy = "profile.share_copy"
        const val ProfileShareSave = "profile.share_save"
        const val ProfileShareSaved = "profile.share_saved"
        const val ProfileShareSaveFailed = "profile.share_save_failed"
        const val ProfileShareLinkCopied = "profile.share_link_copied"
        const val ProfileShareLinkPending = "profile.share_link_pending"
        const val ProfileNoLiked = "profile.no_liked"
        const val ProfileNoSaved = "profile.no_saved"
        const val ProfileNoPrivate = "profile.no_private"
        const val ProfileReceivingEmpty = "profile.receiving_empty"
        const val TxnTitle = "txn.title"
        const val TxnSpent = "txn.spent"
        const val TxnEarned = "txn.earned"
        const val TxnCoins = "txn.coins"
        const val TxnDiamonds = "txn.diamonds"
        const val TxnCatGiftsSent = "txn.cat.gifts_sent"
        const val TxnCatStore = "txn.cat.store"
        const val TxnCatGameBets = "txn.cat.game_bets"
        const val TxnCatGiftsReceived = "txn.cat.gifts_received"
        const val TxnCatTopups = "txn.cat.topups"
        const val TxnCatGameWins = "txn.cat.game_wins"
        const val TxnCatWithdrawals = "txn.cat.withdrawals"
        const val TxnCatAdjustments = "txn.cat.adjustments"
        const val TxnEntries = "txn.entries"
        const val TxnByDay = "txn.by_day"
        const val TxnEmpty = "txn.empty"
        const val TxnSentTo = "txn.sent_to"
        const val TxnReceivedFrom = "txn.received_from"
        const val TxnGiftsWith = "txn.gifts_with"
        const val TxnGiftsFromName = "txn.gifts_from_name"
        const val TxnLoadMore = "txn.load_more"
        const val TxnRetry = "txn.retry"
        const val TxnDaysPack = "txn.days_pack"
        const val TxnExpires = "txn.expires"
        const val TxnPeople = "txn.people"
        const val WithdrawTitle = "withdraw.title"
        const val WithdrawTabMethods = "withdraw.tab_methods"
        const val WithdrawTabRequests = "withdraw.tab_requests"
        const val WithdrawAvailable = "withdraw.available"
        const val WithdrawOnHold = "withdraw.on_hold"
        const val WithdrawLimits = "withdraw.limits"
        const val WithdrawClosedRange = "withdraw.closed_range"
        const val WithdrawClosedDay = "withdraw.closed_day"
        const val WithdrawOpenRange = "withdraw.open_range"
        const val WithdrawOpenDay = "withdraw.open_day"
        const val WithdrawNoMethods = "withdraw.no_methods"
        const val WithdrawPickMethod = "withdraw.pick_method"
        const val WithdrawAccountTitle = "withdraw.account_title"
        const val WithdrawAmount = "withdraw.amount"
        const val WithdrawSubmit = "withdraw.submit"
        const val WithdrawSubmitting = "withdraw.submitting"
        const val WithdrawFill = "withdraw.fill"
        const val WithdrawInsufficient = "withdraw.insufficient"
        const val WithdrawBelowMin = "withdraw.below_min"
        const val WithdrawAboveMax = "withdraw.above_max"
        const val WithdrawDone = "withdraw.done"
        const val WithdrawStatusPending = "withdraw.status_pending"
        const val WithdrawStatusApproved = "withdraw.status_approved"
        const val WithdrawStatusRejected = "withdraw.status_rejected"
        const val WithdrawNoRequests = "withdraw.no_requests"
        const val WithdrawCountry = "withdraw.country"
        const val WithdrawAll = "withdraw.all"
        const val LiveSaySomething = "live.say_something"
        const val LiveLeaveTitle = "live.leave_title"
        const val LiveMinimize = "live.minimize"
        const val LiveMinimizeSub = "live.minimize_sub"
        const val LiveClose = "live.close"
        const val LiveCloseSub = "live.close_sub"
        const val LiveCancelStay = "live.cancel_stay"
        const val LiveHostEnded = "live.host_ended"
        const val LiveConnectionLost = "live.connection_lost"
        const val LiveOk = "live.ok"
        const val LiveSummaryTitle = "live.summary_title"
        const val LiveSummaryLost = "live.summary_lost"
        const val LiveSummaryDuration = "live.summary_duration"
        const val LiveSummaryPeak = "live.summary_peak"
        const val LiveSummaryJoins = "live.summary_joins"
        const val LiveSummaryFailed = "live.summary_failed"
        const val LiveNoneLive = "live.none_live"
        const val LiveNoneLiveSub = "live.none_live_sub"
        const val LiveWatching = "live.watching"
        const val LiveStartFailedTitle = "live.start_failed_title"
        const val LivePrivate = "live.private"
        const val LiveFriends = "live.friends"
        const val LivePasswordTitle = "live.password_title"
        const val LivePasswordHint = "live.password_hint"
        const val LivePasswordJoin = "live.password_join"
        const val LivePasswordWrong = "live.password_wrong"
        const val LivePasswordCancel = "live.password_cancel"
        const val LiveHostAway = "live.host_away"
        const val LiveHostBack = "live.host_back"
        const val LivePeopleTitle = "live.people_title"
        const val LivePeopleEmpty = "live.people_empty"
        const val LivePeopleHost = "live.people_host"
        const val LivePeopleGuest = "live.people_guest"
        const val LiveCoHosts = "live.cohosts"
        const val LiveCoHostRequest = "live.cohost_request"
        const val LiveCoHostRequested = "live.cohost_requested"
        const val LiveCoHostWants = "live.cohost_wants"
        const val LiveCoHostAccept = "live.cohost_accept"
        const val LiveCoHostDecline = "live.cohost_decline"
        const val LiveCoHostMore = "live.cohost_more"
        const val LiveCoHostNone = "live.cohost_none"
        const val LiveRequestsOn = "live.requests_on"
        const val LiveRequestsOff = "live.requests_off"
        const val LiveStageRemove = "live.stage_remove"
        const val LiveStageLeave = "live.stage_leave"
        const val LiveStageDenied = "live.stage_denied"
        const val LiveStageDropped = "live.stage_dropped"
        const val LiveStageFull = "live.stage_full"
        const val LiveStageCoins = "live.stage_coins"
        const val LiveStageSince = "live.stage_since"
        const val LiveSeatTap = "live.seat_tap"
        const val LiveSeatsTitle = "live.seats_title"
        const val LiveSeatsHint = "live.seats_hint"
        const val LiveSeatsCount = "live.seats_count"
        const val LiveEndCall = "live.end_call"
        const val LiveMore = "live.more"
        const val LiveViewProfile = "live.view_profile"
        const val LiveModeration = "live.moderation"
        const val LiveModerators = "live.moderators"
        const val LiveModeratorsNone = "live.moderators_none"
        const val LiveMakeMod = "live.make_mod"
        const val LiveRemoveMod = "live.remove_mod"
        const val LiveModFor = "live.mod_for"
        const val LiveMute = "live.mute"
        const val LiveUnmute = "live.unmute"
        const val LiveMuteFor = "live.mute_for"
        const val LiveBlock = "live.block"
        const val LiveBlockFor = "live.block_for"
        const val LiveKick = "live.kick"
        const val LiveDays = "live.days"
        const val LiveMinutes = "live.minutes"
        const val LiveCustom = "live.custom"
        const val LiveApply = "live.apply"
        const val LiveDaysLeft = "live.days_left"
        const val LiveChatMuted = "live.chat_muted"
        const val LiveKickedNotice = "live.kicked_notice"
        const val LiveBannedNotice = "live.banned_notice"
        const val LiveMadeMod = "live.made_mod"
        const val LiveModRemoved = "live.mod_removed"
        const val LiveModDone = "live.mod_done"
        const val LiveReactMore = "live.react_more"
        const val LiveLikes = "live.likes"
        const val StorySeenBy = "story.seen_by"
        const val StoryNoViews = "story.no_views"
        const val StoryViewedYours = "story.viewed_yours"
        const val StoryPosting = "story.posting"
        const val StoryFailed = "story.failed"
        const val StoryTooBig = "story.too_big"
        const val StoryDeleted = "story.deleted"
        const val StoryYou = "story.you"
        const val ActivityEmpty = "activity.empty"
        const val ActivityCommented = "activity.commented"
        const val ActivityReplied = "activity.replied"
        const val ActivityCommentLiked = "activity.comment_liked"
        const val PkTitle = "pk.title"
        const val PkHint = "pk.hint"
        const val PkDuration = "pk.duration"
        const val PkCustomMin = "pk.custom_min"
        const val PkSearch = "pk.search"
        const val PkSearchHint = "pk.search_hint"
        const val PkNoHosts = "pk.no_hosts"
        const val PkInvite = "pk.invite"
        const val PkSent = "pk.sent"
        const val PkIncoming = "pk.incoming"
        const val PkAccept = "pk.accept"
        const val PkDecline = "pk.decline"
        const val PkWin = "pk.win"
        const val PkLose = "pk.lose"
        const val PkDraw = "pk.draw"
        const val PkResult = "pk.result"
        const val PkResultHold = "pk.result_hold"
        const val PkRematch = "pk.rematch"
        const val PkRematchWait = "pk.rematch_wait"
        const val PkEnd = "pk.end"
        const val PkSentNotice = "pk.sent_notice"
        const val PkDeclined = "pk.declined"
        const val PkExpired = "pk.expired"
        const val PkRound = "pk.round"
        const val PkWinTag = "pk.win_tag"
        const val PkLoseTag = "pk.lose_tag"
        const val PkConnecting = "pk.connecting"
        const val LiveWatchEndedTitle = "live.watch_ended_title"
        const val LiveWatchEndedDuration = "live.watch_ended_duration"
        const val GamesTitle = "games.title"
        const val GamesBody = "games.body"
        const val GamesRefresh = "games.refresh"
        const val GamesLoading = "games.loading"
        const val GamesUnavailable = "games.unavailable"
        const val GamesRetry = "games.retry"
        const val GamesEmpty = "games.empty"
        const val ComingSoon = "reserved.badge"
        const val CreateTitle = "create.title"
        const val CreateBody = "create.body"
        const val CreateAddSound = "create.add_sound"
        const val CreateClipTooShort = "create.clip_too_short"
        const val CreateCameraBusy = "create.camera_busy"
        const val CreatePost = "create.post"
        const val CreateGoLive = "create.go_live"
        const val CreateGoLiveTitle = "create.go_live_title"
        const val CreateGoLiveBtn = "create.go_live_btn"
        const val CreatePhoto = "create.photo"
        const val CreateText = "create.text"
        const val CreateTextHint = "create.text_hint"
        const val CreateTextTitle = "create.text_title"
        const val CreateTextType = "create.text_type"
        const val CreateNext = "create.next"
        const val CreateCoHosts = "create.co_hosts"
        const val Create10m = "create.10m"
        const val Create5m = "create.5m"
        const val Create60s = "create.60s"
        const val Create15s = "create.15s"
        const val CreateCover = "create.cover"
        const val CreateCoverHint = "create.cover_hint"
        const val CreateLiveHint = "create.live_hint"
        const val CreateVideo = "create.video"
        const val CreateAudioOnly = "create.audio_only"
        const val CreateRoomSize = "create.room_size"
        const val CreateSeats = "create.seats"
        const val CreateSeatsHint = "create.seats_hint"
        const val CreateSeatLayout = "create.seat_layout"
        const val CreateOrderSeats = "create.order_seats"
        const val CreateOrderSeatsHint = "create.order_seats_hint"
        const val CreateSeatHost = "create.seat_host"
        const val CreateSeatCount = "create.seat_count"
        const val CreateDone = "create.done"
        const val CreateChipCode = "create.chip_code"
        const val CreateChipAma = "create.chip_ama"
        const val CreateChipStudy = "create.chip_study"
        const val CreateSaved = "create.saved"
        const val CreateEndLive = "create.end_live"
        const val CreateFlashOn = "create.flash_on"
        const val CreateFlashOff = "create.flash_off"
        const val CreateTimer = "create.timer"
        const val CreateGrid = "create.grid"
        const val CreateSpeed = "create.speed"
        const val CreateBeauty = "create.beauty"
        const val CreateLiveVideoTitle = "create.live_video_title"
        const val CreateLiveAudioTitle = "create.live_audio_title"
        const val CreateLiveVideoSub = "create.live_video_sub"
        const val CreateLiveAudioSub = "create.live_audio_sub"
        const val CreateAudioRoomLabel = "create.audio_room_label"
        const val CreateAudioRoomSub = "create.audio_room_sub"
        const val CreateBanner = "create.banner"
        const val CreateBannerPhoto = "create.banner_photo"
        const val CreateTitleField = "create.title_field"
        const val CreateLiveTitleHint = "create.live_title_hint"
        const val CreateSpeakerSeats = "create.speaker_seats"
        const val CreateCustom = "create.custom"
        const val CreateSeatsMax = "create.seats_max"
        const val CreateAudience = "create.audience"
        const val CreateAudiencePublic = "create.audience_public"
        const val CreateAudienceFriends = "create.audience_friends"
        const val CreateAudiencePrivate = "create.audience_private"
        const val CreateAudiencePublicSub = "create.audience_public_sub"
        const val CreateAudienceFriendsSub = "create.audience_friends_sub"
        const val CreateAudiencePrivateSub = "create.audience_private_sub"
        const val CreatePasswordHint = "create.password_hint"
        const val CreateNextSeats = "create.next_seats"
        const val CreateSelectSeats = "create.select_seats"
        const val CreateArrangeRoom = "create.arrange_room"
        const val CreateArrangeHint = "create.arrange_hint"
        const val CreateStartAudioRoom = "create.start_audio_room"
        const val CreateLiveChatHint = "create.live_chat_hint"
        const val CreateGalleryAll = "create.gallery_all"
        const val CreateGalleryVideos = "create.gallery_videos"
        const val CreateGalleryPhotos = "create.gallery_photos"
        const val HomeFeedEmpty = "home.feed_empty"
        const val HomeFeedOffline = "home.feed_offline"
        const val HomeFeedRetry = "home.feed_retry"
        const val HomeCommentsEmpty = "home.comments_empty"
        const val HomeCommentsOff = "home.comments_off"
        const val HomePosting = "home.posting"
        const val HomeFailed = "home.failed"
        const val ProfileNoPosts = "profile.no_posts"
        const val HomeSearchPeople = "home.search_people"
        const val HomeSearchNoPeople = "home.search_no_people"
        const val HomeSearchFollowers = "home.search_followers"
        const val HomeFollowsYou = "home.follows_you"
        const val ProfileNoPeople = "profile.no_people"
        const val InboxTitle = "inbox.title"
        const val InboxBody = "inbox.body"
        const val ProfileCopy = "profile.copy"
        const val ProfileVerified = "profile.verified"
        const val ProfileFollowing = "profile.following"
        const val ProfileFollowers = "profile.followers"
        const val ProfileLikes = "profile.likes"
        const val ProfileRestricted = "profile.restricted"
        const val ProfileCoins = "profile.coins"
        const val ProfileDiamonds = "profile.diamonds"
        const val ProfileEdit = "profile.edit"
        const val ProfileShare = "profile.share"
        const val ProfileLatest = "profile.latest"
        const val ProfilePopular = "profile.popular"
        const val ProfileSearch = "profile.search"
        const val ProfileName = "profile.name"
        const val ProfileNameLabel = "profile.name_label"
        const val ProfileStatus = "profile.status"
        const val ProfileWhatsOnMind = "profile.whats_on_mind"
        const val ProfileStatusTitle = "profile.status_title"
        const val ProfileCopied = "profile.copied"
        const val ProfileUsernameLocked = "profile.username_locked"
        const val ProfileUsernameAgainIn = "profile.username_again_in"
        const val ProfileUsernameAvailable = "profile.username_available"
        const val ProfileUsernameTaken = "profile.username_taken"
        const val ProfileUsernameRule = "profile.username_rule"
        const val ProfileUsernameChecking = "profile.username_checking"
        const val ProfileSaveFailed = "profile.save_failed"
        const val ProfileBio = "profile.bio"
        const val ProfileSave = "profile.save"
        const val ProfileReceiving = "profile.receiving"
        const val ProfileViews = "profile.views"
        const val ProfileVisitsTitle = "profile.visits_title"
        const val ProfileVisitsEmpty = "profile.visits_empty"
        const val ProfileVisitsMonth = "profile.visits_month"
        const val ProfileMessage = "profile.message"
        const val ProfilePosts = "profile.posts"
        const val ProfileNoPostsYet = "profile.no_posts_yet"
        const val ProfileEditTitle = "profile.edit_title"
        const val ProfilePostEdit = "profile.post_edit"
        const val ProfileChangePrivacy = "profile.change_privacy"
        const val ProfilePostDelete = "profile.post_delete"
        const val ProfilePostDeleteAsk = "profile.post_delete_ask"
        const val ProfilePostDeleteYes = "profile.post_delete_yes"
        const val ProfilePostSaving = "profile.post_saving"
        const val ProfilePhotoTitle = "profile.photo_title"
        const val ProfilePhotoCamera = "profile.photo_camera"
        const val ProfilePhotoGallery = "profile.photo_gallery"
        const val ProfilePhotoAdd = "profile.photo_add"
        const val ProfileChangePhoto = "profile.change_photo"
        const val ProfileUsername = "profile.username"
        const val ProfileGender = "profile.gender"
        const val ProfileGenderMale = "profile.gender_male"
        const val ProfileGenderFemale = "profile.gender_female"
        const val ProfileGenderPreferNot = "profile.gender_prefer_not"
        const val ProfileDrawerAccount = "profile.drawer_account"
        const val ProfileDrawerCreator = "profile.drawer_creator"
        const val ProfileDrawerSettings = "profile.drawer_settings"
        const val ProfileDrawerSupport = "profile.drawer_support"
        const val ProfileDrawerLegal = "profile.drawer_legal"
        const val ProfileBalance = "profile.balance"
        const val ProfileWallet = "profile.wallet"
        const val ProfileTxnHistory = "profile.txn_history"
        const val ProfileCreatorTools = "profile.creator_tools"
        const val ProfileAnalytics = "profile.analytics"
        const val ProfileLiveCenter = "profile.live_center"
        const val ProfileSettings = "profile.settings"
        const val ProfileHelpCentre = "profile.help_centre"
        const val ProfileReportProblem = "profile.report_problem"
        const val HelpSubject = "help.subject"
        const val HelpMessage = "help.message"
        const val HelpSubmit = "help.submit"
        const val HelpMyReports = "help.my_reports"
        const val HelpAddAccount = "help.add_account"
        const val HelpCreateAccount = "help.create_account"
        const val HelpUseExisting = "help.use_existing"
        const val ProfileTerms = "profile.terms"
        const val ProfileLogOut = "profile.log_out"
        const val ProfileTopSenders = "profile.top_senders"
        const val ProfileGifts = "profile.gifts"
        const val ProfileDaily = "profile.daily"
        const val ProfileWeekly = "profile.weekly"
        const val ProfileMonthly = "profile.monthly"
        const val ProfileMutual = "profile.mutual"
        const val ProfileUnfollow = "profile.unfollow"
        const val ProfileFollowBack = "profile.follow_back"
        const val ProfileFollow = "profile.follow"
        const val ProfileFollowingBtn = "profile.following_btn"
        const val ProfileSwitchAccounts = "profile.switch_accounts"
        const val ProfileAccountSettings = "profile.account_settings"
        const val ProfileActivatePassword = "profile.activate_password"
        const val ProfileChangePassword = "profile.change_password"
        const val ProfileActivatePasswordHint = "profile.activate_password_hint"
        const val ProfileChangePasswordHint = "profile.change_password_hint"
        const val ProfileCurrentPassword = "profile.current_password"
        const val ProfileNewPassword = "profile.new_password"
        const val ProfileConfirmPassword = "profile.confirm_password"
        const val ProfilePasswordMismatch = "profile.password_mismatch"
        const val ProfileUpdatePassword = "profile.update_password"
        const val ProfilePrivacySafety = "profile.privacy_safety"
        const val ProfilePrivacy = "profile.privacy"
        const val ProfileDms = "profile.dms"
        const val ProfileBlocked = "profile.blocked"
        const val ProfileSafeMode = "profile.safe_mode"
        const val ProfileContentDisplay = "profile.content_display"
        const val ProfileDisplay = "profile.display"
        const val ProfileLanguage = "profile.language"
        const val ProfileTranslation = "profile.translation"
        const val ProfileNotifications = "profile.notifications"
        const val NotifyMessages = "notify.messages"
        const val NotifyAudioCalls = "notify.audio_calls"
        const val NotifyVideoCalls = "notify.video_calls"
        const val NotifyLive = "notify.live"
        const val NotifyComments = "notify.comments"
        const val NotifyReplies = "notify.replies"
        const val NotifyMentions = "notify.mentions"
        const val NotifyPushOn = "notify.push_on"
        const val NotifyOsOff = "notify.os_off"
        const val NotifyNotReady = "notify.not_ready"
        const val NotifyOpenSystem = "notify.open_system"
        const val NotifyLoadFailed = "notify.load_failed"
        const val NotifyRetry = "notify.retry"
        const val BlockedChats = "blocked.chats"
        const val BlockedLive = "blocked.live"
        const val BlockedChatsHint = "blocked.chats_hint"
        const val BlockedLiveHint = "blocked.live_hint"
        const val BlockedEmpty = "blocked.empty"
        const val RolesTitle = "roles.title"
        const val RolesTabMine = "roles.tab_mine"
        const val RolesTabRequests = "roles.tab_requests"
        const val RolesTabApprovals = "roles.tab_approvals"
        const val RolesMineEmpty = "roles.mine_empty"
        const val RolesJoinTitle = "roles.join_title"
        const val RolesJoinHint = "roles.join_hint"
        const val RolesCodePlaceholder = "roles.code_placeholder"
        const val RolesCodeOwner = "roles.code_owner"
        const val RolesPickRole = "roles.pick_role"
        const val RolesNotePlaceholder = "roles.note_placeholder"
        const val RolesApply = "roles.apply"
        const val RolesApplied = "roles.applied"
        const val RolesMyCodes = "roles.my_codes"
        const val RolesCodeOpens = "roles.code_opens"
        const val RolesCopied = "roles.copied"
        const val RolesShareCode = "roles.share_code"
        const val RolesShareText = "roles.share_text"
        const val RolesFinance = "roles.finance"
        const val RolesGiftsReceived = "roles.gifts_received"
        const val RolesGiftsSent = "roles.gifts_sent"
        const val RolesReceivedLine = "roles.received_line"
        const val RolesSentLine = "roles.sent_line"
        const val RolesWithdrawals = "roles.withdrawals"
        const val RolesNoWithdrawals = "roles.no_withdrawals"
        const val RolesWdAmount = "roles.wd_amount"
        const val RolesWdPending = "roles.wd_pending"
        const val RolesWdApproved = "roles.wd_approved"
        const val RolesWdRejected = "roles.wd_rejected"
        const val RolesUpline = "roles.upline"
        const val RolesTopOfTree = "roles.top_of_tree"
        const val RolesDownline = "roles.downline"
        const val RolesDirect = "roles.direct"
        const val RolesWhole = "roles.whole"
        const val RolesDownlineEmpty = "roles.downline_empty"
        const val RolesDirectCount = "roles.direct_count"
        const val RolesRequestsEmpty = "roles.requests_empty"
        const val RolesApprovalsEmpty = "roles.approvals_empty"
        const val RolesApprove = "roles.approve"
        const val RolesReject = "roles.reject"
        const val RolesCancel = "roles.cancel"
        const val RolesReasonPlaceholder = "roles.reason_placeholder"
        const val RolesReasonRequired = "roles.reason_required"
        const val RolesDecided = "roles.decided"
        const val RolesRequestedBy = "roles.requested_by"
        const val RolesWaiting = "roles.waiting"
        const val RolesExpires = "roles.expires"
        const val RolesStatusActive = "roles.status_active"
        const val RolesStatusSuspended = "roles.status_suspended"
        const val RolesStatusRevoked = "roles.status_revoked"
        const val RolesStatusBlocked = "roles.status_blocked"
        const val RolesReqPending = "roles.req_pending"
        const val RolesReqApproved = "roles.req_approved"
        const val RolesReqRejected = "roles.req_rejected"
        const val RolesReqCancelled = "roles.req_cancelled"
        const val RolesReqExpired = "roles.req_expired"
        const val RolesKindAssign = "roles.kind_assign"
        const val RolesKindApply = "roles.kind_apply"
        const val RolesKindTransfer = "roles.kind_transfer"
        const val RolesKindRevoke = "roles.kind_revoke"
        const val RolesKindReactivate = "roles.kind_reactivate"
        const val RolesNoticeRequest = "roles.notice_request"
        const val RolesNoticeDecision = "roles.notice_decision"
        const val RolesNoticeChange = "roles.notice_change"
        const val RolesLoadFailed = "roles.load_failed"
        const val RolesYourCode = "roles.your_code"
        const val RolesJoinedWith = "roles.joined_with"
        const val RolesAddMember = "roles.add_member"
        const val RolesAddMemberAs = "roles.add_member_as"
        const val RolesCanRecruit = "roles.can_recruit"
        const val RolesRecruitViaCode = "roles.recruit_via_code"
        const val RolesPickPerson = "roles.pick_person"
        const val RolesSearchPeople = "roles.search_people"
        const val RolesNoPeople = "roles.no_people"
        const val RolesFormTitle = "roles.form_title"
        const val RolesFormNone = "roles.form_none"
        const val RolesFormOptional = "roles.form_optional"
        const val RolesFormRequired = "roles.form_required"
        const val RolesFormChoose = "roles.form_choose"
        const val RolesFormChooseCountry = "roles.form_choose_country"
        const val RolesFormPhoneHint = "roles.form_phone_hint"
        const val RolesFormNumber = "roles.form_number"
        const val RolesFormMinLen = "roles.form_min_len"
        const val RolesFormMaxLen = "roles.form_max_len"
        const val RolesFormMin = "roles.form_min"
        const val RolesFormMax = "roles.form_max"
        const val RolesFormFix = "roles.form_fix"
        const val RolesApproveNow = "roles.approve_now"
        const val RolesApproveNowHint = "roles.approve_now_hint"
        const val RolesSendForApproval = "roles.send_for_approval"
        const val RolesAssignNow = "roles.assign_now"
        const val RolesAssigned = "roles.assigned"
        const val RolesAssignSent = "roles.assign_sent"
        const val RolesAnswers = "roles.answers"
        const val RolesManage = "roles.manage"
        const val RolesActSuspend = "roles.act_suspend"
        const val RolesActReactivate = "roles.act_reactivate"
        const val RolesActBlock = "roles.act_block"
        const val RolesActUnblock = "roles.act_unblock"
        const val RolesActRemove = "roles.act_remove"
        const val RolesActTransfer = "roles.act_transfer"
        const val RolesActDone = "roles.act_done"
        const val RolesTransferTo = "roles.transfer_to"
        const val RolesTransferChildrenOnly = "roles.transfer_children_only"
        const val RolesTransferHint = "roles.transfer_hint"
        const val RolesRemoveKeep = "roles.remove_keep"
        const val RolesRemoveMoveUp = "roles.remove_move_up"
        const val RolesRemoveSuspend = "roles.remove_suspend"
        const val RolesQuestions = "roles.questions"
        const val RolesActions = "roles.actions"
        const val ProfileSupportLegal = "profile.support_legal"
        const val ProfileLocationContacts = "profile.location_contacts"
        const val ProfileVersion = "profile.version"
        const val FeatureUnavailable = "feature.unavailable"
        const val UploadChannel = "upload.channel"
        const val UploadPostingPct = "upload.posting_pct"
        const val UploadPosting = "upload.posting"
        const val UploadProcessing = "upload.processing"
        const val UploadPosted = "upload.posted"
        const val UploadFailed = "upload.failed"
        const val UploadRetry = "upload.retry"
        const val UploadCancel = "upload.cancel"
        const val UploadCount = "upload.count"
        const val UploadQueued = "upload.queued"
        const val UploadFormTitle = "upload.form_title"
        const val UploadCaptionHint = "upload.caption_hint"
        const val UploadWhoCanSee = "upload.who_can_see"
        const val UploadPublic = "upload.public"
        const val UploadFriends = "upload.friends"
        const val UploadPrivate = "upload.private"
        const val UploadAllowComments = "upload.allow_comments"
        const val UploadAllowDuet = "upload.allow_duet"
        const val UploadPostBtn = "upload.post_btn"
        const val UploadDetails = "upload.details"
        const val UploadDuration = "upload.duration"
        const val UploadSize = "upload.size"
        const val UploadResolution = "upload.resolution"
        const val UploadTaken = "upload.taken"
        const val UploadNot916 = "upload.not_916"
        const val UploadReading = "upload.reading"
        const val UploadErrTooLong = "upload.err_too_long"
        const val UploadErrTooLarge = "upload.err_too_large"
        const val UploadErrUnsupported = "upload.err_unsupported"
        const val UploadErrUnreadable = "upload.err_unreadable"
        const val UploadErrAspect = "upload.err_aspect"
        const val InboxChatOpenFail = "inbox.chat_open_fail"
        const val InboxOk = "inbox.ok"
        const val YouHandle = "profile.handle"
        const val InboxChats = "inbox.chats"
        const val InboxNotifications = "inbox.notifications"
        const val InboxActivity = "inbox.activity"
        const val InboxSearch = "inbox.search"
        const val InboxPinned = "inbox.pinned"
        const val InboxAll = "inbox.all"
        const val InboxArchived = "inbox.archived"
        const val InboxNewGroup = "inbox.new_group"
        const val InboxNewChat = "inbox.new_chat"
        const val InboxGroupName = "inbox.group_name"
        const val InboxInviteLink = "inbox.invite_link"
        const val InboxCreate = "inbox.create"
        const val InboxCancel = "inbox.cancel"
        const val InboxMyStory = "inbox.my_story"
        const val InboxMessage = "inbox.message"
        const val InboxOnline = "inbox.online"
        const val InboxOffline = "inbox.offline"
        const val InboxTyping = "inbox.typing"
        const val InboxLastSeen = "inbox.last_seen"
        const val InboxBlocked = "inbox.blocked"
        const val InboxUnblock = "inbox.unblock"
        const val InboxReported = "inbox.reported"
        const val InboxMembers = "inbox.members"
        const val InboxVanish = "inbox.vanish"
        const val InboxGallery = "inbox.gallery"
        const val InboxCamera = "inbox.camera"
        const val InboxContact = "inbox.contact"
        const val InboxLocation = "inbox.location"
        const val InboxDocument = "inbox.document"
        const val InboxPoll = "inbox.poll"
        const val InboxEvent = "inbox.event"
        const val InboxAi = "inbox.ai"
        const val InboxMute = "inbox.mute"
        const val InboxUnmute = "inbox.unmute"
        const val InboxPin = "inbox.pin"
        const val InboxUnpin = "inbox.unpin"
        const val InboxArchive = "inbox.archive"
        const val InboxUnarchive = "inbox.unarchive"
        const val InboxDelete = "inbox.delete"
        const val InboxTheme = "inbox.theme"
        const val InboxNicknames = "inbox.nicknames"
        const val InboxDisappear = "inbox.disappear"
        const val InboxChatInfo = "inbox.chat_info"
        const val InboxCalling = "inbox.calling"
        const val CallRinging = "call.ringing"
        const val CallReconnecting = "call.reconnecting"
        const val CallIncomingAudio = "call.incoming_audio"
        const val CallIncomingVideo = "call.incoming_video"
        const val CallAccept = "call.accept"
        const val CallDecline = "call.decline"
        const val CallBusy = "call.busy"
        const val CallNoAnswer = "call.no_answer"
        const val CallDeclined = "call.declined"
        const val CallMissed = "call.missed"
        const val CallLost = "call.lost"
        const val CallFailed = "call.failed"
        const val CallUnavailable = "call.unavailable"
        const val CallEnded = "call.ended"
        const val CallOngoing = "call.ongoing"
        const val CallChannel = "call.channel"
        const val CallIncomingChannel = "call.incoming_channel"
        const val CallAddPerson = "call.add_person"
        const val CallNobodyToAdd = "call.nobody_to_add"
        const val CallInCall = "call.in_call"
        const val CallYou = "call.you"
        const val InboxEnd = "inbox.end"
        const val InboxSpeaker = "inbox.speaker"
        const val InboxVideo = "inbox.video"
        const val InboxEncrypted = "inbox.encrypted"
        const val InboxFollow = "inbox.follow"
        const val InboxNewFollowers = "inbox.new_followers"
        const val InboxSystem = "inbox.system"
        const val InboxVideoRestrictedTitle = "inbox.video_restricted_title"
        const val InboxVideoRestrictedBody = "inbox.video_restricted_body"
        const val InboxSupportRepliedTitle = "inbox.support_replied_title"
        const val InboxTypeSearch = "inbox.type_search"
        const val InboxNoResults = "inbox.no_results"
        const val InboxPeople = "inbox.people"
        const val InboxSeen = "inbox.seen"
        const val InboxDelivered = "inbox.delivered"
        const val InboxRecents = "inbox.recents"
        const val InboxSeeAll = "inbox.see_all"
        const val InboxPause = "inbox.pause"
        const val InboxResume = "inbox.resume"
        const val InboxShare = "inbox.share"
        const val InboxReadReceipts = "inbox.read_receipts"
        const val InboxMedia = "inbox.media"
        const val InboxMarkRead = "inbox.mark_read"
        const val InboxOnlineShort = "inbox.members_n"
        const val InboxCreateStory = "inbox.create_story"
        const val InboxShareStory = "inbox.share_story"
        const val InboxSearchChat = "inbox.search_chat"
        const val InboxPinnedMessage = "inbox.pinned_msg"
        const val InboxMuteNotifs = "inbox.mute_notifs"
        const val InboxShareProfile = "inbox.share_profile"
        const val InboxExportChat = "inbox.export_chat"
        const val InboxClearChat = "inbox.clear_chat"
        const val InboxRestrict = "inbox.restrict"
        const val InboxBlock = "inbox.block"
        const val InboxReport = "inbox.report"
        const val InboxEveryone = "inbox.everyone"
        const val InboxFriends = "inbox.friends"
        const val InboxOnlyMe = "inbox.only_me"
        const val InboxPhoto = "inbox.photo"
        const val InboxText = "inbox.text"
        const val InboxAddNick = "inbox.add_nick"
        const val InboxConnecting = "inbox.connecting"
        const val InboxMore = "inbox.more"
        const val InboxChooseType = "inbox.choose_type"
        const val InboxAudience = "inbox.audience"
        const val InboxChatTheme = "inbox.chat_theme"
        const val InboxOff = "inbox.off"
        const val InboxOnceSeen = "inbox.once_seen"
        const val InboxHours = "inbox.24h"
        const val InboxDays = "inbox.7d"
        const val InboxMakeVanish = "inbox.make_vanish"
        const val InboxTimer = "inbox.timer"
        const val InboxFollowedYou = "inbox.followed_you"
        const val InboxTypeMessage = "inbox.type_message"
        const val InboxReply = "inbox.reply"
        const val InboxYouReplied = "inbox.you_replied"
        const val InboxRepliedToYou = "inbox.replied_to_you"
        const val InboxEdit = "inbox.edit"
        const val InboxEdited = "inbox.edited"
        const val InboxForward = "inbox.forward"
        const val InboxCopyAction = "inbox.copy"
        const val InboxInfo = "inbox.info"
        const val InboxUnsend = "inbox.unsend"
        const val InboxDeleteMsg = "inbox.delete_msg"
        const val InboxRemove = "inbox.remove"
        const val InboxFromYou = "inbox.from_you"
        const val InboxCreatePoll = "inbox.create_poll"
        const val InboxAskQuestion = "inbox.ask_question"
        const val InboxLeavePoll = "inbox.leave_poll"
        const val InboxMultiple = "inbox.multiple"
        const val InboxCreateEvent = "inbox.create_event"
        const val InboxEventName = "inbox.event_name"
        const val InboxEventDesc = "inbox.event_desc"
        const val InboxEventWhen = "inbox.event_when"
        const val InboxReminder = "inbox.reminder"
        const val InboxHourBefore = "inbox.hour_before"
        const val InboxMinBefore = "inbox.min_before"
        const val InboxDayBefore = "inbox.day_before"
        const val InboxNever = "inbox.never"
        const val InboxAddCalendar = "inbox.add_calendar"
        const val InboxEditEvent = "inbox.edit_event"
        const val InboxLeaveEvent = "inbox.leave_event"
        const val InboxEventSoon = "inbox.event_soon"
        const val InboxPhotoStory = "inbox.photo_story"
        const val InboxVideoStory = "inbox.video_story"
        const val InboxOpenCamera = "inbox.open_camera"
        const val InboxChooseGallery = "inbox.choose_gallery"
        const val InboxYourStory = "inbox.your_story"
        const val InboxBackground = "inbox.background"
        const val InboxVoiceHint = "inbox.voice_hint"
        const val InboxHoldMic = "inbox.hold_mic"
        const val InboxEncryptedHint = "inbox.encrypted_hint"
        const val InboxVanishHintOn = "inbox.vanish_hint_on"
        const val InboxVanishHintOff = "inbox.vanish_hint_off"
        const val InboxProfile = "inbox.profile"
        const val InboxOptions = "inbox.options"
        const val InboxCreateGroup = "inbox.create_group"
        const val InboxCreateGroupSub = "inbox.create_group_sub"
        const val InboxMediaVis = "inbox.media_vis"
        const val InboxSavingMedia = "inbox.saving_media"
        const val InboxNotSavingMedia = "inbox.not_saving_media"
        const val InboxMsgPin = "inbox.msg_pin"
        const val InboxMsgUnpin = "inbox.msg_unpin"
        const val InboxDownload = "inbox.download"
        const val InboxTranslate = "inbox.translate"
        const val InboxForwarding = "inbox.forwarding"
        const val InboxAddToStory = "inbox.add_to_story"
        const val InboxFwdStory = "inbox.fwd_story"
        const val InboxWhatsApp = "inbox.whatsapp"
        const val InboxCopyLink = "inbox.copy_link"
        const val InboxWhatsAppStatus = "inbox.whatsapp_status"
        const val InboxFwdStatus = "inbox.fwd_status"
        const val InboxSnapchat = "inbox.snapchat"
        const val InboxMessenger = "inbox.messenger"
        const val InboxSms = "inbox.sms"
        const val InboxLink = "inbox.link"
        const val InboxFacebook = "inbox.facebook"
        const val InboxWriteMsg = "inbox.write_msg"
        const val InboxYourReactions = "inbox.your_reactions"
        const val InboxReactions = "inbox.reactions"
        const val InboxTapToRemove = "inbox.tap_to_remove"
        const val InboxYou = "inbox.you"
        const val InboxCustomize = "inbox.customize"
        const val InboxSearchShort = "inbox.search_short"
        const val InboxSendBtn = "inbox.send_btn"
        const val InboxJustNow = "inbox.just_now"
        const val InboxMinutesAgo = "inbox.minutes_ago"
        const val InboxPaste = "inbox.paste"
        const val InboxSelectAll = "inbox.select_all"
        const val InboxImages = "inbox.images"
        const val InboxReels = "inbox.reels"
        const val InboxCopied = "inbox.copied"
        const val InboxAllowPhotos = "inbox.allow_photos"
        const val InboxWallpaper = "inbox.wallpaper"
        const val InboxBubbleColor = "inbox.bubble_color"
        const val InboxApply = "inbox.apply"
        const val InboxContacts = "inbox.contacts_title"
        const val InboxAllowContacts = "inbox.allow_contacts"
        const val InboxDocs = "inbox.docs"
        const val InboxLinks = "inbox.links"
    }
}

@Composable
fun rememberCopy(): Copy {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    return remember(locale.language) { Copy(locale.language) }
}

private fun table(lang: String): Map<String, String> = when (lang) {
    "zh" -> Zh
    "ar" -> Ar
    "ru" -> Ru
    "ur" -> Ur
    "hi" -> Hi
    "fr" -> Fr
    "nl" -> Nl
    else -> En
}

private val En = mapOf(
    Copy.TabHome to "Home",
    Copy.TabGames to "Games",
    Copy.TabCreate to "Create",
    Copy.TabInbox to "Inbox",
    Copy.TabProfile to "Profile",
    Copy.HomeForYou to "For You",
    Copy.HomeFollowing to "Following",
    Copy.HomeLive to "Live",
    Copy.HomeFollowEmpty to "Follow creators to see their videos",
    Copy.LiveBadge to "LIVE",
    Copy.Home2x to "2×",
    Copy.HomeCommentsCount to "{n} comments",
    Copy.HomeReply to "Reply",
    Copy.HomeReplyingTo to "Replying to {name}",
    Copy.HomeViewReplies to "View {n} replies",
    Copy.HomeAddComment to "Add comment…",
    Copy.HomeSortTop to "Top comments",
    Copy.HomeSortNewest to "Newest first",
    Copy.HomeSortMedia to "With media",
    Copy.HomeSoundOriginal to "Original Sound",
    Copy.HomeSoundPosts to "{n} posts",
    Copy.HomeSoundSave to "Save",
    Copy.HomeSoundUse to "Use sound",
    Copy.HomeSoundStory to "Post to Story",
    Copy.HomeSoundFind to "Find related content",
    Copy.HomeSearchHint to "Search…",
    Copy.HomeSearchGo to "Search",
    Copy.HomeSearchRecent to "Recent video",
    Copy.HomeSearchSeeMore to "See more",
    Copy.HomeSearchYouMayLike to "You may like",
    Copy.HomeSearchRefresh to "Refresh",
    Copy.LiveExplore to "Explore",
    Copy.LivePkBattle to "PK Battle",
    Copy.LiveAudioTab to "Audio",
    Copy.LiveAll to "All",
    Copy.LiveVideoPill to "Video",
    Copy.LiveAudioPill to "Audio",
    Copy.LiveSearchCountry to "Search country…",
    Copy.LiveNoCountries to "No countries found",
    Copy.LiveEvent to "LIVE EVENT",
    Copy.LiveFeatured to "🔥 Top Featured Streams",
    Copy.LiveFeaturedSub to "Tap to join a live now",
    Copy.LiveTrendingNow to "Trending Now",
    Copy.LivePkBattles to "PK Battles",
    Copy.LiveNow to "Live Now",
    Copy.LiveCount to "{n} live",
    Copy.LiveFollow to "Follow",
    Copy.LiveFollowing to "Following",
    Copy.LiveHostMessage to "Message",
    Copy.LiveHostDiamonds to "Diamonds this LIVE",
    Copy.LiveHostWatching to "Watching",
    Copy.LiveHostFollowers to "Followers",
    Copy.FramesTitle to "Frames",
    Copy.FramesMine to "My frames",
    Copy.FramesShop to "Frame shop",
    Copy.FramesBuy to "Buy · {n} coins",
    Copy.FramesActivate to "Activate",
    Copy.FramesActive to "Active",
    Copy.FramesExpired to "Expired",
    Copy.FramesPermanent to "Permanent",
    Copy.FramesDays to "{n} days",
    Copy.FramesDay1 to "1 day",
    Copy.FramesWeek1 to "1 week",
    Copy.FramesMonth1 to "1 month",
    Copy.FramesMonths3 to "3 months",
    Copy.FramesDaysLeft to "{n}d left",
    Copy.FramesInsufficient to "Not enough coins.",
    Copy.FramesEmpty to "No frames in the shop yet.",
    Copy.FramesOwnedEmpty to "You don't own any frames yet.",
    Copy.FramesPickDuration to "Choose duration",
    Copy.StoreTitle to "Store",
    Copy.StoreMine to "My items",
    Copy.StoreKindFrames to "Avatar frames",
    Copy.StoreKindBadges to "Badges",
    Copy.StoreKindEntrance to "Entrance effects",
    Copy.StoreKindVip to "VIP",
    Copy.StoreKindBubbles to "Chat bubbles",
    Copy.LiveEntranceJoined to "{name} joined",
    Copy.LiveSendGift to "Send Gift",
    Copy.LiveGiftsTitle to "Gifts",
    Copy.LiveGiftSend to "Send",
    Copy.LiveGiftSendTo to "Send to",
    Copy.LiveGiftBalance to "Coins",
    Copy.LiveGiftRecharge to "Recharge",
    Copy.LiveGiftNotEnough to "Not enough coins for this gift.",
    Copy.LiveGiftNotEnoughTitle to "Insufficient balance",
    Copy.LiveGiftNotEnoughBody to "{gift} costs {price} coins and you have {coins}. Recharge to keep the party going.",
    Copy.LiveGiftFailed to "Could not send the gift. Try again.",
    Copy.LiveGiftFailedTitle to "Gift not sent",
    Copy.LiveGiftDismiss to "Not now",
    Copy.LiveGiftRetry to "Try again",
    Copy.LiveGiftTooFast to "You are sending very fast. Take a breath and try again.",
    Copy.LiveChatSend to "Send",
    Copy.LiveGiftEmpty to "No gifts yet.",
    Copy.LiveGiftLoading to "Loading gifts…",
    Copy.LiveGiftSentLine to "sent {gift} to {to}",
    Copy.LiveGiftHost to "Host",
    Copy.LiveGiftCombo to "Combo",
    Copy.LiveGiftLuckyHint to "Tap again to keep sending",
    Copy.LiveStickers to "Stickers",
    Copy.LiveStickerTo to "To",
    Copy.LiveStickerEveryone to "Everyone",
    Copy.LiveStickerEmpty to "No stickers yet.",
    Copy.LiveStickerLoading to "Loading stickers…",
    Copy.GiftRecordTitle to "Gifting record",
    Copy.GiftRecordToday to "Today",
    Copy.GiftRecordWeek to "This week",
    Copy.GiftRecordMonth to "This month",
    Copy.GiftRecordCustom to "Dates",
    Copy.GiftRecordSent to "Sent",
    Copy.GiftRecordReceived to "Received",
    Copy.GiftRecordGifts to "{n} gifts",
    Copy.GiftRecordSenderLevel to "Sender level",
    Copy.GiftRecordReceiverLevel to "Receiver level",
    Copy.GiftRecordNoLevel to "No level yet",
    Copy.GiftRecordLevelN to "Level {n}",
    Copy.GiftRecordToNext to "{n} coins to the next level",
    Copy.GiftRecordTop to "Top level reached",
    Copy.GiftRecordRange to "{from} – {to}",
    Copy.GiftRecordByDay to "By day",
    Copy.GiftRecordEmpty to "No gifts in this period.",
    Copy.GiftRecordFrom to "From (YYYY-MM-DD)",
    Copy.GiftRecordTo to "To (YYYY-MM-DD)",
    Copy.GiftRecordApply to "Show",
    Copy.GiftRecordBadDate to "Enter dates as YYYY-MM-DD.",
    Copy.ProfileShareTitle to "Share profile",
    Copy.ProfileShareTagline to "Follow me on {app}",
    Copy.ProfileShareCopy to "Copy link",
    Copy.ProfileShareSave to "Save card",
    Copy.ProfileShareSaved to "Saved to your gallery",
    Copy.ProfileShareSaveFailed to "Couldn't save the card",
    Copy.ProfileShareLinkCopied to "Profile link copied",
    Copy.ProfileShareLinkPending to "Your link isn't ready yet. Try again in a moment.",
    Copy.ProfileNoLiked to "Videos you like will show up here",
    Copy.ProfileNoSaved to "Nothing saved for later yet",
    Copy.ProfileNoPrivate to "No private posts",
    Copy.ProfileReceivingEmpty to "No gifts received in this period",
    Copy.TxnTitle to "Transaction history",
    Copy.TxnSpent to "Spent",
    Copy.TxnEarned to "Received",
    Copy.TxnCoins to "Coins",
    Copy.TxnDiamonds to "Diamonds",
    Copy.TxnCatGiftsSent to "Gifts sent",
    Copy.TxnCatStore to "Store purchases",
    Copy.TxnCatGameBets to "Game bets",
    Copy.TxnCatGiftsReceived to "Gifts received",
    Copy.TxnCatTopups to "Top-ups",
    Copy.TxnCatGameWins to "Game wins",
    Copy.TxnCatWithdrawals to "Withdrawals",
    Copy.TxnCatAdjustments to "Adjustments",
    Copy.TxnEntries to "{n} entries",
    Copy.TxnByDay to "By day",
    Copy.TxnEmpty to "Nothing in this period.",
    Copy.TxnSentTo to "Sent to",
    Copy.TxnReceivedFrom to "Received from",
    Copy.TxnGiftsWith to "Gifts to {name}",
    Copy.TxnGiftsFromName to "Gifts from {name}",
    Copy.TxnLoadMore to "Load more",
    Copy.TxnRetry to "Retry",
    Copy.TxnDaysPack to "{n}-day pack",
    Copy.TxnExpires to "Expires {date}",
    Copy.TxnPeople to "{n} people",
    Copy.WithdrawTitle to "Withdraw diamonds",
    Copy.WithdrawTabMethods to "Payment methods",
    Copy.WithdrawTabRequests to "My requests",
    Copy.WithdrawAvailable to "Available diamonds",
    Copy.WithdrawOnHold to "{n} on hold in {c} pending",
    Copy.WithdrawLimits to "Min {min} · Max {max} diamonds",
    Copy.WithdrawClosedRange to "Withdrawals open from the {from} to the {to} of each month. Next window: {next}.",
    Copy.WithdrawClosedDay to "Withdrawals open on the {from} of each month. Next window: {next}.",
    Copy.WithdrawOpenRange to "Open from the {from} to the {to} of each month",
    Copy.WithdrawOpenDay to "Open on the {from} of each month",
    Copy.WithdrawNoMethods to "No payment methods are available in your country yet.",
    Copy.WithdrawPickMethod to "Choose how you want to be paid",
    Copy.WithdrawAccountTitle to "Account holder name",
    Copy.WithdrawAmount to "Diamonds to withdraw",
    Copy.WithdrawSubmit to "Submit request",
    Copy.WithdrawSubmitting to "Submitting…",
    Copy.WithdrawFill to "Fill in every field.",
    Copy.WithdrawInsufficient to "You only have {n} diamonds.",
    Copy.WithdrawBelowMin to "Minimum is {n} diamonds.",
    Copy.WithdrawAboveMax to "Maximum is {n} diamonds.",
    Copy.WithdrawDone to "Request submitted. {n} diamonds are on hold until it is reviewed.",
    Copy.WithdrawStatusPending to "Pending",
    Copy.WithdrawStatusApproved to "Approved",
    Copy.WithdrawStatusRejected to "Rejected",
    Copy.WithdrawNoRequests to "No withdrawal requests yet.",
    Copy.WithdrawCountry to "Country: {c}",
    Copy.WithdrawAll to "All",
    Copy.LiveSaySomething to "Say something…",
    Copy.LiveLeaveTitle to "Leave this broadcast?",
    Copy.LiveMinimize to "Minimize",
    Copy.LiveMinimizeSub to "Keep watching in mini player",
    Copy.LiveClose to "Close",
    Copy.LiveCloseSub to "Leave this stream",
    Copy.LiveCancelStay to "Cancel, stay in stream",
    Copy.LiveHostEnded to "The host has ended this live",
    Copy.LiveConnectionLost to "Your connection dropped and the live could not be rejoined",
    Copy.LiveOk to "OK",
    Copy.LiveSummaryTitle to "Your live has ended",
    Copy.LiveSummaryLost to "Your connection dropped for too long, so the live was ended for everyone.",
    Copy.LiveSummaryDuration to "Duration",
    Copy.LiveSummaryPeak to "Peak viewers",
    Copy.LiveSummaryJoins to "Joins",
    Copy.LiveSummaryFailed to "Couldn't start the live. Check your connection and try again.",
    Copy.LiveNoneLive to "Nobody is live right now",
    Copy.LiveNoneLiveSub to "Go live and you'll show up here instantly.",
    Copy.LiveWatching to "{n} watching",
    Copy.LiveStartFailedTitle to "Couldn't go live",
    Copy.LivePrivate to "Private",
    Copy.LiveFriends to "Friends",
    Copy.LivePasswordTitle to "This live is private",
    Copy.LivePasswordHint to "Enter the password",
    Copy.LivePasswordJoin to "Join",
    Copy.LivePasswordWrong to "That password is not correct. Try again.",
    Copy.LivePasswordCancel to "Cancel",
    Copy.LiveHostAway to "Host's connection is weak — waiting for them to reconnect…",
    Copy.LiveHostBack to "Host is back",
    Copy.LivePeopleTitle to "In this live",
    Copy.LivePeopleEmpty to "Nobody else is here yet",
    Copy.LivePeopleHost to "Host",
    Copy.LivePeopleGuest to "Guest",
    Copy.LiveCoHosts to "Co-hosts",
    Copy.LiveCoHostRequest to "Join as co-host",
    Copy.LiveCoHostRequested to "Requested · tap to cancel",
    Copy.LiveCoHostWants to "{name} wants to join as co-host",
    Copy.LiveCoHostAccept to "Accept",
    Copy.LiveCoHostDecline to "Decline",
    Copy.LiveCoHostMore to "+{n} more",
    Copy.LiveCoHostNone to "No co-hosts yet",
    Copy.LiveRequestsOn to "Co-host requests on",
    Copy.LiveRequestsOff to "Co-host requests off",
    Copy.LiveStageRemove to "Remove",
    Copy.LiveStageLeave to "Leave stage",
    Copy.LiveStageDenied to "The host declined your request.",
    Copy.LiveStageDropped to "The host removed you from the stage.",
    Copy.LiveStageFull to "The stage is full.",
    Copy.LiveStageCoins to "Received on stage",
    Copy.LiveStageSince to "On stage {t}",
    Copy.LiveSeatTap to "Tap to join",
    Copy.LiveSeatsTitle to "Seats",
    Copy.LiveSeatsHint to "Add seats while live — nobody has to leave.",
    Copy.LiveSeatsCount to "{n} seats",
    Copy.LiveEndCall to "End",
    Copy.LiveMore to "More",
    Copy.LiveViewProfile to "View profile",
    Copy.LiveModeration to "Moderation",
    Copy.LiveModerators to "Moderators",
    Copy.LiveModeratorsNone to "No moderators yet. Open someone's card in chat to make them one.",
    Copy.LiveMakeMod to "Make moderator",
    Copy.LiveRemoveMod to "Remove moderator",
    Copy.LiveModFor to "Moderator for",
    Copy.LiveMute to "Mute",
    Copy.LiveUnmute to "Unmute",
    Copy.LiveMuteFor to "Mute chat for",
    Copy.LiveBlock to "Block from room",
    Copy.LiveBlockFor to "Block from joining for",
    Copy.LiveKick to "Kick",
    Copy.LiveDays to "{n} days",
    Copy.LiveMinutes to "{n} min",
    Copy.LiveCustom to "Custom",
    Copy.LiveApply to "Apply",
    Copy.LiveDaysLeft to "{n}d left",
    Copy.LiveChatMuted to "A moderator muted your chat for now.",
    Copy.LiveKickedNotice to "A moderator removed you from this live.",
    Copy.LiveBannedNotice to "You can't join this live right now.",
    Copy.LiveMadeMod to "You're now a moderator of this live.",
    Copy.LiveModRemoved to "You're no longer a moderator here.",
    Copy.LiveModDone to "Done",
    Copy.LiveReactMore to "More",
    Copy.LiveLikes to "{n} likes",
    Copy.StorySeenBy to "Seen by {n}",
    Copy.StoryNoViews to "No views yet",
    Copy.StoryViewedYours to "viewed your status",
    Copy.StoryPosting to "Posting…",
    Copy.StoryFailed to "Couldn't post your story. Try again.",
    Copy.StoryTooBig to "That file is too large for a story.",
    Copy.StoryDeleted to "Story deleted",
    Copy.StoryYou to "You",
    Copy.ActivityEmpty to "Nothing here yet. When someone views your status it shows up here.",
    Copy.ActivityCommented to "commented on your post",
    Copy.ActivityReplied to "replied to your comment",
    Copy.ActivityCommentLiked to "liked your comment",
    Copy.PkTitle to "PK Battle",
    Copy.PkHint to "Pick a time, find a live host, and invite them. Neither room ends — both audiences watch the fight.",
    Copy.PkDuration to "Duration",
    Copy.PkCustomMin to "min",
    Copy.PkSearch to "Find a host",
    Copy.PkSearchHint to "Search username or name",
    Copy.PkNoHosts to "No live hosts match that search",
    Copy.PkInvite to "Invite",
    Copy.PkSent to "Sent",
    Copy.PkIncoming to "{name} invited you to a {m} min PK battle",
    Copy.PkAccept to "Accept",
    Copy.PkDecline to "Decline",
    Copy.PkWin to "Victory!",
    Copy.PkLose to "Defeat",
    Copy.PkDraw to "Draw",
    Copy.PkResult to "Result",
    Copy.PkResultHold to "{s}s left to rematch",
    Copy.PkRematch to "Rematch",
    Copy.PkRematchWait to "Waiting for opponent…",
    Copy.PkEnd to "End PK",
    Copy.PkSentNotice to "PK invite sent",
    Copy.PkDeclined to "Your PK invite was declined",
    Copy.PkExpired to "No answer to your PK invite",
    Copy.PkRound to "Round {n}",
    Copy.PkWinTag to "WIN",
    Copy.PkLoseTag to "LOSE",
    Copy.PkConnecting to "Connecting…",
    Copy.LiveWatchEndedTitle to "This live has ended",
    Copy.LiveWatchEndedDuration to "Was live for",
    Copy.GamesTitle to "Games",
    Copy.GamesBody to "Play full-mode games with your coin balance.",
    Copy.GamesRefresh to "Refresh",
    Copy.GamesLoading to "Loading games…",
    Copy.GamesUnavailable to "Games are temporarily unavailable. Pull refresh to try again.",
    Copy.GamesRetry to "Try again",
    Copy.GamesEmpty to "No games available right now.",
    Copy.ComingSoon to "COMING SOON",
    Copy.CreateTitle to "Create",
    Copy.CreateBody to "Shoot, upload, or go live.",
    Copy.CreateAddSound to "Add sound",
    Copy.CreateClipTooShort to "Hold a little longer to record a clip",
    Copy.CreateCameraBusy to "Couldn't start recording. Try again.",
    Copy.CreatePost to "Post",
    Copy.CreateGoLive to "Live",
    Copy.CreateGoLiveTitle to "Live",
    Copy.CreateGoLiveBtn to "Go LIVE",
    Copy.CreatePhoto to "PHOTO",
    Copy.CreateText to "TEXT",
    Copy.CreateTextHint to "Write something",
    Copy.CreateTextTitle to "Share your thoughts",
    Copy.CreateTextType to "Type something…",
    Copy.CreateNext to "Next",
    Copy.CreateCoHosts to "Co-hosts",
    Copy.Create10m to "10m",
    Copy.Create5m to "5m",
    Copy.Create60s to "60s",
    Copy.Create15s to "15s",
    Copy.CreateCover to "Cover image",
    Copy.CreateCoverHint to "Select and preview a cover",
    Copy.CreateLiveHint to "Weekend coding session & Q&A",
    Copy.CreateVideo to "Video",
    Copy.CreateAudioOnly to "Audio",
    Copy.CreateRoomSize to "Room size",
    Copy.CreateSeats to "Custom number of seats",
    Copy.CreateSeatsHint to "Enter number (max 35)",
    Copy.CreateSeatLayout to "Seat layout",
    Copy.CreateOrderSeats to "Order seats",
    Copy.CreateOrderSeatsHint to "Drag a seat into a slot. Empty slots stay open.",
    Copy.CreateSeatHost to "Host",
    Copy.CreateSeatCount to "{n} seats",
    Copy.CreateDone to "Done",
    Copy.CreateChipCode to "Coding live",
    Copy.CreateChipAma to "Ask me anything",
    Copy.CreateChipStudy to "Study with me",
    Copy.CreateSaved to "Saved",
    Copy.CreateEndLive to "End LIVE",
    Copy.CreateFlashOn to "Flash On",
    Copy.CreateFlashOff to "Flash Off",
    Copy.CreateTimer to "Timer",
    Copy.CreateGrid to "Grid",
    Copy.CreateSpeed to "Speed",
    Copy.CreateBeauty to "Beauty",
    Copy.CreateLiveVideoTitle to "Start a Video Live",
    Copy.CreateLiveAudioTitle to "Start an Audio Room",
    Copy.CreateLiveVideoSub to "You'll be visible on camera.",
    Copy.CreateLiveAudioSub to "Listeners join to hear you speak.",
    Copy.CreateAudioRoomLabel to "Audio Room",
    Copy.CreateAudioRoomSub to "Live voice with speakers & audience",
    Copy.CreateBanner to "Listing Banner",
    Copy.CreateBannerPhoto to "Photo",
    Copy.CreateTitleField to "Title",
    Copy.CreateLiveTitleHint to "What's your live about?",
    Copy.CreateSpeakerSeats to "Speaker Seats",
    Copy.CreateCustom to "Custom",
    Copy.CreateSeatsMax to "max 35",
    Copy.CreateAudience to "Audience",
    Copy.CreateAudiencePublic to "Public",
    Copy.CreateAudienceFriends to "Friends",
    Copy.CreateAudiencePrivate to "Private",
    Copy.CreateAudiencePublicSub to "Everyone",
    Copy.CreateAudienceFriendsSub to "Following",
    Copy.CreateAudiencePrivateSub to "Password",
    Copy.CreatePasswordHint to "Set a room password…",
    Copy.CreateNextSeats to "Next — Review Seats",
    Copy.CreateSelectSeats to "Select seats to continue",
    Copy.CreateArrangeRoom to "Arrange Room",
    Copy.CreateArrangeHint to "{n} seats · tap a seat to claim host · drag to reorder",
    Copy.CreateStartAudioRoom to "Start Audio Room",
    Copy.CreateLiveChatHint to "Say something…",
    Copy.CreateGalleryAll to "All",
    Copy.CreateGalleryVideos to "Videos",
    Copy.CreateGalleryPhotos to "Photos",
    Copy.HomeFeedEmpty to "Nothing here yet. Be the first to post.",
    Copy.HomeFeedOffline to "You're offline. Showing what we have.",
    Copy.HomeFeedRetry to "Retry",
    Copy.HomeCommentsEmpty to "No comments yet. Say something nice.",
    Copy.HomeCommentsOff to "Comments are off for this post.",
    Copy.HomePosting to "Posting…",
    Copy.HomeFailed to "Couldn't process",
    Copy.ProfileNoPosts to "No posts yet",
    Copy.HomeSearchPeople to "People",
    Copy.HomeSearchNoPeople to "No one found for \"{q}\"",
    Copy.HomeSearchFollowers to "{n} followers",
    Copy.HomeFollowsYou to "Follows you",
    Copy.ProfileNoPeople to "No one here yet",
    Copy.InboxTitle to "Inbox",
    Copy.InboxBody to "Chats, notifications, and activity arrive next.",
    Copy.ProfileCopy to "Copy",
    Copy.ProfileVerified to "Verified",
    Copy.ProfileFollowing to "Following",
    Copy.ProfileFollowers to "Followers",
    Copy.ProfileLikes to "Likes",
    Copy.ProfileRestricted to "Restricted",
    Copy.ProfileCoins to "Coins",
    Copy.ProfileDiamonds to "Diamonds",
    Copy.ProfileEdit to "Edit Profile",
    Copy.ProfileShare to "Share",
    Copy.ProfileLatest to "Latest",
    Copy.ProfilePopular to "Popular",
    Copy.ProfileSearch to "Search profile, posts, sounds…",
    Copy.ProfileName to "Your Name",
    Copy.ProfileNameLabel to "Name",
    Copy.ProfileStatus to "Living the moment",
    Copy.ProfileWhatsOnMind to "What's on your mind?",
    Copy.ProfileStatusTitle to "Status",
    Copy.ProfileCopied to "Copied to clipboard",
    Copy.ProfileUsernameLocked to "Username can be changed once every 30 days",
    Copy.ProfileUsernameAgainIn to "You can change your username again in {n} days",
    Copy.ProfileUsernameAvailable to "@{u} is available",
    Copy.ProfileUsernameTaken to "@{u} is already taken",
    Copy.ProfileUsernameRule to "3–24 characters: letters, numbers, dot or underscore",
    Copy.ProfileUsernameChecking to "Checking…",
    Copy.ProfileSaveFailed to "Could not save. We put your profile back.",
    Copy.ProfileBio to "Creator · Dancer · Dreamer",
    Copy.ProfileSave to "Save",
    Copy.ProfileReceiving to "Receiving",
    Copy.ProfileViews to "Views",
    Copy.ProfileVisitsTitle to "Profile visits",
    Copy.ProfileVisitsEmpty to "No visits yet",
    Copy.ProfileVisitsMonth to "Last 30 days",
    Copy.ProfileMessage to "Message",
    Copy.ProfilePosts to "Posts",
    Copy.ProfileNoPostsYet to "No posts yet",
    Copy.ProfileEditTitle to "Edit profile",
    Copy.ProfilePostEdit to "Edit post",
    Copy.ProfileChangePrivacy to "Change privacy",
    Copy.ProfilePostDelete to "Delete post",
    Copy.ProfilePostDeleteAsk to "This removes the post for everyone. It cannot be undone.",
    Copy.ProfilePostDeleteYes to "Delete",
    Copy.ProfilePostSaving to "Saving…",
    Copy.ProfilePhotoTitle to "Profile picture",
    Copy.ProfilePhotoCamera to "Take a photo",
    Copy.ProfilePhotoGallery to "Choose from gallery",
    Copy.ProfilePhotoAdd to "Add a picture",
    Copy.ProfileChangePhoto to "Change photo",
    Copy.ProfileUsername to "Username",
    Copy.ProfileGender to "Gender",
    Copy.ProfileGenderMale to "Male",
    Copy.ProfileGenderFemale to "Female",
    Copy.ProfileGenderPreferNot to "Prefer not to say",
    Copy.ProfileDrawerAccount to "Account",
    Copy.ProfileDrawerCreator to "Creator",
    Copy.ProfileDrawerSettings to "Settings",
    Copy.ProfileDrawerSupport to "Support",
    Copy.ProfileDrawerLegal to "Legal",
    Copy.ProfileBalance to "Balance",
    Copy.ProfileWallet to "Wallet / Coins",
    Copy.ProfileTxnHistory to "Transaction History",
    Copy.ProfileCreatorTools to "Creator Tools",
    Copy.ProfileAnalytics to "Analytics",
    Copy.ProfileLiveCenter to "LIVE Center",
    Copy.ProfileSettings to "Settings",
    Copy.ProfileHelpCentre to "Help Centre",
    Copy.ProfileReportProblem to "Report a Problem",
    Copy.HelpSubject to "Subject",
    Copy.HelpMessage to "Message",
    Copy.HelpSubmit to "Submit",
    Copy.HelpMyReports to "My reports",
    Copy.HelpAddAccount to "Log in to another account",
    Copy.HelpCreateAccount to "Create account",
    Copy.HelpUseExisting to "Use existing account",
    Copy.ProfileTerms to "Terms & Policies",
    Copy.ProfileLogOut to "Log Out",
    Copy.ProfileTopSenders to "Top Senders",
    Copy.ProfileGifts to "Gifts",
    Copy.ProfileDaily to "Daily",
    Copy.ProfileWeekly to "Weekly",
    Copy.ProfileMonthly to "Monthly",
    Copy.ProfileMutual to "Mutual",
    Copy.ProfileUnfollow to "Unfollow",
    Copy.ProfileFollowBack to "Follow Back",
    Copy.ProfileFollow to "Follow",
    Copy.ProfileFollowingBtn to "Following",
    Copy.ProfileSwitchAccounts to "Switch accounts",
    Copy.ProfileAccountSettings to "Account settings",
    Copy.ProfileActivatePassword to "Activate password login",
    Copy.ProfileChangePassword to "Change password",
    Copy.ProfileActivatePasswordHint to "Add a password so you can also sign in with your email or username.",
    Copy.ProfileChangePasswordHint to "Enter your current password, then choose a new one.",
    Copy.ProfileCurrentPassword to "Current password",
    Copy.ProfileNewPassword to "New password (at least 8 characters)",
    Copy.ProfileConfirmPassword to "Confirm new password",
    Copy.ProfilePasswordMismatch to "Passwords do not match.",
    Copy.ProfileUpdatePassword to "Update password",
    Copy.ProfilePrivacySafety to "Privacy & Safety",
    Copy.ProfilePrivacy to "Privacy",
    Copy.ProfileDms to "Direct messages",
    Copy.ProfileBlocked to "Blocked accounts",
    Copy.ProfileSafeMode to "Safe mode",
    Copy.ProfileContentDisplay to "Content & Display",
    Copy.ProfileDisplay to "Display",
    Copy.ProfileLanguage to "Language",
    Copy.ProfileTranslation to "Translation",
    Copy.ProfileNotifications to "Notifications",
    Copy.NotifyMessages to "Messages",
    Copy.NotifyAudioCalls to "Audio calls",
    Copy.NotifyVideoCalls to "Video calls",
    Copy.NotifyLive to "Live",
    Copy.NotifyComments to "Video comments",
    Copy.NotifyReplies to "Comment replies",
    Copy.NotifyMentions to "Mentions",
    Copy.NotifyPushOn to "Push notifications are on for this device.",
    Copy.NotifyOsOff to "Notifications are turned off for this app in system settings.",
    Copy.NotifyNotReady to "This device is not registered for push yet. It retries automatically.",
    Copy.NotifyOpenSystem to "Open settings",
    Copy.NotifyLoadFailed to "Could not load your notification settings.",
    Copy.NotifyRetry to "Retry",
    Copy.BlockedChats to "Accounts",
    Copy.BlockedLive to "My lives",
    Copy.BlockedChatsHint to "You can't message or call each other.",
    Copy.BlockedLiveHint to "They can't join your live broadcasts.",
    Copy.BlockedEmpty to "No blocked accounts",
    Copy.RolesTitle to "My roles",
    Copy.RolesTabMine to "Roles",
    Copy.RolesTabRequests to "Requests",
    Copy.RolesTabApprovals to "Approvals",
    Copy.RolesMineEmpty to "You don't hold a role yet. Got a referral code? Enter it below.",
    Copy.RolesJoinTitle to "Join with a referral code",
    Copy.RolesJoinHint to "The person who gave you the code becomes your upline and approves your application.",
    Copy.RolesCodePlaceholder to "Referral code",
    Copy.RolesCodeOwner to "Code from {name} ({role})",
    Copy.RolesPickRole to "Apply as",
    Copy.RolesNotePlaceholder to "Note to your upline (optional)",
    Copy.RolesApply to "Apply",
    Copy.RolesApplied to "Application sent. You'll be notified once it's reviewed.",
    Copy.RolesMyCodes to "My referral codes",
    Copy.RolesCodeOpens to "Onboards: {roles}",
    Copy.RolesCopied to "Code copied",
    Copy.RolesShareCode to "Share",
    Copy.RolesShareText to "Use my referral code {code} to join: {link}",
    Copy.RolesFinance to "Gifts & earnings",
    Copy.RolesGiftsReceived to "Received",
    Copy.RolesGiftsSent to "Sent",
    Copy.RolesReceivedLine to "{coins} coins · {diamonds} diamonds · {n} gifts",
    Copy.RolesSentLine to "{coins} coins · {n} gifts",
    Copy.RolesWithdrawals to "Withdrawal requests",
    Copy.RolesNoWithdrawals to "No withdrawal requests yet.",
    Copy.RolesWdAmount to "{n} diamonds",
    Copy.RolesWdPending to "Pending",
    Copy.RolesWdApproved to "Approved",
    Copy.RolesWdRejected to "Rejected",
    Copy.RolesUpline to "Upline",
    Copy.RolesTopOfTree to "Top of the tree",
    Copy.RolesDownline to "Downline",
    Copy.RolesDirect to "Direct",
    Copy.RolesWhole to "Everyone",
    Copy.RolesDownlineEmpty to "Nobody under you yet. Share your referral code.",
    Copy.RolesDirectCount to "{n} direct",
    Copy.RolesRequestsEmpty to "No requests yet.",
    Copy.RolesApprovalsEmpty to "Nothing waiting for you.",
    Copy.RolesApprove to "Approve",
    Copy.RolesReject to "Reject",
    Copy.RolesCancel to "Cancel request",
    Copy.RolesReasonPlaceholder to "Reason (shown to the person)",
    Copy.RolesReasonRequired to "Add a short reason.",
    Copy.RolesDecided to "Decided by {name}",
    Copy.RolesRequestedBy to "Requested by {name}",
    Copy.RolesWaiting to "Waiting for approval",
    Copy.RolesExpires to "Expires {when}",
    Copy.RolesStatusActive to "Active",
    Copy.RolesStatusSuspended to "Suspended",
    Copy.RolesStatusRevoked to "Removed",
    Copy.RolesStatusBlocked to "Blocked",
    Copy.RolesReqPending to "Pending",
    Copy.RolesReqApproved to "Approved",
    Copy.RolesReqRejected to "Rejected",
    Copy.RolesReqCancelled to "Cancelled",
    Copy.RolesReqExpired to "Expired",
    Copy.RolesKindAssign to "Assignment",
    Copy.RolesKindApply to "Application",
    Copy.RolesKindTransfer to "Transfer",
    Copy.RolesKindRevoke to "Removal",
    Copy.RolesKindReactivate to "Reactivation",
    Copy.RolesNoticeRequest to "Role request",
    Copy.RolesNoticeDecision to "Role request decided",
    Copy.RolesNoticeChange to "Your role changed",
    Copy.RolesLoadFailed to "Could not load. Check your connection.",
    Copy.RolesYourCode to "Your code",
    Copy.RolesJoinedWith to "Joined with code {code}",
    Copy.RolesAddMember to "Add a member",
    Copy.RolesAddMemberAs to "Add as",
    Copy.RolesCanRecruit to "You can add: {roles}",
    Copy.RolesRecruitViaCode to "Share your code — they apply, you approve",
    Copy.RolesPickPerson to "Who are you adding?",
    Copy.RolesSearchPeople to "Search by name or @username",
    Copy.RolesNoPeople to "No one found",
    Copy.RolesFormTitle to "Application for {role}",
    Copy.RolesFormNone to "No questions for this role — just confirm.",
    Copy.RolesFormOptional to "optional",
    Copy.RolesFormRequired to "Required",
    Copy.RolesFormChoose to "Choose…",
    Copy.RolesFormChooseCountry to "Choose a country",
    Copy.RolesFormPhoneHint to "Phone with country code, e.g. +92 300 1234567",
    Copy.RolesFormNumber to "Enter a number",
    Copy.RolesFormMinLen to "At least {n} characters",
    Copy.RolesFormMaxLen to "At most {n} characters",
    Copy.RolesFormMin to "Minimum {n}",
    Copy.RolesFormMax to "Maximum {n}",
    Copy.RolesFormFix to "Check the highlighted answers.",
    Copy.RolesApproveNow to "Approve now",
    Copy.RolesApproveNowHint to "Activate immediately instead of sending for approval.",
    Copy.RolesSendForApproval to "Send for approval",
    Copy.RolesAssignNow to "Add now",
    Copy.RolesAssigned to "{name} is now {role}.",
    Copy.RolesAssignSent to "Request sent for approval.",
    Copy.RolesAnswers to "Application answers",
    Copy.RolesManage to "Manage {name}",
    Copy.RolesActSuspend to "Suspend",
    Copy.RolesActReactivate to "Reactivate",
    Copy.RolesActBlock to "Block",
    Copy.RolesActUnblock to "Unblock",
    Copy.RolesActRemove to "Remove from role",
    Copy.RolesActTransfer to "Move to another upline",
    Copy.RolesActDone to "Done.",
    Copy.RolesTransferTo to "New upline",
    Copy.RolesTransferChildrenOnly to "Move only their downline (they stay)",
    Copy.RolesTransferHint to "Search members of your role to move them under.",
    Copy.RolesRemoveKeep to "Keep their downline in place",
    Copy.RolesRemoveMoveUp to "Move their downline up to you",
    Copy.RolesRemoveSuspend to "Suspend everyone below them",
    Copy.RolesQuestions to "{n} questions",
    Copy.RolesActions to "Actions",
    Copy.ProfileSupportLegal to "Support & Legal",
    Copy.ProfileLocationContacts to "Location & Contacts",
    Copy.ProfileVersion to "{app} v1.0.0",
    Copy.FeatureUnavailable to "This section is temporarily unavailable.",
    Copy.InboxChatOpenFail to "Couldn't open this chat. Please try again.",
    Copy.InboxOk to "OK",
    Copy.YouHandle to "@you_{wordmark}",
    Copy.InboxChats to "Chats",
    Copy.InboxNotifications to "Notifications",
    Copy.InboxActivity to "Activity",
    Copy.InboxSearch to "Search chats and people",
    Copy.InboxPinned to "PINNED",
    Copy.InboxAll to "ALL MESSAGES",
    Copy.InboxArchived to "Archived",
    Copy.InboxNewGroup to "New Group",
    Copy.InboxNewChat to "New Chat",
    Copy.InboxGroupName to "Group name",
    Copy.InboxInviteLink to "Invite link",
    Copy.InboxCreate to "Create",
    Copy.InboxCancel to "Cancel",
    Copy.InboxMyStory to "My story",
    Copy.InboxMessage to "Message",
    Copy.InboxOnline to "Online",
    Copy.InboxOffline to "Offline",
    Copy.InboxTyping to "typing…",
    Copy.InboxLastSeen to "Last seen {t} ago",
    Copy.InboxBlocked to "You blocked this account",
    Copy.InboxUnblock to "Unblock",
    Copy.InboxReported to "Thanks. We'll review this.",
    Copy.InboxMembers to "members",
    Copy.InboxVanish to "Vanish Mode · Messages disappear after seen",
    Copy.InboxGallery to "Gallery",
    Copy.InboxCamera to "Camera",
    Copy.InboxContact to "Contact",
    Copy.InboxLocation to "Location",
    Copy.InboxDocument to "Document",
    Copy.InboxPoll to "Poll",
    Copy.InboxEvent to "Event",
    Copy.InboxAi to "AI Images",
    Copy.InboxMute to "Mute",
    Copy.InboxUnmute to "Unmute",
    Copy.InboxPin to "Pin Chat",
    Copy.InboxUnpin to "Unpin Chat",
    Copy.InboxArchive to "Archive",
    Copy.InboxUnarchive to "Unarchive",
    Copy.InboxDelete to "Delete Chat",
    Copy.InboxTheme to "Theme",
    Copy.InboxNicknames to "Nicknames",
    Copy.InboxDisappear to "Disappearing Messages",
    Copy.InboxChatInfo to "Chat Info",
    Copy.InboxCalling to "Calling…",
    Copy.CallRinging to "Ringing…",
    Copy.CallReconnecting to "Reconnecting…",
    Copy.CallIncomingAudio to "Incoming voice call",
    Copy.CallIncomingVideo to "Incoming video call",
    Copy.CallAccept to "Accept",
    Copy.CallDecline to "Decline",
    Copy.CallBusy to "Busy",
    Copy.CallNoAnswer to "No answer",
    Copy.CallDeclined to "Call declined",
    Copy.CallMissed to "Missed call",
    Copy.CallLost to "Connection lost",
    Copy.CallFailed to "Couldn't connect",
    Copy.CallUnavailable to "Calling isn't available on this build",
    Copy.CallEnded to "Call ended",
    Copy.CallOngoing to "Ongoing call",
    Copy.CallChannel to "Calls",
    Copy.CallIncomingChannel to "Incoming calls",
    Copy.CallAddPerson to "Add person",
    Copy.CallNobodyToAdd to "Nobody else to add right now",
    Copy.CallInCall to "{n} in call",
    Copy.CallYou to "You",
    Copy.InboxEnd to "End",
    Copy.InboxSpeaker to "Speaker",
    Copy.InboxVideo to "Video",
    Copy.InboxEncrypted to "End-to-end encrypted",
    Copy.InboxFollow to "Follow",
    Copy.InboxNewFollowers to "NEW FOLLOWERS",
    Copy.InboxSystem to "SYSTEM",
    Copy.InboxVideoRestrictedTitle to "Video restricted",
    Copy.InboxVideoRestrictedBody to "Your video has been restricted for posting against guidelines. If you think this is a mistake you can appeal back for review.",
    Copy.InboxSupportRepliedTitle to "Support replied",
    Copy.InboxTypeSearch to "Type to search messages",
    Copy.InboxNoResults to "No results for \"{q}\"",
    Copy.InboxPeople to "People",
    Copy.InboxSeen to "Seen",
    Copy.InboxDelivered to "Delivered",
    Copy.InboxRecents to "Recents",
    Copy.InboxSeeAll to "See all",
    Copy.InboxPause to "Pause",
    Copy.InboxResume to "Resume",
    Copy.InboxShare to "Share",
    Copy.InboxReadReceipts to "Read Receipts",
    Copy.InboxMedia to "Media, Links & Docs",
    Copy.InboxMarkRead to "Mark as Read",
    Copy.InboxOnlineShort to "{n} members",
    Copy.InboxCreateStory to "Create Story",
    Copy.InboxShareStory to "Share Story",
    Copy.InboxSearchChat to "Search in conversation…",
    Copy.InboxPinnedMessage to "Pinned Message",
    Copy.InboxMuteNotifs to "Mute Notifications",
    Copy.InboxShareProfile to "Share Profile",
    Copy.InboxExportChat to "Export Chat",
    Copy.InboxClearChat to "Clear Chat",
    Copy.InboxRestrict to "Restrict",
    Copy.InboxBlock to "Block",
    Copy.InboxReport to "Report",
    Copy.InboxEveryone to "Everyone",
    Copy.InboxFriends to "Friends",
    Copy.InboxOnlyMe to "Only me",
    Copy.InboxPhoto to "Photo",
    Copy.InboxText to "Text",
    Copy.InboxAddNick to "Add nickname",
    Copy.InboxConnecting to "Connecting…",
    Copy.InboxMore to "More",
    Copy.InboxChooseType to "CHOOSE TYPE",
    Copy.InboxAudience to "AUDIENCE",
    Copy.InboxChatTheme to "Chat Theme",
    Copy.InboxOff to "Off",
    Copy.InboxOnceSeen to "Once they're seen",
    Copy.InboxHours to "24 hours",
    Copy.InboxDays to "7 days",
    Copy.InboxMakeVanish to "Make messages disappear",
    Copy.InboxTimer to "Message timer",
    Copy.InboxFollowedYou to "started following you",
    Copy.InboxTypeMessage to "Message",
    Copy.InboxReply to "Reply",
    Copy.InboxYouReplied to "You replied",
    Copy.InboxRepliedToYou to "Replied to you",
    Copy.InboxEdit to "Edit",
    Copy.InboxEdited to "edited",
    Copy.InboxForward to "Forward",
    Copy.InboxCopyAction to "Copy",
    Copy.InboxInfo to "Info",
    Copy.InboxUnsend to "Unsend",
    Copy.InboxDeleteMsg to "Delete",
    Copy.InboxRemove to "Remove",
    Copy.InboxFromYou to "From you",
    Copy.InboxCreatePoll to "Create poll",
    Copy.InboxAskQuestion to "Ask a question",
    Copy.InboxLeavePoll to "Leave poll",
    Copy.InboxMultiple to "Allow multiple answers",
    Copy.InboxCreateEvent to "Create event",
    Copy.InboxEventName to "Event name",
    Copy.InboxEventDesc to "Description (optional)",
    Copy.InboxEventWhen to "Date and time",
    Copy.InboxReminder to "Reminder",
    Copy.InboxHourBefore to "1 hour before",
    Copy.InboxMinBefore to "15 minutes before",
    Copy.InboxEventSoon to "Pick a time at least 15 minutes from now",
    Copy.InboxDayBefore to "1 day before",
    Copy.InboxNever to "Never",
    Copy.InboxAddCalendar to "Add to calendar",
    Copy.InboxEditEvent to "Edit event",
    Copy.InboxLeaveEvent to "Leave event",
    Copy.InboxPhotoStory to "Photo Story",
    Copy.InboxVideoStory to "Video Story",
    Copy.InboxOpenCamera to "Open Camera",
    Copy.InboxChooseGallery to "Choose from Gallery",
    Copy.InboxYourStory to "Your story goes here",
    Copy.InboxBackground to "Background",
    Copy.InboxVoiceHint to "Voice message",
    Copy.InboxHoldMic to "Hold to record",
    Copy.InboxEncryptedHint to "End-to-end encrypted",
    Copy.InboxVanishHintOn to "Hold the sheet and pull up to turn on disappearing messages",
    Copy.InboxVanishHintOff to "Hold the sheet and pull up to turn off disappearing messages",
    Copy.InboxProfile to "Profile",
    Copy.InboxOptions to "Options",
    Copy.InboxCreateGroup to "Create a group chat",
    Copy.InboxCreateGroupSub to "Add {name} and others",
    Copy.InboxMediaVis to "Media Visibility",
    Copy.InboxSavingMedia to "Saving media to gallery",
    Copy.InboxNotSavingMedia to "Not saving to gallery",
    Copy.InboxMsgPin to "Pin",
    Copy.InboxMsgUnpin to "Unpin",
    Copy.InboxDownload to "Download",
    Copy.InboxTranslate to "Translate",
    Copy.InboxForwarding to "Forwarding",
    Copy.InboxAddToStory to "Add to story",
    Copy.InboxFwdStory to "Story",
    Copy.InboxWhatsApp to "WhatsApp",
    Copy.InboxCopyLink to "Copy link",
    Copy.InboxWhatsAppStatus to "WhatsApp Status",
    Copy.InboxFwdStatus to "Status",
    Copy.InboxSnapchat to "Snapchat",
    Copy.InboxMessenger to "Messenger",
    Copy.InboxSms to "SMS",
    Copy.InboxLink to "Link",
    Copy.InboxFacebook to "Facebook",
    Copy.InboxWriteMsg to "Write a message...",
    Copy.InboxYourReactions to "Your reactions",
    Copy.InboxReactions to "Reactions",
    Copy.InboxTapToRemove to "Tap to remove",
    Copy.InboxYou to "You",
    Copy.InboxCustomize to "Customize",
    Copy.InboxSearchShort to "Search",
    Copy.InboxSendBtn to "Send",
    Copy.InboxJustNow to "Just now",
    Copy.InboxMinutesAgo to "{n} minutes ago",
    Copy.InboxPaste to "Paste",
    Copy.InboxSelectAll to "Select all",
    Copy.InboxImages to "Images",
    Copy.InboxReels to "Reels",
    Copy.InboxCopied to "Copied",
    Copy.InboxAllowPhotos to "Allow photos",
    Copy.InboxWallpaper to "Wallpaper",
    Copy.InboxBubbleColor to "Bubble color",
    Copy.InboxApply to "Select",
    Copy.InboxContacts to "Contacts",
    Copy.InboxAllowContacts to "Allow contacts",
    Copy.InboxDocs to "Docs",
    Copy.InboxLinks to "Links",
    Copy.UploadChannel to "Uploads",
    Copy.UploadPostingPct to "Posting… {pct}%",
    Copy.UploadPosting to "Posting…",
    Copy.UploadProcessing to "Finishing your post…",
    Copy.UploadPosted to "Posted",
    Copy.UploadFailed to "Couldn't post",
    Copy.UploadRetry to "Retry",
    Copy.UploadCancel to "Cancel",
    Copy.UploadCount to "{n} posts",
    Copy.UploadQueued to "Waiting…",
    Copy.UploadFormTitle to "New post",
    Copy.UploadCaptionHint to "Describe your post…",
    Copy.UploadWhoCanSee to "Who can see this",
    Copy.UploadPublic to "Everyone",
    Copy.UploadFriends to "Friends",
    Copy.UploadPrivate to "Only me",
    Copy.UploadAllowComments to "Allow comments",
    Copy.UploadAllowDuet to "Allow duet and stitch",
    Copy.UploadPostBtn to "Post",
    Copy.UploadDetails to "Details",
    Copy.UploadDuration to "Duration",
    Copy.UploadSize to "Size",
    Copy.UploadResolution to "Resolution",
    Copy.UploadTaken to "Taken",
    Copy.UploadNot916 to "Not 9:16 — it will be shown with bars in the feed.",
    Copy.UploadReading to "Reading file…",
    Copy.UploadErrTooLong to "Videos can be up to {min} minutes.",
    Copy.UploadErrTooLarge to "This file is too large ({max} MB max).",
    Copy.UploadErrUnsupported to "This format isn't supported.",
    Copy.UploadErrUnreadable to "This file couldn't be read.",
    Copy.UploadErrAspect to "This aspect ratio isn't supported.",
)

private val Zh = mapOf(
    Copy.TabHome to "首页",
    Copy.TabGames to "游戏",
    Copy.TabCreate to "创作",
    Copy.TabInbox to "消息",
    Copy.TabProfile to "我",
    Copy.HomeForYou to "推荐",
    Copy.HomeFollowing to "关注",
    Copy.HomeLive to "直播",
    Copy.HomeFollowEmpty to "关注创作者后即可看到他们的视频",
    Copy.LiveBadge to "直播",
    Copy.GamesTitle to "游戏",
    Copy.GamesBody to "第三方游戏即将接入。",
    Copy.ComingSoon to "即将推出",
    Copy.CreateTitle to "创作",
    Copy.CreateBody to "拍照、文字和视频拍摄即将推出。",
    Copy.InboxTitle to "消息",
    Copy.InboxBody to "聊天、通知和动态即将推出。",
    Copy.ProfileCopy to "复制",
    Copy.ProfileVerified to "已认证",
    Copy.ProfileFollowing to "关注",
    Copy.ProfileFollowers to "粉丝",
    Copy.ProfileLikes to "获赞",
    Copy.ProfileCoins to "金币",
    Copy.ProfileDiamonds to "钻石",
    Copy.ProfileEdit to "编辑资料",
    Copy.ProfileShare to "分享",
    Copy.ProfileLatest to "最新",
    Copy.ProfilePopular to "热门",
    Copy.ProfileSearch to "搜索资料、作品、声音…",
    Copy.ProfileName to "你的名字",
    Copy.ProfileStatus to "活在当下",
    Copy.ProfileBio to "创作者 · 舞者 · 追梦人",
    Copy.ProfileSave to "保存",
    Copy.FeatureUnavailable to "该功能暂时不可用。",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Ar = mapOf(
    Copy.TabHome to "الرئيسية",
    Copy.TabGames to "ألعاب",
    Copy.TabCreate to "إنشاء",
    Copy.TabInbox to "الوارد",
    Copy.TabProfile to "الملف",
    Copy.HomeForYou to "لك",
    Copy.HomeFollowing to "المتابَعون",
    Copy.HomeLive to "مباشر",
    Copy.HomeFollowEmpty to "تابع صنّاع المحتوى لمشاهدة فيديوهاتهم",
    Copy.LiveBadge to "مباشر",
    Copy.GamesTitle to "ألعاب",
    Copy.GamesBody to "دمج الألعاب من أطراف ثالثة قريبًا.",
    Copy.ComingSoon to "قريبًا",
    Copy.CreateTitle to "إنشاء",
    Copy.CreateBody to "التقاط الصور والنصوص والفيديو قريبًا.",
    Copy.InboxTitle to "الوارد",
    Copy.InboxBody to "الدردشات والإشعارات والنشاط قريبًا.",
    Copy.ProfileCopy to "نسخ",
    Copy.ProfileVerified to "موثّق",
    Copy.ProfileFollowing to "يتابع",
    Copy.ProfileFollowers to "المتابعون",
    Copy.ProfileLikes to "الإعجابات",
    Copy.ProfileCoins to "عملات",
    Copy.ProfileDiamonds to "ألماس",
    Copy.ProfileEdit to "تعديل الملف",
    Copy.ProfileShare to "مشاركة",
    Copy.ProfileLatest to "الأحدث",
    Copy.ProfilePopular to "الأشهر",
    Copy.ProfileSearch to "ابحث في الملف والمنشورات والأصوات…",
    Copy.ProfileName to "اسمك",
    Copy.ProfileStatus to "أعيش اللحظة",
    Copy.ProfileBio to "صانع محتوى · راقص · حالم",
    Copy.ProfileSave to "حفظ",
    Copy.FeatureUnavailable to "هذا القسم غير متاح مؤقتًا.",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Ru = mapOf(
    Copy.TabHome to "Главная",
    Copy.TabGames to "Игры",
    Copy.TabCreate to "Создать",
    Copy.TabInbox to "Входящие",
    Copy.TabProfile to "Профиль",
    Copy.HomeForYou to "Реки",
    Copy.HomeFollowing to "Подписки",
    Copy.HomeLive to "Эфир",
    Copy.HomeFollowEmpty to "Подпишитесь на авторов, чтобы видеть их видео",
    Copy.LiveBadge to "ЭФИР",
    Copy.GamesTitle to "Игры",
    Copy.GamesBody to "Сторонние игры появятся позже.",
    Copy.ComingSoon to "СКОРО",
    Copy.CreateTitle to "Создать",
    Copy.CreateBody to "Фото, текст и запись видео появятся позже.",
    Copy.InboxTitle to "Входящие",
    Copy.InboxBody to "Чаты, уведомления и активность появятся позже.",
    Copy.ProfileCopy to "Копировать",
    Copy.ProfileVerified to "Подтверждён",
    Copy.ProfileFollowing to "Подписки",
    Copy.ProfileFollowers to "Подписчики",
    Copy.ProfileLikes to "Лайки",
    Copy.ProfileCoins to "Монеты",
    Copy.ProfileDiamonds to "Алмазы",
    Copy.ProfileEdit to "Изменить профиль",
    Copy.ProfileShare to "Поделиться",
    Copy.ProfileLatest to "Новые",
    Copy.ProfilePopular to "Популярные",
    Copy.ProfileSearch to "Поиск профиля, постов, звуков…",
    Copy.ProfileName to "Ваше имя",
    Copy.ProfileStatus to "Живу моментом",
    Copy.ProfileBio to "Автор · Танцор · Мечтатель",
    Copy.ProfileSave to "Сохранить",
    Copy.FeatureUnavailable to "Раздел временно недоступен.",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Ur = mapOf(
    Copy.TabHome to "ہوم",
    Copy.TabGames to "گیمز",
    Copy.TabCreate to "بنائیں",
    Copy.TabInbox to "انباکس",
    Copy.TabProfile to "پروفائل",
    Copy.HomeForYou to "آپ کے لیے",
    Copy.HomeFollowing to "فالو잉",
    Copy.HomeLive to "لائیو",
    Copy.HomeFollowEmpty to "کریٹرز کو فالو کریں تاکہ ان کی ویڈیوز نظر آئیں",
    Copy.LiveBadge to "لائیو",
    Copy.GamesTitle to "گیمز",
    Copy.GamesBody to "تھرڈ پارٹی گیمز جلد آ رہی ہیں۔",
    Copy.ComingSoon to "جلد آرہا ہے",
    Copy.CreateTitle to "بنائیں",
    Copy.CreateBody to "تصویر، متن اور ویڈیو جلد آئے گی۔",
    Copy.InboxTitle to "انباکس",
    Copy.InboxBody to "چیٹس، نوٹیفکیشنز اور سرگرمی جلد آئے گی۔",
    Copy.ProfileCopy to "کاپی",
    Copy.ProfileVerified to "تصدیق شدہ",
    Copy.ProfileFollowing to "فالونگ",
    Copy.ProfileFollowers to "فالوورز",
    Copy.ProfileLikes to "لائکس",
    Copy.ProfileCoins to "سکے",
    Copy.ProfileDiamonds to "ہیرے",
    Copy.ProfileEdit to "پروفائل ترمیم",
    Copy.ProfileShare to "شیئر",
    Copy.ProfileLatest to "تازہ ترین",
    Copy.ProfilePopular to "مقبول",
    Copy.ProfileSearch to "پروفائل، پوسٹس، آوازیں تلاش کریں…",
    Copy.ProfileName to "آپ کا نام",
    Copy.ProfileStatus to "لمحے میں جی رہا ہوں",
    Copy.ProfileBio to "کریٹر · ڈانسر · خواب دیکھنے والا",
    Copy.ProfileSave to "محفوظ",
    Copy.FeatureUnavailable to "یہ حصہ عارضی طور پر دستیاب نہیں۔",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Hi = mapOf(
    Copy.TabHome to "होम",
    Copy.TabGames to "गेम्स",
    Copy.TabCreate to "बनाएँ",
    Copy.TabInbox to "इनबॉक्स",
    Copy.TabProfile to "प्रोफ़ाइल",
    Copy.HomeForYou to "आपके लिए",
    Copy.HomeFollowing to "फ़ॉलोइंग",
    Copy.HomeLive to "लाइव",
    Copy.HomeFollowEmpty to "क्रिएटर्स को फ़ॉलो करें ताकि उनके वीडियो दिखें",
    Copy.LiveBadge to "लाइव",
    Copy.GamesTitle to "गेम्स",
    Copy.GamesBody to "थर्ड-पार्टी गेम्स जल्द आएँगी।",
    Copy.ComingSoon to "जल्द आ रहा है",
    Copy.CreateTitle to "बनाएँ",
    Copy.CreateBody to "फ़ोटो, टेक्स्ट और वीडियो जल्द आएगा।",
    Copy.InboxTitle to "इनबॉक्स",
    Copy.InboxBody to "चैट, सूचनाएँ और गतिविधि जल्द आएगी।",
    Copy.ProfileCopy to "कॉपी",
    Copy.ProfileVerified to "वेरिफ़ाइड",
    Copy.ProfileFollowing to "फ़ॉलोइंग",
    Copy.ProfileFollowers to "फ़ॉलोअर्स",
    Copy.ProfileLikes to "लाइक",
    Copy.ProfileCoins to "सिक्के",
    Copy.ProfileDiamonds to "हीरे",
    Copy.ProfileEdit to "प्रोफ़ाइल बदलें",
    Copy.ProfileShare to "शेयर",
    Copy.ProfileLatest to "नवीनतम",
    Copy.ProfilePopular to "लोकप्रिय",
    Copy.ProfileSearch to "प्रोफ़ाइल, पोस्ट, साउंड खोजें…",
    Copy.ProfileName to "आपका नाम",
    Copy.ProfileStatus to "पल में जी रहा हूँ",
    Copy.ProfileBio to "क्रिएटर · डांसर · सपने देखने वाला",
    Copy.ProfileSave to "सेव",
    Copy.FeatureUnavailable to "यह भाग अभी उपलब्ध नहीं है।",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Fr = mapOf(
    Copy.TabHome to "Accueil",
    Copy.TabGames to "Jeux",
    Copy.TabCreate to "Créer",
    Copy.TabInbox to "Boîte",
    Copy.TabProfile to "Profil",
    Copy.HomeForYou to "Pour toi",
    Copy.HomeFollowing to "Abonnements",
    Copy.HomeLive to "Live",
    Copy.HomeFollowEmpty to "Abonnez-vous pour voir leurs vidéos",
    Copy.LiveBadge to "LIVE",
    Copy.GamesTitle to "Jeux",
    Copy.GamesBody to "Les jeux tiers arrivent bientôt.",
    Copy.ComingSoon to "BIENTÔT",
    Copy.CreateTitle to "Créer",
    Copy.CreateBody to "Photo, texte et vidéo arrivent ensuite.",
    Copy.InboxTitle to "Boîte",
    Copy.InboxSystem to "SYSTÈME",
    Copy.InboxVideoRestrictedTitle to "Vidéo restreinte",
    Copy.InboxVideoRestrictedBody to "Votre vidéo a été restreinte pour non-respect des règles. Si vous pensez qu’il s’agit d’une erreur, vous pouvez demander un nouvel examen.",
    Copy.InboxBody to "Chats, notifications et activité arrivent ensuite.",
    Copy.ProfileCopy to "Copier",
    Copy.ProfileVerified to "Vérifié",
    Copy.ProfileFollowing to "Abonnements",
    Copy.ProfileFollowers to "Abonnés",
    Copy.ProfileLikes to "J’aime",
    Copy.ProfileCoins to "Pièces",
    Copy.ProfileDiamonds to "Diamants",
    Copy.ProfileEdit to "Modifier le profil",
    Copy.ProfileShare to "Partager",
    Copy.ProfileLatest to "Récents",
    Copy.ProfilePopular to "Populaires",
    Copy.ProfileSearch to "Rechercher profil, posts, sons…",
    Copy.ProfileName to "Votre nom",
    Copy.ProfileStatus to "Je vis l’instant",
    Copy.ProfileBio to "Créateur · Danseur · Rêveur",
    Copy.ProfileSave to "Enregistrer",
    Copy.FeatureUnavailable to "Cette section est temporairement indisponible.",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Nl = mapOf(
    Copy.TabHome to "Home",
    Copy.TabGames to "Games",
    Copy.TabCreate to "Maken",
    Copy.TabInbox to "Inbox",
    Copy.TabProfile to "Profiel",
    Copy.HomeForYou to "Voor jou",
    Copy.HomeFollowing to "Volgend",
    Copy.HomeLive to "Live",
    Copy.HomeFollowEmpty to "Volg makers om hun video’s te zien",
    Copy.LiveBadge to "LIVE",
    Copy.GamesTitle to "Games",
    Copy.GamesBody to "Games van derden komen binnenkort.",
    Copy.ComingSoon to "BINNENKORT",
    Copy.CreateTitle to "Maken",
    Copy.CreateBody to "Foto, tekst en video volgen later.",
    Copy.InboxTitle to "Inbox",
    Copy.InboxBody to "Chats, meldingen en activiteit volgen later.",
    Copy.ProfileCopy to "Kopieer",
    Copy.ProfileVerified to "Geverifieerd",
    Copy.ProfileFollowing to "Volgend",
    Copy.ProfileFollowers to "Volgers",
    Copy.ProfileLikes to "Likes",
    Copy.ProfileCoins to "Munten",
    Copy.ProfileDiamonds to "Diamanten",
    Copy.ProfileEdit to "Profiel bewerken",
    Copy.ProfileShare to "Delen",
    Copy.ProfileLatest to "Nieuwste",
    Copy.ProfilePopular to "Populair",
    Copy.ProfileSearch to "Zoek profiel, posts, sounds…",
    Copy.ProfileName to "Jouw naam",
    Copy.ProfileStatus to "Leef het moment",
    Copy.ProfileBio to "Maker · Danser · Dromer",
    Copy.ProfileSave to "Bewaar",
    Copy.FeatureUnavailable to "Dit onderdeel is tijdelijk niet beschikbaar.",
    Copy.YouHandle to "@you_{wordmark}",
)
