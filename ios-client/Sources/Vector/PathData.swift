import CoreGraphics
import SwiftUI

/// Minimal SVG path-data parser so both clients can share the exact same geometry
/// strings exported from Figma. Android gets this from Compose's PathParser; iOS has
/// no equivalent in the SDK, and pulling in an SVG library to draw a back chevron
/// would be an absurd trade.
///
/// Supports M m L l H h V v C c S s Q q T t A a Z z, which covers everything Figma
/// emits for icon-scale artwork.
enum PathData {
    static func path(_ data: String) -> Path {
        var path = Path()
        var scanner = Scanner(data)

        var current = CGPoint.zero
        var subpathStart = CGPoint.zero
        var lastControl: CGPoint?
        var lastQuadControl: CGPoint?
        var previousCommand: Character = " "

        while let command = scanner.nextCommand(repeating: previousCommand) {
            let relative = command.isLowercase
            switch command.lowercased().first! {
            case "m":
                guard let x = scanner.number(), let y = scanner.number() else { break }
                current = resolve(x, y, current, relative)
                subpathStart = current
                path.move(to: current)
                lastControl = nil
                lastQuadControl = nil

            case "l":
                guard let x = scanner.number(), let y = scanner.number() else { break }
                current = resolve(x, y, current, relative)
                path.addLine(to: current)
                lastControl = nil
                lastQuadControl = nil

            case "h":
                guard let x = scanner.number() else { break }
                current = CGPoint(x: relative ? current.x + x : x, y: current.y)
                path.addLine(to: current)
                lastControl = nil
                lastQuadControl = nil

            case "v":
                guard let y = scanner.number() else { break }
                current = CGPoint(x: current.x, y: relative ? current.y + y : y)
                path.addLine(to: current)
                lastControl = nil
                lastQuadControl = nil

            case "c":
                guard let x1 = scanner.number(), let y1 = scanner.number(),
                      let x2 = scanner.number(), let y2 = scanner.number(),
                      let x = scanner.number(), let y = scanner.number() else { break }
                let c1 = resolve(x1, y1, current, relative)
                let c2 = resolve(x2, y2, current, relative)
                current = resolve(x, y, current, relative)
                path.addCurve(to: current, control1: c1, control2: c2)
                lastControl = c2
                lastQuadControl = nil

            case "s":
                guard let x2 = scanner.number(), let y2 = scanner.number(),
                      let x = scanner.number(), let y = scanner.number() else { break }
                let c1 = reflect(lastControl, about: current)
                let c2 = resolve(x2, y2, current, relative)
                current = resolve(x, y, current, relative)
                path.addCurve(to: current, control1: c1, control2: c2)
                lastControl = c2
                lastQuadControl = nil

            case "q":
                guard let x1 = scanner.number(), let y1 = scanner.number(),
                      let x = scanner.number(), let y = scanner.number() else { break }
                let control = resolve(x1, y1, current, relative)
                current = resolve(x, y, current, relative)
                path.addQuadCurve(to: current, control: control)
                lastQuadControl = control
                lastControl = nil

            case "t":
                guard let x = scanner.number(), let y = scanner.number() else { break }
                let control = reflect(lastQuadControl, about: current)
                current = resolve(x, y, current, relative)
                path.addQuadCurve(to: current, control: control)
                lastQuadControl = control
                lastControl = nil

            case "a":
                guard let rx = scanner.number(), let ry = scanner.number(),
                      let rotation = scanner.number(),
                      let largeArc = scanner.number(), let sweep = scanner.number(),
                      let x = scanner.number(), let y = scanner.number() else { break }
                let end = resolve(x, y, current, relative)
                appendArc(
                    to: &path,
                    from: current,
                    to: end,
                    rx: rx,
                    ry: ry,
                    rotationDegrees: rotation,
                    largeArc: largeArc != 0,
                    sweep: sweep != 0
                )
                current = end
                lastControl = nil
                lastQuadControl = nil

            case "z":
                path.closeSubpath()
                current = subpathStart
                lastControl = nil
                lastQuadControl = nil

            default:
                break
            }
            previousCommand = command
        }

        return path
    }

