# MathMotor Video — current project status

## Current direction

MathMotor Video is a local Android video editor with a CapCut-inspired workflow. Normal editing should work on-device without a cloud account or paid AI service.

## Version and release boundary

- Source: versionCode 393, versionName 8.27-timeline-text-controls.
- Latest published signed artifact verified from GitHub Releases: app-v8.3-60 (versionCode 393).
- The current source includes later timeline/editor changes that are not contained in that published APK.
- Do not change versionCode or publish a replacement APK until explicitly requested. The existing release and in-app update must not be described as containing un-published source commits.

## Current source functionality

- Home screen with a primary gallery-import action, an empty-editor action, recent outputs and local drafts.
- Video import, multi-clip timeline, clip reorder/copy/delete/split, source trim start/end, playhead seek and timeline zoom.
- Trim and playhead controls now share a millisecond mapping; legal trim bounds consider the opposing edge and source duration. Short clips auto-zoom to keep handles reachable.
- Video thumbnail extraction uses timestamped platform-frame APIs first, then Media3 for the same requested timestamp; it avoids an untimestamped first-frame fallback.
- Text layer timing, position, size, colour and bold settings; background music and one timed sound effect; original audio volume.
- MP4 export with source / 720p / 1080p options via Media3 Transformer.
- JSON project save/load and local draft autosave. Draft state includes playhead time, zoom and horizontal scroll; older JSON files without those fields remain supported.
- Responsive preview/timeline layout, reduced keyboard mode and compact dock.
- Emulator KVM preparation step is fixed and has passed in prior runs.

## Known gaps and verification still required

- The latest trim/time/auto-zoom changes must pass CI on the final commit before being considered verified.
- A physical phone test is still required to test both trim handles by touch, trim down to the minimum legal duration, drag them back outward, and compare source time, project time and exported duration.
- CI currently captures screens and checks app/sample-video launch; it does not fully exercise trim gestures or export an MP4 and reopen it.
- Export should be tested end-to-end with multiple clips, trim boundaries, timed text, music and sound effects. Verify the output file's duration, frame content and audio sync.
- Undo/redo currently records clip edits only; text and audio edits are not yet part of one global project history.
- Multiple text layers at the same position can overlap; preview/export placement parity needs explicit tests.
- The existing Android screenshot test selector was updated to match the current home screen, but the shell screenshot workflow does not itself guarantee that instrumentation test is executed.
- README/status documentation must stay distinct between source state and signed release state.

## Recovery background

Some older physics/scenario-related source and tests remain as reconstruction leftovers; they are outside the active video-editing workflow. The original complete V7 source tree was not recoverable.
