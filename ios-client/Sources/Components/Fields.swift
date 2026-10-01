import SwiftUI
import UIKit

enum FieldKind {
    case text
    case email
    case password
    case username
    case phone
}

struct LightField<Trailing: View>: View {
    @Binding private var value: String
    private let placeholder: String
    private let kind: FieldKind
    private let maxCharacters: Int
    private let submitLabel: SubmitLabel
    private let obscured: Bool
    private let prefix: String?
    private let onSubmit: () -> Void
    private let trailing: () -> Trailing

    @Environment(\.uiScale) private var scale
    @FocusState private var focused: Bool

    /// Written out rather than synthesised: a generic view with a `@FocusState` and a
    /// `@ViewBuilder` member is exactly where an implicit memberwise initialiser gets
    /// surprising.
    init(
        value: Binding<String>,
        placeholder: String,
        kind: FieldKind = .text,
        maxCharacters: Int = 120,
        submitLabel: SubmitLabel = .next,
        obscured: Bool = false,
        prefix: String? = nil,
        onSubmit: @escaping () -> Void = {},
        @ViewBuilder trailing: @escaping () -> Trailing
    ) {
        self._value = value
        self.placeholder = placeholder
        self.kind = kind
        self.maxCharacters = maxCharacters
        self.submitLabel = submitLabel
        self.obscured = obscured
        self.prefix = prefix
        self.onSubmit = onSubmit
        self.trailing = trailing
    }

    var body: some View {
        // Clamping in the binding rather than in an onChange keeps the field a pure
        // function of state: no transient over-length value ever reaches the view.
        let bounded = Binding(
            get: { value },
            set: { next in
                value = String(next.prefix(maxCharacters))
            }
        )

        let atLimit = value.count >= maxCharacters
        HStack(spacing: 8 * scale) {
            if let prefix {
                AppText(prefix, style: Type.body, color: Light.sub, lineLimit: 1)
            }
            ZStack(alignment: .leading) {
                if value.isEmpty {
                    AppText(placeholder, style: Type.body, color: Light.muted, lineLimit: 1)
                        .allowsHitTesting(false)
                }
                field(bounded)
            }
            trailing()
        }
        .padding(.horizontal, 16 * scale)
        .frame(height: 52 * scale)
        .background(Light.input)
        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 6 * scale, style: .continuous)
                .strokeBorder(
                    atLimit && focused ? Light.error : focused ? Dark.primary : Light.borderStrong,
                    lineWidth: 1.5 * scale
                )
                .animation(.easeOut(duration: 0.16), value: focused)
        )
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }

    @ViewBuilder
    private func field(_ binding: Binding<String>) -> some View {
        Group {
            // `focused` is bound on each concrete field rather than on the Group, so
            // switching between secure and plain never drops the focus binding.
            if obscured {
                SecureField("", text: binding).focused($focused)
            } else {
                TextField("", text: binding).focused($focused)
            }
        }
        .font(Type.body.family.font(size: Type.body.size * scale, weight: Type.body.weight))
        .foregroundStyle(Light.text)
        .tint(value.count >= maxCharacters ? Light.error : Dark.primary)
        .submitLabel(submitLabel)
        .onSubmit(onSubmit)
        .keyboardType(keyboardType)
        .textContentType(contentType)
        .textInputAutocapitalization(capitalization)
        .autocorrectionDisabled(kind != .text)
    }

    private var keyboardType: UIKeyboardType {
        switch kind {
        case .email: return .emailAddress
        case .username: return .asciiCapable
        case .phone: return .phonePad
        case .password, .text: return .default
        }
    }

    private var contentType: UITextContentType? {
        switch kind {
        case .email: return .emailAddress
        case .password: return .newPassword
        case .username: return .username
        case .phone: return .telephoneNumber
        case .text: return .name
        }
    }

    private var capitalization: TextInputAutocapitalization {
        switch kind {
        case .text: return .words
        default: return .never
        }
    }
}

