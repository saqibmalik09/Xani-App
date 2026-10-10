import SwiftUI
import AVFoundation
import Photos

private enum CreateLane { case post, live }
/// Figma modes: 5m · 60s · 15s · Photo (Text opens its own screen).
private enum ShootMode: CaseIterable { case fiveMin, sixty, fifteen, photo }

/// This pane stays mounted after close, so every activation must reset
/// `closing` and replay the slide-in — otherwise reopen shows the slid-out
/// frame forever (black screen with no chrome).
struct CreateView: View {
    var active: Bool
    var onClose: () -> Void
    @State private var lane = CreateLane.post
    @State private var closing = false
    @State private var slide: CGFloat = 1

    var body: some View {
        GeometryReader { geo in
            ZStack {
                if lane == .live {
                    CreateLiveFlow(active: active && !closing, onPost: { lane = .post })
                } else {
                    PostStudio(active: active && !closing, onClose: dismiss, onLive: { lane = .live })
                }
            }
            .offset(x: geo.size.width * slide)
            .opacity(0.9 + 0.1 * (1 - Double(slide)))
        }
        .ignoresSafeArea()
        .onAppear { if active { enter() } }
        .onChange(of: active) { on in if on { enter() } }
    }

    private func enter() {
        closing = false
        slide = 1
        withAnimation(.easeInOut(duration: 0.38)) { slide = 0 }
    }

    private func dismiss() {
        guard !closing else { return }
        closing = true
        withAnimation(.easeInOut(duration: 0.36)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.36) {
            onClose()
            // Fresh start on next open: Live flow unmounts, lane is Post.
            lane = .post
        }
    }
}

private struct PostStudio: View {
    var active: Bool
    var onClose: () -> Void
    var onLive: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @StateObject private var snap = CameraSnap()
    @State private var front = false
    @State private var flash = false
    @State private var mode = ShootMode.fifteen
    @State private var textPost = false
    @State private var camOk = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
    @State private var thumb: URL?
    /// A picked or captured file waiting for its caption. Non-nil shows the post form.
    @State private var composing: URL?
    /// The user has watched the preview and tapped Next.
    @State private var previewed = false
    @State private var recording = false
    @State private var elapsed = 0
    @State private var pick = false
    @State private var grid = false
    @State private var timerSec = 0
    @ObservedObject private var session = CreateSession.shared
    @ObservedObject private var postLens = LensStore.post

    private var cameraNeed: Bool { active && !textPost && composing == nil }
    private var capSec: Int {
        switch mode {
        case .fiveMin: return 300
        case .sixty: return 60
        case .fifteen: return 15
        default: return 0
        }
    }

