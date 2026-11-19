# Data safety form answers

Answers for the Play Console Data safety form (App content › Data safety), with the reason for each. Check the current form wording when you submit, because Google changes the questions from time to time.

## Why the answer is "no data collected"

Play defines **collection** as transmitting data from the app off the user's device, and **sharing** as transferring data to a third party. Data that is processed only on the device does not count as collected, and it does not need to be declared.

Tidemark reads Health Connect data and shows it on screen. It cannot send that data anywhere:

- The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"`, so no library can merge them back in.
- CI fails the build if the release APK requests any permission outside the allowed list. See `scripts/check-permissions.sh`.
- There are no network, analytics, crash reporting or ads libraries.
- `android:allowBackup="false"`, and the data extraction rules exclude every domain from cloud backup and device transfer.
- On-device storage is limited to two settings: the unit choice and the time access was first granted. Neither is health data, and neither leaves the device.

## Data collection and security

- **Does your app collect or share any of the required user data types?** No.
  Reason: Tidemark has no network access, so no data leaves the device. Health Connect data is read and displayed on the device only.

When the answer is No, the form skips the encryption in transit and data deletion questions. For reference:

- **Is all of the user data collected by your app encrypted in transit?** Not applicable. Nothing is transmitted.
- **Do you provide a way for users to request that their data is deleted?** Not applicable. Tidemark holds no user data. Uninstalling it removes its two settings, and the user manages their health data in Health Connect.

## Data types

Leave every data type unchecked, including Health and fitness. Tidemark reads health and fitness data from Health Connect, but only processes it on the device, which Play does not count as collection.

## Other App content answers

These are on separate App content pages, listed here so the whole submission comes from one place.

- **Ads:** No, the app contains no ads.
- **App access:** All functionality is available without an account or login. The reviewer needs Health Connect with some data in it. Health Connect Toolbox can write test data.
- **Privacy policy:** [https://tidemark.reddevs.io/privacy/](https://tidemark.reddevs.io/privacy/). Check that it loads over HTTPS and matches the text bundled in the app.
- **Health apps:** see [health-declaration.md](health-declaration.md).
- **Government app, financial features, news app:** No.

## Store listing text

The listing text lives in `fastlane/metadata/android/en-US/` and `fastlane/metadata/android/fr-FR/`. The full description says that Tidemark has no network access and collects nothing, which matches these answers.

## Keeping this file true

If a change adds a permission, a library that talks to the network, or new on-device storage, update this file, `PRIVACY.md` and `PRIVACY.fr.md` in the same pull request, and update the Play form before the next Play release.
