# Vow

Digital wellbeing & self-binding restrictions Android app with psychology-driven friction, local DNS filtering, and system-level enforcement. Built with Kotlin and Jetpack Compose.

## Key Features

- **Accessibility Service Protection (`VowAccessibilityService`, `ProfileGuard`)**:
  - Intercepts restricted app launches and addictive short-video loops (Shorts/Reels).
  - Shields Vow's application settings and device administrator status against impulse disabling.
  - Profile-aware: enforces restrictions strictly on the personal profile while leaving work profile apps untouched.
  - Safe event copying on Android 8–12 (API 26–32) to avoid recycled event exceptions.
- **Local DNS Sinkhole VPN & Private DNS Lock (`VowVpnService`, `PrivateDns`, `DnsPacket`)**:
  - Routes only DNS traffic through a local loopback VPN; returns NXDOMAIN for blocked adult, distraction, or gambling domains.
  - Private DNS lock prevents bypassing sinkholes via system DNS overrides.
  - Fully local: zero packet data leaves the device.
- **Device Administrator Integration (`VowDeviceAdminReceiver`)**:
  - Enforces system security policies declared in `device_admin_policies.xml` (including `<force-lock />`).
  - Optional Device Owner setup via ADB for non-removable restriction mode.
- **Psychological Friction & Restriction Modes**:
  - **Time-Decaying Allowances**: Weekly decaying limits (e.g., YouTube allowance automatically decaying 3% per week).
  - **Cooling-Off Delays**: 24–72 hour delays before easing restriction rules (tightening is always immediate).
  - **Paced Breathing Gate (`BreathingScreen.kt`)**: 90-second haptic-guided breathing exercise required before granting temporary bypasses.
  - **Permanent Seals**: Hold-to-seal irreversible lock-in for critical restriction periods.
- **Lume Ecosystem Integration**:
  - IPC bus (`LumeBus.kt`, `VowStatusProvider.kt`) for inter-app state and sleep mode synchronization.
- **Testing & Verification**:
  - Comprehensive unit test suite with Robolectric.
  - Visual verification with Roborazzi screenshot tests.

## Architecture

```
vow-android/
├── app/
│   ├── src/main/java/com/vow/app/
│   │   ├── service/
│   │   │   ├── accessibility/   # VowAccessibilityService, ProfileGuard
│   │   │   ├── vpn/             # VowVpnService, PrivateDns, DnsPacket
│   │   │   ├── admin/           # VowDeviceAdminReceiver
│   │   │   ├── blocking/        # BlockPolicy, UsageTracker
│   │   │   └── usage/           # UsageMonitorService
│   │   ├── focus/               # FocusActivity, FocusLogic, FocusSnapshotLoader
│   │   ├── ui/
│   │   │   ├── components/      # SystemScreens, EditFeedback, Haptics
│   │   │   ├── screens/         # Home, Settings, Bypass, Breathing, Profiles, Night
│   │   │   └── theme/           # Aurora dark theme, Typography, Colors
│   │   ├── ipc/                 # LumeBus, VowStatusProvider, SleepSyncReceiver
│   │   └── data/                # VowPreferences, VowRepository, ExceptionPolicy
│   └── src/main/res/xml/        # device_admin_policies.xml
```

## Build & Test

```bash
cd vow-android

# Run unit & Robolectric tests
./gradlew testDebugUnitTest -q --console=plain --no-scan

# Run a specific test class
./gradlew testDebugUnitTest --tests "com.vow.app.focus.FocusLogicTest" -q --console=plain --no-scan

# Build debug APK
./gradlew assembleDebug -q --console=plain --no-scan

# Lint check
./gradlew lintDebug -q --console=plain --no-scan
```
