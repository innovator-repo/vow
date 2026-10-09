# Vow: psychology first

The enemy of a restriction app is not the phone. It is the user's own brain at
11:40 pm, bored, tired and negotiating. Every feature in Vow has to answer one
question: **how does this make the brain stop trying to remove the restriction?**

## Why people remove restrictions

| Mechanism | What happens | Evidence |
|---|---|---|
| Hot-cold empathy gap | When calm, you can't imagine how strong the urge will feel. When the urge is on, you can't remember why you cared. The cold self sets rules, the hot self deletes them. | Loewenstein |
| Hyperbolic discounting | Reward in 10 seconds beats reward in 10 days, even when the larger one is far better. | Laibson |
| Ego depletion / decision fatigue | Late in the day, the cost of resisting rises, so the easy path ("just turn it off once") wins. | Baumeister, contested but practical |
| Negotiation loop | If a door is *possibly* open, the brain spends effort probing it. A door that is visibly bricked stops being probed. | Gilbert & Ebert: people are happier with irreversible choices |
| Urge decay | A craving peaks and fades within about 15-25 minutes if not fed. | Marlatt, urge surfing |
| Boredom vacuum | Blocking leaves a void. The brain fills it by finding the exit or a substitute. | Behavioural substitution research |
| Reactance | Rules that feel imposed by someone else provoke resistance. Rules the person authored do not. | Brehm |

## Design principles derived from this

### 1. The cold self writes the contract, the hot self cannot amend it
Loosening a rule is **slow**. Tightening is **instant**.
- Tightening needs no delay.
- Loosening a non-lifetime rule starts a **cooling-off timer** (default 24 h,
  longer for stricter rules). Nothing is deleted when you ask. The request just
  matures. Urges decay in about 20 minutes, so a 24 h wait outlasts every urge.
- A request for loosening can only be *completed* in the daytime window
  (08:00-20:00), never at night, when depletion is highest.
- Cancelling a loosening request is always one tap.

### 2. Close the door once, ceremonially, then stop negotiating (Lifetime)
Lifetime is not a toggle. It is a one-time **sealing**. It is deliberately
heavy so that it is made in the cold state:
- A 3-step flow spread over time: choose, wait 24 h, confirm with a
  typed sentence.
- After sealing, there is **no UI path to undo it**, so there is nothing for
  the brain to probe. The settings entry simply shows a sealed state.
  The only remaining exit is a factory reset, which is the OS's job, not ours.
- Each restriction type is sealed independently (DNS, date/time, uninstall,
  short-video, adult sites, keywords, night mode).

### 3. Your own words, not someone else's
No motivational quotes. When you set a rule you write one line, *"why am I
doing this"*, and one **if-then plan** (implementation intention,
Gollwitzer): *"If I want to open Reels, I will ____."*
At the moment of attempted bypass, Vow shows **your own sentence and your own
plan**, in your own wording, with the time you wrote it. A message from
your calm self carries authority a generic quote cannot.

### 4. Make the cost of the break concrete and personal
Loss aversion: the pain of losing is about twice the pleasure of gaining.
- Vow keeps a local **ledger of reclaimed hours**, computed from your real
  baseline usage (first 7 days, measured not guessed) minus current usage.
- A bypass attempt shows what is lost in your own units: *"This resets
  the 3-day cooling-off and 41 h you got back"*. This is a number
  you earned, not a streak badge.
- A lapse never zeroes progress, because all-or-nothing collapse ("what-the-hell
  effect", Polivy) is what turns a slip into a relapse. Progress is a
  cumulative total, not a streak.

### 5. Friction that can't be rationalised
- Bypass requires a **slow, effortful, boring** action (a 90-second paced
  breathing hold that you cannot skip), not a clever puzzle. Puzzles become
  a game; boredom plus time lets the urge decay.
- No "are you sure?" dialog. Reflex-confirmed dialogs teach the brain to
  tap through. Waiting works.

### 6. Fill the void (substitution)
Blocking short video without a replacement fails. When a short-video feed is
dismissed, Vow shows a **single quiet screen**: one pre-chosen alternative the
user set when calm (book, call someone, walk). It's the if-then plan from
principle 3. It never shows a feed or list.

### 7. Night: remove decisions instead of adding willpower
After 22:00 the brain is least able to resist, so Vow takes away choices:
- Screen turns **greyscale-like** (reduced stimulation, lower reward value).
- Only an **essentials allowlist** opens: phone, messages, maps, alarm,
  banking OTP, chosen emergency apps. Everything else shows a calm
  "closed until 06:00" screen.
- The goal is not to scold. Opening the door is simply not available.
- Wind-down begins 30 minutes earlier with a gentle dim, not a hard cut
  (avoids reactance).

### 8. Authorship and autonomy (no outside people)
Everything is local and self-authored, with no accounts, accountability partners
or cloud. That removes the shame and reactance of being monitored, and it was
a requirement. The "other person" in the contract is your past self.

### 9. YouTube limit decided by the app, not the user
User-set limits get renegotiated. Vow computes the allowance itself:
- Baseline measured over 7 days.
- Daily allowance starts at the baseline minus a small cut and **declines
  slowly** (about 3% per week, floor 30 min) so that no single day feels like a
  loss (avoids reactance to a sudden wall).
- Allowance is lower on days following over-use and later in the evening.
- The user sees only a soft "time left" ring, never a setting.

## Mapping to features

| Principle | Feature |
|---|---|
| 1 | Cooling-off queue for loosening, daytime-only completion |
| 2 | Lifetime sealing per restriction type |
| 3 | Reason and if-then plan at setup, shown on bypass attempt |
| 4 | Reclaimed-hours ledger, cumulative not streak |
| 5 | Paced-breath hold instead of dialogs |
| 6 | Substitute screen after short-video block |
| 7 | Night mode with essentials allowlist |
| 9 | Auto-decaying YouTube allowance |

## How enforcement works on Android (the "no lock-out screen" approach)

Apps like AppBlock fight uninstall by covering Settings with a lock screen,
which feels hostile and triggers reactance. Vow instead asks the OS to remove
the option:

- **Tier A, Device Owner (strongest, one-time `adb` setup):**
  `setUninstallBlocked`, `DISALLOW_CONFIG_PRIVATE_DNS`,
  `DISALLOW_CONFIG_DATE_TIME` + `setAutoTimeRequired`, `DISALLOW_APPS_CONTROL`.
  The uninstall button, DNS and date/time rows are greyed out by the system
  itself, so there is nothing to block visually and no hostile screen.
- **Tier B, no adb (fallback):** Device Admin (uninstall requires deactivation
  first) + an Accessibility service that quietly steers away from the
  device-admin deactivate, uninstall, DNS and date/time pages with a calm
  redirect rather than a lock wall. This is weaker, and Vow tells the
  user so honestly.
- **Local website/keyword blocking:** an on-device `VpnService` sinkhole for DNS
  with a bundled adult-domain list (compact, offline) plus keyword matching on
  hostnames and, via Accessibility, on browser URL bars.
- **Short-video blocking without blocking the app:** Accessibility detects the
  Shorts, Reels and TikTok-style feed views and returns to the previous
  screen, leaving the rest of the app usable.
- **Usage limits and screen time:** `UsageStatsManager`.
- **Auto-activation:** time, weekdays, connected Wi-Fi SSID, location
  (geofence), all evaluated on-device.
- **Profiles:** reusable rule sets (Work, Study, Bedtime).

Scope note: "Usage limits (iOS)" in the brief is out of scope for this Android
app; the Android equivalent is implemented through `UsageStatsManager`.
