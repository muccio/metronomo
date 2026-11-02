import AppKit
import Combine
import Foundation
import UserNotifications

/// Stopwatch / countdown timer with audible signals every N minutes.
///
/// Elapsed time comes from `ContinuousClock`, which keeps counting across
/// system sleep: delayed UI ticks can never make the clock drift, and every
/// signal that became due while the Mac was asleep is caught up at once.
@MainActor
final class ChronometerEngine: ObservableObject {

    enum Mode: String, CaseIterable {
        case stopwatch
        case timer
    }

    @Published private(set) var running = false
    @Published private(set) var mode: Mode = .stopwatch
    @Published private(set) var elapsed: TimeInterval = 0
    @Published private(set) var target: TimeInterval = 10 * 60
    @Published private(set) var intervalMinutes: Int
    @Published private(set) var soundEnabled: Bool
    @Published private(set) var notificationsEnabled: Bool
    @Published private(set) var signals = 0
    @Published private(set) var finished = false
    @Published private(set) var pulse: UInt64 = 0

    var remaining: TimeInterval { max(0, target - elapsed) }
    var progress: Double { target > 0 ? min(1, max(0, elapsed / target)) : 0 }

    /// Seconds until the next interval signal, or nil when disabled.
    var nextSignalIn: TimeInterval? {
        guard intervalMinutes > 0 else { return nil }
        let interval = Double(intervalMinutes) * 60
        let next = Double(signals + 1) * interval
        let limit = mode == .timer ? target : .infinity
        guard next <= limit else { return nil }
        return max(0, next - elapsed)
    }

    private let clock = ContinuousClock()
    private let defaults = UserDefaults.standard
    private let chime = ChimePlayer()
    private var startedAt: ContinuousClock.Instant?
    private var accumulated: TimeInterval = 0
    private var ticker: Timer?
    private var activity: NSObjectProtocol?
    private var notificationsRequested = false

    init() {
        intervalMinutes = (defaults.object(forKey: Key.interval) as? Int ?? 5)
            .clamped(to: 0...180)
        target = TimeInterval(
            (defaults.object(forKey: Key.target) as? Int ?? 10).clamped(to: 1...600)
        ) * 60
        soundEnabled = defaults.object(forKey: Key.sound) as? Bool ?? true
        notificationsEnabled = defaults.object(forKey: Key.notifications) as? Bool ?? true
    }

    // MARK: - Transport

    func start() {
        guard !running else { return }

        if mode == .timer && (elapsed >= target || finished) {
            accumulated = 0
            elapsed = 0
            finished = false
            signals = 0
        } else {
            accumulated = elapsed
        }

        startedAt = clock.now
        running = true
        finished = false
        startTicker()
        beginActivity()
        requestNotificationsIfNeeded()
    }

    func pause() {
        guard running else { return }
        accumulated = currentElapsed()
        startedAt = nil
        elapsed = accumulated
        running = false
        ticker?.invalidate()
        ticker = nil
        endActivity()
    }

    func toggle() {
        running ? pause() : start()
    }

    func reset() {
        ticker?.invalidate()
        ticker = nil
        accumulated = 0
        startedAt = nil
        running = false
        elapsed = 0
        signals = 0
        finished = false
        endActivity()
    }

    func setMode(_ newMode: Mode) {
        guard newMode != mode else { return }
        reset()
        mode = newMode
    }

    func setInterval(minutes: Int) {
        let clamped = minutes.clamped(to: 0...180)
        guard clamped != intervalMinutes else { return }
        let interval = Double(clamped) * 60
        signals = clamped > 0 ? Int(elapsed / interval) : 0
        intervalMinutes = clamped
        defaults.set(clamped, forKey: Key.interval)
    }

    func setTarget(minutes: Int) {
        guard !running else { return }
        let clamped = minutes.clamped(to: 1...600)
        target = TimeInterval(clamped) * 60
        elapsed = 0
        finished = false
        signals = 0
        accumulated = 0
        defaults.set(clamped, forKey: Key.target)
    }

