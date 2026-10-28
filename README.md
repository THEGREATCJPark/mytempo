<img src="docs/icon.png" width="96" alt="">

# mytempo

A metronome without ads.

Free. Take it, use it.

**[Download APK](https://github.com/THEGREATCJPark/mytempo/releases/latest/download/mytempo.apk)** · Android 8.0+

## What it does

- 10–400 BPM. Tap the number to type it.
- − / +  change by 1. Hold to change by 5.
- Tap tempo.
- Time signatures, subdivisions.
- Tap a beat to set accent, normal, or silent.
- Keeps playing with the screen off.
- Bluetooth pedal and keyboard support.
- Settings → Scales: 23 numeric formulas in suggested study order. Select one to show it on the main screen, or hide it.
- Settings → Available tensions: chord-function and chord-scale reference.

Scale formulas use major-scale degrees. Tensions depend on harmony, melody and voicing.

## What it doesn't

- No ads.
- No account.
- No tracking.
- No internet permission.

## Build

JDK 17+, Android SDK platform 35, build-tools 35.0.0.

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
bash build.sh
bash tests/run-audio-tests.sh
```


## Releases

The CI test-build artifact uses a temporary certificate. Official releases use the stable 1.2 signing certificate, verified before publication. The signed APK and checksum in `downloads/` are the release package. Maintainers build with the private original key, update that package and bump the manifest version together. Existing releases are never overwritten.