extension LightField where Trailing == EmptyView {
    init(
        value: Binding<String>,
        placeholder: String,
        kind: FieldKind = .text,
        maxCharacters: Int = 120,
        submitLabel: SubmitLabel = .next,
        obscured: Bool = false,
        prefix: String? = nil,
        onSubmit: @escaping () -> Void = {}
    ) {
        self.init(
            value: value,
            placeholder: placeholder,
            kind: kind,
            maxCharacters: maxCharacters,
            submitLabel: submitLabel,
            obscured: obscured,
            prefix: prefix,
            onSubmit: onSubmit,
            trailing: { EmptyView() }
        )
    }
}

private let limitReached = "Characters limit reached"

struct CountedField: View {
    @Binding var value: String
    let placeholder: String
    let maxCharacters: Int
    var kind: FieldKind = .text
    var prefix: String? = nil
    var footnote: String? = nil
    var sanitize: ((String) -> String)? = nil

    @Environment(\.uiScale) private var scale

    private var atLimit: Bool { value.count >= maxCharacters }

    var body: some View {
        let bounded = Binding(
            get: { value },
            set: { next in
                let cleaned = sanitize?(next) ?? next
                value = String(cleaned.prefix(maxCharacters))
            }
        )

        VStack(spacing: 0) {
            LightField(
                value: bounded,
                placeholder: placeholder,
                kind: kind,
                maxCharacters: maxCharacters,
                prefix: prefix
            )
            HStack(alignment: .firstTextBaseline, spacing: 8 * scale) {
                if atLimit {
                    AppText(limitReached, style: Type.micro, color: Light.error)
                    Spacer(minLength: 8 * scale)
                } else if let footnote {
                    AppText(footnote, style: Type.micro, color: Light.hint)
                    Spacer(minLength: 8 * scale)
                } else {
                    Spacer(minLength: 0)
                }
                AppText(
                    "\(value.count)/\(maxCharacters)",
                    style: Type.micro,
                    color: atLimit ? Light.error : Light.hint,
                    alignment: .trailing
                )
            }
            .padding(.top, 6 * scale)
        }
    }
}

/// Handle-only: letters, digits, underscore. The leading `@` is visual, never stored.
func sanitizeUsername(_ raw: String) -> String {
    var out = ""
    out.reserveCapacity(min(raw.count, 24))
    for c in raw {
        guard let ascii = c.asciiValue else { continue }
        switch ascii {
        case 65...90: out.append(contentsOf: c.lowercased())
        case 97...122, 48...57, 95: out.append(c)
        default: break
        }
    }
    return out
}

struct PasswordField: View {
    @Binding var value: String
    let placeholder: String
    var showStrength: Bool = false
    var submitLabel: SubmitLabel = .next
    var onSubmit: () -> Void = {}

    @Environment(\.uiScale) private var scale
    @State private var revealed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LightField(
                value: $value,
                placeholder: placeholder,
                kind: .password,
                maxCharacters: 72,
                submitLabel: submitLabel,
                obscured: !revealed,
                onSubmit: onSubmit
            ) {
                Button {
                    revealed.toggle()
                } label: {
                    Group {
                        if revealed {
                            Icon.eyeOff(20 * scale, tint: Light.muted)
                        } else {
                            Icon.eye(20 * scale, tint: Light.muted)
                        }
                    }
                    .frame(width: 28 * scale, height: 28 * scale)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PressScaleStyle())
                .accessibilityLabel(revealed ? "Hide password" : "Show password")
            }

            if showStrength, !value.isEmpty {
                let strength = passwordStrength(value)
                HStack(spacing: 4 * scale) {
                    ForEach(0..<3, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 2 * scale, style: .continuous)
                            .fill(index <= strength ? strengthColors[strength] : Light.border)
                            .frame(height: 3 * scale)
                    }
                    AppText(
                        strengthLabels[strength],
                        style: Type.micro,
                        color: strengthColors[strength]
                    )
                    .padding(.leading, 4 * scale)
                }
                .padding(.top, 6 * scale)
            }
        }
    }
}

private let strengthColors = [Dark.accent, Dark.warning, Dark.success]
private let strengthLabels = ["Weak", "Fair", "Strong"]

func passwordStrength(_ value: String) -> Int {
    switch value.count {
    case ..<4: return 0
    case ..<8: return 1
    default: return 2
    }
}

/// One real field, caret pinned to the end, so digits always fill left to right
/// into the next empty cell. The system caret and Cut/Copy menu stay hidden.
struct OtpInput: View {
    @Binding private var code: String
    private let length: Int
    private let verified: Bool
    var focused: FocusState<Bool>.Binding

