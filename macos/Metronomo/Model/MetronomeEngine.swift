import Combine
import Foundation

/// Metronome state and persistence. The audio itself is rendered by
/// `MetronomeAudio` on a real-time thread; this object only owns the controls
/// and polls the current beat for the visual indicator.
@MainActor
final class MetronomeEngine: ObservableObject {

    static let minBPM = 30
    static let maxBPM = 300
    static let maxBeats = 16
    static let maxSubdivision = 6

    static let accentSilent = 0
    static let accentNormal = 1
    static let accentStrong = 2

    @Published private(set) var running = false
    @Published private(set) var bpm: Int
    @Published private(set) var beatsPerBar: Int
    @Published private(set) var beatUnit: Int
    @Published private(set) var subdivision: Int
    @Published private(set) var accents: [Int]
    @Published private(set) var soundEnabled: Bool
    @Published private(set) var currentBeat = -1

    private let defaults = UserDefaults.standard
    private let audio = MetronomeAudio()
    private var displayTimer: Timer?
    private var activity: NSObjectProtocol?
    private var taps: [TimeInterval] = []

    init() {
        let storedBeats = (defaults.object(forKey: Key.beats) as? Int ?? 4)
            .clamped(to: 1...Self.maxBeats)
        let storedAccents = (defaults.string(forKey: Key.accents) ?? "")
            .split(separator: ",")
            .compactMap { Int($0) }
        bpm = (defaults.object(forKey: Key.bpm) as? Int ?? 100)
            .clamped(to: Self.minBPM...Self.maxBPM)
        beatsPerBar = storedBeats
        beatUnit = defaults.object(forKey: Key.unit) as? Int ?? 4
        subdivision = (defaults.object(forKey: Key.subdivision) as? Int ?? 1)
            .clamped(to: 1...Self.maxSubdivision)
        soundEnabled = defaults.object(forKey: Key.sound) as? Bool ?? true
        accents = storedAccents.count == storedBeats
            ? storedAccents
            : (0..<storedBeats).map { $0 == 0 ? Self.accentStrong : Self.accentNormal }
        pushParameters()
    }

    var tempoName: String {
        switch bpm {
        case ..<40: return "Largo"
        case 40..<60: return "Larghetto"
        case 60..<66: return "Adagio"
        case 66..<76: return "Andante"
        case 76..<108: return "Moderato"
        case 108..<120: return "Allegretto"
        case 120..<156: return "Allegro"
        case 156..<176: return "Vivace"
        case 176..<200: return "Presto"
        default: return "Prestissimo"
        }
    }

    var signature: String { "\(beatsPerBar)/\(beatUnit)" }

    // MARK: - Transport

    func toggle() {
        running ? stop() : start()
    }

    func start() {
        guard !running else { return }
        pushParameters()
        guard audio.start() else { return }

        running = true
        currentBeat = -1
        startDisplayTimer()
        activity = ProcessInfo.processInfo.beginActivity(
            options: [.userInitiated, .latencyCritical],
            reason: "Metronomo in riproduzione"
        )
    }

    func stop() {
        guard running else { return }
        running = false
        currentBeat = -1
        audio.stop()
        displayTimer?.invalidate()
        displayTimer = nil
        taps.removeAll()
        if let activity {
            ProcessInfo.processInfo.endActivity(activity)
            self.activity = nil
        }
    }

    // MARK: - Controls

    func setBPM(_ value: Int) {
        let clamped = value.clamped(to: Self.minBPM...Self.maxBPM)
        guard clamped != bpm else { return }
        bpm = clamped
        defaults.set(clamped, forKey: Key.bpm)
        pushParameters()
    }

    func setBeatsPerBar(_ count: Int) {
        let clamped = count.clamped(to: 1...Self.maxBeats)
        guard clamped != beatsPerBar else { return }
        let resized = (0..<clamped).map { index -> Int in
            if index < accents.count { return accents[index] }
            return index == 0 ? Self.accentStrong : Self.accentNormal
        }
        beatsPerBar = clamped
        accents = resized
        currentBeat = -1
        defaults.set(clamped, forKey: Key.beats)
        defaults.set(resized.map(String.init).joined(separator: ","), forKey: Key.accents)
        pushParameters()
    }

    func setBeatUnit(_ unit: Int) {
        guard unit != beatUnit else { return }
        beatUnit = unit
        currentBeat = -1
        defaults.set(unit, forKey: Key.unit)
    }

    func setSubdivision(_ value: Int) {
        let clamped = value.clamped(to: 1...Self.maxSubdivision)
        guard clamped != subdivision else { return }
        subdivision = clamped
        defaults.set(clamped, forKey: Key.subdivision)
        pushParameters()
    }

    func setSoundEnabled(_ enabled: Bool) {
        soundEnabled = enabled
        defaults.set(enabled, forKey: Key.sound)
        pushParameters()
    }

    /// Cycles the accent of one beat: strong -> normal -> silent -> strong.
    func cycleAccent(at index: Int) {
        guard accents.indices.contains(index) else { return }
        var next = accents
        switch next[index] {
        case Self.accentStrong: next[index] = Self.accentNormal
        case Self.accentNormal: next[index] = Self.accentSilent
        default: next[index] = Self.accentStrong
        }
        accents = next
        defaults.set(next.map(String.init).joined(separator: ","), forKey: Key.accents)
        pushParameters()
    }

    func resetAccents() {
        let reset = (0..<beatsPerBar).map {
            $0 == 0 ? Self.accentStrong : Self.accentNormal
        }
        accents = reset
        defaults.set(reset.map(String.init).joined(separator: ","), forKey: Key.accents)
        pushParameters()
    }

    /// Tap tempo: average of the taps collected in the last 2.5 seconds.
    func tap() {
        let now = ProcessInfo.processInfo.systemUptime
        taps = taps.filter { now - $0 <= 2.5 }
        taps.append(now)
        guard taps.count >= 2 else { return }
        let intervals = zip(taps.dropFirst(), taps).map { $0 - $1 }
        let average = intervals.reduce(0, +) / Double(intervals.count)
        guard average > 0 else { return }
        setBPM(Int((60.0 / average).rounded()))
    }

    // MARK: - Private

    private func pushParameters() {
        var mask: UInt32 = 0
        for (index, level) in accents.prefix(Self.maxBeats).enumerated() {
            mask |= UInt32(level & 0b11) << UInt32(2 * index)
        }
        audio.update(
            MetronomeParameters(
                bpm: Double(bpm),
                beatsPerBar: Int32(beatsPerBar),
                subdivision: Int32(subdivision),
                soundEnabled: soundEnabled,
                accentsMask: mask
            )
        )
    }

    private func startDisplayTimer() {
        displayTimer?.invalidate()
        let timer = Timer(timeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.running else { return }
                let beat = self.audio.renderer.currentBeat
                if beat != self.currentBeat {
                    self.currentBeat = beat
                }
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        displayTimer = timer
    }

    private enum Key {
        static let bpm = "metronome.bpm"
        static let beats = "metronome.beats"
        static let unit = "metronome.unit"
        static let subdivision = "metronome.subdivision"
        static let accents = "metronome.accents"
        static let sound = "metronome.sound"
    }
}
