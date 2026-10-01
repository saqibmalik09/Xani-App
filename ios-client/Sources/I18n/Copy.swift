import Foundation

enum CopyKey: String {
    case tabHome, tabGames, tabCreate, tabInbox, tabProfile
    case homeForYou, homeFollowing, homeLive, homeFollowEmpty, liveBadge
    case homeCommentsCount, homeReply, homeViewReplies, homeAddComment
    case homeFeedEmpty, homeFeedOffline, homeFeedRetry, homeCommentsEmpty, homeCommentsOff, homePosting, homeFailed, profileNoPosts
    case homeSearchPeople, homeSearchNoPeople, homeSearchFollowers, homeFollowsYou, profileNoPeople
    case homeSortTop, homeSortNewest, homeSortMedia
    case homeSoundOriginal, homeSoundPosts, homeSoundSave, homeSoundUse, homeSoundStory, homeSoundFind
    case homeSearchHint, homeSearchGo, homeSearchRecent, homeSearchSeeMore, homeSearchYouMayLike, homeSearchRefresh
    case liveExplore, livePkBattle, liveAudioTab, liveAll, liveVideoPill, liveAudioPill
    case liveSearchCountry, liveNoCountries, liveEvent, liveFeatured, liveFeaturedSub
    case liveTrendingNow, livePkBattles, liveNow, liveCount, liveFollow, liveSendGift, liveSaySomething
    case liveLeaveTitle, liveMinimize, liveMinimizeSub, liveClose, liveCloseSub, liveCancelStay
    case liveGiftsTitle, liveGiftSend, liveGiftSendTo, liveGiftBalance, liveGiftRecharge, liveGiftNotEnough, liveGiftFailed
    case liveGiftNotEnoughTitle, liveGiftNotEnoughBody, liveGiftFailedTitle, liveGiftDismiss, liveGiftRetry, liveGiftTooFast
    case liveGiftEmpty, liveGiftLoading, liveGiftSentLine, liveGiftHost, liveGiftCombo, liveGiftLuckyHint
    case gamesTitle, gamesBody, gamesRefresh, gamesLoading, gamesUnavailable, gamesRetry, gamesEmpty, comingSoon
    case createTitle, createBody
    case createAddSound, createPost, createGoLive, createGoLiveTitle, createGoLiveBtn
    case createPhoto, createText, createTextHint, create5m, create60s, create15s
    case createTextTitle, createTextType, createNext, createCoHosts
    case createCover, createCoverHint, createLiveHint, createVideo, createAudioOnly
    case createRoomSize, createSeats, createSeatsHint, createChipCode, createChipAma, createChipStudy
    case createSaved, createEndLive
    case createFlashOn, createFlashOff, createTimer, createGrid, createSpeed, createBeauty
    case createSeatLayout, createOrderSeats, createOrderSeatsHint, createSeatHost, createSeatCount, createDone
    case createLiveVideoTitle, createLiveAudioTitle, createLiveVideoSub, createLiveAudioSub
    case createAudioRoomLabel, createAudioRoomSub, createBanner, createTitleField, createLiveTitleHint
    case createSpeakerSeats, createCustom, createSeatsMax
    case createAudience, createAudiencePublic, createAudienceFriends, createAudiencePrivate
    case createAudiencePublicSub, createAudienceFriendsSub, createAudiencePrivateSub
    case createPasswordHint, createNextSeats, createSelectSeats
    case createArrangeRoom, createArrangeHint, createStartAudioRoom, createLiveChatHint
    case profileReceiving, profileEditTitle, profileChangePhoto, profileUsername, profileNameLabel
    case profileGender, profileGenderMale, profileGenderFemale, profileGenderPreferNot
    case profileDrawerAccount, profileDrawerCreator, profileDrawerSettings, profileDrawerSupport, profileDrawerLegal
    case profileBalance, profileWallet, profileTxnHistory, profileCreatorTools, profileAnalytics, profileLiveCenter
    case profileSettings, profileHelpCentre, profileReportProblem, profileTerms, profileLogOut
    case helpSubject, helpSubmit, helpMyReports
    case profileTopSenders, profileGifts, profileDaily, profileWeekly, profileMonthly, profileMutual
    case profileUnfollow, profileFollowBack, profileFollow, profileFollowingBtn
    case profileSwitchAccounts, profileAddAccount, profileCreateAccount, profileUseExisting, profileAccountSettings, profileActivatePassword, profileChangePassword
    case profileActivatePasswordHint, profileChangePasswordHint
    case profileCurrentPassword, profileNewPassword, profileConfirmPassword, profilePasswordMismatch, profileUpdatePassword
    case profilePrivacySafety, profilePrivacy, profileDms
    case profileBlocked, profileSafeMode, profileContentDisplay, profileDisplay, profileLanguage, profileTranslation
    case profileNotifications, profileSupportLegal, profileLocationContacts, profileVersion
    case inboxTitle, inboxBody
    case profileCopy, profileVerified, profileFollowing, profileFollowers, profileLikes, profileRestricted
    case profileCoins, profileDiamonds, profileEdit, profileShare
    case profileLatest, profilePopular, profileSearch, profileName, profileStatus, profileWhatsOnMind, profileStatusTitle, profileCopied, profileUsernameLocked, profileUsernameAgainIn, profileBio
    case profileUsernameAvailable, profileUsernameTaken, profileUsernameRule, profileUsernameChecking, profileSaveFailed
    case profilePhotoTitle, profilePhotoCamera, profilePhotoGallery, profilePhotoAdd, profilePhotoUploading
    case profileViews, profileVisitsTitle, profileVisitsEmpty, profileVisitsMonth, profileMessage, profilePosts, profileNoPostsYet
    case uploadPostingPct, uploadProcessing, uploadPosted, uploadFailed, uploadRetry, uploadCancel, uploadQueued
    case uploadFormTitle, uploadCaptionHint, uploadWhoCanSee, uploadPublic, uploadFriends, uploadPrivate
    case uploadAllowComments, uploadAllowDuet, uploadPostBtn, uploadDetails, uploadDuration, uploadSize
    case uploadResolution, uploadTaken, uploadNot916, uploadReading
    case uploadErrTooLong, uploadErrTooLarge, uploadErrUnsupported, uploadErrUnreadable, uploadErrAspect
    case profileSave, featureUnavailable, youHandle, inboxChatOpenFail, inboxOk
    case profilePostEdit, profileChangePrivacy, profilePostDelete, profilePostDeleteAsk, profilePostDeleteYes, profilePostSaving
    case inboxChats, inboxNotifications, inboxActivity, inboxSearch, inboxPinned, inboxAll
    case inboxArchived, inboxNewGroup, inboxNewChat, inboxGroupName, inboxInviteLink, inboxCreate, inboxCancel, inboxMyStory, inboxMessage
    case inboxOnline, inboxMembers, inboxVanish, inboxGallery, inboxCamera, inboxContact
    case inboxLocation, inboxDocument, inboxPoll, inboxEvent, inboxAi, inboxMute, inboxUnmute
    case inboxPin, inboxUnpin, inboxArchive, inboxUnarchive, inboxDelete, inboxTheme
    case inboxNicknames, inboxDisappear, inboxChatInfo, inboxCalling, inboxEnd, inboxSpeaker
    case callRinging, callReconnecting, callIncomingAudio, callIncomingVideo, callAccept, callDecline
    case callBusy, callNoAnswer, callDeclined, callMissed, callLost, callFailed, callUnavailable, callEnded
    case callOngoing, callAddPerson, callNobodyToAdd, callInCall, callYou
    case inboxVideo, inboxEncrypted, inboxFollow, inboxNewFollowers, inboxSystem, inboxVideoRestrictedTitle, inboxVideoRestrictedBody, inboxSupportRepliedTitle
    case inboxTypeSearch, inboxNoResults, inboxSeen, inboxDelivered, inboxRecents, inboxSeeAll
    case inboxPause, inboxResume, inboxShare, inboxReadReceipts, inboxMedia, inboxMarkRead
    case inboxOnlineShort, inboxCreateStory, inboxShareStory, inboxSearchChat, inboxPinnedMessage
    case inboxMuteNotifs, inboxShareProfile, inboxExportChat, inboxClearChat, inboxRestrict
    case inboxBlock, inboxReport, inboxEveryone, inboxFriends, inboxOnlyMe, inboxPhoto
    case inboxVideoType, inboxText, inboxAddNick, inboxConnecting, inboxMore, inboxChooseType
    case inboxAudience, inboxChatTheme, inboxOff, inboxOnceSeen, inboxHours, inboxDays
    case inboxMakeVanish, inboxTimer, inboxFollowedYou, inboxTypeMessage, inboxReply, inboxYouReplied, inboxRepliedToYou, inboxEdit, inboxEdited, inboxForward
    case inboxCopyAction, inboxInfo, inboxUnsend, inboxPhotoStory, inboxVideoStory
    case inboxOpenCamera, inboxChooseGallery, inboxYourStory, inboxBackground, inboxVoiceHint
    case inboxHoldMic, inboxEncryptedHint
    case inboxVanishHintOn, inboxVanishHintOff, inboxProfile, inboxOptions
    case inboxCreateGroup, inboxCreateGroupSub, inboxMediaVis, inboxSavingMedia, inboxNotSavingMedia
    case inboxMsgPin, inboxMsgUnpin, inboxDownload, inboxTranslate, inboxForwarding
    case inboxWriteMsg, inboxYourReactions, inboxReactions, inboxTapToRemove, inboxYou, inboxCustomize, inboxSearchShort, inboxSendBtn
    case inboxAddToStory, inboxShareStoryShort, inboxWhatsApp, inboxCopyLink, inboxWhatsAppStatus, inboxShareStatusShort, inboxSnapchat, inboxMessenger, inboxSms, inboxLink, inboxFacebook
    case inboxJustNow, inboxMinutesAgo
    case inboxOffline, inboxTyping, inboxLastSeen, inboxBlocked, inboxUnblock, inboxReported, inboxPeople
    case inboxPaste, inboxSelectAll, inboxImages, inboxReels, inboxCopied, inboxDocs, inboxLinks
    case inboxAllowPhotos
    case inboxWallpaper, inboxBubbleColor, inboxApply, inboxContacts, inboxAllowContacts
    case inboxDeleteMsg, inboxRemove, inboxFromYou, inboxCreatePoll, inboxAskQuestion, inboxLeavePoll, inboxMultiple
    case inboxCreateEvent, inboxEventName, inboxEventDesc, inboxEventWhen, inboxReminder
    case inboxHourBefore, inboxMinBefore, inboxDayBefore, inboxNever, inboxAddCalendar, inboxEditEvent, inboxLeaveEvent, inboxEventSoon
    case rolesTitle, rolesTabMine, rolesTabRequests, rolesTabApprovals, rolesMineEmpty
    case rolesJoinTitle, rolesJoinHint, rolesCodePlaceholder, rolesCodeOwner, rolesPickRole, rolesNotePlaceholder
    case rolesApply, rolesApplied, rolesMyCodes, rolesCodeOpens, rolesCopied, rolesShareCode
    case rolesUpline, rolesTopOfTree, rolesDownline, rolesDirect, rolesWhole, rolesDownlineEmpty, rolesDirectCount
    case rolesRequestsEmpty, rolesApprovalsEmpty, rolesApprove, rolesReject, rolesCancel
    case rolesReasonPlaceholder, rolesReasonRequired, rolesDecided, rolesRequestedBy, rolesWaiting, rolesExpires
    case rolesStatusActive, rolesStatusSuspended, rolesStatusRevoked, rolesStatusBlocked
    case rolesReqPending, rolesReqApproved, rolesReqRejected, rolesReqCancelled, rolesReqExpired
    case rolesKindAssign, rolesKindApply, rolesKindTransfer, rolesKindRevoke, rolesKindReactivate
    case rolesNoticeRequest, rolesNoticeDecision, rolesNoticeChange, rolesLoadFailed
}

