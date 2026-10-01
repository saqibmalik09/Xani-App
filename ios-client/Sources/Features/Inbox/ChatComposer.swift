import SwiftUI
import CoreLocation

struct ChatComposer: View {
    @ObservedObject var inbox: InboxController
    let chat: InboxChat
    let vanish: Bool
    @Binding var reply: ChatMessage?
    @Binding var edit: ChatMessage?
    var focused: FocusState<Bool>.Binding
    @Binding var injectPaste: String?
    @Environment(\.uiScale) private var scale

    @State private var draft = ""
    @State private var attach = false
    @State private var recording = false
    @State private var paused = false
    @State private var secs = 0
    @State private var camera = false
    @State private var gallery = false
    @State private var contacts = false
    @State private var emojiPad = false
    @State private var docs = false
    @State private var poll = false
    @State private var eventOpen = false
    @State private var peek: URL?
    @State private var mediaQueue: [URL] = []
    @State private var recorder = VoiceRecorder()
    @State private var pending: PendingAttach?
    @State private var locator = LocationPin()

    private var iconTint: Color { vanish ? Color.white.opacity(0.5) : Color(rgb: 0x888888) }

    var body: some View {
        VStack(spacing: 0) {
            if let edit {
                HStack {
                    RoundedRectangle(cornerRadius: 2 * scale).fill(Gradients.brand).frame(width: 3 * scale, height: 28 * scale)
                    VStack(alignment: .leading, spacing: 2) {
                        AppText(Copy.t(.inboxEdit), style: Type.labelBold, color: Dark.primary, sizeOverride: 11)
                        AppText(edit.text, style: Type.caption, color: vanish ? Color.white.opacity(0.7) : Light.sub, lineLimit: 1)
                    }
                    Spacer()
                    Button { self.edit = nil; draft = "" } label: {
                        AppText("×", style: Type.labelBold, color: Light.sub, sizeOverride: 18)
                    }
                }
                .padding(.horizontal, 12 * scale)
                .padding(.vertical, 8 * scale)
            } else if let reply {
                HStack(spacing: 8 * scale) {
                    RoundedRectangle(cornerRadius: 2 * scale).fill(Gradients.brand).frame(width: 3 * scale, height: 36 * scale)
                    VStack(alignment: .leading, spacing: 2) {
                        AppText(Copy.t(.inboxReply), style: Type.labelBold, color: Dark.primary, sizeOverride: 11)
                        AppText(reply.text.isEmpty ? preview(of: reply) : reply.text, style: Type.caption, color: vanish ? Color.white.opacity(0.55) : Light.sub, lineLimit: 2)
                    }
                    Spacer(minLength: 0)
                    if reply.kind == .image || reply.kind == .video || reply.kind == .ai {
                        LocalThumb(url: reply.mediaURL, fallback: reply.color)
                            .frame(width: 40 * scale, height: 40 * scale)
                            .clipShape(RoundedRectangle(cornerRadius: 8 * scale))
                            .opacity(0.82)
                    }
                    Button { self.reply = nil } label: {
                        AppText("×", style: Type.labelBold, color: Light.sub, sizeOverride: 18)
                    }
                }
                .padding(.horizontal, 12 * scale)
                .padding(.vertical, 8 * scale)
                .background(vanish ? Color.white.opacity(0.08) : Color(rgb: 0xF3EEF8))
            }
            if !mediaQueue.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8 * scale) {
                        ForEach(mediaQueue, id: \.self) { url in
                            ZStack(alignment: .topTrailing) {
                                LocalThumb(url: url, fallback: 0x8B5CF6)
                                    .frame(width: 64 * scale, height: 64 * scale)
                                    .clipShape(RoundedRectangle(cornerRadius: 10 * scale))
                                    .onTapGesture { peek = url }
                                Button { mediaQueue.removeAll { $0 == url } } label: {
                                    Circle().fill(Color.black.opacity(0.7)).frame(width: 20 * scale, height: 20 * scale)
                                        .overlay(ChatIco.close(.white, side: 10 * scale))
                                }
                                .padding(2 * scale)
                            }
                        }
                    }
                    .padding(.horizontal, 12 * scale)
                    .padding(.vertical, 8 * scale)
                }
            } else if let hold = pending {
                HStack(spacing: 10 * scale) {
                    if let url = hold.url, hold.kind == .image || hold.kind == .video || hold.kind == .ai {
                        LocalThumb(url: url, fallback: 0x8B5CF6)
                            .frame(width: 48 * scale, height: 48 * scale)
                            .clipShape(RoundedRectangle(cornerRadius: 8 * scale))
                            .onTapGesture { peek = url }
                    } else if hold.kind == .contact {
                        Group {
                            if let url = hold.url {
                                LocalThumb(url: url, fallback: 0x8B5CF6)
                            } else {
                                Dark.primary.opacity(0.18)
                            }
                        }
                        .frame(width: 52 * scale, height: 52 * scale)
                        .clipShape(Circle())
                        .overlay {
                            if hold.url == nil {
                                AppText(String((hold.label.isEmpty ? "C" : hold.label).prefix(1)).uppercased(), style: Type.labelBold, color: Dark.primary, sizeOverride: 18)
                            }
                        }
                    } else {
                        Circle().fill(Color(rgb: 0xF3F4F6)).frame(width: 48 * scale, height: 48 * scale)
                            .overlay(ChatIco.attach(4, side: 22 * scale))
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        AppText(
                            hold.kind == .video ? Copy.t(.inboxVideo)
                            : hold.kind == .document ? Copy.t(.inboxDocument)
                            : hold.kind == .contact ? (hold.label.isEmpty ? Copy.t(.inboxContact) : hold.label)
                            : Copy.t(.inboxPhoto),
                            style: Type.labelBold,
                            color: Dark.primary,
                            sizeOverride: 12
                        )
                        AppText(Copy.t(.inboxWriteMsg), style: Type.caption, color: vanish ? Color.white.opacity(0.7) : Light.sub, lineLimit: 1)
                    }
                    Spacer()
                    Button { pending = nil } label: {
                        Circle().fill(Color(rgb: 0xE5E7EB)).frame(width: 28 * scale, height: 28 * scale)
                            .overlay(ChatIco.close(Color(rgb: 0x555555), side: 14 * scale))
                    }
                }
                .padding(.horizontal, 12 * scale)
                .padding(.vertical, 8 * scale)
            }
            if recording {
                voiceBar
            } else {
                HStack(alignment: .bottom, spacing: 8 * scale) {
                    HStack(spacing: 6 * scale) {
                        Button {
                            focused.wrappedValue = false
                            emojiPad.toggle()
                            attach = false
                        } label: {
                            ChatIco.emoji(emojiPad ? Dark.primary : iconTint, side: 22 * scale)
                        }
                        .buttonStyle(PressScaleStyle())
                        TextField(Copy.t(.inboxTypeMessage), text: $draft)
                            .onChange(of: draft) { next in if !next.isEmpty { inbox.typing(chatId: chat.id) } }
                            .font(.system(size: 15 * scale, weight: .medium))
                            .foregroundColor(vanish ? .white : Light.text)
                            .submitLabel(.send)
                            .textInputAutocapitalization(.sentences)
                            .textSelection(.enabled)
                            .focused(focused)
                            .onSubmit(sendNow)
                            .onChange(of: injectPaste) { val in
                                guard let t = val, !t.isEmpty else { return }
                                draft += t
                                injectPaste = nil
                            }
                        Button { attach.toggle(); emojiPad = false; focused.wrappedValue = false } label: {
                            ChatIco.paperclip(attach ? Dark.primary : iconTint, side: 22 * scale)
                        }
                        .buttonStyle(PressScaleStyle())
                        if draft.trimmingCharacters(in: .whitespaces).isEmpty && pending == nil && mediaQueue.isEmpty {
                            Button {
                                camera = true
                                attach = false
                            } label: {
                                ChatIco.camera(iconTint, side: 22 * scale)
                            }
                            .buttonStyle(PressScaleStyle())
                        }
                    }
                    .padding(.leading, 14 * scale)
                    .padding(.trailing, 10 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(vanish ? Color.white.opacity(0.06) : Color(rgb: 0xF0F0F0))
                    .clipShape(RoundedRectangle(cornerRadius: 26 * scale, style: .continuous))
                    Button {
                        sendNow()
                    } label: {
                        Circle()
                            .fill(Gradients.brand)
                            .frame(width: 46 * scale, height: 46 * scale)
                            .overlay {
                                if pending != nil || !mediaQueue.isEmpty || !draft.trimmingCharacters(in: .whitespaces).isEmpty {
                                    ChatIco.send(.white, side: 18 * scale)
                                } else {
                                    ChatIco.mic(.white, side: 22 * scale)
                                }
                            }
                            .shadow(color: Dark.primary.opacity(0.35), radius: 8 * scale, y: 2 * scale)
                    }
                    .buttonStyle(PressScaleStyle(pressedScale: 0.9))
                }
                .padding(.horizontal, 10 * scale)
                .padding(.vertical, 8 * scale)
            }
            if attach && !recording { attachSheet }
            if emojiPad && !recording { emojiPadView }
        }
        .background(vanish ? Color(rgb: 0x0D0D12) : Color.white)
        .fullScreenCover(isPresented: $camera) {
            ChatCameraSheet(
                onShot: { url in
                    mediaQueue.append(url)
                    camera = false
                    attach = false
                },
                onGallery: {
                    camera = false
                    gallery = true
                },
                onClose: { camera = false },
                onPicked: { urls in
                    mediaQueue.append(contentsOf: urls)
                    camera = false
                    attach = false
                }
            )
        }
        .sheet(isPresented: $gallery) {
            DeviceGalleryView(
                onPick: { url in
                    mediaQueue.append(url)
                    gallery = false
                    attach = false
                },
                onClose: { gallery = false }
            )
        }
        .sheet(isPresented: $contacts) {
            ContactPickView(
                onPick: { names in
                    if names.count == 1 {
                        pending = PendingAttach(kind: .contact, url: nil, label: names[0])
                    } else {
                        for name in names { inbox.send(chatId: chat.id, text: name, kind: .contact) }
                    }
                    contacts = false
                    attach = false
                },
                onClose: { contacts = false }
            )
        }
        .sheet(isPresented: $docs) {
            DocumentPick { url in
                pending = PendingAttach(kind: .document, url: url)
                attach = false
            }
        }
        .fullScreenCover(isPresented: $poll) {
            CreatePollView(
                onLeave: { poll = false },
                onSend: { body in
                    inbox.send(chatId: chat.id, text: body, kind: .poll)
                    poll = false
                }
            )
        }
        .fullScreenCover(isPresented: $eventOpen) {
            CreateEventView(
                onLeave: { eventOpen = false },
                onSend: { body in
                    inbox.send(chatId: chat.id, text: body, kind: .event)
                    eventOpen = false
                }
            )
        }
        .fullScreenCover(item: Binding(
            get: { peek.map { IdentifiedURL(url: $0) } },
            set: { peek = $0?.url }
        )) { item in
            ZStack(alignment: .topLeading) {
                Color.black.ignoresSafeArea()
                LocalThumb(url: item.url, fallback: 0x111111).ignoresSafeArea()
                Button { peek = nil } label: {
                    Circle().fill(Color.black.opacity(0.45)).frame(width: 40 * scale, height: 40 * scale)
                        .overlay(ChatIco.close(.white, side: 16 * scale))
                }
                .padding(16 * scale)
            }
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            if recording && !paused { secs += 1 }
        }
        .onDisappear { recorder.cancel() }
        .onChange(of: edit?.id) { _ in
            if let edit {
                draft = edit.text
                focused.wrappedValue = true
            }
        }
    }

    private func sendNow() {
        let cap = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        if let editing = edit {
            if !cap.isEmpty {
                inbox.patch(chatId: chat.id, msgId: editing.id, text: cap)
            }
            edit = nil
            draft = ""
            attach = false
            return
        }
        if !mediaQueue.isEmpty {
            for (i, url) in mediaQueue.enumerated() {
                // The picker hands back pictures and clips through the same
                // callback. Sending a clip as a picture is refused by the
                // server (and rightly so), so the kind comes from the file.
                inbox.send(chatId: chat.id, text: i == 0 ? cap : "", kind: attachmentKind(url), media: url, replyTo: reply?.id)
            }
            mediaQueue = []
            pending = nil
            draft = ""
            reply = nil
            attach = false
            return
        }
        if let p = pending {
            let body: String
            if p.kind == .contact {
                body = [p.label, cap].filter { !$0.isEmpty }.joined(separator: "\n")
            } else {
                body = cap
            }
            inbox.send(chatId: chat.id, text: body, kind: p.kind, media: p.url, replyTo: reply?.id)
            pending = nil
            draft = ""
            reply = nil
            attach = false
            return
        }
        if !cap.isEmpty {
            sendText()
            return
        }
        if recorder.start() {
            recording = true
            paused = false
            secs = 0
            attach = false
        }
    }

    private func sendText() {
        let t = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        inbox.send(chatId: chat.id, text: t, replyTo: reply?.id)
        draft = ""
        reply = nil
        attach = false
    }

    private var voiceBar: some View {
        let waves: [CGFloat] = [14, 22, 10, 28, 18, 32, 12, 26, 20, 16, 30, 8, 24, 34, 14, 20, 28, 10, 18, 24]
        let stamp = String(format: "%02d:%02d", secs / 60, secs % 60)
        let ink: Color = vanish ? .white : Light.text
        return VStack(spacing: 6 * scale) {
            HStack(spacing: 10 * scale) {
                if paused {
                    Button(action: { paused = false }) { ChatIco.play(ink, side: 22 * scale) }
                    Circle().fill(Dark.primary).frame(width: 8 * scale, height: 8 * scale)
                } else {
                    AppText(stamp, style: Type.labelBold, color: ink, sizeOverride: 14)
                }
                TimelineView(.animation(minimumInterval: 0.05)) { context in
                    let t = context.date.timeIntervalSinceReferenceDate
                    HStack(spacing: 1.5 * scale) {
                        ForEach(waves.indices, id: \.self) { i in
                            let live: CGFloat = paused
                                ? waves[i] * 0.7
                                : waves[i] * (0.42 + 0.58 * abs(sin(t * 3.2 + Double(i) * 0.13)))
                            Capsule()
                                .fill(paused && i >= 10 ? Color(rgb: 0xD1D5DB) : Dark.primary)
                                .frame(width: 3 * scale, height: max(6, live) * scale)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 36 * scale)
                }
                if paused {
                    AppText(stamp, style: Type.labelBold, color: ink, sizeOverride: 14)
                } else {
                    Text("1×")
                        .font(.system(size: 10 * scale, weight: .bold))
                        .foregroundColor(Color(rgb: 0x888888))
                        .frame(width: 26 * scale, height: 26 * scale)
                        .overlay(Circle().stroke(Color(rgb: 0x888888), lineWidth: 1.5))
                }
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, 8 * scale)
            HStack {
                Button {
                    recorder.cancel()
                    recording = false
                    secs = 0
                } label: {
                    Circle().fill(Color(rgb: 0xFEE2E2)).frame(width: 52 * scale, height: 52 * scale)
                        .overlay(ChatIco.trash(Color(rgb: 0xEF4444), side: 22 * scale))
                }
                Button { paused.toggle() } label: {
                    HStack(spacing: 8 * scale) {
                        if paused { ChatIco.mic(ink, side: 18 * scale) }
                        else { ChatIco.pause(ink, side: 16 * scale) }
                        AppText(paused ? Copy.t(.inboxResume) : Copy.t(.inboxPause), style: Type.labelBold, color: ink, sizeOverride: 15)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 46 * scale)
                    .background(vanish ? Color.white.opacity(0.08) : Color(rgb: 0xF5F5F5))
                    .clipShape(Capsule())
                }
                .padding(.horizontal, 14 * scale)
                Button {
                    let url = recorder.stop()
                    recording = false
                    let label = String(format: "%02d:%02d", secs / 60, secs % 60)
                    secs = 0
                    inbox.send(chatId: chat.id, text: "\(Copy.t(.inboxVoiceHint)) · \(label)", kind: .voice, media: url, replyTo: reply?.id)
                    reply = nil
                } label: {
                    Circle().fill(Gradients.brand).frame(width: 46 * scale, height: 46 * scale)
                        .overlay(ChatIco.send(.white, side: 18 * scale))
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.bottom, 8 * scale)
        }
    }

    private var attachSheet: some View {
        let items: [(CopyKey, Int, () -> Void)] = [
            (.inboxGallery, 0, { gallery = true; attach = false }),
            (.inboxCamera, 1, { camera = true; attach = false }),
            (.inboxContact, 2, { contacts = true; attach = false }),
            (.inboxLocation, 3, {
                locator.request { link in
                    inbox.send(chatId: chat.id, text: link, kind: .location)
                    attach = false
                }
            }),
            (.inboxDocument, 4, { docs = true; attach = false }),
            (.inboxPoll, 5, { poll = true; attach = false }),
            (.inboxEvent, 6, { eventOpen = true; attach = false }),
            (.inboxAi, 7, { inbox.send(chatId: chat.id, text: "AI image", kind: .ai); attach = false }),
        ]
        return VStack(spacing: 8 * scale) {
            Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 36 * scale, height: 4 * scale).padding(.top, 8 * scale)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 12 * scale) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button(action: item.2) {
                        VStack(spacing: 6 * scale) {
                            Circle().fill(vanish ? Color.white.opacity(0.08) : Color(rgb: 0xF5F5F5))
                                .frame(width: 52 * scale, height: 52 * scale)
                                .overlay(ChatIco.attach(item.1, side: 26 * scale))
                            AppText(Copy.t(item.0), style: Type.micro, color: vanish ? Color.white.opacity(0.75) : Light.textSoft, sizeOverride: 11)
                        }
                    }
                    .buttonStyle(PressScaleStyle())
                }
            }
            HStack {
                AppText(Copy.t(.inboxRecents), style: Type.labelBold, color: vanish ? Color.white.opacity(0.7) : Color(rgb: 0x555555), sizeOverride: 12)
                Spacer()
                Button { gallery = true; attach = false } label: {
                    AppText(Copy.t(.inboxSeeAll), style: Type.labelBold, color: Dark.primary, sizeOverride: 12)
                }
            }
            .padding(.horizontal, 16 * scale)
            .padding(.bottom, 8 * scale)
        }
        .padding(.horizontal, 8 * scale)
        .background(vanish ? Color(rgb: 0x0D0D12) : Color.white)
        .padding(.bottom, 4 * scale)
    }

    private var emojiPadView: some View {
        let emojis = ["😀", "😂", "🥰", "😍", "🤩", "😘", "😊", "😉", "😭", "😡", "🤯", "🤔", "🙌", "👏", "🙏", "👍", "👎", "🔥", "❤️", "💯", "✨", "🎉", "😎", "💀"]
        return VStack(spacing: 8 * scale) {
            Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 36 * scale, height: 4 * scale).padding(.top, 8 * scale)
            ForEach(0..<((emojis.count + 7) / 8), id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(0..<8, id: \.self) { col in
                        let i = row * 8 + col
                        if i < emojis.count {
                            Button {
                                draft += emojis[i]
                            } label: {
                                AppText(emojis[i], style: Type.label, sizeOverride: 26)
                                    .frame(maxWidth: .infinity, minHeight: 40 * scale)
                            }
                        } else {
                            Spacer().frame(maxWidth: .infinity)
                        }
                    }
                }
            .padding(.bottom, 8 * scale)
        }
        .background(vanish ? Color(rgb: 0x0D0D12) : Color.white)
    }
}

