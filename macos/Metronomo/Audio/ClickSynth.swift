import Foundation

/// Procedural click synthesis. Every click is a short sine burst with a fast
/// exponential decay, so the metronome needs no audio assets at all.
enum ClickSynth {

    static let sampleRate: Double = 48_000

    /// A short pitched click.
    /// - Parameters:
    ///   - freq: fundamental frequency in Hz
    ///   - durationMs: total click length
    ///   - decayMs: time constant of the exponential decay
    ///   - gain: 0...1 peak amplitude
    static func click(freq: Double, durationMs: Double, decayMs: Double, gain: Double) -> [Float] {
        let count = max(1, Int(sampleRate * durationMs / 1000.0))
        var out = [Float](repeating: 0, count: count)
        let tau = decayMs / 1000.0
        let attack = 0.0012
        let twoPi = 2.0 * Double.pi

        for i in 0..<count {
            let t = Double(i) / sampleRate
            let envelope = exp(-t / tau) * min(1.0, t / attack)
            let signal = sin(twoPi * freq * t) * 0.78 +
                sin(twoPi * freq * 2.01 * t) * 0.22
            out[i] = Float(max(-1.0, min(1.0, signal * envelope * gain)))
        }
        return out
    }

    /// Rich bell used by the chronometer signals.
    static func bell(freqs: [Double], durationMs: Double, decayMs: Double, gain: Double) -> [Float] {
        let count = max(1, Int(sampleRate * durationMs / 1000.0))
        var out = [Float](repeating: 0, count: count)
        let tau = decayMs / 1000.0
        let attack = 0.004
        let twoPi = 2.0 * Double.pi

        for i in 0..<count {
            let t = Double(i) / sampleRate
            let envelope = exp(-t / tau) * min(1.0, t / attack) * gain
            var signal = 0.0
            for (index, f) in freqs.enumerated() {
                let weight = 1.0 / Double(index + 1)
                signal += sin(twoPi * f * t) * weight
            }
            signal /= Double(freqs.count) * 0.65
            out[i] = Float(max(-1.0, min(1.0, signal * envelope)))
        }
        return out
    }
}
