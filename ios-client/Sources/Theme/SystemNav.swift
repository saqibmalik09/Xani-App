import SwiftUI
import UIKit

/// Live home-indicator / home-button inset. The window root ignores the system
/// safe area so screens can paint edge-to-edge; interactive content pads with
/// [systemSafeInsets]. `safeAreaInsetsDidChange` covers rotation, Split View,
/// Stage Manager, and chrome changes.
private struct SystemSafeInsetsKey: EnvironmentKey {
    static let defaultValue = EdgeInsets()
}

extension EnvironmentValues {
    var systemSafeInsets: EdgeInsets {
        get { self[SystemSafeInsetsKey.self] }
        set { self[SystemSafeInsetsKey.self] = newValue }
    }
}

func currentWindowSafeInsets() -> EdgeInsets {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let window = scenes
        .flatMap { $0.windows }
        .first(where: { $0.isKeyWindow }) ?? scenes.first?.windows.first
    guard let inset = window?.safeAreaInsets else { return EdgeInsets() }
    return EdgeInsets(top: inset.top, leading: inset.left, bottom: inset.bottom, trailing: inset.right)
}

struct SystemSafeProbe: UIViewRepresentable {
    @Binding var insets: EdgeInsets

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.isUserInteractionEnabled = false
        view.onChange = { insets = $0 }
        return view
    }

    func updateUIView(_ uiView: ProbeView, context: Context) {
        uiView.onChange = { insets = $0 }
    }

    final class ProbeView: UIView {
        var onChange: (EdgeInsets) -> Void = { _ in }
        private var last = UIEdgeInsets(top: -1, left: -1, bottom: -1, right: -1)

        override func didMoveToWindow() {
            super.didMoveToWindow()
            emit()
        }

        override func safeAreaInsetsDidChange() {
            super.safeAreaInsetsDidChange()
            emit()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            emit()
        }

        private func emit() {
            let next = window?.safeAreaInsets ?? safeAreaInsets
            guard next != last else { return }
            last = next
            onChange(
                EdgeInsets(
                    top: next.top,
                    leading: next.left,
                    bottom: next.bottom,
                    trailing: next.right
                )
            )
        }
    }
}

extension View {
    /// Pads interactive content inside the safe rect. Backgrounds ignore the
    /// safe area so status and home-indicator strips match the screen colour.
    func systemContentInsets(_ insets: EdgeInsets) -> some View {
        self.padding(insets)
    }
}