enum Copy {
    static func t(_ key: CopyKey, _ args: [String: String] = [:]) -> String {
        var raw = table[key] ?? En[key] ?? key.rawValue
        for (k, v) in args { raw = raw.replacingOccurrences(of: "{\(k)}", with: v) }
        return raw
    }

    private static var lang: String {
        if #available(iOS 16.0, *) {
            return Locale.current.language.languageCode?.identifier ?? "en"
        }
        return Locale.current.languageCode ?? "en"
    }

    private static var table: [CopyKey: String] {
        switch lang {
        case "zh": return Zh
        case "ar": return Ar
        case "ru": return Ru
        case "ur": return Ur
        case "hi": return Hi
        case "fr": return Fr
        case "nl": return Nl
        default: return En
        }
    }
}

private let En: [CopyKey: String] = [
    .tabHome: "Home", .tabGames: "Games", .tabCreate: "Create", .tabInbox: "Inbox", .tabProfile: "Profile",
    .homeForYou: "For You", .homeFollowing: "Following", .homeLive: "Live",
    .homeFollowEmpty: "Follow creators to see their videos", .liveBadge: "LIVE",
    .homeCommentsCount: "{n} comments", .homeReply: "Reply", .homeViewReplies: "View {n} replies", .homeAddComment: "Add comment…",
    .homeFeedEmpty: "Nothing here yet. Be the first to post.", .homeFeedOffline: "You're offline. Showing what we have.",
    .homeFeedRetry: "Retry", .homeCommentsEmpty: "No comments yet. Say something nice.",
    .homeCommentsOff: "Comments are off for this post.", .homePosting: "Posting…", .homeFailed: "Couldn't process",
    .profileNoPosts: "No posts yet",
    .homeSearchPeople: "People", .homeSearchNoPeople: "No one found for \"{q}\"",
    .homeSearchFollowers: "{n} followers", .homeFollowsYou: "Follows you", .profileNoPeople: "No one here yet",
    .homeSortTop: "Top comments", .homeSortNewest: "Newest first", .homeSortMedia: "With media",
    .homeSoundOriginal: "Original Sound", .homeSoundPosts: "{n} posts", .homeSoundSave: "Save",
    .homeSoundUse: "Use sound", .homeSoundStory: "Post to Story", .homeSoundFind: "Find related content",
    .homeSearchHint: "Search…", .homeSearchGo: "Search", .homeSearchRecent: "Recent video",
    .homeSearchSeeMore: "See more", .homeSearchYouMayLike: "You may like", .homeSearchRefresh: "Refresh",
    .liveExplore: "Explore", .livePkBattle: "PK Battle", .liveAudioTab: "Audio",
    .liveAll: "All", .liveVideoPill: "Video", .liveAudioPill: "Audio",
    .liveSearchCountry: "Search country…", .liveNoCountries: "No countries found",
    .liveEvent: "LIVE EVENT", .liveFeatured: "🔥 Top Featured Streams", .liveFeaturedSub: "Tap to join a live now",
    .liveTrendingNow: "Trending Now", .livePkBattles: "PK Battles", .liveNow: "Live Now", .liveCount: "{n} live",
    .liveFollow: "Follow", .liveSendGift: "Send Gift", .liveSaySomething: "Say something…",
    .liveLeaveTitle: "Leave this broadcast?", .liveMinimize: "Minimize", .liveMinimizeSub: "Keep watching in mini player",
    .liveClose: "Close", .liveCloseSub: "Leave this stream", .liveCancelStay: "Cancel, stay in stream",
    .liveGiftsTitle: "Gifts", .liveGiftSend: "Send", .liveGiftSendTo: "Send to", .liveGiftBalance: "Coins", .liveGiftRecharge: "Recharge",
    .liveGiftNotEnough: "Not enough coins for this gift.", .liveGiftFailed: "Could not send the gift. Try again.",
    .liveGiftNotEnoughTitle: "Insufficient balance",
    .liveGiftNotEnoughBody: "{gift} costs {price} coins and you have {coins}. Recharge to keep the party going.",
    .liveGiftFailedTitle: "Gift not sent", .liveGiftDismiss: "Not now", .liveGiftRetry: "Try again",
    .liveGiftTooFast: "You are sending very fast. Take a breath and try again.",
    .liveGiftEmpty: "No gifts yet.", .liveGiftLoading: "Loading gifts…", .liveGiftSentLine: "sent {gift} to {to}",
    .liveGiftHost: "Host", .liveGiftCombo: "Combo", .liveGiftLuckyHint: "Tap again to keep sending",
    .gamesTitle: "Games",
    .gamesBody: "Play full-mode games with your coin balance.",
    .gamesRefresh: "Refresh",
    .gamesLoading: "Loading games…",
    .gamesUnavailable: "Games are temporarily unavailable. Try again shortly.",
    .gamesRetry: "Try again",
    .gamesEmpty: "No games available right now.",
    .comingSoon: "COMING SOON",
    .createTitle: "Create", .createBody: "Shoot, upload, or go live.",
    .createAddSound: "Add sound", .createPost: "Post", .createGoLive: "Live",
    .createGoLiveTitle: "Live", .createGoLiveBtn: "Go LIVE",
    .createPhoto: "PHOTO", .createText: "TEXT", .createTextHint: "Write something",
    .createTextTitle: "Share your thoughts", .createTextType: "Type something…",
    .createNext: "Next", .createCoHosts: "Co-hosts",
    .create5m: "5m", .create60s: "60s", .create15s: "15s",
    .createCover: "Cover image", .createCoverHint: "Select and preview a cover",
    .createLiveHint: "Weekend coding session & Q&A", .createVideo: "Video", .createAudioOnly: "Audio",
    .createRoomSize: "Room size", .createSeats: "Custom number of seats", .createSeatsHint: "Enter number (max 35)",
    .createChipCode: "Coding live", .createChipAma: "Ask me anything", .createChipStudy: "Study with me",
    .createSaved: "Saved", .createEndLive: "End LIVE",
    .createFlashOn: "Flash On", .createFlashOff: "Flash Off", .createTimer: "Timer",
    .createGrid: "Grid", .createSpeed: "Speed", .createBeauty: "Beauty",
    .createSeatLayout: "Seat layout", .createOrderSeats: "Order seats",
    .createOrderSeatsHint: "Drag a seat into a slot. Empty slots stay open.", .createSeatHost: "Host",
    .createSeatCount: "{n} seats", .createDone: "Done",
    .createLiveVideoTitle: "Start a Video Live", .createLiveAudioTitle: "Start an Audio Room",
    .createLiveVideoSub: "You'll be visible on camera.", .createLiveAudioSub: "Listeners join to hear you speak.",
    .createAudioRoomLabel: "Audio Room", .createAudioRoomSub: "Live voice with speakers & audience",
    .createBanner: "Listing Banner", .createTitleField: "Title", .createLiveTitleHint: "What's your live about?",
    .createSpeakerSeats: "Speaker Seats", .createCustom: "Custom", .createSeatsMax: "max 35",
    .createAudience: "Audience", .createAudiencePublic: "Public", .createAudienceFriends: "Friends",
    .createAudiencePrivate: "Private", .createAudiencePublicSub: "Everyone",
    .createAudienceFriendsSub: "Following", .createAudiencePrivateSub: "Password",
    .createPasswordHint: "Set a room password…", .createNextSeats: "Next — Review Seats",
    .createSelectSeats: "Select seats to continue", .createArrangeRoom: "Arrange Room",
    .createArrangeHint: "{n} seats · tap a seat to claim host · drag to reorder",
    .createStartAudioRoom: "Start Audio Room", .createLiveChatHint: "Say something…",
    .profileReceiving: "Receiving", .profileEditTitle: "Edit profile", .profileChangePhoto: "Change photo",
    .profileUsername: "Username", .profileNameLabel: "Name",
    .profileGender: "Gender", .profileGenderMale: "Male", .profileGenderFemale: "Female",
    .profileGenderPreferNot: "Prefer not to say",
    .profileDrawerAccount: "Account", .profileDrawerCreator: "Creator", .profileDrawerSettings: "Settings",
    .profileDrawerSupport: "Support", .profileDrawerLegal: "Legal",
    .profileBalance: "Balance", .profileWallet: "Wallet / Coins", .profileTxnHistory: "Transaction History",
    .profileCreatorTools: "Creator Tools", .profileAnalytics: "Analytics", .profileLiveCenter: "LIVE Center",
    .profileSettings: "Settings", .profileHelpCentre: "Help Centre", .profileReportProblem: "Report a Problem",
    .profileTerms: "Terms & Policies", .profileLogOut: "Log Out",
    .helpSubject: "Subject", .helpSubmit: "Submit", .helpMyReports: "My reports",
    .profileTopSenders: "Top Senders", .profileGifts: "Gifts",
    .profileDaily: "Daily", .profileWeekly: "Weekly", .profileMonthly: "Monthly", .profileMutual: "Mutual",
    .profileUnfollow: "Unfollow", .profileFollowBack: "Follow Back", .profileFollow: "Follow",
    .profileFollowingBtn: "Following",
    .profileSwitchAccounts: "Switch accounts",
    .profileAddAccount: "Log in to another account",
    .profileCreateAccount: "Create account",
    .profileUseExisting: "Use existing account",
    .profileAccountSettings: "Account settings",
    .profileActivatePassword: "Activate password login",
    .profileChangePassword: "Change password",
    .profileActivatePasswordHint: "Add a password so you can also sign in with your email or username.",
    .profileChangePasswordHint: "Enter your current password, then choose a new one.",
    .profileCurrentPassword: "Current password",
    .profileNewPassword: "New password (at least 8 characters)",
    .profileConfirmPassword: "Confirm new password",
    .profilePasswordMismatch: "Passwords do not match.",
    .profileUpdatePassword: "Update password",
    .profilePrivacySafety: "Privacy & Safety", .profilePrivacy: "Privacy", .profileDms: "Direct messages",
    .profileBlocked: "Blocked accounts", .profileSafeMode: "Safe mode",
    .profileContentDisplay: "Content & Display", .profileDisplay: "Display",
    .profileLanguage: "Language", .profileTranslation: "Translation",
    .profileNotifications: "Notifications", .profileSupportLegal: "Support & Legal",
    .profileLocationContacts: "Location & Contacts", .profileVersion: "{app} v1.0.0",
    .inboxTitle: "Inbox", .inboxBody: "Chats, notifications, and activity arrive next.",
    .profileCopy: "Copy", .profileVerified: "Verified", .profileFollowing: "Following",
    .profileFollowers: "Followers", .profileLikes: "Likes", .profileRestricted: "Restricted", .profileCoins: "Coins", .profileDiamonds: "Diamonds",
    .profileEdit: "Edit Profile", .profileShare: "Share", .profileLatest: "Latest", .profilePopular: "Popular",
    .profileSearch: "Search profile, posts, sounds…", .profileName: "Your Name",
    .profileStatus: "Living the moment", .profileWhatsOnMind: "What's on your mind?", .profileStatusTitle: "Status",
    .profileCopied: "Copied to clipboard", .profileUsernameLocked: "Username can be changed once every 30 days",
    .profileUsernameAgainIn: "You can change your username again in {n} days",
    .profileUsernameAvailable: "@{u} is available", .profileUsernameTaken: "@{u} is already taken",
    .profileUsernameRule: "3–24 characters: letters, numbers, dot or underscore",
    .profileUsernameChecking: "Checking…",
    .profileSaveFailed: "Could not save. We put your profile back.",
    .profilePhotoTitle: "Profile photo", .profilePhotoCamera: "Take a photo",
    .profilePhotoGallery: "Choose from gallery", .profilePhotoAdd: "Add a photo",
    .profilePhotoUploading: "Uploading…",
    .profileViews: "Views", .profileVisitsTitle: "Profile visits", .profileVisitsEmpty: "No visits yet",
    .profileVisitsMonth: "Last 30 days", .profileMessage: "Message", .profilePosts: "Posts",
    .profileNoPostsYet: "No posts yet",
    .uploadPostingPct: "Posting… {pct}%", .uploadProcessing: "Finishing your post…", .uploadPosted: "Posted",
    .uploadFailed: "Couldn't post", .uploadRetry: "Retry", .uploadCancel: "Cancel", .uploadQueued: "Waiting…",
    .uploadFormTitle: "New post", .uploadCaptionHint: "Describe your post…", .uploadWhoCanSee: "Who can see this",
    .uploadPublic: "Everyone", .uploadFriends: "Friends", .uploadPrivate: "Only me",
    .uploadAllowComments: "Allow comments", .uploadAllowDuet: "Allow duet and stitch", .uploadPostBtn: "Post",
    .uploadDetails: "Details", .uploadDuration: "Duration", .uploadSize: "Size", .uploadResolution: "Resolution",
    .uploadTaken: "Taken", .uploadNot916: "Not 9:16 — it will be shown with bars in the feed.", .uploadReading: "Reading file…",
    .uploadErrTooLong: "Videos can be up to {min} minutes.", .uploadErrTooLarge: "This file is too large ({max} MB max).",
    .uploadErrUnsupported: "This format isn't supported.", .uploadErrUnreadable: "This file couldn't be read.",
    .uploadErrAspect: "This aspect ratio isn't supported.",
    .profileBio: "Creator · Dancer · Dreamer",
    .profileSave: "Save", .featureUnavailable: "This section is temporarily unavailable.",
    .profilePostEdit: "Edit post", .profileChangePrivacy: "Change privacy", .profilePostDelete: "Delete post",
    .profilePostDeleteAsk: "This removes the post for everyone. It cannot be undone.",
    .profilePostDeleteYes: "Delete", .profilePostSaving: "Saving…",
    .inboxChatOpenFail: "Couldn't open this chat. Please try again.", .inboxOk: "OK",
    .youHandle: "@you_{wordmark}",
    .inboxChats: "Chats", .inboxNotifications: "Notifications", .inboxActivity: "Activity",
    .inboxSearch: "Search chats and people", .inboxPinned: "PINNED", .inboxAll: "ALL MESSAGES",
    .inboxArchived: "Archived", .inboxNewGroup: "New Group", .inboxNewChat: "New Chat",
    .inboxGroupName: "Group name", .inboxInviteLink: "Invite link", .inboxCreate: "Create", .inboxCancel: "Cancel",
    .inboxMyStory: "My story", .inboxMessage: "Message", .inboxOnline: "Online", .inboxMembers: "members",
    .inboxVanish: "Vanish Mode · Messages disappear after seen", .inboxGallery: "Gallery",
    .inboxCamera: "Camera", .inboxContact: "Contact", .inboxLocation: "Location", .inboxDocument: "Document",
    .inboxPoll: "Poll", .inboxEvent: "Event", .inboxAi: "AI Images", .inboxMute: "Mute", .inboxUnmute: "Unmute",
    .inboxPin: "Pin Chat", .inboxUnpin: "Unpin Chat", .inboxArchive: "Archive", .inboxUnarchive: "Unarchive",
    .inboxDelete: "Delete Chat", .inboxTheme: "Theme", .inboxNicknames: "Nicknames",
    .inboxDisappear: "Disappearing Messages", .inboxChatInfo: "Chat Info", .inboxCalling: "Calling…",
    .inboxEnd: "End", .inboxSpeaker: "Speaker", .inboxVideo: "Video", .inboxEncrypted: "End-to-end encrypted",
    .callRinging: "Ringing…", .callReconnecting: "Reconnecting…", .callIncomingAudio: "Incoming voice call",
    .callIncomingVideo: "Incoming video call", .callAccept: "Accept", .callDecline: "Decline", .callBusy: "Busy",
    .callNoAnswer: "No answer", .callDeclined: "Call declined", .callMissed: "Missed call", .callLost: "Connection lost",
    .callFailed: "Couldn't connect", .callUnavailable: "Calling isn't available on this build", .callEnded: "Call ended",
    .callOngoing: "Ongoing call", .callAddPerson: "Add person", .callNobodyToAdd: "Nobody else to add right now",
    .callInCall: "{n} in call", .callYou: "You",
    .inboxFollow: "Follow", .inboxNewFollowers: "NEW FOLLOWERS", .inboxSystem: "SYSTEM",
    .inboxVideoRestrictedTitle: "Video restricted",
    .inboxVideoRestrictedBody: "Your video has been restricted for posting against guidelines. If you think this is a mistake you can appeal back for review.",
    .inboxSupportRepliedTitle: "Support replied",
    .inboxTypeSearch: "Type to search messages", .inboxNoResults: "No results for \"{q}\"",
    .inboxSeen: "Seen", .inboxDelivered: "Delivered", .inboxRecents: "Recents", .inboxSeeAll: "See all",
    .inboxPause: "Pause", .inboxResume: "Resume", .inboxShare: "Share", .inboxReadReceipts: "Read Receipts",
    .inboxMedia: "Media, Links & Docs", .inboxMarkRead: "Mark as Read", .inboxOnlineShort: "{n} members",
    .inboxCreateStory: "Create Story", .inboxShareStory: "Share Story", .inboxSearchChat: "Search in conversation…",
    .inboxPinnedMessage: "Pinned Message", .inboxMuteNotifs: "Mute Notifications",
    .inboxShareProfile: "Share Profile", .inboxExportChat: "Export Chat", .inboxClearChat: "Clear Chat",
    .inboxRestrict: "Restrict", .inboxBlock: "Block", .inboxReport: "Report", .inboxEveryone: "Everyone",
    .inboxFriends: "Friends", .inboxOnlyMe: "Only me", .inboxPhoto: "Photo", .inboxVideoType: "Video",
    .inboxText: "Text", .inboxAddNick: "Add nickname", .inboxConnecting: "Connecting…", .inboxMore: "More",
    .inboxChooseType: "CHOOSE TYPE", .inboxAudience: "AUDIENCE", .inboxChatTheme: "Chat Theme",
    .inboxOff: "Off", .inboxOnceSeen: "Once they're seen", .inboxHours: "24 hours", .inboxDays: "7 days",
    .inboxMakeVanish: "Make messages disappear", .inboxTimer: "Message timer",
    .inboxFollowedYou: "started following you",     .inboxTypeMessage: "Message", .inboxReply: "Reply",
    .inboxYouReplied: "You replied", .inboxRepliedToYou: "Replied to you",
    .inboxEdit: "Edit", .inboxEdited: "edited",
    .inboxForward: "Forward", .inboxCopyAction: "Copy", .inboxInfo: "Info", .inboxUnsend: "Unsend",
    .inboxPhotoStory: "Photo Story", .inboxVideoStory: "Video Story", .inboxOpenCamera: "Open Camera",
    .inboxChooseGallery: "Choose from Gallery", .inboxYourStory: "Your story goes here",
    .inboxBackground: "Background", .inboxVoiceHint: "Voice message", .inboxHoldMic: "Hold to record",
    .inboxEncryptedHint: "End-to-end encrypted",
    .inboxVanishHintOn: "Hold the sheet and pull up to turn on disappearing messages",
    .inboxVanishHintOff: "Hold the sheet and pull up to turn off disappearing messages",
    .inboxProfile: "Profile", .inboxOptions: "Options",
    .inboxCreateGroup: "Create a group chat",
    .inboxCreateGroupSub: "Add {name} and others",
    .inboxMediaVis: "Media Visibility",
    .inboxSavingMedia: "Saving media to gallery",
    .inboxNotSavingMedia: "Not saving to gallery",
    .inboxMsgPin: "Pin", .inboxMsgUnpin: "Unpin", .inboxDownload: "Download",
    .inboxTranslate: "Translate", .inboxForwarding: "Forwarding",
    .inboxWriteMsg: "Write a message...", .inboxYourReactions: "Your reactions",
    .inboxReactions: "Reactions", .inboxTapToRemove: "Tap to remove", .inboxYou: "You",
    .inboxCustomize: "Customize", .inboxSearchShort: "Search", .inboxSendBtn: "Send",
    .inboxAddToStory: "Add to story", .inboxShareStoryShort: "Story", .inboxWhatsApp: "WhatsApp", .inboxCopyLink: "Copy link",
    .inboxWhatsAppStatus: "WhatsApp Status", .inboxShareStatusShort: "Status", .inboxSnapchat: "Snapchat",
    .inboxMessenger: "Messenger", .inboxSms: "SMS", .inboxLink: "Link", .inboxFacebook: "Facebook",
    .inboxJustNow: "Just now", .inboxMinutesAgo: "{n} minutes ago",
    .inboxPeople: "People", .inboxOffline: "Offline", .inboxTyping: "typing…", .inboxLastSeen: "Last seen {t} ago",
    .inboxBlocked: "You blocked this account", .inboxUnblock: "Unblock", .inboxReported: "Thanks. We'll review this.",
    .inboxPaste: "Paste", .inboxSelectAll: "Select all",
    .inboxImages: "Images", .inboxReels: "Reels",
    .inboxCopied: "Copied", .inboxDocs: "Docs", .inboxLinks: "Links",
    .inboxAllowPhotos: "Allow photos",
    .inboxWallpaper: "Wallpaper", .inboxBubbleColor: "Bubble color", .inboxApply: "Select",
    .inboxContacts: "Contacts", .inboxAllowContacts: "Allow contacts",
    .inboxDeleteMsg: "Delete", .inboxRemove: "Remove",
    .inboxFromYou: "From you", .inboxCreatePoll: "Create poll",
    .inboxAskQuestion: "Ask a question", .inboxLeavePoll: "Leave poll",
    .inboxMultiple: "Allow multiple answers",
    .inboxCreateEvent: "Create event", .inboxEventName: "Event name",
    .inboxEventDesc: "Description (optional)", .inboxEventWhen: "Date and time",
    .inboxReminder: "Reminder", .inboxHourBefore: "1 hour before",
    .inboxMinBefore: "15 minutes before", .inboxDayBefore: "1 day before",
    .inboxNever: "Never", .inboxAddCalendar: "Add to calendar",
    .inboxEditEvent: "Edit event", .inboxLeaveEvent: "Leave event",
    .inboxEventSoon: "Pick a time at least 15 minutes from now",
    .rolesTitle: "My roles", .rolesTabMine: "Roles", .rolesTabRequests: "Requests", .rolesTabApprovals: "Approvals",
    .rolesMineEmpty: "You don't hold a role yet. Got a referral code? Enter it below.",
    .rolesJoinTitle: "Join with a referral code",
    .rolesJoinHint: "The person who gave you the code becomes your upline and approves your application.",
    .rolesCodePlaceholder: "Referral code", .rolesCodeOwner: "Code from {name} ({role})", .rolesPickRole: "Apply as",
    .rolesNotePlaceholder: "Note to your upline (optional)", .rolesApply: "Apply",
    .rolesApplied: "Application sent. You'll be notified once it's reviewed.",
    .rolesMyCodes: "My referral codes", .rolesCodeOpens: "Onboards: {roles}", .rolesCopied: "Code copied", .rolesShareCode: "Share",
    .rolesUpline: "Upline", .rolesTopOfTree: "Top of the tree", .rolesDownline: "Downline",
    .rolesDirect: "Direct", .rolesWhole: "Everyone", .rolesDownlineEmpty: "Nobody under you yet. Share your referral code.",
    .rolesDirectCount: "{n} direct", .rolesRequestsEmpty: "No requests yet.", .rolesApprovalsEmpty: "Nothing waiting for you.",
    .rolesApprove: "Approve", .rolesReject: "Reject", .rolesCancel: "Cancel request",
    .rolesReasonPlaceholder: "Reason (shown to the person)", .rolesReasonRequired: "Add a short reason.",
    .rolesDecided: "Decided by {name}", .rolesRequestedBy: "Requested by {name}", .rolesWaiting: "Waiting for approval",
    .rolesExpires: "Expires {when}",
    .rolesStatusActive: "Active", .rolesStatusSuspended: "Suspended", .rolesStatusRevoked: "Removed", .rolesStatusBlocked: "Blocked",
    .rolesReqPending: "Pending", .rolesReqApproved: "Approved", .rolesReqRejected: "Rejected",
    .rolesReqCancelled: "Cancelled", .rolesReqExpired: "Expired",
    .rolesKindAssign: "Assignment", .rolesKindApply: "Application", .rolesKindTransfer: "Transfer",
    .rolesKindRevoke: "Removal", .rolesKindReactivate: "Reactivation",
    .rolesNoticeRequest: "Role request", .rolesNoticeDecision: "Role request decided", .rolesNoticeChange: "Your role changed",
    .rolesLoadFailed: "Could not load. Check your connection.",
]

