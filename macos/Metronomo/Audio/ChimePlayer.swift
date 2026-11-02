import AVFoundation

/// Plays the chronometer chimes. The buffers are synthesised procedurally at
/// launch, so the app ships without binary audio assets.
final class ChimePlayer {

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let format: AVAudioFormat
    private let signalBuffer: AVAudioPCMBuffer
    private let finishBuffer: AVAudioPCMBuffer

    init() {
        format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: ClickSynth.sampleRate,
            channels: 1,
            interleaved: false
        )!

        signalBuffer = Self.makeBuffer(
            samples: ClickSynth.bell(
                freqs: [880.0, 1320.0, 1760.0],
                durationMs: 900.0,
                decayMs: 260.0,
                gain: 0.75
            ),
            format: format
        )
        finishBuffer = Self.makeBuffer(
            samples: ClickSynth.bell(
                freqs: [660.0, 990.0, 1320.0],
                durationMs: 1500.0,
                decayMs: 420.0,
                gain: 0.8
            ),
            format: format
        )

        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: format)
        engine.mainMixerNode.outputVolume = 1.0
        engine.prepare()
    }

    func signal() {
        play(signalBuffer)
    }

    func finish() {
        play(finishBuffer)
    }

    private func play(_ buffer: AVAudioPCMBuffer) {
        if !engine.isRunning {
            do {
                try engine.start()
            } catch {
                return
            }
        }
        player.scheduleBuffer(buffer, at: nil, options: [], completionHandler: nil)
        if !player.isPlaying {
            player.play()
        }
    }

    private static func makeBuffer(samples: [Float], format: AVAudioFormat) -> AVAudioPCMBuffer {
        let buffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: AVAudioFrameCount(samples.count)
        )!
        buffer.frameLength = AVAudioFrameCount(samples.count)
        if let channel = buffer.floatChannelData?[0] {
            samples.withUnsafeBufferPointer { source in
                channel.update(from: source.baseAddress!, count: samples.count)
            }
        }
        return buffer
    }
}
