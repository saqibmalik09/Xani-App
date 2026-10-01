import SwiftUI

enum LiveCategory: String, CaseIterable { case explore, live, pk, audio }
enum LiveMedia: String, CaseIterable { case all, video, audio }

struct LiveBrowsePane: View {
    let tiles: [LiveTile]
    var onOpen: (LiveTile) -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.feedBottomReserve) private var reserve
    @ObservedObject private var bannerStore = BannerStore.shared
    @State private var category: LiveCategory = .explore
    @State private var media: LiveMedia = .all
    @State private var country = "ALL"
    @State private var countryOpen = false
    @State private var countryQuery = ""
    @State private var bannerIdx = 0

    private let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    private var filtered: [LiveTile] {
        tiles.filter { t in
            (country == "ALL" || t.country == country)
                && (media == .all || (media == .video && t.kind == .video) || (media == .audio && t.kind == .audio))
                && (category != .pk || t.pk)
                && (category != .audio || t.kind == .audio)
        }
    }

    private var selectedCountry: LiveCountry {
        FeedStore.countries.first { $0.code == country } ?? FeedStore.countries[0]
    }

    private var banners: [BannerData] { bannerStore.items }

    var body: some View {
        ZStack(alignment: .top) {
            Color(rgb: 0x07070F).ignoresSafeArea()
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 0) {
                        catTab("✦", Copy.t(.liveExplore), .explore)
                        catTab("●", Copy.t(.homeLive), .live)
                        catTab("⚔", Copy.t(.livePkBattle), .pk)
                        catTab("🎙", Copy.t(.liveAudioTab), .audio)
                    }
                    .padding(.horizontal, 14 * scale)
                }
                Color.white.opacity(0.06).frame(height: 1)

                HStack(spacing: 6 * scale) {
                    mediaPill(Copy.t(.liveAll), .all)
                    mediaPill(Copy.t(.liveVideoPill), .video)
                    mediaPill(Copy.t(.liveAudioPill), .audio)
                    Spacer(minLength: 0)
                    Button(action: { countryOpen.toggle() }) {
                        HStack(spacing: 6 * scale) {
                            AppText(selectedCountry.flag, style: Type.label, sizeOverride: 15)
                            AppText(selectedCountry.label, style: Type.labelBold, color: country != "ALL" ? .white : Color.white.opacity(0.6), sizeOverride: 12)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 10 * scale, weight: .semibold))
                                .foregroundColor(Color.white.opacity(0.5))
                                .rotationEffect(.degrees(countryOpen ? 180 : 0))
                        }
                        .padding(.horizontal, 12 * scale)
                        .padding(.vertical, 6 * scale)
                        .background(country != "ALL" ? Color(rgb: 0x8B5CF6).opacity(0.22) : Color.white.opacity(0.07))
                        .overlay(
                            Capsule().stroke(country != "ALL" ? Color(rgb: 0x8B5CF6).opacity(0.5) : Color.white.opacity(0.12), lineWidth: 1)
                        )
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 9 * scale)

                ScrollView {
                    if !banners.isEmpty {
                        banner
                            .padding(.horizontal, 12 * scale)
                            .padding(.top, 4 * scale)
                    }

                    HStack {
                        AppText(
                            category == .explore ? Copy.t(.liveTrendingNow) : (category == .pk ? Copy.t(.livePkBattles) : Copy.t(.liveNow)),
                            style: Type.labelBold,
                            color: .white,
                            sizeOverride: 15
                        )
                        Spacer()
                        AppText(Copy.t(.liveCount, ["n": "\(filtered.count)"]), style: Type.labelBold, color: Color(rgb: 0x8B5CF6), sizeOverride: 12)
                    }
                    .padding(.horizontal, 16 * scale)
                    .padding(.top, 16 * scale)
                    .padding(.bottom, 6 * scale)

                    LazyVGrid(columns: cols, spacing: 10 * scale) {
                        ForEach(filtered) { tile in
                            LiveCardView(tile: tile, showPk: category == .pk) { onOpen(tile) }
                        }
                    }
                    .padding(.horizontal, 12 * scale)
                    .padding(.bottom, reserve + 20 * scale)
                }
            }

            if countryOpen {
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .onTapGesture { countryOpen = false; countryQuery = "" }
                countryDropdown
                    .padding(.top, 88 * scale)
                    .padding(.horizontal, 14 * scale)
            }
        }
        .task {
            await bannerStore.load()
        }
        .task(id: banners.count) {
            guard !banners.isEmpty else { return }
            if bannerIdx >= banners.count { bannerIdx = 0 }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_200_000_000)
                guard !banners.isEmpty else { return }
                bannerIdx = (bannerIdx + 1) % banners.count
            }
        }
    }

    private func catTab(_ icon: String, _ label: String, _ value: LiveCategory) -> some View {
        let on = category == value
        return Button(action: { category = value }) {
            VStack(spacing: 6 * scale) {
                HStack(spacing: 5 * scale) {
                    AppText(icon, style: Type.label, color: on ? Color(rgb: 0xA78BFA) : Color.white.opacity(0.4), sizeOverride: 11)
                    AppText(label, style: on ? Type.labelBold : Type.label, color: on ? .white : Color.white.opacity(0.42), sizeOverride: 13)
                }
                Capsule()
                    .fill(on ? Color(rgb: 0x8B5CF6) : Color.clear)
                    .frame(width: 26 * scale, height: 2 * scale)
            }
            .padding(.horizontal, 14 * scale)
            .padding(.vertical, 10 * scale)
        }
        .buttonStyle(.plain)
    }

    private func mediaPill(_ label: String, _ value: LiveMedia) -> some View {
        let on = media == value
        return Button(action: { media = value }) {
            AppText(label, style: on ? Type.labelBold : Type.label, color: on ? .white : Color.white.opacity(0.45), sizeOverride: 12)
                .padding(.horizontal, 12 * scale)
                .padding(.vertical, 5 * scale)
                .background(on ? Color(rgb: 0x8B5CF6).opacity(0.28) : Color.white.opacity(0.06))
                .overlay(Capsule().stroke(on ? Color(rgb: 0x8B5CF6).opacity(0.55) : Color.white.opacity(0.1), lineWidth: 1))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var banner: some View {
        ZStack(alignment: .bottomTrailing) {
            Color(rgb: 0x110C22)
            ForEach(Array(banners.enumerated()), id: \.element.id) { i, b in
                RemoteImage(url: bannerStore.imageURL(b.imageKey), contentMode: .fill, fallback: Color(rgb: 0x110C22))
                    .opacity(i == bannerIdx ? 1 : 0)
                    .animation(.easeInOut(duration: 0.7), value: bannerIdx)
            }
            if banners.count > 1 {
                HStack(spacing: 5 * scale) {
                    ForEach(0..<banners.count, id: \.self) { i in
                        Capsule()
                            .fill(i == bannerIdx ? Color.white : Color.white.opacity(0.38))
                            .frame(width: (i == bannerIdx ? 18 : 6) * scale, height: 6 * scale)
                            .onTapGesture { bannerIdx = i }
                    }
                }
                .padding(14 * scale)
            }
        }
        .frame(height: 150 * scale)
        .clipShape(RoundedRectangle(cornerRadius: 18 * scale, style: .continuous))
    }

    private var countryDropdown: some View {
        let list = countryQuery.isEmpty
            ? FeedStore.countries
            : FeedStore.countries.filter {
                $0.label.localizedCaseInsensitiveContains(countryQuery) || $0.code.localizedCaseInsensitiveContains(countryQuery)
            }
        return VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Image(systemName: "magnifyingglass").foregroundColor(Color.white.opacity(0.4)).font(.system(size: 13 * scale))
                TextField(Copy.t(.liveSearchCountry), text: $countryQuery)
                    .font(.system(size: 13 * scale))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 12 * scale)
            Color.white.opacity(0.07).frame(height: 1)
            ScrollView {
                if list.isEmpty {
                    AppText(Copy.t(.liveNoCountries), style: Type.caption, color: Color.white.opacity(0.35), sizeOverride: 13, alignment: .center)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 24 * scale)
                } else {
                    ForEach(list) { c in
                        Button(action: {
                            country = c.code
                            countryOpen = false
                            countryQuery = ""
                        }) {
                            HStack(spacing: 12 * scale) {
                                AppText(c.flag, style: Type.label, sizeOverride: 20)
                                AppText(c.label, style: country == c.code ? Type.labelBold : Type.caption, color: country == c.code ? .white : Color.white.opacity(0.72), sizeOverride: 14)
                                Spacer()
                                if country == c.code {
                                    Image(systemName: "checkmark").foregroundColor(Color(rgb: 0x8B5CF6)).font(.system(size: 12 * scale, weight: .bold))
                                }
                            }
                            .padding(.horizontal, 18 * scale)
                            .padding(.vertical, 11 * scale)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(maxHeight: 272 * scale)
        }
        .background(Color(rgb: 0x120C23).opacity(0.97))
        .overlay(RoundedRectangle(cornerRadius: 18 * scale, style: .continuous).stroke(Color(rgb: 0x8B5CF6).opacity(0.25), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 18 * scale, style: .continuous))
        .shadow(color: Color.black.opacity(0.7), radius: 20, y: 10)
    }
}

private struct LiveCardView: View {
    let tile: LiveTile
    let showPk: Bool
    var onOpen: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        let color = Color(rgb: tile.color)
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [color, Color(rgb: 0x0A0514)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    LinearGradient(colors: [.clear, Color(rgb: 0x0A0514).opacity(0.9)], startPoint: .center, endPoint: .bottom)
                    HStack {
                        HStack(spacing: 4 * scale) {
                            Circle().fill(Color.white.opacity(0.85)).frame(width: 4 * scale, height: 4 * scale)
                            AppText(Copy.t(.liveBadge), style: Type.labelBold, color: .white, sizeOverride: 9)
                        }
                        .padding(.horizontal, 8 * scale)
                        .padding(.vertical, 3 * scale)
                        .background(Color(rgb: 0xEC4899).opacity(0.9))
                        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                        Spacer()
                        AppText(tile.flag, style: Type.label, sizeOverride: 13)
                            .padding(.horizontal, 7 * scale)
                            .padding(.vertical, 3 * scale)
                            .background(Color.black.opacity(0.55))
                            .clipShape(Capsule())
                    }
                    .padding(8 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

                    if showPk || tile.pk {
                        AppText("⚔ PK", style: Type.labelBold, color: .white, sizeOverride: 11)
                            .padding(.horizontal, 10 * scale)
                            .padding(.vertical, 4 * scale)
                            .background(LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing))
                            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    }

                    HStack(spacing: 3 * scale) {
                        Image(systemName: "eye").font(.system(size: 9 * scale)).foregroundColor(.white)
                        AppText(tile.viewers, style: Type.labelBold, color: .white, sizeOverride: 9)
                    }
                    .padding(.horizontal, 7 * scale)
                    .padding(.vertical, 3 * scale)
                    .background(Color.black.opacity(0.5))
                    .clipShape(Capsule())
                    .padding(8 * scale)
                }
                .aspectRatio(3 / 4, contentMode: .fit)

                VStack(alignment: .leading, spacing: 6 * scale) {
                    HStack(spacing: 6 * scale) {
                        Circle().fill(color).frame(width: 26 * scale, height: 26 * scale)
                            .overlay(AppText(tile.initials, style: Type.labelBold, color: .white, sizeOverride: 10))
                        VStack(alignment: .leading, spacing: 0) {
                            AppText(tile.name, style: Type.labelBold, color: .white, sizeOverride: 12, lineLimit: 1)
                            AppText(tile.host, style: Type.caption, color: Color.white.opacity(0.4), sizeOverride: 10)
                        }
                    }
                    HStack {
                        AppText(tile.category, style: Type.caption, color: Color.white.opacity(0.5), sizeOverride: 10)
                            .padding(.horizontal, 7 * scale)
                            .padding(.vertical, 2 * scale)
                            .background(Color.white.opacity(0.07))
                            .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                        Spacer()
                        AppText("💎", style: Type.caption, sizeOverride: 11)
                        AppText(tile.diamonds, style: Type.labelBold, color: Color(rgb: 0xC4B5FD), sizeOverride: 10)
                    }
                }
                .padding(10 * scale)
            }
            .background(Color(rgb: 0x110C22))
            .overlay(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous).stroke(Color.white.opacity(0.06), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.97))
    }
}