private let Zh: [CopyKey: String] = [
    .tabHome: "首页", .tabGames: "游戏", .tabCreate: "创作", .tabInbox: "消息", .tabProfile: "我",
    .homeForYou: "推荐", .homeFollowing: "关注", .homeLive: "直播",
    .homeFollowEmpty: "关注创作者后即可看到他们的视频", .liveBadge: "直播",
    .gamesTitle: "游戏", .gamesBody: "第三方游戏即将接入。", .comingSoon: "即将推出",
    .createTitle: "创作", .createBody: "拍照、文字和视频拍摄即将推出。",
    .inboxTitle: "消息", .inboxBody: "聊天、通知和动态即将推出。",
    .profileCopy: "复制", .profileVerified: "已认证", .profileFollowing: "关注",
    .profileFollowers: "粉丝", .profileLikes: "获赞", .profileCoins: "金币", .profileDiamonds: "钻石",
    .profileEdit: "编辑资料", .profileShare: "分享", .profileLatest: "最新", .profilePopular: "热门",
    .profileSearch: "搜索资料、作品、声音…", .profileName: "你的名字",
    .profileStatus: "活在当下", .profileBio: "创作者 · 舞者 · 追梦人",
    .profileSave: "保存", .featureUnavailable: "该功能暂时不可用。",
    .youHandle: "@you_{wordmark}",
]