final class LocationPin: NSObject, CLLocationManagerDelegate {
    private let mgr = CLLocationManager()
    private var onFix: ((String) -> Void)?

    override init() {
        super.init()
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func request(_ done: @escaping (String) -> Void) {
        onFix = done
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) { status = mgr.authorizationStatus }
        else { status = CLLocationManager.authorizationStatus() }
        switch status {
        case .authorizedWhenInUse, .authorizedAlways: mgr.requestLocation()
        default: mgr.requestWhenInUseAuthorization()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let c = locations.last?.coordinate else { return }
        onFix?("https://maps.google.com/?q=\(c.latitude),\(c.longitude)")
        onFix = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        onFix?("📍 Location")
        onFix = nil
    }
}

struct CreatePollView: View {
    var onLeave: () -> Void
    var onSend: (String) -> Void
    @Environment(\.uiScale) private var scale
    @State private var question = ""
    @State private var options = ["", ""]
    @State private var multi = false

    private var ready: Bool { !question.trimmingCharacters(in: .whitespaces).isEmpty && options.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count >= 2 }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onLeave) { ChatIco.close(Color(rgb: 0x111111), side: 16 * scale) }
                    .frame(width: 36 * scale, height: 36 * scale)
                AppText(Copy.t(.inboxLeavePoll), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                Spacer()
                Button {
                    let opts = options.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
                    var body = question.trimmingCharacters(in: .whitespacesAndNewlines)
                    for o in opts { body += "\n" + o }
                    if multi { body += "\n#multi" }
                    onSend(body)
                } label: {
                    AppText(Copy.t(.inboxSendBtn), style: Type.labelBold, color: ready ? .white : Color(rgb: 0x9CA3AF), sizeOverride: 13)
                        .padding(.horizontal, 14 * scale).padding(.vertical, 8 * scale)
                        .background(ready ? Dark.primary : Color(rgb: 0xE5E7EB))
                        .clipShape(Capsule())
                }
                .disabled(!ready)
            }
            .padding(8 * scale)
            ScrollView {
                VStack(alignment: .leading, spacing: 12 * scale) {
                    AppText(Copy.t(.inboxCreatePoll), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 22)
                    AppText(Copy.t(.inboxAskQuestion), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                    TextField(Copy.t(.inboxAskQuestion), text: $question)
                        .padding(12 * scale)
                        .background(Color(rgb: 0xF5F5F5))
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    HStack {
                        AppText(Copy.t(.inboxOptions), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                        Spacer()
                        Button { if options.count < 12 { options.append("") } } label: {
                            ChatIco.plus(Dark.primary, side: 18 * scale)
                        }
                    }
                    ForEach(options.indices, id: \.self) { i in
                        HStack {
                            TextField("\(Copy.t(.inboxOptions)) \(i + 1)", text: $options[i])
                                .padding(12 * scale)
                                .background(Color(rgb: 0xF5F5F5))
                                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                            if i >= 2 {
                                Button { options.remove(at: i) } label: {
                                    ChatIco.minus(Color(rgb: 0xEF4444), side: 16 * scale)
                                }
                                .frame(width: 36 * scale, height: 36 * scale)
                            }
                        }
                    }
                    HStack {
                        AppText(Copy.t(.inboxMultiple), style: Type.labelStrong, color: Color(rgb: 0x111111), sizeOverride: 14)
                        Spacer()
                        Button { multi.toggle() } label: {
                            Capsule().fill(multi ? Dark.primary : Color(rgb: 0xE5E7EB))
                                .frame(width: 44 * scale, height: 26 * scale)
                                .overlay(alignment: multi ? .trailing : .leading) {
                                    Circle().fill(Color.white).frame(width: 20 * scale, height: 20 * scale).padding(3 * scale)
                                }
                        }
                    }
                }
                .padding(.horizontal, 16 * scale)
                .padding(.bottom, 24 * scale)
            }
        }
        .background(Color.white.ignoresSafeArea())
    }
}