    var body: some View {
        ZStack {
            CreateTokens.scrim.ignoresSafeArea()
            // Figma viewfinder: below top chrome, rounded top — resizeAspectFill stays sharp.
            VStack(spacing: 0) {
                Color.clear.frame(height: insets.top + 56 * scale)
                ZStack {
                    CameraPreview(front: front, enabled: cameraNeed && camOk, snap: snap, lens: LensStore.post)
                    if grid {
                        gridOverlay.allowsHitTesting(false)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
                .clipped()
            }
            .ignoresSafeArea(edges: .bottom)

            if flash {
                Color.white.opacity(0.08).ignoresSafeArea().allowsHitTesting(false)
            }
            VStack(spacing: 0) {
                topBar.padding(.top, insets.top + 6 * scale)
                Spacer(minLength: 0)
            }
            if !recording { sideTools }
            if recording {
                AppText(clock(elapsed), style: Type.labelBold, color: .white, sizeOverride: 16)
                    .padding(.horizontal, 18 * scale)
                    .padding(.vertical, 6 * scale)
                    .background(CreateTokens.chipFill)
                    .clipShape(Capsule())
                    .padding(.top, insets.top + 100 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            VStack {
                Spacer(minLength: 0)
                bottomChrome
            }
            if textPost {
                CreateTextPostView(
                    onClose: { textPost = false },
                    onPost: { body in
                        session.remember(CreateDraft(kind: .text, text: body))
                        // No bytes to move: one request, live on return, fanned
                        // out on the post bus so For You and the grid show it at once.
                        Task {
                            if let post = await Xani.media.postText(body).value {
                                LiveFeed.shared.emitPost(post)
                            }
                        }
                        textPost = false
                        onClose()
                    }
                )
                .transition(.move(edge: .trailing))
                .zIndex(5)
            }
            if let url = composing {
                if !previewed {
                    // Capture / pick → watch it back → Next → details form.
                    MediaPreviewView(
                        url: url,
                        onBack: { composing = nil },
                        onNext: { previewed = true }
                    )
                    .zIndex(6)
                } else {
                    PostFormView(
                        url: url,
                        onClose: { previewed = false },
                        onPosted: {
                            // The strip in the shell owns progress from here; Create goes home.
                            composing = nil
                            previewed = false
                            onClose()
                        }
                    )
                    .zIndex(6)
                }
            }
        }
        .animation(.easeInOut(duration: 0.38), value: textPost)
        .onAppear { requestCam() }
        .onDisappear { LensStore.post.reset() }
        .onChange(of: active) { on in if on { requestCam() } }
        .task(id: recording) {
            guard recording else { return }
            let start = Date()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                elapsed = Int(Date().timeIntervalSince(start))
                if capSec > 0 && elapsed >= capSec {
                    recording = false
                    snap.capture { url in composing = url }
                    return
                }
            }
        }
        .sheet(isPresented: $pick) {
            DeviceGallery { url in
                thumb = url
                composing = url
            }
        }
    }

    private var topBar: some View {
        HStack {
            hit { onClose() } icon: { CreateIco.close(.white, side: 18 * scale) }
            Spacer()
            HStack(spacing: 7 * scale) {
                CreateIco.music(.white, side: 14 * scale)
                AppText(Copy.t(.createAddSound), style: Type.label, color: .white, sizeOverride: 13)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 9 * scale)
            .background(CreateTokens.glass)
            .clipShape(Capsule())
            Spacer()
            hit { front.toggle() } icon: { CreateIco.flipCam(.white, side: 20 * scale) }
        }
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 8 * scale)
        .background(CreateTokens.topBarScrim)
    }

    private var sideTools: some View {
        VStack(spacing: 18 * scale) {
            labeledTool(Copy.t(flash ? .createFlashOn : .createFlashOff), active: flash) {
                flash.toggle()
            } icon: { CreateIco.flash(.white, side: 22 * scale, off: !flash) }
            labeledTool(Copy.t(.createTimer), active: timerSec > 0) {
                timerSec = timerSec == 0 ? 3 : (timerSec == 3 ? 10 : 0)
            } icon: { CreateIco.timer(.white, side: 22 * scale) }
            labeledTool(Copy.t(.createGrid), active: grid) {
                grid.toggle()
            } icon: { CreateIco.grid(grid ? .white : CreateTokens.toolDim, side: 22 * scale) }
            labeledTool(Copy.t(.createSpeed)) { } icon: { CreateIco.speed(.white, side: 22 * scale) }
            labeledTool(Copy.t(.createBeauty), active: !postLens.state.identity) { } icon: { CreateIco.beauty(.white, side: 22 * scale) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
        .padding(.top, insets.top + 108 * scale)
        .padding(.trailing, 12 * scale)
    }

    private var bottomChrome: some View {
        VStack(spacing: 12 * scale) {
            if !recording { modeRail }
            LensTray(controller: LensStore.post, compact: recording)
            // Figma record row: gallery pinned left, capture ring always centered.
            ZStack {
                if !recording {
                    ZStack {
                        Circle().fill(Color(rgb: 0x333333))
                        if let thumb { FileThumb(url: thumb) }
                    }
                    .frame(width: 56 * scale, height: 56 * scale)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(CreateTokens.galleryRing, lineWidth: 2.5 * scale))
                    .onTapGesture { pick = true }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button(action: shutter) {
                    ZStack {
                        if recording && capSec > 0 {
                            Circle()
                                .stroke(Color.white.opacity(0.2), lineWidth: 3.5 * scale)
                                .frame(width: 84 * scale, height: 84 * scale)
                            Circle()
                                .trim(from: 0, to: CGFloat(elapsed) / CGFloat(capSec))
                                .stroke(Color.white.opacity(0.65), style: StrokeStyle(lineWidth: 3.5 * scale, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                                .frame(width: 84 * scale, height: 84 * scale)
                                .animation(.linear(duration: 1), value: elapsed)
                        }
                        if recording {
                            Circle().fill(CreateTokens.recordWash)
                            RoundedRectangle(cornerRadius: 5 * scale, style: .continuous)
                                .fill(Color.white.opacity(0.92))
                                .frame(width: 22 * scale, height: 22 * scale)
                        } else {
                            Circle().stroke(Color.white, lineWidth: (mode == .photo ? 7 : 6) * scale)
                            if mode != .photo {
                                Circle().fill(CreateTokens.recordAccent)
                                    .frame(width: 44 * scale, height: 44 * scale)
                            }
                        }
                    }
                    .frame(width: 92 * scale, height: 92 * scale)
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.92))
            }
            .padding(.horizontal, 28 * scale)
            .frame(height: 88 * scale)

            if !recording {
                HStack(spacing: 36 * scale) {
                    laneTab(Copy.t(.createPost), active: true) {}
                    laneTab(Copy.t(.createGoLive), active: false, onClick: onLive)
                }
                .padding(.top, 4 * scale)
            } else {
                Color.clear.frame(height: 36 * scale)
            }
        }
        .padding(.bottom, insets.bottom + 8 * scale)
        .padding(.top, 16 * scale)
        .frame(maxWidth: .infinity)
        .background(CreateTokens.bottomFade)
    }

    private var modeRail: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4 * scale) {
                // Figma order: 5m · 60s · 15s · Photo · Text (Text opens its own screen)
                ForEach(ShootMode.allCases, id: \.self) { m in
                    let on = mode == m
                    AppText(modeLabel(m), style: on ? Type.labelBold : Type.label, color: on ? CreateTokens.modeOnFg : CreateTokens.modeOffFg, sizeOverride: on ? 15 : 14)
                        .padding(.horizontal, (on ? 14 : 10) * scale)
                        .padding(.vertical, 5 * scale)
                        .background(on ? CreateTokens.modeOnBg : Color.clear)
                        .clipShape(Capsule())
                        .onTapGesture { mode = m; recording = false }
                }
                AppText(Copy.t(.createText), style: Type.label, color: CreateTokens.modeOffFg, sizeOverride: 14)
                    .padding(.horizontal, 10 * scale)
                    .padding(.vertical, 5 * scale)
                    .onTapGesture { textPost = true }
            }
            .padding(.horizontal, 12 * scale)
        }
    }

    private var gridOverlay: some View {
        VStack(spacing: 0) {
            ForEach(0..<3, id: \.self) { _ in
                HStack(spacing: 0) {
                    ForEach(0..<3, id: \.self) { _ in
                        Rectangle().stroke(CreateTokens.gridLine, lineWidth: 0.5)
                    }
                }
            }
        }
    }

    private func hit(action: @escaping () -> Void, @ViewBuilder icon: () -> some View) -> some View {
        Button(action: action) {
            icon()
                .frame(width: 40 * scale, height: 40 * scale)
                .background(CreateTokens.glass)
                .clipShape(Circle())
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.92))
    }

    private func labeledTool(
        _ label: String,
        active: Bool = false,
        action: @escaping () -> Void,
        @ViewBuilder icon: () -> some View
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4 * scale) {
                icon()
                AppText(label, style: Type.micro, color: active ? .white : CreateTokens.toolMuted, sizeOverride: 10)
            }
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.92))
    }

    private func laneTab(_ label: String, active: Bool, onClick: @escaping () -> Void) -> some View {
        Button(action: onClick) {
            VStack(spacing: 3 * scale) {
                AppText(label, style: active ? Type.labelBold : Type.label, color: active ? CreateTokens.laneActive : CreateTokens.laneIdle, sizeOverride: active ? 17 : 15)
                Capsule()
                    .fill(active ? Color.white : Color.clear)
                    .frame(width: 20 * scale, height: 2.5 * scale)
            }
            .padding(.horizontal, 8 * scale)
            .padding(.vertical, 4 * scale)
        }
        .buttonStyle(.plain)
    }

    private func modeLabel(_ m: ShootMode) -> String {
        switch m {
        case .fiveMin: return Copy.t(.create5m)
        case .sixty: return Copy.t(.create60s)
        case .fifteen: return Copy.t(.create15s)
        case .photo: return Copy.t(.createPhoto)
        }
    }

    private func shutter() {
        switch mode {
        case .photo:
            snap.capture { url in composing = url }
        default:
            if recording {
                recording = false
                snap.capture { url in composing = url }
            } else if timerSec > 0 {
                elapsed = 0
                DispatchQueue.main.asyncAfter(deadline: .now() + Double(timerSec)) {
                    if active { recording = true }
                }
            } else {
                elapsed = 0
                recording = true
            }
        }
    }

    private func requestCam() {
        guard cameraNeed else { return }
        AVCaptureDevice.requestAccess(for: .video) { ok in
            DispatchQueue.main.async { camOk = ok }
        }
    }

    private func clock(_ sec: Int) -> String {
        String(format: "%d:%02d", sec / 60, sec % 60)
    }
}

