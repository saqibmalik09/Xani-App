import SwiftUI

/// What the author can do to their own post, from a long press on its grid tile.
///
/// Delete asks once — it is the one action here that cannot be undone — and both
/// actions answer through the post bus, so the tile, the feed card and any open
/// viewer all change from the same event instead of each re-reading.
struct ProfilePostActionsSheet: View {
    let post: PostData
    var onEdit: () -> Void
    var onDeleted: () -> Void
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var confirming = false
    @State private var privacy = false
    @State private var working = false
    @State private var shown = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(shown ? 0.35 : 0)
                .ignoresSafeArea()
                .onTapGesture { dismiss() }

            VStack(spacing: 0) {
                Capsule()
                    .fill(Color(rgb: 0xDBDBDB))
                    .frame(width: 36 * scale, height: 4 * scale)
                    .padding(.vertical, 10 * scale)

                if confirming {
                    AppText(Copy.t(.profilePostDeleteAsk), style: Type.caption,
                            color: Color(rgb: 0x888888), alignment: .center, sizeOverride: 13)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 24 * scale)
                        .padding(.vertical, 12 * scale)
                    action(Copy.t(.profilePostDeleteYes), Color(rgb: 0xEF4444)) {
                        working = true
                        FeedRepo.shared.delete(post.id) { ok in
                            working = false
                            if ok { onDeleted() } else { onClose() }
                        }
                    }
                    action(Copy.t(.uploadCancel), Color(rgb: 0x111111)) { confirming = false }
                } else if privacy {
                    AppText(Copy.t(.uploadWhoCanSee), style: Type.caption,
                            color: Color(rgb: 0x888888), alignment: .center, sizeOverride: 12)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 24 * scale)
                        .padding(.vertical, 8 * scale)
                    privacyPick(Copy.t(.uploadPublic), "public")
                    privacyPick(Copy.t(.uploadFriends), "friends")
                    privacyPick(Copy.t(.uploadPrivate), "private")
                    action(Copy.t(.uploadCancel), Color(rgb: 0x111111)) { privacy = false }
                } else {
                    action(Copy.t(.profilePostEdit), Color(rgb: 0x111111)) {
                        onEdit()
                        onClose()
                    }
                    action(Copy.t(.profileChangePrivacy), Color(rgb: 0x111111)) { privacy = true }
                    action(Copy.t(.profilePostDelete), Color(rgb: 0xEF4444)) { confirming = true }
                }
                Spacer().frame(height: max(insets.bottom, 10 * scale))
            }
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
            .offset(y: shown ? 0 : 400)
        }
        .ignoresSafeArea()
        .onAppear { withAnimation(.easeOut(duration: 0.24)) { shown = true } }
    }

    private func action(_ label: String, _ tint: Color, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            AppText(label, style: Type.labelBold, color: working ? Color(rgb: 0xBBBBBB) : tint,
                    alignment: .center, sizeOverride: 15)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15 * scale)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle(enabled: !working, pressedScale: 0.99))
        .disabled(working)
    }

    private func privacyPick(_ label: String, _ value: String) -> some View {
        let on = post.visibility == value
        return action(label, on ? Color(rgb: 0x7C3AED) : Color(rgb: 0x111111)) {
            setVisibility(value)
        }
    }

    private func setVisibility(_ visibility: String) {
        if post.visibility == visibility {
            onClose()
            return
        }
        working = true
        FeedRepo.shared.edit(post.id, visibility: visibility) { ok in
            working = false
            if ok { onClose() }
        }
    }

    private func dismiss() {
        guard !working else { return }
        withAnimation(.easeIn(duration: 0.2)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { onClose() }
    }
}

/// Caption, audience and comments for a post that is already published.
///
/// Saving does not reload anything: the server answers with the post as everyone
/// will now see it, that goes on the post bus, and the grid tile and the feed
/// card swap in place.
struct ProfilePostEditSheet: View {
    let post: PostData
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var caption = ""
    @State private var visibility = "public"
    @State private var allowComments = true
    @State private var saving = false
    @State private var slide: CGFloat = 1
    @State private var loaded = false

