import AVFoundation
import os

/// Immutable snapshot the audio thread reads at the start of every block.
/// Kept free of heap values (accents are packed into a bit mask) so the render
/// callback never allocates or touches ARC-managed storage.
struct MetronomeParameters: Sendable {
    var bpm: Double = 100
    var beatsPerBar: Int32 = 4
    var subdivision: Int32 = 1
    var soundEnabled: Bool = true
    /// Two bits per beat, index 0 in the lowest bits: 0 silent, 1 normal, 2 strong.
    var accentsMask: UInt32 = 0
}

private struct MetronomeRenderInfo: Sendable {
    var currentBeat: Int = -1
    var pulse: UInt64 = 0
}

/// Renders the click track block by block at 48 kHz. Every beat sits on an
/// exact frame position, so the tempo never drifts even after hours.
final class MetronomeRenderer: @unchecked Sendable {

    static let sampleRate: Double = ClickSynth.sampleRate

    private let parameters = OSAllocatedUnfairLock(initialState: MetronomeParameters())
    private let info = OSAllocatedUnfairLock(initialState: MetronomeRenderInfo())

    private let strong: [Float]
    private let normal: [Float]
    private let soft: [Float]

    private struct Voice {
        var start: Int64
        var samples: [Float]
    }

    private var voices: [Voice] = []
    private var blockStart: Int64 = 0
    private var nextEvent: Double = 0
    private var beatIndex = 0
    private var subIndex = 0

    init() {
        strong = ClickSynth.click(freq: 1568.0, durationMs: 70.0, decayMs: 22.0, gain: 0.95)
        normal = ClickSynth.click(freq: 1046.0, durationMs: 50.0, decayMs: 13.0, gain: 0.65)
        soft = ClickSynth.click(freq: 784.0, durationMs: 32.0, decayMs: 8.0, gain: 0.32)
        voices.reserveCapacity(16)
    }

    func setParameters(_ value: MetronomeParameters) {
        parameters.withLockUnchecked { $0 = value }
    }

    var currentBeat: Int {
        info.withLockUnchecked { $0.currentBeat }
    }

    /// Only safe to call while the audio engine is paused.
    func reset() {
        blockStart = 0
        nextEvent = 0
        beatIndex = 0
        subIndex = 0
        voices.removeAll(keepingCapacity: true)
        info.withLockUnchecked { $0 = MetronomeRenderInfo() }
    }

    func render(into buffer: UnsafeMutablePointer<Float>, frameCount: Int) {
        let p = parameters.withLockUnchecked { $0 }
        let blockEnd = blockStart + Int64(frameCount)
        let samplesPerBeat = Self.sampleRate * 60.0 / max(1.0, p.bpm)
        let subdivision = max(1, Int(p.subdivision))
        let beatsPerBar = max(1, Int(p.beatsPerBar))

        while nextEvent < Double(blockEnd) {
            let startFrame = Int64(nextEvent.rounded())
            let beatSlot = beatIndex % beatsPerBar
            let level = (p.accentsMask >> UInt32(2 * beatSlot)) & 0b11

            if p.soundEnabled && level != 0 {
                let samples: [Float]
                if subIndex > 0 {
                    samples = soft
                } else if level >= 2 {
                    samples = strong
                } else {
                    samples = normal
                }
                if voices.count >= 16 {
                    voices.removeFirst()
                }
                voices.append(Voice(start: startFrame, samples: samples))
            }

            if subIndex == 0 {
                info.withLockUnchecked {
                    $0.currentBeat = beatSlot
                    $0.pulse &+= 1
                }
            }

            subIndex += 1
            if subIndex >= subdivision {
                subIndex = 0
                beatIndex += 1
            }
            nextEvent += samplesPerBeat / Double(subdivision)
        }

        buffer.update(repeating: 0, count: frameCount)

        var index = 0
        while index < voices.count {
            let voice = voices[index]
            let end = voice.start + Int64(voice.samples.count)

            if end <= blockStart {
                voices.remove(at: index)
                continue
            }

            let from = max(voice.start, blockStart)
            let to = min(end, blockEnd)
            var frame = from
            while frame < to {
                let destination = Int(frame - blockStart)
                let source = Int(frame - voice.start)
                buffer[destination] += voice.samples[source]
                frame += 1
            }
            index += 1
        }

        for frame in 0..<frameCount {
            buffer[frame] = min(1.0, max(-1.0, buffer[frame]))
        }

        blockStart = blockEnd
    }
}

/// Owns the AVAudioEngine that pulls blocks from the renderer.
final class MetronomeAudio {

    private let engine = AVAudioEngine()
    let renderer = MetronomeRenderer()
    private let format: AVAudioFormat

    init() {
        format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: MetronomeRenderer.sampleRate,
            channels: 1,
            interleaved: false
        )!

        let renderer = self.renderer
        let source = AVAudioSourceNode(format: format) { _, _, frameCount, audioBufferList -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
            guard let data = buffers[0].mData else { return noErr }
            let samples = data.assumingMemoryBound(to: Float.self)
            renderer.render(into: samples, frameCount: Int(frameCount))
            return noErr
        }

        engine.attach(source)
        engine.connect(source, to: engine.mainMixerNode, format: format)
        engine.mainMixerNode.outputVolume = 1.0
        engine.prepare()
    }

    func update(_ parameters: MetronomeParameters) {
        renderer.setParameters(parameters)
    }

    @discardableResult
    func start() -> Bool {
        renderer.reset()
        do {
            try engine.start()
            return true
        } catch {
            return false
        }
    }

    func stop() {
        engine.pause()
    }
}
