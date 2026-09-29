# Windows Android build environment

The local build uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Android API 35, and Build Tools 35.0.0. AGP 8.7 requires Gradle 8.9 and JDK 17; Kotlin 2.0.21 supports this toolchain and avoids relying on Kotlin 1.9's narrower language/plugin alignment.

Run `powershell -ExecutionPolicy Bypass -File .\tools\setup-android.ps1` from the project to download and unpack the pinned tools under `.toolchain`. Then run it with `-AcceptAndroidLicenses` to accept the Android SDK license prompts and install platform-tools, API 35, and Build Tools 35.0.0. That flag accepts the free Android SDK licenses required for local development. The setup never changes the machine's PATH or global environment; it scopes `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` to its own PowerShell process.

The Gradle archive is checked offline against Gradle's [official release-checksums page](https://gradle.org/release-checksums/): `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab` for Gradle 8.9. The Temurin JDK comes from [Adoptium's latest JDK 17 Windows x64 API](https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse); the command-line tools come from [Google's Android repository](https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip). Their SHA-256 digests are recorded locally without claiming independent upstream checksum verification. Downloads and extracted tools are gitignored.

Build/install commands from the project root after setup and license acceptance:

```powershell
.\tools\android-build.ps1
.\tools\package-apk.ps1 -SkipBuild
.\.toolchain\android-sdk\platform-tools\adb.exe devices
```

`adb devices` reports only devices currently visible to this machine; a successful toolchain setup does not imply a BOOX device is connected. To create an installable APK, build the `assembleDebug` variant; its APK is normally written to `app\build\outputs\apk\debug\app-debug.apk`.

Provisioned locally on 2026-09-28: Temurin JDK 17.0.20.1, Gradle 8.9, Android platform-tools 37.0.1, API 35 revision 2, and Build Tools 35.0.0. The command-line tools archive SHA-256 is `98b565cb657b012dae6794cefc0f66ae1efb4690c699b78a614b4a6a3505b003`; the Temurin archive SHA-256 is `e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0`. `adb devices` ran successfully and listed no connected devices.

AGP 8.7.3 subsequently installed its default Build Tools 34.0.0 during compilation; signature checks use the already-installed 35.0.0 apksigner. Gradle caches, Android user files and the pilot debug keystore live under `.toolchain`. The wrapper distribution has an official SHA-256 pin.

This host rejects Java AF_UNIX NIO pipe connections even when ordinary local TCP works. The build script selects the JDK's existing TCP fallback through a process-local unavailable Unix socket directory. `tools/LoopbackProbe.java` verified this before builds resumed. Use the build script on this host rather than invoking Gradle without its environment. See D013 in DECISIONS.md. Network dependencies and Gradle IPC may need execution approval from the host sandbox.

During initial provisioning, `sdkmanager --licenses` accepted all seven license records bundled by the Android command-line tools. That set included additional license records beyond the three requested packages. Future setup runs use package-scoped installation prompts instead of calling the broad `--licenses` command.
