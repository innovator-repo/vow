# CLAUDE.md - vow

## App Overview & Key Packages

**Vow** (`vow-android/`) is an Android digital wellbeing and self-binding restriction application designed with psychology-driven friction mechanics, local VPN DNS sinkholing, accessibility enforcement, and device administration.

- **Working Directory**: `vow-android/`
- **Core Architecture & Key Packages**: `com.vow.app`
  - `service/accessibility/`: `VowAccessibilityService.kt` and `ProfileGuard.kt`
    - Intercepts launches of blocked apps and addictive video feeds (Shorts/Reels).
    - Protects Vow's settings and device admin removal pages from impulsive circumvention.
    - Profile isolation: verifies usage stats so personal restrictions never interfere with work profile apps.
    - Synchronous `AccessibilityEvent` field copying on Android 8–12 (API 26–32) to avoid recycled event errors.
  - `service/vpn/`: `VowVpnService.kt`, `PrivateDns.kt`, and `DnsPacket.kt`
    - Pure local loopback VPN DNS sinkhole returning NXDOMAIN for blocked domains.
    - Enforces Private DNS lock without proxying non-DNS payload traffic.
  - `service/admin/`: `VowDeviceAdminReceiver.kt`
    - Declares `device_admin_policies.xml` containing required `<force-lock />` policy.
  - `service/blocking/`: `BlockPolicy.kt`, `UsageTracker.kt`
  - `focus/`: `FocusActivity.kt`, `FocusLogic.kt`, `FocusSnapshotLoader.kt`
  - `ui/components/SystemScreens.kt`: Safe wrapped intent routing for OEM ROMs (Samsung OneUI / Android 16) with user guidance when system intents fail.
  - `ui/screens/bypass/BreathingScreen.kt`: 90-second haptic-guided breathing exercise required before unlocking temporary allowances.
  - `ipc/`: `LumeBus.kt`, `VowStatusProvider.kt`, `SleepSyncReceiver.kt` (inter-app ecosystem communication).

## Build, Test & Lint Commands (Quiet Mode for Token Saving)

Execute all commands from the `vow-android/` project folder:

```bash
cd vow-android

# Run unit & Robolectric tests
./gradlew testDebugUnitTest -q --console=plain --no-scan

# Run a single test class
./gradlew testDebugUnitTest --tests "com.vow.app.focus.FocusLogicTest" -q --console=plain --no-scan

# Build debug APK
./gradlew assembleDebug -q --console=plain --no-scan

# Lint check
./gradlew lintDebug -q --console=plain --no-scan
```

## Android & Cloud Container Engineering Rules (from innovator-repo/memory)

- **No Android Emulator in Cloud Containers**: Cloud containers lack KVM virtualization; do NOT attempt to launch emulators. Use Robolectric (+ Roborazzi where configured) for fast, headless UI and screenshot verification.
- **Compose Performance & Phases**: Defer rapidly changing State reads (such as scroll offsets and animation values) to the Layout or Draw phases using lambda modifiers (`Modifier.offset { ... }`, `Modifier.graphicsLayer { ... }`, `Modifier.drawBehind { ... }`) to avoid re-triggering composition. Never annotate mutable non-snapshot types with `@Immutable` or `@Stable`.
- **Platform Gotchas**:
  - **AccessibilityEvent recycling**: Copy `AccessibilityEvent` fields (`packageName`, `eventType`, `className`) synchronously on API 26–32 before crossing coroutine or async boundaries to avoid recycled event crashes.
  - **Device Admin Policy XML**: Android `<device-admin>` XML must declare at least one active policy (e.g., `<force-lock />`) to be valid on newer Android releases.
  - **System Settings Intents**: Always wrap system settings intents (`com.android.vpndialogs`, Private DNS settings, Device Admin enrollment) with fallback intents and written in-app user directions to prevent `ActivityNotFoundException` on heavily customized OEM ROMs (Samsung OneUI / Android 16).
- **APK Delivery Pattern**: When pushing debug APKs to an `apk/*` orphan branch, use a separate git worktree (`/tmp/apkwt`) and `git add --force` to prevent binary bloat on source branches.

## Token Hygiene & Compaction Keep-List

- **Token Hygiene**: Never read `.gradle/`, `build/`, `captures/`, `*.apk`, `*.aab`, `*.keystore`, or `*.dex`. Use targeted `read_file` with specific line ranges.
- **Cross-Session Memory**: Long-term project memory and cross-session insights live in `innovator-repo/memory` (`MEMORY.md`).
- **Compaction Keep-List**: When running `/compact`, always preserve:
  1. Current task goal and acceptance criteria.
  2. Kotlin/XML files created or modified.
  3. Build, test, or crash stack traces and identified root causes.
  4. Architectural decisions and design trade-offs.
  5. Pending Gradle, test, or APK delivery commands.