    @Environment(\.uiScale) private var scale

    init(
        code: Binding<String>,
        length: Int,
        verified: Bool,
        focused: FocusState<Bool>.Binding
    ) {
        self._code = code
        self.length = length
        self.verified = verified
        self.focused = focused
    }

    private var activeIndex: Int {
        if verified { return -1 }
        if code.count >= length { return length - 1 }
        return code.count
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            HiddenOtpField(code: $code, length: length, isFocused: focused)
                .frame(width: 1, height: 1)
                .offset(x: -4000)
                .accessibilityLabel("Verification code")

            HStack(spacing: 10 * scale) {
                ForEach(0..<length, id: \.self) { index in
                    OtpCell(
                        digit: digit(at: index),
                        verified: verified,
                        active: index == activeIndex && focused.wrappedValue
                    )
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { focused.wrappedValue = true }
        .task {
            await sleepMillis(50)
            focused.wrappedValue = true
        }
    }

    private func digit(at index: Int) -> String {
        guard index < code.count else { return "" }
        return String(Array(code)[index])
    }
}

/// UIKit field so we can pin the insertion point to the end and suppress the
/// caret / edit menu that SwiftUI's TextField paints over cell 0.
private struct HiddenOtpField: UIViewRepresentable {
    @Binding var code: String
    var length: Int
    var isFocused: FocusState<Bool>.Binding

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UITextField {
        let field = OtpPinField()
        field.keyboardType = .numberPad
        field.textContentType = .oneTimeCode
        field.tintColor = .clear
        field.textColor = .clear
        field.backgroundColor = .clear
        field.borderStyle = .none
        field.delegate = context.coordinator
        context.coordinator.onChange = { next in
            let digits = String(next.filter(\.isNumber).prefix(length))
            if code != digits { code = digits }
        }
        field.addTarget(context.coordinator, action: #selector(Coordinator.editingChanged), for: .editingChanged)
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.length = length
        context.coordinator.onChange = { next in
            let digits = String(next.filter(\.isNumber).prefix(length))
            if code != digits { code = digits }
        }
        if field.text != code {
            field.text = code
        }
        DispatchQueue.main.async {
            if self.isFocused.wrappedValue {
                if !field.isFirstResponder { field.becomeFirstResponder() }
            } else if field.isFirstResponder {
                field.resignFirstResponder()
            }
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var length = 6
        var onChange: (String) -> Void = { _ in }

        @objc func editingChanged(_ field: UITextField) {
            let digits = String((field.text ?? "").filter(\.isNumber).prefix(length))
            if field.text != digits { field.text = digits }
            onChange(digits)
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            let end = textField.endOfDocument
            textField.selectedTextRange = textField.textRange(from: end, to: end)
        }
    }
}

private final class OtpPinField: UITextField {
    override var selectedTextRange: UITextRange? {
        get { super.selectedTextRange }
        set { super.selectedTextRange = textRange(from: endOfDocument, to: endOfDocument) }
    }

    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool { false }
    override func caretRect(for position: UITextPosition) -> CGRect { .zero }
    override func selectionRects(for range: UITextRange) -> [UITextSelectionRect] { [] }
    override func closestPosition(to point: CGPoint) -> UITextPosition? { endOfDocument }
}

private struct OtpCell: View {
    let digit: String
    let verified: Bool
    let active: Bool

    @Environment(\.uiScale) private var scale

    var body: some View {
        let filled = !digit.isEmpty
        let border: Color = {
            if verified { return Dark.success }
            if active { return Dark.primary }
            if filled { return Dark.primary.opacity(0.7) }
            return Dark.primary.opacity(0.18)
        }()
        let fill: Color = {
            if verified || filled { return Dark.primary.opacity(0.14) }
            if active { return Dark.primary.opacity(0.10) }
            return Color.white.opacity(0.04)
        }()

        ZStack {
            if filled {
                AppText(digit, style: Type.otp, color: Dark.foreground, alignment: .center)
            }
        }
        .frame(width: 46 * scale, height: 58 * scale)
        .background(fill)
        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                .strokeBorder(border, lineWidth: 1.5 * scale)
        )
    }
}
