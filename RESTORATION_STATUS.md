# Restoration Status — 2026-10-26

**Resume from this handoff; do not restart completed work.** Detailed evidence/checklist: [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md). Anti-loop protocol: [VERIFICATION_ANTI_LOOP.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/VERIFICATION_ANTI_LOOP.md).

- Repo: agabeyayd-lgtm/MathMotorVideo-Restore
- Active branch: fix/editor-395-gate
- Draft repair PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed phone baseline: versionCode **394**, build **61** (app-v8.3-61)
- Latest successful gate: [#234 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40462756764), commit `d202a621db413cad76f0c302067fde0f4a761a29`. All 14/14 Android instrumentation tests passed (XML: `failures=0 errors=0 skipped=0`). Text seek/zoom is verified: playhead 4.2 s within the 1–6 s text interval; zoom 100%→623%; playhead outside at 7.1 s; back to 4.2 s; preview restored and text content, 32 sp size and timing remained intact. Earlier audio gate [#228](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40399875346) remains green: decoded AAC source baseline 16380.5592; kept early ratio 0.3990434; kept late magnitude 6533.6536; muted ratio 0.0000145058; `originalsUnchanged=true`.
- [#216 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40372603373) already verified looped music and the configured timed-effect window in actual MP4/AAC; do not repeat either successful audio gate unchanged.
- Historical preflight loop closed: #226's FFmpeg package download timeout was fixed in workflow commit 7560ce792288ece7d2b3ca2116502589a68a24f1; #227 then isolated the second-launch reused-task issue, fixed in test-only commit 5380eef, which #228 verified.
- Current unique gate: [#231 — IN PROGRESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40456248654), commit `7e050ee535bc025f5793fe69faaa30cfcccb4d71`. #230 fixed the FFmpeg preflight and ran all 14 tests; 13 passed. The sole failure was in the new text seek/zoom test: after pinch zoom, its coordinate-based seek helper requested 4200 ms but read 3330 ms because it estimated thumb position from outer bounds. Test-only correction now uses UiAutomator's accessible SeekBar progress action and asserts actual project time within 150 ms of target. Confirm this assertion and the remaining text preview/settings checks pass; do not treat #230 as an app defect or weaken assertions.
- Remaining after that: A/V synchronization against known frame boundaries; split-at-playhead/export boundary; trim-vs-scroll and multi-clip edge cases; Undo/Redo coverage for trim/split/reorder and sound-effect changes; physical-device review; signed update/install.
- After #229: measured A/V synchronization; split-at-playhead/export boundary; trim/scroll and multi-clip edge cases; Undo/Redo grouping; physical-device usability and signed update/install.
- **VersionCode 395 remains blocked.** No version bump, signing, publication or in-app update activation until required gates pass and release is explicitly authorized.


### 2026-10-27 — seek/zoom test compile failures and correction

- [#231 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40456248654): `UiObject2.setProgress` unresolved; Android instrumentation did not run.
- [#232 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40457727313): legacy `UiObject.setProgress` was also unresolved; again, the failure occurred during test compilation before target assertions.
- [#234 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40462756764), commit [`d202a62`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/d202a621db413cad76f0c302067fde0f4a761a29): test-only SeekBar drag helper uses current displayed project time and measured time delta, avoiding unavailable progress APIs and tap failures. Artifact confirms 14 tests, 0 failures. Target test passed the seek/zoom, preview hidden/restore and unchanged settings assertions.


### 2026-10-27 — #233 target seek assertion and #234 fix

- [#233 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40458467025): unit tests and Debug APK passed. The Android suite ran 14 tests; 13 passed. The new text seek/zoom test failed at its own seek helper: after timeline zoom reached 623%, seeking to 7100 ms read 7800 ms despite six taps. The failure is in test interaction/mapping; subsequent preview/settings checks did not run. Artifact log confirms tap attempts at x=197 then x=190 without any playhead movement.
- [#234 QUEUED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40462756764), commit [`d202a62`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/d202a621db413cad76f0c302067fde0f4a761a29): use drag instead of taps; calculate start X from displayed actual time, apply the measured time delta as drag distance, and use the compact emulator thumb inset. Do not relax the ±150 ms assertion or preview/settings checks; wait for this exact target test to pass.


### Next focused task — measured A/V synchronization

Do not repeat completed text seek/zoom, source-audio gain/mute, or music/effect tone-window gates unchanged. Next create a deterministic audio/video fixture with identifiable video-frame boundaries and a synchronized audio impulse/tone, export through the editor, and measure decoded MP4 audio timing against decoded frame timestamps. Record an explicit tolerance and source-preservation check. Release 395 remains blocked until required gates pass and release is authorized.
