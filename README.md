# Metronomo

App nativa per **Android** (Kotlin + Jetpack Compose) e **macOS** (Swift + SwiftUI) con due strumenti in un'unica schermata:

1. **Metronomo musicale** — tempo, metrica, suddivisioni e accenti per ogni battito.
2. **Cronometro / Timer** — segnali acustici impostabili ogni *X* minuti, anche a schermo spento.

Interfaccia in italiano, tema scuro, nessun asset audio: tutti i suoni sono sintetizzati a runtime.

## Download

| Piattaforma | File | Requisiti |
|---|---|---|
| Android | `metronomo-1.0.0.apk` | Android 8.0+ (API 26) |
| macOS Intel + Apple Silicon | `Metronomo-1.1.0-macOS-universal.zip` o `.dmg` | macOS 13 Ventura+ |

Tutti i file sono in [Releases](https://github.com/muccio/metronomo/releases).

> **macOS**: a build è firmata ad-hoc (non notarizzata). Al primo avvio, se Gatekeeper blocca l'app,
> fai clic destro su `Metronomo.app` → **Apri**, oppure esegui
> `xattr -dr com.apple.quarantine /Applications/Metronomo.app`.

## Schermate

| Metronomo | Cronometro / Timer |
|---|---|
| ![Metronomo](docs/metronomo.png) | ![Cronometro](docs/cronometro.png) |

---

## 1. Metronomo

| Funzione | Dettagli |
|---|---|
| Tempo | 30–300 BPM, slider + step ±1, **tap tempo** (media degli ultimi 2,5 s) |
| Indicazione | Largo → Prestissimo |
| Metrica | 1–12 battiti per battuta, unità 2/4/8/16 |
| Suddivisione | ×1 … ×6 (duine, terzine, quartine, quintine, sestine) |
| Accenti | Per ogni battito: **accento**, **normale**, **silenzio** (tocca il cerchio) |
| Indicatore | Pallino attivo animato, sincronizzato con l'audio |
| Audio | Click sintetizzati (accento più acuto e forte, suddivisioni più leggere), mute |

**Precisione**: il click non è programmato con timer ma renderizzato campione per campione a 48 kHz.
Ogni evento è posizionato su un frame esatto, quindi il tempo non deriva nemmeno dopo ore.

- Android: `AudioTrack` in streaming su thread a priorità massima, buffer di 512 frame (~10 ms)
  con `PERFORMANCE_MODE_LOW_LATENCY`.
- macOS: `AVAudioEngine` + `AVAudioSourceNode` in tempo reale, buffer di 512 frame.

## 2. Cronometro / Timer

| Funzione | Dettagli |
|---|---|
| Cronometro | Avvia / Pausa / Riprendi / Azzera, centesimi di secondo |
| Timer | Durata 1–600 min, conto alla rovescia con barra di avanzamento |
| Segnali | Campanello ogni **N minuti** (0 = off, 1–180), presets rapidi |
| Extra | Conteggio segnali emessi, countdown al prossimo segnale, flash visivo |
| Background | I segnali arrivano anche con l'app in background o il Mac in stop |

Il tempo trascorso è sempre calcolato da un orologio monotono (`elapsedRealtime` su Android,
`ContinuousClock` su macOS): eventuali ritardi del ticker non producono drift, e i segnali
arretrati vengono recuperati tutti insieme.

Su macOS i segnali fuori dall'app arrivano come notifiche di sistema; Android usa un foreground
service con `PARTIAL_WAKE_LOCK` e una notifica con Pausa/Azzera.

---

## Build

### Android

Requisiti: JDK 17, Android SDK (compileSdk 34).

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Installazione su device/emulatore collegato:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.metronomo.app/.MainActivity
```

Release (R8 attivo, APK ~0,8 MB):

```bash
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

#### Firma

La firma release è opzionale: se `keystore.properties` non esiste (clone pulito, CI) l'APK
viene prodotto non firmato. Per firmarlo creare una keystore e un `keystore.properties`
nella root del progetto (entrambi **ignorati da git**):

```properties
storeFile=.keystore/metronomo-release.jks
storePassword=...
keyAlias=metronomo
keyPassword=...
```

```bash
keytool -genkeypair -keystore .keystore/metronomo-release.jks -alias metronomo \
  -keyalg RSA -keysize 4096 -validity 10950
```

### macOS

Requisiti: Xcode 15+ (testato con Xcode 27), macOS 13+.

```bash
cd macos
./build.sh     # build universale arm64 + x86_64 in macos/build/
./package.sh   # ZIP + DMG firmati ad-hoc in macos/dist/
```

Il progetto Xcode è versionato e si apre direttamente con `open macos/Metronomo.xcodeproj`.
Se modifichi `project.yml` rigenera il progetto con [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd macos && xcodegen generate --spec project.yml
```

L'icona è generata da codice (nessun binario nel repository):

```bash
swift macos/tools/make_icon.swift /tmp/icon_1024.png
```

## Struttura

```
app/src/main/java/com/metronomo/app/   # Android
├── MainActivity.kt              # entry point, edge-to-edge, permesso notifiche
├── MetronomoApp.kt              # Application: singleton dei due engine
├── audio/
│   ├── MetronomeEngine.kt       # motore audio campione-accurato (AudioTrack streaming)
│   ├── ChronometerEngine.kt     # cronometro/timer, ticker, segnali, vibrazione
│   ├── ClickSynth.kt            # sintesi dei click e dei campanelli
│   └── ChimePlayer.kt           # WAV generati in cache + SoundPool
├── service/
│   └── TimerService.kt          # foreground service + notifica con Pausa/Azzera
└── ui/
    ├── MetronomoRoot.kt         # header e tab
    ├── MetronomeScreen.kt       # UI metronomo
    ├── ChronometerScreen.kt     # UI cronometro/timer
    ├── Components.kt            # card, chip, stepper, trasporto
    └── theme/Theme.kt           # palette e tipografia

macos/Metronomo/
├── App/
│   ├── MetronomoApp.swift       # @main, finestra SwiftUI
│   └── RootView.swift           # header, tab e contenitore
├── Audio/
│   ├── MetronomeAudio.swift     # AVAudioEngine + renderer campione-accurato
│   ├── ClickSynth.swift         # sintesi dei click e dei campanelli
│   └── ChimePlayer.swift        # player dei segnali del cronometro
├── Model/
│   ├── MetronomeEngine.swift    # stato, controlli, persistenza, tap tempo
│   └── ChronometerEngine.swift  # cronometro/timer, ticker, segnali, notifiche
├── UI/
│   ├── Theme.swift              # palette e tipografia
│   ├── Components.swift         # card, chip, stepper, indicatori
│   ├── MetronomeScreen.swift    # UI metronomo
│   └── ChronometerScreen.swift  # UI cronometro/timer
├── Resources/Assets.xcassets    # icona generata
└── Support.swift
```

## Permessi (Android)

- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — cronometro attivo in background
- `WAKE_LOCK` — mantenere la CPU attiva per i segnali a schermo spento
- `POST_NOTIFICATIONS` — notifica del cronometro (Android 13+)
- `VIBRATE` — vibrazione opzionale

## Note

- Le impostazioni (BPM, metrica, accenti, intervallo, durata, toggle audio/notifiche) sono
  persistite in `SharedPreferences` su Android e `UserDefaults` su macOS, e ripristinate al riavvio.
- Su Android il metronomo continua a suonare se l'app va in background; si ferma chiudendo l'app.
- Su macOS le notifiche del cronometro vengono inviate solo quando l'app non è in primo piano.
