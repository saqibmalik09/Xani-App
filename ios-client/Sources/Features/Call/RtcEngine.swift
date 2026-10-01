import Foundation
import UIKit

/// Media engine contract. Everything above this line is signalling and UI
/// state; everything below it is libwebrtc (1:1) and the SFU client (rooms).
/// `RtcEngineFactory` returns nil when the build shipped without the media
/// framework, and the app reports calling as unavailable instead of crashing.
enum RtcLink { case new, connecting, connected, disconnected, failed, closed }

protocol RtcPeerListener: AnyObject {
    /// A local SDP is ready to be relayed: `offer` or `answer`.
    func peer(_ peer: RtcPeer, localDescription type: String, sdp: String)
    /// A local ICE candidate; forwarded verbatim as the signal payload.
    func peer(_ peer: RtcPeer, iceCandidate: [String: Any])
    func peer(_ peer: RtcPeer, link: RtcLink)
    func peer(_ peer: RtcPeer, remoteVideo present: Bool)
}

/// One peer-to-peer media session. Created ringing, closed on end.
protocol RtcPeer: AnyObject {
    func offer(iceRestart: Bool)
    func setRemote(type: String, sdp: String)
    func addCandidate(_ candidate: [String: Any])
    func setMuted(_ muted: Bool)
    func setVideoEnabled(_ enabled: Bool)
    func switchCamera()
    func setMaxVideoKbps(_ kbps: Int)
    func localView() -> UIView?
    func remoteView() -> UIView?
    func close()
}

protocol RtcRoomListener: AnyObject {
    func room(_ room: RtcRoom, link: RtcLink)
    func room(_ room: RtcRoom, participants: [Int64])
    func room(_ room: RtcRoom, userId: Int64, remoteVideo present: Bool)
}

/// One SFU room session: group calls, live host, live guest or low-latency viewer.
protocol RtcRoom: AnyObject {
    func setMuted(_ muted: Bool)
    func setVideoEnabled(_ enabled: Bool)
    func switchCamera()
    func localView() -> UIView?
    func remoteView(userId: Int64) -> UIView?
    func close()
}

protocol RtcEngine: AnyObject {
    func createPeer(ice: [IceServerData], relayOnly: Bool, video: Bool, listener: RtcPeerListener) -> RtcPeer
    /// `publish` false is a viewer seat: no capture, no mic permission needed.
    func joinRoom(url: String, token: String, video: Bool, publish: Bool, listener: RtcRoomListener) -> RtcRoom
}

/// Resolves the engine at runtime. The real adapter lands in
/// `Features/Call/Rtc/WebRtcEngine.swift` behind `#if canImport(WebRTC)` once
/// the libwebrtc xcframework built from native-core/xani-rtc is linked.
enum RtcEngineFactory {
    #if canImport(WebRTC)
    static let available = true
    static func create() -> RtcEngine? { WebRtcEngine() }
    #else
    static let available = false
    static func create() -> RtcEngine? { nil }
    #endif
}
