import SwiftUI

struct ChronometerScreen: View {
    @ObservedObject var engine: ChronometerEngine
    @State private var glow: Double = 0

    private var isTimer: Bool { engine.mode == .timer }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                modeSwitcher
                timePanel
                if isTimer {
                    durationPanel
                }
                signalPanel
                togglesPanel
                Spacer().frame(height: 2)
                transportRow
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 30)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - Mode

    private var modeSwitcher: some View {
        HStack(spacing: 4) {
            modeTab("Cronometro", mode: .stopwatch)
            modeTab("Timer", mode: .timer)
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Palette.surface)
        )
    }

    private func modeTab(_ label: String, mode: ChronometerEngine.Mode) -> some View {
        let active = engine.mode == mode
        return Button {
            engine.setMode(mode)
        } label: {
            Text(label)
                .font(.system(size: 14, weight: active ? .semibold : .regular))
                .foregroundStyle(active ? Palette.textPrimary : Palette.textMuted)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(active ? Palette.surfaceHigh : Color.clear)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Clock

    private var timePanel: some View {
        let innerBackground = Color.blend(Palette.surface, Color(hex: 0x2A1F05), amount: glow * 0.9)
        let borderColor = Color.blend(Palette.hairline, Palette.amber, amount: glow)

        let clock: String = isTimer
            ? ChronometerEngine.formatClock(engine.remaining)
            : ChronometerEngine.formatStopwatch(engine.elapsed)
        let label: String = {
            if isTimer && engine.finished { return "Tempo scaduto" }
            if isTimer { return "Rimangono" }
            return "Tempo trascorso"
        }()

        return Panel(border: borderColor) {
            VStack(alignment: .leading, spacing: 6) {
                SectionLabel(label)
                Text(clock)
                    .font(.numeral(isTimer ? 56 : 50))
                    .foregroundStyle(
                        engine.finished && isTimer ? Palette.amber : Palette.textPrimary
                    )
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 8)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(innerBackground)
            )

            if isTimer {
                ProgressTrack(fraction: engine.progress)
                    .padding(.top, 14)
            }

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Segnali emessi")
                        .font(.system(size: 11))
                        .foregroundStyle(Palette.textMuted)
                    Text("\(engine.signals)")
                        .font(.numeral(20))
                        .foregroundStyle(Palette.textPrimary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text("Prossimo segnale")
                        .font(.system(size: 11))
                        .foregroundStyle(Palette.textMuted)
                    Text(nextSignalText)
                        .font(.numeral(20))
                        .foregroundStyle(Palette.amber)
                }
            }
            .padding(.top, 12)
        }
        .onChange(of: engine.pulse) { _ in
            flash()
        }
    }

    private var nextSignalText: String {
        guard engine.intervalMinutes > 0 else { return "off" }
        guard let next = engine.nextSignalIn else { return "—" }
        return ChronometerEngine.formatShort(next)
    }

    // MARK: - Timer duration

    private var durationPanel: some View {
        let current = Int(engine.target / 60)
        return Panel {
            SectionLabel("Durata timer")

            HStack {
                StepButton("−", enabled: current > 1 && !engine.running) {
                    engine.setTarget(minutes: current - 1)
                }
                Spacer()
                Text("\(current) min")
                    .font(.numeral(30))
                    .foregroundStyle(Palette.textPrimary)
                Spacer()
                StepButton("+", enabled: current < 600 && !engine.running) {
                    engine.setTarget(minutes: current + 1)
                }
            }
            .padding(.top, 10)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach([1, 5, 10, 15, 20, 25, 30, 45, 60], id: \.self) { minutes in
                        ChipButton(
                            label: "\(minutes)′",
                            selected: current == minutes
                        ) {
                            guard !engine.running else { return }
                            engine.setTarget(minutes: minutes)
                        }
                    }
                }
            }
            .padding(.top, 12)
        }
    }

    // MARK: - Interval signals

    private var signalPanel: some View {
        let interval = engine.intervalMinutes
        return Panel {
            HStack {
                SectionLabel("Segnale ogni")
                Spacer()
                Text(interval == 0 ? "disattivato" : "\(interval) min")
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.amber)
            }

            HStack {
                StepButton("−", enabled: interval > 0) {
                    engine.setInterval(minutes: interval - 1)
                }
                Spacer()
                Text(interval == 0 ? "off" : "\(interval)")
                    .font(.numeral(30))
                    .foregroundStyle(Palette.textPrimary)
                Spacer()
                StepButton("+", enabled: interval < 180) {
                    engine.setInterval(minutes: interval + 1)
                }
            }
            .padding(.top, 10)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach([0, 1, 2, 5, 10, 15, 20, 30], id: \.self) { minutes in
                        ChipButton(
                            label: minutes == 0 ? "Off" : "\(minutes)′",
                            selected: interval == minutes
                        ) {
                            engine.setInterval(minutes: minutes)
                        }
                    }
                }
            }
            .padding(.top, 12)

            Text(
                interval == 0
                    ? "Segnali disattivati: il cronometro resta muto."
                    : "Un segnale acustico ogni \(interval == 1 ? "minuto" : "\(interval) minuti"), anche a display spento."
            )
            .font(.system(size: 12))
            .foregroundStyle(Palette.textMuted)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 10)
        }
    }

    // MARK: - Toggles

    private var togglesPanel: some View {
        Panel {
            ToggleRow(
                title: "Segnale sonoro",
                subtitle: "Campanello all'intervallo e a fine timer",
                isOn: Binding(
                    get: { engine.soundEnabled },
                    set: { engine.setSoundEnabled($0) }
                )
            )

            Rectangle()
                .fill(Palette.hairline)
                .frame(height: 1)
                .padding(.vertical, 12)

            ToggleRow(
                title: "Notifica di sistema",
                subtitle: "Avviso a fine timer e per i segnali quando l'app è in background",
                isOn: Binding(
                    get: { engine.notificationsEnabled },
                    set: { engine.setNotificationsEnabled($0) }
                )
            )
        }
    }

    // MARK: - Transport

    private var transportRow: some View {
        let label: String = {
            if engine.running { return "Pausa" }
            if engine.finished { return "Riavvia" }
            if engine.elapsed > 0 { return "Riprendi" }
            return "Avvia"
        }()

        return HStack(spacing: 10) {
            PrimaryButton(
                label: label,
                systemImage: engine.running ? "pause.fill" : "play.fill"
            ) {
                engine.finished ? engine.start() : engine.toggle()
            }
            SecondaryButton(
                label: "Azzera",
                systemImage: "arrow.counterclockwise",
                action: engine.reset
            )
        }
    }

    // MARK: - Flash

    private func flash() {
        guard engine.pulse > 0 else { return }
        glow = 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.02) {
            withAnimation(.linear(duration: 1.2)) {
                glow = 0
            }
        }
    }
}
