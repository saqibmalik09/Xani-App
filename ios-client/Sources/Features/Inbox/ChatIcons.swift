import SwiftUI

enum ChatIco {
    static func emoji(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(11, 11, 9, .stroke(tint, 1.5)),
            .data("M7.5 13c.8 1.2 2 2 3.5 2s2.7-0.8 3.5-2", .stroke(tint, 1.4)),
            .circle(8, 9, 1.1, .fill(tint)),
            .circle(14, 9, 1.1, .fill(tint)),
        ], viewport: 22, side: side)
    }

    static func paperclip(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M18 11.5L10 19.5C7.8 21.7 4.3 21.7 2 19.5C-0.2 17.3-0.2 13.8 2 11.5L11 2.5C12.5 1 14.8 1 16.3 2.5C17.8 4 17.8 6.3 16.3 7.8L8.3 15.8C7.5 16.6 6.3 16.6 5.5 15.8C4.7 15 4.7 13.8 5.5 13L12.5 6", .stroke(tint, 1.5)),
        ], viewport: 22, side: side)
    }

    static func camera(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(2, 5, 18, 14, 2.5, .stroke(tint, 1.6)),
            .circle(11, 13, 4, .stroke(tint, 1.6)),
            .data("M7.5 5l1.8-2.2h3.4L14.5 5", .stroke(tint, 1.6)),
        ], viewport: 22, side: side)
    }

    static func mic(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(8, 2, 6, 12, 3, .fill(tint)),
            .data("M4 11C4 15.4 7.1 19 11 19C14.9 19 18 15.4 18 11", .stroke(tint, 1.8)),
            .line(11, 19, 11, 21, .stroke(tint, 1.8)),
        ], viewport: 22, side: side)
    }

    static func send(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M2 9L16 2l-4.5 13.5L9 9 2 9z", .fill(tint))], viewport: 18, side: side)
    }

    static func sendTri(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M5 3L19 11 5 19z", .fill(tint))], viewport: 22, side: side)
    }

    static func play(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M4 2L20 11 4 20z", .fill(tint))], viewport: 22, side: side)
    }

    static func forwardFill(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M2.5 8.5L7 4v3c5 0 7.5 3 7.5 7.5C12.5 11 10 9.5 7 9.5v3L2.5 8.5z", .fill(tint))], viewport: 17, side: side)
    }

    static func restrict(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(9, 9, 7, .stroke(tint, 1.4)),
            .line(3, 3, 15, 15, .stroke(tint, 1.4)),
        ], viewport: 18, side: side)
    }

    static func blockUser(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(9, 9, 7, .stroke(tint, 1.4)),
            .data("M6 6h6M6 9h6M6 12h4", .stroke(tint, 1.3)),
            .line(3, 3, 15, 15, .stroke(tint, 1.4)),
        ], viewport: 18, side: side)
    }

    static func exportChat(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(4, 10, 10, 8, 2, .stroke(tint, 1.4)),
            .data("M9 10V3M6 6l3-3 3 3", .stroke(tint, 1.4)),
        ], viewport: 18, side: side)
    }

    static func phone(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M3.5 3.5C5 2 6.5 3.5 6.5 3.5L8 5c0.7 0.7 0.7 1.8 0 2.5L7 8.5C7.8 9.8 9.2 11.2 10.5 12l1-0.9c0.7-0.7 1.8-0.7 2.5 0l1.5 1.5c1.5 1.5 0 3 0 3C8 19.5-0.5 11 3.5 3.5z", .fill(tint)),
        ], viewport: 19, side: side)
    }

    static func video(_ tint: Color, side: CGFloat, off: Bool = false) -> some View {
        var shapes: [VShape] = [
            .box(2, 7, 15, 12, 3, .fill(tint, alpha: off ? 0.35 : 1)),
            .data("M17 11l7-4v12l-7-4V11Z", .fill(tint, alpha: off ? 0.35 : 1)),
        ]
        if off { shapes.append(.line(2, 24, 24, 2, .stroke(tint, 2))) }
        return VectorCanvas(shapes, viewport: 26, side: side)
    }

    static func hangup(_ tint: Color = .white, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M2 16.5C5.5 10 11 6 14 6C17 6 22.5 10 26 16.5L23 19C22.5 19.5 21.5 19.5 21 18.5L19.5 15.5C19 14.5 19.5 13.5 20.5 13C19 11 16.5 9.5 14 9.5C11.5 9.5 9 11 7.5 13C8.5 13.5 9 14.5 8.5 15.5L7 18.5C6.5 19.5 5.5 19.5 5 19L2 16.5Z", .fill(tint)),
        ], viewport: 28, side: side)
    }

    static func speaker(_ tint: Color, side: CGFloat, dim: Bool = false) -> some View {
        VectorCanvas([
            .data("M4 9H8L14 4v18L8 17H4V9Z", .fill(tint, alpha: dim ? 0.6 : 1)),
            .data("M17 9C18.5 10.5 18.5 15.5 17 17", .stroke(tint, 1.8)),
            .data("M20 6C23 8.5 23 17.5 20 20", .stroke(tint, 1.8)),
        ], viewport: 26, side: side)
    }

    static func mute(_ tint: Color, side: CGFloat, muted: Bool = false) -> some View {
        var shapes: [VShape] = [
            .box(9, 2, 8, 14, 4, .fill(tint, alpha: muted ? 0.4 : 1)),
            .data("M5 13C5 17.4 8.6 21 13 21C17.4 21 21 17.4 21 13", .stroke(tint, 2)),
            .line(13, 21, 13, 24, .stroke(tint, 2)),
        ]
        if muted { shapes.append(.line(3, 3, 23, 23, .stroke(tint, 2.2))) }
        return VectorCanvas(shapes, viewport: 26, side: side)
    }

    static func moreH(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(7, 13, 2.2, .fill(tint, alpha: 0.7)),
            .circle(13, 13, 2.2, .fill(tint, alpha: 0.7)),
            .circle(19, 13, 2.2, .fill(tint, alpha: 0.7)),
        ], viewport: 26, side: side)
    }

    static func moreV(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(9, 3.5, 1.5, .fill(tint)),
            .circle(9, 9, 1.5, .fill(tint)),
            .circle(9, 14.5, 1.5, .fill(tint)),
        ], viewport: 18, side: side)
    }

    static func share(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M13 3V17M8 8l5-5 5 5", .stroke(tint, 2)),
            .data("M5 14V21H21V14", .stroke(tint, 2)),
        ], viewport: 26, side: side)
    }

    static func person(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(11, 8, 4, .stroke(tint, 1.6)),
            .data("M3 20C3 16.5 6.6 14 11 14s8 2.5 8 6", .stroke(tint, 1.6)),
        ], viewport: 22, side: side)
    }

    static func search(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(9, 9, 6, .stroke(tint, 1.6)),
            .data("M15 15L20 20", .stroke(tint, 1.6)),
        ], viewport: 22, side: side)
    }

    static func bell(_ tint: Color, side: CGFloat, muted: Bool = false) -> some View {
        var shapes: [VShape] = [
            .data("M11 3C7 3 4 5.7 4 9v5l-2 2.5h18L18 14V9C18 5.7 15 3 11 3z", .stroke(tint, 1.6)),
            .data("M9 19a2 2 0 004 0", .stroke(tint, 1.6)),
        ]
        if muted { shapes.append(.line(3, 3, 19, 19, .stroke(tint, 1.6))) }
        return VectorCanvas(shapes, viewport: 22, side: side)
    }

    static func trash(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .line(4, 6, 18, 6, .stroke(tint, 1.8)),
            .box(8.5, 3, 5, 3, 1, .stroke(tint, 1.6)),
            .box(5.5, 6, 11, 13, 2, .stroke(tint, 1.6)),
            .line(9, 9, 9, 16, .stroke(tint, 1.5)),
            .line(13, 9, 13, 16, .stroke(tint, 1.5)),
        ], viewport: 22, side: side)
    }

    static func unsend(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M7 6H3v4", .stroke(tint, 1.7)),
            .data("M3 10c2.2-4.2 9.5-4.6 12 1.2 2 4.6-2.4 8.8-7.2 8", .stroke(tint, 1.7)),
        ], viewport: 22, side: side)
    }

    static func pause(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(4, 3, 4, 14, 1.5, .fill(tint)),
            .box(12, 3, 4, 14, 1.5, .fill(tint)),
        ], viewport: 20, side: side)
    }

    static func flipCam(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M4 11C4 7.7 7.1 5 11 5C14.9 5 18 7.7 18 11", .stroke(tint, 1.6)),
            .data("M18 11C18 14.3 14.9 17 11 17C7.1 17 4 14.3 4 11", .stroke(tint, 1.6)),
            .data("M15 2l3 3-3 3", .stroke(tint, 1.7)),
            .data("M7 20l-3-3 3-3", .stroke(tint, 1.7)),
        ], viewport: 22, side: side)
    }

    static func addPerson(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z", .stroke(tint, 1.6)),
            .data("M2 20c0-3.9 3.1-7 7-7s7 3.1 7 7", .stroke(tint, 1.6)),
            .data("M18 8v6M15 11h6", .stroke(tint, 1.7)),
        ], viewport: 22, side: side)
    }

    static func close(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M4 4l8 8M12 4l-8 8", .stroke(tint, 1.5))], viewport: 16, side: side)
    }

    static func doubleTick(read: Bool, side: CGFloat) -> some View {
        let c = read ? Color(rgb: 0x3B82F6) : Color(rgb: 0x9CA3AF)
        return VectorCanvas([
            .data("M2.5 10.2l3.4 3.3 6.6-8.2", .stroke(c, 1.45)),
            .data("M7.2 10.2l3.4 3.3 6.6-8.2", .stroke(c, 1.45)),
        ], viewport: 18, side: side)
    }

    /// WhatsApp-style state for one of my messages: clock while sending, one
    /// grey tick on the server, two grey on their phone, two blue once read.
    @ViewBuilder
    static func deliveryMark(_ d: Delivery, side: CGFloat) -> some View {
        switch d {
        case .sending:
            VectorCanvas([
                .circle(9, 9, 6.2, .stroke(Color(rgb: 0x9CA3AF), 1.3)),
                .data("M9 5.4V9l2.6 1.6", .stroke(Color(rgb: 0x9CA3AF), 1.3)),
            ], viewport: 18, side: side)
        case .sent:
            VectorCanvas([
                .data("M4.5 10.2l3.4 3.3 6.6-8.2", .stroke(Color(rgb: 0x9CA3AF), 1.45)),
            ], viewport: 18, side: side)
        case .delivered:
            doubleTick(read: false, side: side)
        case .seen:
            doubleTick(read: true, side: side)
        }
    }

    static func plus(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .line(9, 3, 9, 15, .stroke(tint, 2)),
            .line(3, 9, 15, 9, .stroke(tint, 2)),
        ], viewport: 18, side: side)
    }

    static func minus(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.line(3, 9, 15, 9, .stroke(tint, 2))], viewport: 18, side: side)
    }

    static func reply(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M3 14l1.5-4L12 3l3 3-7.5 7.5L3 14z", .stroke(tint, 1.4)),
            .data("M10 5l3 3", .stroke(tint, 1.4)),
        ], viewport: 18, side: side)
    }

    static func forward(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M2 9l5-5v3c4 0 8 2.5 9 8-2-3.5-5-5-9-5v3L2 9z", .stroke(tint, 1.4))], viewport: 18, side: side)
    }

    static func copyLines(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(3, 3, 12, 12, 2, .stroke(tint, 1.4)),
            .data("M6 7h6M6 10h4", .stroke(tint, 1.3)),
        ], viewport: 18, side: side)
    }

    static func pin(_ tint: Color, side: CGFloat, filled: Bool = false) -> some View {
        VectorCanvas([
            .box(8, 2, 2, 8, 1, filled ? .fill(tint) : .stroke(tint, 1.4)),
            .data("M5 10h8l-1 3H6L5 10z", filled ? .fill(tint) : .stroke(tint, 1.4)),
            .line(9, 13, 9, 17, .stroke(tint, 1.6)),
        ], viewport: 18, side: side)
    }

    static func imagesGrid(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(1, 1, 6, 6, 1, .stroke(tint, 1.3)),
            .box(9, 1, 6, 6, 1, .stroke(tint, 1.3)),
            .box(1, 9, 6, 6, 1, .stroke(tint, 1.3)),
            .box(9, 9, 6, 6, 1, .stroke(tint, 1.3)),
        ], viewport: 16, side: side)
    }

    static func doc(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(3, 1, 10, 14, 1.5, .stroke(tint, 1.3)),
            .line(6, 6, 10, 6, .stroke(tint, 1.2)),
            .line(6, 9, 10, 9, .stroke(tint, 1.2)),
        ], viewport: 16, side: side)
    }

    static func link(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M7 9H5C3.3 9 2 7.7 2 6C2 4.3 3.3 3 5 3h3", .stroke(tint, 1.4)),
            .data("M9 7h2c1.7 0 3 1.3 3 3s-1.3 3-3 3H9", .stroke(tint, 1.4)),
        ], viewport: 16, side: side)
    }

    static func info(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(9, 9, 7, .stroke(tint, 1.4)),
            .data("M9 8v5M9 6v.5", .stroke(tint, 1.6)),
        ], viewport: 18, side: side)
    }

    static func translate(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(2, 3, 14, 12, 2, .stroke(tint, 1.4)),
            .data("M6 8h2l1.5 4L11 6l1.5 6H15", .stroke(tint, 1.2)),
        ], viewport: 18, side: side)
    }

    static func report(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M3.5 15.5h11l-5.5-12-5.5 12z", .stroke(tint, 1.4)),
            .data("M9 5v5M9 13v.5", .stroke(tint, 1.8)),
        ], viewport: 18, side: side)
    }

    static func download(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M9 3v9M6 9l3 3 3-3", .stroke(tint, 1.4)),
            .data("M3 15h12", .stroke(tint, 1.4)),
        ], viewport: 18, side: side)
    }

    static func tick(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([.data("M1 4l3 3 5-6", .stroke(tint, 1.5))], viewport: 10, side: side)
    }

    static func shareStory(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(4, 3, 14, 16, 3.5, .stroke(tint, 1.6)),
            .data("M11 8v6M8 11h6", .stroke(tint, 1.7)),
        ], viewport: 22, side: side)
    }

    static func shareWhatsApp(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38c1.45.79 3.08 1.21 4.74 1.21 5.46 0 9.91-4.45 9.91-9.91 0-2.65-1.03-5.14-2.9-7.01A9.82 9.82 0 0012.04 2zm5.16 14.24c-.22.62-1.28 1.18-1.78 1.26-.46.07-1.05.1-1.69-.1-1.02-.33-2.34-.96-3.8-2.35-1.75-1.66-2.89-3.72-3.15-4.37-.26-.64-.53-1.68-.2-2.4.16-.36.5-.7.86-.82.18-.06.38-.07.54 0 .16.06.3.22.43.48l.86 1.86c.11.24.05.42-.08.58l-.42.5c-.1.12-.12.26-.03.4.28.48.86 1.16 1.58 1.78.76.64 1.6 1.08 2.12 1.28.16.06.34.02.46-.12l.48-.58c.12-.14.3-.18.48-.1l1.92.92c.2.1.3.24.28.44-.02.18-.08.4-.26.9z", .fill(tint)),
        ], viewport: 24, side: side)
    }

    static func shareStatus(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M11 3.6a7.4 7.4 0 1 1-6.5 3.8", .stroke(tint, 1.6)),
            .circle(11, 11, 3.2, .fill(tint)),
        ], viewport: 22, side: side)
    }

    static func shareSnap(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M11 4c2.8 0 4.8 2.2 4.8 5.1 0 2.2-.8 3.4-1.2 4.2.9.4 1.9 1 1.9 1.8 0 .7-.8 1.1-1.8 1.1-.6 0-1.1-.1-1.6-.3-.5 1.2-1.3 1.8-2.1 1.8s-1.6-.6-2.1-1.8c-.5.2-1 .3-1.6.3-1 0-1.8-.4-1.8-1.1 0-.8 1-1.4 1.9-1.8-.4-.8-1.2-2-1.2-4.2C6.2 6.2 8.2 4 11 4z", .fill(tint)),
        ], viewport: 22, side: side)
    }

    static func shareMessenger(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M11 3.5C6.8 3.5 3.5 6.5 3.5 10.2c0 2.2 1.1 4.1 2.8 5.4V19l2.6-1.4c.7.2 1.4.3 2.1.3 4.2 0 7.5-3 7.5-6.7S15.2 3.5 11 3.5z", .stroke(tint, 1.5)),
            .data("M7.5 11.5L10 9l2 2 2.5-2.5", .stroke(tint, 1.5)),
        ], viewport: 22, side: side)
    }

    static func shareSms(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(3.5, 4.5, 15, 10, 2.5, .stroke(tint, 1.55)),
            .data("M8 14.5L6 18.2l4-2.4", .stroke(tint, 1.5)),
            .data("M8 9.5h6", .stroke(tint, 1.4)),
        ], viewport: 22, side: side)
    }

    static func shareFacebook(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M13.2 4H11c-2 0-3.4 1.4-3.4 3.5V10H5.8v2.8h1.8V20h3V12.8h2.4l.5-2.8h-2.9V7.6c0-.6.4-1 1-1h2.6V4z", .fill(tint)),
        ], viewport: 22, side: side)
    }

    static func clock(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(9, 9, 7, .stroke(tint, 1.4)),
            .data("M9 5v4l2.5 2.5", .stroke(tint, 1.4)),
        ], viewport: 18, side: side)
    }

    static func pencil(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M13 3l2 2-8 8H5v-2L13 3z", .stroke(tint, 1.4)),
            .data("M2 15h14", .stroke(tint, 1.3)),
        ], viewport: 18, side: side)
    }

    static func mediaVis(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(2, 4, 14, 11, 2, .stroke(tint, 1.4)),
            .data("M2 8l5 4 3-3 4 4", .stroke(tint, 1.3)),
            .circle(6, 7, 1.2, .fill(tint)),
        ], viewport: 18, side: side)
    }

    static func createGroup(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(7, 7, 3, .stroke(tint, 1.5)),
            .circle(14, 7, 2.5, .stroke(tint, 1.3)),
            .data("M1 17c0-3 2.7-5 6-5s6 2 6 5", .stroke(tint, 1.5)),
            .data("M14 10c2 0.5 4 1.8 4 4.5", .stroke(tint, 1.3)),
            .data("M17 7v4M15 9h4", .stroke(tint, 1.3)),
        ], viewport: 20, side: side)
    }

    static func attach(_ kind: Int, side: CGFloat) -> some View {
        switch kind {
        case 0:
            return AnyView(VectorCanvas([
                .box(2, 4, 22, 18, 3, .fill(Color(rgb: 0x4F46E5))),
                .data("M2 12l7 5 5-4 5 5 5-3", .stroke(.white, 1.6)),
                .circle(8, 9, 2, .fill(.white, alpha: 0.8)),
            ], viewport: 26, side: side))
        case 1:
            return AnyView(VectorCanvas([
                .box(2, 5, 22, 17, 3, .fill(Color(rgb: 0xEC4899))),
                .circle(13, 13.5, 4.5, .stroke(.white, 1.8)),
                .circle(13, 13.5, 2, .fill(.white, alpha: 0.5)),
                .data("M8 6l2-2h6l2 2", .stroke(.white, 1.5)),
            ], viewport: 26, side: side))
        case 2:
            return AnyView(VectorCanvas([
                .circle(13, 13, 11, .fill(Color(rgb: 0x10B981))),
                .circle(13, 10, 3.5, .stroke(.white, 1.5)),
                .data("M6 22c0-3.5 3.1-6 7-6s7 2.5 7 6", .stroke(.white, 1.5)),
            ], viewport: 26, side: side))
        case 3:
            return AnyView(VectorCanvas([
                .circle(13, 11, 7, .stroke(Color(rgb: 0xEF4444), 1.8)),
                .circle(13, 11, 2.5, .fill(Color(rgb: 0xEF4444))),
                .data("M13 18l-3 5h6l-3-5z", .fill(Color(rgb: 0xEF4444))),
            ], viewport: 26, side: side))
        case 4:
            return AnyView(VectorCanvas([
                .box(4, 3, 18, 20, 3, .fill(Color(rgb: 0xF97316))),
                .data("M8 9h10M8 13h10M8 17h6", .stroke(.white, 1.6)),
            ], viewport: 26, side: side))
        case 5:
            return AnyView(VectorCanvas([
                .box(3, 5, 20, 16, 3, .fill(Color(rgb: 0x8B5CF6))),
                .data("M8 11h10M8 15h6", .stroke(.white, 1.6)),
                .circle(6, 11, 1.5, .fill(.white)),
                .circle(6, 15, 1.5, .fill(.white)),
            ], viewport: 26, side: side))
        case 6:
            return AnyView(VectorCanvas([
                .box(3, 3, 20, 20, 3, .fill(Color(rgb: 0xEF4444))),
                .box(7, 6, 12, 3, 1, .fill(.white)),
                .circle(9, 5, 1.5, .fill(.white)),
                .circle(17, 5, 1.5, .fill(.white)),
                .data("M7 13h12M7 17h8", .stroke(.white, 1.5)),
            ], viewport: 26, side: side))
        default:
            return AnyView(VectorCanvas([
                .box(3, 3, 20, 20, 3, .fill(Color(rgb: 0x06B6D4))),
                .data("M8 13l3 3 7-7", .stroke(.white, 2)),
            ], viewport: 26, side: side))
        }
    }
}

struct VanishProgressRing: View {
    let progress: CGFloat
    let vanish: Bool

    var body: some View {
        let accent = vanish ? Color(rgb: 0xEC4899) : Color(rgb: 0x8B5CF6)
        let track = vanish ? Color.white.opacity(0.12) : Color.black.opacity(0.1)
        ZStack {
            Circle().stroke(track, lineWidth: 3)
            Circle()
                .trim(from: 0, to: min(1, max(0, progress)))
                .stroke(accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
            if progress >= 1 {
                Path { p in
                    p.move(to: CGPoint(x: 13, y: 20))
                    p.addLine(to: CGPoint(x: 17, y: 24))
                    p.addLine(to: CGPoint(x: 27, y: 16))
                }
                .stroke(accent, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            } else {
                Path { p in
                    p.move(to: CGPoint(x: 20, y: 24))
                    p.addLine(to: CGPoint(x: 20, y: 16))
                    p.move(to: CGPoint(x: 16, y: 20))
                    p.addLine(to: CGPoint(x: 20, y: 16))
                    p.addLine(to: CGPoint(x: 24, y: 20))
                }
                .stroke(progress > 0 ? accent : track, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: 40, height: 40)
    }
}
