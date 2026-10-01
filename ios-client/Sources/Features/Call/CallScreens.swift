import SwiftUI
import UIKit

private let audioBg = Color(rgb: 0x1A1F2E)
private let red = Color(rgb: 0xEF4444)
private let green = Color(rgb: 0x22C55E)
private let amber = Color(rgb: 0xF59E0B)

/// Line under the name: state first, then the running clock once connected.
private func callStatus(_ ui: CallUi, secs: Int) -> (String, Color) {
    let dim = Color.white.opacity(0.55)
    switch ui.phase {
    case .outgoing: return (Copy.t(ui.ringing ? .callRinging : .inboxCalling), dim)
    case .incoming: return (Copy.t(ui.video ? .callIncomingVideo : .callIncomingAudio), dim)
    case .connecting: return (Copy.t(.inboxConnecting), dim)
    case .connected: return (String(format: "%02d:%02d", secs / 60, secs % 60), dim)
    case .reconnecting: return (Copy.t(.callReconnecting), amber)
    case .ended:
        let key: CopyKey
        switch ui.outcome {
        case .busy: key = .callBusy
        case .noAnswer: key = .callNoAnswer
        case .declined: key = .callDeclined
        case .lost: key = .callLost
        case .failed: key = .callFailed
        case .unavailable: key = .callUnavailable
        case .missed: key = .callMissed
        default: key = .callEnded
        }
        return (Copy.t(key), ui.outcome == .ended ? dim : Color(rgb: 0xFCA5A5))
    }
}

/// Seconds since media connected, ticking only while it matters.
private struct CallClock: ViewModifier {
    let ui: CallUi
    @Binding var secs: Int
    func body(content: Content) -> some View {
        content.onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            guard ui.connectedAt > 0, ui.inMedia else { return }
            secs = Int(ProcessInfo.processInfo.systemUptime - ui.connectedAt)
        }
    }
}

struct AudioCallView: View {
    let ui: CallUi
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var secs = 0
    @State private var addOpen = false
    @State private var pulse = false

    var body: some View {
        let (status, statusColor) = callStatus(ui, secs: secs)
        ZStack {
            audioBg.ignoresSafeArea()
            VStack {
                HStack {
                    CallRound { CallController.shared.setMinimized(true) } icon: {
                        Image(systemName: "chevron.left").foregroundColor(.white)
                    }
                    Spacer()
                    VStack {
                        AppText(ui.peer.name, style: Type.labelBold, color: .white, sizeOverride: 17)
                        AppText(Copy.t(.inboxEncrypted), style: Type.micro, color: Color.white.opacity(0.45), sizeOverride: 11)
                    }
                    Spacer()
                    CallRound { if ui.inMedia { addOpen = true } } icon: { ChatIco.addPerson(.white, side: 22 * scale) }
                }
                .padding(.horizontal, 16 * scale)
                .padding(.top, insets.top)
                Spacer()
                ZStack {
                    if ui.phase != .connected {
                        Circle().fill(Color.white.opacity(0.04))
                            .frame(width: 220 * scale, height: 220 * scale)
                            .scaleEffect(pulse ? 1.06 : 0.96)
                            .animation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true), value: pulse)
                    }
                    PeerFace(peer: ui.peer, size: 140 * scale)
                }
                AppText(status, style: Type.label, color: statusColor).padding(.top, 16 * scale)
                if ui.participants.count > 1 {
                    AppText(Copy.t(.callInCall, ["n": String(ui.participants.count + 1)]), style: Type.micro, color: Color.white.opacity(0.45), sizeOverride: 11)
                }
                Spacer()
                VStack(spacing: 16 * scale) {
                    HStack {
                        CallCtl(Copy.t(.inboxSpeaker), active: ui.speaker, action: CallController.shared.toggleSpeaker) {
                            ChatIco.speaker(.white, side: 26 * scale, dim: !ui.speaker)
                        }
                        CallCtl(Copy.t(.inboxVideo), active: false, action: CallController.shared.upgradeToVideo) {
                            ChatIco.video(.white, side: 26 * scale)
                        }
                        CallCtl(Copy.t(ui.muted ? .inboxUnmute : .inboxMute), active: ui.muted, action: CallController.shared.toggleMute) {
                            ChatIco.mute(.white, side: 26 * scale, muted: ui.muted)
                        }
                    }
                    HStack {
                        CallCtl(Copy.t(.inboxMore), active: false, action: {}) { ChatIco.moreH(.white, side: 26 * scale) }
                        CallCtl(Copy.t(.callAddPerson), active: false, action: { if ui.inMedia { addOpen = true } }) {
                            ChatIco.addPerson(.white, side: 26 * scale)
                        }
                        CallCtl(Copy.t(.inboxEnd), active: false, red: true, action: CallController.shared.end) {
                            ChatIco.hangup(.white, side: 28 * scale)
                        }
                    }
                }
                .padding(20 * scale)
                .background(Color.white.opacity(0.07))
                .clipShape(RoundedRectangle(cornerRadius: 28 * scale))
                .padding(.horizontal, 12 * scale)
                .padding(.bottom, insets.bottom + 16 * scale)
            }
            if addOpen { AddParticipantSheet(ui: ui, onClose: { addOpen = false }) }
        }
        .modifier(CallClock(ui: ui, secs: $secs))
        .onAppear { pulse = true }
    }
}

