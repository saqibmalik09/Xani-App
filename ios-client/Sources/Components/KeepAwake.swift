import SwiftUI
import UIKit

/// Holds the screen awake while the view is on screen — a broadcast (hosting
/// or watching) must never dim or auto-lock mid-stream; a slept phone stops
/// the camera and freezes the room for everyone. Reference-counted, so
/// overlapping holders (a viewer opening the host flow) release correctly and
/// the rest of the app keeps the system's normal sleep behaviour.
@MainActor
enum ScreenAwake {
    private static var holds = 0

    static func acquire() {
        holds += 1
        UIApplication.shared.isIdleTimerDisabled = true
    }

    static func release() {
        holds = max(0, holds - 1)
        if holds == 0 { UIApplication.shared.isIdleTimerDisabled = false }
    }
}

private struct KeepScreenAwake: ViewModifier {
    func body(content: Content) -> some View {
        content
            .onAppear { ScreenAwake.acquire() }
            .onDisappear { ScreenAwake.release() }
    }
}

extension View {
    /// The screen stays on while this view is visible.
    func keepScreenAwake() -> some View { modifier(KeepScreenAwake()) }
}