    private static func resolve(
        _ x: CGFloat,
        _ y: CGFloat,
        _ current: CGPoint,
        _ relative: Bool
    ) -> CGPoint {
        relative ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y)
    }

    private static func reflect(_ control: CGPoint?, about point: CGPoint) -> CGPoint {
        guard let control else { return point }
        return CGPoint(x: 2 * point.x - control.x, y: 2 * point.y - control.y)
    }

    /// Endpoint parameterisation from the SVG spec, emitted as <=90 degree cubics.
    private static func appendArc(
        to path: inout Path,
        from start: CGPoint,
        to end: CGPoint,
        rx rxIn: CGFloat,
        ry ryIn: CGFloat,
        rotationDegrees: CGFloat,
        largeArc: Bool,
        sweep: Bool
    ) {
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        guard rx > 0, ry > 0, start != end else {
            path.addLine(to: end)
            return
        }

        let phi = rotationDegrees * .pi / 180
        let cosPhi = cos(phi)
        let sinPhi = sin(phi)

        let dx = (start.x - end.x) / 2
        let dy = (start.y - end.y) / 2
        let x1p = cosPhi * dx + sinPhi * dy
        let y1p = -sinPhi * dx + cosPhi * dy

        let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 {
            let correction = sqrt(lambda)
            rx *= correction
            ry *= correction
        }

        let denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        let numerator = max(0, rx * rx * ry * ry - denominator)
        let sign: CGFloat = largeArc == sweep ? -1 : 1
        let coefficient = denominator == 0 ? 0 : sign * sqrt(numerator / denominator)

        let cxp = coefficient * rx * y1p / ry
        let cyp = -coefficient * ry * x1p / rx
        let cx = cosPhi * cxp - sinPhi * cyp + (start.x + end.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (start.y + end.y) / 2

        func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let length = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            guard length > 0 else { return 0 }
            var value = acos(min(1, max(-1, (ux * vx + uy * vy) / length)))
            if ux * vy - uy * vx < 0 { value = -value }
            return value
        }

        let ux = (x1p - cxp) / rx
        let uy = (y1p - cyp) / ry
        let vx = (-x1p - cxp) / rx
        let vy = (-y1p - cyp) / ry

        let theta = angle(1, 0, ux, uy)
        var sweptAngle = angle(ux, uy, vx, vy)
        if !sweep, sweptAngle > 0 { sweptAngle -= 2 * .pi }
        if sweep, sweptAngle < 0 { sweptAngle += 2 * .pi }

        let segments = max(1, Int(ceil(abs(sweptAngle) / (.pi / 2))))
        let delta = sweptAngle / CGFloat(segments)
        let alpha = 4.0 / 3.0 * tan(delta / 4)

        func point(_ a: CGFloat) -> CGPoint {
            let x = rx * cos(a)
            let y = ry * sin(a)
            return CGPoint(x: cosPhi * x - sinPhi * y + cx, y: sinPhi * x + cosPhi * y + cy)
        }
        func derivative(_ a: CGFloat) -> CGPoint {
            let x = -rx * sin(a)
            let y = ry * cos(a)
            return CGPoint(x: cosPhi * x - sinPhi * y, y: sinPhi * x + cosPhi * y)
        }

        var from = start
        var angleStart = theta
        for _ in 0..<segments {
            let angleEnd = angleStart + delta
            let to = point(angleEnd)
            let d1 = derivative(angleStart)
            let d2 = derivative(angleEnd)
            path.addCurve(
                to: to,
                control1: CGPoint(x: from.x + alpha * d1.x, y: from.y + alpha * d1.y),
                control2: CGPoint(x: to.x - alpha * d2.x, y: to.y - alpha * d2.y)
            )
            from = to
            angleStart = angleEnd
        }
    }
}

private struct Scanner {
    private let characters: [Character]
    private var index = 0

    init(_ value: String) {
        characters = Array(value)
    }

    /// Path data allows an implicit repeat of the previous command, and `M x y x y`
    /// means the repeats are line-tos rather than move-tos.
    mutating func nextCommand(repeating previous: Character) -> Character? {
        skipSeparators()
        guard index < characters.count else { return nil }
        let character = characters[index]
        if character.isLetter {
            index += 1
            return character
        }
        guard previous != " " else { return nil }
        switch previous {
        case "M": return "L"
        case "m": return "l"
        case "Z", "z": return nil
        default: return previous
        }
    }

    mutating func number() -> CGFloat? {
        skipSeparators()
        guard index < characters.count else { return nil }

        var text = ""
        if characters[index] == "-" || characters[index] == "+" {
            text.append(characters[index])
            index += 1
        }
        var sawDot = false
        while index < characters.count {
            let character = characters[index]
            if character.isNumber {
                text.append(character)
                index += 1
            } else if character == ".", !sawDot {
                sawDot = true
                text.append(character)
                index += 1
            } else if character == "e" || character == "E" {
                text.append(character)
                index += 1
                if index < characters.count, characters[index] == "-" || characters[index] == "+" {
                    text.append(characters[index])
                    index += 1
                }
            } else {
                break
            }
        }

        guard let value = Double(text) else { return nil }
        return CGFloat(value)
    }

    private mutating func skipSeparators() {
        while index < characters.count {
            switch characters[index] {
            case " ", ",", "\n", "\r", "\t": index += 1
            default: return
            }
        }
    }
}