private let Ar: [CopyKey: String] = [
    .tabHome: "الرئيسية", .tabGames: "ألعاب", .tabCreate: "إنشاء", .tabInbox: "الوارد", .tabProfile: "الملف",
    .homeForYou: "لك", .homeFollowing: "المتابَعون", .homeLive: "مباشر",
    .homeFollowEmpty: "تابع صنّاع المحتوى لمشاهدة فيديوهاتهم", .liveBadge: "مباشر",
    .gamesTitle: "ألعاب", .gamesBody: "دمج الألعاب من أطراف ثالثة قريبًا.", .comingSoon: "قريبًا",
    .createTitle: "إنشاء", .createBody: "التقاط الصور والنصوص والفيديو قريبًا.",
    .inboxTitle: "الوارد", .inboxBody: "الدردشات والإشعارات والنشاط قريبًا.",
    .profileCopy: "نسخ", .profileVerified: "موثّق", .profileFollowing: "يتابع",
    .profileFollowers: "المتابعون", .profileLikes: "الإعجابات", .profileCoins: "عملات", .profileDiamonds: "ألماس",
    .profileEdit: "تعديل الملف", .profileShare: "مشاركة", .profileLatest: "الأحدث", .profilePopular: "الأشهر",
    .profileSearch: "ابحث في الملف والمنشورات والأصوات…", .profileName: "اسمك",
    .profileStatus: "أعيش اللحظة", .profileBio: "صانع محتوى · راقص · حالم",
    .profileSave: "حفظ", .featureUnavailable: "هذا القسم غير متاح مؤقتًا.",
    .youHandle: "@you_{wordmark}",
]

