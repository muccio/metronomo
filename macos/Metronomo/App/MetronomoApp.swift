import SwiftUI

@main
struct MetronomoApp: App {
    @StateObject private var metronome = MetronomeEngine()
    @StateObject private var chronometer = ChronometerEngine()

    var body: some Scene {
        Window("Metronomo", id: "main") {
            RootView(metronome: metronome, chronometer: chronometer)
                .preferredColorScheme(.dark)
                .frame(minWidth: 420, minHeight: 660)
        }
        .defaultSize(width: 470, height: 900)
        .windowResizability(.contentMinSize)
    }
}
