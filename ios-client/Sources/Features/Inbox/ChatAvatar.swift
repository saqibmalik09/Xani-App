import SwiftUI

/// The circle every inbox surface draws for a person: their picture when they
/// have one, otherwise the initials tile the design already uses. A small green
/// dot marks presence when `showOnline` is set.
struct ChatAvatar: View {
    let chat: InboxChat
    let size: CGFloat
    var showOnline: Bool = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            if let url = MediaConfig.shared.url(chat.avatarUrl.isEmpty ? nil : chat.avatarUrl) {
                RemoteImage(url: url, contentMode: .fill, fallback: Color(rgb: chat.color))
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            } else {
                initials(chat.initials, color: chat.color, group: false, size: size)
            }
            if showOnline, chat.online {
                Circle()
                    .fill(Color(rgb: 0x31D158))
                    .frame(width: size * 0.22, height: size * 0.22)
                    .overlay(Circle().stroke(Light.background, lineWidth: 1.5))
            }
        }
        .frame(width: size, height: size)
    }
}