private let Ru: [CopyKey: String] = [
    .tabHome: "Главная", .tabGames: "Игры", .tabCreate: "Создать", .tabInbox: "Входящие", .tabProfile: "Профиль",
    .homeForYou: "Реки", .homeFollowing: "Подписки", .homeLive: "Эфир",
    .homeFollowEmpty: "Подпишитесь на авторов, чтобы видеть их видео", .liveBadge: "ЭФИР",
    .gamesTitle: "Игры", .gamesBody: "Сторонние игры появятся позже.", .comingSoon: "СКОРО",
    .createTitle: "Создать", .createBody: "Фото, текст и запись видео появятся позже.",
    .inboxTitle: "Входящие", .inboxBody: "Чаты, уведомления и активность появятся позже.",
    .profileCopy: "Копировать", .profileVerified: "Подтверждён", .profileFollowing: "Подписки",
    .profileFollowers: "Подписчики", .profileLikes: "Лайки", .profileCoins: "Монеты", .profileDiamonds: "Алмазы",
    .profileEdit: "Изменить профиль", .profileShare: "Поделиться", .profileLatest: "Новые", .profilePopular: "Популярные",
    .profileSearch: "Поиск профиля, постов, звуков…", .profileName: "Ваше имя",
    .profileStatus: "Живу моментом", .profileBio: "Автор · Танцор · Мечтатель",
    .profileSave: "Сохранить", .featureUnavailable: "Раздел временно недоступен.",
    .youHandle: "@you_{wordmark}",
]

