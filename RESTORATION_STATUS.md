# Restoration Status — 2026-10-26

**Resume from this handoff; do not restart completed work.** Detailed evidence/checklist: [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md). Anti-loop protocol: [VERIFICATION_ANTI_LOOP.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/VERIFICATION_ANTI_LOOP.md).

- Repo: agabeyayd-lgtm/MathMotorVideo-Restore
- Active branch: fix/editor-395-gate
- Draft repair PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed phone baseline: versionCode **394**, build **61** (app-v8.3-61)
- Latest successful gate: [#236 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40466133767), commit `a38415c360d54f5a0645ac24d7a6b03dbbe21cea`. All 15/15 Android tests passed (XML: `failures=0 errors=0 skipped=0`). Measured A/V sync from the exported MP4: video boundary at 2,000 ms; first qualifying 100 ms 997 Hz audio window at 1,925 ms; delta 75 ms ≤150 ms. Tone absent before boundary (0.0), present after (8177.09); original fixture size/hash unchanged. Text seek/zoom remains green from #234 (14/14): 4.2 s→7.1 s→4.2 s after 623% zoom, preview hidden/restored and 32 sp / 1–6 s timing preserved. Source-audio gain/mute remains green from #228 (40% ratio 0.3990434; mute ratio 0.0000145058; source unchanged).
- [#216 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40372603373) already verified looped music and the configured timed-effect window in actual MP4/AAC; do not repeat either successful audio gate unchanged.
- Historical preflight loop closed: #226's FFmpeg package download timeout was fixed in workflow commit 7560ce792288ece7d2b3ca2116502589a68a24f1; #227 then isolated the second-launch reused-task issue, fixed in test-only commit 5380eef, which #228 verified.
- Text seek/zoom gate is now closed by [#234 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40462756764): 14/14 instrumentation tests passed; the target test verified 4.2 s → 7.1 s → 4.2 s after zoom and retained text/settings.
- **Current unique gate: [#236 RUNNING](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40466133767), head SHA `a38415c360d54f5a0645ac24d7a6b03dbbe21cea` — measured A/V sync.** The new deterministic source has a red→lime video boundary at 2,000 ms and a 997 Hz audio tone onset at that same timestamp. The test exports through the editor, checks decoded frame colors and decoded AAC tone onset, requires a difference ≤150 ms, checks silence before/tone after the boundary and verifies the original source hash/size. #236 includes a fixture-prep ordering correction (generate the file before listing fixture sizes); this is the authoritative run. Keep release 395 blocked until this and all later release gates pass.
- Remaining after #237: trim-vs-scroll and multi-clip edge cases; Undo/Redo coverage for trim/split/reorder and sound-effect changes; physical-device review; signed update/install.
- Historical next-step list from #229 (superseded): measured A/V synchronization is now green in #236; split/export is being tested in #237. Still open: trim/scroll multi-clip cases, Undo/Redo grouping, physical-device usability and signed update/install.
- **VersionCode 395 remains blocked.** No version bump, signing, publication or in-app update activation until required gates pass and release is explicitly authorized.


### 2026-10-27 — seek/zoom test compile failures and correction

- [#231 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40456248654): `UiObject2.setProgress` unresolved; Android instrumentation did not run.
- [#232 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40457727313): legacy `UiObject.setProgress` was also unresolved; again, the failure occurred during test compilation before target assertions.
- [#234 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40462756764), commit [`d202a62`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/d202a621db413cad76f0c302067fde0f4a761a29): test-only SeekBar drag helper uses current displayed project time and measured time delta, avoiding unavailable progress APIs and tap failures. Artifact confirms 14 tests, 0 failures. Target test passed the seek/zoom, preview hidden/restore and unchanged settings assertions.


### 2026-10-27 — #233 target seek assertion and #234 fix

- [#233 FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40458467025): unit tests and Debug APK passed. The Android suite ran 14 tests; 13 passed. The new text seek/zoom test failed at its own seek helper: after timeline zoom reached 623%, seeking to 7100 ms read 7800 ms despite six taps. The failure is in test interaction/mapping; subsequent preview/settings checks did not run. Artifact log confirms tap attempts at x=197 then x=190 without any playhead movement.
- [#234 QUEUED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40462756764), commit [`d202a62`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/d202a621db413cad76f0c302067fde0f4a761a29): use drag instead of taps; calculate start X from displayed actual time, apply the measured time delta as drag distance, and use the compact emulator thumb inset. Do not relax the ±150 ms assertion or preview/settings checks; wait for this exact target test to pass.


### Historical next-focused task — measured A/V synchronization (completed in #236)

Do not repeat completed text seek/zoom, source-audio gain/mute, or music/effect tone-window gates unchanged. Next create a deterministic audio/video fixture with identifiable video-frame boundaries and a synchronized audio impulse/tone, export through the editor, and measure decoded MP4 audio timing against decoded frame timestamps. Record an explicit tolerance and source-preservation check. Release 395 remains blocked until required gates pass and release is authorized.


### 2026-10-27 — #236 measured A/V synchronization gate (initially queued, now passed)

- Added a new deterministic four-second MP4 fixture: red video up to 2,000 ms, lime video after 2,000 ms, and a 997 Hz audio tone delayed to start at 2,000 ms. Workflow runs ffprobe to confirm both streams.
- The new instrumentation test exports the fixture using a seeded editor draft, detects the first decoded lime frame and the first 100 ms decoded-tone window above threshold, asserts the two estimates differ by no more than 150 ms, checks no measurable tone in the pre-boundary window and tone after the boundary, and confirms source fixture size/hash remain unchanged.
- [#235](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40466066055) was triggered before an ordering issue in the workflow's fixture inventory was noticed. [#236](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40466133767), commit `a38415c360d54f5a0645ac24d7a6b03dbbe21cea`, moves fixture generation before file-size validation and is the authoritative verification. Do not mark A/V sync complete until its specific instrumentation assertion passes.


### 2026-10-27 — #236 A/V synchronization passed; next split/export

- [#236 SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40466133767): 15/15 instrumentation tests; no failures/errors/skips. The output MP4 produced a red→lime frame boundary at 2,000 ms and a 997 Hz tone onset window at 1,925 ms, a 75 ms difference against the 150 ms threshold. Pre-boundary magnitude was 0.0, post-boundary magnitude 8177.09; original file hash and size were unchanged.
- Next test should use the same deterministic fixture to split at 2,000 ms through the actual UI, confirm two ordered clip tiles and preserve split segments in saved draft/export. Check exported duration around 4 seconds and decode video/audio around the seam. Do not repeat #234 text seek/zoom or #236 single-clip A/V sync unchanged.


### 2026-10-27 — #237 split-at-playhead/export gate running

- [#237 RUNNING](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40487968674), commit [`ff379f3`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/ff379f3eb3357f9ae1381e28f712f2319a0cb5a4): adds `splitAtPlayheadExportsTwoOrderedSegmentsWithoutMovingAvSeam`. It restores a 4 s source with a red→lime video boundary and 997 Hz tone both at 2,000 ms; seeks to the boundary through the actual timeline, taps the UI split action, asserts clip tiles 1 and 2 and two 2.00 s segments, exports the resulting two-clip project, measures duration/color/audio onset near the seam, and checks the source hash and size. Unit test/build/emulator assertions remain to be confirmed. Do not call the gate passed until the target instrumentation test succeeds.
