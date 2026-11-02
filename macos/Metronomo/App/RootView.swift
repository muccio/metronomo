import SwiftUI

struct RootView: View {
    @ObservedObject var metronome: MetronomeEngine
    @ObservedObject var chronometer: ChronometerEngine
    @State private var tab = 0

    var body: some View {
        ZStack {
            Palette.ink.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                TabSwitcher(selection: $tab)
                    .padding(.horizontal, 20)

                ZStack {
                    if tab == 0 {
                        MetronomeScreen(engine: metronome)
                            .transition(.opacity)
                    } else {
                        ChronometerScreen(engine: chronometer)
                            .transition(.opacity)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(maxWidth: 560)
        }
        .background(Palette.ink)
    }

    private var header: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(Palette.amber)
                .frame(width: 6, height: 26)

            VStack(alignment: .leading, spacing: 1) {
                Text("METRONOMO")
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(4)
                    .foregroundStyle(Palette.textPrimary)
                Text("tempo · accenti · segnali")
                    .font(.system(size: 11))
                    .tracking(0.6)
                    .foregroundStyle(Palette.textMuted)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 22)
        .padding(.top, 12)
        .padding(.bottom, 14)
    }
}
