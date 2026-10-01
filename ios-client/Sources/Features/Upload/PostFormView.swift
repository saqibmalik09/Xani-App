import AVFoundation
import ImageIO
import SwiftUI

private enum FormTokens {
    static let ink = Color(rgb: 0x111111)
    static let sub = Color(rgb: 0x7A7A8C)
    static let field = Color(rgb: 0xF4F4F8)
    static let line = Color(rgb: 0xE9E9F0)
    static let accent = Color(rgb: 0x7C3AED)
    static let pink = Color(rgb: 0xEC4899)
    static let danger = Color(rgb: 0xDC2626)
}

/// The details screen between capture and upload. Reads the file's facts off
/// the main thread while the user types; Post hands the work to
/// `UploadManager` and returns immediately — the strip takes it from there.
struct PostFormView: View {
    let url: URL
    var onClose: () -> Void
    var onPosted: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var caption = ""
    @State private var visibility = "public"
    @State private var comments = true
    @State private var duet = true
    @State private var info: MediaInfo?
    @State private var problem: MediaProblem?
    @State private var probed = false
    @State private var slide: CGFloat = 1
    @State private var closing = false
    @FocusState private var captionFocus: Bool

    private var maxCaption: Int { MediaConfig.shared.maxCaption }
    private var canPost: Bool { probed && problem == nil && info != nil && caption.count <= maxCaption }

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                topBar
                ScrollView {
                    VStack(spacing: 16 * scale) {
                        coverAndCaption
                        details
                        audience
                        toggle(Copy.t(.uploadAllowComments), $comments)
                        toggle(Copy.t(.uploadAllowDuet), $duet)
                    }
                    .padding(.horizontal, 16 * scale)
                    .padding(.bottom, 12 * scale)
                }
                postButton
            }
            .padding(.top, insets.top)
            .padding(.bottom, insets.bottom)
            .background(Color.white.ignoresSafeArea())
            .offset(x: geo.size.width * slide)
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.easeInOut(duration: 0.36)) { slide = 0 }
        }
        .task(id: url) {
            let p = await MediaProbe.probe(url)
            info = p
            if let p { problem = MediaProbe.check(p) } else { problem = .unreadable }
            probed = true
        }
    }

    private var topBar: some View {
        HStack(spacing: 12 * scale) {
            Button(action: dismiss) {
                AppText("‹", style: Type.h2, color: FormTokens.ink, sizeOverride: 24)
                    .frame(width: 40 * scale, height: 40 * scale)
                    .background(FormTokens.field)
                    .clipShape(Circle())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.92))
            AppText(Copy.t(.uploadFormTitle), style: Type.labelBold, color: FormTokens.ink, sizeOverride: 17)
            Spacer()
        }
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 10 * scale)
    }

    private var coverAndCaption: some View {
        HStack(spacing: 12 * scale) {
            ZStack(alignment: .bottomTrailing) {
                FileCover(url: url, isVideo: info?.isVideo ?? false)
                if let i = info, i.isVideo, i.durationMs > 0 {
                    AppText(clock(i.durationMs), style: Type.micro, color: .white, sizeOverride: 11)
                        .padding(.horizontal, 6 * scale)
                        .padding(.vertical, 2 * scale)
                        .background(Color.black.opacity(0.55))
                        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                        .padding(6 * scale)
                }
            }
            .frame(width: 96 * scale, height: 160 * scale)
            .background(FormTokens.field)
            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))

            ZStack(alignment: .topLeading) {
                if caption.isEmpty {
                    AppText(Copy.t(.uploadCaptionHint), style: Type.body, color: FormTokens.sub, sizeOverride: 15)
                        .padding(.top, 8 * scale)
                        .padding(.leading, 5 * scale)
                }
                TextEditor(text: $caption)
                    .font(.system(size: 15 * scale))
                    .foregroundColor(FormTokens.ink)
                    .focused($captionFocus)
                    .scrollContentBackgroundHidden()
                    .onChange(of: caption) { v in
                        if v.count > maxCaption + 50 { caption = String(v.prefix(maxCaption + 50)) }
                    }
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        AppText("\(caption.count)/\(maxCaption)", style: Type.micro,
                                color: caption.count > maxCaption ? FormTokens.danger : FormTokens.sub, sizeOverride: 11)
                    }
                }
            }
            .padding(8 * scale)
            .frame(maxWidth: .infinity)
            .frame(height: 160 * scale)
            .background(FormTokens.field)
            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
        }
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 6 * scale) {
            AppText(Copy.t(.uploadDetails), style: Type.labelBold, color: FormTokens.ink, sizeOverride: 13)
            if !probed {
                AppText(Copy.t(.uploadReading), style: Type.caption, color: FormTokens.sub)
            } else {
                if let problem {
                    AppText(problemText(problem), style: Type.label, color: FormTokens.danger, sizeOverride: 13)
                }
                if let i = info {
                    if i.isVideo { fact(Copy.t(.uploadDuration), clock(i.durationMs)) }
                    fact(Copy.t(.uploadSize), humanBytes(i.size))
                    fact(Copy.t(.uploadResolution), "\(i.width) × \(i.height)")
                    if let c = i.capturedAt { fact(Copy.t(.uploadTaken), localDate(c)) }
                    if !i.isPortrait916, problem == nil {
                        AppText(Copy.t(.uploadNot916), style: Type.caption, color: FormTokens.sub)
                    }
                }
            }
        }
        .padding(12 * scale)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FormTokens.field)
        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
    }

    private func fact(_ label: String, _ value: String) -> some View {
        HStack(spacing: 0) {
            AppText(label, style: Type.caption, color: FormTokens.sub).frame(width: 92 * scale, alignment: .leading)
            AppText(value, style: Type.caption, color: FormTokens.ink).lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    private var audience: some View {
        VStack(alignment: .leading, spacing: 8 * scale) {
            AppText(Copy.t(.uploadWhoCanSee), style: Type.labelBold, color: FormTokens.ink, sizeOverride: 14)
            HStack(spacing: 8 * scale) {
                chip("public", Copy.t(.uploadPublic))
                chip("friends", Copy.t(.uploadFriends))
                chip("private", Copy.t(.uploadPrivate))
            }
        }
    }

    private func chip(_ value: String, _ label: String) -> some View {
        let on = visibility == value
        return Button(action: { visibility = value }) {
            AppText(label, style: on ? Type.labelBold : Type.label, color: on ? FormTokens.accent : FormTokens.ink, sizeOverride: 13)
                .frame(maxWidth: .infinity)
                .frame(height: 40 * scale)
                .background(on ? FormTokens.accent.opacity(0.1) : FormTokens.field)
                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                        .stroke(on ? FormTokens.accent : Color.clear, lineWidth: 1.5 * scale)
                )
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.98))
    }

    private func toggle(_ label: String, _ on: Binding<Bool>) -> some View {
        HStack {
            AppText(label, style: Type.label, color: FormTokens.ink, sizeOverride: 14)
            Spacer()
            Toggle("", isOn: on).labelsHidden().tint(FormTokens.accent)
        }
        .frame(height: 44 * scale)
    }

    private var postButton: some View {
        Button(action: post) {
            AppText(Copy.t(probed ? .uploadPostBtn : .uploadReading), style: Type.labelBold,
                    color: canPost ? .white : FormTokens.sub, sizeOverride: 16)
                .frame(maxWidth: .infinity)
                .frame(height: 52 * scale)
                .background(
                    canPost
                        ? AnyShapeStyle(LinearGradient(colors: [FormTokens.accent, FormTokens.pink], startPoint: .leading, endPoint: .trailing))
                        : AnyShapeStyle(FormTokens.line)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(enabled: canPost, pressedScale: 0.98))
        .disabled(!canPost)
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 12 * scale)
    }

    private func post() {
        guard canPost, let i = info else { return }
        captionFocus = false
        let form = PostForm(caption: caption.trimmingCharacters(in: .whitespacesAndNewlines),
                            visibility: visibility, allowComments: comments, allowDuet: duet)
        UploadManager.shared.enqueue(i, form: form)
        onPosted()
    }

    private func dismiss() {
        guard !closing else { return }
        closing = true
        captionFocus = false
        withAnimation(.easeInOut(duration: 0.32)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) { onClose() }
    }

    private func problemText(_ p: MediaProblem) -> String {
        let cfg = MediaConfig.shared
        switch p {
        case .tooLong: return Copy.t(.uploadErrTooLong, ["min": String(cfg.maxVideoSeconds / 60)])
        case .tooLarge: return Copy.t(.uploadErrTooLarge, ["max": String(cfg.maxVideoBytes >> 20)])
        case .unsupported: return Copy.t(.uploadErrUnsupported)
        case .unreadable: return Copy.t(.uploadErrUnreadable)
        case .badAspect: return Copy.t(.uploadErrAspect)
        }
    }
}