private struct IdentifiedURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

struct CreateEventView: View {
    var seed: EventPayload? = nil
    var canEdit: Bool = true
    var onLeave: () -> Void
    var onSend: (String) -> Void
    @Environment(\.uiScale) private var scale
    @State private var name = ""
    @State private var desc = ""
    @State private var loc = ""
    @State private var at = Date().addingTimeInterval(60 * 60)
    @State private var reminder = 60
    @State private var remindOpen = false

    private var ready: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty }
    private var tooSoon: Bool { at < Date().addingTimeInterval(15 * 60) }
    private var reminders: [(Int, CopyKey)] {
        [(60, .inboxHourBefore), (0, .inboxNever), (15, .inboxMinBefore), (1440, .inboxDayBefore)]
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onLeave) { ChatIco.close(Color(rgb: 0x111111), side: 16 * scale) }
                    .frame(width: 36 * scale, height: 36 * scale)
                AppText(Copy.t(.inboxLeaveEvent), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                Spacer()
                if canEdit {
                    Button {
                        onSend(encodeEvent(EventPayload(name: name, desc: desc, at: at.timeIntervalSince1970, loc: loc, reminder: reminder)))
                    } label: {
                        AppText(Copy.t(.inboxSendBtn), style: Type.labelBold, color: ready && !tooSoon ? .white : Color(rgb: 0x9CA3AF), sizeOverride: 13)
                            .padding(.horizontal, 14 * scale).padding(.vertical, 8 * scale)
                            .background(ready && !tooSoon ? Dark.primary : Color(rgb: 0xE5E7EB))
                            .clipShape(Capsule())
                    }
                    .disabled(!ready || tooSoon)
                } else {
                    Button {
                        openCalendar(EventPayload(name: name, desc: desc, at: at.timeIntervalSince1970, loc: loc, reminder: reminder))
                    } label: {
                        AppText(Copy.t(.inboxAddCalendar), style: Type.labelBold, color: .white, sizeOverride: 13)
                            .padding(.horizontal, 14 * scale).padding(.vertical, 8 * scale)
                            .background(Dark.primary).clipShape(Capsule())
                    }
                }
            }
            .padding(8 * scale)
            ScrollView {
                VStack(alignment: .leading, spacing: 12 * scale) {
                    AppText(Copy.t(canEdit && seed != nil ? .inboxEditEvent : .inboxCreateEvent), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 22)
                    TextField(Copy.t(.inboxEventName), text: $name).disabled(!canEdit)
                        .font(.system(size: 28 * scale, weight: .bold))
                        .foregroundColor(Color(rgb: 0x111111))
                    AppText(Copy.t(.inboxEventDesc), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                    TextField(Copy.t(.inboxEventDesc), text: $desc, axis: .vertical).disabled(!canEdit)
                        .lineLimit(3...6)
                        .onChange(of: desc) { v in
                            let n = v.split(whereSeparator: \.isWhitespace).count
                            if n > 100 { desc = v.split(whereSeparator: \.isWhitespace).prefix(100).joined(separator: " ") }
                        }
                        .padding(12 * scale).background(Color(rgb: 0xF5F5F5)).clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    AppText(Copy.t(.inboxEventWhen), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                    DatePicker("", selection: $at, displayedComponents: [.date, .hourAndMinute]).labelsHidden().disabled(!canEdit)
                    if tooSoon {
                        AppText(Copy.t(.inboxEventSoon), style: Type.caption, color: Color(rgb: 0xEF4444), sizeOverride: 12)
                    }
                    AppText(Copy.t(.inboxLocation), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                    TextField(Copy.t(.inboxLocation), text: $loc).disabled(!canEdit)
                        .padding(12 * scale).background(Color(rgb: 0xF5F5F5)).clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    AppText(Copy.t(.inboxReminder), style: Type.labelStrong, color: Color(rgb: 0x555555), sizeOverride: 13)
                    Button { if canEdit { remindOpen.toggle() } } label: {
                        HStack {
                            AppText(Copy.t(reminders.first(where: { $0.0 == reminder })?.1 ?? .inboxHourBefore), style: Type.label, color: Color(rgb: 0x111111), sizeOverride: 14)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12 * scale, weight: .semibold))
                                .foregroundColor(Color(rgb: 0x9CA3AF))
                        }
                        .padding(12 * scale)
                        .background(Color(rgb: 0xF5F5F5))
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    }
                    if remindOpen && canEdit {
                        VStack(spacing: 0) {
                            ForEach(reminders, id: \.0) { item in
                                Button {
                                    reminder = item.0
                                    remindOpen = false
                                } label: {
                                    HStack {
                                        AppText(Copy.t(item.1), style: Type.label, color: Color(rgb: 0x111111), sizeOverride: 14)
                                        Spacer()
                                        if reminder == item.0 { ChatIco.tick(Dark.primary, side: 14 * scale) }
                                    }
                                    .padding(12 * scale)
                                }
                            }
                        }
                        .background(Color(rgb: 0xF5F5F5))
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    }
                }
                .padding(.horizontal, 16 * scale)
                .padding(.bottom, 24 * scale)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            if let seed {
                name = seed.name
                desc = seed.desc
                loc = seed.loc
                reminder = seed.reminder
                if seed.at > 0 { at = Date(timeIntervalSince1970: seed.at) }
            }
        }
    }
}
