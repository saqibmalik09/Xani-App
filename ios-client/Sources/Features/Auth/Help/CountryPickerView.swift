import SwiftUI
import UIKit

struct CountryPickerView: View {
    let selectedIso: String
    let onSelect: (DialCountry) -> Void
    let onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var query = ""

    private var sections: [CountrySection] { DialCountries.grouped(query: query) }

    var body: some View {
        VStack(spacing: 0) {
            header
            search
            if sections.isEmpty {
                VStack(spacing: 0) {
                    Icon.search(28 * scale, tint: Light.muted)
                        .frame(width: 72 * scale, height: 72 * scale)
                        .background(Light.surface)
                        .clipShape(Circle())
                    AppText(
                        "No results",
                        style: Type.h2,
                        color: Light.text,
                        alignment: .center,
                        sizeOverride: 20
                    )
                    .padding(.top, 16 * scale)
                    AppText(
                        "Try a different country or region.",
                        style: Type.caption,
                        color: Light.sub,
                        alignment: .center
                    )
                    .padding(.top, 8 * scale)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                CountryTable(
                    sections: sections,
                    selectedIso: selectedIso,
                    onSelect: onSelect
                )
                .clipped()
                .ignoresSafeArea(.keyboard, edges: .bottom)
            }
        }
        .padding(insets)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Light.background.ignoresSafeArea())
        .statusBar()
    }

    private var header: some View {
        ZStack {
            AppText("Select country/region", style: Type.labelStrong, color: Light.text, alignment: .center)
            HStack {
                Button(action: onClose) {
                    Icon.close(16 * scale)
                        .frame(width: 36 * scale, height: 36 * scale)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.92))
                .accessibilityLabel("Close")
                Spacer(minLength: 0)
            }
            .padding(.leading, 12 * scale)
        }
        .frame(height: 52 * scale)
        .background(Light.background)
        .zIndex(1)
    }

    private var search: some View {
        HStack(spacing: 8 * scale) {
            Icon.search(18 * scale, tint: Light.muted)
            TextField("Search countries and regions", text: $query)
                .font(Type.body.family.font(size: Type.body.size * scale, weight: Type.body.weight))
                .foregroundStyle(Light.text)
                .tint(Dark.primary)
            ClearFieldButton(visible: !query.isEmpty) { query = "" }
        }
        .padding(.horizontal, 16 * scale)
        .frame(height: 52 * scale)
        .background(Light.input)
        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 6 * scale, style: .continuous)
                .strokeBorder(Light.borderStrong, lineWidth: 1.5 * scale)
        )
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 8 * scale)
        .background(Light.background)
        .zIndex(1)
    }
}

/// UIKit table + native section index. SwiftUI `List` rebuilds cells on every
/// scrub; UITableView reuses rows and the A–Z rail is drawn by the system.
private struct CountryTable: UIViewRepresentable {
    var sections: [CountrySection]
    var selectedIso: String
    var onSelect: (DialCountry) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(sections: sections, selectedIso: selectedIso, onSelect: onSelect)
    }

    func makeUIView(context: Context) -> ThemeTableView {
        let table = ThemeTableView(frame: .zero, style: .plain)
        table.dataSource = context.coordinator
        table.delegate = context.coordinator
        table.rowHeight = 48
        table.estimatedRowHeight = 48
        table.sectionHeaderHeight = 28
        table.estimatedSectionHeaderHeight = 28
        table.separatorInset = UIEdgeInsets(top: 0, left: 20, bottom: 0, right: 24)
        table.backgroundColor = UIPalette.background
        table.sectionIndexColor = UIColor(red: 0.545, green: 0.361, blue: 0.965, alpha: 1)
        table.sectionIndexBackgroundColor = .clear
        table.sectionIndexTrackingBackgroundColor = .clear
        table.clipsToBounds = true
        table.bounces = false
        table.alwaysBounceVertical = false
        table.delaysContentTouches = false
        table.keyboardDismissMode = .onDrag
        table.tableFooterView = UIView()
        table.onTheme = { [weak table] in
            table?.backgroundColor = UIPalette.background
            table?.reloadData()
        }
        return table
    }

    func updateUIView(_ table: ThemeTableView, context: Context) {
        let changed = context.coordinator.sections != sections
            || context.coordinator.selectedIso != selectedIso
        context.coordinator.sections = sections
        context.coordinator.selectedIso = selectedIso
        context.coordinator.onSelect = onSelect
        table.backgroundColor = UIPalette.background
        if changed {
            table.reloadData()
        }
    }

    final class Coordinator: NSObject, UITableViewDataSource, UITableViewDelegate {
        var sections: [CountrySection]
        var selectedIso: String
        var onSelect: (DialCountry) -> Void

        init(sections: [CountrySection], selectedIso: String, onSelect: @escaping (DialCountry) -> Void) {
            self.sections = sections
            self.selectedIso = selectedIso
            self.onSelect = onSelect
        }

        func numberOfSections(in tableView: UITableView) -> Int { sections.count }

        func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
            sections[section].countries.count
        }

        func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
            String(sections[section].letter)
        }

        func sectionIndexTitles(for tableView: UITableView) -> [String]? {
            sections.map { String($0.letter) }
        }

        func tableView(_ tableView: UITableView, sectionForSectionIndexTitle title: String, at index: Int) -> Int {
            index
        }

        func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
            let id = "country"
            let cell = tableView.dequeueReusableCell(withIdentifier: id)
                ?? UITableViewCell(style: .value1, reuseIdentifier: id)
            let country = sections[indexPath.section].countries[indexPath.row]
            let selected = country.iso == selectedIso
            cell.textLabel?.text = country.name
            cell.detailTextLabel?.text = country.label
            cell.textLabel?.font = .systemFont(ofSize: 15)
            cell.detailTextLabel?.font = .systemFont(ofSize: 15)
            cell.textLabel?.textColor = selected
                ? UIColor(red: 0.545, green: 0.361, blue: 0.965, alpha: 1)
                : UIPalette.text
            cell.detailTextLabel?.textColor = selected
                ? UIColor(red: 0.545, green: 0.361, blue: 0.965, alpha: 1)
                : UIPalette.sub
            cell.backgroundColor = UIPalette.background
            cell.contentView.backgroundColor = UIPalette.background
            cell.selectionStyle = .none
            return cell
        }

        func tableView(_ tableView: UITableView, willDisplayHeaderView view: UIView, forSection section: Int) {
            guard let header = view as? UITableViewHeaderFooterView else { return }
            header.contentView.backgroundColor = UIPalette.background
            header.backgroundView?.backgroundColor = UIPalette.background
            header.textLabel?.textColor = UIPalette.sub
        }

        func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
            onSelect(sections[indexPath.section].countries[indexPath.row])
        }
    }
}

private final class ThemeTableView: UITableView {
    var onTheme: (() -> Void)?

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) else { return }
        onTheme?()
    }
}
