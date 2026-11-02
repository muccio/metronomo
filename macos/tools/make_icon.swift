#!/usr/bin/env swift

import AppKit
import Foundation

// Renders the 1024x1024 master icon used by the macOS app.
// Usage: swift make_icon.swift <output.png>

let arguments = CommandLine.arguments
guard arguments.count >= 2 else {
    fputs("usage: make_icon.swift <output.png>\n", stderr)
    exit(1)
}
let outputPath = arguments[1]
let size = 1024.0
let full = NSRect(x: 0, y: 0, width: size, height: size)

guard let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: Int(size),
    pixelsHigh: Int(size),
    bitsPerSample: 8,
    samplesPerPixel: 4,
    hasAlpha: true,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
) else {
    fputs("cannot allocate bitmap\n", stderr)
    exit(1)
}

NSGraphicsContext.saveGraphicsState()
guard let context = NSGraphicsContext(bitmapImageRep: bitmap) else {
    fputs("cannot create context\n", stderr)
    exit(1)
}
NSGraphicsContext.current = context
context.imageInterpolation = .high

func color(_ hex: UInt32, alpha: CGFloat = 1) -> NSColor {
    NSColor(
        calibratedRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

// Background squircle.
let background = NSBezierPath(
    roundedRect: NSRect(x: 40, y: 40, width: 944, height: 944),
    xRadius: 210,
    yRadius: 210
)
background.addClip()

let bodyGradient = NSGradient(colors: [color(0x1B222C), color(0x080A0D)])!
bodyGradient.draw(in: full, angle: -90)

// Warm glow rising from the base of the metronome.
let glow = NSGradient(
    colorsAndLocations:
        (color(0xFFC24B, alpha: 0.30), 0.0),
        (color(0xFFC24B, alpha: 0.10), 0.35),
        (color(0xFFC24B, alpha: 0.0), 0.75)
)!
glow.draw(
    fromCenter: NSPoint(x: 512, y: 330),
    radius: 0,
    toCenter: NSPoint(x: 512, y: 330),
    radius: 470,
    options: []
)

// Subtle inner border.
NSColor.white.withAlphaComponent(0.06).setStroke()
let border = NSBezierPath(
    roundedRect: NSRect(x: 44, y: 44, width: 936, height: 936),
    xRadius: 206,
    yRadius: 206
)
border.lineWidth = 2
border.stroke()

// Metronome base.
let base = NSBezierPath(
    roundedRect: NSRect(x: 252, y: 208, width: 520, height: 74),
    xRadius: 26,
    yRadius: 26
)
NSGradient(colors: [color(0xFFD98A), color(0xE8A02A)])!.draw(in: base, angle: -90)

// Metronome body (trapezoid).
let body = NSBezierPath()
body.move(to: NSPoint(x: 432, y: 790))
body.line(to: NSPoint(x: 592, y: 790))
body.line(to: NSPoint(x: 714, y: 276))
body.line(to: NSPoint(x: 310, y: 276))
body.close()
NSGradient(colors: [color(0xFFE3A6), color(0xF3AC2F)])!.draw(in: body, angle: -90)

// Recessed front panel.
let panel = NSBezierPath()
panel.move(to: NSPoint(x: 456, y: 748))
panel.line(to: NSPoint(x: 568, y: 748))
panel.line(to: NSPoint(x: 660, y: 316))
panel.line(to: NSPoint(x: 364, y: 316))
panel.close()
color(0x0B0E13).withAlphaComponent(0.92).setFill()
panel.fill()

// Beat ticks on the left edge of the panel.
for index in 0..<4 {
    let y = 380.0 + Double(index) * 72.0
    let tick = NSBezierPath()
    tick.move(to: NSPoint(x: 388 + Double(index) * 3, y: y))
    tick.line(to: NSPoint(x: 412 + Double(index) * 3, y: y))
    tick.lineWidth = 7
    tick.lineCapStyle = .round
    color(0xFFC24B, alpha: 0.55).setStroke()
    tick.stroke()
}

// Pendulum.
let pendulum = NSBezierPath()
pendulum.move(to: NSPoint(x: 512, y: 322))
pendulum.line(to: NSPoint(x: 556, y: 720))
pendulum.lineWidth = 16
pendulum.lineCapStyle = .round
color(0x10151C).setStroke()
pendulum.stroke()

let pendulumCore = NSBezierPath()
pendulumCore.move(to: NSPoint(x: 512, y: 322))
pendulumCore.line(to: NSPoint(x: 556, y: 720))
pendulumCore.lineWidth = 8
pendulumCore.lineCapStyle = .round
NSGradient(colors: [color(0x6FE3C9), color(0x36B49C)])!.draw(in: pendulumCore, angle: -70)

// Pendulum weight.
context.cgContext.saveGState()
context.cgContext.translateBy(x: 547, y: 540)
context.cgContext.rotate(by: .pi * 0.075)
let weight = NSBezierPath(
    roundedRect: NSRect(x: -52, y: -22, width: 104, height: 44),
    xRadius: 18,
    yRadius: 18
)
NSGradient(colors: [color(0x8CF0D8), color(0x3FC3A8)])!.draw(in: weight, angle: -90)
context.cgContext.restoreGState()

// Pivot dot at the bottom of the pendulum.
let pivot = NSBezierPath(ovalIn: NSRect(x: 494, y: 304, width: 36, height: 36))
color(0xFFE2A3).setFill()
pivot.fill()

// Small teal status LED on the base.
let ledGlow = NSBezierPath(ovalIn: NSRect(x: 470, y: 226, width: 84, height: 40))
color(0x6FE3C9, alpha: 0.20).setFill()
ledGlow.fill()
let led = NSBezierPath(ovalIn: NSRect(x: 492, y: 236, width: 40, height: 20))
color(0x6FE3C9).setFill()
led.fill()

NSGraphicsContext.restoreGraphicsState()

guard let png = bitmap.representation(using: .png, properties: [:]) else {
    fputs("cannot encode png\n", stderr)
    exit(1)
}
try png.write(to: URL(fileURLWithPath: outputPath))
print("wrote \(outputPath)")
