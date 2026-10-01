import SwiftUI
import UIKit

/// The signed-in account's own picture, wherever it is shown large.
///
/// One view for the profile header and the signup step so the two can never
/// drift: the same fallback initials, the same upload ring, the same instant
/// swap the moment a new picture is picked.
struct MyAvatar: View {
    var size: CGFloat = 90
    /// Name used for initials before a picture exists. Falls back to the
    /// profile, which is empty during signup until the form is filled.
    var name: String = ""
    /// Present when the avatar is tappable: shows the camera badge.
    var onPick: (() -> Void)?
    /// Press and hold to see the picture full size.
    var onHold: (() -> Void)? = nil

    @Environment(\.uiScale) private var scale
    @ObservedObject private var avatars = AvatarStore.shared
    @ObservedObject private var store = ProfileStore.shared

    var body: some View {
        let who = name.isEmpty ? (store.snapshot.displayName.isEmpty ? store.snapshot.username : store.snapshot.displayName) : name
        let side = size * scale
        let badge = onPick == nil ? 0 : side * 0.3
        let pad = badge * 0.28

        ZStack {
            ZStack {
                Circle().fill(ProfileTokens.cta)
                if let preview = avatars.preview {
                    Image(uiImage: preview).resizable().scaledToFill()
                } else if let stored = MediaConfig.shared.url(store.snapshot.avatarUrl) {
                    RemoteImage(url: stored, contentMode: .fill, fallback: .clear)
                } else if who.isEmpty {
                    Circle().fill(Color(rgb: 0xF0EEFF)).padding(3 * scale)
                        .overlay(BrandMark(side: side * 0.49))
                } else {
                    AppText(FeedRepo.initials(who), style: Type.labelBold, color: .white, sizeOverride: size * 0.34)
                }
            }
            .frame(width: side, height: side)
            .clipShape(Circle())
            .overlay(avatars.uploading ? UploadRing(side: side) : nil)

            if onPick != nil {
                Circle()
                    .fill(ProfileTokens.accent)
                    .overlay(Circle().stroke(Color.white, lineWidth: 2 * scale))
                    .overlay(Image(systemName: "camera.fill")
                        .font(.system(size: side * 0.14, weight: .semibold))
                        .foregroundColor(.white))
                    .frame(width: badge, height: badge)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(pad * 0.15)
            }
        }
        // Layout is larger than the face when the camera badge is on, so a
        // parent that clips to the reported size cannot cut the badge in half.
        .frame(width: side + pad * 2, height: side + pad * 2)
        .contentShape(Circle())
        .onTapGesture { onPick?() }
        .onLongPressGesture { onHold?() }
    }
}

/// A sweep around the picture while its bytes go up. The picture is already the
/// new one underneath, so this says "saving", not "loading".
private struct UploadRing: View {
    let side: CGFloat
    @State private var spin = false

    var body: some View {
        Circle()
            .trim(from: 0, to: 0.22)
            .stroke(ProfileTokens.accent, style: StrokeStyle(lineWidth: side * 0.045, lineCap: .round))
            .frame(width: side, height: side)
            .rotationEffect(.degrees(spin ? 360 : 0))
            .animation(.linear(duration: 0.9).repeatForever(autoreverses: false), value: spin)
            .onAppear { spin = true }
    }
}

/// Camera or gallery, then straight to the server. There is no crop step: the
/// picture is centre-cropped to a square on the way out, which is what the user
/// expects from a round avatar and one fewer screen between them and done.
struct AvatarPickerSheet: View {
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @ObservedObject private var avatars = AvatarStore.shared
    @State private var source: UIImagePickerController.SourceType?

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.35).ignoresSafeArea().onTapGesture(perform: onClose)

            VStack(spacing: 0) {
                Capsule()
                    .fill(Color(rgb: 0xDBDBDB))
                    .frame(width: 36 * scale, height: 4 * scale)
                    .padding(.vertical, 10 * scale)

                AppText(Copy.t(.profilePhotoTitle), style: Type.labelBold,
                        color: ProfileTokens.text, sizeOverride: 16)
                    .padding(.bottom, 4 * scale)

                if !avatars.error.isEmpty {
                    AppText(avatars.error, style: Type.caption, color: ProfileTokens.danger,
                            alignment: .center, sizeOverride: 12)
                        .padding(.horizontal, 24 * scale)
                        .padding(.vertical, 8 * scale)
                }

                row(Copy.t(.profilePhotoCamera), "camera.fill") { source = .camera }
                row(Copy.t(.profilePhotoGallery), "photo.on.rectangle") { source = .photoLibrary }
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, insets.bottom + 12 * scale)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
            .ignoresSafeArea(.container, edges: .bottom)
        }
        .fullScreenCover(item: Binding(
            get: { source.map(PickerSource.init) },
            set: { source = $0?.value }
        )) { pick in
            MediaPicker(source: pick.value) { url in
                AvatarStore.shared.upload(url)
                source = nil
                onClose()
            }
            .ignoresSafeArea()
        }
        .onAppear { avatars.clearError() }
    }

    private func row(_ label: String, _ symbol: String, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            HStack(spacing: 14 * scale) {
                Image(systemName: symbol)
                    .font(.system(size: 16 * scale, weight: .medium))
                    .foregroundColor(ProfileTokens.accent)
                    .frame(width: 22 * scale)
                AppText(label, style: Type.label, color: ProfileTokens.text, sizeOverride: 15)
                Spacer()
            }
            .padding(.horizontal, 24 * scale)
            .padding(.vertical, 15 * scale)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// `fullScreenCover(item:)` needs something identifiable; the source type is not.
private struct PickerSource: Identifiable {
    let value: UIImagePickerController.SourceType
    var id: Int { value.rawValue }
    init(_ value: UIImagePickerController.SourceType) { self.value = value }
}