struct VideoCallView: View {
    let ui: CallUi
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var secs = 0
    @State private var addOpen = false

    var body: some View {
        let (status, statusColor) = callStatus(ui, secs: secs)
        ZStack {
            if ui.remoteVideo, let remote = CallController.shared.remoteView() {
                PlatformView(view: remote).ignoresSafeArea()
            } else {
                Color(rgb: ui.peer.color).opacity(0.18).ignoresSafeArea()
                PeerFace(peer: ui.peer, size: 120 * scale)
            }
            VStack {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4 * scale) {
                        HStack(spacing: 6 * scale) {
                            Circle().fill(ui.phase == .reconnecting ? amber : green).frame(width: 6 * scale, height: 6 * scale)
                            AppText(status, style: Type.labelBold, color: ui.phase == .connected ? .white : statusColor, sizeOverride: 11)
                        }
                        .padding(.horizontal, 10 * scale).padding(.vertical, 4 * scale)
                        .background(Color.black.opacity(0.5))
                        .clipShape(Capsule())
                        AppText(ui.peer.name, style: Type.labelBold, color: .white, sizeOverride: 17).padding(.top, 6 * scale)
                        AppText(Copy.t(.inboxEncrypted), style: Type.micro, color: Color.white.opacity(0.5), sizeOverride: 11)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 12 * scale) {
                        HStack(spacing: 8 * scale) {
                            CallRound(dark: true) { CallController.shared.setMinimized(true) } icon: {
                                Image(systemName: "arrow.down.right.and.arrow.up.left").font(.system(size: 14, weight: .semibold)).foregroundColor(.white)
                            }
                            CallRound(dark: true, action: CallController.shared.switchCamera) { ChatIco.flipCam(.white, side: 22 * scale) }
                        }
                        ZStack {
                            Color(rgb: ui.peer.color)
                            if !ui.cameraOff, let local = CallController.shared.localView() {
                                PlatformView(view: local)
                            } else {
                                AppText(Copy.t(.callYou), style: Type.labelBold, color: .white, sizeOverride: 14)
                            }
                        }
                        .frame(width: 80 * scale, height: 112 * scale)
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale))
                        .overlay(RoundedRectangle(cornerRadius: 14 * scale).stroke(Color.white.opacity(0.18), lineWidth: 2))
                    }
                }
                .padding(.horizontal, 16 * scale)
                .padding(.top, insets.top + 8 * scale)
                Spacer()
                HStack {
                    VideoCtl(action: { if ui.inMedia { addOpen = true } }) { ChatIco.addPerson(.white, side: 22 * scale) }
                    VideoCtl(action: CallController.shared.toggleCamera) { ChatIco.video(.white, side: 22 * scale, off: ui.cameraOff) }
                    VideoCtl(active: ui.speaker, action: CallController.shared.toggleSpeaker) {
                        ChatIco.speaker(ui.speaker ? Color(rgb: 0x111111) : .white, side: 22 * scale, dim: !ui.speaker)
                    }
                    VideoCtl(action: CallController.shared.toggleMute) { ChatIco.mute(.white, side: 22 * scale, muted: ui.muted) }
                    Button(action: CallController.shared.end) {
                        Circle().fill(red).frame(width: 52 * scale, height: 52 * scale)
                            .overlay(ChatIco.hangup(.white, side: 26 * scale))
                    }
                }
                .padding(12 * scale)
                .background(Color(rgb: 0x232323).opacity(0.95))
                .clipShape(Capsule())
                .padding(.horizontal, 12 * scale)
                .padding(.bottom, insets.bottom + 16 * scale)
            }
            if addOpen { AddParticipantSheet(ui: ui, onClose: { addOpen = false }) }
        }
        .background(Color.black.ignoresSafeArea())
        .modifier(CallClock(ui: ui, secs: $secs))
    }
}

