import SwiftUI

// Same path data as the Android client, so a glyph can never drift between
// platforms. Provider marks keep their official brand colours.

private let googleShapes: [VShape] = [
    .data(
        "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 4.96 2.56 12.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
        .strokedFill(Color(rgb: 0xEA4335), Color(rgb: 0xEA4335), 0.7)
    ),
    .data(
        "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
        .strokedFill(Color(rgb: 0x4285F4), Color(rgb: 0x4285F4), 0.7)
    ),
    .data(
        "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
        .strokedFill(Color(rgb: 0xFBBC05), Color(rgb: 0xFBBC05), 0.7)
    ),
    .data(
        "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 43.04 14.62 48 24 48z",
        .strokedFill(Color(rgb: 0x34A853), Color(rgb: 0x34A853), 0.7)
    ),
]

private let appleShapes: [VShape] = [
    .data(
        "M14.95 10.63c-.02-2.3 1.88-3.4 1.97-3.46-1.08-1.58-2.76-1.8-3.36-1.82-1.43-.15-2.8.84-3.53.84-.73 0-1.85-.82-3.04-.8-1.56.02-3 .91-3.8 2.3-1.63 2.82-.42 6.99 1.17 9.28.78 1.12 1.7 2.38 2.9 2.34 1.17-.05 1.61-.75 3.03-.75 1.41 0 1.81.75 3.04.73 1.26-.02 2.06-1.14 2.83-2.27a10.3 10.3 0 001.27-2.62c-.03-.01-2.44-.93-2.48-3.77zM12.57 3.52C13.24 2.71 13.7 1.6 13.57.46c-.99.04-2.2.66-2.91 1.47-.64.72-1.2 1.88-1.05 2.99 1.1.08 2.23-.56 2.96-1.4z",
        .fill(Color(rgb: 0x111111))
    ),
]

private let facebookShapes: [VShape] = [
    .data(
        "M20 10C20 4.477 15.523 0 10 0S0 4.477 0 10c0 4.991 3.657 9.128 8.438 9.878V12.89H5.898V10h2.54V7.797c0-2.506 1.492-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562V10h2.773l-.443 2.89h-2.33v6.988C16.343 19.128 20 14.991 20 10z",
        .fill(Color(rgb: 0x1877F2))
    ),
]

enum Icon {
    static func google(_ side: CGFloat) -> VectorCanvas {
        VectorCanvas(googleShapes, viewport: 48, side: side)
    }

    static func apple(_ side: CGFloat) -> VectorCanvas {
        VectorCanvas(appleShapes, viewport: 20, side: side)
    }

    static func facebook(_ side: CGFloat) -> VectorCanvas {
        VectorCanvas(facebookShapes, viewport: 20, side: side)
    }

    static func mail(_ side: CGFloat, tint: Color = .white) -> VectorCanvas {
        VectorCanvas(
            [
                .box(2, 4, 16, 12, 3, .stroke(tint, 1.5)),
                .data("M2 7.5l7.2 5a1.4 1.4 0 001.6 0L18 7.5", .stroke(tint, 1.5)),
            ],
            viewport: 20,
            side: side
        )
    }

    static func back(_ side: CGFloat, tint: Color = Color(rgb: 0x333333)) -> VectorCanvas {
        VectorCanvas([.data("M10 3L5 8L10 13", .stroke(tint, 1.8))], viewport: 16, side: side)
    }

    static func help(_ side: CGFloat, tint: Color = Light.sub) -> VectorCanvas {
        VectorCanvas(
            [
                .circle(7, 7, 6, .stroke(tint, 1.3)),
                .data(
                    "M5.5 5.3C5.5 4.45 6.17 3.8 7 3.8s1.5.65 1.5 1.5c0 .7-.45 1.15-1 1.4-.4.18-.5.4-.5.8",
                    .stroke(tint, 1.3)
                ),
                .circle(7, 10, 0.7, .fill(tint)),
            ],
            viewport: 14,
            side: side
        )
    }

    static func eye(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas(
            [
                .ellipse(10, 10, 3.5, 2.5, .stroke(tint, 1.4)),
                .data(
                    "M1 10C3 6.4 6.5 4 10 4s7 2.4 9 6c-2 3.6-5.5 6-9 6s-7-2.4-9-6z",
                    .stroke(tint, 1.4)
                ),
            ],
            viewport: 20,
            side: side
        )
    }

    static func eyeOff(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas(
            [
                .data("M2 2L18 18", .stroke(tint, 1.4)),
                .data("M8.5 8.6A2.5 2.5 0 0011.4 11.5", .stroke(tint, 1.4)),
                .data(
                    "M6 5.4C4 6.8 2.4 8.2 1 10c2 3.6 5.5 6 9 6 1.6 0 3.2-.5 4.6-1.4",
                    .stroke(tint, 1.4)
                ),
                .data(
                    "M9 4.1C9.3 4 9.7 4 10 4c3.5 0 7 2.4 9 6-0.6 1.1-1.4 2.2-2.4 3",
                    .stroke(tint, 1.4)
                ),
            ],
            viewport: 20,
            side: side
        )
    }

    static func arrowRight(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas([.data("M3 8h10M9 4l4 4-4 4", .stroke(tint, 1.8))], viewport: 16, side: side)
    }

    static func check(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas([.data("M2.5 7L5.5 10L11.5 4", .stroke(tint, 1.8))], viewport: 14, side: side)
    }

    static func chevronDown(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas([.data("M1 1l7 7 7-7", .stroke(tint, 1.8))], viewport: 16, side: side)
    }

    static func chevronRight(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas([.data("M6 3l5 5-5 5", .stroke(tint, 1.8))], viewport: 16, side: side)
    }

    static func close(_ side: CGFloat, tint: Color = Color(rgb: 0x333333)) -> VectorCanvas {
        VectorCanvas(
            [
                .data("M4 4l12 12", .stroke(tint, 1.8)),
                .data("M16 4L4 16", .stroke(tint, 1.8)),
            ],
            viewport: 20,
            side: side
        )
    }

    static func search(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas(
            [
                .circle(8.5, 8.5, 5.5, .stroke(tint, 1.6)),
                .data("M12.6 12.6L17 17", .stroke(tint, 1.6)),
            ],
            viewport: 20,
            side: side
        )
    }

    static func wifi(_ side: CGFloat, tint: Color) -> VectorCanvas {
        VectorCanvas(
            [
                .data("M1.6 6.2C5 2.8 11 2.8 14.4 6.2", .stroke(tint, 1.5)),
                .data("M4.1 9.1C6.4 6.9 9.6 6.9 11.9 9.1", .stroke(tint, 1.5)),
                .data("M6.6 11.9C7.7 10.8 8.3 10.8 9.4 11.9", .stroke(tint, 1.5)),
                .circle(8, 14.2, 1, .fill(tint)),
            ],
            viewport: 16,
            side: side
        )
    }

    static func otpBadge(_ side: CGFloat) -> VectorCanvas {
        VectorCanvas(
            [
                .box(4, 8, 28, 20, 5, .stroke(Dark.primarySoft, 1.5)),
                .data("M4 15h28", .stroke(Dark.primarySoft, 1.5)),
                .circle(11, 22, 2, .fill(Dark.markStart)),
                .circle(18, 22, 2, .fill(Dark.markEnd)),
                .circle(25, 22, 2, .fill(Dark.markEnd)),
            ],
            viewport: 36,
            side: side
        )
    }
}
