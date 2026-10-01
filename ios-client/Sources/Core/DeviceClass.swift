import Foundation
import SwiftUI

enum DeviceClass {
    case low, mid, high

    var pagerNeighbours: Int { self == .low ? 0 : 1 }
    var pinHomeOffTab: Bool { self != .low }

    static func current() -> DeviceClass {
        let megabytes = ProcessInfo.processInfo.physicalMemory / (1024 * 1024)
        if megabytes <= 2048 { return .low }
        if megabytes <= 4096 { return .mid }
        return .high
    }
}

private struct DeviceClassKey: EnvironmentKey {
    static let defaultValue: DeviceClass = .current()
}

extension EnvironmentValues {
    var deviceClass: DeviceClass {
        get { self[DeviceClassKey.self] }
        set { self[DeviceClassKey.self] = newValue }
    }

    var feedBottomReserve: CGFloat {
        get { self[FeedBottomReserveKey.self] }
        set { self[FeedBottomReserveKey.self] = newValue }
    }
}

private struct FeedBottomReserveKey: EnvironmentKey {
    static let defaultValue: CGFloat = 70
}
