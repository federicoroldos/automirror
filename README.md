# AutoMirror

Mirrors your phone's screen on **Android Auto** and lets you use it by touching the car's screen.

When you connect the phone to the car, AutoMirror shows up in the Android Auto app menu. Opening it
shows the phone's screen in landscape, filling the whole car screen, with Back, Home, Recents and
Notifications buttons on top.

> ⚠️ Only use it while the car is parked. Personal project, not on the Play Store and not affiliated with Google.

## Screenshots

Taken with the [Desktop Head Unit](#testing-without-a-car) (Google's car screen simulator).

![Phone home screen mirrored on the car, with the Back, Home, Recents and Notifications buttons](docs/screenshots/auto-inicio.png)

![Browser open on the phone, used from the car's screen](docs/screenshots/auto-navegador.png)

| Capture permission on the phone | Settings on the phone |
|---|---|
| ![Dialog to share the entire screen](docs/screenshots/celular-permiso.png) | ![AutoMirror settings screen](docs/screenshots/celular-configuracion.png) |

## How it works

```
 Phone                                                Car
┌──────────────────────────────┐                  ┌──────────────────────┐
│ MediaProjection (capture) ───┼─► VirtualDisplay ─┼─► "map" surface      │
│                              │                  │   (Android Auto,     │
│ AccessibilityService ◄───────┼── touches ◄──────┼── navigation         │
│   (injects gestures)         │                  │   template)          │
└──────────────────────────────┘                  └──────────────────────┘
```

- **On the car side**, the app declares itself as a **navigation** app using the
  [Car App Library](https://developer.android.com/training/cars/apps). Android Auto gives those apps
  a free surface to draw the map on; AutoMirror draws the phone's screen there instead.
- **Capture**: `MediaProjection` creates a `VirtualDisplay` that mirrors the screen straight onto
  that surface, with no intermediate copies.
- **Touch**: the surface receives taps, drags, flings and pinches. They're converted to phone
  coordinates and injected with an `AccessibilityService` (`dispatchGesture`), no root needed.
- **Landscape**: while mirroring, an invisible accessibility window requests landscape orientation,
  which also rotates apps locked to portrait.
- **Full screen**: if the app has `WRITE_SECURE_SETTINGS` (granted via adb), it changes the phone's
  logical resolution (like `wm size`) so it has the same aspect ratio as the car's screen. It
  restores it when mirroring ends.
- **Screen on**: a wake lock keeps the phone awake; if the screen turns off, the car shows black.

### Limitations

- Android Auto only forwards simple taps, drags, flings and pinches. There's **no** long press or
  free multi-finger gestures. Drags always start at the center of the screen, because Android Auto
  doesn't report where the finger went down.
- There's some latency: Android Auto encodes the image as video to send it to the car.
- Video only; audio goes through Android Auto as usual.
- Every time it starts, you have to accept the screen capture permission on the phone (Android
  requires it since Android 14).
- The car needs a touchscreen.
- In Android Auto's split view (apps or media on the side), the image gets cropped instead of
  scaled down.

## Installation

Requirements: Android 10 or later on the phone, a PC on the same Wi-Fi network, and JDK 17+ with the
Android SDK (the easiest way is to install [Android Studio](https://developer.android.com/studio),
which includes both).

### Recommended: with an AI agent and wireless debugging

The app is meant to be installed from the PC with an AI agent that has terminal access (for
example [Claude Code](https://claude.com/claude-code)), which builds, installs and configures
everything over adb, wirelessly.

1. On the phone: **Developer options → Wireless debugging** → turn on →
   **Pair device with pairing code**.
2. From this folder, ask the agent something like:

   > Build AutoMirror and install it on my phone via wireless debugging. The pairing code is
   > `123456`, the pairing IP:port is `192.168.0.10:37000` and the connection IP:port is
   > `192.168.0.10:41000`. Grant it the resolution permission, enable the accessibility service
   > and exempt it from battery restrictions.

### Manual

```sh
# Build (on Windows: gradlew.bat)
./gradlew assembleDebug

# Pair and connect over Wi-Fi (values from the "Wireless debugging" screen)
adb pair <ip>:<pairing-port> <code>
adb connect <ip>:<connection-port>

# Install
adb install --user 0 -r app/build/outputs/apk/debug/app-debug.apk

# Permission to match the resolution to the car's (optional: without it you get black bars)
adb shell pm grant --user 0 com.automirror android.permission.WRITE_SECURE_SETTINGS

# Accessibility service (for touch input).
# Note: this replaces the list; if you already use other services, add them separated by ':'
adb shell settings put secure enabled_accessibility_services com.automirror/.TouchService
adb shell settings put secure accessibility_enabled 1

# Keep the system from killing it in the background
adb shell cmd deviceidle whitelist +com.automirror
adb shell cmd appops set com.automirror RUN_ANY_IN_BACKGROUND allow
```

Installing via adb avoids the "restricted setting" block that Android applies to the accessibility
service of apps installed from a file.

### Android Auto

Android Auto hides apps that don't come from the Play Store until you enable them:

1. Settings → Connected devices → **Android Auto** (or the Android Auto app).
2. Tap **Version** several times until developer mode is enabled.
3. **⋮ → Developer settings → Unknown sources**.

## Usage

1. Connect the phone to the car.
2. Open **AutoMirror** from the Android Auto menu.
3. On the phone, accept capturing the **entire screen**.

To stop: leave the app on the car, disconnect, or tap **Stop** in the notification.

## Troubleshooting

- **The phone was left with a weird resolution**: open AutoMirror on the phone (it restores the
  resolution on startup), or run `adb shell wm size reset`.
- **Touches don't do anything**: check that AutoMirror's accessibility service is still enabled.
- **It doesn't show up in Android Auto**: check that *Unknown sources* is enabled.

## Testing without a car

With the [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu) (SDK Manager →
SDK Tools → *Android Auto Desktop Head Unit Emulator*):

```sh
# In Android Auto on the phone: ⋮ → Start head unit server
adb forward tcp:5277 tcp:5277
$ANDROID_SDK/extras/google/auto/desktop-head-unit
```

## Project structure

| File | What it does |
|---|---|
| `car/MirrorCarAppService.kt`, `car/MirrorSession.kt` | Android Auto entry point |
| `car/MirrorScreen.kt` | Navigation template, buttons and surface gestures |
| `Mirror.kt` | Connects the capture to the car's surface and converts coordinates |
| `MirrorService.kt` | Foreground service for the capture and wake lock |
| `ProjectionActivity.kt` | Requests the capture permission on the phone |
| `TouchService.kt` | Accessibility service: gestures, global buttons and orientation |
| `DisplaySize.kt` | Matches the resolution to the car's aspect ratio |
| `MainActivity.kt` | Settings screen on the phone |

## License

[MIT](LICENSE)
