# LifeLog · Daily To-Do

A minimalist, dark-mode personal to-do book: **what to do today, what you did on any past day, what's planned for any future day** — all in one view.

No account, no server, no tracking. Your data stays on your phone.

![icon](docs/icon.png)

**Download APK**: [GitHub Releases · v2.2](https://github.com/Mike486-ling/lifelog-todo/releases/latest)　·　[CDN mirror](https://cdn.jsdelivr.net/gh/Mike486-ling/lifelog-todo@v2.2/docs/download/LifeLog-2.2.apk)

[中文说明](README.md)

![screenshot](docs/screenshots/home.jpg)　![stats](docs/screenshots/stats.jpg)

![weekly heat map](docs/screenshots/week.jpg)　![search](docs/screenshots/search.jpg)

![light theme](docs/screenshots/light.jpg)　![drag to reorder](docs/screenshots/drag.jpg)

![inline subtasks](docs/screenshots/subtasks.jpg)　![first-run guide](docs/screenshots/guide.jpg)

## Features

| Feature | Detail |
|---|---|
| **Repeating tasks** | Daily / chosen weekdays / a day each month; completing one spawns the next, un-completing removes it. |
| **Subtasks** | Break a task into steps — they render right on the home list, tick them inline; the 2/3 progress chip expands/collapses. |
| **First-run guide** | On first launch, the 5 permissions that matter (notifications, exact alarm, autostart, battery, background pop-up) with a direct jump to each setting. |
| **Quick add** | Type "明天8点 跑步 30分钟" and the date, time and category are filled in for you; the title keeps only the text. |
| **Search** | Search titles and notes across every date; tap a result to jump to that day and tick it off right there. |
| **Undo delete** | Deleted tasks leave a 5-second "Undo" bar at the bottom. |
| **Themes** | Dark / Light / Follow system, switched from the Data menu. |
| **Date strip** | Horizontally scrollable date bar at the top. Today / tomorrow / the day after are labelled; scroll freely into past and future. Days with tasks show a blue dot. |
| **Quick jump** | Tap "Jump to date" to pick any year-month-day; the strip scrolls there. Shortcuts: 1 year ago, 30 days ago, 7 days ago, today, +7 days, +30 days. |
| **Three tracks** | Study / Life / Sport, each with its own task list. |
| **Long-term ideas** | A dedicated board for "maybe someday" ideas, with a live "37 days 5 hours left" countdown. |
| **Second-level alarms** | Reminders are accurate to the second and fire a **real system alarm**: wakes the screen, shows over the lock screen, loops the alarm ringtone (plays even in silent mode), vibrates, and keeps ringing until dismissed. |
| **Snooze** | "Remind me in 5 / 10 / 30 minutes" right from the alarm screen. |
| **Early reminder** | Each task can ring 1 / 5 / 10 / 30 minutes before its time. |
| **Overdue never lost** | Unfinished tasks from yesterday roll to the top of today, marked in red with "N days overdue". |
| **Survives reboot** | All alarms are re-registered after a reboot. |
| **Drag to reorder** | Long-press 0.6 s to lift a row; it follows your finger while neighbours slide out of the way, auto-scrolls near the screen edges, and saves on release. |
| **Priority dots** | A dot in front of each open task shifts red / amber / green based on how many tasks are left today; you can also pin a colour manually. |
| **Yearly stats** | Total completed, active days, longest / current streak, category split and a full-year heat map (by month or GitHub-style by week); tap a day to jump to it. |
| **Calendar export** | Export reminders as `.ics` from the Data menu for the system calendar — useful for people who only use the web version. |
| **Auto backup** | A JSON copy is written to `Downloads/今日待办备份/` every week; manual backup in the Data menu. |
| **Notification progress** | The status bar shows "4/8 done · 4 left" for today; tap to jump back into the app. |
| **Backup** | One-tap JSON export / import. |

## Two ways to use it

### 1. Web version (no install)

Open `web/app.html` in any browser. "Add to home screen" makes it feel like an app.

> Limitation: browsers usually block notifications for `file://` pages, so the web version relies on exporting `.ics` to the system calendar to get background reminders.

### 2. Android app (recommended)

Install the APK for real system-level alarm reminders. The app is a **native shell + WebView**: the UI is still that single HTML file, but reminders go through `AlarmManager` exact alarms, so they work with the app closed and even after a reboot.

Build it yourself (no Gradle required):

```bash
cd android
./build.sh   # needs JDK 17, aapt, zipalign, apksigner, android.jar (API 33), d8
```

Output: `android/build/lifelog.apk`. This hand-rolled build pipeline works on arm64 Linux.

## How it works

- **UI**: one HTML file + vanilla JS. No framework, no CDN, no network dependency, everything inlined (~40 KB).
- **Storage**: `localStorage` (private app directory on Android), with JSON backup.
- **Reminders**:
  - Web → foreground `Notification` + in-page timer, or `.ics` export
  - App → `AlarmManager.setExactAndAllowWhileIdle` + full-screen intent + `MediaPlayer(USAGE_ALARM)` looping ringtone
- **Native bridge**: `addJavascriptInterface` exposes `sync()` / `notifyNow()` / `askPermission()`; the page switches to native mode automatically when `AndroidHost` exists.

## Known limitations

- The ringtone starts inside the broadcast receiver to dodge MIUI's background-activity block; in aggressive battery-saving modes it can still be delayed.
- Required permissions: notifications, exact alarms (Android 12+), and it's recommended to enable autostart + "no restrictions" battery policy.
- The APK is self-signed, so Android will warn about installing from an unknown source.
- Uninstalling clears data; upgrade by installing over the old version.

## License

MIT
