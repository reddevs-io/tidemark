# Architecture

Tidemark is one Android module with one activity and Compose screens. Every screen reads Health Connect when it opens, through a single repository interface, and keeps nothing but two settings on disk. This page explains the layers, follows one read from Health Connect to the screen, and gives the reasons for the main design choices.

## Layers and packages

All code is under `app/src/main/java/io/reddevs/tidemark/`.

| Package | Role |
| --- | --- |
| root | `TidemarkApplication` creates the `AppContainer`. `MainActivity` asks `OnboardingGate` for the first screen and hosts the NavHost. |
| `navigation` | `Routes.kt` declares the type-safe routes (`Onboarding`, `Dashboard`, `MetricDetail`, `Explorer`, `RecordDetail`, `Settings`, `PrivacyPolicy`). `TidemarkNavHost` maps each route to a screen. |
| `data` | `HealthRepository`, its only production implementation `HealthConnectHealthRepository`, error mapping (`HealthAccessException`, `healthCall`), aggregate metrics (`MetricAggregates`), chart windows and buckets (`ChartBuckets`, `SeriesPlan`), the sleep night rule (`SleepNights`) and `HealthConnectIntents` for opening Health Connect or Play. |
| `data.model` | Plain models: `RecordType` (the eight types and their read permissions), `Metric` (the six dashboard metrics), `Series`, `SleepNight`, `RecordPage` and others. Values are in SI units. |
| `format` | `MeasurementFormatter` and `LocaleMeasurementFormatter`: numbers, units, dates and durations for the current locale and unit system. |
| `settings` | `UserPreferences` and its DataStore implementation, the settings screen. |
| `onboarding` | First run, the permission request, the privacy policy screen and `PrivacyPolicyActivity`, which Health Connect opens to show the app's rationale. `onboarding.markdown` renders the bundled `PRIVACY.md`. |
| `dashboard.home` | Tiles with sparklines and the history banner. |
| `dashboard.detail` | Day, week, month and year charts, the heart rate day view and the sleep hypnogram. |
| `explorer` | Record type list, paged record list with date range and source filter, generic record detail. |
| `ui.components`, `ui.theme` | Shared composables (state views, card segments, app labels, chart helpers) and the theme. |

Feature packages depend on `data`, `format`, `settings` and `ui`. They never depend on each other, except through navigation routes.

Each feature follows the same split:

- A `*Route` composable takes the `AppContainer`, builds its ViewModel with `viewModel { ... }`, collects state with `collectAsStateWithLifecycle()` and handles navigation and intents.
- A `*ViewModel` takes the repository, preferences and a `Clock` as constructor arguments and exposes one `StateFlow` of a UI state class.
- A `*Screen` composable is stateless. It takes the state, the current formatter and callbacks, which keeps it previewable and testable.

## How a read reaches the screen

The steps tile on the dashboard shows the path every read takes.

1. `DashboardRoute` creates `DashboardViewModel(container.healthRepository, container.userPreferences, container.clock)`.
2. A `LifecycleResumeEffect` calls `viewModel.refresh()` every time the screen resumes. When the user comes back from Health Connect settings, changed permissions show up at once. There is no timer.
3. `refresh()` launches one coroutine per metric. For steps it calls `repository.todayTotal(Metric.STEPS, today)` and, in parallel, `repository.series(...)` for the seven-day sparkline.
4. `HealthConnectHealthRepository.todayTotal` turns the day into a local midnight-to-midnight span, picks the aggregate metric (`StepsRecord.COUNT_TOTAL`) from `MetricAggregates`, and calls `HealthConnectClient.aggregate` inside `healthCall(RecordType.STEPS) { ... }`.
5. `healthCall` maps every failure to a `HealthAccessException`. A `SecurityException` becomes `PermissionMissing(STEPS)`, a quota error becomes `RateLimited`, a remote or I/O error becomes `Unavailable`. Cancellation passes through untouched.
6. The ViewModel turns the result into `TileState.Value`, `TileState.NoData` or `TileState.Error` for that tile only, and combines all tiles, the history banner and the refresh flag into `DashboardUiState`.
7. `DashboardScreen` draws the state. It formats the number with the `MeasurementFormatter` from `container.formatter`, a `StateFlow` that is rebuilt when the locale or the unit preference changes.

The other screens follow the same path. The detail chart calls `series(metric, window)`. The explorer calls `dataOrigins` for its source chips and `readPage` for 100 records at a time, loading the next page when the list scrolls near its end. The record detail calls `readRecord`.

The client is created lazily, and only when `availability()` reports Health Connect as available. On Android 9 to 13 Health Connect is a separate Play app, so `availability()` also checks whether its package is installed and distinguishes "not installed" from "update required".

## Design choices

### Read-only

The manifest requests the eight read permissions and `READ_HEALTH_DATA_HISTORY`, nothing else. There is no write permission, no background read and no exercise route access. A viewer needs none of them, and each extra health permission is something users have to trust and Google Play has to approve against a visible feature. The record type set is the same in every build, so there is one manifest, one Play declaration and one privacy policy.

