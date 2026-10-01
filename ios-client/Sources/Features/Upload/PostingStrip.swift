import SwiftUI

private enum StripTokens {
    static let ink = Color(rgb: 0x111111)
    static let sub = Color(rgb: 0x7A7A8C)
    static let track = Color(rgb: 0xEDEDF3)
    static let accent = Color(rgb: 0x7C3AED)
    static let pink = Color(rgb: 0xEC4899)
    static let good = Color(rgb: 0x10B981)
    static let danger = Color(rgb: 0xDC2626)
}

/// The "Posting… / Posted" strip. Lives in the main shell so it rides along
/// wherever the user goes; one row per task, newest at the bottom. A Posted row
/// removes itself; a failed row offers retry and cancel.
struct PostingStrip: View {
    @ObservedObject private var manager = UploadManager.shared
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        VStack(spacing: 6 * scale) {
            ForEach(manager.tasks.suffix(3)) { t in
                StripRow(task: t)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .padding(.horizontal, 12 * scale)
        .padding(.top, insets.top + 6 * scale)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .allowsHitTesting(!manager.tasks.isEmpty)
        .animation(.easeInOut(duration: 0.28), value: manager.tasks.map { "\($0.id):\($0.state.rawValue)" })
    }
}

private struct StripRow: View {
    let task: UploadTask
    @Environment(\.uiScale) private var scale

    private var tint: Color {
        switch task.state {
        case .posted: return StripTokens.good
        case .failed: return StripTokens.danger
        default: return StripTokens.accent
        }
    }

    private var label: String {
        switch task.state {
        case .queued: return Copy.t(.uploadQueued)
        case .uploading: return Copy.t(.uploadPostingPct, ["pct": String(Int(task.progress * 100))])
        case .completing, .processing: return Copy.t(.uploadProcessing)
        case .posted: return Copy.t(.uploadPosted)
        case .failed: return Copy.t(.uploadFailed)
        }
    }

    var body: some View {
        HStack(spacing: 10 * scale) {
            FileCover(url: UploadManager.shared.fileURL(task), isVideo: task.isVideo)
                .frame(width: 40 * scale, height: 40 * scale)
                .background(Color(rgb: 0xF1F1F6))
                .clipShape(RoundedRectangle(cornerRadius: 8 * scale, style: .continuous))

            VStack(alignment: .leading, spacing: 5 * scale) {
                HStack(spacing: 6 * scale) {
                    if task.state == .posted {
                        AppText("✓", style: Type.labelBold, color: .white, sizeOverride: 10)
                            .frame(width: 16 * scale, height: 16 * scale)
                            .background(tint)
                            .clipShape(Circle())
                    }
                    AppText(label, style: Type.labelBold, color: task.state == .failed ? StripTokens.danger : StripTokens.ink, sizeOverride: 13)
                        .lineLimit(1)
                }
                if task.state == .failed {
                    AppText(task.error, style: Type.caption, color: StripTokens.sub).lineLimit(2)
                } else if task.state != .posted {
                    bar
                } else if !task.form.caption.isEmpty {
                    AppText(task.form.caption, style: Type.caption, color: StripTokens.sub).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            switch task.state {
            case .failed:
                pill(Copy.t(.uploadRetry), StripTokens.accent) { UploadManager.shared.retry(task.id) }
                pill("×", StripTokens.sub) { UploadManager.shared.cancel(task.id) }
            case .posted:
                pill("×", StripTokens.sub) { UploadManager.shared.dismiss(task.id) }
            case .processing:
                EmptyView()
            default:
                pill("×", StripTokens.sub) { UploadManager.shared.cancel(task.id) }
            }
        }
        .padding(8 * scale)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 6 * scale, y: 2 * scale)
    }

    private var bar: some View {
        let indeterminate = task.state == .processing || task.state == .completing
        return GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(StripTokens.track)
                Capsule()
                    .fill(LinearGradient(colors: [tint, StripTokens.pink.opacity(0.7)], startPoint: .leading, endPoint: .trailing))
                    .frame(width: indeterminate ? geo.size.width : geo.size.width * CGFloat(task.progress))
                    .animation(.easeOut(duration: 0.3), value: task.progress)
            }
        }
        .frame(height: 4 * scale)
    }

    private func pill(_ label: String, _ color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(label, style: Type.labelBold, color: color, sizeOverride: 12)
                .padding(.horizontal, 10 * scale)
                .frame(height: 30 * scale)
                .background(color.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.96))
    }
}