/// Full-screen ring: who, what kind, decline / accept.
struct IncomingCallView: View {
    let ui: CallUi
    let onAccept: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var pulse = false

    var body: some View {
        let (status, statusColor) = callStatus(ui, secs: 0)
        ZStack {
            audioBg.ignoresSafeArea()
            VStack {
                Spacer()
                ZStack {
                    Circle().fill(Color.white.opacity(0.05))
                        .frame(width: 200 * scale, height: 200 * scale)
                        .scaleEffect(pulse ? 1.08 : 0.94)
                        .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: pulse)
                    PeerFace(peer: ui.peer, size: 132 * scale)
                }
                AppText(ui.peer.name, style: Type.h1, color: .white, sizeOverride: 26).padding(.top, 20 * scale)
                AppText(status, style: Type.label, color: statusColor).padding(.top, 6 * scale)
                Spacer()
                HStack {
                    CallCtl(Copy.t(.callDecline), active: false, red: true, action: CallController.shared.reject) {
                        ChatIco.hangup(.white, side: 28 * scale)
                    }
                    CallCtl(Copy.t(.callAccept), active: false, green: true, action: onAccept) {
                        if ui.video { ChatIco.video(.white, side: 26 * scale) } else { ChatIco.phone(.white, side: 24 * scale) }
                    }
                }
                .padding(.horizontal, 24 * scale)
                .padding(.bottom, insets.bottom + 40 * scale)
            }
        }
        .onAppear { pulse = true }
    }
}

/// Minimized call: a strip at the top edge; tap returns to the call.
struct CallPill: View {
    let ui: CallUi
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var secs = 0

    var body: some View {
        let (status, _) = callStatus(ui, secs: secs)
        HStack(spacing: 8 * scale) {
            ChatIco.phone(.white, side: 14 * scale)
            AppText(ui.peer.name, style: Type.labelBold, color: .white, sizeOverride: 13)
            Spacer(minLength: 0)
            AppText(status, style: Type.labelBold, color: .white, sizeOverride: 13)
            Button(action: CallController.shared.end) {
                Circle().fill(red).frame(width: 28 * scale, height: 28 * scale).overlay(ChatIco.hangup(.white, side: 16 * scale))
            }
            .padding(.leading, 4 * scale)
        }
        .padding(.horizontal, 14 * scale)
        .padding(.vertical, 9 * scale)
        .background(ui.phase == .reconnecting ? amber : green)
        .clipShape(Capsule())
        .padding(.horizontal, 12 * scale)
        .padding(.top, insets.top + 6 * scale)
        .contentShape(Rectangle())
        .onTapGesture { CallController.shared.setMinimized(false) }
        .modifier(CallClock(ui: ui, secs: $secs))
    }
}