Without the history permission Health Connect returns only data from 30 days before the first grant, and older data is silently missing. The dashboard shows a banner with the earliest visible date, which is why `UserPreferences` stores the first grant time.

### No network

Privacy is the reason the app exists, and with no network there is nothing to collect or share, so the Play Data safety form stays empty. The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` in case a library merges them in, `ManifestContractTest` checks the merged manifest in unit tests, and `scripts/check-permissions.sh` checks the built release APK in CI. Links to the source code or to Play open other apps through intents.

### Live queries, no local cache

Every screen reads Health Connect directly when it resumes. There is no Room database and no copy of health data on disk. That keeps the code small, keeps the "nothing stored" promise easy to check, and means a revoked permission or a deleted record disappears on the next resume. Reads happen on resume and on pull to refresh, never on a timer, to stay under Health Connect's foreground rate limits. If year charts turn out too slow on real devices, a cache is the thing to revisit.

### Aggregates for charts

Totals, sparklines and charts use `aggregate`, `aggregateGroupByDuration` and `aggregateGroupByPeriod`. Health Connect deduplicates aggregates by the data source priority the user set in Health Connect, so two apps writing the same steps are not counted twice and Tidemark needs no source picker of its own. The bucket rules:

- Day view: hourly buckets, 23 to 25 of them across daylight saving changes.
- Week and month: daily buckets. Weeks start on the locale's first day of the week.
- Year: monthly points. Cumulative metrics and sleep show each month's daily average over the days with data, so the query asks for daily buckets and folds them into months.
- Sleep buckets run noon to noon and count towards the morning they end on.

Raw reads are used where an aggregate can't answer: last night's sleep with its stages, the latest weight and resting heart rate, heart rate samples for one day, and the explorer. `readRecords` does not apply the user's priority order, so `SleepNights` picks one night by a fixed rule: the longest session wins, then the one that ends later.

### One generic record renderer

The explorer has one detail screen for all eight record types. `RecordFieldExtractor` maps each record class to a list of `FieldSection`s (time, values, samples, stages, segments, laps, metadata), and `RecordDetailScreen` renders any list of sections. Each field carries its SDK property path as a key, and `RecordFieldExtractorTest` uses reflection to check that every public property of each record and its metadata appears. Supporting a new record type means one new branch in the extractor, not a new screen, and the explorer shows everything Health Connect stores instead of a curated subset.

### Manual dependency wiring

`AppContainer` is a plain class created once in `TidemarkApplication.onCreate`. It holds the repository, the preferences, the clock and the formatter flow. `MainActivity` reads it through `Context.appContainer` and passes it to every route. There is no Hilt, Dagger or Koin. The app is small, the build stays fast, and a contributor can read the whole object graph in one file. Tests pass fakes through the `AppContainer` constructor or build ViewModels directly.

### Strings per feature

Android merges every file in `values/`, so each feature keeps its own `strings_<feature>.xml` with a French twin in `values-fr/`. Two changes to different features rarely touch the same resource file.

## Tests

Everything runs on the JVM with `./gradlew testDebugUnitTest`. There are no instrumented tests; [testing/device-checklist.md](testing/device-checklist.md) covers manual checks on devices. Test packages mirror the main packages.

- Robolectric runs at SDK 34 (`app/src/test/resources/robolectric.properties`). Emulating SDK 35 and above needs JDK 21, and the build uses JDK 17. Don't set a higher SDK per test.
- Repository tests (`data/HealthConnectHealthRepositoryTest`) use `TestHealthConnectClient`, a wrapper around `FakeHealthConnectClient` from `connect-testing`. The wrapper adds what the fake lacks: permission checks, feature status, newest-first ordering and lookup of a record by id. The fake computes no aggregates, so tests stub them through `fake.overrides`.
- Pure logic has its own tests without Health Connect: `SeriesPlanTest` for buckets and daylight saving, `SleepNightsTest`, `MetricAggregatesTest`, `HealthErrorsTest`, the formatter and unit system tests.
- ViewModel tests use the shared fakes in `testing/`: `FakeHealthRepository` (enforces read permissions like Health Connect and counts calls), `FakeUserPreferences`, `MainDispatcherRule` and a fixed `Clock`. Wrap or subclass the shared fakes in your own test package rather than changing them for one test.
- Build records with `TestRecords.metadata(id, packageName)`, because the public `Metadata` constructor can't set an id or a data origin.
- Compose UI tests use the v2 rules, `androidx.compose.ui.test.junit4.v2.createComposeRule` and `createEmptyComposeRule`, on Robolectric. French layouts run with `@Config(qualifiers = "fr-rFR")`.
- Contract tests pin things other systems rely on. `ManifestContractTest` checks the permission set, `PrivacyRationaleManifestTest` checks the intents Health Connect uses to open the rationale, and `AppContainerTest` checks the wiring.