func clock(_ ms: Int) -> String {
    let s = ms / 1000
    let m = s / 60
    return m >= 60 ? String(format: "%d:%02d:%02d", m / 60, m % 60, s % 60) : String(format: "%d:%02d", m, s % 60)
}

func humanBytes(_ b: Int64) -> String {
    if b >= 1 << 30 { return String(format: "%.2f GB", Double(b) / Double(1 << 30)) }
    if b >= 1 << 20 { return String(format: "%.1f MB", Double(b) / Double(1 << 20)) }
    if b >= 1 << 10 { return "\(b >> 10) KB" }
    return "\(b) B"
}

private func localDate(_ rfc3339: String) -> String {
    let inF = DateFormatter()
    inF.locale = Locale(identifier: "en_US_POSIX")
    inF.timeZone = TimeZone(identifier: "UTC")
    inF.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    guard let d = inF.date(from: rfc3339) else { return rfc3339 }
    let out = DateFormatter()
    out.dateStyle = .medium
    out.timeStyle = .short
    return out.string(from: d)
}

private extension View {
    @ViewBuilder
    func scrollContentBackgroundHidden() -> some View {
        if #available(iOS 16.0, *) {
            self.scrollContentBackground(.hidden).background(Color.clear)
        } else {
            self.onAppear { UITextView.appearance().backgroundColor = .clear }
        }
    }
}

/// A poster for a local file. Videos take one frame from the container index;
/// images decode a small sample. Both happen off the main thread.
struct FileCover: View {
    let url: URL
    var isVideo: Bool
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .clipped()
        .task(id: url) {
            image = await Task.detached(priority: .userInitiated) { FileCover.load(url, isVideo: isVideo) }.value
        }
    }

    nonisolated static func load(_ url: URL, isVideo: Bool) -> UIImage? {
        if isVideo || ["mp4", "mov", "m4v"].contains(url.pathExtension.lowercased()) {
            let gen = AVAssetImageGenerator(asset: AVURLAsset(url: url))
            gen.appliesPreferredTrackTransform = true
            gen.maximumSize = CGSize(width: 480, height: 480)
            guard let cg = try? gen.copyCGImage(at: CMTime(seconds: 0.5, preferredTimescale: 600), actualTime: nil) else { return nil }
            return UIImage(cgImage: cg)
        }
        guard let src = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 480,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }
}