private struct FileThumb: View {
    let url: URL
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .onAppear {
            DispatchQueue.global(qos: .userInitiated).async {
                let loaded = UIImage(contentsOfFile: url.path)
                DispatchQueue.main.async { image = loaded }
            }
        }
    }
}

private struct DeviceGallery: View {
    var onPick: (URL) -> Void
    @Environment(\.presentationMode) private var presentation
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var assets: [PHAsset] = []

    private let cols = [GridItem(.flexible(), spacing: 3), GridItem(.flexible(), spacing: 3), GridItem(.flexible(), spacing: 3), GridItem(.flexible(), spacing: 3)]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { presentation.wrappedValue.dismiss() }) {
                    CreateIco.close(.white, side: 18 * scale).frame(width: 36 * scale, height: 36 * scale)
                }
                .buttonStyle(.plain)
                Spacer()
                AppText(Copy.t(.inboxGallery), style: Type.labelStrong, color: .white, sizeOverride: 16)
                Spacer()
                Color.clear.frame(width: 36 * scale, height: 36 * scale)
            }
            .padding(.horizontal, 12 * scale)
            .padding(.top, insets.top + 8 * scale)
            ScrollView {
                LazyVGrid(columns: cols, spacing: 3) {
                    ForEach(assets, id: \.localIdentifier) { asset in
                        GalleryThumbView(asset: asset, edge: 220)
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                            .onTapGesture {
                                if asset.mediaType == .video {
                                    PHImageManager.default().requestAVAsset(forVideo: asset, options: nil) { av, _, _ in
                                        guard let url = (av as? AVURLAsset)?.url else { return }
                                        DispatchQueue.main.async {
                                            onPick(url)
                                            presentation.wrappedValue.dismiss()
                                        }
                                    }
                                } else {
                                    exportPhotoAsset(asset) { url in
                                        onPick(url)
                                        presentation.wrappedValue.dismiss()
                                    }
                                }
                            }
                    }
                }
                .padding(6 * scale)
            }
        }
        .background(Color(rgb: 0x0C0C0E).ignoresSafeArea())
        .onAppear { load() }
    }

    private func load() {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            guard status == .authorized || status == .limited else { return }
            let opts = PHFetchOptions()
            opts.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
            opts.fetchLimit = 160
            let result = PHAsset.fetchAssets(with: opts)
            var list: [PHAsset] = []
            result.enumerateObjects { asset, _, _ in list.append(asset) }
            DispatchQueue.main.async { assets = list }
        }
    }
}
