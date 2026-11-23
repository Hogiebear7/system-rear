# System Rear

A Solo-Levelling-style "System" panel for the **Xiaomi 18 Pro Max** rear screen (also 17 Pro / Pro Max, 18 Pro).

## On the back screen (swipe between pages, or tap the tabs)
- **STATUS** – level, rank (E→S), XP bar, stats (STR/AGI/VIT/END/SEN) with points to spend, streak, and your companion character.
- **QUESTS** – daily quests (push-ups, sit-ups, squats, steps by default). Tap + to log reps; steps fill in automatically. Clear them all for bonus XP and a streak; miss a day and the streak resets.
- **MSGS** – incoming messages pop up as banners. Open one to reply with quick replies, speech-to-text or the keyboard, or dismiss it. Works with WhatsApp, SMS and any app with a reply button.
- **CALL** – your starred contacts; tap, confirm, call.
- **REC** – one-tap voice recorder (saved to `Recordings/SystemRear`).
- **CLASS** – today's class workout from S&C Performance Coaching; "Done" gives XP.

Level-ups, rank-ups and quest completions show System pop-ups with a buzz.

## Install
1. Install and start **Shizuku** (Wireless debugging → Start; again after each reboot).
2. Install `SystemRear.apk` from the latest release.
3. Open System Rear on the front: allow Shizuku, tap **Grant everything with Shizuku**, then **Show on back screen**.
4. Leave "Show System whenever the back screen wakes" on so it returns after Xiaomi's clock takes over.

## How it works
HyperOS lets an activity start on the rear display (display 1) only if it declares the meta-data
`miui.rear.policy = 1`. The panel is such an activity; Shizuku's shell (`am start --display 1`) relaunches it
whenever the rear screen wakes.

## Credits
Rear-display approach informed by these MIT-licensed projects:
- hyperos-neon-pick-tuner (AndreySkyFoxSidorov) – the `miui.rear.policy` finding on an 18 Pro Max
- mi-back-screen (Copyright (c) 2026 Andrew Worgan) – Shizuku command runner pattern
- Magic Rear / ai-usage-companion (896kb) – the original idea

Not affiliated with Xiaomi. A HyperOS update may break the rear-screen part.