/// Pick someone from the inbox to pull into the call.
private struct AddParticipantSheet: View {
    let ui: CallUi
    let onClose: () -> Void
    @ObservedObject private var inbox = InboxController.shared
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        let candidates = inbox.chats.filter { !$0.group && $0.peerId != 0 && $0.peerId != ui.peer.id && !ui.participants.contains($0.peerId) && !$0.blocked }
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture(perform: onClose)
            VStack(alignment: .leading, spacing: 0) {
                Capsule().fill(Color.white.opacity(0.25)).frame(width: 36 * scale, height: 4 * scale)
                    .frame(maxWidth: .infinity).padding(.top, 14 * scale)
                AppText(Copy.t(.callAddPerson), style: Type.labelBold, color: .white, sizeOverride: 16)
                    .padding(.horizontal, 18 * scale).padding(.top, 12 * scale).padding(.bottom, 8 * scale)
                if candidates.isEmpty {
                    AppText(Copy.t(.callNobodyToAdd), style: Type.label, color: Color.white.opacity(0.55)).padding(18 * scale)
                }
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(candidates) { chat in
                            Button {
                                CallController.shared.addParticipant(chat.peerId)
                                onClose()
                            } label: {
                                HStack(spacing: 12 * scale) {
                                    PeerFace(peer: CallPeer(id: chat.peerId, name: chat.name, initials: chat.initials, color: chat.color, avatarUrl: chat.avatarUrl), size: 40 * scale)
                                    VStack(alignment: .leading, spacing: 2) {
                                        AppText(inbox.displayName(chat), style: Type.labelBold, color: .white, sizeOverride: 15)
                                        AppText(chat.handle, style: Type.micro, color: Color.white.opacity(0.5), sizeOverride: 11)
                                    }
                                    Spacer()
                                    ChatIco.addPerson(Color.white.opacity(0.7), side: 20 * scale)
                                }
                                .padding(.horizontal, 18 * scale).padding(.vertical, 10 * scale)
                            }
                        }
                    }
                }
                .frame(height: 320 * scale)
                .padding(.bottom, insets.bottom + 12 * scale)
            }
            .background(Color(rgb: 0x232838))
            .clipShape(RoundedCorners(radius: 22 * scale))
        }
    }
}

private struct RoundedCorners: Shape {
    let radius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: radius, height: radius)).cgPath)
    }
}

/// Hosts an engine-owned UIView (camera preview / remote stream) inside SwiftUI.
struct PlatformView: UIViewRepresentable {
    let view: UIView
    func makeUIView(context: Context) -> UIView { view }
    func updateUIView(_ uiView: UIView, context: Context) {}
}

private struct PeerFace: View {
    let peer: CallPeer
    let size: CGFloat
    var body: some View {
        if let url = MediaConfig.shared.url(peer.avatarUrl.isEmpty ? nil : peer.avatarUrl) {
            RemoteImage(url: url, contentMode: .fill, fallback: Color(rgb: peer.color))
                .frame(width: size, height: size)
                .clipShape(Circle())
        } else {
            initials(peer.initials, color: peer.color, group: false, size: size)
        }
    }
}

private struct CallRound<Icon: View>: View {
    var dark = false
    let action: () -> Void
    @ViewBuilder let icon: () -> Icon
    @Environment(\.uiScale) private var scale
    var body: some View {
        Button(action: action) {
            Circle().fill(dark ? Color(rgb: 0x282828).opacity(0.85) : Color.white.opacity(0.12))
                .frame(width: 44 * scale, height: 44 * scale)
                .overlay(icon())
        }
    }
}

private struct CallCtl<Icon: View>: View {
    let label: String
    let active: Bool
    var red = false
    var green = false
    let action: () -> Void
    @ViewBuilder let icon: () -> Icon
    @Environment(\.uiScale) private var scale

    init(_ label: String, active: Bool, red: Bool = false, green: Bool = false, action: @escaping () -> Void, @ViewBuilder icon: @escaping () -> Icon) {
        self.label = label; self.active = active; self.red = red; self.green = green; self.action = action; self.icon = icon
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8 * scale) {
                Circle()
                    .fill(red ? Color(rgb: 0xEF4444) : green ? Color(rgb: 0x22C55E) : (active ? Color.white.opacity(0.25) : Color.white.opacity(0.12)))
                    .frame(width: 64 * scale, height: 64 * scale)
                    .overlay(icon())
                AppText(label, style: Type.caption, color: Color.white.opacity(0.75), sizeOverride: 12)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

private struct VideoCtl<Icon: View>: View {
    var active = false
    let action: () -> Void
    @ViewBuilder let icon: () -> Icon
    @Environment(\.uiScale) private var scale
    var body: some View {
        Button(action: action) {
            Circle().fill(active ? Color.white : Color.white.opacity(0.1))
                .frame(width: 52 * scale, height: 52 * scale)
                .overlay(icon())
        }
    }
}
