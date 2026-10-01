import AVFoundation
import SwiftUI

/// Asks for mic (and camera for video) then runs the action on the main
/// thread. Both dial and accept go through here so the controller can assume
/// permissions are granted and fail fast when they are not.
enum CallPermissions {
    static func request(video: Bool, then: @escaping () -> Void) {
        AVCaptureDevice.requestAccess(for: .audio) { mic in
            guard mic else { return }
            guard video else { DispatchQueue.main.async(execute: then); return }
            AVCaptureDevice.requestAccess(for: .video) { cam in
                guard cam else { return }
                DispatchQueue.main.async(execute: then)
            }
        }
    }
}

/// The one call surface for the whole app. Sits above every tab so a call that
/// rings in on Home, or was minimized from Inbox, renders the same way.
struct CallHost: View {
    @ObservedObject private var calls = CallController.shared

    var body: some View {
        if let ui = calls.state {
            Group {
                if ui.phase == .incoming {
                    IncomingCallView(ui: ui, onAccept: { CallPermissions.request(video: ui.video) { CallController.shared.accept() } })
                } else if ui.minimized, ui.active {
                    VStack { CallPill(ui: ui); Spacer(minLength: 0) }
                } else if ui.video {
                    VideoCallView(ui: ui)
                } else {
                    AudioCallView(ui: ui)
                }
            }
            .transition(.opacity)
        }
    }
}
