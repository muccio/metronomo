import SwiftUI

struct MetronomeScreen: View {
    @ObservedObject var engine: MetronomeEngine

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                tempoPanel
                beatPanel
                signaturePanel
                soundPanel
                Spacer().frame(height: 2)
                PrimaryButton(
                    label: engine.running ? "Ferma" : "Avvia",
                    systemImage: engine.running ? "stop.fill" : "play.fill",
                    action: engine.toggle
                )
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 30)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - Tempo

    private var tempoPanel: some View {
        Panel {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 0) {
                    SectionLabel("Tempo")
                    HStack(alignment: .bottom, spacing: 8) {
                        Text("\(engine.bpm)")
                            .font(.numeral(58))
                            .foregroundStyle(Palette.textPrimary)
                        Text("BPM")
                            .font(.label())
                            .tracking(1.4)
                            .foregroundStyle(Palette.amber)
                            .padding(.bottom, 12)
                    }
                    Text("\(engine.tempoName) · \(engine.signature)")
                        .font(.system(size: 13))
                        .foregroundStyle(Palette.textMuted)
                }

                Spacer(minLength: 12)

                Button(action: engine.toggle) {
                    ZStack {
                        Circle()
                            .fill(engine.running ? Palette.amber : Palette.surfaceHigh)
                            .frame(width: 66, height: 66)
                        Image(systemName: engine.running ? "pause.fill" : "play.fill")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(engine.running ? Palette.onAmber : Palette.amber)
                            .offset(x: engine.running ? 0 : 1)
                    }
                }
                .buttonStyle(.plain)
                .help(engine.running ? "Ferma il metronomo" : "Avvia il metronomo")
            }

            Slider(
                value: Binding(
                    get: { Double(engine.bpm) },
                    set: { engine.setBPM(Int($0.rounded())) }
                ),
                in: Double(MetronomeEngine.minBPM)...Double(MetronomeEngine.maxBPM),
                step: 1
            )
            .tint(Palette.amber)
            .padding(.top, 10)

            HStack(spacing: 10) {
                StepButton("−", enabled: engine.bpm > MetronomeEngine.minBPM) {
                    engine.setBPM(engine.bpm - 1)
                }
                Button(action: engine.tap) {
                    Text("Tap tempo")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Palette.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Palette.surfaceHigh)
                        )
                }
                .buttonStyle(.plain)
                .help("Batti il tempo con un clic del mouse")
                StepButton("+", enabled: engine.bpm < MetronomeEngine.maxBPM) {
                    engine.setBPM(engine.bpm + 1)
                }
            }
        }
    }

    // MARK: - Beats

    private var beatPanel: some View {
        Panel {
            HStack {
                SectionLabel("Battuta")
                Spacer()
                Text(engine.running ? "IN CORSO" : "PRONTO")
                    .font(.system(size: 11))
                    .tracking(1)
                    .foregroundStyle(engine.running ? Palette.amber : Palette.textMuted)
            }

            GeometryReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 0) {
                        ForEach(Array(engine.accents.enumerated()), id: \.offset) { index, level in
                            BeatDot(
                                level: level,
                                active: engine.running && engine.currentBeat == index,
                                number: index + 1
                            ) {
                                engine.cycleAccent(at: index)
                            }
                        }
                    }
                    .frame(minWidth: proxy.size.width, alignment: .center)
                }
            }
            .frame(height: 66)
            .padding(.top, 6)

            HStack(spacing: 14) {
                LegendDot(color: Palette.amber, label: "Accento")
                LegendDot(color: Palette.textPrimary.opacity(0.55), label: "Battito")
                LegendDot(color: .clear, label: "Silenzio", outlined: true)
            }
            .padding(.top, 2)

            Text("Tocca un cerchio per cambiare il suo accento.")
                .font(.system(size: 12))
                .foregroundStyle(Palette.textMuted)
                .padding(.top, 8)
        }
    }

    // MARK: - Signature

    private var signaturePanel: some View {
        Panel {
            HStack {
                SectionLabel("Metrica")
                Spacer()
                Text(engine.signature)
                    .font(.numeral(18))
                    .foregroundStyle(Palette.amber)
            }

            SectionLabel("Battiti per battuta")
                .padding(.top, 10)
                .padding(.bottom, 8)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(1...12, id: \.self) { count in
                        ChipButton(
                            label: "\(count)",
                            selected: engine.beatsPerBar == count
                        ) {
                            engine.setBeatsPerBar(count)
                        }
                    }
                }
            }

            SectionLabel("Unità di battito")
                .padding(.top, 16)
                .padding(.bottom, 8)

            HStack(spacing: 8) {
                ForEach([2, 4, 8, 16], id: \.self) { unit in
                    ChipButton(
                        label: "\(unit)",
                        selected: engine.beatUnit == unit
                    ) {
                        engine.setBeatUnit(unit)
                    }
                }
            }

            HStack {
                SectionLabel("Suddivisione")
                Spacer()
                Text(subdivisionName(engine.subdivision))
                    .font(.system(size: 11))
                    .foregroundStyle(Palette.amber)
            }
            .padding(.top, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(1...6, id: \.self) { value in
                        ChipButton(
                            label: "×\(value)",
                            selected: engine.subdivision == value
                        ) {
                            engine.setSubdivision(value)
                        }
                    }
                }
            }
            .padding(.top, 8)

            HStack(alignment: .center) {
                Text("Frazioni tra un battito e l'altro, con un click più leggero.")
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Button(action: engine.resetAccents) {
                    Text("Reimposta accenti")
                        .font(.system(size: 12))
                        .foregroundStyle(Palette.amber)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 12)
        }
    }

    // MARK: - Audio

    private var soundPanel: some View {
        Panel {
            ToggleRow(
                title: "Click audio",
                subtitle: engine.soundEnabled
                    ? "Segnale sonoro su ogni battito"
                    : "Modalità silenziosa: solo indicatore visivo",
                isOn: Binding(
                    get: { engine.soundEnabled },
                    set: { engine.setSoundEnabled($0) }
                )
            )
        }
    }

    private func subdivisionName(_ value: Int) -> String {
        switch value {
        case 1: return "nessuna"
        case 2: return "duine"
        case 3: return "terzine"
        case 4: return "quartine"
        case 5: return "quintine"
        default: return "sestine"
        }
    }
}
