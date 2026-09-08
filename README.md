# RemuTrack

RemuTrack is a phone-only Android app for tracking remuneration. The first built-in remuneration type is **Invigilation**. Entries retain the rate that was current when they were created, so changing the setting never changes historical totals.

## MVP features

- Dashboard with unpaid/paid counts, amounts, and current-month total.
- Add, edit, delete, search, multi-select, and mark invigilation sessions paid.
- Paid history can be returned to unpaid.
- Required date, series, year, and exam type validation; `Other` requires custom text.
- Local persistence using the Android app's private SharedPreferences storage.
- Domain models include a remuneration type discriminator for future categories.

## Build and install locally

This is a standard Gradle Android project. With Android SDK and Gradle available:

```powershell
gradle assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
```

The debug APK is produced at `app\build\outputs\apk\debug\app-debug.apk`. This environment did not include a Gradle executable or Android SDK, so the project could not be assembled in-place here; Android Studio is not required on a machine with the Android SDK and Gradle tooling installed.

## Build remotely with GitHub Actions

The workflow at `.github/workflows/android.yml` builds the debug APK on GitHub-hosted Ubuntu runners. It installs Java 17, Gradle 8.10, Android platform 35/build-tools 35.0.0, runs `testDebugUnitTest`, and then runs `assembleDebug`.

To trigger a build and download the APK:

1. Push this branch to GitHub and open the repository's **Actions** tab.
2. Select **Android APK**, click **Run workflow**, choose the branch, and click **Run workflow**. Pushes to `main` and this MVP branch also trigger it automatically.
3. Open the completed workflow run, scroll to **Artifacts**, and download `remutrack-debug-apk`.
4. Extract the downloaded ZIP to obtain `app-debug.apk`.
5. On an Android phone, enable installation from the browser/file manager when prompted, open the APK, and install it. The debug APK is unsigned for production distribution but is suitable for local testing.

The workflow requires GitHub Actions to be enabled for the repository. It does not publish to Google Play, and this repository currently has no Gradle wrapper, so the workflow provisions Gradle 8.10 directly.