private let Ur: [CopyKey: String] = [
    .tabHome: "ہوم", .tabGames: "گیمز", .tabCreate: "بنائیں", .tabInbox: "انباکس", .tabProfile: "پروفائل",
    .homeForYou: "آپ کے لیے", .homeFollowing: "فالوینگ", .homeLive: "لائیو",
    .homeFollowEmpty: "کریٹرز کو فالو کریں تاکہ ان کی ویڈیوز نظر آئیں", .liveBadge: "لائیو",
    .gamesTitle: "گیمز", .gamesBody: "تھرڈ پارٹی گیمز جلد آ رہی ہیں۔", .comingSoon: "جلد آرہا ہے",
    .createTitle: "بنائیں", .createBody: "تصویر، متن اور ویڈیو جلد آئے گی۔",
    .inboxTitle: "انباکس", .inboxBody: "چیٹس، نوٹیفکیشنز اور سرگرمی جلد آئے گی۔",
    .profileCopy: "کاپی", .profileVerified: "تصدیق شدہ", .profileFollowing: "فالونگ",
    .profileFollowers: "فالوورز", .profileLikes: "لائکس", .profileCoins: "سکے", .profileDiamonds: "ہیرے",
    .profileEdit: "پروفائل ترمیم", .profileShare: "شیئر", .profileLatest: "تازہ ترین", .profilePopular: "مقبول",
    .profileSearch: "پروفائل، پوسٹس، آوازیں تلاش کریں…", .profileName: "آپ کا نام",
    .profileStatus: "لمحے میں جی رہا ہوں", .profileBio: "کریٹر · ڈانسر · خواب دیکھنے والا",
    .profileSave: "محفوظ", .featureUnavailable: "یہ حصہ عارضی طور پر دستیاب نہیں۔",
    .youHandle: "@you_{wordmark}",
]

