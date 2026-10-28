package app.xani.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

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
        const val LivePartyBadge = "home.party_badge"
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
        const val StoreFree = "store.free"
        const val StoreFreeSub = "store.free_sub"
        const val StoreTabAll = "store.tab_all"
        const val StoreTabMine = "store.tab_mine"
        const val StoreNew = "store.new"
        const val StoreOwned = "store.owned"
        const val StoreWearing = "store.wearing"
        const val StoreParked = "store.parked"
        const val StoreLapsed = "store.lapsed"
        const val StoreMineEmpty = "store.mine_empty"
        const val StoreShopEmpty = "store.shop_empty"
        const val StoreFreeEmpty = "store.free_empty"
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
        const val LiveGiftNotEnoughMultiBody = "live.gift_not_enough_multi_body"
        const val LiveGiftRecipients = "live.gift_recipients"
        const val LiveGiftToOthers = "live.gift_to_others"
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
        const val LiveGiftLocked = "live.gift_locked"
        const val LiveGiftLockedTitle = "live.gift_locked_title"
        const val GiftingTabProfile = "gifting.tab_profile"
        const val GiftingTabGifting = "gifting.tab_gifting"
        const val GiftingThisLive = "gifting.this_live"
        const val GiftingToday = "gifting.today"
        const val GiftingWeek = "gifting.week"
        const val GiftingMonth = "gifting.month"
        const val GiftingDates = "gifting.dates"
        const val GiftingTopSenders = "gifting.top_senders"
        const val GiftingTopReceivers = "gifting.top_receivers"
        const val GiftingLatest = "gifting.latest"
        const val GiftingReceived = "gifting.received"
        const val GiftingSent = "gifting.sent"
        const val GiftingDiamonds = "gifting.diamonds"
        const val GiftingCoins = "gifting.coins"
        const val GiftingGiftsN = "gifting.gifts_n"
        const val GiftingEmpty = "gifting.empty"
        const val GiftingPrivate = "gifting.private"
        const val GiftingLoadFailed = "gifting.load_failed"
        const val GiftingRetry = "gifting.retry"
        const val GiftingShow = "gifting.show"
        const val GiftingLive = "gifting.live"
        const val GiftingCashback = "gifting.cashback"
        const val GiftingLuckyWon = "gifting.lucky_won"
        const val LiveStickers = "live.stickers"
        const val LiveStickerTo = "live.sticker_to"
        const val LiveStickerSentLine = "live.sticker_sent_line"
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
        const val ProfileSendingEmpty = "profile.sending_empty"
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
        const val BalanceTitle = "balance.title"
        const val BalanceCoins = "balance.coins"
        const val BalanceDiamonds = "balance.diamonds"
        const val BalanceBeans = "balance.beans"
        const val BalanceRecharge = "balance.recharge"
        const val BalanceWithdraw = "balance.withdraw"
        const val BalanceHistory = "balance.history"
        const val BalancePending = "balance.pending"
        const val BalanceRecent = "balance.recent"
        const val RechargeTitle = "recharge.title"
        const val RechargeTabPackages = "recharge.tab_packages"
        const val RechargeTabRequests = "recharge.tab_requests"
        const val RechargePickPackage = "recharge.pick_package"
        const val RechargeNoPackages = "recharge.no_packages"
        const val RechargePickMethod = "recharge.pick_method"
        const val RechargeNoMethods = "recharge.no_methods"
        const val RechargeBonus = "recharge.bonus"
        const val RechargeSendExactly = "recharge.send_exactly"
        const val RechargeAccountTitle = "recharge.account_title"
        const val RechargeAccountNo = "recharge.account_no"
        const val RechargeCopy = "recharge.copy"
        const val RechargeCopied = "recharge.copied"
        const val RechargeInstructions = "recharge.instructions"
        const val RechargeProof = "recharge.proof"
        const val RechargeProofHint = "recharge.proof_hint"
        const val RechargeAttach = "recharge.attach"
        const val RechargeChange = "recharge.change"
        const val RechargeNote = "recharge.note"
        const val RechargeNoteHint = "recharge.note_hint"
        const val RechargeSubmit = "recharge.submit"
        const val RechargeNeedProof = "recharge.need_proof"
        const val RechargeDone = "recharge.done"
        const val RechargeNoRequests = "recharge.no_requests"
        const val RechargeCredited = "recharge.credited"
        const val RechargeYouGet = "recharge.you_get"
        const val RechargeStep = "recharge.step"
        const val SellerHubTitle = "seller.hub_title"
        const val SellerDrawerHub = "seller.drawer_hub"
        const val SellerDrawerList = "seller.drawer_list"
        const val SellerTabOverview = "seller.tab_overview"
        const val SellerTabSend = "seller.tab_send"
        const val SellerTabRequests = "seller.tab_requests"
        const val SellerTabHistory = "seller.tab_history"
        const val SellerTabTeam = "seller.tab_team"
        const val SellerBalance = "seller.balance"
        const val SellerSentToday = "seller.sent_today"
        const val SellerSentWeek = "seller.sent_week"
        const val SellerSentMonth = "seller.sent_month"
        const val SellerSentTotal = "seller.sent_total"
        const val SellerToppedUp = "seller.topped_up"
        const val SellerInactive = "seller.inactive"
        const val SellerNoSendPerm = "seller.no_send_perm"
        const val SellerFindUser = "seller.find_user"
        const val SellerFindHint = "seller.find_hint"
        const val SellerAmount = "seller.amount"
        const val SellerAmountHint = "seller.amount_hint"
        const val SellerNote = "seller.note"
        const val SellerSendBtn = "seller.send_btn"
        const val SellerSent = "seller.sent"
        const val SellerTheyGet = "seller.they_get"
        const val SellerLimits = "seller.limits"
        const val SellerNoTransfers = "seller.no_transfers"
        const val SellerNoTeam = "seller.no_team"
        const val SellerNoRequests = "seller.no_requests"
        const val SellerApprove = "seller.approve"
        const val SellerReject = "seller.reject"
        const val SellerViewProof = "seller.view_proof"
        const val SellerListTitle = "seller.list_title"
        const val SellerListEmpty = "seller.list_empty"
        const val SellerListOff = "seller.list_off"
        const val SellerTabSellers = "seller.tab_sellers"
        const val SellerTabMyRequests = "seller.tab_my_requests"
        const val SellerTabDisputes = "seller.tab_disputes"
        const val SellerRequestBtn = "seller.request_btn"
        const val SellerRequestTitle = "seller.request_title"
        const val SellerRequestDone = "seller.request_done"
        const val SellerContactWhatsapp = "seller.contact_whatsapp"
        const val SellerAccountNo = "seller.account_no"
        const val SellerSends = "seller.sends"
        const val SellerProofOptional = "seller.proof_optional"
        const val SellerDispute = "seller.dispute"
        const val SellerDisputeReason = "seller.dispute_reason"
        const val SellerDisputeOpenBtn = "seller.dispute_open"
        const val SellerDisputeEmpty = "seller.dispute_empty"
        const val SellerDisputeClosed = "seller.dispute_closed"
        const val SellerDisputeReply = "seller.dispute_reply"
        const val SellerDisputeSystem = "seller.dispute_system"
        const val SellerDisputeAgainst = "seller.dispute_against"
        const val SellerStatusPending = "seller.status_pending"
        const val SellerStatusCompleted = "seller.status_completed"
        const val SellerStatusDisputed = "seller.status_disputed"
        const val SellerStatusFailed = "seller.status_failed"
        const val SellerStatusApproved = "seller.status_approved"
        const val SellerStatusRejected = "seller.status_rejected"
        const val SellerStatusOpen = "seller.status_open"
        const val SellerStatusResolved = "seller.status_resolved"
        const val SellerStatusDismissed = "seller.status_dismissed"

        // Transfers (user → user)
        const val TransferTitle = "transfer.title"
        const val TransferDrawer = "transfer.drawer"
        const val TransferTabSend = "transfer.tab_send"
        const val TransferTabHistory = "transfer.tab_history"
        const val TransferTabDisputes = "transfer.tab_disputes"
        const val TransferFindUser = "transfer.find_user"
        const val TransferFindHint = "transfer.find_hint"
        const val TransferAmount = "transfer.amount"
        const val TransferAmountHint = "transfer.amount_hint"
        const val TransferLimits = "transfer.limits"
        const val TransferFeeLine = "transfer.fee_line"
        const val TransferTheyGet = "transfer.they_get"
        const val TransferNote = "transfer.note"
        const val TransferSendBtn = "transfer.send_btn"
        const val TransferSentOk = "transfer.sent_ok"
        const val TransferWindowLine = "transfer.window_line"
        const val TransferRecvRemaining = "transfer.recv_remaining"
        const val TransferCantReceive = "transfer.cant_receive"
        const val TransferDisabled = "transfer.disabled"
        const val TransferNoHistory = "transfer.no_history"
        const val TransferDirSent = "transfer.dir_sent"
        const val TransferDirReceived = "transfer.dir_received"
        const val TransferDisputeBtn = "transfer.dispute_btn"
        const val TransferDisputeReason = "transfer.dispute_reason"
        const val TransferDisputeOpened = "transfer.dispute_opened"
        const val TransferNoDisputes = "transfer.no_disputes"
        const val TransferDisputeTitle = "transfer.dispute_title"

        // Exchange (user ↔ system)
        const val ExchangeTitle = "exchange.title"
        const val ExchangeDrawer = "exchange.drawer"
        const val ExchangeTabConvert = "exchange.tab_convert"
        const val ExchangeTabHistory = "exchange.tab_history"
        const val ExchangePair = "exchange.pair"
        const val ExchangeAmount = "exchange.amount"
        const val ExchangeAmountHint = "exchange.amount_hint"
        const val ExchangeRateLine = "exchange.rate_line"
        const val ExchangeFeeLine = "exchange.fee_line"
        const val ExchangeYouGet = "exchange.you_get"
        const val ExchangeBtn = "exchange.btn"
        const val ExchangeDone = "exchange.done"
        const val ExchangeDisabled = "exchange.disabled"
        const val ExchangeNoHistory = "exchange.no_history"
        const val ExchangeLimits = "exchange.limits"
        const val ExchangeWindowLine = "exchange.window_line"
        const val ExchangeTotal = "exchange.total"
        const val ExchangeFeesPaid = "exchange.fees_paid"

        // Live leaderboard (trophy on the Live lane)
        const val LbTitle = "lb.title"
        const val LbSenders = "lb.senders"
        const val LbHosts = "lb.hosts"
        const val LbAgencies = "lb.agencies"
        const val LbDaily = "lb.daily"
        const val LbWeekly = "lb.weekly"
        const val LbMonthly = "lb.monthly"
        const val LbTotal = "lb.total"
        const val LbMembers = "lb.members"
        const val LbJoin = "lb.join"
        const val LbEmpty = "lb.empty"

        // Post gift sheet (gift button on a feed post → its creator)
        const val PostGiftTitle = "post.gift_title"
        const val PostGiftSend = "post.gift_send"
        const val PostGiftSent = "post.gift_sent"
        const val PostGiftNoCoins = "post.gift_no_coins"
        const val PostGiftFailed = "post.gift_failed"
        const val PostGiftEmpty = "post.gift_empty"
        const val PostShareBody = "post.share_body"

        // Leaderboard: top creators tab
        const val LbCreators = "lb.creators"
        const val LbViews = "lb.views"

        // Creator rewards (profile → Rewards → Creator)
        const val CreatorTabLive = "creator.tab_live"
        const val CreatorTabCreator = "creator.tab_creator"
        const val CreatorEarned = "creator.earned"
        const val CreatorGiftsLbl = "creator.gifts"
        const val CreatorBonusLbl = "creator.bonus"
        const val CreatorClaim = "creator.claim"
        const val CreatorClaimClosed = "creator.claim_closed"
        const val CreatorHow = "creator.how"
        const val CreatorEmpty = "creator.empty"
        const val CreatorOff = "creator.off"
        const val CreatorKindGift = "creator.kind_gift"
        const val CreatorKindBonus = "creator.kind_bonus"
        const val TxnBy = "txn.by"
        const val TxnWith = "txn.with"
        const val LiveSaySomething = "live.say_something"
        const val LiveLeaveTitle = "live.leave_title"
        const val LiveMinimize = "live.minimize"
        const val LiveMinimizeSub = "live.minimize_sub"
        const val LiveShare = "live.share"
        const val LiveShareSub = "live.share_sub"
        const val LiveShareLinkCopied = "live.share_link_copied"
        const val LiveShareLinkPending = "live.share_link_pending"
        const val LiveShareBody = "live.share_body"
        const val LiveShareBodyHost = "live.share_body_host"
        const val LiveClose = "live.close"
        const val LiveCloseSub = "live.close_sub"
        const val LiveCancelStay = "live.cancel_stay"
        const val LiveHostExitTitle = "live.host_exit_title"
        const val LiveHostMinimizeSub = "live.host_minimize_sub"
        const val LiveHostEnd = "live.host_end"
        const val LiveHostEndSub = "live.host_end_sub"
        const val LiveHostStay = "live.host_stay"
        const val LiveHostEnded = "live.host_ended"
        const val LiveConnectionLost = "live.connection_lost"
        const val LiveOk = "live.ok"
        const val LiveSummaryTitle = "live.summary_title"
        const val LiveSummaryLost = "live.summary_lost"
        const val LiveSummaryDuration = "live.summary_duration"
        const val LiveSummaryPeak = "live.summary_peak"
        const val LiveSummaryJoins = "live.summary_joins"
        const val LiveSummaryFailed = "live.summary_failed"
        const val LiveSummaryVerified = "live.summary_verified"
        const val LiveSummaryPaused = "live.summary_paused"
        const val LiveSummaryToday = "live.summary_today"
        const val LiveSummaryWall = "live.summary_wall"
        const val LiveTimeNotCounting = "live.time_not_counting"
        const val LiveTimeCountingNote = "live.time_counting_note"
        const val LiveChannel = "live.channel"
        const val LiveNotifHosting = "live.notif_hosting"
        const val LiveNotifOnStage = "live.notif_on_stage"
        const val LiveNotifCounted = "live.notif_counted"
        const val LiveNotifPaused = "live.notif_paused"
        const val MyLiveTitle = "mylive.title"
        const val MyLiveToday = "mylive.today"
        const val MyLiveLiveNow = "mylive.live_now"
        const val MyLiveCounting = "mylive.counting"
        const val MyLivePaused = "mylive.paused"
        const val MyLiveWeek = "mylive.week"
        const val MyLiveMonth = "mylive.month"
        const val MyLiveLifetime = "mylive.lifetime"
        const val MyLiveDays = "mylive.days"
        const val MyLiveDaysEmpty = "mylive.days_empty"
        const val MyLiveSessions = "mylive.sessions"
        const val MyLiveSessionsEmpty = "mylive.sessions_empty"
        const val MyLiveSessionsCount = "mylive.sessions_count"
        const val MyLiveCohost = "mylive.cohost"
        const val MyLiveAudio = "mylive.audio"
        const val MyLiveDropsOnce = "mylive.drops_once"
        const val MyLiveDrops = "mylive.drops"
        const val MyLivePausedFor = "mylive.paused_for"
        const val MyLiveDayNote = "mylive.day_note"
        const val MyLiveEndedBy = "mylive.ended_by"
        const val MyLiveReasonHost = "mylive.reason_host"
        const val MyLiveReasonLost = "mylive.reason_lost"
        const val MyLiveReasonAppClosed = "mylive.reason_app_closed"
        const val MyLiveReasonMax = "mylive.reason_max"
        const val MyLiveReasonStand = "mylive.reason_stand"
        const val MyLiveReasonOther = "mylive.reason_other"
        const val MyLiveLoadFailed = "mylive.load_failed"
        const val MyLiveOlder = "mylive.older"
        const val MyLiveRewards = "mylive.rewards"
        const val RewardsTitle = "rewards.title"
        const val RewardsToday = "rewards.today"
        const val RewardsWeek = "rewards.week"
        const val RewardsMonth = "rewards.month"
        const val RewardsAll = "rewards.all"
        const val RewardsLiveTime = "rewards.live_time"
        const val RewardsAdded = "rewards.added"
        const val RewardsPending = "rewards.pending"
        const val RewardsClaimable = "rewards.claimable"
        const val RewardsClaim = "rewards.claim"
        const val RewardsClaimed = "rewards.claimed"
        const val RewardsClaimWaiting = "rewards.claim_waiting"
        const val RewardsClaimOpens = "rewards.claim_opens"
        const val RewardsByDay = "rewards.by_day"
        const val RewardsHistory = "rewards.history"
        const val RewardsEmpty = "rewards.empty"
        const val RewardsEmptyRange = "rewards.empty_range"
        const val RewardsLoadFailed = "rewards.load_failed"
        const val RewardsOlder = "rewards.older"
        const val RewardsFilterAll = "rewards.filter_all"
        const val RewardsStatusPending = "rewards.status_pending"
        const val RewardsStatusAdded = "rewards.status_added"
        const val RewardsStatusClaimable = "rewards.status_claimable"
        const val RewardsStatusClaimed = "rewards.status_claimed"
        const val RewardsStatusExpired = "rewards.status_expired"
        const val RewardsStatusCancelled = "rewards.status_cancelled"
        const val RewardsCompletedAt = "rewards.completed_at"
        const val RewardsAddedAt = "rewards.added_at"
        const val RewardsBalance = "rewards.balance"
        const val RewardsRequired = "rewards.required"
        const val RewardsVerified = "rewards.verified"
        const val RewardsDay = "rewards.day"
        const val RewardsRole = "rewards.role"
        const val RewardsHost = "rewards.host"
        const val RewardsTimeline = "rewards.timeline"
        const val RewardsDetail = "rewards.detail"
        const val RewardsNoRewardsDay = "rewards.no_rewards_day"
        const val RewardsDayNote = "rewards.day_note"
        const val RewardsOff = "rewards.off"
        const val RewardsEligible = "rewards.eligible"
        const val RewardsEligibleNext = "rewards.eligible_next"
        const val RewardsEligibleDone = "rewards.eligible_done"
        const val RewardsNotEligible = "rewards.not_eligible"
        const val RewardsNotEligibleRole = "rewards.not_eligible_role"
        const val RewardsNotEligibleCondition = "rewards.not_eligible_condition"
        const val RewardsNotEligibleNeed = "rewards.not_eligible_need"
        const val RewardsNotEligibleLimit = "rewards.not_eligible_limit"
        const val RewardsNotEligibleNone = "rewards.not_eligible_none"
        const val RewardsNotEligibleTime = "rewards.not_eligible_time"
        const val RewardsNotEligibleFace = "rewards.not_eligible_face"
        const val RewardsNotEligibleUnlock = "rewards.not_eligible_unlock"
        const val RewardsNotEligibleOff = "rewards.not_eligible_off"
        const val RewardsHudFaceNeeded = "rewards.hud_face_needed"
        const val RewardsHudFaceNotAdded = "rewards.hud_face_not_added"
        const val RewardsHudNext = "rewards.hud_next"
        const val RewardsHudEarned = "rewards.hud_earned"
        const val RewardsToastAdded = "rewards.toast_added"
        const val RewardsToastClaimable = "rewards.toast_claimable"
        const val RewardsToastPending = "rewards.toast_pending"
        const val RewardsCampaigns = "rewards.campaigns"
        const val RewardsCampaignEligible = "rewards.campaign_eligible"
        const val RewardsCampaignProgress = "rewards.campaign_progress"
        const val RewardsQualifyingPolicy = "rewards.qualifying_policy"
        const val RewardsQualifyingReward = "rewards.qualifying_reward"
        const val RewardsLevels = "rewards.levels"
        const val RewardsConditions = "rewards.conditions"
        const val RewardsPendingWhy = "rewards.pending_why"
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
        const val LiveGuestCamOn = "live.guest_cam_on"
        const val LiveGuestCamOff = "live.guest_cam_off"
        const val LiveMirrorOn = "live.mirror_on"
        const val LiveMirrorOff = "live.mirror_off"
        const val LiveSentLike = "live.sent_like"
        const val LiveEnteredRoom = "live.entered_room"
        const val LiveLeftRoom = "live.left_room"
        const val LiveGameWin = "live.game_win"
        const val LiveGameWinGame = "live.game_win_game"
        const val ProfileSending = "profile.sending"
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
        const val LiveSeatTitle = "live.seat_title"
        const val LiveSeatLock = "live.seat_lock"
        const val LiveSeatUnlock = "live.seat_unlock"
        const val LiveSeatsLockAll = "live.seats_lock_all"
        const val LiveSeatsUnlockAll = "live.seats_unlock_all"
        const val LiveSeatTake = "live.seat_take"
        const val LiveSeatLocked = "live.seat_locked"
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
        const val LiveBlockLive = "live.block_live"
        const val LiveBlockedTitle = "live.blocked_title"
        const val LiveBlockedNone = "live.blocked_none"
        const val LiveBlockedTag = "live.blocked_tag"
        const val LiveRemovedTag = "live.removed_tag"
        const val LiveBanOver = "live.ban_over"
        const val LiveUnblock = "live.unblock"
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
        const val PkHostLeft = "pk.host_left"
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
        const val CreateLivePreview = "create.live_preview"
        const val CreateLiveCamAsk = "create.live_cam_ask"
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
        const val CreateSave = "create.save"
        const val CreateSavedToGallery = "create.saved_to_gallery"
        const val CreateSaveFailed = "create.save_failed"
        const val SnapTermsTitle = "snap.terms_title"
        const val SnapTermsAccept = "snap.terms_accept"
        const val SnapTermsDecline = "snap.terms_decline"
        const val CreateEndLive = "create.end_live"
        const val CreateFlashOn = "create.flash_on"
        const val CreateFlashOff = "create.flash_off"
        const val CreateTimer = "create.timer"
        const val CreateGrid = "create.grid"
        const val CreateSpeed = "create.speed"
        const val CreateBeauty = "create.beauty"
        const val CreateLooks = "create.looks"
        const val CreateBackground = "create.background"
        const val CreateMasks = "create.masks"
        const val CreateBeautyAuto = "create.beauty_auto"
        const val CreateBeautyGlam = "create.beauty_glam"
        const val CreateBeautyBrightness = "create.beauty_brightness"
        const val CreateBeautyLightening = "create.beauty_lightening"
        const val CreateBeautyFaceSmooth = "create.beauty_face_smooth"
        const val CreateBeautyRedness = "create.beauty_redness"
        const val CreateBeautyClarity = "create.beauty_clarity"
        const val CreateBeautyPinkness = "create.beauty_pinkness"
        const val CreateBeautyContrast = "create.beauty_contrast"
        const val CreateBeautyLipPink = "create.beauty_lip_pink"
        const val CreateEffects = "create.effects"
        const val CreateLensOff = "create.lens_off"
        const val CreateBgBlur = "create.bg_blur"
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
        const val ProfileUserId = "profile.user_id"
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
        const val DeleteAccountTitle = "delete_account.title"
        const val DeleteAccountHeading = "delete_account.heading"
        const val DeleteAccountBody = "delete_account.body"
        const val DeleteAccountRecoverNote = "delete_account.recover_note"
        const val DeleteAccountConfirmLabel = "delete_account.confirm_label"
        const val DeleteAccountCta = "delete_account.cta"
        const val DeleteAccountCancel = "delete_account.cancel"
        const val DeleteAccountDone = "delete_account.done"
        const val ProfileTopSenders = "profile.top_senders"
        const val ProfileTopRecipients = "profile.top_recipients"
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
        const val ModTitle = "mod.title"
        const val ModSearchHint = "mod.search_hint"
        const val ModSearchEmpty = "mod.search_empty"
        const val ModCapture = "mod.capture"
        const val ModCaptureOn = "mod.capture_on"
        const val ModCaptureOff = "mod.capture_off"
        const val ModCaptureAllow = "mod.capture_allow"
        const val ModCaptureRevoke = "mod.capture_revoke"
        const val ModCaptureInherit = "mod.capture_inherit"
        const val ModMic = "mod.mic"
        const val ModMicOn = "mod.mic_on"
        const val ModMicOff = "mod.mic_off"
        const val ModMicDisable = "mod.mic_disable"
        const val ModMicEnable = "mod.mic_enable"
        const val ModWarn = "mod.warn"
        const val ModWarnSend = "mod.warn_send"
        const val ModWarnSent = "mod.warn_sent"
        const val ModReasonHint = "mod.reason_hint"
        const val ModReasonRequired = "mod.reason_required"
        const val ModBlock = "mod.block"
        const val ModBlocked = "mod.blocked"
        const val ModUnblock = "mod.unblock"
        const val ModSaved = "mod.saved"
        const val FindFriendsTitle = "ff.title"
        const val FindFriendsSub = "ff.sub"
        const val FindFriendsEnable = "ff.enable"
        const val FindFriendsEnableHint = "ff.enable_hint"
        const val FindFriendsSyncing = "ff.syncing"
        const val FindFriendsPermission = "ff.permission"
        const val FindFriendsOpenSettings = "ff.open_settings"
        const val FindFriendsResults = "ff.results"
        const val FindFriendsEmpty = "ff.empty"
        const val FindFriendsPrivacy = "ff.privacy"
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
        const val RolesApplyingAs = "roles.applying_as"
        const val RolesNotePlaceholder = "roles.note_placeholder"
        const val RolesApply = "roles.apply"
        const val RolesApplied = "roles.applied"
        const val RolesMyCodes = "roles.my_codes"
        const val RolesCodeOpens = "roles.code_opens"
        const val RolesCopied = "roles.copied"
        const val RolesCopyCode = "roles.copy_code"
        const val RolesShareCode = "roles.share_code"
        const val RolesShareLink = "roles.share_link"
        const val RolesShareText = "roles.share_text"
        const val RolesShareTextRole = "roles.share_text_role"
        const val RolesShareTextRoleCode = "roles.share_text_role_code"
        const val RolesSharePickRole = "roles.share_pick_role"
        const val RolesShareNoRoles = "roles.share_no_roles"
        const val RolesShareNoWeb = "roles.share_no_web"
        const val RolesLinkCopied = "roles.link_copied"
        const val RolesFinance = "roles.finance"
        const val RolesGiftsReceived = "roles.gifts_received"
        const val RolesGiftsSent = "roles.gifts_sent"
        const val RolesReceivedLine = "roles.received_line"
        const val RolesSentLine = "roles.sent_line"
        const val RolesRecharges = "roles.recharges"
        const val RolesRechargeLine = "roles.recharge_line"
        const val RolesWithdrawals = "roles.withdrawals"
        const val RolesNoWithdrawals = "roles.no_withdrawals"
        const val RolesWdAmount = "roles.wd_amount"
        const val RolesWdPending = "roles.wd_pending"
        const val RolesWdApproved = "roles.wd_approved"
        const val RolesWdRejected = "roles.wd_rejected"
        const val RolesUpline = "roles.upline"
        const val RolesLeaveUpline = "roles.leave_upline"
        const val RolesLeaveUplineHint = "roles.leave_upline_hint"
        const val RolesLeaveRole = "roles.leave_role"
        const val RolesLeaveRoleHint = "roles.leave_role_hint"
        const val RolesLeftRole = "roles.left_role"
        const val RolesShiftUpline = "roles.shift_upline"
        const val RolesShiftUplineHint = "roles.shift_upline_hint"
        const val RolesShiftCodePlaceholder = "roles.shift_code_placeholder"
        const val RolesShiftCodeRequired = "roles.shift_code_required"
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
        // Face verification capture + outcomes.
        const val FaceTitle = "face.title"
        const val FaceIntro = "face.intro"
        const val FaceHintCenter = "face.hint_center"
        const val FaceHintCloser = "face.hint_closer"
        const val FaceHintBack = "face.hint_back"
        const val FaceHintLight = "face.hint_light"
        const val FaceHintStill = "face.hint_still"
        const val FaceHintOne = "face.hint_one"
        const val FaceHintFront = "face.hint_front"
        const val FaceTurnLeft = "face.turn_left"
        const val FaceTurnRight = "face.turn_right"
        const val FaceCapture = "face.capture"
        const val FaceChecking = "face.checking"
        const val FaceOkVerified = "face.ok_verified"
        const val FacePendingMsg = "face.pending_msg"
        const val FaceReviewMsg = "face.review_msg"
        const val FaceDuplicateMsg = "face.duplicate_msg"
        // Status screen's friendlier wording for the duplicate state: explains
        // the per-face account limit and that re-verifying re-checks it.
        const val FaceDuplicateRetryHint = "face.duplicate_retry_hint"
        const val FaceRecheckCta = "face.recheck_cta"
        // Dynamic duplicate wording when the server sent the live numbers:
        // {limit} allowed per face, {used} taken, {remaining} free.
        const val FaceDuplicateLimitHint = "face.duplicate_limit_hint"
        const val FaceDuplicateFullHint = "face.duplicate_full_hint"
        const val FaceStatusCanRetry = "face.status_can_retry"
        const val FaceMismatchMsg = "face.mismatch_msg"
        const val FaceSpoofMsg = "face.spoof_msg"
        const val FaceQualityMsg = "face.quality_msg"
        const val FaceChallengeFailMsg = "face.challenge_fail_msg"
        const val FaceErrorMsg = "face.error_msg"
        const val FaceRequiredMsg = "face.required_msg"
        const val FaceRetry = "face.retry"
        const val FaceClose = "face.close"
        const val FaceVerifyCta = "face.verify_cta"
        const val FaceGateHint = "face.gate_hint"
        const val FacePermMsg = "face.perm_msg"
        const val FacePermGrant = "face.perm_grant"
        const val FacePermSettings = "face.perm_settings"
        // Account-level face verification (hamburger menu), independent of roles.
        const val FaceStatusNone = "face.status_none"
        const val FaceStatusPending = "face.status_pending"
        const val FaceStatusReview = "face.status_review"
        const val FaceStatusDuplicate = "face.status_duplicate"
        const val FaceStatusRejected = "face.status_rejected"
        const val FaceStatusOff = "face.status_off"
        const val FaceStandaloneIntro = "face.standalone_intro"
        const val FaceVerifiedHint = "face.verified_hint"
        const val FaceRejectedHint = "face.rejected_hint"
        const val FaceOffHint = "face.off_hint"
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
        const val CallNotifyJoin = "call.notify_join"
        const val CallNotifyEnd = "call.notify_end"
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

        // Paid chat & calls
        const val PaidTitle = "paid.title"
        const val PaidLoadFailed = "paid.load_failed"
        const val PaidTabOverview = "paid.tab.overview"
        const val PaidTabMyRates = "paid.tab.my_rates"
        const val PaidTabHistory = "paid.tab.history"
        const val PaidRangeToday = "paid.range.today"
        const val PaidRangeWeek = "paid.range.week"
        const val PaidRangeMonth = "paid.range.month"
        const val PaidRangeAll = "paid.range.all"
        const val PaidOff = "paid.off"
        const val PaidFreeRole = "paid.free_role"
        const val PaidFreeForYou = "paid.free_for_you"
        const val PaidYouPay = "paid.you_pay"
        const val PaidYouEarn = "paid.you_earn"
        const val PaidChatUnlocked = "paid.chat_unlocked"
        const val PaidPromoLeft = "paid.promo_left"
        const val PaidPromoDay = "paid.promo.day"
        const val PaidPromoWeek = "paid.promo.week"
        const val PaidPromoMonth = "paid.promo.month"
        const val PaidPromoTag = "paid.promo_tag"
        const val PaidPromoUsed = "paid.promo_used"
        const val PaidUnitMessage = "paid.unit.message"
        const val PaidUnitConversation = "paid.unit.conversation"
        const val PaidUnitCall = "paid.unit.call"
        const val PaidUnitMinute = "paid.unit.minute"
        const val PaidClaimable = "paid.claimable"
        const val PaidClaimableCount = "paid.claimable_count"
        const val PaidClaimableShort = "paid.claimable_short"
        const val PaidClaimExpiry = "paid.claim_expiry"
        const val PaidClaimBtn = "paid.claim_btn"
        const val PaidClaimed = "paid.claimed"
        const val PaidEarned = "paid.earned"
        const val PaidClaimedTotal = "paid.claimed_total"
        const val PaidDirectTotal = "paid.direct_total"
        const val PaidExpiredTotal = "paid.expired_total"
        const val PaidSpentToday = "paid.spent_today"
        const val PaidSpentAll = "paid.spent_all"
        const val PaidPolicy = "paid.policy"
        const val PaidOwnRatesLine = "paid.own_rates_line"
        const val PaidNoRules = "paid.no_rules"
        const val PaidFeeLine = "paid.fee_line"
        const val PaidPayoutClaim = "paid.payout_claim"
        const val PaidPayoutDirect = "paid.payout_direct"
        const val PaidRulePay = "paid.rule_pay"
        const val PaidRuleEarn = "paid.rule_earn"
        const val PaidRuleBoth = "paid.rule_both"
        const val PaidAnyone = "paid.anyone"
        const val PaidReceivedFrom = "paid.received_from"
        const val PaidFromOn = "paid.from_on"
        const val PaidUnits = "paid.units"
        const val PaidEmpty = "paid.empty"
        const val PaidLoadMore = "paid.load_more"
        const val PaidChargeMessage = "paid.charge.message"
        const val PaidChargeUnlock = "paid.charge.unlock"
        const val PaidChargeAudio = "paid.charge.audio"
        const val PaidChargeAudioMin = "paid.charge.audio_min"
        const val PaidChargeVideo = "paid.charge.video"
        const val PaidChargeVideoMin = "paid.charge.video_min"
        const val PaidStatusPaid = "paid.status.paid"
        const val PaidStatusClaimable = "paid.status.claimable"
        const val PaidStatusClaimed = "paid.status.claimed"
        const val PaidStatusExpired = "paid.status.expired"
        const val PaidBeforeAfter = "paid.before_after"
        const val PaidFree = "paid.free"
        const val PaidSideEarned = "paid.side.earned"
        const val PaidSidePaid = "paid.side.paid"
        const val PaidMyRatesHint = "paid.my_rates_hint"
        const val PaidRateMax = "paid.rate_max"
        const val PaidChannelMessage = "paid.channel.message"
        const val PaidChannelAudio = "paid.channel.audio"
        const val PaidChannelVideo = "paid.channel.video"
        const val PaidRatesSave = "paid.rates_save"
        const val PaidRatesClear = "paid.rates_clear"
        const val PaidRatesSaved = "paid.rates_saved"
        const val PaidRatesCleared = "paid.rates_cleared"
        const val CallNoFundsMine = "call.no_funds_mine"
        const val CallNoFundsTheirs = "call.no_funds_theirs"
        const val CallPaidSoFar = "call.paid_so_far"
        const val CallPromoFree = "call.promo_free"
        const val CallEarnedSoFar = "call.earned_so_far"
        const val TxnCatPaidContactPaid = "txn.cat.paid_contact_paid"
        const val TxnCatPaidContactEarned = "txn.cat.paid_contact_earned"
    }
}

