# MathMotor Video

MathMotor Video is a local-first Android video editor with a CapCut-inspired workflow. Normal editing runs on-device; AI generation, AI scoring and paid cloud APIs are outside the current product scope.

## Focused timeline APK build — 2026-11-01

- PR #12 candidate branch: `fix/timeline-filmstrip-thumbnail-separators`.
- The focused change removes the per-thumbnail right margin, keeps the `00:00` zero anchor fixed, and keeps the video-add `＋` action in the fixed timeline header.
- Android build/unit-test workflow passed before APK packaging; that workflow did not upload an APK artifact.
- The APK packaging workflow is enabled for pushes to this candidate branch so the debug APK can be built and uploaded as a workflow artifact.
- This is a debug verification build only. It is not version 395, a signed release, or physical-device verification.
