import SwiftUI

/// Create icons — UpdatedFigma paths, single paint pass, no emoji stand-ins.
enum CreateIco {
    static func close(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M2 2l14 14M16 2L2 16", .stroke(tint, 2.2)),
        ], viewport: 18, side: side)
    }

    static func music(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M6 12V4l8-2v8", .stroke(tint, 1.6)),
            .circle(4, 12, 2, .fill(tint)),
            .circle(12, 10, 2, .fill(tint)),
        ], viewport: 16, side: side)
    }

    static func flipCam(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(3, 9, 18, 13, 3.5, .stroke(tint, 1.8)),
            .circle(12, 15.5, 3.8, .stroke(tint, 1.8)),
            .circle(12, 15.5, 1.4, .fill(tint.opacity(0.6))),
            .data("M8 9V7.5C8 7 8.6 6.5 9.2 6.5h4.6c.6 0 1.2.5 1.2 1V9", .stroke(tint, 1.6)),
            .data("M22 8l2 3h-3l1.5-3 1.5 3", .stroke(tint, 1.5)),
            .data("M24 18l-2-3h3l-1.5 3-1.5-3", .stroke(tint, 1.5)),
        ], viewport: 30, side: side)
    }

    static func flash(_ tint: Color, side: CGFloat, off: Bool = false) -> some View {
        var shapes: [VShape] = []
        if off {
            shapes.append(.data("M16 3L8 16h7l-3 9 11-13h-7L16 3Z", .stroke(tint, 1.8)))
            shapes.append(.line(4, 24, 24, 4, .stroke(tint, 1.8)))
        } else {
            shapes.append(.data("M16 3L8 16h7l-3 9 11-13h-7L16 3Z", .fill(tint)))
        }
        return VectorCanvas(shapes, viewport: 28, side: side)
    }

    static func timer(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(14, 15, 9, .stroke(tint, 1.8)),
            .data("M14 10v5l3 3", .stroke(tint, 1.8)),
            .data("M11 4h6M14 4v2", .stroke(tint, 1.8)),
        ], viewport: 28, side: side)
    }

    static func grid(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(3, 3, 22, 22, 2, .stroke(tint, 1.6)),
            .line(10.3, 3, 10.3, 25, .stroke(tint, 1.2)),
            .line(17.7, 3, 17.7, 25, .stroke(tint, 1.2)),
            .line(3, 10.3, 25, 10.3, .stroke(tint, 1.2)),
            .line(3, 17.7, 25, 17.7, .stroke(tint, 1.2)),
        ], viewport: 28, side: side)
    }

    static func speed(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M4 16a8 8 0 1 1 14 0", .stroke(tint, 1.6)),
            .data("M11 16L16 9", .stroke(tint, 1.7)),
            .circle(11, 16, 1.4, .fill(tint)),
        ], viewport: 22, side: side)
    }

    static func beauty(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(11, 11, 7, .stroke(tint, 1.5)),
            .data("M8 10.5c.5-.8 1.5-1.3 3-1.3s2.5.5 3 1.3", .stroke(tint, 1.4)),
            .circle(8.5, 9, 0.9, .fill(tint)),
            .circle(13.5, 9, 0.9, .fill(tint)),
            .data("M9 14c1 .8 3 .8 4 0", .stroke(tint, 1.4)),
        ], viewport: 22, side: side)
    }

    static func users(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .circle(8, 8, 3.2, .stroke(tint, 1.5)),
            .data("M3 18c0-2.6 2.2-4.5 5-4.5s5 1.9 5 4.5", .stroke(tint, 1.5)),
            .circle(15.5, 8.5, 2.4, .stroke(tint, 1.4)),
            .data("M14 18c.4-2 2-3.4 4.2-3.4 1.2 0 2.2.4 3 1", .stroke(tint, 1.4)),
        ], viewport: 22, side: side)
    }

    static func photo(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(3, 5.5, 16, 12, 2.2, .stroke(tint, 1.5)),
            .circle(11, 11.5, 3.2, .stroke(tint, 1.5)),
            .data("M7 5.5l1.6-2h4.8L15 5.5", .stroke(tint, 1.5)),
        ], viewport: 22, side: side)
    }

    static func mic(_ tint: Color, side: CGFloat, off: Bool = false) -> some View {
        var shapes: [VShape] = [
            .box(9, 2, 6, 12, 3, .stroke(tint, 1.6)),
            .data("M5 11c0 4 3.1 7 7 7s7-3 7-7", .stroke(tint, 1.6)),
            .line(12, 18, 12, 22, .stroke(tint, 1.6)),
        ]
        if off { shapes.append(.line(4, 4, 20, 20, .stroke(tint, 1.8))) }
        return VectorCanvas(shapes, viewport: 24, side: side)
    }

    static func cam(_ tint: Color, side: CGFloat, off: Bool = false) -> some View {
        var shapes: [VShape] = [
            .box(2, 6, 14, 12, 2, .stroke(tint, 1.6)),
            .data("M16 10l5-3v10l-5-3V10z", .stroke(tint, 1.6)),
        ]
        if off { shapes.append(.line(3, 4, 21, 20, .stroke(tint, 1.8))) }
        return VectorCanvas(shapes, viewport: 24, side: side)
    }

    static func chevron(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .data("M11 4L5 10l6 6", .stroke(tint, 2)),
        ], viewport: 16, side: side)
    }

    static func softMic(_ tint: Color, side: CGFloat) -> some View {
        VectorCanvas([
            .box(9, 2, 8, 14, 4, .stroke(tint, 1.8)),
            .data("M4 13c0 5 4 9 9 9s9-4 9-9", .stroke(tint, 1.8)),
            .line(13, 22, 13, 25, .stroke(tint, 1.8)),
        ], viewport: 26, side: side)
    }
}
