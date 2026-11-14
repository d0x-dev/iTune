# Contributing to iTune

Thanks for helping improve iTune. Keep changes focused, explain their user impact, and preserve existing behavior unless the change intentionally updates it.

## Before you start

- Search existing issues and pull requests to avoid duplicate work.
- For larger changes, open a discussion or issue first to agree on scope.
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md).
- Review [Security](SECURITY.md) before reporting or changing security-sensitive behavior.

## Development setup

1. Install Android Studio, JDK 21, and Android SDK Platform 37.1.
2. Clone this repository and open its root directory in Android Studio.
3. Let Gradle sync and install the requested SDK components.
4. Run the `app` configuration on an Android 7.0+ device or emulator.

Useful commands from the repository root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

On Windows, use `gradlew.bat` in place of `./gradlew`. Connected tests require an attached device or running emulator.

## Making a change

- Create a focused branch from the default branch.
- Keep UI, playback, audio, data, and platform work in their corresponding packages.
- Prefer small, reviewable commits with clear messages.
- Update README or feature documentation when user-visible behavior, setup, permissions, or limitations change.
- Keep credentials, user data, and private account material out of source, logs, screenshots, and test fixtures.
- Do not add dependencies without a clear need; consider APK size, maintenance, licenses, and Android compatibility.

## Pull requests

Include:

- What changed and why.
- How the change was checked (for example, the exact build/test command and result).
- Screenshots or a short recording for visible UI changes.
- Android API level and device details for platform-specific changes.
- Any permission, network, account, or data-migration impact.

Before submitting, run the relevant unit tests and `:app:assembleDebug`. If you cannot run a check, say so and explain why. Do not claim a check passed unless it did.

## Reporting defects

Use GitHub Issues for ordinary bugs and feature requests. Include app version, Android version, reproduction steps, expected and actual behavior, and sanitized logs. For security vulnerabilities, follow the private process in [`SECURITY.md`](SECURITY.md) instead of opening a public issue.

## License for contributions

By submitting a contribution, you agree that your contribution is made available under the repository's [MIT License](LICENSE). You retain copyright to your contribution.
