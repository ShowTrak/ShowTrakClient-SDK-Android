# Developing the ShowTrak Android SDK

Needs JDK 17 and the Android SDK (Android Studio sets `local.properties`).

```sh
./gradlew spotlessApply                  # format Kotlin and Gradle scripts (ktlint)
./gradlew spotlessCheck                  # check formatting, as CI does
./gradlew :showtrak-sdk:lint             # Android Lint (fails on errors)
./gradlew :showtrak-sdk:assembleRelease  # build the AAR
```

The code style is ktlint's `android_studio` style, set in `.editorconfig`, so
Android Studio's formatter and ktlint agree. CI (`.github/workflows/ci.yml`)
runs the format check, Android Lint and the build on every push and pull
request.

Releases are published by `.github/workflows/publish-android-sdk.yml` when a
`v*.*.*` tag is pushed.
