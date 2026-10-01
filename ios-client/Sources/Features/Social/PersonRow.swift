import SwiftUI

/// Palette used for every avatar fallback so a person looks the same on every surface.
private let tints: [(UInt32, UInt32)] = [
    (0x8B5CF6, 0xEC4899), (0x10B981, 0x3B82F6), (0xF59E0B, 0xEF4444),
    (0x06B6D4, 0x8B5CF6), (0xEC4899, 0xF59E0B), (0x34D399, 0x60A5FA),
]

func tintsFor(_ id: Int64) -> (UInt32, UInt32) {
    tints[Int((UInt64(bitPattern: id) >> 5) % UInt64(tints.count))]
}

/// Avatar: the person's picture when they have one, initials on their gradient
/// otherwise, and the frame they wear ringed around it. The frame comes from
/// the explicit parameters when the surface has fresh data (a live payload) or
/// from `FrameStore` when it only knows the id. The ring overflows the circle
/// by design and never grows the layout slot.
struct PersonAvatar: View {
    let id: Int64
    let name: String
    let avatarUrl: String
    let size: CGFloat
    /// nil = not known by this surface, read the store; "" = wearing none.
    var frameUrl: String? = nil
    var frameMime: String = ""
    @ObservedObject private var frameStore = FrameStore.shared

    var body: some View {
        let (a, b) = tintsFor(id)
        let known = frameStore.frames[id]
        let fUrl = frameUrl ?? known?.url ?? ""
        let fMime = frameUrl != nil ? frameMime : (known?.mime ?? "")
        ZStack {
            Circle().fill(LinearGradient(colors: [Color(rgb: a), Color(rgb: b)], startPoint: .topLeading, endPoint: .bottomTrailing))
            if let u = avatarUrl.hasPrefix("http") ? URL(string: avatarUrl) : MediaConfig.shared.url(avatarUrl.isEmpty ? nil : avatarUrl) {
                RemoteImage(url: u, contentMode: .fill, fallback: .clear)
            } else {
                AppText(FeedRepo.initials(name), style: Type.labelBold, color: .white, sizeOverride: size * 0.36)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(
            Group { if !fUrl.isEmpty { AvatarFrame(url: fUrl, mime: fMime, size: size) } },
            alignment: .center
        )
        .onAppear {
            // A surface with fresh knowledge teaches every other surface.
            if let f = frameUrl { FrameStore.shared.learn(id, url: f, mime: frameMime) }
        }
    }
}

/// One person as every list shows them, with the follow button bound to
/// `FollowStore` — so a tap here flips the same button on the feed card.
///
/// `showMeta` is what separates the two places this row appears. In search, how
/// many followers a stranger has is the signal that tells you whether you found
/// the right account. In a follower or following list you already know who they
/// are, and their follower count is their business, not a number to rank your
/// own people by — so those lists show the picture, the handle, and the button.
struct PersonRow: View {
    let person: PersonData
    var dark = false
    var showMeta = true
    var onOpen: (PersonData) -> Void = { _ in }
    @Environment(\.uiScale) private var scale
    @ObservedObject private var follows = FollowStore.shared

    var body: some View {
        let rel = follows.relations[person.id]
        let following = rel?.following ?? person.following
        let followers = (rel?.followers ?? -1) >= 0 ? rel!.followers : person.followers
        let ink = dark ? Color.white : Color(rgb: 0x111111)
        let sub = dark ? Color(rgb: 0xAAAAAA) : Color(rgb: 0x888888)
        let display = person.fullName.isEmpty ? person.username : person.fullName
        var meta = "@\(person.username) · " + Copy.t(.homeSearchFollowers, ["n": FeedRepo.compact(Int64(followers))])
        if person.followsYou { meta += " · " + Copy.t(.homeFollowsYou) }
        // No handle prefix and no counts in a follower list: just who they are.
        let primary = showMeta ? display : (person.username.isEmpty ? display : person.username)
        let secondary = person.fullName.isEmpty || person.fullName == person.username ? "" : person.fullName

        return HStack(spacing: 12 * scale) {
            PersonAvatar(id: person.id, name: display, avatarUrl: person.avatarUrl, size: 46 * scale)
            VStack(alignment: .leading, spacing: 2 * scale) {
                HStack(spacing: 4 * scale) {
                    AppText(primary, style: Type.labelBold, color: ink, sizeOverride: 14)
                    if person.verified {
                        AppText("✓", style: Type.labelBold, color: Color(rgb: 0x7C3AED), sizeOverride: 12)
                    }
                }
                if showMeta {
                    AppText(meta, style: Type.micro, color: sub, sizeOverride: 11)
                } else if !secondary.isEmpty {
                    AppText(secondary, style: Type.micro, color: sub, sizeOverride: 11)
                }
            }
            Spacer(minLength: 10 * scale)
            FollowButton(following: following, followsYou: person.followsYou, dark: dark) {
                follows.set(person.id, on: !following)
            }
        }
        .padding(.horizontal, 18 * scale)
        .padding(.vertical, 10 * scale)
        .contentShape(Rectangle())
        .onTapGesture { onOpen(person) }
    }
}

struct FollowButton: View {
    let following: Bool
    let followsYou: Bool
    var dark = false
    let onClick: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        let label: String = following ? Copy.t(.profileFollowingBtn) : (followsYou ? Copy.t(.profileFollowBack) : Copy.t(.profileFollow))
        Button(action: onClick) {
            AppText(
                label,
                style: Type.labelBold,
                color: following ? (dark ? .white : Color(rgb: 0x111111)) : .white,
                sizeOverride: 12
            )
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 7 * scale)
            .background(
                Capsule().fill(following ? (dark ? Color(rgb: 0x1F1F23) : Color(rgb: 0xF3F3F3)) : Color(rgb: 0x7C3AED))
            )
            .overlay(
                Capsule().stroke(following ? (dark ? Color(rgb: 0x3A3A40) : Color(rgb: 0xDDDDDD)) : .clear, lineWidth: 1)
            )
        }
        .buttonStyle(PressScaleStyle())
    }
}