    private var dirty: Bool {
        caption != post.caption || visibility != post.visibility || allowComments != post.allowComments
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12 * scale) {
                Button(action: dismiss) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16 * scale, weight: .semibold))
                        .foregroundColor(Color(rgb: 0x111111))
                        .frame(width: 36 * scale, height: 36 * scale)
                        .background(Color(rgb: 0xF7F7F7))
                        .clipShape(Circle())
                }
                .buttonStyle(PressScaleStyle())
                AppText(Copy.t(.profilePostEdit), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                Spacer()
                Button(action: save) {
                    AppText(Copy.t(saving ? .profilePostSaving : .profileSave), style: Type.labelBold,
                            color: dirty && !saving ? Color(rgb: 0x7C3AED) : Color(rgb: 0xBBBBBB), sizeOverride: 15)
                }
                .buttonStyle(PressScaleStyle(enabled: dirty && !saving))
                .disabled(!dirty || saving)
            }
            .padding(.horizontal, 14 * scale)
            .padding(.vertical, 12 * scale)
            Color(rgb: 0xF0F0F0).frame(height: 1)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ZStack(alignment: .topLeading) {
                        if caption.isEmpty {
                            AppText(Copy.t(.uploadCaptionHint), style: Type.caption,
                                    color: Color(rgb: 0xBBBBBB), sizeOverride: 15)
                                .padding(.horizontal, 14 * scale)
                                .padding(.vertical, 16 * scale)
                        }
                        TextEditor(text: $caption)
                            .font(.system(size: 15 * scale))
                            .foregroundColor(Color(rgb: 0x111111))
                            .tint(Color(rgb: 0x7C3AED))
                            .frame(minHeight: 100 * scale)
                            .padding(.horizontal, 10 * scale)
                            .padding(.vertical, 8 * scale)
                            .onChange(of: caption) { v in if v.count > 2200 { caption = String(v.prefix(2200)) } }
                    }
                    .background(Color(rgb: 0xF7F7F7))
                    .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                    .padding(14 * scale)

                    AppText(Copy.t(.uploadWhoCanSee), style: Type.labelBold, color: Color(rgb: 0x888888), sizeOverride: 12)
                        .padding(.horizontal, 18 * scale)
                        .padding(.bottom, 6 * scale)
                    HStack(spacing: 8 * scale) {
                        chip(Copy.t(.uploadPublic), "public")
                        chip(Copy.t(.uploadFriends), "friends")
                        chip(Copy.t(.uploadPrivate), "private")
                    }
                    .padding(.horizontal, 14 * scale)

                    Toggle(isOn: $allowComments) {
                        AppText(Copy.t(.uploadAllowComments), style: Type.caption, color: Color(rgb: 0x111111), sizeOverride: 15)
                    }
                    .tint(Color(rgb: 0x7C3AED))
                    .padding(.horizontal, 18 * scale)
                    .padding(.vertical, 18 * scale)
                }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .padding(.top, insets.top)
        .offset(x: UIScreen.main.bounds.width * slide)
        .onAppear {
            // Seeded once, so a realtime event for this post mid-edit cannot
            // overwrite what the author is typing.
            if !loaded {
                caption = post.caption
                visibility = post.visibility
                allowComments = post.allowComments
                loaded = true
            }
            withAnimation(.easeInOut(duration: 0.32)) { slide = 0 }
        }
    }

    private func chip(_ label: String, _ value: String) -> some View {
        let on = visibility == value
        return Button(action: { visibility = value }) {
            AppText(label, style: on ? Type.labelBold : Type.label,
                    color: on ? .white : Color(rgb: 0x111111), sizeOverride: 13)
                .padding(.horizontal, 16 * scale)
                .padding(.vertical, 9 * scale)
                .background(on ? Color(rgb: 0x7C3AED) : Color(rgb: 0xF7F7F7))
                .clipShape(Capsule())
        }
        .buttonStyle(PressScaleStyle())
    }

    private func save() {
        saving = true
        FeedRepo.shared.edit(
            post.id,
            caption: caption == post.caption ? nil : caption,
            visibility: visibility == post.visibility ? nil : visibility,
            allowComments: allowComments == post.allowComments ? nil : allowComments
        ) { ok in
            saving = false
            if ok { onClose() }
        }
    }

    private func dismiss() {
        guard !saving else { return }
        withAnimation(.easeInOut(duration: 0.28)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) { onClose() }
    }
}