private let Hi: [CopyKey: String] = [
    .tabHome: "होम", .tabGames: "गेम्स", .tabCreate: "बनाएँ", .tabInbox: "इनबॉक्स", .tabProfile: "प्रोफ़ाइल",
    .homeForYou: "आपके लिए", .homeFollowing: "फ़ॉलोइंग", .homeLive: "लाइव",
    .homeFollowEmpty: "क्रिएटर्स को फ़ॉलो करें ताकि उनके वीडियो दिखें", .liveBadge: "लाइव",
    .gamesTitle: "गेम्स", .gamesBody: "थर्ड-पार्टी गेम्स जल्द आएँगी।", .comingSoon: "जल्द आ रहा है",
    .createTitle: "बनाएँ", .createBody: "फ़ोटो, टेक्स्ट और वीडियो जल्द आएगा।",
    .inboxTitle: "इनबॉक्स", .inboxBody: "चैट, सूचनाएँ और गतिविधि जल्द आएगी।",
    .profileCopy: "कॉपी", .profileVerified: "वेरिफ़ाइड", .profileFollowing: "फ़ॉलोइंग",
    .profileFollowers: "फ़ॉलोअर्स", .profileLikes: "लाइक", .profileCoins: "सिक्के", .profileDiamonds: "हीरे",
    .profileEdit: "प्रोफ़ाइल बदलें", .profileShare: "शेयर", .profileLatest: "नवीनतम", .profilePopular: "लोकप्रिय",
    .profileSearch: "प्रोफ़ाइल, पोस्ट, साउंड खोजें…", .profileName: "आपका नाम",
    .profileStatus: "पल में जी रहा हूँ", .profileBio: "क्रिएटर · डांसर · सपने देखने वाला",
    .profileSave: "सेव", .featureUnavailable: "यह भाग अभी उपलब्ध नहीं है।",
    .youHandle: "@you_{wordmark}",
]

