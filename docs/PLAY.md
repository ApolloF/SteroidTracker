# Google Play release

What the `play` flavour needs before and at each Play release. Background and sources: the owner's notes `protocoltracker/protocoltracker-play.md` (policy inventory) and `protocoltracker-play-strategy.md` (naming, money). Not legal advice.

## 1. Owner decisions still open

| Decision | Default in the code | Where to change it |
|---|---|---|
| Launcher label and Play title | "ProtocolTracker" (foss stays "SteroidTracker") | `-Pplay.label=...`; comment in `app/src/play/res/values/strings.xml` |
| App id | `com.apollof.protocoltracker`, same as foss | `-Pplay.idSuffix=.play` (side-by-side installs; moving between builds then needs backup and restore) |
| Signing | CI signs the bundle with the release key | Play App Signing: upload the existing release key as the app signing key ("use my own key") if foss and Play users should be able to switch builds by update. With a Google-generated key they can't, and the release key becomes the upload key only |
| Presets left out | MK-677, nandrolone decanoate, oxymetholone, sublingual testosterone base | `PLAY_PRESET_IDS`, `core/domain/.../pk/PresetChannel.kt` |
| Pro tiers | 5 plan items; Levels: 14-day trial, then the last 14 days and one chart at a time; custom report range Pro | `PlayPolicy`, `core/domain/.../entitlement/Entitlement.kt` |
| Monetisation | `unlock` (one-time `pro_unlock`) | `-Pplay.monetization=none\|paid_listing\|unlock` |

## 2. One-time Play Console setup

1. Developer account. A personal account created after November 2023 must run a closed test with at least 12 testers for 14 days before it can apply for production.
2. Payments profile and merchant account (needs the KvK number and a business address); without it the in-app product can't be activated.
3. Create the app: default language English (US), app, free. Category **Medical**. Contact email me@apollof.nl.
4. In-app product: one-time product `pro_unlock`, €9.99, activated. Add licence testers (Setup › License testing).
5. Upload the first bundle to **Internal testing**, then **Closed testing** with the 12 testers.

## 3. App content (Policy › App content)

| Item | Answer |
|---|---|
| Privacy policy | Publish the text of `app/src/play/res/raw/privacy_policy.md` (the app shows the same text in Settings › Privacy policy) at a public, non-geofenced HTML URL. Use a neutral path: the foss policy URL contains a word the Play wording avoids |
| Ads | No ads |
| App access | All functionality is available without an account or login. Pro features open with the 14-day trial or the in-app purchase; no credentials needed |
| Content rating (IARC) | Reference/utility type. Violence, sexuality, language, gambling: no. Controlled substances: the app doesn't depict or encourage drug use; it logs medicines the user enters. No user interaction or sharing, no location sharing. Digital purchases: yes |
| Target audience | 18 and over only. Not designed for children |
| Data safety | Collects data: **No**. Shares data: **No**. What the user logs is processed only on the device. Payment data is collected by Google Play's billing system directly and isn't disclosed by the developer; the app keeps only a "Pro unlocked" flag on the phone. Encryption in transit: not applicable. Deletion: in the app and by uninstalling. Revisit this if any library that sends data is added |
| Health apps declaration | Medication and treatment management. Not a medical device. No Health Connect, no health-sensitive permissions |
| Financial features, government, news, COVID-19 | None / No |
| Exact alarms | Play builds don't declare `USE_EXACT_ALARM`; `SCHEDULE_EXACT_ALARM` needs no declaration. Without the grant, reminders fall back to a 10-minute window |
| Foreground service | No typed foreground-service permission, so no declaration (WorkManager's `FOREGROUND_SERVICE` is unused) |
| 16 KB page size | The only native libraries (AndroidX graphics-path and DataStore) have 16 KB-aligned segments (checked 2026-10-11). Recheck after adding a dependency with native code |

Permissions in the Play bundle (`PlayManifestTest`): `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `com.android.vending.BILLING`, and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

## 4. Store listing

**Title** (max. 30): `ProtocolTracker: Dose Log`

**Short description** (max. 80): `Private medication log with reminders, injection sites and lab results.`

**Full description:**

> A private log for medicines you take on a schedule: tablets, gels and injections.
>
> • Plan and Today: weekly, every few days, every few hours or as needed. One tap logs a dose; reminders and a home-screen widget show what is due.
> • Injection sites: rotation and history per medicine.
> • Journal: blood pressure, symptoms, mood, notes and lab results you enter, flagged only against the range your lab printed.
> • Estimated levels: model curves from published pharmacokinetic data for the doses you log. Estimates, not measurements.
> • Reports and backup: HTML and Markdown reports, and a backup file you keep.
>
> Your data stays on your phone. No account, no ads, no analytics. The app connects only to Google Play, for the optional Pro purchase.
>
> Used for example to keep track of testosterone therapy, menopause or gender-affirming hormone therapy, GLP-1 medicines and fertility treatment.
>
> Pro (one-time purchase, no subscription) adds more plan items, the full Levels history with several medicines on one chart, planned versus logged doses, the lab overlay and custom report ranges. Full Levels is free for 14 days after you first open it.
>
> This app is not a medical device and does not diagnose, treat, cure, or prevent any medical condition. It does not recommend or adjust doses. Always consult a healthcare professional for medical advice, diagnosis, or treatment.

Never use in listing text, screenshots or release notes: cycle, blast, cruise, PCT, SARM, research compound, "optimise", "dial in", "enhanced", any substance left out of the Play presets, grey-market peptide names, prices or sources. `PlayWording.BANNED_TOKENS` is the list the code checks.

**Website:** the privacy-policy page or a neutral project page, not the GitHub README. No donation links.

**Screenshots** (phone, at least 2; 1080 × 1920 or larger): Today, Plan, Levels with the "Estimated" label visible, Journal with a lab result, the injection-site sheet, Settings › Privacy policy. Demo data: testosterone cypionate weekly with anastrozole, estradiol valerate with oral progesterone, semaglutide weekly. Never hCG next to testosterone, no "Pre-workout" slot, no symptom sheet with the estrogen groups. Make them with the play debug build on an emulator, or with `ScreenshotTest` (`-Pscreenshots.dir`) in `testPlayDebugUnitTest`.

## 5. Each release

1. Bump `versionCode` (Play rejects a code it has seen) and `versionName`, add `docs/releases/v<version>.md`.
2. Before the tag: `connectedFossDebugAndroidTest connectedPlayDebugAndroidTest` on an emulator, the release build on a device, `adb logcat -b crash` empty.
3. Push the `v*` tag. The `release` job publishes the foss APK on GitHub and uploads the signed Play bundle as the `steroidtracker-play-bundle` workflow artifact.
4. Upload that `.aab` in Play Console to the testing track, with release notes in the Play wording (the GitHub notes may name foss-only presets; rewrite them).
5. Licence-tester checks on a real device: buy, pending purchase (slow test card), refund (Pro goes away on the next start), Restore purchase.
6. Promote to production once the closed test has run its 14 days.