@Composable
fun rememberCopy(): Copy {
    val lang by AppLang.current.collectAsState()
    return remember(lang) { Copy(lang) }
}

/**
 * One map per language. A missing key stays English, so a new language can
 * ship before every screen is translated. Add the code in [AppLang] and a
 * branch here; nothing is loaded over the network.
 */
private fun table(lang: String): Map<String, String> = when (lang) {
    AppLang.Arabic -> Ar
    AppLang.Bangla -> Bn
    AppLang.Filipino -> Fil
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
    Copy.LivePartyBadge to "PARTY",
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
    Copy.StoreFree to "Free",
    Copy.StoreFreeSub to "Welcome packs, role packs and gifts from the team",
    Copy.StoreTabAll to "All {name}",
    Copy.StoreTabMine to "My {name}",
    Copy.StoreNew to "New",
    Copy.StoreOwned to "Owned",
    Copy.StoreWearing to "Wearing now",
    Copy.StoreParked to "Owned · not active",
    Copy.StoreLapsed to "Expired",
    Copy.StoreMineEmpty to "You don't have any {name} yet.",
    Copy.StoreShopEmpty to "Nothing in {name} right now.",
    Copy.StoreFreeEmpty to "Nothing free yet. Welcome packs land here.",
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
    Copy.LiveGiftNotEnoughMultiBody to "{gift} for {n} people needs {required} coins and you have {coins}. Recharge or pick fewer people.",
    Copy.LiveGiftRecipients to "{n} people",
    Copy.LiveGiftToOthers to "{to} +{n}",
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
    Copy.LiveGiftLocked to "Locked",
    Copy.LiveGiftLockedTitle to "Not available right now",
    Copy.GiftingTabProfile to "Profile",
    Copy.GiftingTabGifting to "Gifting",
    Copy.GiftingThisLive to "This LIVE",
    Copy.GiftingToday to "Today",
    Copy.GiftingWeek to "This week",
    Copy.GiftingMonth to "This month",
    Copy.GiftingDates to "Dates",
    Copy.GiftingTopSenders to "Top senders",
    Copy.GiftingTopReceivers to "Top receivers",
    Copy.GiftingLatest to "Latest",
    Copy.GiftingReceived to "Received",
    Copy.GiftingSent to "Sent",
    Copy.GiftingDiamonds to "diamonds",
    Copy.GiftingCoins to "coins",
    Copy.GiftingGiftsN to "{n} gifts",
    Copy.GiftingEmpty to "No gifts in this period yet.",
    Copy.GiftingPrivate to "This person's gifting is private.",
    Copy.GiftingLoadFailed to "Could not load gifting.",
    Copy.GiftingRetry to "Try again",
    Copy.GiftingShow to "Show",
    Copy.GiftingLive to "LIVE",
    Copy.GiftingCashback to "+{n} back",
    Copy.GiftingLuckyWon to "You won {n} coins back!",
    Copy.LiveStickers to "Stickers",
    Copy.LiveStickerTo to "To",
    Copy.LiveStickerSentLine to "sent {to} a sticker",
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
    Copy.ProfileSendingEmpty to "No gifts sent in this period",
    Copy.TxnTitle to "Transaction history",
    Copy.TxnSpent to "Spent",
    Copy.TxnEarned to "Received",
    Copy.TxnCoins to "Coins",
    Copy.TxnDiamonds to "Diamonds",
    Copy.TxnCatGiftsSent to "Gifts sent",
    Copy.TxnCatStore to "Store purchases",
    Copy.TxnCatGameBets to "Game bets",
    Copy.TxnCatGiftsReceived to "Gifts received",
    Copy.TxnCatTopups to "Recharges",
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
    Copy.BalanceTitle to "My balance",
    Copy.BalanceCoins to "Coins",
    Copy.BalanceDiamonds to "Diamonds",
    Copy.BalanceBeans to "Beans",
    Copy.BalanceRecharge to "Recharge",
    Copy.BalanceWithdraw to "Withdraw",
    Copy.BalanceHistory to "History",
    Copy.BalancePending to "{n} recharge waiting for approval",
    Copy.BalanceRecent to "Recent recharges",
    Copy.RechargeTitle to "Recharge coins",
    Copy.RechargeTabPackages to "Packages",
    Copy.RechargeTabRequests to "My requests",
    Copy.RechargePickPackage to "Choose how many coins to buy",
    Copy.RechargeNoPackages to "No packages are available in your country yet.",
    Copy.RechargePickMethod to "Choose where to send the payment",
    Copy.RechargeNoMethods to "No payment methods are available in your country yet.",
    Copy.RechargeBonus to "+{n} bonus",
    Copy.RechargeSendExactly to "Send exactly",
    Copy.RechargeAccountTitle to "Account title",
    Copy.RechargeAccountNo to "Account number",
    Copy.RechargeCopy to "Copy",
    Copy.RechargeCopied to "Copied",
    Copy.RechargeInstructions to "Instructions",
    Copy.RechargeProof to "Payment screenshot",
    Copy.RechargeProofHint to "After paying, attach the receipt or confirmation screenshot so we can verify it.",
    Copy.RechargeAttach to "Attach screenshot",
    Copy.RechargeChange to "Change",
    Copy.RechargeNote to "Message (optional)",
    Copy.RechargeNoteHint to "Transaction ID, sender name, anything that helps us match your payment",
    Copy.RechargeSubmit to "Submit for approval",
    Copy.RechargeNeedProof to "Attach your payment screenshot first.",
    Copy.RechargeDone to "Submitted. Your coins will be added once the payment is confirmed.",
    Copy.RechargeNoRequests to "No recharge requests yet.",
    Copy.RechargeCredited to "{n} coins credited",
    Copy.RechargeYouGet to "You get",
    Copy.RechargeStep to "Step {n} of 3",
    Copy.SellerHubTitle to "Coin seller",
    Copy.SellerDrawerHub to "Coin seller",
    Copy.SellerDrawerList to "Buy from a seller",
    Copy.TransferTitle to "Transfer",
    Copy.TransferDrawer to "Transfer",
    Copy.TransferTabSend to "Send",
    Copy.TransferTabHistory to "History",
    Copy.TransferTabDisputes to "Disputes",
    Copy.TransferFindUser to "Send to",
    Copy.TransferFindHint to "User id or username",
    Copy.TransferAmount to "Amount",
    Copy.TransferAmountHint to "How much to send",
    Copy.TransferLimits to "Min {min} · Max {max}",
    Copy.TransferFeeLine to "Fee: {n}",
    Copy.TransferTheyGet to "{name} gets {n} {cur}",
    Copy.TransferNote to "Note (optional)",
    Copy.TransferSendBtn to "Send",
    Copy.TransferSentOk to "Sent.",
    Copy.TransferWindowLine to "Used {used} of {max} per {d} day(s)",
    Copy.TransferRecvRemaining to "They can still receive {n}",
    Copy.TransferCantReceive to "This account cannot receive transfers from you.",
    Copy.TransferDisabled to "Transfers are not available for your account.",
    Copy.TransferNoHistory to "No transfers yet.",
    Copy.TransferDirSent to "Sent",
    Copy.TransferDirReceived to "Received",
    Copy.TransferDisputeBtn to "Report a problem",
    Copy.TransferDisputeReason to "What went wrong? e.g. wrong deduction",
    Copy.TransferDisputeOpened to "Dispute opened.",
    Copy.TransferNoDisputes to "No disputes.",
    Copy.TransferDisputeTitle to "Dispute",
    Copy.ExchangeTitle to "Exchange",
    Copy.ExchangeDrawer to "Exchange",
    Copy.ExchangeTabConvert to "Convert",
    Copy.ExchangeTabHistory to "History",
    Copy.ExchangePair to "Convert",
    Copy.ExchangeAmount to "Amount",
    Copy.ExchangeAmountHint to "How much to convert",
    Copy.ExchangeRateLine to "Rate: {r}×",
    Copy.ExchangeFeeLine to "Fee: {n} ({p}%)",
    Copy.ExchangeYouGet to "You get {n} {cur}",
    Copy.ExchangeBtn to "Exchange",
    Copy.ExchangeDone to "Exchanged.",
    Copy.ExchangeDisabled to "Exchange is not available for your account.",
    Copy.ExchangeNoHistory to "No exchanges yet.",
    Copy.ExchangeLimits to "Min {min} · Max {max}",
    Copy.ExchangeWindowLine to "Used {used} of {max} per {d} day(s)",
    Copy.ExchangeTotal to "Total exchanged",
    Copy.ExchangeFeesPaid to "Fees paid",
    Copy.LbTitle to "Leaderboard",
    Copy.LbSenders to "Top senders",
    Copy.LbHosts to "Top hosts",
    Copy.LbAgencies to "Top agencies",
    Copy.LbDaily to "Daily",
    Copy.LbWeekly to "Weekly",
    Copy.LbMonthly to "Monthly",
    Copy.LbTotal to "All time",
    Copy.LbMembers to "{n} members",
    Copy.LbJoin to "Join",
    Copy.LbEmpty to "No rankings yet.",
    Copy.PostGiftTitle to "Send a gift to {name}",
    Copy.PostGiftSend to "Send",
    Copy.PostGiftSent to "Sent {gift} ✓",
    Copy.PostGiftNoCoins to "Not enough coins",
    Copy.PostGiftFailed to "Couldn't send the gift",
    Copy.PostGiftEmpty to "No gifts available right now.",
    Copy.PostShareBody to "Watch {name} on {app}",
    Copy.LbCreators to "Top creators",
    Copy.LbViews to "{n} views",
    Copy.CreatorTabLive to "Live",
    Copy.CreatorTabCreator to "Creator",
    Copy.CreatorEarned to "Creator earnings",
    Copy.CreatorGiftsLbl to "Gifts",
    Copy.CreatorBonusLbl to "Bonus",
    Copy.CreatorClaim to "Claim",
    Copy.CreatorClaimClosed to "Claiming is closed right now.",
    Copy.CreatorHow to "How it works",
    Copy.CreatorEmpty to "No creator rewards yet.",
    Copy.CreatorOff to "Creator rewards are not available right now.",
    Copy.CreatorKindGift to "Gift credit",
    Copy.CreatorKindBonus to "Milestone bonus",
    Copy.SellerTabOverview to "Overview",
    Copy.SellerTabSend to "Send",
    Copy.SellerTabRequests to "Requests",
    Copy.SellerTabHistory to "History",
    Copy.SellerTabTeam to "Team",
    Copy.SellerBalance to "Your balance",
    Copy.SellerSentToday to "Today",
    Copy.SellerSentWeek to "This week",
    Copy.SellerSentMonth to "This month",
    Copy.SellerSentTotal to "All time",
    Copy.SellerToppedUp to "Received from platform",
    Copy.SellerInactive to "Your seller account is inactive. Contact support for details.",
    Copy.SellerNoSendPerm to "Sending is not enabled for your account yet.",
    Copy.SellerFindUser to "Find the receiver",
    Copy.SellerFindHint to "User id or username",
    Copy.SellerAmount to "Amount",
    Copy.SellerAmountHint to "How much to send",
    Copy.SellerNote to "Note (optional)",
    Copy.SellerSendBtn to "Send {n}",
    Copy.SellerSent to "Sent. New balance: {n}",
    Copy.SellerTheyGet to "They receive {n} {cur}",
    Copy.SellerLimits to "Per send: {min} – {max}",
    Copy.SellerNoTransfers to "No sends yet.",
    Copy.SellerNoTeam to "No team members yet.",
    Copy.SellerNoRequests to "No requests yet.",
    Copy.SellerApprove to "Approve & send",
    Copy.SellerReject to "Decline",
    Copy.SellerViewProof to "View payment screenshot",
    Copy.SellerListTitle to "Coin sellers",
    Copy.SellerListEmpty to "No sellers are available right now.",
    Copy.SellerListOff to "Coin sellers are not enabled.",
    Copy.SellerTabSellers to "Sellers",
    Copy.SellerTabMyRequests to "My requests",
    Copy.SellerTabDisputes to "Disputes",
    Copy.SellerRequestBtn to "Request recharge",
    Copy.SellerRequestTitle to "Request from @{name}",
    Copy.SellerRequestDone to "Request sent. The seller will review it.",
    Copy.SellerContactWhatsapp to "WhatsApp",
    Copy.SellerAccountNo to "Account",
    Copy.SellerSends to "{n} sends",
    Copy.SellerProofOptional to "Payment screenshot (optional)",
    Copy.SellerDispute to "Report a problem",
    Copy.SellerDisputeReason to "Describe the problem",
    Copy.SellerDisputeOpenBtn to "Open dispute",
    Copy.SellerDisputeEmpty to "No disputes.",
    Copy.SellerDisputeClosed to "This dispute is closed.",
    Copy.SellerDisputeReply to "Write a message…",
    Copy.SellerDisputeSystem to "System",
    Copy.SellerDisputeAgainst to "Against @{name}",
    Copy.SellerStatusPending to "Pending",
    Copy.SellerStatusCompleted to "Completed",
    Copy.SellerStatusDisputed to "Disputed",
    Copy.SellerStatusFailed to "Failed",
    Copy.SellerStatusApproved to "Approved",
    Copy.SellerStatusRejected to "Rejected",
    Copy.SellerStatusOpen to "Open",
    Copy.SellerStatusDismissed to "Dismissed",
    Copy.SellerStatusResolved to "Resolved",
    Copy.TxnBy to "by {name}",
    Copy.TxnWith to "with {name}",
    Copy.LiveSaySomething to "Say something…",
    Copy.LiveLeaveTitle to "Leave this broadcast?",
    Copy.LiveMinimize to "Minimize",
    Copy.LiveMinimizeSub to "Keep watching in mini player",
    Copy.LiveShare to "Share",
    Copy.LiveShareSub to "Copy or send the live link",
    Copy.LiveShareLinkCopied to "Live link copied",
    Copy.LiveShareLinkPending to "Link isn’t ready yet. Try again in a moment.",
    Copy.LiveShareBody to "Watch me live on {app}",
    Copy.LiveShareBodyHost to "Watch {name} live on {app}",
    Copy.LiveClose to "Close",
    Copy.LiveCloseSub to "Leave this stream",
    Copy.LiveCancelStay to "Cancel, stay in stream",
    Copy.LiveHostExitTitle to "End your LIVE?",
    Copy.LiveHostMinimizeSub to "Stay live while you use the app",
    Copy.LiveHostEnd to "End LIVE",
    Copy.LiveHostEndSub to "Close the room for everyone",
    Copy.LiveHostStay to "Cancel, keep broadcasting",
    Copy.LiveHostEnded to "The host has ended this live",
    Copy.LiveConnectionLost to "Your connection dropped and the live could not be rejoined",
    Copy.LiveOk to "OK",
    Copy.LiveSummaryTitle to "Your live has ended",
    Copy.LiveSummaryLost to "Your connection dropped for too long, so the live was ended for everyone.",
    Copy.LiveSummaryDuration to "Duration",
    Copy.LiveSummaryPeak to "Peak viewers",
    Copy.LiveSummaryJoins to "Joins",
    Copy.LiveSummaryFailed to "Couldn't start the live. Check your connection and try again.",
    Copy.LiveSummaryVerified to "Time counted",
    Copy.LiveSummaryPaused to "{t} not counted while you were offline",
    Copy.LiveSummaryToday to "Today so far: {t}",
    Copy.LiveSummaryWall to "On air {t}",
    Copy.LiveTimeNotCounting to "Not counting — reconnecting",
    Copy.LiveTimeCountingNote to "counted",
    Copy.LiveChannel to "Live",
    Copy.LiveNotifHosting to "You're live",
    Copy.LiveNotifOnStage to "You're on stage",
    Copy.LiveNotifCounted to "{t} counted",
    Copy.LiveNotifPaused to "Time paused — reconnecting",
    Copy.MyLiveTitle to "My Live",
    Copy.MyLiveToday to "Today",
    Copy.MyLiveLiveNow to "Live now",
    Copy.MyLiveCounting to "Counting",
    Copy.MyLivePaused to "Paused — reconnecting",
    Copy.MyLiveWeek to "This week",
    Copy.MyLiveMonth to "This month",
    Copy.MyLiveLifetime to "All time",
    Copy.MyLiveDays to "By day",
    Copy.MyLiveDaysEmpty to "No live time in this period yet.",
    Copy.MyLiveSessions to "Sessions",
    Copy.MyLiveSessionsEmpty to "You did not go live this day.",
    Copy.MyLiveSessionsCount to "{n} sessions",
    Copy.MyLiveCohost to "Co-host",
    Copy.MyLiveAudio to "Audio",
    Copy.MyLiveDropsOnce to "1 reconnect",
    Copy.MyLiveDrops to "{n} reconnects",
    Copy.MyLivePausedFor to "{t} offline, not counted",
    Copy.MyLiveDayNote to "Days run from {start} in {tz}. Only time your phone was connected is counted; the record is the same one support sees.",
    Copy.MyLiveEndedBy to "Ended: {r}",
    Copy.MyLiveReasonHost to "you ended it",
    Copy.MyLiveReasonLost to "connection lost",
    Copy.MyLiveReasonAppClosed to "app closed",
    Copy.MyLiveReasonMax to "maximum length",
    Copy.MyLiveReasonStand to "left the stage",
    Copy.MyLiveReasonOther to "{r}",
    Copy.MyLiveLoadFailed to "Couldn't load your live time.",
    Copy.MyLiveOlder to "Earlier days",
    Copy.MyLiveRewards to "Rewards",
    Copy.RewardsTitle to "My Rewards",
    Copy.RewardsToday to "Today",
    Copy.RewardsWeek to "Week",
    Copy.RewardsMonth to "Month",
    Copy.RewardsAll to "All",
    Copy.RewardsLiveTime to "Live time",
    Copy.RewardsAdded to "Added to balance",
    Copy.RewardsPending to "Pending",
    Copy.RewardsClaimable to "Ready to claim",
    Copy.RewardsClaim to "Claim {amt}",
    Copy.RewardsClaimed to "{amt} added to your balance",
    Copy.RewardsClaimWaiting to "{amt} waiting for its claim window",
    Copy.RewardsClaimOpens to "opens {t}",
    Copy.RewardsByDay to "By day",
    Copy.RewardsHistory to "History",
    Copy.RewardsEmpty to "No rewards yet. Go live and your time on air earns them.",
    Copy.RewardsEmptyRange to "No rewards in this period.",
    Copy.RewardsLoadFailed to "Couldn't load your rewards.",
    Copy.RewardsOlder to "Earlier rewards",
    Copy.RewardsFilterAll to "All",
    Copy.RewardsStatusPending to "Pending",
    Copy.RewardsStatusAdded to "Added",
    Copy.RewardsStatusClaimable to "Claimable",
    Copy.RewardsStatusClaimed to "Claimed",
    Copy.RewardsStatusExpired to "Expired",
    Copy.RewardsStatusCancelled to "Cancelled",
    Copy.RewardsCompletedAt to "Completed at",
    Copy.RewardsAddedAt to "Added at",
    Copy.RewardsBalance to "Balance",
    Copy.RewardsRequired to "Required",
    Copy.RewardsVerified to "Verified",
    Copy.RewardsDay to "Reward day",
    Copy.RewardsRole to "As",
    Copy.RewardsHost to "Host",
    Copy.RewardsTimeline to "Timeline",
    Copy.RewardsDetail to "Reward",
    Copy.RewardsNoRewardsDay to "Live time, no reward reached",
    Copy.RewardsDayNote to "Reward days run from {start} in {tz}, the same calendar as your live time. Times shown are in {tz}.",
    Copy.RewardsOff to "Live rewards are paused right now.",
    Copy.RewardsEligible to "Eligible for rewards",
    Copy.RewardsEligibleNext to "{amt} after {t} live",
    Copy.RewardsEligibleDone to "Today's rewards all earned",
    Copy.RewardsNotEligible to "Not eligible for rewards",
    Copy.RewardsNotEligibleRole to "Your role isn't included in this campaign",
    Copy.RewardsNotEligibleCondition to "A campaign condition isn't met yet",
    Copy.RewardsNotEligibleNeed to "Need {which}",
    Copy.RewardsNotEligibleLimit to "Reward limit reached for this period",
    Copy.RewardsNotEligibleNone to "No reward campaign is running",
    Copy.RewardsNotEligibleTime to "{t} more live time",
    Copy.RewardsNotEligibleFace to "Face not verified — verify your face to receive rewards",
    Copy.RewardsNotEligibleUnlock to "No level unlocked yet — receive more diamonds in gifts to unlock an hourly rate",
    Copy.RewardsNotEligibleOff to "This reward is switched off right now",
    Copy.RewardsHudFaceNeeded to "Face not verified · rewards won't be added",
    Copy.RewardsHudFaceNotAdded to "Face not verified · {amt} not added",
    Copy.RewardsHudNext to "{amt} in {t}",
    Copy.RewardsHudEarned to "{amt} earned",
    Copy.RewardsToastAdded to "+{amt} added · {b} → {a}",
    Copy.RewardsToastClaimable to "+{amt} ready to claim",
    Copy.RewardsToastPending to "{amt} reached · checking",
    Copy.RewardsCampaigns to "Live reward campaigns",
    Copy.RewardsCampaignEligible to "Eligible",
    Copy.RewardsCampaignProgress to "In progress",
    Copy.RewardsQualifyingPolicy to "Go live {target} a day · {need} valid days this cycle",
    Copy.RewardsQualifyingReward to "{amt} once all valid days are met",
    Copy.RewardsLevels to "Levels",
    Copy.RewardsConditions to "Conditions",
    Copy.RewardsPendingWhy to "Reached {t}, waiting: {r}",
    Copy.LiveNoneLive to "Nobody is live right now",
    Copy.LiveNoneLiveSub to "Go live and you'll show up here instantly.",
    Copy.LiveWatching to "{n} watching",
    Copy.LiveStartFailedTitle to "Couldn't go live",
    Copy.LivePrivate to "Private",
    Copy.LiveFriends to "Friends",
    Copy.LivePasswordTitle to "This live is private",
    Copy.LivePasswordHint to "Enter the 5-character password",
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
    Copy.LiveGuestCamOn to "Co-host cameras allowed",
    Copy.LiveGuestCamOff to "Allow co-host cameras",
    Copy.LiveMirrorOn to "Mirror on",
    Copy.LiveMirrorOff to "Mirror off",
    Copy.LiveSentLike to "liked the LIVE ❤️",
    Copy.LiveEnteredRoom to "has entered the room 👋",
    Copy.LiveLeftRoom to "has left the room",
    Copy.LiveGameWin to "won {amount} in {game} 🎉",
    Copy.LiveGameWinGame to "a game",
    Copy.ProfileSending to "Sending",
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
    Copy.LiveSeatTitle to "Seat {n}",
    Copy.LiveSeatLock to "Lock this seat",
    Copy.LiveSeatUnlock to "Unlock this seat",
    Copy.LiveSeatsLockAll to "Lock all seats",
    Copy.LiveSeatsUnlockAll to "Unlock all seats",
    Copy.LiveSeatTake to "Take this seat",
    Copy.LiveSeatLocked to "That seat is locked.",
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
    Copy.LiveBlockLive to "Block for this live",
    Copy.LiveBlockedTitle to "Blocked this live",
    Copy.LiveBlockedNone to "No one has been blocked or removed in this live",
    Copy.LiveBlockedTag to "Blocked for this live",
    Copy.LiveRemovedTag to "Removed",
    Copy.LiveBanOver to "ended",
    Copy.LiveUnblock to "Unblock",
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
    Copy.PkHostLeft to "Left the PK",
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
    Copy.CreateLivePreview to "Camera & beauty",
    Copy.CreateLiveCamAsk to "Tap to allow camera access",
    Copy.CreateLiveHint to "Weekend coding session & Q&A",
    Copy.CreateVideo to "Video",
    Copy.CreateAudioOnly to "Audio",
    Copy.CreateRoomSize to "Room size",
    Copy.CreateSeats to "Custom number of seats",
    Copy.CreateSeatsHint to "Enter number (max 45)",
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
    Copy.CreateSave to "Save",
    Copy.CreateSavedToGallery to "Saved to your gallery",
    Copy.CreateSaveFailed to "Couldn't save to gallery",
    Copy.SnapTermsTitle to "Terms of Service",
    Copy.SnapTermsAccept to "Accept",
    Copy.SnapTermsDecline to "Decline",
    Copy.CreateEndLive to "End LIVE",
    Copy.CreateFlashOn to "Flash On",
    Copy.CreateFlashOff to "Flash Off",
    Copy.CreateTimer to "Timer",
    Copy.CreateGrid to "Grid",
    Copy.CreateSpeed to "Speed",
    Copy.CreateBeauty to "Beauty",
    Copy.CreateLooks to "Looks",
    Copy.CreateBackground to "Background",
    Copy.CreateMasks to "Masks",
    Copy.CreateBeautyAuto to "Auto",
    Copy.CreateBeautyGlam to "Glam",
    Copy.CreateBeautyBrightness to "Brightness",
    Copy.CreateBeautyLightening to "Lightening",
    Copy.CreateBeautyFaceSmooth to "Face Smooth",
    Copy.CreateBeautyRedness to "Redness",
    Copy.CreateBeautyClarity to "Clarity",
    Copy.CreateBeautyPinkness to "Pinkness",
    Copy.CreateBeautyContrast to "Contrast",
    Copy.CreateBeautyLipPink to "Lip Pink",
    Copy.CreateEffects to "Effects",
    Copy.CreateLensOff to "Off",
    Copy.CreateBgBlur to "Blur",
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
    Copy.CreateSeatsMax to "max 45",
    Copy.CreateAudience to "Audience",
    Copy.CreateAudiencePublic to "Public",
    Copy.CreateAudienceFriends to "Friends",
    Copy.CreateAudiencePrivate to "Private",
    Copy.CreateAudiencePublicSub to "Everyone",
    Copy.CreateAudienceFriendsSub to "Following",
    Copy.CreateAudiencePrivateSub to "Password",
    Copy.CreatePasswordHint to "Set a 5-character room password…",
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
    Copy.ProfileUserId to "ID {n}",
    Copy.ProfileUsernameLocked to "Username can be changed once every {n} days",
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
    Copy.DeleteAccountTitle to "Delete account",
    Copy.DeleteAccountHeading to "Delete your {app} account?",
    Copy.DeleteAccountBody to
        "Your profile, posts, followers, messages and balance will be closed and will " +
        "no longer be visible to anyone.",
    Copy.DeleteAccountRecoverNote to
        "Your account will be deleted after 48 hours. You will be signed out now. If you " +
        "change your mind, just log back in within 48 hours and your account will be kept.",
    Copy.DeleteAccountConfirmLabel to "I understand my account will be deleted",
    Copy.DeleteAccountCta to "Delete my account",
    Copy.DeleteAccountCancel to "Keep my account",
    Copy.DeleteAccountDone to
        "Your account will be deleted after 48 hours. Log back in within 48 hours to keep it.",
    Copy.ProfileTopSenders to "Top Senders",
    Copy.ProfileTopRecipients to "Top Recipients",
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
    Copy.ModTitle to "Moderation",
    Copy.FindFriendsTitle to "Find friends",
    Copy.FindFriendsSub to "Find people you know who are already here.",
    Copy.FindFriendsEnable to "Find friends from contacts",
    Copy.FindFriendsEnableHint to "You will be given suggestions for matching accounts.",
    Copy.FindFriendsSyncing to "You will be given suggestions for matching accounts.",
    Copy.FindFriendsPermission to "Allow contacts access so we can find people you know.",
    Copy.FindFriendsOpenSettings to "Open settings",
    Copy.FindFriendsResults to "People you may know",
    Copy.FindFriendsEmpty to "You will be given suggestions for matching accounts.",
    Copy.FindFriendsPrivacy to "You can turn this off anytime. Your synced data stays so suggestions can still work later.",
    Copy.ModSearchHint to "Search id, username or name",
    Copy.ModSearchEmpty to "No accounts match that.",
    Copy.ModCapture to "Screen capture",
    Copy.ModCaptureOn to "Capture allowed",
    Copy.ModCaptureOff to "Capture blocked",
    Copy.ModCaptureAllow to "Allow",
    Copy.ModCaptureRevoke to "Revoke",
    Copy.ModCaptureInherit to "Role decides",
    Copy.ModMic to "Microphone",
    Copy.ModMicOn to "Mic enabled",
    Copy.ModMicOff to "Mic disabled",
    Copy.ModMicDisable to "Disable mic",
    Copy.ModMicEnable to "Enable mic",
    Copy.ModWarn to "Warning",
    Copy.ModWarnSend to "Send warning",
    Copy.ModWarnSent to "Warning sent.",
    Copy.ModReasonHint to "Reason",
    Copy.ModReasonRequired to "Say why first.",
    Copy.ModBlock to "Block account",
    Copy.ModBlocked to "Account blocked",
    Copy.ModUnblock to "Unblock",
    Copy.ModSaved to "Saved.",
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
    Copy.RolesApplyingAs to "Applying as {role}",
    Copy.RolesNotePlaceholder to "Note to your upline (optional)",
    Copy.RolesApply to "Apply",
    Copy.RolesApplied to "Application sent. You'll be notified once it's reviewed.",
    Copy.RolesMyCodes to "My referral codes",
    Copy.RolesCodeOpens to "Onboards: {roles}",
    Copy.RolesCopied to "Code copied",
    Copy.RolesCopyCode to "Copy code",
    Copy.RolesShareCode to "Share",
    Copy.RolesShareLink to "Share link",
    Copy.RolesShareText to "Use my referral code {code} to join: {link}",
    Copy.RolesShareTextRole to "Apply as {role} with my code {code}: {link}",
    Copy.RolesShareTextRoleCode to "Apply as {role} with my referral code {code}",
    Copy.RolesSharePickRole to "Which role is this invite for?",
    Copy.RolesShareNoRoles to "This code has no roles to share yet.",
    Copy.RolesShareNoWeb to "Set Website URL in Settings → Branding so invite links can be shared.",
    Copy.RolesLinkCopied to "Link copied",
    Copy.RolesFinance to "Gifts & earnings",
    Copy.RolesGiftsReceived to "Received",
    Copy.RolesGiftsSent to "Sent",
    Copy.RolesReceivedLine to "{coins} coins · {diamonds} diamonds · {n} gifts",
    Copy.RolesSentLine to "{coins} coins · {n} gifts",
    Copy.RolesRecharges to "Recharges",
    Copy.RolesRechargeLine to "{coins} coins credited · {n} approved",
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
    Copy.RolesLeaveUpline to "Leave upline",
    Copy.RolesLeaveUplineHint to "You keep your role and your team — only the link to your upline is removed. Your upline will be informed with your reason.",
    Copy.RolesLeaveRole to "Leave role",
    Copy.RolesLeaveRoleHint to "This ends your membership in this role immediately — its permissions, team link and role rewards stop. Your upline will be informed with your reason.",
    Copy.RolesLeftRole to "You left the role.",
    Copy.RolesShiftUpline to "Change upline",
    Copy.RolesShiftUplineHint to "Enter your new upline's referral code. The move happens only after they approve your request.",
    Copy.RolesShiftCodePlaceholder to "New upline's referral code",
    Copy.RolesShiftCodeRequired to "Enter the new upline's referral code.",
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
    Copy.FaceTitle to "Face verification",
    Copy.FaceIntro to "Position your face in the oval. It captures automatically when everything looks right.",
    Copy.FaceHintCenter to "Center your face in the oval",
    Copy.FaceHintCloser to "Move closer",
    Copy.FaceHintBack to "Move back a little",
    Copy.FaceHintLight to "Find more light",
    Copy.FaceHintStill to "Hold still…",
    Copy.FaceHintOne to "Only you in the frame",
    Copy.FaceHintFront to "Look straight at the camera",
    Copy.FaceTurnLeft to "Slowly turn your head to the left",
    Copy.FaceTurnRight to "Slowly turn your head to the right",
    Copy.FaceCapture to "Capture",
    Copy.FaceChecking to "Verifying…",
    Copy.FaceOkVerified to "Face verified",
    Copy.FacePendingMsg to "Submitted. We'll confirm shortly.",
    Copy.FaceReviewMsg to "Your verification is being reviewed.",
    Copy.FaceDuplicateMsg to "This face is already registered to another account.",
    Copy.FaceDuplicateRetryHint to "A face can verify a limited number of accounts, and that limit was reached when you last tried. If the limit now allows your account, verify again and it will be re-checked.",
    Copy.FaceRecheckCta to "Verify again",
    Copy.FaceDuplicateLimitHint to "Accounts allowed per face: {limit}. This face is already used by {used} account(s), so {remaining} more can be verified. You can verify this account now.",
    Copy.FaceDuplicateFullHint to "Accounts allowed per face: {limit}, and this face already verifies {used} account(s). No more accounts can be verified with this face.",
    Copy.FaceMismatchMsg to "This doesn't match the face verified on this account.",
    Copy.FaceSpoofMsg to "Liveness check failed. Use your real face in good light.",
    Copy.FaceQualityMsg to "Image quality too low. Try again with better light.",
    Copy.FaceChallengeFailMsg to "We couldn't confirm the movement. Try again.",
    Copy.FaceErrorMsg to "Verification is busy. Try again in a moment.",
    Copy.FaceRequiredMsg to "This role requires face verification.",
    Copy.FaceRetry to "Try again",
    Copy.FaceClose to "Close",
    Copy.FaceVerifyCta to "Verify face",
    Copy.FaceGateHint to "This role requires a quick face check before you apply.",
    Copy.FaceStatusNone to "Not verified",
    Copy.FaceStatusPending to "Pending confirmation",
    Copy.FaceStatusReview to "Under review",
    Copy.FaceStatusDuplicate to "Account limit reached",
    Copy.FaceStatusRejected to "Not approved",
    Copy.FaceStatusCanRetry to "Verification available",
    Copy.FaceStatusOff to "Not available right now",
    Copy.FaceStandaloneIntro to "Verify your face once to get the verified badge on your profile. The same check covers any role or reward that needs a verified face.",
    Copy.FaceVerifiedHint to "Your face is verified. The badge shows on your profile and every role or reward that needs it is covered.",
    Copy.FaceRejectedHint to "Your last check wasn't approved. You can try again in good light with your face centred.",
    Copy.FaceOffHint to "Face verification is switched off at the moment. Please check back later.",
    Copy.FacePermMsg to "Camera access is needed to verify your face. Nothing is recorded — one photo is checked and that's it.",
    Copy.FacePermGrant to "Allow camera",
    Copy.FacePermSettings to "Open settings",
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
    Copy.ProfileVersion to "{app} v{version}",
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
    Copy.CallNotifyJoin to "Join",
    Copy.CallNotifyEnd to "End",
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

    // Paid chat & calls
    Copy.PaidTitle to "Paid chat & calls",
    Copy.PaidLoadFailed to "Couldn't load paid chat & calls. Pull to try again.",
    Copy.PaidTabOverview to "Overview",
    Copy.PaidTabMyRates to "My rates",
    Copy.PaidTabHistory to "History",
    Copy.PaidRangeToday to "Today",
    Copy.PaidRangeWeek to "This week",
    Copy.PaidRangeMonth to "This month",
    Copy.PaidRangeAll to "All time",
    Copy.PaidOff to "Paid chat & calls is off right now. Messages and calls are free.",
    Copy.PaidFreeRole to "Your role is free of charges — messaging and calling anyone costs you nothing.",
    Copy.PaidFreeForYou to "Free for you",
    Copy.PaidYouPay to "You pay {rates}",
    Copy.PaidYouEarn to "You earn {rates}",
    Copy.PaidChatUnlocked to "Chat unlocked — messages here are free now",
    Copy.PaidPromoLeft to "{m} free messages and {n} free call minutes left {period}",
    Copy.PaidPromoDay to "today",
    Copy.PaidPromoWeek to "this week",
    Copy.PaidPromoMonth to "this month",
    Copy.PaidPromoTag to "free (promo)",
    Copy.PaidPromoUsed to "Promo units used",
    Copy.PaidUnitMessage to "message",
    Copy.PaidUnitConversation to "chat",
    Copy.PaidUnitCall to "call",
    Copy.PaidUnitMinute to "min",
    Copy.PaidClaimable to "Ready to claim",
    Copy.PaidClaimableCount to "{n} earnings waiting",
    Copy.PaidClaimableShort to "{n} to claim",
    Copy.PaidClaimExpiry to "Unclaimed earnings expire after {n} days.",
    Copy.PaidClaimBtn to "Claim to wallet",
    Copy.PaidClaimed to "Claimed {n}. Wallet {before} → {after}.",
    Copy.PaidEarned to "Earned",
    Copy.PaidClaimedTotal to "Claimed so far",
    Copy.PaidDirectTotal to "Credited directly",
    Copy.PaidExpiredTotal to "Expired unclaimed",
    Copy.PaidSpentToday to "Spent today",
    Copy.PaidSpentAll to "Spent all time",
    Copy.PaidPolicy to "How it works for you",
    Copy.PaidOwnRatesLine to "Your rates: {rates}",
    Copy.PaidNoRules to "No price applies to you right now.",
    Copy.PaidFeeLine to "Platform fee: {fee} (taken from the receiver's side).",
    Copy.PaidPayoutClaim to "Earnings wait here as {cur} until you claim them.",
    Copy.PaidPayoutDirect to "Earnings land in your wallet as {cur} immediately.",
    Copy.PaidRulePay to "you pay when contacting {to}",
    Copy.PaidRuleEarn to "you earn when {from} contact you",
    Copy.PaidRuleBoth to "applies both ways",
    Copy.PaidAnyone to "anyone",
    Copy.PaidReceivedFrom to "Received, by chat and day",
    Copy.PaidFromOn to "From {name} on {date}",
    Copy.PaidUnits to "{n} charges",
    Copy.PaidEmpty to "Nothing here yet.",
    Copy.PaidLoadMore to "Load more",
    Copy.PaidChargeMessage to "Message",
    Copy.PaidChargeUnlock to "Chat unlock",
    Copy.PaidChargeAudio to "Audio call",
    Copy.PaidChargeAudioMin to "Audio call · minute {n}",
    Copy.PaidChargeVideo to "Video call",
    Copy.PaidChargeVideoMin to "Video call · minute {n}",
    Copy.PaidStatusPaid to "Credited",
    Copy.PaidStatusClaimable to "To claim",
    Copy.PaidStatusClaimed to "Claimed",
    Copy.PaidStatusExpired to "Expired",
    Copy.PaidBeforeAfter to "Wallet {before} → {after}",
    Copy.PaidFree to "Free",
    Copy.PaidSideEarned to "Earned",
    Copy.PaidSidePaid to "Paid",
    Copy.PaidMyRatesHint to "Set what people pay (in {cur}) to message or call you. Leave a field empty for free.",
    Copy.PaidRateMax to "Highest price allowed: {n}.",
    Copy.PaidChannelMessage to "Messages",
    Copy.PaidChannelAudio to "Audio calls",
    Copy.PaidChannelVideo to "Video calls",
    Copy.PaidRatesSave to "Save my rates",
    Copy.PaidRatesClear to "Remove my rates",
    Copy.PaidRatesSaved to "Your rates are saved.",
    Copy.PaidRatesCleared to "Your rates were removed.",
    Copy.CallNoFundsMine to "Call ended — not enough balance to continue",
    Copy.CallNoFundsTheirs to "Call ended — the caller's balance ran out",
    Copy.CallPaidSoFar to "{rate} · {n} so far",
    Copy.CallPromoFree to "{rate} · free minutes (promo)",
    Copy.CallEarnedSoFar to "You've earned {n} on this call",
    Copy.TxnCatPaidContactPaid to "Paid chat & calls",
    Copy.TxnCatPaidContactEarned to "Chat & call earnings",
)

