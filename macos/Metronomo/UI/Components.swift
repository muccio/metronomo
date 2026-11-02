import SwiftUI

// MARK: - Containers

struct Panel<Content: View>: View {
    var background: Color = Palette.surface
    var border: Color = Palette.hairline
    var spacing: CGFloat = 0
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(background)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(border, lineWidth: 1)
            )
    }
}

struct SectionLabel: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text.uppercased())
            .font(.label())
            .tracking(1.4)
            .foregroundStyle(Palette.textMuted)
    }
}

// MARK: - Controls

struct ChipButton: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: selected ? .semibold : .regular))
                .foregroundStyle(selected ? Palette.onAmber : Palette.textMuted)
                .padding(.horizontal, 15)
                .padding(.vertical, 9)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(selected ? Palette.amber : Palette.surfaceHigh)
                )
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.15), value: selected)
    }
}

struct StepButton: View {
    let label: String
    var enabled = true
    let action: () -> Void

    init(_ label: String, enabled: Bool = true, action: @escaping () -> Void) {
        self.label = label
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(enabled ? Palette.textPrimary : Palette.textMuted.opacity(0.4))
                .frame(width: 48, height: 48)
                .background(Circle().fill(Palette.surfaceHigh))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct ToggleRow: View {
    let title: String
    let subtitle: String
    let isOn: Binding<Bool>

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Palette.textPrimary)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Toggle("", isOn: isOn)
                .labelsHidden()
                .toggleStyle(.switch)
                .tint(Palette.amber)
        }
    }
}

struct PrimaryButton: View {
    let label: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .semibold))
                Text(label)
                    .font(.system(size: 17, weight: .semibold))
            }
            .foregroundStyle(Palette.onAmber)
            .frame(maxWidth: .infinity)
            .frame(height: 60)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Palette.amber)
            )
        }
        .buttonStyle(.plain)
    }
}

struct SecondaryButton: View {
    let label: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.system(size: 16, weight: .medium))
                Text(label)
                    .font(.system(size: 15, weight: .medium))
            }
            .foregroundStyle(Palette.textMuted)
            .padding(.horizontal, 20)
            .frame(height: 60)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Palette.hairline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Metronome indicators

struct BeatDot: View {
    let level: Int
    let active: Bool
    let number: Int
    let action: () -> Void

    private var dotSize: CGFloat {
        switch level {
        case 2: return 40
        case 1: return 30
        default: return 20
        }
    }

    private var baseColor: Color {
        switch level {
        case 2: return Palette.amber
        case 1: return Palette.textPrimary.opacity(0.55)
        default: return .clear
        }
    }

    private var numberColor: Color {
        if active { return Palette.surface }
        return level == 0 ? Palette.textMuted : Palette.onAmber
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                if active {
                    Circle()
                        .fill(Palette.amber.opacity(0.16))
                        .frame(width: 52, height: 52)
                }
                Circle()
                    .fill(baseColor)
                    .overlay(
                        Circle().strokeBorder(
                            Palette.hairline,
                            lineWidth: level == 0 && !active ? 1.5 : 0
                        )
                    )
                    .frame(
                        width: dotSize * (active ? 1.18 : 1),
                        height: dotSize * (active ? 1.18 : 1)
                    )
                    .overlay(
                        Text("\(number)")
                            .font(.system(size: level == 2 ? 13 : 11, weight: .bold))
                            .foregroundStyle(numberColor)
                    )
            }
            .frame(width: dotSize + 22, height: 66)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.09), value: active)
        .help("Cambia l'accento del battito \(number)")
    }
}

struct LegendDot: View {
    let color: Color
    let label: String
    var outlined = false

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(outlined ? Color.clear : color)
                .overlay(Circle().strokeBorder(Palette.hairline, lineWidth: outlined ? 1.5 : 0))
                .frame(width: 10, height: 10)
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(Palette.textMuted)
        }
    }
}

struct ProgressTrack: View {
    let fraction: Double

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.surfaceHigh)
                if fraction > 0 {
                    Capsule()
                        .fill(Palette.amber)
                        .frame(width: proxy.size.width * min(1, max(0, fraction)))
                }
            }
        }
        .frame(height: 6)
    }
}

// MARK: - Tabs

struct TabSwitcher: View {
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 4) {
            tab("Metronomo", index: 0)
            tab("Cronometro", index: 1)
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Palette.surface)
        )
    }

    private func tab(_ label: String, index: Int) -> some View {
        Button {
            withAnimation(.easeOut(duration: 0.16)) { selection = index }
        } label: {
            Text(label)
                .font(.system(size: 14, weight: selection == index ? .semibold : .medium))
                .foregroundStyle(selection == index ? Palette.onAmber : Palette.textMuted)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(selection == index ? Palette.amber : Color.clear)
                )
        }
        .buttonStyle(.plain)
    }
}
