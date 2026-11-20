# Manual device checklist

Run this checklist before every release that changes behaviour. Every scenario must pass on API 28 and API 36.

## How to fill it in

- Run each scenario on both devices. Write **Pass**, **Fail** or **N/A** in the Result column. N/A needs a reason in Notes.
- Put the device model or emulator image, the Android version with its API level, and the Health Connect version on each row. The Health Connect version is in Health Connect's settings, or in the Play Store page on Android 9 to 13.
- For a failure, write what happened in Notes and open a GitHub issue with the scenario number.
- Commit the filled-in copy with the run date in the run details below.

## Before you start

- API 28: a Google Play system image or a real device, with Health Connect installed from Play. A plain AOSP image can't run Health Connect.
- API 36: any Google APIs image. Health Connect is built in.
- Seed data with Google's Health Connect Toolbox, plus at least one more writer app for the same metric (for example Google Fit, or a second Toolbox-written origin), so scenarios 7 and 8 have two sources.
- For scenario 6, write at least a year of steps and sleep data.

## Run details

| Field | API 28 run | API 36 run |
| --- | --- | --- |
| Date | 2026-11-19 | 2026-11-19 |
| Tester | Claude Code (primary session) | Claude Code (primary session) |
| Tidemark version and build type | 0.1.0, debug and signed release | 0.1.0, debug and signed release |
| Commit | da21e9b plus the layout fixes in the next commit | cc817d4 to da21e9b |
| Device or emulator image | Emulator, Google Play x86_64 image | Emulator, Google APIs x86_64 image |
| Android version (API level) | Android 9 (API 28) | Android 16 (API 36) |
| Health Connect version | 2026.10.01.00.release | Built in (system module) |
| Writer apps used | Seeder Watch and Seeder Phone (throwaway seeder, two origins) | Seeder Watch and Seeder Phone (throwaway seeder, two origins) |

## 1. API 28, Health Connect not installed

**Expected:** Install prompt opens Play. After installing and returning, onboarding moves on.

API 28 only. On API 34 and up Health Connect is part of the system, so mark N/A.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Before the fix the prompt said "Update" because the SDK reports update-required for a missing provider; fixed in da21e9b. Install opens Play; after installing, onboarding moved on to the permission step on return. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | N/A | Health Connect is part of the system on Android 14 and up. |

## 2. Health Connect needs an update

**Expected:** Update prompt is shown.

Needs an outdated Health Connect app (API 28 to 33). Mark N/A if you can't get one.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | N/A | Play only offers the current Health Connect build, so no outdated version was available. Covered by HealthConnectHealthRepositoryTest. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | N/A | Health Connect is part of the system on Android 14 and up. |

## 3. Deny all permissions twice

**Expected:** Explanation plus a working "open Health Connect settings" button.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | After two refusals the dialog no longer appears. The explanation, Try again and Open Health Connect settings (Health Connect home) work. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Same. The settings button first opened the data browser, a dead end for permissions; it now opens Health Connect home (da21e9b). |

## 4. Grant only steps and sleep

**Expected:** Those tiles load. The others show "permission missing" and the explorer marks those types locked.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Steps and sleep load; the other four tiles show the no-access state with a settings button. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Same. Once, right after the first grant, Steps showed "No data today"; not reproducible, likely because the seeders got permissions through adb. |

## 5. History feature unavailable, or history permission denied

**Expected:** Banner with the 30-day explanation and date. Older data is absent, and nothing crashes.

Note which case you tested: feature unavailable or permission denied.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Banner dated Oct 20 with a settings button. Steps year view shows only October and November. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Banner dated Oct 20 with a settings button. |

## 6. History granted, a year of data

**Expected:** Year charts show the full year. Year view loads in under 2 seconds.

Record the year view load time for steps and for sleep in the notes. Over 2 seconds on a mid-range device or emulator: open a GitHub issue.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Year view loads in under 0.5 s (steps) and 0.7 s (sleep). |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Year view loads in under 0.4 s for steps and sleep. All months shown. |

## 7. Two apps write steps for the same hours

**Expected:** The dashboard total follows the priority order set in Health Connect, with no double count. Changing the priority changes the total. The explorer lists both sources and filters by each.

Name both writer apps in the notes.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | No double count: the total matches Seeder Watch alone. This Health Connect version has no priority setting, so the priority change could not be tested. The explorer lists and filters both sources. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Dashboard total equals Health Connect's total and follows the priority order (3,727 with Watch first, 2,831 with Phone first). The explorer lists and filters both sources. |

## 8. Sleep with stages from one app, no stages from another

**Expected:** Last night picks per the rule and names the source. The hypnogram draws every stage.

Rule: the longest session ending between 12:00 yesterday and now.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Longest night wins and its source is named; the hypnogram draws every stage and the gaps. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Same. Drill-down from the hypnogram lists both nights for that morning. |

## 9. Health Connect's "app permissions" screen, privacy link (API 34+), and the rationale link (API 28 to 33)

**Expected:** Each opens Tidemark's privacy policy, cold start included.

For the cold start, force-stop Tidemark first.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Privacy link in the permission dialog and in Tidemark's app page in Health Connect both open the policy, including with Tidemark force-stopped. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Health Connect's "Read privacy policy" link opens the policy, including with Tidemark force-stopped. |

## 10. Device in French, region France. Then English, region US

**Expected:** All text is translated, with metric units in France and imperial in the US. The override in settings works both ways.

Health Connect is called Connexion Santé in French. Check that name everywhere.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | System language French (France): all text translated, metric, French dates and numbers, week starts Monday. English (US): imperial. The override works both ways. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Per-app language fr-FR and en-US. Translations, units and formats correct; the override works both ways. |

## 11. Revoke a permission in Health Connect while Tidemark is in the background

**Expected:** On return, the affected tile shows "permission missing" without crashing.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Steps revoked while in the background: the tile shows the no-access state on return, no crash. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Same, and the value comes back after granting again. |

## 12. Dark theme, dynamic color, 200% font size, TalkBack on the dashboard

**Expected:** Readable, nothing clipped, tiles announce value and unit.

Dynamic color needs API 31 or later. On API 28, check the static palette.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | 200% font: no clipping after fixing the tile button shape and the top bar height. No system dark mode on Android 9. TalkBack not installed; tiles expose value and unit as content descriptions. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Dark theme with dynamic color and 200% font: readable, nothing clipped. TalkBack not installed on the image; content descriptions checked. |

## 13. Release build (R8)

**Expected:** Every screen works, with no missing-class crash.

Use the minified release APK, not the debug build.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | Emulator, Google Play x86_64 image | Android 9 (API 28) | 2026.10.01.00.release | Pass | Signed release (throwaway key): onboarding, dashboard, three detail screens in every range, explorer and a record detail. No crash. |
| API 36 | Emulator, Google APIs x86_64 image | Android 16 (API 36) | Built in (system module) | Pass | Signed release (throwaway key): every metric in every range, all eight record types and a record detail, settings, cold-start privacy policy. No crash. |

## Sign-off

| Run | All scenarios pass or N/A with a reason | Open issues blocking release | Signed off by |
| --- | --- | --- | --- |
| API 28 | Yes (item 2 N/A, priority change in item 7 not available) | None | Pending the user |
| API 36 | Yes (items 1 and 2 N/A) | None | Pending the user |