private val Zh = mapOf(
    Copy.SnapTermsTitle to "服务条款",
    Copy.SnapTermsAccept to "接受",
    Copy.SnapTermsDecline to "拒绝",
    Copy.CreateSave to "保存",
    Copy.CreateSavedToGallery to "已保存到相册",
    Copy.CreateSaveFailed to "无法保存到相册",
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
    Copy.LivePartyBadge to "派对",
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
    Copy.LiveStickerSentLine to "给 {to} 发了一个贴纸",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Ar = mapOf(
    Copy.SnapTermsTitle to "شروط الخدمة",
    Copy.SnapTermsAccept to "قبول",
    Copy.SnapTermsDecline to "رفض",
    Copy.CreateSave to "حفظ",
    Copy.CreateSavedToGallery to "تم الحفظ في المعرض",
    Copy.CreateSaveFailed to "تعذّر الحفظ في المعرض",
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
    Copy.LivePartyBadge to "حفلة",
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
    Copy.LiveStickerSentLine to "أرسل ملصقًا إلى {to}",
    Copy.YouHandle to "@you_{wordmark}",
    Copy.ProfileSettings to "الإعدادات",
    Copy.ProfileDrawerAccount to "الحساب",
    Copy.ProfileSwitchAccounts to "تبديل الحسابات",
    Copy.ProfileActivatePassword to "تفعيل تسجيل الدخول بكلمة المرور",
    Copy.ProfileChangePassword to "تغيير كلمة المرور",
    Copy.FindFriendsTitle to "البحث عن أصدقاء",
    Copy.ProfileWallet to "المحفظة / العملات",
    Copy.TxnTitle to "سجل المعاملات",
    Copy.WithdrawTitle to "سحب الألماس",
    Copy.ProfilePrivacySafety to "الخصوصية والأمان",
    Copy.ProfilePrivacy to "الخصوصية",
    Copy.ProfileDms to "الرسائل المباشرة",
    Copy.ProfileBlocked to "الحسابات المحظورة",
    Copy.ProfileSafeMode to "الوضع الآمن",
    Copy.ProfileContentDisplay to "المحتوى والعرض",
    Copy.ProfileDisplay to "العرض",
    Copy.ProfileLanguage to "اللغة",
    Copy.CreateDone to "تم",
    Copy.ProfileTranslation to "الترجمة",
    Copy.ProfileNotifications to "الإشعارات",
    Copy.ProfileSupportLegal to "الدعم والشروط",
    Copy.ProfileHelpCentre to "مركز المساعدة",
    Copy.ProfileTerms to "الشروط والسياسات",
    Copy.ProfileLocationContacts to "الموقع وجهات الاتصال",
    Copy.ProfileLogOut to "تسجيل الخروج",
    Copy.DeleteAccountTitle to "حذف الحساب",
    Copy.DeleteAccountHeading to "حذف حسابك في {app}؟",
    Copy.DeleteAccountBody to
        "سيتم إغلاق ملفك الشخصي ومنشوراتك ومتابعيك ورسائلك ورصيدك ولن يعود أحد قادراً على رؤيتها.",
    Copy.DeleteAccountRecoverNote to
        "سيتم حذف حسابك بعد ٤٨ ساعة. سيتم تسجيل خروجك الآن. إذا غيرت رأيك، سجّل الدخول مرة " +
        "أخرى خلال ٤٨ ساعة وسيتم الاحتفاظ بحسابك.",
    Copy.DeleteAccountConfirmLabel to "أفهم أن حسابي سيتم حذفه",
    Copy.DeleteAccountCta to "حذف حسابي",
    Copy.DeleteAccountCancel to "الاحتفاظ بحسابي",
    Copy.DeleteAccountDone to
        "سيتم حذف حسابك بعد ٤٨ ساعة. سجّل الدخول خلال ٤٨ ساعة للاحتفاظ به.",
    Copy.ProfileEditTitle to "تعديل الملف",
    Copy.ProfileChangePhoto to "تغيير الصورة",
    Copy.ProfileNameLabel to "الاسم",
    Copy.ProfileUsername to "اسم المستخدم",
    Copy.ProfileGender to "الجنس",
    Copy.ProfileGenderMale to "ذكر",
    Copy.ProfileGenderFemale to "أنثى",
    Copy.ProfileGenderPreferNot to "أفضل عدم الذكر",
    Copy.ProfileWhatsOnMind to "بماذا تفكر؟",
    Copy.ProfileCopied to "تم النسخ",
    Copy.ProfileUsernameLocked to "يمكن تغيير اسم المستخدم مرة كل {n} يومًا",
    Copy.ProfileUsernameAgainIn to "يمكنك تغيير اسم المستخدم بعد {n} يومًا",
    Copy.ProfileUsernameAvailable to "@{u} متاح",
    Copy.ProfileUsernameTaken to "@{u} مستخدم بالفعل",
    Copy.ProfileUsernameRule to "٣–٢٤ حرفًا: حروف أو أرقام أو نقطة أو شرطة سفلية",
    Copy.ProfileUsernameChecking to "جارٍ التحقق…",
    Copy.ProfileRestricted to "مقيّد",
    Copy.HomePosting to "جارٍ النشر…",
    Copy.HomeFailed to "تعذرت المعالجة",
    Copy.GiftRecordTitle to "سجل الهدايا",
    Copy.GiftRecordToday to "اليوم",
    Copy.GiftRecordWeek to "هذا الأسبوع",
    Copy.GiftRecordMonth to "هذا الشهر",
    Copy.GiftRecordCustom to "تواريخ",
    Copy.GiftRecordSent to "أُرسل",
    Copy.GiftRecordReceived to "استُلم",
    Copy.GiftRecordGifts to "{n} هدية",
    Copy.GiftRecordSenderLevel to "مستوى المُرسل",
    Copy.GiftRecordReceiverLevel to "مستوى المستلم",
    Copy.GiftRecordNoLevel to "لا مستوى بعد",
    Copy.GiftRecordLevelN to "المستوى {n}",
    Copy.GiftRecordToNext to "{n} عملة للمستوى التالي",
    Copy.GiftRecordTop to "بلغت أعلى مستوى",
    Copy.GiftRecordRange to "{from} – {to}",
    Copy.GiftRecordByDay to "حسب اليوم",
    Copy.GiftRecordEmpty to "لا هدايا في هذه الفترة.",
    Copy.GiftRecordFrom to "من (YYYY-MM-DD)",
    Copy.GiftRecordTo to "إلى (YYYY-MM-DD)",
    Copy.GiftRecordApply to "عرض",
    Copy.GiftRecordBadDate to "أدخل التواريخ بصيغة YYYY-MM-DD.",
    Copy.RewardsTitle to "مكافآتي",
    Copy.RewardsToday to "اليوم",
    Copy.RewardsByDay to "حسب اليوم",
    Copy.RewardsEmpty to "لا مكافآت بعد. ابدأ بثًا مباشرًا لتربحها.",
    Copy.RewardsLoadFailed to "تعذر تحميل مكافآتك.",
    Copy.RewardsLiveTime to "وقت البث",
    Copy.RewardsAdded to "أُضيف إلى الرصيد",
    Copy.RewardsPending to "قيد الانتظار",
    Copy.RewardsClaimable to "جاهز للاستلام",
    Copy.RewardsClaim to "استلم {amt}",
    Copy.RewardsClaimed to "أُضيف {amt} إلى رصيدك",
    Copy.RewardsClaimWaiting to "{amt} بانتظار نافذة الاستلام",
    Copy.RewardsClaimOpens to "يفتح {t}",
    Copy.RewardsHistory to "السجل",
    Copy.RewardsOlder to "مكافآت أقدم",
    Copy.RewardsStatusClaimed to "تم الاستلام",
    Copy.RewardsCompletedAt to "اكتمل في",
    Copy.RewardsAddedAt to "أُضيف في",
    Copy.RewardsBalance to "الرصيد",
    Copy.RewardsRequired to "المطلوب",
    Copy.RewardsVerified to "الموثّق",
    Copy.RewardsDay to "يوم المكافأة",
    Copy.RewardsRole to "بصفة",
    Copy.RewardsTimeline to "الخط الزمني",
    Copy.RewardsDetail to "المكافأة",
    Copy.RewardsNoRewardsDay to "وقت بث دون بلوغ مكافأة",
    Copy.RewardsDayNote to "أيام المكافأة تبدأ من {start} بتوقيت {tz}، نفس تقويم وقت البث. الأوقات المعروضة بتوقيت {tz}.",
    Copy.RewardsOff to "مكافآت البث متوقفة الآن.",
    Copy.TxnRetry to "إعادة المحاولة",
    Copy.MyLiveCohost to "مشارك",
    Copy.RewardsHost to "مضيف",
    Copy.RolesTitle to "\u0623\u062F\u0648\u0627\u0631\u064A",
    Copy.RolesTabMine to "الأدوار",
    Copy.RolesTabRequests to "الطلبات",
    Copy.RolesTabApprovals to "الموافقات",
    Copy.RolesMineEmpty to "ليس لديك دور بعد. لديك رمز دعوة؟ أدخله بالأسفل.",
    Copy.RolesJoinTitle to "انضم برمز الدعوة",
    Copy.RolesApply to "تقديم",
    Copy.RolesMyCodes to "رموز الدعوة الخاصة بي",
    Copy.RolesCopied to "تم نسخ الرمز",
    Copy.RolesRequestsEmpty to "لا طلبات بعد.",
    Copy.RolesApprovalsEmpty to "لا شيء بانتظارك.",
    Copy.RolesLoadFailed to "تعذر التحميل. تحقق من الاتصال.",
    Copy.RolesYourCode to "رمزك",
    Copy.StoreKindFrames to "إطارات",
    Copy.StoreKindBadges to "شارات",
    Copy.StoreKindEntrance to "دخول",
    Copy.StoreKindVip to "كبار الشخصيات",
    Copy.StoreKindBubbles to "فقاعات الدردشة",
    Copy.StoreTitle to "المتجر",
    Copy.StoreTabAll to "كل {name}",
    Copy.StoreTabMine to "{name} الخاصة بي",
    Copy.StoreFree to "مجاني",
    Copy.StoreNew to "جديد",
    Copy.StoreOwned to "مملوك",
    Copy.StoreWearing to "مستخدم الآن",
    Copy.StoreParked to "مملوك · غير مفعّل",
    Copy.StoreLapsed to "منتهٍ",
    Copy.StoreMineEmpty to "ليس لديك أي {name} بعد.",
    Copy.StoreShopEmpty to "لا شيء في {name} الآن.",
    Copy.StoreFreeEmpty to "لا عناصر مجانية بعد.",
    Copy.StoreFreeSub to "حزم الترحيب والأدوار وهدايا الفريق",
    Copy.FramesBuy to "شراء · {n} عملة",
    Copy.FramesActivate to "تفعيل",
    Copy.FramesActive to "مفعّل",
    Copy.FramesExpired to "منتهٍ",
    Copy.FramesPermanent to "دائم",
    Copy.FramesInsufficient to "العملات غير كافية.",
    Copy.FramesDays to "{n} يوم",
    Copy.FramesDay1 to "يوم واحد",
    Copy.FramesWeek1 to "أسبوع",
    Copy.FramesMonth1 to "شهر",
    Copy.FramesMonths3 to "٣ أشهر",
    Copy.FramesDaysLeft to "متبقٍ {n}ي",
    Copy.FramesPickDuration to "اختر المدة",
    Copy.InboxChats to "الدردشات",
    Copy.InboxNotifications to "الإشعارات",
    Copy.InboxActivity to "النشاط",
    Copy.InboxSearch to "ابحث في الدردشات والأشخاص",
    Copy.InboxOnline to "متصل",
    Copy.InboxTyping to "يكتب…",
    Copy.InboxPinned to "مثبّت",
    Copy.InboxAll to "كل الرسائل",
    Copy.InboxArchived to "الأرشيف",
    Copy.InboxNewChat to "دردشة جديدة",
    Copy.InboxCancel to "إلغاء",
    Copy.InboxFollow to "متابعة",
    Copy.InboxNewFollowers to "متابعون جدد",
    Copy.InboxSystem to "النظام",
    Copy.InboxNoResults to "لا نتائج لـ \"{q}\"",
    Copy.InboxPeople to "أشخاص",
    Copy.InboxFollowedYou to "بدأ بمتابعتك",
    Copy.InboxVideoRestrictedTitle to "الفيديو مقيّد",
    Copy.InboxVideoRestrictedBody to "تم تقييد فيديوك لمخالفة الإرشادات. إذا كان ذلك خطأ يمكنك طلب مراجعة.",
    Copy.InboxOk to "حسنًا",
    Copy.ProfileSaveFailed to "تعذر الحفظ. أعدنا ملفك كما كان.",
    Copy.RolesJoinHint to "صاحب الرمز يصبح مسؤولاً عنك ويوافق على طلبك.",
    Copy.RolesCodePlaceholder to "رمز الدعوة",
    Copy.RolesPickRole to "قدّم بصفة",
    Copy.RolesNotePlaceholder to "ملاحظة للمسؤول (اختياري)",
    Copy.RolesApplied to "أُرسل الطلب. سنبلغك بعد المراجعة.",
    Copy.FaceTitle to "التحقق من الوجه",
    Copy.FaceIntro to "ضع وجهك داخل الإطار البيضاوي. يتم الالتقاط تلقائيًا عندما يكون كل شيء مناسبًا.",
    Copy.FaceHintCenter to "ضع وجهك في منتصف الإطار",
    Copy.FaceHintCloser to "اقترب أكثر",
    Copy.FaceHintBack to "ابتعد قليلًا",
    Copy.FaceHintLight to "ابحث عن إضاءة أفضل",
    Copy.FaceHintStill to "اثبت مكانك…",
    Copy.FaceHintOne to "يجب أن تكون وحدك في الصورة",
    Copy.FaceHintFront to "انظر مباشرة إلى الكاميرا",
    Copy.FaceTurnLeft to "أدر رأسك ببطء إلى اليسار",
    Copy.FaceTurnRight to "أدر رأسك ببطء إلى اليمين",
    Copy.FaceCapture to "التقاط",
    Copy.FaceChecking to "جارٍ التحقق…",
    Copy.FaceOkVerified to "تم التحقق من الوجه",
    Copy.RewardsNotEligibleFace to "الوجه غير موثّق — وثّق وجهك لتحصل على المكافآت",
    Copy.RewardsHudFaceNeeded to "الوجه غير موثّق · لن تُضاف المكافآت",
    Copy.RewardsHudFaceNotAdded to "الوجه غير موثّق · لم تُضف {amt}",
    Copy.FacePendingMsg to "تم الإرسال. سنؤكد قريبًا.",
    Copy.FaceReviewMsg to "طلب التحقق قيد المراجعة.",
    Copy.FaceDuplicateMsg to "هذا الوجه مسجل بالفعل في حساب آخر.",
    Copy.FaceDuplicateRetryHint to "يمكن للوجه الواحد توثيق عدد محدود من الحسابات، وقد اكتمل هذا الحد عند آخر محاولة. إذا أصبح الحد يسمح بحسابك الآن، فوثّق مرة أخرى وسيُعاد الفحص.",
    Copy.FaceRecheckCta to "التحقق مرة أخرى",
    Copy.FaceDuplicateLimitHint to "عدد الحسابات المسموح به لكل وجه: {limit}. هذا الوجه مستخدم بالفعل في {used} حساب، ويتبقى {remaining}. يمكنك توثيق هذا الحساب الآن.",
    Copy.FaceDuplicateFullHint to "عدد الحسابات المسموح به لكل وجه: {limit}، وهذا الوجه موثّق بالفعل في {used} حساب. لا يمكن توثيق حسابات أخرى بهذا الوجه.",
    Copy.FaceMismatchMsg to "هذا لا يطابق الوجه الموثق في هذا الحساب.",
    Copy.FaceSpoofMsg to "فشل فحص الحيوية. استخدم وجهك الحقيقي في إضاءة جيدة.",
    Copy.FaceQualityMsg to "جودة الصورة منخفضة جدًا. حاول مجددًا بإضاءة أفضل.",
    Copy.FaceChallengeFailMsg to "تعذر تأكيد الحركة. حاول مرة أخرى.",
    Copy.FaceErrorMsg to "خدمة التحقق مشغولة. حاول بعد قليل.",
    Copy.FaceRequiredMsg to "يتطلب هذا الدور التحقق من الوجه.",
    Copy.FaceRetry to "إعادة المحاولة",
    Copy.FaceClose to "إغلاق",
    Copy.FaceVerifyCta to "التحقق من الوجه",
    Copy.FaceGateHint to "يتطلب هذا الدور فحصًا سريعًا للوجه قبل التقديم.",
    Copy.FaceStatusNone to "غير موثّق",
    Copy.FaceStatusPending to "بانتظار التأكيد",
    Copy.FaceStatusReview to "قيد المراجعة",
    Copy.FaceStatusDuplicate to "تم بلوغ حد الحسابات",
    Copy.FaceStatusCanRetry to "التوثيق متاح",
    Copy.FaceStatusRejected to "لم تتم الموافقة",
    Copy.FaceStatusOff to "غير متاح حاليًا",
    Copy.FaceStandaloneIntro to "وثّق وجهك مرة واحدة لتحصل على شارة التوثيق في ملفك. الفحص نفسه يغطي أي دور أو مكافأة تتطلب وجهًا موثّقًا.",
    Copy.FaceVerifiedHint to "تم توثيق وجهك. تظهر الشارة في ملفك وكل دور أو مكافأة تتطلبها مشمولة.",
    Copy.FaceRejectedHint to "لم تتم الموافقة على آخر فحص. يمكنك المحاولة مجددًا في إضاءة جيدة مع توسيط وجهك.",
    Copy.FaceOffHint to "التحقق من الوجه متوقف حاليًا. يرجى المحاولة لاحقًا.",
    Copy.FacePermMsg to "يلزم الوصول إلى الكاميرا للتحقق من وجهك. لا يتم تسجيل أي شيء — تُفحص صورة واحدة فقط.",
    Copy.FacePermGrant to "السماح بالكاميرا",
    Copy.FacePermSettings to "فتح الإعدادات",
    Copy.RolesCopyCode to "نسخ الرمز",
    Copy.RolesShareLink to "مشاركة الرابط",
    Copy.RolesLinkCopied to "تم نسخ الرابط",
    Copy.RolesUpline to "المسؤول",
    Copy.RolesDownline to "الفريق",
    Copy.RolesDirect to "المباشرون",
    Copy.RolesWhole to "الجميع",
    Copy.RolesDownlineEmpty to "لا أحد تحتك بعد. شارك رمز الدعوة.",
    Copy.RolesApprove to "موافقة",
    Copy.RolesReject to "رفض",
    Copy.RolesCancel to "إلغاء الطلب",
    Copy.RolesWaiting to "بانتظار الموافقة",
    Copy.RolesAddMember to "إضافة عضو",
    Copy.ProfileNoPosts to "لا منشورات بعد",
    Copy.ProfileNoLiked to "الفيديوهات التي تعجبك تظهر هنا",
    Copy.ProfileNoSaved to "لا شيء محفوظ بعد",
    Copy.ProfileNoPrivate to "لا منشورات خاصة",
    Copy.ActivityEmpty to "لا شيء هنا بعد. عندما يشاهد أحد حالتك تظهر هنا.",
    Copy.StoryViewedYours to "شاهد حالتك",
    Copy.ActivityCommented to "علّق على منشورك",
    Copy.ActivityReplied to "رد على تعليقك",
    Copy.ActivityCommentLiked to "أعجب بتعليقك",
    Copy.InboxJustNow to "الآن",
    Copy.InboxLastSeen to "آخر ظهور قبل {t}",
    Copy.InboxOffline to "غير متصل",
    Copy.InboxChatOpenFail to "تعذر فتح هذه الدردشة. حاول مرة أخرى.",
    Copy.InboxNewGroup to "مجموعة جديدة",
    Copy.InboxCreateGroup to "إنشاء مجموعة",
    Copy.InboxSearchShort to "بحث",
    Copy.InboxRecents to "الأخيرة",
    Copy.InboxCreate to "إنشاء",
    Copy.InboxGroupName to "اسم المجموعة",
    Copy.InboxInviteLink to "رابط الدعوة",
    Copy.InboxCopyAction to "نسخ",
    Copy.InboxMessage to "رسالة",
    Copy.InboxPin to "تثبيت",
    Copy.InboxUnpin to "إلغاء التثبيت",
    Copy.InboxMute to "كتم",
    Copy.InboxUnmute to "إلغاء الكتم",
    Copy.InboxArchive to "أرشفة",
    Copy.InboxUnarchive to "إلغاء الأرشفة",
    Copy.InboxBlock to "حظر",
    Copy.InboxUnblock to "إلغاء الحظر",
    Copy.InboxDelete to "حذف الدردشة",
    Copy.InboxSupportRepliedTitle to "رد الدعم",
    Copy.RolesNoticeRequest to "طلب دور",
    Copy.RolesNoticeDecision to "تم البت في طلب الدور",
    Copy.RolesNoticeChange to "تغيّر دورك",
    Copy.ProfileBalance to "الرصيد",
    Copy.ProfileTxnHistory to "سجل المعاملات",
    Copy.ProfileReportProblem to "الإبلاغ عن مشكلة",
    Copy.ProfileDrawerSettings to "الإعدادات",
    Copy.ProfileDrawerSupport to "الدعم",
    Copy.ProfileDrawerLegal to "قانوني",
    Copy.BalanceTitle to "رصيدي",
    Copy.BalanceCoins to "عملات",
    Copy.BalanceDiamonds to "ألماس",
    Copy.BalanceBeans to "فاصوليا",
    Copy.BalanceRecharge to "شحن",
    Copy.BalanceWithdraw to "سحب",
    Copy.BalanceHistory to "السجل",
    Copy.BalancePending to "{n} عملية شحن بانتظار الموافقة",
    Copy.BalanceRecent to "عمليات الشحن الأخيرة",
    Copy.RechargeTitle to "شحن العملات",
    Copy.RechargeTabPackages to "الباقات",
    Copy.RechargeTabRequests to "طلباتي",
    Copy.RechargePickPackage to "اختر عدد العملات",
    Copy.RechargeNoPackages to "لا باقات متاحة في بلدك بعد.",
    Copy.RechargePickMethod to "اختر طريقة الدفع",
    Copy.RechargeNoMethods to "لا طرق دفع متاحة في بلدك بعد.",
    Copy.RechargeBonus to "+{n} إضافية",
    Copy.RechargeSendExactly to "أرسل المبلغ بالضبط",
    Copy.RechargeYouGet to "ستحصل على",
    Copy.RechargeAccountTitle to "اسم الحساب",
    Copy.RechargeAccountNo to "رقم الحساب",
    Copy.RechargeCopy to "نسخ",
    Copy.RechargeCopied to "تم النسخ",
    Copy.RechargeInstructions to "التعليمات",
    Copy.RechargeProof to "صورة الدفع",
    Copy.RechargeProofHint to "بعد الدفع أرفق الإيصال لنتمكن من التحقق.",
    Copy.RechargeAttach to "إرفاق صورة",
    Copy.RechargeChange to "تغيير",
    Copy.RechargeNote to "رسالة (اختياري)",
    Copy.RechargeSubmit to "إرسال للموافقة",
    Copy.RechargeNeedProof to "أرفق صورة الدفع أولًا.",
    Copy.RechargeDone to "تم الإرسال. تُضاف العملات بعد تأكيد الدفع.",
    Copy.RechargeNoRequests to "لا طلبات شحن بعد.",
    Copy.SellerHubTitle to "بائع العملات",
    Copy.SellerDrawerHub to "بائع العملات",
    Copy.SellerDrawerList to "الشراء من بائع",
    Copy.TransferTitle to "تحويل",
    Copy.TransferDrawer to "تحويل",
    Copy.TransferTabSend to "إرسال",
    Copy.TransferTabHistory to "السجل",
    Copy.TransferTabDisputes to "النزاعات",
    Copy.TransferFindUser to "إرسال إلى",
    Copy.TransferFindHint to "معرّف المستخدم أو اسم المستخدم",
    Copy.TransferAmount to "المبلغ",
    Copy.TransferAmountHint to "المبلغ المراد إرساله",
    Copy.TransferLimits to "الحد الأدنى {min} · الأقصى {max}",
    Copy.TransferFeeLine to "الرسوم: {n}",
    Copy.TransferTheyGet to "يحصل {name} على {n} {cur}",
    Copy.TransferNote to "ملاحظة (اختياري)",
    Copy.TransferSendBtn to "إرسال",
    Copy.TransferSentOk to "تم الإرسال.",
    Copy.TransferWindowLine to "استُخدم {used} من {max} لكل {d} يوم",
    Copy.TransferRecvRemaining to "يمكنه استلام {n} إضافية",
    Copy.TransferCantReceive to "لا يمكن لهذا الحساب استلام تحويلات منك.",
    Copy.TransferDisabled to "التحويلات غير متاحة لحسابك.",
    Copy.TransferNoHistory to "لا توجد تحويلات بعد.",
    Copy.TransferDirSent to "المرسلة",
    Copy.TransferDirReceived to "المستلمة",
    Copy.TransferDisputeBtn to "الإبلاغ عن مشكلة",
    Copy.TransferDisputeReason to "ما المشكلة؟ مثل خصم خاطئ",
    Copy.TransferDisputeOpened to "تم فتح النزاع.",
    Copy.TransferNoDisputes to "لا توجد نزاعات.",
    Copy.TransferDisputeTitle to "نزاع",
    Copy.ExchangeTitle to "صرف العملات",
    Copy.ExchangeDrawer to "صرف العملات",
    Copy.ExchangeTabConvert to "تحويل",
    Copy.ExchangeTabHistory to "السجل",
    Copy.ExchangePair to "تحويل",
    Copy.ExchangeAmount to "المبلغ",
    Copy.ExchangeAmountHint to "المبلغ المراد تحويله",
    Copy.ExchangeRateLine to "السعر: {r}×",
    Copy.ExchangeFeeLine to "الرسوم: {n} ({p}%)",
    Copy.ExchangeYouGet to "تحصل على {n} {cur}",
    Copy.ExchangeBtn to "صرف",
    Copy.ExchangeDone to "تم الصرف.",
    Copy.ExchangeDisabled to "الصرف غير متاح لحسابك.",
    Copy.ExchangeNoHistory to "لا توجد عمليات صرف بعد.",
    Copy.ExchangeLimits to "الحد الأدنى {min} · الأقصى {max}",
    Copy.ExchangeWindowLine to "استُخدم {used} من {max} لكل {d} يوم",
    Copy.ExchangeTotal to "إجمالي الصرف",
    Copy.ExchangeFeesPaid to "الرسوم المدفوعة",
    Copy.LbTitle to "لوحة الصدارة",
    Copy.LbSenders to "أكبر المرسلين",
    Copy.LbHosts to "أفضل المضيفين",
    Copy.LbAgencies to "أفضل الوكالات",
    Copy.LbDaily to "يومي",
    Copy.LbWeekly to "أسبوعي",
    Copy.LbMonthly to "شهري",
    Copy.LbTotal to "كل الأوقات",
    Copy.LbMembers to "{n} عضو",
    Copy.LbJoin to "انضم",
    Copy.LbEmpty to "لا توجد تصنيفات بعد.",
    Copy.PostGiftTitle to "أرسل هدية إلى {name}",
    Copy.PostGiftSend to "إرسال",
    Copy.PostGiftSent to "تم إرسال {gift} ✓",
    Copy.PostGiftNoCoins to "عملات غير كافية",
    Copy.PostGiftFailed to "تعذّر إرسال الهدية",
    Copy.PostGiftEmpty to "لا توجد هدايا متاحة حالياً.",
    Copy.PostShareBody to "شاهد {name} على {app}",
    Copy.LbCreators to "أفضل المبدعين",
    Copy.LbViews to "{n} مشاهدة",
    Copy.CreatorTabLive to "البث",
    Copy.CreatorTabCreator to "المبدع",
    Copy.CreatorEarned to "أرباح المبدع",
    Copy.CreatorGiftsLbl to "الهدايا",
    Copy.CreatorBonusLbl to "مكافأة",
    Copy.CreatorClaim to "استلام",
    Copy.CreatorClaimClosed to "الاستلام مغلق حالياً.",
    Copy.CreatorHow to "كيف يعمل",
    Copy.CreatorEmpty to "لا توجد مكافآت مبدع بعد.",
    Copy.CreatorOff to "مكافآت المبدعين غير متاحة حالياً.",
    Copy.CreatorKindGift to "رصيد هدية",
    Copy.CreatorKindBonus to "مكافأة إنجاز",
    Copy.SellerTabOverview to "نظرة عامة",
    Copy.SellerTabSend to "إرسال",
    Copy.SellerTabRequests to "الطلبات",
    Copy.SellerTabHistory to "السجل",
    Copy.SellerTabTeam to "الفريق",
    Copy.SellerBalance to "رصيدك",
    Copy.SellerListTitle to "بائعو العملات",
    Copy.SellerListEmpty to "لا بائعين متاحين الآن.",
    Copy.SellerTabSellers to "البائعون",
    Copy.SellerTabMyRequests to "طلباتي",
    Copy.SellerRequestBtn to "طلب شحن",
    Copy.SellerNoTransfers to "لا عمليات إرسال بعد.",
    Copy.SellerNoRequests to "لا طلبات بعد.",
    Copy.HelpSubject to "الموضوع",
    Copy.HelpMessage to "الرسالة",
    Copy.HelpSubmit to "إرسال",
    Copy.HelpMyReports to "بلاغاتي",
    Copy.MyLiveTitle to "بثّي",
    Copy.MyLiveToday to "اليوم",
    Copy.MyLiveLiveNow to "مباشر الآن",
    Copy.MyLiveCounting to "يُحتسب",
    Copy.MyLivePaused to "متوقف — يعيد الاتصال",
    Copy.MyLiveWeek to "هذا الأسبوع",
    Copy.MyLiveMonth to "هذا الشهر",
    Copy.MyLiveLifetime to "كل الوقت",
    Copy.MyLiveDays to "حسب اليوم",
    Copy.MyLiveDaysEmpty to "لا وقت بث في هذه الفترة.",
    Copy.MyLiveSessions to "الجلسات",
    Copy.MyLiveSessionsEmpty to "لم تبدأ بثًا في هذا اليوم.",
    Copy.MyLiveSessionsCount to "{n} جلسة",
    Copy.MyLiveAudio to "صوت",
    Copy.MyLiveDropsOnce to "إعادة اتصال واحدة",
    Copy.MyLiveDrops to "{n} إعادات اتصال",
    Copy.MyLivePausedFor to "{t} غير متصل، لا يُحتسب",
    Copy.MyLiveDayNote to "الأيام تبدأ من {start} بتوقيت {tz}. يُحتسب وقت اتصال الهاتف فقط.",
    Copy.MyLiveEndedBy to "انتهى: {r}",
    Copy.MyLiveReasonHost to "أنهيتَه",
    Copy.MyLiveReasonLost to "انقطع الاتصال",
    Copy.MyLiveReasonAppClosed to "أُغلق التطبيق",
    Copy.MyLiveReasonMax to "بلغ الحد الأقصى",
    Copy.MyLiveReasonStand to "غادر المنصة",
    Copy.MyLiveReasonOther to "{r}",
    Copy.MyLiveLoadFailed to "تعذر تحميل وقت البث.",
    Copy.MyLiveOlder to "أيام أقدم",
    Copy.MyLiveRewards to "المكافآت",
    Copy.CreatePost to "منشور",
    Copy.CreateGoLive to "بث",
    Copy.CreateVideo to "فيديو",
    Copy.CreateAudioOnly to "صوت",
    Copy.CreateLiveVideoTitle to "ابدأ بث فيديو",
    Copy.CreateLiveAudioTitle to "ابدأ غرفة صوت",
    Copy.CreateGoLiveBtn to "ابدأ البث",
    Copy.CreateStartAudioRoom to "ابدأ غرفة الصوت",

    // Paid chat & calls
    Copy.PaidTitle to "الدردشة والمكالمات المدفوعة",
    Copy.PaidLoadFailed to "تعذّر تحميل الدردشة والمكالمات المدفوعة. حاول مجددًا.",
    Copy.PaidTabOverview to "نظرة عامة",
    Copy.PaidTabMyRates to "أسعاري",
    Copy.PaidTabHistory to "السجل",
    Copy.PaidRangeToday to "اليوم",
    Copy.PaidRangeWeek to "هذا الأسبوع",
    Copy.PaidRangeMonth to "هذا الشهر",
    Copy.PaidRangeAll to "كل الوقت",
    Copy.PaidOff to "الدردشة والمكالمات المدفوعة متوقفة حاليًا. الرسائل والمكالمات مجانية.",
    Copy.PaidFreeRole to "دورك معفى من الرسوم — مراسلة أي شخص والاتصال به لا يكلفك شيئًا.",
    Copy.PaidFreeForYou to "مجاني لك",
    Copy.PaidYouPay to "تدفع {rates}",
    Copy.PaidYouEarn to "تكسب {rates}",
    Copy.PaidChatUnlocked to "تم فتح الدردشة — الرسائل هنا مجانية الآن",
    Copy.PaidPromoLeft to "تبقّى لك {m} رسائل مجانية و{n} دقائق مكالمات مجانية {period}",
    Copy.PaidPromoDay to "اليوم",
    Copy.PaidPromoWeek to "هذا الأسبوع",
    Copy.PaidPromoMonth to "هذا الشهر",
    Copy.PaidPromoTag to "مجاني (عرض)",
    Copy.PaidPromoUsed to "وحدات العرض المستخدمة",
    Copy.PaidUnitMessage to "رسالة",
    Copy.PaidUnitConversation to "محادثة",
    Copy.PaidUnitCall to "مكالمة",
    Copy.PaidUnitMinute to "دقيقة",
    Copy.PaidClaimable to "جاهز للمطالبة",
    Copy.PaidClaimableCount to "{n} أرباح بانتظارك",
    Copy.PaidClaimableShort to "{n} للمطالبة",
    Copy.PaidClaimExpiry to "تنتهي صلاحية الأرباح غير المُطالَب بها بعد {n} أيام.",
    Copy.PaidClaimBtn to "أضف إلى المحفظة",
    Copy.PaidClaimed to "تمت المطالبة بـ {n}. المحفظة {before} ← {after}.",
    Copy.PaidEarned to "المكتسب",
    Copy.PaidClaimedTotal to "تمت المطالبة به",
    Copy.PaidDirectTotal to "أُضيف مباشرة",
    Copy.PaidExpiredTotal to "انتهت صلاحيته دون مطالبة",
    Copy.PaidSpentToday to "أُنفق اليوم",
    Copy.PaidSpentAll to "أُنفق إجمالًا",
    Copy.PaidPolicy to "كيف يعمل معك",
    Copy.PaidOwnRatesLine to "أسعارك: {rates}",
    Copy.PaidNoRules to "لا ينطبق عليك أي سعر حاليًا.",
    Copy.PaidFeeLine to "رسوم المنصة: {fee} (تُخصم من جهة المستلم).",
    Copy.PaidPayoutClaim to "تبقى الأرباح هنا بعملة {cur} حتى تطالب بها.",
    Copy.PaidPayoutDirect to "تصل الأرباح إلى محفظتك بعملة {cur} فورًا.",
    Copy.PaidRulePay to "تدفع عند التواصل مع {to}",
    Copy.PaidRuleEarn to "تكسب عندما يتواصل معك {from}",
    Copy.PaidRuleBoth to "ينطبق في الاتجاهين",
    Copy.PaidAnyone to "أي شخص",
    Copy.PaidReceivedFrom to "المستلم، حسب المحادثة واليوم",
    Copy.PaidFromOn to "من {name} في {date}",
    Copy.PaidUnits to "{n} عمليات",
    Copy.PaidEmpty to "لا شيء هنا بعد.",
    Copy.PaidLoadMore to "تحميل المزيد",
    Copy.PaidChargeMessage to "رسالة",
    Copy.PaidChargeUnlock to "فتح الدردشة",
    Copy.PaidChargeAudio to "مكالمة صوتية",
    Copy.PaidChargeAudioMin to "مكالمة صوتية · الدقيقة {n}",
    Copy.PaidChargeVideo to "مكالمة فيديو",
    Copy.PaidChargeVideoMin to "مكالمة فيديو · الدقيقة {n}",
    Copy.PaidStatusPaid to "أُضيف",
    Copy.PaidStatusClaimable to "للمطالبة",
    Copy.PaidStatusClaimed to "تمت المطالبة",
    Copy.PaidStatusExpired to "منتهي",
    Copy.PaidBeforeAfter to "المحفظة {before} ← {after}",
    Copy.PaidFree to "مجاني",
    Copy.PaidSideEarned to "المكتسب",
    Copy.PaidSidePaid to "المدفوع",
    Copy.PaidMyRatesHint to "حدّد ما يدفعه الآخرون (بـ {cur}) لمراسلتك أو الاتصال بك. اترك الحقل فارغًا للمجاني.",
    Copy.PaidRateMax to "أعلى سعر مسموح: {n}.",
    Copy.PaidChannelMessage to "الرسائل",
    Copy.PaidChannelAudio to "المكالمات الصوتية",
    Copy.PaidChannelVideo to "مكالمات الفيديو",
    Copy.PaidRatesSave to "حفظ أسعاري",
    Copy.PaidRatesClear to "إزالة أسعاري",
    Copy.PaidRatesSaved to "تم حفظ أسعارك.",
    Copy.PaidRatesCleared to "تمت إزالة أسعارك.",
    Copy.CallNoFundsMine to "انتهت المكالمة — الرصيد غير كافٍ للمتابعة",
    Copy.CallNoFundsTheirs to "انتهت المكالمة — نفد رصيد المتصل",
    Copy.CallPaidSoFar to "{rate} · {n} حتى الآن",
    Copy.CallPromoFree to "{rate} · دقائق مجانية (عرض)",
    Copy.CallEarnedSoFar to "كسبت {n} في هذه المكالمة",
    Copy.TxnCatPaidContactPaid to "الدردشة والمكالمات المدفوعة",
    Copy.TxnCatPaidContactEarned to "أرباح الدردشة والمكالمات",
)

private val Bn = mapOf(
    Copy.SnapTermsTitle to "পরিষেবার শর্তাবলী",
    Copy.SnapTermsAccept to "গ্রহণ করুন",
    Copy.SnapTermsDecline to "প্রত্যাখ্যান",
    Copy.CreateSave to "সংরক্ষণ",
    Copy.CreateSavedToGallery to "গ্যালারিতে সংরক্ষিত হয়েছে",
    Copy.CreateSaveFailed to "গ্যালারিতে সংরক্ষণ করা যায়নি",
    Copy.TabHome to "হোম",
    Copy.TabGames to "গেমস",
    Copy.TabCreate to "তৈরি",
    Copy.TabInbox to "ইনবক্স",
    Copy.TabProfile to "প্রোফাইল",
    Copy.HomeForYou to "আপনার জন্য",
    Copy.HomeFollowing to "ফলোইং",
    Copy.HomeLive to "লাইভ",
    Copy.HomeFollowEmpty to "ভিডিও দেখতে ক্রিয়েটরদের ফলো করুন",
    Copy.LiveBadge to "লাইভ",
    Copy.LivePartyBadge to "পার্টি",
    Copy.GamesTitle to "গেমস",
    Copy.GamesBody to "তৃতীয় পক্ষের গেম শিগগিরই যুক্ত হবে।",
    Copy.ComingSoon to "শিগগিরই",
    Copy.CreateTitle to "তৈরি",
    Copy.CreateBody to "ছবি, লেখা ও ভিডিও শিগগিরই।",
    Copy.InboxTitle to "ইনবক্স",
    Copy.InboxBody to "চ্যাট, নোটিফিকেশন ও অ্যাক্টিভিটি শিগগিরই।",
    Copy.ProfileCopy to "কপি",
    Copy.ProfileVerified to "ভেরিফাইড",
    Copy.ProfileFollowing to "ফলো করছেন",
    Copy.ProfileFollowers to "ফলোয়ার",
    Copy.ProfileLikes to "লাইক",
    Copy.ProfileCoins to "কয়েন",
    Copy.ProfileDiamonds to "ডায়মন্ড",
    Copy.ProfileEdit to "প্রোফাইল এডিট",
    Copy.ProfileShare to "শেয়ার",
    Copy.ProfileLatest to "সাম্প্রতিক",
    Copy.ProfilePopular to "জনপ্রিয়",
    Copy.ProfileSearch to "প্রোফাইল, পোস্ট ও সাউন্ড খুঁজুন…",
    Copy.ProfileName to "আপনার নাম",
    Copy.ProfileStatus to "এই মুহূর্তে বাঁচছি",
    Copy.ProfileBio to "ক্রিয়েটর · ড্যান্সার · স্বপ্নদ্রষ্টা",
    Copy.ProfileSave to "সেভ",
    Copy.FeatureUnavailable to "এই অংশটি এখন বন্ধ আছে।",
    Copy.LiveStickerSentLine to "{to}-কে একটি স্টিকার পাঠিয়েছে",
    Copy.YouHandle to "@you_{wordmark}",
    Copy.ProfileSettings to "সেটিংস",
    Copy.ProfileDrawerAccount to "অ্যাকাউন্ট",
    Copy.ProfileSwitchAccounts to "অ্যাকাউন্ট বদলান",
    Copy.ProfileActivatePassword to "পাসওয়ার্ড লগইন চালু করুন",
    Copy.ProfileChangePassword to "পাসওয়ার্ড বদলান",
    Copy.FindFriendsTitle to "বন্ধু খুঁজুন",
    Copy.ProfileWallet to "ওয়ালেট / কয়েন",
    Copy.TxnTitle to "লেনদেনের ইতিহাস",
    Copy.WithdrawTitle to "ডায়মন্ড উত্তোলন",
    Copy.ProfilePrivacySafety to "প্রাইভেসি ও নিরাপত্তা",
    Copy.ProfilePrivacy to "প্রাইভেসি",
    Copy.ProfileDms to "ডাইরেক্ট মেসেজ",
    Copy.ProfileBlocked to "ব্লক করা অ্যাকাউন্ট",
    Copy.ProfileSafeMode to "সেফ মোড",
    Copy.ProfileContentDisplay to "কনটেন্ট ও ডিসপ্লে",
    Copy.ProfileDisplay to "ডিসপ্লে",
    Copy.ProfileLanguage to "ভাষা",
    Copy.CreateDone to "সম্পন্ন",
    Copy.ProfileTranslation to "অনুবাদ",
    Copy.ProfileNotifications to "নোটিফিকেশন",
    Copy.ProfileSupportLegal to "সাপোর্ট ও শর্তাবলি",
    Copy.ProfileHelpCentre to "হেল্প সেন্টার",
    Copy.ProfileTerms to "শর্ত ও নীতি",
    Copy.ProfileLocationContacts to "লোকেশন ও কন্টাক্ট",
    Copy.ProfileLogOut to "লগ আউট",
    Copy.DeleteAccountTitle to "অ্যাকাউন্ট ডিলিট",
    Copy.DeleteAccountHeading to "আপনার {app} অ্যাকাউন্ট ডিলিট করবেন?",
    Copy.DeleteAccountBody to
        "আপনার প্রোফাইল, পোস্ট, ফলোয়ার, মেসেজ ও ব্যালেন্স বন্ধ হয়ে যাবে এবং কেউ আর দেখতে পাবে না।",
    Copy.DeleteAccountRecoverNote to
        "৪৮ ঘণ্টা পর আপনার অ্যাকাউন্ট ডিলিট হবে। এখনই আপনাকে লগ আউট করা হবে। মত পরিবর্তন হলে " +
        "৪৮ ঘণ্টার মধ্যে আবার লগ ইন করুন, অ্যাকাউন্ট থেকে যাবে।",
    Copy.DeleteAccountConfirmLabel to "আমি বুঝেছি আমার অ্যাকাউন্ট ডিলিট হবে",
    Copy.DeleteAccountCta to "আমার অ্যাকাউন্ট ডিলিট করুন",
    Copy.DeleteAccountCancel to "অ্যাকাউন্ট রেখে দিন",
    Copy.DeleteAccountDone to
        "৪৮ ঘণ্টা পর আপনার অ্যাকাউন্ট ডিলিট হবে। রাখতে চাইলে ৪৮ ঘণ্টার মধ্যে আবার লগ ইন করুন।",
    Copy.ProfileEditTitle to "প্রোফাইল এডিট",
    Copy.ProfileChangePhoto to "ছবি বদলান",
    Copy.ProfileNameLabel to "নাম",
    Copy.ProfileUsername to "ইউজারনেম",
    Copy.ProfileGender to "লিঙ্গ",
    Copy.ProfileGenderMale to "পুরুষ",
    Copy.ProfileGenderFemale to "নারী",
    Copy.ProfileGenderPreferNot to "বলতে চাই না",
    Copy.ProfileWhatsOnMind to "কী ভাবছেন?",
    Copy.ProfileCopied to "কপি হয়েছে",
    Copy.ProfileUsernameLocked to "ইউজারনেম প্রতি {n} দিনে একবার বদলানো যায়",
    Copy.ProfileUsernameAgainIn to "ইউজারনেম আবার বদলাতে {n} দিন বাকি",
    Copy.ProfileUsernameAvailable to "@{u} খালি আছে",
    Copy.ProfileUsernameTaken to "@{u} আগেই নেওয়া",
    Copy.ProfileUsernameRule to "৩–২৪ অক্ষর: অক্ষর, সংখ্যা, ডট বা আন্ডারস্কোর",
    Copy.ProfileUsernameChecking to "যাচাই হচ্ছে…",
    Copy.ProfileRestricted to "সীমিত",
    Copy.ProfileSaveFailed to "সেভ হয়নি। প্রোফাইল আগের অবস্থায় ফিরিয়েছি।",
    Copy.ProfileNoPosts to "এখনো কোনো পোস্ট নেই",
    Copy.ProfileNoLiked to "লাইক করা ভিডিও এখানে দেখা যাবে",
    Copy.ProfileNoSaved to "এখনো কিছু সেভ করা হয়নি",
    Copy.ProfileNoPrivate to "কোনো প্রাইভেট পোস্ট নেই",
    Copy.HomePosting to "পোস্ট হচ্ছে…",
    Copy.HomeFailed to "প্রসেস করা যায়নি",
    Copy.GiftRecordTitle to "গিফট রেকর্ড",
    Copy.GiftRecordToday to "আজ",
    Copy.GiftRecordWeek to "এই সপ্তাহ",
    Copy.GiftRecordMonth to "এই মাস",
    Copy.GiftRecordCustom to "তারিখ",
    Copy.GiftRecordSent to "পাঠানো",
    Copy.GiftRecordReceived to "পাওয়া",
    Copy.GiftRecordGifts to "{n} গিফট",
    Copy.GiftRecordSenderLevel to "প্রেরকের লেভেল",
    Copy.GiftRecordReceiverLevel to "গ্রহীতার লেভেল",
    Copy.GiftRecordNoLevel to "এখনো কোনো লেভেল নেই",
    Copy.GiftRecordLevelN to "লেভেল {n}",
    Copy.GiftRecordToNext to "পরের লেভেলে {n} কয়েন",
    Copy.GiftRecordTop to "সর্বোচ্চ লেভেল",
    Copy.GiftRecordRange to "{from} – {to}",
    Copy.GiftRecordByDay to "দিন অনুযায়ী",
    Copy.GiftRecordEmpty to "এই সময়ে কোনো গিফট নেই।",
    Copy.GiftRecordFrom to "থেকে (YYYY-MM-DD)",
    Copy.GiftRecordTo to "পর্যন্ত (YYYY-MM-DD)",
    Copy.GiftRecordApply to "দেখান",
    Copy.GiftRecordBadDate to "তারিখ YYYY-MM-DD আকারে লিখুন।",
    Copy.RewardsTitle to "আমার রিওয়ার্ড",
    Copy.RewardsToday to "আজ",
    Copy.RewardsByDay to "দিন অনুযায়ী",
    Copy.RewardsEmpty to "এখনো কোনো রিওয়ার্ড নেই। লাইভ করলে সময় থেকে রিওয়ার্ড জমে।",
    Copy.RewardsLoadFailed to "রিওয়ার্ড লোড হয়নি।",
    Copy.RewardsLiveTime to "লাইভ সময়",
    Copy.RewardsAdded to "ব্যালেন্সে যোগ হয়েছে",
    Copy.RewardsPending to "অপেক্ষমাণ",
    Copy.RewardsClaimable to "নেওয়ার জন্য প্রস্তুত",
    Copy.RewardsClaim to "{amt} নিন",
    Copy.RewardsClaimed to "{amt} ব্যালেন্সে যোগ হয়েছে",
    Copy.RewardsClaimWaiting to "{amt} নেওয়ার সময়ের অপেক্ষায়",
    Copy.RewardsClaimOpens to "{t}-এ খুলবে",
    Copy.RewardsHistory to "ইতিহাস",
    Copy.RewardsOlder to "আগের রিওয়ার্ড",
    Copy.RewardsStatusClaimed to "নেওয়া হয়েছে",
    Copy.RewardsCompletedAt to "সম্পন্ন হয়েছে",
    Copy.RewardsAddedAt to "যোগ হয়েছে",
    Copy.RewardsBalance to "ব্যালেন্স",
    Copy.RewardsRequired to "প্রয়োজন",
    Copy.RewardsVerified to "যাচাইকৃত",
    Copy.RewardsDay to "রিওয়ার্ডের দিন",
    Copy.RewardsRole to "হিসেবে",
    Copy.RewardsHost to "হোস্ট",
    Copy.RewardsTimeline to "টাইমলাইন",
    Copy.RewardsDetail to "রিওয়ার্ড",
    Copy.RewardsNoRewardsDay to "লাইভ সময় আছে, রিওয়ার্ড পৌঁছায়নি",
    Copy.RewardsDayNote to "রিওয়ার্ডের দিন {tz}-এ {start} থেকে শুরু, লাইভ সময়ের একই ক্যালেন্ডার। দেখানো সময় {tz}-এ।",
    Copy.RewardsOff to "লাইভ রিওয়ার্ড এখন বন্ধ।",
    Copy.TxnRetry to "আবার চেষ্টা",
    Copy.MyLiveCohost to "সহ-হোস্ট",
    Copy.RolesTitle to "আমার রোল",
    Copy.RolesTabMine to "রোল",
    Copy.RolesTabRequests to "অনুরোধ",
    Copy.RolesTabApprovals to "অনুমোদন",
    Copy.RolesMineEmpty to "এখনো কোনো রোল নেই। রেফারেল কোড থাকলে নিচে দিন।",
    Copy.RolesJoinTitle to "রেফারেল কোড দিয়ে যোগ দিন",
    Copy.RolesJoinHint to "কোড যিনি দিয়েছেন তিনি আপনার আপলাইন হবেন এবং আবেদন অনুমোদন করবেন।",
    Copy.RolesCodePlaceholder to "রেফারেল কোড",
    Copy.RolesPickRole to "যে রোলে আবেদন",
    Copy.RolesNotePlaceholder to "আপলাইনের জন্য নোট (ঐচ্ছিক)",
    Copy.RolesApply to "আবেদন",
    Copy.RolesApplied to "আবেদন পাঠানো হয়েছে। পর্যালোচনার পর জানানো হবে।",
    Copy.FaceTitle to "মুখ যাচাই",
    Copy.FaceIntro to "ডিম্বাকৃতির ভেতরে মুখ রাখুন। সব ঠিক থাকলে স্বয়ংক্রিয়ভাবে ছবি উঠবে।",
    Copy.FaceHintCenter to "মুখটি ফ্রেমের মাঝে রাখুন",
    Copy.FaceHintCloser to "আরও কাছে আসুন",
    Copy.FaceHintBack to "একটু দূরে যান",
    Copy.FaceHintLight to "আরও আলোতে যান",
    Copy.FaceHintStill to "স্থির থাকুন…",
    Copy.FaceHintOne to "ফ্রেমে শুধু আপনি থাকুন",
    Copy.FaceHintFront to "সরাসরি ক্যামেরার দিকে তাকান",
    Copy.FaceTurnLeft to "ধীরে মাথা বাঁ দিকে ঘোরান",
    Copy.FaceTurnRight to "ধীরে মাথা ডান দিকে ঘোরান",
    Copy.FaceCapture to "ছবি তুলুন",
    Copy.FaceChecking to "যাচাই হচ্ছে…",
    Copy.FaceOkVerified to "মুখ যাচাই সম্পন্ন",
    Copy.RewardsNotEligibleFace to "মুখ যাচাই হয়নি — পুরস্কার পেতে মুখ যাচাই করুন",
    Copy.RewardsHudFaceNeeded to "মুখ যাচাই হয়নি · পুরস্কার যোগ হবে না",
    Copy.RewardsHudFaceNotAdded to "মুখ যাচাই হয়নি · {amt} যোগ হয়নি",
    Copy.FacePendingMsg to "জমা হয়েছে। শিগগিরই নিশ্চিত করা হবে।",
    Copy.FaceReviewMsg to "আপনার যাচাই পর্যালোচনাধীন।",
    Copy.FaceDuplicateMsg to "এই মুখ ইতিমধ্যে অন্য অ্যাকাউন্টে নিবন্ধিত।",
    Copy.FaceDuplicateRetryHint to "একটি মুখ দিয়ে সীমিত সংখ্যক অ্যাকাউন্ট যাচাই করা যায়, এবং শেষ চেষ্টায় সেই সীমা পূর্ণ ছিল। সীমা এখন আপনার অ্যাকাউন্টের অনুমতি দিলে আবার যাচাই করুন — পুনরায় পরীক্ষা করা হবে।",
    Copy.FaceRecheckCta to "আবার যাচাই করুন",
    Copy.FaceDuplicateLimitHint to "প্রতি মুখে অনুমোদিত অ্যাকাউন্ট: {limit}টি। এই মুখ ইতিমধ্যে {used}টি অ্যাকাউন্টে ব্যবহৃত, আরও {remaining}টি যাচাই করা যাবে। আপনি এখন এই অ্যাকাউন্ট যাচাই করতে পারেন।",
    Copy.FaceDuplicateFullHint to "প্রতি মুখে অনুমোদিত অ্যাকাউন্ট: {limit}টি, এবং এই মুখ ইতিমধ্যে {used}টি অ্যাকাউন্ট যাচাই করেছে। এই মুখ দিয়ে আর কোনো অ্যাকাউন্ট যাচাই করা যাবে না।",
    Copy.FaceMismatchMsg to "এটি এই অ্যাকাউন্টের যাচাইকৃত মুখের সাথে মেলে না।",
    Copy.FaceSpoofMsg to "লাইভনেস পরীক্ষা ব্যর্থ। ভালো আলোতে আসল মুখ ব্যবহার করুন।",
    Copy.FaceQualityMsg to "ছবির মান খুব কম। ভালো আলোতে আবার চেষ্টা করুন।",
    Copy.FaceChallengeFailMsg to "নড়াচড়া নিশ্চিত করা যায়নি। আবার চেষ্টা করুন।",
    Copy.FaceErrorMsg to "যাচাই ব্যস্ত। একটু পরে চেষ্টা করুন।",
    Copy.FaceRequiredMsg to "এই ভূমিকার জন্য মুখ যাচাই প্রয়োজন।",
    Copy.FaceRetry to "আবার চেষ্টা করুন",
    Copy.FaceClose to "বন্ধ করুন",
    Copy.FaceVerifyCta to "মুখ যাচাই করুন",
    Copy.FaceGateHint to "আবেদনের আগে এই ভূমিকার জন্য একটি দ্রুত মুখ যাচাই প্রয়োজন।",
    Copy.FaceStatusNone to "যাচাই হয়নি",
    Copy.FaceStatusPending to "নিশ্চিতকরণ বাকি",
    Copy.FaceStatusReview to "পর্যালোচনাধীন",
    Copy.FaceStatusDuplicate to "অ্যাকাউন্ট সীমা পূর্ণ",
    Copy.FaceStatusCanRetry to "যাচাই করা সম্ভব",
    Copy.FaceStatusRejected to "অনুমোদিত হয়নি",
    Copy.FaceStatusOff to "এখন উপলব্ধ নয়",
    Copy.FaceStandaloneIntro to "প্রোফাইলে যাচাইকৃত ব্যাজ পেতে একবার মুখ যাচাই করুন। একই যাচাই যেকোনো ভূমিকা বা পুরস্কারের জন্য প্রযোজ্য যেখানে যাচাইকৃত মুখ প্রয়োজন।",
    Copy.FaceVerifiedHint to "আপনার মুখ যাচাই হয়েছে। ব্যাজটি প্রোফাইলে দেখা যাচ্ছে এবং প্রয়োজনীয় সব ভূমিকা বা পুরস্কার এর আওতায় আছে।",
    Copy.FaceRejectedHint to "আপনার শেষ যাচাই অনুমোদিত হয়নি। ভালো আলোয় মুখ মাঝখানে রেখে আবার চেষ্টা করুন।",
    Copy.FaceOffHint to "মুখ যাচাই এই মুহূর্তে বন্ধ আছে। পরে আবার দেখুন।",
    Copy.FacePermMsg to "মুখ যাচাইয়ের জন্য ক্যামেরার অনুমতি প্রয়োজন। কিছুই রেকর্ড হয় না — শুধু একটি ছবি যাচাই করা হয়।",
    Copy.FacePermGrant to "ক্যামেরার অনুমতি দিন",
    Copy.FacePermSettings to "সেটিংস খুলুন",
    Copy.RolesMyCodes to "আমার রেফারেল কোড",
    Copy.RolesCopied to "কোড কপি হয়েছে",
    Copy.RolesCopyCode to "কোড কপি",
    Copy.RolesShareLink to "লিংক শেয়ার",
    Copy.RolesLinkCopied to "লিংক কপি হয়েছে",
    Copy.RolesUpline to "আপলাইন",
    Copy.RolesDownline to "ডাউনলাইন",
    Copy.RolesDirect to "সরাসরি",
    Copy.RolesWhole to "সবাই",
    Copy.RolesDownlineEmpty to "এখনো কেউ নেই। রেফারেল কোড শেয়ার করুন।",
    Copy.RolesApprove to "অনুমোদন",
    Copy.RolesReject to "প্রত্যাখ্যান",
    Copy.RolesCancel to "অনুরোধ বাতিল",
    Copy.RolesWaiting to "অনুমোদনের অপেক্ষায়",
    Copy.RolesAddMember to "সদস্য যোগ",
    Copy.RolesRequestsEmpty to "এখনো কোনো অনুরোধ নেই।",
    Copy.RolesApprovalsEmpty to "আপনার জন্য কিছু অপেক্ষা করছে না।",
    Copy.RolesLoadFailed to "লোড হয়নি। সংযোগ দেখুন।",
    Copy.RolesYourCode to "আপনার কোড",
    Copy.StoreKindFrames to "ফ্রেম",
    Copy.StoreKindBadges to "ব্যাজ",
    Copy.StoreKindEntrance to "এন্ট্রান্স",
    Copy.StoreKindVip to "ভিআইপি",
    Copy.StoreKindBubbles to "চ্যাট বাবল",
    Copy.StoreTitle to "স্টোর",
    Copy.StoreTabAll to "সব {name}",
    Copy.StoreTabMine to "আমার {name}",
    Copy.StoreFree to "ফ্রি",
    Copy.StoreNew to "নতুন",
    Copy.StoreOwned to "কেনা",
    Copy.StoreWearing to "এখন পরানো",
    Copy.StoreParked to "কেনা · চালু নয়",
    Copy.StoreLapsed to "মেয়াদ শেষ",
    Copy.StoreMineEmpty to "আপনার কোনো {name} নেই।",
    Copy.StoreShopEmpty to "{name}-এ এখন কিছু নেই।",
    Copy.StoreFreeEmpty to "এখনো ফ্রি কিছু নেই।",
    Copy.StoreFreeSub to "ওয়েলকাম প্যাক, রোল প্যাক ও টিমের উপহার",
    Copy.FramesBuy to "কিনুন · {n} কয়েন",
    Copy.FramesActivate to "চালু করুন",
    Copy.FramesActive to "চালু",
    Copy.FramesExpired to "মেয়াদ শেষ",
    Copy.FramesPermanent to "স্থায়ী",
    Copy.FramesInsufficient to "কয়েন যথেষ্ট নয়।",
    Copy.FramesDays to "{n} দিন",
    Copy.FramesDay1 to "১ দিন",
    Copy.FramesWeek1 to "১ সপ্তাহ",
    Copy.FramesMonth1 to "১ মাস",
    Copy.FramesMonths3 to "৩ মাস",
    Copy.FramesDaysLeft to "{n}দি বাকি",
    Copy.FramesPickDuration to "মেয়াদ বেছে নিন",
    Copy.InboxChats to "চ্যাট",
    Copy.InboxNotifications to "নোটিফিকেশন",
    Copy.InboxActivity to "অ্যাক্টিভিটি",
    Copy.InboxSearch to "চ্যাট ও মানুষ খুঁজুন",
    Copy.InboxOnline to "অনলাইন",
    Copy.InboxTyping to "লিখছেন…",
    Copy.InboxPinned to "পিন করা",
    Copy.InboxAll to "সব বার্তা",
    Copy.InboxArchived to "আর্কাইভ",
    Copy.InboxNewChat to "নতুন চ্যাট",
    Copy.InboxCancel to "বাতিল",
    Copy.InboxFollow to "ফলো",
    Copy.InboxNewFollowers to "নতুন ফলোয়ার",
    Copy.InboxSystem to "সিস্টেম",
    Copy.InboxNoResults to "\"{q}\"-এর কোনো ফল নেই",
    Copy.InboxPeople to "মানুষ",
    Copy.InboxFollowedYou to "আপনাকে ফলো করা শুরু করেছেন",
    Copy.InboxVideoRestrictedTitle to "ভিডিও সীমিত",
    Copy.InboxVideoRestrictedBody to "নিয়ম ভঙ্গের জন্য আপনার ভিডিও সীমিত করা হয়েছে। ভুল মনে হলে আবার পর্যালোচনা চাইতে পারেন।",
    Copy.InboxOk to "ঠিক আছে",
    Copy.ActivityEmpty to "এখনো কিছু নেই। কেউ আপনার স্ট্যাটাস দেখলে এখানে দেখা যাবে।",
    Copy.StoryViewedYours to "আপনার স্ট্যাটাস দেখেছেন",
    Copy.ActivityCommented to "আপনার পোস্টে মন্তব্য করেছেন",
    Copy.ActivityReplied to "আপনার মন্তব্যের উত্তর দিয়েছেন",
    Copy.ActivityCommentLiked to "আপনার মন্তব্যে লাইক করেছেন",
    Copy.InboxJustNow to "এইমাত্র",
    Copy.InboxLastSeen to "শেষ দেখা {t} আগে",
    Copy.InboxOffline to "অফলাইন",
    Copy.InboxChatOpenFail to "এই চ্যাট খোলা যায়নি। আবার চেষ্টা করুন।",
    Copy.InboxNewGroup to "নতুন গ্রুপ",
    Copy.InboxCreateGroup to "গ্রুপ চ্যাট তৈরি",
    Copy.InboxSearchShort to "খুঁজুন",
    Copy.InboxRecents to "সাম্প্রতিক",
    Copy.InboxCreate to "তৈরি",
    Copy.InboxGroupName to "গ্রুপের নাম",
    Copy.InboxInviteLink to "ইনভাইট লিংক",
    Copy.InboxCopyAction to "কপি",
    Copy.InboxMessage to "বার্তা",
    Copy.InboxPin to "পিন",
    Copy.InboxUnpin to "আনপিন",
    Copy.InboxMute to "মিউট",
    Copy.InboxUnmute to "আনমিউট",
    Copy.InboxArchive to "আর্কাইভ",
    Copy.InboxUnarchive to "আনআর্কাইভ",
    Copy.InboxBlock to "ব্লক",
    Copy.InboxUnblock to "আনব্লক",
    Copy.InboxDelete to "চ্যাট মুছুন",
    Copy.InboxSupportRepliedTitle to "সাপোর্ট উত্তর দিয়েছে",
    Copy.RolesNoticeRequest to "রোল অনুরোধ",
    Copy.RolesNoticeDecision to "রোল অনুরোধের সিদ্ধান্ত",
    Copy.RolesNoticeChange to "আপনার রোল বদলেছে",
    Copy.ProfileBalance to "ব্যালেন্স",
    Copy.ProfileTxnHistory to "লেনদেনের ইতিহাস",
    Copy.ProfileReportProblem to "সমস্যা জানান",
    Copy.ProfileDrawerSettings to "সেটিংস",
    Copy.ProfileDrawerSupport to "সাপোর্ট",
    Copy.ProfileDrawerLegal to "আইনি",
    Copy.BalanceTitle to "আমার ব্যালেন্স",
    Copy.BalanceCoins to "কয়েন",
    Copy.BalanceDiamonds to "ডায়মন্ড",
    Copy.BalanceBeans to "বিন",
    Copy.BalanceRecharge to "রিচার্জ",
    Copy.BalanceWithdraw to "উত্তোলন",
    Copy.BalanceHistory to "ইতিহাস",
    Copy.BalancePending to "{n}টি রিচার্জ অনুমোদনের অপেক্ষায়",
    Copy.BalanceRecent to "সাম্প্রতিক রিচার্জ",
    Copy.RechargeTitle to "কয়েন রিচার্জ",
    Copy.RechargeTabPackages to "প্যাকেজ",
    Copy.RechargeTabRequests to "আমার অনুরোধ",
    Copy.RechargePickPackage to "কত কয়েন কিনবেন বেছে নিন",
    Copy.RechargeNoPackages to "আপনার দেশে এখনো কোনো প্যাকেজ নেই।",
    Copy.RechargePickMethod to "পেমেন্ট কোথায় পাঠাবেন বেছে নিন",
    Copy.RechargeNoMethods to "আপনার দেশে এখনো কোনো পেমেন্ট পদ্ধতি নেই।",
    Copy.RechargeBonus to "+{n} বোনাস",
    Copy.RechargeSendExactly to "ঠিক এই পরিমাণ পাঠান",
    Copy.RechargeYouGet to "আপনি পাবেন",
    Copy.RechargeAccountTitle to "অ্যাকাউন্টের নাম",
    Copy.RechargeAccountNo to "অ্যাকাউন্ট নম্বর",
    Copy.RechargeCopy to "কপি",
    Copy.RechargeCopied to "কপি হয়েছে",
    Copy.RechargeInstructions to "নির্দেশনা",
    Copy.RechargeProof to "পেমেন্টের স্ক্রিনশট",
    Copy.RechargeProofHint to "পেমেন্টের পর রসিদ যোগ করুন, যাতে আমরা মিলিয়ে দেখতে পারি।",
    Copy.RechargeAttach to "স্ক্রিনশট যোগ",
    Copy.RechargeChange to "বদলান",
    Copy.RechargeNote to "বার্তা (ঐচ্ছিক)",
    Copy.RechargeSubmit to "অনুমোদনের জন্য পাঠান",
    Copy.RechargeNeedProof to "আগে পেমেন্টের স্ক্রিনশট যোগ করুন।",
    Copy.RechargeDone to "পাঠানো হয়েছে। পেমেন্ট নিশ্চিত হলে কয়েন যোগ হবে।",
    Copy.RechargeNoRequests to "এখনো কোনো রিচার্জ অনুরোধ নেই।",
    Copy.SellerHubTitle to "কয়েন সেলার",
    Copy.SellerDrawerHub to "কয়েন সেলার",
    Copy.SellerDrawerList to "সেলারের কাছ থেকে কিনুন",
    Copy.TransferTitle to "ট্রান্সফার",
    Copy.TransferDrawer to "ট্রান্সফার",
    Copy.TransferTabSend to "পাঠান",
    Copy.TransferTabHistory to "ইতিহাস",
    Copy.TransferTabDisputes to "অভিযোগ",
    Copy.TransferFindUser to "যাকে পাঠাবেন",
    Copy.TransferFindHint to "ইউজার আইডি বা ইউজারনেম",
    Copy.TransferAmount to "পরিমাণ",
    Copy.TransferAmountHint to "কত পাঠাবেন",
    Copy.TransferLimits to "সর্বনিম্ন {min} · সর্বোচ্চ {max}",
    Copy.TransferFeeLine to "ফি: {n}",
    Copy.TransferTheyGet to "{name} পাবেন {n} {cur}",
    Copy.TransferNote to "নোট (ঐচ্ছিক)",
    Copy.TransferSendBtn to "পাঠান",
    Copy.TransferSentOk to "পাঠানো হয়েছে।",
    Copy.TransferWindowLine to "প্রতি {d} দিনে {max}-এর মধ্যে {used} ব্যবহৃত",
    Copy.TransferRecvRemaining to "তিনি আরও {n} নিতে পারবেন",
    Copy.TransferCantReceive to "এই অ্যাকাউন্ট আপনার কাছ থেকে ট্রান্সফার নিতে পারবে না।",
    Copy.TransferDisabled to "আপনার অ্যাকাউন্টে ট্রান্সফার উপলব্ধ নয়।",
    Copy.TransferNoHistory to "এখনও কোনো ট্রান্সফার নেই।",
    Copy.TransferDirSent to "পাঠানো",
    Copy.TransferDirReceived to "প্রাপ্ত",
    Copy.TransferDisputeBtn to "সমস্যা জানান",
    Copy.TransferDisputeReason to "কী সমস্যা হয়েছে? যেমন ভুল কাটা হয়েছে",
    Copy.TransferDisputeOpened to "অভিযোগ খোলা হয়েছে।",
    Copy.TransferNoDisputes to "কোনো অভিযোগ নেই।",
    Copy.TransferDisputeTitle to "অভিযোগ",
    Copy.ExchangeTitle to "এক্সচেঞ্জ",
    Copy.ExchangeDrawer to "এক্সচেঞ্জ",
    Copy.ExchangeTabConvert to "রূপান্তর",
    Copy.ExchangeTabHistory to "ইতিহাস",
    Copy.ExchangePair to "রূপান্তর",
    Copy.ExchangeAmount to "পরিমাণ",
    Copy.ExchangeAmountHint to "কত রূপান্তর করবেন",
    Copy.ExchangeRateLine to "রেট: {r}×",
    Copy.ExchangeFeeLine to "ফি: {n} ({p}%)",
    Copy.ExchangeYouGet to "আপনি পাবেন {n} {cur}",
    Copy.ExchangeBtn to "এক্সচেঞ্জ",
    Copy.ExchangeDone to "এক্সচেঞ্জ সম্পন্ন।",
    Copy.ExchangeDisabled to "আপনার অ্যাকাউন্টে এক্সচেঞ্জ উপলব্ধ নয়।",
    Copy.ExchangeNoHistory to "এখনও কোনো এক্সচেঞ্জ নেই।",
    Copy.ExchangeLimits to "সর্বনিম্ন {min} · সর্বোচ্চ {max}",
    Copy.ExchangeWindowLine to "প্রতি {d} দিনে {max}-এর মধ্যে {used} ব্যবহৃত",
    Copy.ExchangeTotal to "মোট এক্সচেঞ্জ",
    Copy.ExchangeFeesPaid to "প্রদত্ত ফি",
    Copy.LbTitle to "লিডারবোর্ড",
    Copy.LbSenders to "শীর্ষ প্রেরক",
    Copy.LbHosts to "শীর্ষ হোস্ট",
    Copy.LbAgencies to "শীর্ষ এজেন্সি",
    Copy.LbDaily to "দৈনিক",
    Copy.LbWeekly to "সাপ্তাহিক",
    Copy.LbMonthly to "মাসিক",
    Copy.LbTotal to "সর্বকাল",
    Copy.LbMembers to "{n} সদস্য",
    Copy.LbJoin to "যোগ দিন",
    Copy.LbEmpty to "এখনও কোনো র‍্যাঙ্কিং নেই।",
    Copy.PostGiftTitle to "{name}-কে উপহার পাঠান",
    Copy.PostGiftSend to "পাঠান",
    Copy.PostGiftSent to "{gift} পাঠানো হয়েছে ✓",
    Copy.PostGiftNoCoins to "পর্যাপ্ত কয়েন নেই",
    Copy.PostGiftFailed to "উপহার পাঠানো যায়নি",
    Copy.PostGiftEmpty to "এই মুহূর্তে কোনো উপহার নেই।",
    Copy.PostShareBody to "{app}-এ {name}-কে দেখুন",
    Copy.LbCreators to "শীর্ষ ক্রিয়েটর",
    Copy.LbViews to "{n} ভিউ",
    Copy.CreatorTabLive to "লাইভ",
    Copy.CreatorTabCreator to "ক্রিয়েটর",
    Copy.CreatorEarned to "ক্রিয়েটর আয়",
    Copy.CreatorGiftsLbl to "উপহার",
    Copy.CreatorBonusLbl to "বোনাস",
    Copy.CreatorClaim to "দাবি করুন",
    Copy.CreatorClaimClosed to "দাবি এখন বন্ধ।",
    Copy.CreatorHow to "কীভাবে কাজ করে",
    Copy.CreatorEmpty to "এখনও কোনো ক্রিয়েটর পুরস্কার নেই।",
    Copy.CreatorOff to "ক্রিয়েটর পুরস্কার এখন উপলব্ধ নয়।",
    Copy.CreatorKindGift to "উপহার ক্রেডিট",
    Copy.CreatorKindBonus to "মাইলফলক বোনাস",
    Copy.SellerTabOverview to "সারাংশ",
    Copy.SellerTabSend to "পাঠান",
    Copy.SellerTabRequests to "অনুরোধ",
    Copy.SellerTabHistory to "ইতিহাস",
    Copy.SellerTabTeam to "টিম",
    Copy.SellerBalance to "আপনার ব্যালেন্স",
    Copy.SellerListTitle to "কয়েন সেলার",
    Copy.SellerListEmpty to "এখন কোনো সেলার নেই।",
    Copy.SellerTabSellers to "সেলার",
    Copy.SellerTabMyRequests to "আমার অনুরোধ",
    Copy.SellerRequestBtn to "রিচার্জের অনুরোধ",
    Copy.SellerNoTransfers to "এখনো কোনো পাঠানো নেই।",
    Copy.SellerNoRequests to "এখনো কোনো অনুরোধ নেই।",
    Copy.HelpSubject to "বিষয়",
    Copy.HelpMessage to "বার্তা",
    Copy.HelpSubmit to "পাঠান",
    Copy.HelpMyReports to "আমার রিপোর্ট",
    Copy.MyLiveTitle to "আমার লাইভ",
    Copy.MyLiveToday to "আজ",
    Copy.MyLiveLiveNow to "এখন লাইভ",
    Copy.MyLiveCounting to "গণনা হচ্ছে",
    Copy.MyLivePaused to "থামানো — আবার যুক্ত হচ্ছে",
    Copy.MyLiveWeek to "এই সপ্তাহ",
    Copy.MyLiveMonth to "এই মাস",
    Copy.MyLiveLifetime to "সব সময়",
    Copy.MyLiveDays to "দিন অনুযায়ী",
    Copy.MyLiveDaysEmpty to "এই সময়ে কোনো লাইভ সময় নেই।",
    Copy.MyLiveSessions to "সেশন",
    Copy.MyLiveSessionsEmpty to "এই দিনে আপনি লাইভ হননি।",
    Copy.MyLiveSessionsCount to "{n} সেশন",
    Copy.MyLiveAudio to "অডিও",
    Copy.MyLiveDropsOnce to "১ বার পুনঃসংযোগ",
    Copy.MyLiveDrops to "{n} বার পুনঃসংযোগ",
    Copy.MyLivePausedFor to "{t} অফলাইন, গণনা হয়নি",
    Copy.MyLiveDayNote to "দিন শুরু {tz}-এ {start} থেকে। ফোন যুক্ত থাকার সময়ই গণনা হয়।",
    Copy.MyLiveEndedBy to "শেষ: {r}",
    Copy.MyLiveReasonHost to "আপনি শেষ করেছেন",
    Copy.MyLiveReasonLost to "সংযোগ বিচ্ছিন্ন",
    Copy.MyLiveReasonAppClosed to "অ্যাপ বন্ধ",
    Copy.MyLiveReasonMax to "সর্বোচ্চ সময়",
    Copy.MyLiveReasonStand to "মঞ্চ ছেড়েছেন",
    Copy.MyLiveReasonOther to "{r}",
    Copy.MyLiveLoadFailed to "লাইভ সময় লোড হয়নি।",
    Copy.MyLiveOlder to "আগের দিন",
    Copy.MyLiveRewards to "রিওয়ার্ড",
    Copy.CreatePost to "পোস্ট",
    Copy.CreateGoLive to "লাইভ",
    Copy.CreateVideo to "ভিডিও",
    Copy.CreateAudioOnly to "অডিও",
    Copy.CreateLiveVideoTitle to "ভিডিও লাইভ শুরু",
    Copy.CreateLiveAudioTitle to "অডিও রুম শুরু",
    Copy.CreateGoLiveBtn to "লাইভ শুরু",
    Copy.CreateStartAudioRoom to "অডিও রুম শুরু",

    // Paid chat & calls
    Copy.PaidTitle to "পেইড চ্যাট ও কল",
    Copy.PaidLoadFailed to "পেইড চ্যাট ও কল লোড করা যায়নি। আবার চেষ্টা করুন।",
    Copy.PaidTabOverview to "সারসংক্ষেপ",
    Copy.PaidTabMyRates to "আমার রেট",
    Copy.PaidTabHistory to "ইতিহাস",
    Copy.PaidRangeToday to "আজ",
    Copy.PaidRangeWeek to "এই সপ্তাহ",
    Copy.PaidRangeMonth to "এই মাস",
    Copy.PaidRangeAll to "সব সময়",
    Copy.PaidOff to "পেইড চ্যাট ও কল এখন বন্ধ। মেসেজ ও কল ফ্রি।",
    Copy.PaidFreeRole to "আপনার রোল চার্জমুক্ত — যে কাউকে মেসেজ বা কল করতে আপনার কিছু খরচ হয় না।",
    Copy.PaidFreeForYou to "আপনার জন্য ফ্রি",
    Copy.PaidYouPay to "আপনি দেন {rates}",
    Copy.PaidYouEarn to "আপনি পান {rates}",
    Copy.PaidChatUnlocked to "চ্যাট আনলক — এখানে মেসেজ এখন ফ্রি",
    Copy.PaidPromoLeft to "{period} আরও {m}টি ফ্রি মেসেজ ও {n} মিনিট ফ্রি কল বাকি",
    Copy.PaidPromoDay to "আজ",
    Copy.PaidPromoWeek to "এই সপ্তাহে",
    Copy.PaidPromoMonth to "এই মাসে",
    Copy.PaidPromoTag to "ফ্রি (প্রোমো)",
    Copy.PaidPromoUsed to "ব্যবহৃত প্রোমো ইউনিট",
    Copy.PaidUnitMessage to "মেসেজ",
    Copy.PaidUnitConversation to "চ্যাট",
    Copy.PaidUnitCall to "কল",
    Copy.PaidUnitMinute to "মিনিট",
    Copy.PaidClaimable to "ক্লেইমের জন্য প্রস্তুত",
    Copy.PaidClaimableCount to "{n}টি আয় অপেক্ষায়",
    Copy.PaidClaimableShort to "{n} ক্লেইম করুন",
    Copy.PaidClaimExpiry to "ক্লেইম না করা আয় {n} দিন পর মেয়াদোত্তীর্ণ হবে।",
    Copy.PaidClaimBtn to "ওয়ালেটে ক্লেইম করুন",
    Copy.PaidClaimed to "{n} ক্লেইম হয়েছে। ওয়ালেট {before} → {after}।",
    Copy.PaidEarned to "আয়",
    Copy.PaidClaimedTotal to "এ পর্যন্ত ক্লেইম",
    Copy.PaidDirectTotal to "সরাসরি যোগ হয়েছে",
    Copy.PaidExpiredTotal to "ক্লেইম না করায় মেয়াদোত্তীর্ণ",
    Copy.PaidSpentToday to "আজ খরচ",
    Copy.PaidSpentAll to "মোট খরচ",
    Copy.PaidPolicy to "আপনার জন্য কীভাবে কাজ করে",
    Copy.PaidOwnRatesLine to "আপনার রেট: {rates}",
    Copy.PaidNoRules to "এখন আপনার জন্য কোনো মূল্য প্রযোজ্য নয়।",
    Copy.PaidFeeLine to "প্ল্যাটফর্ম ফি: {fee} (প্রাপকের দিক থেকে কাটা হয়)।",
    Copy.PaidPayoutClaim to "আয় {cur} হিসেবে এখানে থাকে যতক্ষণ না আপনি ক্লেইম করেন।",
    Copy.PaidPayoutDirect to "আয় সঙ্গে সঙ্গে {cur} হিসেবে আপনার ওয়ালেটে যায়।",
    Copy.PaidRulePay to "{to}-কে যোগাযোগ করলে আপনি দেন",
    Copy.PaidRuleEarn to "{from} আপনাকে যোগাযোগ করলে আপনি পান",
    Copy.PaidRuleBoth to "দুই দিকেই প্রযোজ্য",
    Copy.PaidAnyone to "যে কেউ",
    Copy.PaidReceivedFrom to "প্রাপ্তি, চ্যাট ও দিন অনুযায়ী",
    Copy.PaidFromOn to "{name} থেকে {date}",
    Copy.PaidUnits to "{n}টি চার্জ",
    Copy.PaidEmpty to "এখনও কিছু নেই।",
    Copy.PaidLoadMore to "আরও দেখুন",
    Copy.PaidChargeMessage to "মেসেজ",
    Copy.PaidChargeUnlock to "চ্যাট আনলক",
    Copy.PaidChargeAudio to "অডিও কল",
    Copy.PaidChargeAudioMin to "অডিও কল · মিনিট {n}",
    Copy.PaidChargeVideo to "ভিডিও কল",
    Copy.PaidChargeVideoMin to "ভিডিও কল · মিনিট {n}",
    Copy.PaidStatusPaid to "যোগ হয়েছে",
    Copy.PaidStatusClaimable to "ক্লেইম করুন",
    Copy.PaidStatusClaimed to "ক্লেইম হয়েছে",
    Copy.PaidStatusExpired to "মেয়াদোত্তীর্ণ",
    Copy.PaidBeforeAfter to "ওয়ালেট {before} → {after}",
    Copy.PaidFree to "ফ্রি",
    Copy.PaidSideEarned to "আয়",
    Copy.PaidSidePaid to "খরচ",
    Copy.PaidMyRatesHint to "আপনাকে মেসেজ বা কল করতে অন্যরা কত দেবে ({cur}) তা ঠিক করুন। ফ্রি রাখতে খালি রাখুন।",
    Copy.PaidRateMax to "সর্বোচ্চ অনুমোদিত মূল্য: {n}।",
    Copy.PaidChannelMessage to "মেসেজ",
    Copy.PaidChannelAudio to "অডিও কল",
    Copy.PaidChannelVideo to "ভিডিও কল",
    Copy.PaidRatesSave to "আমার রেট সেভ করুন",
    Copy.PaidRatesClear to "আমার রেট মুছুন",
    Copy.PaidRatesSaved to "আপনার রেট সেভ হয়েছে।",
    Copy.PaidRatesCleared to "আপনার রেট মুছে ফেলা হয়েছে।",
    Copy.CallNoFundsMine to "কল শেষ — চালিয়ে যাওয়ার মতো ব্যালেন্স নেই",
    Copy.CallNoFundsTheirs to "কল শেষ — কলারের ব্যালেন্স শেষ",
    Copy.CallPaidSoFar to "{rate} · এ পর্যন্ত {n}",
    Copy.CallPromoFree to "{rate} · ফ্রি মিনিট (প্রোমো)",
    Copy.CallEarnedSoFar to "এই কলে আপনি {n} আয় করেছেন",
    Copy.TxnCatPaidContactPaid to "পেইড চ্যাট ও কল",
    Copy.TxnCatPaidContactEarned to "চ্যাট ও কল আয়",
)

private val Fil = mapOf(
    Copy.SnapTermsTitle to "Mga Tuntunin ng Serbisyo",
    Copy.SnapTermsAccept to "Tanggapin",
    Copy.SnapTermsDecline to "Tanggihan",
    Copy.CreateSave to "I-save",
    Copy.CreateSavedToGallery to "Na-save sa gallery mo",
    Copy.CreateSaveFailed to "Hindi ma-save sa gallery",
    Copy.TabHome to "Home",
    Copy.TabGames to "Mga Laro",
    Copy.TabCreate to "Gumawa",
    Copy.TabInbox to "Inbox",
    Copy.TabProfile to "Profile",
    Copy.HomeForYou to "Para sa Iyo",
    Copy.HomeFollowing to "Sinusundan",
    Copy.HomeLive to "Live",
    Copy.HomeFollowEmpty to "I-follow ang mga creator para makita ang kanilang mga video",
    Copy.LiveBadge to "LIVE",
    Copy.LivePartyBadge to "PARTY",
    Copy.GamesTitle to "Mga Laro",
    Copy.GamesBody to "Malapit nang maidagdag ang mga laro mula sa iba.",
    Copy.ComingSoon to "Malapit na",
    Copy.CreateTitle to "Gumawa",
    Copy.CreateBody to "Larawan, teksto, at video — malapit na.",
    Copy.InboxTitle to "Inbox",
    Copy.InboxBody to "Mga chat, abiso, at aktibidad — malapit na.",
    Copy.ProfileCopy to "Kopyahin",
    Copy.ProfileVerified to "Beripikado",
    Copy.ProfileFollowing to "Sinusundan",
    Copy.ProfileFollowers to "Mga Follower",
    Copy.ProfileLikes to "Mga Like",
    Copy.ProfileCoins to "Mga Coin",
    Copy.ProfileDiamonds to "Mga Diamond",
    Copy.ProfileEdit to "I-edit ang profile",
    Copy.ProfileShare to "Ibahagi",
    Copy.ProfileLatest to "Pinakabago",
    Copy.ProfilePopular to "Sikat",
    Copy.ProfileSearch to "Maghanap sa profile, mga post, at tunog…",
    Copy.ProfileName to "Pangalan mo",
    Copy.ProfileStatus to "Nabubuhay sa sandaling ito",
    Copy.ProfileBio to "Creator · Mananayaw · Mapangarapin",
    Copy.ProfileSave to "I-save",
    Copy.FeatureUnavailable to "Hindi available ang bahaging ito sa ngayon.",
    Copy.LiveStickerSentLine to "nagpadala ng sticker kay {to}",
    Copy.YouHandle to "@you_{wordmark}",
    Copy.ProfileSettings to "Mga Setting",
    Copy.ProfileDrawerAccount to "Account",
    Copy.ProfileSwitchAccounts to "Magpalit ng account",
    Copy.ProfileActivatePassword to "I-on ang password login",
    Copy.ProfileChangePassword to "Palitan ang password",
    Copy.FindFriendsTitle to "Maghanap ng kaibigan",
    Copy.ProfileWallet to "Wallet / Mga Coin",
    Copy.TxnTitle to "Kasaysayan ng transaksyon",
    Copy.WithdrawTitle to "I-withdraw ang mga diamond",
    Copy.ProfilePrivacySafety to "Privacy at Kaligtasan",
    Copy.ProfilePrivacy to "Privacy",
    Copy.ProfileDms to "Direktang mensahe",
    Copy.ProfileBlocked to "Mga naka-block na account",
    Copy.ProfileSafeMode to "Safe mode",
    Copy.ProfileContentDisplay to "Content at Display",
    Copy.ProfileDisplay to "Display",
    Copy.ProfileLanguage to "Wika",
    Copy.CreateDone to "Tapos",
    Copy.ProfileTranslation to "Pagsasalin",
    Copy.ProfileNotifications to "Mga Abiso",
    Copy.ProfileSupportLegal to "Suporta at Patakaran",
    Copy.ProfileHelpCentre to "Help Center",
    Copy.ProfileTerms to "Mga Tuntunin at Patakaran",
    Copy.ProfileLocationContacts to "Lokasyon at Mga Contact",
    Copy.ProfileLogOut to "Mag-log out",
    Copy.DeleteAccountTitle to "I-delete ang account",
    Copy.DeleteAccountHeading to "I-delete ang {app} account mo?",
    Copy.DeleteAccountBody to
        "Isasara ang profile, posts, followers, mensahe at balanse mo at hindi na " +
        "makikita ninuman.",
    Copy.DeleteAccountRecoverNote to
        "Ide-delete ang account mo pagkatapos ng 48 oras. Mala-log out ka ngayon. Kung " +
        "magbago ang isip mo, mag-log in lang muli sa loob ng 48 oras at mananatili ang account mo.",
    Copy.DeleteAccountConfirmLabel to "Naiintindihan ko na ide-delete ang account ko",
    Copy.DeleteAccountCta to "I-delete ang account ko",
    Copy.DeleteAccountCancel to "Itago ang account ko",
    Copy.DeleteAccountDone to
        "Ide-delete ang account mo pagkatapos ng 48 oras. Mag-log in sa loob ng 48 oras para itago ito.",
    Copy.ProfileEditTitle to "I-edit ang profile",
    Copy.ProfileChangePhoto to "Palitan ang larawan",
    Copy.ProfileNameLabel to "Pangalan",
    Copy.ProfileUsername to "Username",
    Copy.ProfileGender to "Kasarian",
    Copy.ProfileGenderMale to "Lalaki",
    Copy.ProfileGenderFemale to "Babae",
    Copy.ProfileGenderPreferNot to "Ayaw sabihin",
    Copy.ProfileWhatsOnMind to "Ano ang iniisip mo?",
    Copy.ProfileCopied to "Nakopya",
    Copy.ProfileUsernameLocked to "Maaaring palitan ang username isang beses bawat {n} araw",
    Copy.ProfileUsernameAgainIn to "Maaari mong palitan ang username pagkalipas ng {n} araw",
    Copy.ProfileUsernameAvailable to "Available ang @{u}",
    Copy.ProfileUsernameTaken to "Ginagamit na ang @{u}",
    Copy.ProfileUsernameRule to "3–24 character: letra, numero, tuldok, o underscore",
    Copy.ProfileUsernameChecking to "Tinitingnan…",
    Copy.ProfileRestricted to "Restricted",
    Copy.ProfileSaveFailed to "Hindi na-save. Ibinalik ang profile.",
    Copy.ProfileNoPosts to "Wala pang post",
    Copy.ProfileNoLiked to "Dito lalabas ang mga video na ni-like mo",
    Copy.ProfileNoSaved to "Wala pang naka-save",
    Copy.ProfileNoPrivate to "Walang pribadong post",
    Copy.HomePosting to "Pinopost…",
    Copy.HomeFailed to "Hindi naproseso",
    Copy.GiftRecordTitle to "Talaan ng regalo",
    Copy.GiftRecordToday to "Ngayon",
    Copy.GiftRecordWeek to "Ngayong linggo",
    Copy.GiftRecordMonth to "Ngayong buwan",
    Copy.GiftRecordCustom to "Mga petsa",
    Copy.GiftRecordSent to "Ipinadala",
    Copy.GiftRecordReceived to "Natanggap",
    Copy.GiftRecordGifts to "{n} regalo",
    Copy.GiftRecordSenderLevel to "Level ng nagpadala",
    Copy.GiftRecordReceiverLevel to "Level ng tumanggap",
    Copy.GiftRecordNoLevel to "Wala pang level",
    Copy.GiftRecordLevelN to "Level {n}",
    Copy.GiftRecordToNext to "{n} coin sa susunod na level",
    Copy.GiftRecordTop to "Naabot na ang pinakamataas na level",
    Copy.GiftRecordRange to "{from} – {to}",
    Copy.GiftRecordByDay to "Ayon sa araw",
    Copy.GiftRecordEmpty to "Walang regalo sa panahong ito.",
    Copy.GiftRecordFrom to "Mula (YYYY-MM-DD)",
    Copy.GiftRecordTo to "Hanggang (YYYY-MM-DD)",
    Copy.GiftRecordApply to "Ipakita",
    Copy.GiftRecordBadDate to "Ilagay ang petsa bilang YYYY-MM-DD.",
    Copy.RewardsTitle to "Aking mga reward",
    Copy.RewardsToday to "Ngayon",
    Copy.RewardsByDay to "Ayon sa araw",
    Copy.RewardsEmpty to "Wala pang reward. Mag-live at kikitain ito ng oras mo sa ere.",
    Copy.RewardsLoadFailed to "Hindi ma-load ang mga reward.",
    Copy.RewardsLiveTime to "Oras ng live",
    Copy.RewardsAdded to "Idinagdag sa balanse",
    Copy.RewardsPending to "Naghihintay",
    Copy.RewardsClaimable to "Puwedeng kunin",
    Copy.RewardsClaim to "Kunin ang {amt}",
    Copy.RewardsClaimed to "Idinagdag ang {amt} sa balanse",
    Copy.RewardsClaimWaiting to "Hinihintay ng {amt} ang oras ng pagkuha",
    Copy.RewardsClaimOpens to "bubukas {t}",
    Copy.RewardsHistory to "Kasaysayan",
    Copy.RewardsOlder to "Naunang reward",
    Copy.RewardsStatusClaimed to "Nakuha na",
    Copy.RewardsCompletedAt to "Natapos noong",
    Copy.RewardsAddedAt to "Idinagdag noong",
    Copy.RewardsBalance to "Balanse",
    Copy.RewardsRequired to "Kailangan",
    Copy.RewardsVerified to "Na-verify",
    Copy.RewardsDay to "Araw ng reward",
    Copy.RewardsRole to "Bilang",
    Copy.RewardsHost to "Host",
    Copy.RewardsTimeline to "Timeline",
    Copy.RewardsDetail to "Reward",
    Copy.RewardsNoRewardsDay to "May oras ng live, hindi umabot sa reward",
    Copy.RewardsDayNote to "Nagsisimula ang araw ng reward sa {start} sa {tz}, kapareho ng kalendaryo ng live. Ang oras ay nasa {tz}.",
    Copy.RewardsOff to "Naka-pause ang live reward ngayon.",
    Copy.TxnRetry to "Subukan ulit",
    Copy.MyLiveCohost to "Co-host",
    Copy.RolesTitle to "Aking mga role",
    Copy.RolesTabMine to "Mga role",
    Copy.RolesTabRequests to "Mga request",
    Copy.RolesTabApprovals to "Mga approval",
    Copy.RolesMineEmpty to "Wala ka pang role. May referral code? Ilagay sa ibaba.",
    Copy.RolesJoinTitle to "Sumali gamit ang referral code",
    Copy.RolesJoinHint to "Ang nagbigay ng code ang magiging upline mo at mag-aapruba ng application.",
    Copy.RolesCodePlaceholder to "Referral code",
    Copy.RolesPickRole to "Mag-apply bilang",
    Copy.RolesNotePlaceholder to "Tala para sa upline (opsyonal)",
    Copy.RolesApply to "Mag-apply",
    Copy.RolesApplied to "Naipadala ang application. Aabisuhan ka pagkatapos suriin.",
    Copy.FaceTitle to "Face verification",
    Copy.FaceIntro to "Ilagay ang mukha mo sa loob ng oval. Awtomatikong kukunan kapag maayos na ang lahat.",
    Copy.FaceHintCenter to "Igitna ang mukha mo sa oval",
    Copy.FaceHintCloser to "Lumapit pa",
    Copy.FaceHintBack to "Lumayo nang kaunti",
    Copy.FaceHintLight to "Humanap ng mas maliwanag na lugar",
    Copy.FaceHintStill to "Huwag gumalaw…",
    Copy.FaceHintOne to "Ikaw lang dapat ang nasa frame",
    Copy.FaceHintFront to "Tumingin nang diretso sa camera",
    Copy.FaceTurnLeft to "Dahan-dahang ilingon ang ulo pakaliwa",
    Copy.FaceTurnRight to "Dahan-dahang ilingon ang ulo pakanan",
    Copy.FaceCapture to "Kunan",
    Copy.FaceChecking to "Bine-verify…",
    Copy.FaceOkVerified to "Na-verify ang mukha",
    Copy.RewardsNotEligibleFace to "Hindi pa na-verify ang mukha — i-verify ang iyong mukha para makatanggap ng rewards",
    Copy.RewardsHudFaceNeeded to "Hindi pa na-verify ang mukha · hindi maidadagdag ang rewards",
    Copy.RewardsHudFaceNotAdded to "Hindi pa na-verify ang mukha · hindi naidagdag ang {amt}",
    Copy.FacePendingMsg to "Naipasa na. Kukumpirmahin namin kaagad.",
    Copy.FaceReviewMsg to "Sinusuri pa ang iyong verification.",
    Copy.FaceDuplicateMsg to "Nakarehistro na ang mukhang ito sa ibang account.",
    Copy.FaceDuplicateRetryHint to "Limitado ang bilang ng account na puwedeng i-verify ng isang mukha, at naabot ang limit noong huli kang sumubok. Kung papayagan na ng limit ang account mo, mag-verify ulit at ire-recheck ito.",
    Copy.FaceRecheckCta to "I-verify ulit",
    Copy.FaceDuplicateLimitHint to "Accounts na pinapayagan bawat mukha: {limit}. Ginagamit na ng {used} account ang mukhang ito, kaya {remaining} pa ang puwedeng i-verify. Puwede mo nang i-verify ang account na ito.",
    Copy.FaceDuplicateFullHint to "Accounts na pinapayagan bawat mukha: {limit}, at na-verify na ng mukhang ito ang {used} account. Wala nang ibang account na puwedeng i-verify gamit ang mukhang ito.",
    Copy.FaceMismatchMsg to "Hindi ito tugma sa na-verify na mukha ng account na ito.",
    Copy.FaceSpoofMsg to "Nabigo ang liveness check. Gamitin ang totoong mukha sa maliwanag na lugar.",
    Copy.FaceQualityMsg to "Masyadong mababa ang kalidad ng larawan. Subukan ulit sa mas maliwanag.",
    Copy.FaceChallengeFailMsg to "Hindi nakumpirma ang galaw. Subukan ulit.",
    Copy.FaceErrorMsg to "Abala ang verification. Subukan ulit mamaya.",
    Copy.FaceRequiredMsg to "Kailangan ng face verification para sa role na ito.",
    Copy.FaceRetry to "Subukan ulit",
    Copy.FaceClose to "Isara",
    Copy.FaceVerifyCta to "I-verify ang mukha",
    Copy.FaceGateHint to "Kailangan ng mabilis na face check bago mag-apply sa role na ito.",
    Copy.FaceStatusNone to "Hindi pa na-verify",
    Copy.FaceStatusPending to "Naghihintay ng kumpirmasyon",
    Copy.FaceStatusReview to "Sinusuri",
    Copy.FaceStatusDuplicate to "Naabot na ang limit ng account",
    Copy.FaceStatusCanRetry to "Puwede nang mag-verify",
    Copy.FaceStatusRejected to "Hindi inaprubahan",
    Copy.FaceStatusOff to "Hindi available ngayon",
    Copy.FaceStandaloneIntro to "I-verify ang iyong mukha nang isang beses para makuha ang verified badge sa iyong profile. Sakop din nito ang anumang role o reward na nangangailangan ng verified na mukha.",
    Copy.FaceVerifiedHint to "Na-verify na ang iyong mukha. Makikita ang badge sa iyong profile at sakop na ang bawat role o reward na nangangailangan nito.",
    Copy.FaceRejectedHint to "Hindi inaprubahan ang huling check. Subukan ulit sa maliwanag na lugar at nakasentro ang mukha.",
    Copy.FaceOffHint to "Naka-off ang face verification sa ngayon. Bumalik na lang mamaya.",
    Copy.FacePermMsg to "Kailangan ng access sa camera para i-verify ang mukha mo. Walang nire-record — isang litrato lang ang sinusuri.",
    Copy.FacePermGrant to "Payagan ang camera",
    Copy.FacePermSettings to "Buksan ang settings",
    Copy.RolesMyCodes to "Aking mga referral code",
    Copy.RolesCopied to "Nakopya ang code",
    Copy.RolesCopyCode to "Kopyahin ang code",
    Copy.RolesShareLink to "Ibahagi ang link",
    Copy.RolesLinkCopied to "Nakopya ang link",
    Copy.RolesUpline to "Upline",
    Copy.RolesDownline to "Downline",
    Copy.RolesDirect to "Direkta",
    Copy.RolesWhole to "Lahat",
    Copy.RolesDownlineEmpty to "Wala pang nasa ilalim mo. Ibahagi ang referral code.",
    Copy.RolesApprove to "Aprubahan",
    Copy.RolesReject to "Tanggihan",
    Copy.RolesCancel to "Kanselahin ang request",
    Copy.RolesWaiting to "Hinihintay ang approval",
    Copy.RolesAddMember to "Magdagdag ng miyembro",
    Copy.RolesRequestsEmpty to "Wala pang request.",
    Copy.RolesApprovalsEmpty to "Walang hinihintay sa iyo.",
    Copy.RolesLoadFailed to "Hindi ma-load. Tingnan ang koneksyon.",
    Copy.RolesYourCode to "Iyong code",
    Copy.StoreKindFrames to "Mga Frame",
    Copy.StoreKindBadges to "Mga Badge",
    Copy.StoreKindEntrance to "Pasok",
    Copy.StoreKindVip to "VIP",
    Copy.StoreKindBubbles to "Mga chat bubble",
    Copy.StoreTitle to "Tindahan",
    Copy.StoreTabAll to "Lahat ng {name}",
    Copy.StoreTabMine to "Aking {name}",
    Copy.StoreFree to "Libre",
    Copy.StoreNew to "Bago",
    Copy.StoreOwned to "Pag-aari",
    Copy.StoreWearing to "Suot ngayon",
    Copy.StoreParked to "Pag-aari · hindi aktibo",
    Copy.StoreLapsed to "Nag-expire",
    Copy.StoreMineEmpty to "Wala ka pang {name}.",
    Copy.StoreShopEmpty to "Walang laman ang {name} ngayon.",
    Copy.StoreFreeEmpty to "Wala pang libre.",
    Copy.StoreFreeSub to "Welcome pack, role pack, at regalo ng team",
    Copy.FramesBuy to "Bumili · {n} coin",
    Copy.FramesActivate to "I-activate",
    Copy.FramesActive to "Aktibo",
    Copy.FramesExpired to "Nag-expire",
    Copy.FramesPermanent to "Permanente",
    Copy.FramesInsufficient to "Kulang ang coin.",
    Copy.FramesDays to "{n} araw",
    Copy.FramesDay1 to "1 araw",
    Copy.FramesWeek1 to "1 linggo",
    Copy.FramesMonth1 to "1 buwan",
    Copy.FramesMonths3 to "3 buwan",
    Copy.FramesDaysLeft to "{n}a natitira",
    Copy.FramesPickDuration to "Pumili ng tagal",
    Copy.InboxChats to "Mga Chat",
    Copy.InboxNotifications to "Mga Abiso",
    Copy.InboxActivity to "Aktibidad",
    Copy.InboxSearch to "Maghanap ng chat at tao",
    Copy.InboxOnline to "Online",
    Copy.InboxTyping to "nagtatype…",
    Copy.InboxPinned to "Naka-pin",
    Copy.InboxAll to "Lahat ng mensahe",
    Copy.InboxArchived to "Naka-archive",
    Copy.InboxNewChat to "Bagong chat",
    Copy.InboxCancel to "Kanselahin",
    Copy.InboxFollow to "I-follow",
    Copy.InboxNewFollowers to "Mga bagong follower",
    Copy.InboxSystem to "Sistema",
    Copy.InboxNoResults to "Walang resulta para sa \"{q}\"",
    Copy.InboxPeople to "Mga tao",
    Copy.InboxFollowedYou to "sinimulang sundan ka",
    Copy.InboxVideoRestrictedTitle to "Restricted ang video",
    Copy.InboxVideoRestrictedBody to "Na-restrict ang video mo dahil labag sa patakaran. Kung mali ito, maaari kang humiling ng review.",
    Copy.InboxOk to "OK",
    Copy.ActivityEmpty to "Wala pang laman. Kapag may tumingin sa status mo, lalabas ito dito.",
    Copy.StoryViewedYours to "tiningnan ang status mo",
    Copy.ActivityCommented to "nagkomento sa post mo",
    Copy.ActivityReplied to "sumagot sa komento mo",
    Copy.ActivityCommentLiked to "ni-like ang komento mo",
    Copy.InboxJustNow to "Ngayon lang",
    Copy.InboxLastSeen to "Huling kita {t} ang nakalipas",
    Copy.InboxOffline to "Offline",
    Copy.InboxChatOpenFail to "Hindi mabuksan ang chat. Subukan ulit.",
    Copy.InboxNewGroup to "Bagong grupo",
    Copy.InboxCreateGroup to "Gumawa ng group chat",
    Copy.InboxSearchShort to "Hanapin",
    Copy.InboxRecents to "Kamakailan",
    Copy.InboxCreate to "Gumawa",
    Copy.InboxGroupName to "Pangalan ng grupo",
    Copy.InboxInviteLink to "Link ng imbitasyon",
    Copy.InboxCopyAction to "Kopyahin",
    Copy.InboxMessage to "Mensahe",
    Copy.InboxPin to "I-pin",
    Copy.InboxUnpin to "I-unpin",
    Copy.InboxMute to "I-mute",
    Copy.InboxUnmute to "I-unmute",
    Copy.InboxArchive to "I-archive",
    Copy.InboxUnarchive to "I-unarchive",
    Copy.InboxBlock to "I-block",
    Copy.InboxUnblock to "I-unblock",
    Copy.InboxDelete to "Burahin ang chat",
    Copy.InboxSupportRepliedTitle to "Sumagot ang support",
    Copy.RolesNoticeRequest to "Request sa role",
    Copy.RolesNoticeDecision to "Napagdesisyunan ang role",
    Copy.RolesNoticeChange to "Nagbago ang role mo",
    Copy.ProfileBalance to "Balanse",
    Copy.ProfileTxnHistory to "Kasaysayan ng transaksyon",
    Copy.ProfileReportProblem to "Mag-ulat ng problema",
    Copy.ProfileDrawerSettings to "Mga Setting",
    Copy.ProfileDrawerSupport to "Suporta",
    Copy.ProfileDrawerLegal to "Legal",
    Copy.BalanceTitle to "Aking balanse",
    Copy.BalanceCoins to "Mga Coin",
    Copy.BalanceDiamonds to "Mga Diamond",
    Copy.BalanceBeans to "Beans",
    Copy.BalanceRecharge to "Mag-recharge",
    Copy.BalanceWithdraw to "Mag-withdraw",
    Copy.BalanceHistory to "Kasaysayan",
    Copy.BalancePending to "{n} recharge ang naghihintay ng approval",
    Copy.BalanceRecent to "Kamakailang recharge",
    Copy.RechargeTitle to "Mag-recharge ng coin",
    Copy.RechargeTabPackages to "Mga package",
    Copy.RechargeTabRequests to "Aking mga request",
    Copy.RechargePickPackage to "Piliin kung ilang coin ang bibilhin",
    Copy.RechargeNoPackages to "Wala pang package sa bansa mo.",
    Copy.RechargePickMethod to "Piliin kung saan ipapadala ang bayad",
    Copy.RechargeNoMethods to "Wala pang paraan ng bayad sa bansa mo.",
    Copy.RechargeBonus to "+{n} bonus",
    Copy.RechargeSendExactly to "Ipadala nang eksakto",
    Copy.RechargeYouGet to "Makukuha mo",
    Copy.RechargeAccountTitle to "Pangalan ng account",
    Copy.RechargeAccountNo to "Numero ng account",
    Copy.RechargeCopy to "Kopyahin",
    Copy.RechargeCopied to "Nakopya",
    Copy.RechargeInstructions to "Mga tagubilin",
    Copy.RechargeProof to "Screenshot ng bayad",
    Copy.RechargeProofHint to "Pagkatapos magbayad, ilakip ang resibo para ma-verify namin.",
    Copy.RechargeAttach to "Maglakip ng screenshot",
    Copy.RechargeChange to "Palitan",
    Copy.RechargeNote to "Mensahe (opsyonal)",
    Copy.RechargeSubmit to "Isumite para sa approval",
    Copy.RechargeNeedProof to "Ilakip muna ang screenshot ng bayad.",
    Copy.RechargeDone to "Naisumite. Idadagdag ang coin kapag nakumpirma ang bayad.",
    Copy.RechargeNoRequests to "Wala pang recharge request.",
    Copy.SellerHubTitle to "Coin seller",
    Copy.SellerDrawerHub to "Coin seller",
    Copy.SellerDrawerList to "Bumili sa seller",
    Copy.TransferTitle to "Transfer",
    Copy.TransferDrawer to "Transfer",
    Copy.TransferTabSend to "Magpadala",
    Copy.TransferTabHistory to "History",
    Copy.TransferTabDisputes to "Mga reklamo",
    Copy.TransferFindUser to "Ipadala kay",
    Copy.TransferFindHint to "User id o username",
    Copy.TransferAmount to "Halaga",
    Copy.TransferAmountHint to "Magkano ang ipapadala",
    Copy.TransferLimits to "Min {min} · Max {max}",
    Copy.TransferFeeLine to "Bayad: {n}",
    Copy.TransferTheyGet to "Makakatanggap si {name} ng {n} {cur}",
    Copy.TransferNote to "Tala (opsyonal)",
    Copy.TransferSendBtn to "Ipadala",
    Copy.TransferSentOk to "Naipadala.",
    Copy.TransferWindowLine to "Nagamit na ang {used} sa {max} kada {d} araw",
    Copy.TransferRecvRemaining to "Maaari pa siyang tumanggap ng {n}",
    Copy.TransferCantReceive to "Hindi makakatanggap ng transfer mula sa iyo ang account na ito.",
    Copy.TransferDisabled to "Hindi available ang transfer para sa iyong account.",
    Copy.TransferNoHistory to "Wala pang transfer.",
    Copy.TransferDirSent to "Ipinadala",
    Copy.TransferDirReceived to "Natanggap",
    Copy.TransferDisputeBtn to "Mag-ulat ng problema",
    Copy.TransferDisputeReason to "Ano ang nangyari? hal. maling bawas",
    Copy.TransferDisputeOpened to "Nabuksan ang reklamo.",
    Copy.TransferNoDisputes to "Walang reklamo.",
    Copy.TransferDisputeTitle to "Reklamo",
    Copy.ExchangeTitle to "Exchange",
    Copy.ExchangeDrawer to "Exchange",
    Copy.ExchangeTabConvert to "I-convert",
    Copy.ExchangeTabHistory to "History",
    Copy.ExchangePair to "I-convert",
    Copy.ExchangeAmount to "Halaga",
    Copy.ExchangeAmountHint to "Magkano ang iko-convert",
    Copy.ExchangeRateLine to "Rate: {r}×",
    Copy.ExchangeFeeLine to "Bayad: {n} ({p}%)",
    Copy.ExchangeYouGet to "Makakatanggap ka ng {n} {cur}",
    Copy.ExchangeBtn to "I-exchange",
    Copy.ExchangeDone to "Na-exchange.",
    Copy.ExchangeDisabled to "Hindi available ang exchange para sa iyong account.",
    Copy.ExchangeNoHistory to "Wala pang exchange.",
    Copy.ExchangeLimits to "Min {min} · Max {max}",
    Copy.ExchangeWindowLine to "Nagamit na ang {used} sa {max} kada {d} araw",
    Copy.ExchangeTotal to "Kabuuang na-exchange",
    Copy.ExchangeFeesPaid to "Mga bayad",
    Copy.LbTitle to "Leaderboard",
    Copy.LbSenders to "Top senders",
    Copy.LbHosts to "Top hosts",
    Copy.LbAgencies to "Top agencies",
    Copy.LbDaily to "Araw-araw",
    Copy.LbWeekly to "Lingguhan",
    Copy.LbMonthly to "Buwanan",
    Copy.LbTotal to "All time",
    Copy.LbMembers to "{n} miyembro",
    Copy.LbJoin to "Sumali",
    Copy.LbEmpty to "Wala pang ranking.",
    Copy.PostGiftTitle to "Magpadala ng regalo kay {name}",
    Copy.PostGiftSend to "Ipadala",
    Copy.PostGiftSent to "Naipadala ang {gift} ✓",
    Copy.PostGiftNoCoins to "Kulang ang coins",
    Copy.PostGiftFailed to "Hindi naipadala ang regalo",
    Copy.PostGiftEmpty to "Wala pang available na regalo.",
    Copy.PostShareBody to "Panoorin si {name} sa {app}",
    Copy.LbCreators to "Top creators",
    Copy.LbViews to "{n} views",
    Copy.CreatorTabLive to "Live",
    Copy.CreatorTabCreator to "Creator",
    Copy.CreatorEarned to "Kita ng creator",
    Copy.CreatorGiftsLbl to "Mga regalo",
    Copy.CreatorBonusLbl to "Bonus",
    Copy.CreatorClaim to "I-claim",
    Copy.CreatorClaimClosed to "Sarado ang pag-claim ngayon.",
    Copy.CreatorHow to "Paano ito gumagana",
    Copy.CreatorEmpty to "Wala pang creator reward.",
    Copy.CreatorOff to "Hindi available ang creator rewards ngayon.",
    Copy.CreatorKindGift to "Gift credit",
    Copy.CreatorKindBonus to "Milestone bonus",
    Copy.SellerTabOverview to "Buod",
    Copy.SellerTabSend to "Ipadala",
    Copy.SellerTabRequests to "Mga request",
    Copy.SellerTabHistory to "Kasaysayan",
    Copy.SellerTabTeam to "Team",
    Copy.SellerBalance to "Iyong balanse",
    Copy.SellerListTitle to "Mga coin seller",
    Copy.SellerListEmpty to "Walang seller ngayon.",
    Copy.SellerTabSellers to "Mga seller",
    Copy.SellerTabMyRequests to "Aking mga request",
    Copy.SellerRequestBtn to "Humiling ng recharge",
    Copy.SellerNoTransfers to "Wala pang naipadala.",
    Copy.SellerNoRequests to "Wala pang request.",
    Copy.HelpSubject to "Paksa",
    Copy.HelpMessage to "Mensahe",
    Copy.HelpSubmit to "Isumite",
    Copy.HelpMyReports to "Aking mga ulat",
    Copy.MyLiveTitle to "Aking Live",
    Copy.MyLiveToday to "Ngayon",
    Copy.MyLiveLiveNow to "Live ngayon",
    Copy.MyLiveCounting to "Binibilang",
    Copy.MyLivePaused to "Naka-pause — kumokonekta ulit",
    Copy.MyLiveWeek to "Ngayong linggo",
    Copy.MyLiveMonth to "Ngayong buwan",
    Copy.MyLiveLifetime to "Lahat ng oras",
    Copy.MyLiveDays to "Ayon sa araw",
    Copy.MyLiveDaysEmpty to "Walang live na oras sa panahong ito.",
    Copy.MyLiveSessions to "Mga session",
    Copy.MyLiveSessionsEmpty to "Hindi ka nag-live sa araw na ito.",
    Copy.MyLiveSessionsCount to "{n} session",
    Copy.MyLiveAudio to "Audio",
    Copy.MyLiveDropsOnce to "1 reconnect",
    Copy.MyLiveDrops to "{n} reconnect",
    Copy.MyLivePausedFor to "{t} offline, hindi binilang",
    Copy.MyLiveDayNote to "Nagsisimula ang araw sa {start} sa {tz}. Binibilang lang ang oras na konektado ang telepono.",
    Copy.MyLiveEndedBy to "Natapos: {r}",
    Copy.MyLiveReasonHost to "ikaw ang nagtapos",
    Copy.MyLiveReasonLost to "nawala ang koneksyon",
    Copy.MyLiveReasonAppClosed to "nagsara ang app",
    Copy.MyLiveReasonMax to "maximum na haba",
    Copy.MyLiveReasonStand to "umalis sa stage",
    Copy.MyLiveReasonOther to "{r}",
    Copy.MyLiveLoadFailed to "Hindi ma-load ang oras ng live.",
    Copy.MyLiveOlder to "Naunang araw",
    Copy.MyLiveRewards to "Mga reward",
    Copy.CreatePost to "Post",
    Copy.CreateGoLive to "Live",
    Copy.CreateVideo to "Video",
    Copy.CreateAudioOnly to "Audio",
    Copy.CreateLiveVideoTitle to "Magsimula ng video live",
    Copy.CreateLiveAudioTitle to "Magsimula ng audio room",
    Copy.CreateGoLiveBtn to "Mag-live",
    Copy.CreateStartAudioRoom to "Simulan ang audio room",

    // Paid chat & calls
    Copy.PaidTitle to "Bayad na chat at tawag",
    Copy.PaidLoadFailed to "Hindi ma-load ang bayad na chat at tawag. Subukan muli.",
    Copy.PaidTabOverview to "Buod",
    Copy.PaidTabMyRates to "Aking presyo",
    Copy.PaidTabHistory to "Kasaysayan",
    Copy.PaidRangeToday to "Ngayon",
    Copy.PaidRangeWeek to "Linggong ito",
    Copy.PaidRangeMonth to "Buwang ito",
    Copy.PaidRangeAll to "Lahat",
    Copy.PaidOff to "Naka-off ang bayad na chat at tawag ngayon. Libre ang mga mensahe at tawag.",
    Copy.PaidFreeRole to "Libre sa singil ang iyong role — walang bayad ang pag-mensahe o pagtawag kahit kanino.",
    Copy.PaidFreeForYou to "Libre para sa iyo",
    Copy.PaidYouPay to "Magbabayad ka ng {rates}",
    Copy.PaidYouEarn to "Kikita ka ng {rates}",
    Copy.PaidChatUnlocked to "Bukas na ang chat — libre na ang mga mensahe rito",
    Copy.PaidPromoLeft to "May natitirang {m} libreng mensahe at {n} libreng minuto ng tawag {period}",
    Copy.PaidPromoDay to "ngayong araw",
    Copy.PaidPromoWeek to "ngayong linggo",
    Copy.PaidPromoMonth to "ngayong buwan",
    Copy.PaidPromoTag to "libre (promo)",
    Copy.PaidPromoUsed to "Nagamit na promo",
    Copy.PaidUnitMessage to "mensahe",
    Copy.PaidUnitConversation to "chat",
    Copy.PaidUnitCall to "tawag",
    Copy.PaidUnitMinute to "min",
    Copy.PaidClaimable to "Handang i-claim",
    Copy.PaidClaimableCount to "{n} kita ang naghihintay",
    Copy.PaidClaimableShort to "{n} i-claim",
    Copy.PaidClaimExpiry to "Mag-e-expire ang hindi na-claim na kita pagkalipas ng {n} araw.",
    Copy.PaidClaimBtn to "I-claim sa wallet",
    Copy.PaidClaimed to "Na-claim ang {n}. Wallet {before} → {after}.",
    Copy.PaidEarned to "Kinita",
    Copy.PaidClaimedTotal to "Na-claim na",
    Copy.PaidDirectTotal to "Direktang naidagdag",
    Copy.PaidExpiredTotal to "Nag-expire nang hindi na-claim",
    Copy.PaidSpentToday to "Nagastos ngayon",
    Copy.PaidSpentAll to "Kabuuang nagastos",
    Copy.PaidPolicy to "Paano ito gumagana para sa iyo",
    Copy.PaidOwnRatesLine to "Iyong presyo: {rates}",
    Copy.PaidNoRules to "Walang presyong nalalapat sa iyo ngayon.",
    Copy.PaidFeeLine to "Bayad sa platform: {fee} (kinukuha sa panig ng tatanggap).",
    Copy.PaidPayoutClaim to "Nananatili rito ang kita bilang {cur} hanggang i-claim mo.",
    Copy.PaidPayoutDirect to "Agad na papasok sa wallet mo ang kita bilang {cur}.",
    Copy.PaidRulePay to "magbabayad ka kapag kinontak mo ang {to}",
    Copy.PaidRuleEarn to "kikita ka kapag kinontak ka ng {from}",
    Copy.PaidRuleBoth to "nalalapat sa magkabilang panig",
    Copy.PaidAnyone to "kahit sino",
    Copy.PaidReceivedFrom to "Natanggap, ayon sa chat at araw",
    Copy.PaidFromOn to "Mula kay {name} noong {date}",
    Copy.PaidUnits to "{n} singil",
    Copy.PaidEmpty to "Wala pang laman.",
    Copy.PaidLoadMore to "Magpakita pa",
    Copy.PaidChargeMessage to "Mensahe",
    Copy.PaidChargeUnlock to "Pagbukas ng chat",
    Copy.PaidChargeAudio to "Audio call",
    Copy.PaidChargeAudioMin to "Audio call · minuto {n}",
    Copy.PaidChargeVideo to "Video call",
    Copy.PaidChargeVideoMin to "Video call · minuto {n}",
    Copy.PaidStatusPaid to "Naidagdag",
    Copy.PaidStatusClaimable to "I-claim",
    Copy.PaidStatusClaimed to "Na-claim",
    Copy.PaidStatusExpired to "Nag-expire",
    Copy.PaidBeforeAfter to "Wallet {before} → {after}",
    Copy.PaidFree to "Libre",
    Copy.PaidSideEarned to "Kinita",
    Copy.PaidSidePaid to "Binayaran",
    Copy.PaidMyRatesHint to "Itakda kung magkano ang babayaran (sa {cur}) para ma-mensahe o matawagan ka. Iwanang blangko para libre.",
    Copy.PaidRateMax to "Pinakamataas na pinapayagang presyo: {n}.",
    Copy.PaidChannelMessage to "Mga mensahe",
    Copy.PaidChannelAudio to "Audio call",
    Copy.PaidChannelVideo to "Video call",
    Copy.PaidRatesSave to "I-save ang presyo ko",
    Copy.PaidRatesClear to "Alisin ang presyo ko",
    Copy.PaidRatesSaved to "Na-save ang iyong presyo.",
    Copy.PaidRatesCleared to "Inalis ang iyong presyo.",
    Copy.CallNoFundsMine to "Natapos ang tawag — kulang ang balanse para magpatuloy",
    Copy.CallNoFundsTheirs to "Natapos ang tawag — naubos ang balanse ng tumawag",
    Copy.CallPaidSoFar to "{rate} · {n} sa ngayon",
    Copy.CallPromoFree to "{rate} · libreng minuto (promo)",
    Copy.CallEarnedSoFar to "Kumita ka ng {n} sa tawag na ito",
    Copy.TxnCatPaidContactPaid to "Bayad na chat at tawag",
    Copy.TxnCatPaidContactEarned to "Kita sa chat at tawag",
)

private val Ru = mapOf(
    Copy.SnapTermsTitle to "Условия использования",
    Copy.SnapTermsAccept to "Принять",
    Copy.SnapTermsDecline to "Отклонить",
    Copy.CreateSave to "Сохранить",
    Copy.CreateSavedToGallery to "Сохранено в галерею",
    Copy.CreateSaveFailed to "Не удалось сохранить в галерею",
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
    Copy.LivePartyBadge to "ВЕЧЕРИНКА",
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
    Copy.LiveStickerSentLine to "отправил(а) стикер {to}",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Ur = mapOf(
    Copy.SnapTermsTitle to "سروس کی شرائط",
    Copy.SnapTermsAccept to "قبول کریں",
    Copy.SnapTermsDecline to "مسترد کریں",
    Copy.CreateSave to "محفوظ کریں",
    Copy.CreateSavedToGallery to "گیلری میں محفوظ ہو گیا",
    Copy.CreateSaveFailed to "گیلری میں محفوظ نہیں ہو سکا",
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
    Copy.LivePartyBadge to "پارٹی",
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
    Copy.LiveStickerSentLine to "نے {to} کو ایک اسٹیکر بھیجا",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Hi = mapOf(
    Copy.SnapTermsTitle to "सेवा की शर्तें",
    Copy.SnapTermsAccept to "स्वीकार करें",
    Copy.SnapTermsDecline to "अस्वीकार करें",
    Copy.CreateSave to "सेव करें",
    Copy.CreateSavedToGallery to "गैलरी में सेव हो गया",
    Copy.CreateSaveFailed to "गैलरी में सेव नहीं हो सका",
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
    Copy.LivePartyBadge to "पार्टी",
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
    Copy.LiveStickerSentLine to "ने {to} को एक स्टिकर भेजा",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Fr = mapOf(
    Copy.SnapTermsTitle to "Conditions d'utilisation",
    Copy.SnapTermsAccept to "Accepter",
    Copy.SnapTermsDecline to "Refuser",
    Copy.CreateSave to "Enregistrer",
    Copy.CreateSavedToGallery to "Enregistré dans votre galerie",
    Copy.CreateSaveFailed to "Impossible d'enregistrer dans la galerie",
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
    Copy.LivePartyBadge to "FÊTE",
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
    Copy.LiveStickerSentLine to "a envoyé un sticker à {to}",
    Copy.YouHandle to "@you_{wordmark}",
)

private val Nl = mapOf(
    Copy.SnapTermsTitle to "Servicevoorwaarden",
    Copy.SnapTermsAccept to "Accepteren",
    Copy.SnapTermsDecline to "Weigeren",
    Copy.CreateSave to "Opslaan",
    Copy.CreateSavedToGallery to "Opgeslagen in je galerij",
    Copy.CreateSaveFailed to "Opslaan in galerij mislukt",
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
    Copy.LivePartyBadge to "FEEST",
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
    Copy.LiveStickerSentLine to "stuurde {to} een sticker",
    Copy.YouHandle to "@you_{wordmark}",
)
