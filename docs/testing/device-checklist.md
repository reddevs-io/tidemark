# Manual device checklist

Run this checklist for T-010 (integration and device verification) and before every release that changes behaviour. It comes from section 6 of the v1 plan. Milestone M3 needs every scenario to pass on API 28 and API 36.

## How to fill it in

- Run each scenario on both devices. Write **Pass**, **Fail** or **N/A** in the Result column. N/A needs a reason in Notes.
- Put the device model or emulator image, the Android version with its API level, and the Health Connect version on each row. The Health Connect version is in Health Connect's settings, or in the Play Store page on Android 9 to 13.
- For a failure, write what happened in Notes and file it in the Memory Bank as an issue with the scenario number.
- Commit the filled-in copy with the run date in the run details below.

## Before you start

- API 28: a Google Play system image or a real device, with Health Connect installed from Play. A plain AOSP image can't run Health Connect.
- API 36: any Google APIs image. Health Connect is built in.
- Seed data with Google's Health Connect Toolbox, plus at least one more writer app for the same metric (for example Google Fit, or a second Toolbox-written origin), so scenarios 7 and 8 have two sources.
- For scenario 6, write at least a year of steps and sleep data.

## Run details

| Field | API 28 run | API 36 run |
| --- | --- | --- |
| Date | | |
| Tester | | |
| Tidemark version and build type | | |
| Commit | | |
| Device or emulator image | | |
| Android version (API level) | | |
| Health Connect version | | |
| Writer apps used | | |

## 1. API 28, Health Connect not installed

**Expected:** Install prompt opens Play. After installing and returning, onboarding moves on.

API 28 only. On API 34 and up Health Connect is part of the system, so mark N/A.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 2. Health Connect needs an update

**Expected:** Update prompt is shown.

Needs an outdated Health Connect app (API 28 to 33). Mark N/A if you can't get one.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 3. Deny all permissions twice

**Expected:** Explanation plus a working "open Health Connect settings" button.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 4. Grant only steps and sleep

**Expected:** Those tiles load. The others show "permission missing" and the explorer marks those types locked.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 5. History feature unavailable, or history permission denied

**Expected:** Banner with the 30-day explanation and date. Older data is absent, and nothing crashes.

Note which case you tested: feature unavailable or permission denied.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 6. History granted, a year of data

**Expected:** Year charts show the full year. Year view loads in under 2 seconds.

Record the year view load time for steps and for sleep in the notes. Over 2 seconds on a mid-range device or emulator: file an issue in the Memory Bank.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 7. Two apps write steps for the same hours

**Expected:** The dashboard total follows the priority order set in Health Connect, with no double count. Changing the priority changes the total. The explorer lists both sources and filters by each.

Name both writer apps in the notes.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 8. Sleep with stages from one app, no stages from another

**Expected:** Last night picks per the rule and names the source. The hypnogram draws every stage.

Rule: the longest session ending between 12:00 yesterday and now.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 9. Health Connect's "app permissions" screen, privacy link (API 34+), and the rationale link (API 28 to 33)

**Expected:** Each opens Tidemark's privacy policy, cold start included.

For the cold start, force-stop Tidemark first.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 10. Device in French, region France. Then English, region US

**Expected:** All text is translated, with metric units in France and imperial in the US. The override in settings works both ways.

Health Connect is called Connexion Santé in French. Check that name everywhere.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 11. Revoke a permission in Health Connect while Tidemark is in the background

**Expected:** On return, the affected tile shows "permission missing" without crashing.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 12. Dark theme, dynamic color, 200% font size, TalkBack on the dashboard

**Expected:** Readable, nothing clipped, tiles announce value and unit.

Dynamic color needs API 31 or later. On API 28, check the static palette.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## 13. Release build (R8)

**Expected:** Every screen works, with no missing-class crash.

Use the minified release APK, not the debug build.

| Run | Device | Android version | Health Connect version | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| API 28 | | | | | |
| API 36 | | | | | |

## Sign-off

| Run | All scenarios pass or N/A with a reason | Open issues blocking release | Signed off by |
| --- | --- | --- | --- |
| API 28 | | | |
| API 36 | | | |