    func setSoundEnabled(_ enabled: Bool) {
        soundEnabled = enabled
        defaults.set(enabled, forKey: Key.sound)
    }

    func setNotificationsEnabled(_ enabled: Bool) {
        notificationsEnabled = enabled
        defaults.set(enabled, forKey: Key.notifications)
        if enabled {
            requestNotificationsIfNeeded()
        }
    }

    // MARK: - Ticker

    private func startTicker() {
        ticker?.invalidate()
        let timer = Timer(timeInterval: 0.033, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        RunLoop.main.add(timer, forMode: .common)
        ticker = timer
    }

    private func tick() {
        guard running else { return }

        let now = currentElapsed()
        var signals = signals
        var pulse = pulse
        var didFinish = false
        var stillRunning = true

        let interval = Double(intervalMinutes) * 60
        let deadline = mode == .timer ? target : .infinity

        if interval > 0 {
            // Catch up on every signal that became due, even if ticks were delayed.
            while Double(signals + 1) * interval <= now
                && Double(signals + 1) * interval <= deadline {
                signals += 1
                pulse &+= 1
                if soundEnabled { chime.signal() }
                announceSignal(number: signals)
            }
        }

        if mode == .timer && now >= target {
            didFinish = true
            stillRunning = false
            pulse &+= 1
            if soundEnabled { chime.finish() }
            announceFinish()
            accumulated = target
            startedAt = nil
            ticker?.invalidate()
            ticker = nil
            endActivity()
        }

        elapsed = didFinish ? target : now
        self.signals = signals
        self.pulse = pulse
        self.finished = didFinish
        running = stillRunning
    }

    private func currentElapsed() -> TimeInterval {
        guard let startedAt else { return accumulated }
        let duration = clock.now - startedAt
        let components = duration.components
        let live = Double(components.seconds) + Double(components.attoseconds) / 1e18
        return accumulated + live
    }

    // MARK: - Background work & notifications

    private func beginActivity() {
        guard activity == nil else { return }
        activity = ProcessInfo.processInfo.beginActivity(
            options: [.userInitiated, .idleSystemSleepDisabled],
            reason: "Cronometro attivo"
        )
    }

    private func endActivity() {
        if let activity {
            ProcessInfo.processInfo.endActivity(activity)
            self.activity = nil
        }
    }

    private func requestNotificationsIfNeeded() {
        guard notificationsEnabled, !notificationsRequested else { return }
        notificationsRequested = true
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    private func announceSignal(number: Int) {
        guard notificationsEnabled, !NSApplication.shared.isActive else { return }
        deliver(title: "Segnale", body: "\(intervalMinutes) min · segnale \(number)")
    }

    private func announceFinish() {
        guard notificationsEnabled else { return }
        deliver(title: "Timer completato", body: "Sono passati \(Int(target / 60)) minuti.")
    }

    private func deliver(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = nil
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    // MARK: - Formatting

    nonisolated static func formatClock(_ seconds: TimeInterval) -> String {
        let total = Int(seconds)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, secs)
            : String(format: "%02d:%02d", minutes, secs)
    }

    nonisolated static func formatStopwatch(_ seconds: TimeInterval) -> String {
        let totalCentis = Int((max(0, seconds) * 100).rounded())
        let minutes = totalCentis / 6000
        let secs = (totalCentis % 6000) / 100
        let centis = totalCentis % 100
        return String(format: "%02d:%02d,%02d", minutes, secs, centis)
    }

    nonisolated static func formatShort(_ seconds: TimeInterval) -> String {
        let total = Int(ceil(seconds))
        let minutes = total / 60
        let secs = total % 60
        return minutes > 0
            ? String(format: "%d:%02d", minutes, secs)
            : String(format: "%d s", secs)
    }

    private enum Key {
        static let interval = "chronometer.interval"
        static let target = "chronometer.target"
        static let sound = "chronometer.sound"
        static let notifications = "chronometer.notifications"
    }
}
