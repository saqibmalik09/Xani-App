import SwiftUI

struct Paint {
    var fillColor: Color?
    var fillOpacity: Double = 1
    var gradientColors: [Color]?
    var strokeColor: Color?
    var strokeWidth: CGFloat = 0

    static func fill(_ color: Color, alpha: Double = 1) -> Paint {
        Paint(fillColor: color, fillOpacity: alpha)
    }

    static func stroke(_ color: Color, _ width: CGFloat) -> Paint {
        Paint(strokeColor: color, strokeWidth: width)
    }

    static func strokedFill(_ fill: Color, _ stroke: Color, _ width: CGFloat) -> Paint {
        Paint(fillColor: fill, strokeColor: stroke, strokeWidth: width)
    }

    static func gradientFill(_ colors: [Color], alpha: Double = 1) -> Paint {
        Paint(fillOpacity: alpha, gradientColors: colors)
    }
}

enum VShape {
    case path(Path, Paint)
    case box(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, Paint)
    case circle(CGFloat, CGFloat, CGFloat, Paint)
    case ellipse(CGFloat, CGFloat, CGFloat, CGFloat, Paint)
    case line(CGFloat, CGFloat, CGFloat, CGFloat, Paint)

    /// Path data is parsed once, when the owning `let` is first touched, never per
    /// frame.
    static func data(_ data: String, _ paint: Paint) -> VShape {
        .path(PathData.path(data), paint)
    }

    var paint: Paint {
        switch self {
        case let .path(_, paint): return paint
        case let .box(_, _, _, _, _, paint): return paint
        case let .circle(_, _, _, paint): return paint
        case let .ellipse(_, _, _, _, paint): return paint
        case let .line(_, _, _, _, paint): return paint
        }
    }

    var resolvedPath: Path {
        switch self {
        case let .path(path, _):
            return path
        case let .box(x, y, width, height, radius, _):
            return Path(
                roundedRect: CGRect(x: x, y: y, width: width, height: height),
                cornerRadius: radius
            )
        case let .circle(centerX, centerY, radius, _):
            return Path(
                ellipseIn: CGRect(
                    x: centerX - radius,
                    y: centerY - radius,
                    width: radius * 2,
                    height: radius * 2
                )
            )
        case let .ellipse(centerX, centerY, radiusX, radiusY, _):
            return Path(
                ellipseIn: CGRect(
                    x: centerX - radiusX,
                    y: centerY - radiusY,
                    width: radiusX * 2,
                    height: radiusY * 2
                )
            )
        case let .line(x1, y1, x2, y2, _):
            var path = Path()
            path.move(to: CGPoint(x: x1, y: y1))
            path.addLine(to: CGPoint(x: x2, y: y2))
            return path
        }
    }
}

/// One `Canvas` per icon: a single drawing pass on the GPU, no image decode, no
/// asset catalogue lookup, and crisp at any scale or density.
struct VectorCanvas: View {
    let shapes: [VShape]
    let viewport: CGFloat
    let side: CGFloat

    init(_ shapes: [VShape], viewport: CGFloat, side: CGFloat) {
        self.shapes = shapes
        self.viewport = viewport
        self.side = side
    }

    var body: some View {
        Canvas(opaque: false, rendersAsynchronously: false) { context, size in
            let factor = min(size.width, size.height) / viewport
            context.scaleBy(x: factor, y: factor)

            for shape in shapes {
                let path = shape.resolvedPath
                let paint = shape.paint

                if let colors = paint.gradientColors, !colors.isEmpty {
                    let box = path.boundingRect
                    context.fill(
                        path,
                        with: .linearGradient(
                            Gradient(colors: colors.map { $0.opacity(paint.fillOpacity) }),
                            startPoint: CGPoint(x: box.minX, y: box.minY),
                            endPoint: CGPoint(x: box.maxX, y: box.maxY)
                        )
                    )
                } else if let color = paint.fillColor {
                    context.fill(path, with: .color(color.opacity(paint.fillOpacity)))
                }

                if let stroke = paint.strokeColor, paint.strokeWidth > 0 {
                    context.stroke(
                        path,
                        with: .color(stroke),
                        style: StrokeStyle(
                            lineWidth: paint.strokeWidth,
                            lineCap: .round,
                            lineJoin: .round
                        )
                    )
                }
            }
        }
        .frame(width: side, height: side)
        .allowsHitTesting(false)
    }
}
