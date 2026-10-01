import Combine
import Darwin
import Network
import SwiftUI

struct LinkSpeed: Equatable {
    var online: Bool
    var wifi: Bool
    var label: String

    static let offline = LinkSpeed(online: false, wifi: false, label: "Offline")
    static let waiting = LinkSpeed(online: true, wifi: true, label: "…")
}

func formatLinkSpeed(_ kbps: Int) -> String {
    if kbps < 1 { return "0 kb/s" }
    if kbps < 1000 { return "\(kbps) kb/s" }
    let mbps = Double(kbps) / 1000
    return mbps >= 10 ? "\(Int(mbps)) Mb/s" : String(format: "%.1f Mb/s", mbps)
}

/// Interface receive counters. No sockets, no probe.
private func interfaceRxBytes() -> UInt64 {
    var addrs: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&addrs) == 0 else { return 0 }
    defer { freeifaddrs(addrs) }
    var total: UInt64 = 0
    var cursor = addrs
    while let ifa = cursor {
        defer { cursor = ifa.pointee.ifa_next }
        let flags = Int32(ifa.pointee.ifa_flags)
        guard flags & IFF_UP == IFF_UP, flags & IFF_LOOPBACK == 0 else { continue }
        guard ifa.pointee.ifa_addr.pointee.sa_family == UInt8(AF_LINK) else { continue }
        guard let raw = ifa.pointee.ifa_data else { continue }
        total += UInt64(raw.assumingMemoryBound(to: if_data.self).pointee.ifi_ibytes)
    }
    return total
}

/// One 5s sampler for the whole app. First live figure appears after 5s.
final class LinkSpeedMonitor: ObservableObject {
    @Published private(set) var speed = LinkSpeed.waiting

    private let pathMonitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "app.xani.link", qos: .utility)
    private var timer: DispatchSourceTimer?
    private var lastRx: UInt64 = 0
    private var lastAt: TimeInterval = 0
    private var wifi = true
    private var online = false

    init() {
        pathMonitor.pathUpdateHandler = { [weak self] path in
            self?.wifi = path.usesInterfaceType(.wifi)
            self?.online = path.status == .satisfied
        }
        pathMonitor.start(queue: queue)
        lastRx = interfaceRxBytes()
        lastAt = ProcessInfo.processInfo.systemUptime
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 5, repeating: 5)
        timer.setEventHandler { [weak self] in self?.tick() }
        timer.resume()
        self.timer = timer
    }

    deinit {
        timer?.cancel()
        pathMonitor.cancel()
    }

    private func tick() {
        let now = ProcessInfo.processInfo.systemUptime
        let rx = interfaceRxBytes()
        let dtMs = max((now - lastAt) * 1000, 1)
        let delta = rx >= lastRx ? rx - lastRx : 0
        lastRx = rx
        lastAt = now
        let kbps = Int((Double(delta) * 8) / dtMs)
        let next: LinkSpeed = online
            ? LinkSpeed(online: true, wifi: wifi, label: formatLinkSpeed(kbps))
            : .offline
        DispatchQueue.main.async { [weak self] in
            guard let self, self.speed != next else { return }
            self.speed = next
        }
    }
}

struct NetworkSpeedBadge: View {
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @StateObject private var monitor = LinkSpeedMonitor()

    var body: some View {
        HStack(spacing: 4 * scale) {
            Icon.wifi(10 * scale, tint: .white)
            AppText(
                monitor.speed.label,
                style: Type.micro,
                color: .white,
                sizeOverride: 9
            )
        }
        .padding(.horizontal, 7 * scale)
        .padding(.vertical, 3 * scale)
        .background(Color.black.opacity(0.4))
        .clipShape(Capsule())
        .padding(.leading, 8 * scale + insets.leading)
        .padding(.top, 56 * scale + insets.top)
        .allowsHitTesting(false)
    }
}