private let Fr: [CopyKey: String] = [
    .tabHome: "Accueil", .tabGames: "Jeux", .tabCreate: "Créer", .tabInbox: "Boîte", .tabProfile: "Profil",
    .homeForYou: "Pour toi", .homeFollowing: "Abonnements", .homeLive: "Live",
    .homeFollowEmpty: "Abonnez-vous pour voir leurs vidéos", .liveBadge: "LIVE",
    .gamesTitle: "Jeux", .gamesBody: "Les jeux tiers arrivent bientôt.", .comingSoon: "BIENTÔT",
    .createTitle: "Créer", .createBody: "Photo, texte et vidéo arrivent ensuite.",
    .inboxTitle: "Boîte", .inboxBody: "Chats, notifications et activité arrivent ensuite.",
    .inboxVideoRestrictedTitle: "Vidéo restreinte",
    .inboxVideoRestrictedBody: "Votre vidéo a été restreinte pour non-respect des règles. Si vous pensez qu’il s’agit d’une erreur, vous pouvez demander un nouvel examen.",
    .profileCopy: "Copier", .profileVerified: "Vérifié", .profileFollowing: "Abonnements",
    .profileFollowers: "Abonnés", .profileLikes: "J’aime", .profileRestricted: "Restreint", .profileCoins: "Pièces", .profileDiamonds: "Diamants",
    .profileEdit: "Modifier le profil", .profileShare: "Partager", .profileLatest: "Récents", .profilePopular: "Populaires",
    .profileSearch: "Rechercher profil, posts, sons…", .profileName: "Votre nom",
    .profileStatus: "Je vis l’instant", .profileBio: "Créateur · Danseur · Rêveur",
    .profileSave: "Enregistrer", .featureUnavailable: "Cette section est temporairement indisponible.",
    .youHandle: "@you_{wordmark}",
]

private let Nl: [CopyKey: String] = [
    .tabHome: "Home", .tabGames: "Games", .tabCreate: "Maken", .tabInbox: "Inbox", .tabProfile: "Profiel",
    .homeForYou: "Voor jou", .homeFollowing: "Volgend", .homeLive: "Live",
    .homeFollowEmpty: "Volg makers om hun video’s te zien", .liveBadge: "LIVE",
    .gamesTitle: "Games", .gamesBody: "Games van derden komen binnenkort.", .comingSoon: "BINNENKORT",
    .createTitle: "Maken", .createBody: "Foto, tekst en video volgen later.",
    .inboxTitle: "Inbox", .inboxBody: "Chats, meldingen en activiteit volgen later.",
    .profileCopy: "Kopieer", .profileVerified: "Geverifieerd", .profileFollowing: "Volgend",
    .profileFollowers: "Volgers", .profileLikes: "Likes", .profileCoins: "Munten", .profileDiamonds: "Diamanten",
    .profileEdit: "Profiel bewerken", .profileShare: "Delen", .profileLatest: "Nieuwste", .profilePopular: "Populair",
    .profileSearch: "Zoek profiel, posts, sounds…", .profileName: "Jouw naam",
    .profileStatus: "Leef het moment", .profileBio: "Maker · Danser · Dromer",
    .profileSave: "Bewaar", .featureUnavailable: "Dit onderdeel is tijdelijk niet beschikbaar.",
    .youHandle: "@you_{wordmark}",
]
