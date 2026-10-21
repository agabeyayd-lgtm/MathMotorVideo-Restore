# MathMotorVideo restoration status

## What was found

- `MathMotorVideo-V7-Build365.apk` (2,938,357 bytes): actual Android APK, not source code.
- `MathMotorVideo-V7-Build365.zip`: ZIP contains only `app-debug.apk`.
- `MathMotorVideo_android_prototype.zip`: early Android Studio prototype with `MainActivity.kt`, `World.kt`, `VideoRenderer.kt`, Gradle files and an Actions workflow.
- The public `MathMotorVideo-Dev.` repository currently exposes only a minimal README and has no usable application source tree.

The V7 APK's embedded class and string inventory indicates that it had more modules than the early prototype, including a scenario/director pipeline, a human-oriented video renderer, video history, viewer/judge logic, social public-data connectors and `PHYSX / FRAME TEST`. The original Kotlin source files were not found in the saved library or accessible repository.

## This restoration project

This is a clean source reconstruction, not a decompiled or byte-for-byte V7 recovery. It provides:

- Seeded, deterministic motion-world generation.
- A separate scene-director layer that reads but does not rewrite generated motion.
- A 300-frame physics/timeline self-test and JVM unit tests.
- A local 9:16 H.264 MP4 renderer and Android MediaStore output.
- A local video history and latest-video opening action.
- GitHub Actions workflow to run tests and build a debug APK.

## Not yet restored / verified

- The original V7 learning/connector modules for YouTube, TikTok and Instagram.
- Exact V7 UI, event-selection algorithms and original media/audio pipeline.
- Compilation and execution on a physical Android device; the included CI workflow must succeed before treating the APK as verified.
- This debug build is not signed with the old release key. To install over an existing release build, the same signing identity is required; otherwise uninstalling the old app may be necessary and can remove local app data.
