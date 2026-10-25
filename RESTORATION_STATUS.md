# MathMotor Video — current handoff status

**Last known baseline:** versionCode 394 / build 61 (`app-v8.3-61`).  
**Active branch:** `fix/editor-395-gate`. **Draft PR:** [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8).  
**Release status:** blocked; no version bump, signed APK publication, or update offer without verified gates and explicit user authorization.

## Latest verified result

[Workflow #40199353737 (#152)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40199353737) passed on source/test commit `515df8035d31508acb03f5e9fe59cf0e78879508`: 6 instrumentation tests, 0 failures/errors/skips. The verified behaviors are the fixed-playhead scroll/pinch path, left/right trim and restoration, distinct filmstrip frames, text input after keyboard resize plus live text size/timing controls, two-clip export to one new 11,029 ms MP4 with a green second-clip frame at 9.5 s, and the home/editor/text-panel smoke capture. Both source video hashes remained unchanged.

The keyboard assertion is inside `textTimingAndSizeControlsUpdatePreview` and passed in run #152. It is no longer an open blocker for the tested emulator configuration. Two-clip import/export is also no longer open.

## New work in progress

The new `savedDraftReopensTextLayerAndClipState` check writes a draft through the real `VideoProjectDraftStore`, then checks restored trim duration, preview text, editable text value and 32 sp size. First attempt [#40201158067 (#154)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40201158067) failed only on this new test: the debug-only test bridge refused to start when it was given a valid draft ID without a new video URI, and the test waited for the wrong status text. Fixes are committed: [`e7820c1`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/e7820c1202a09f7bd0b1de6100c5e36bb73b4f0a) permits a valid draft ID as a launch target; [`288e21d`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/288e21da2a9971c89c9e9914eefa9c6fcfe50dec) waits for the actual “Taslak açıldı” message; [`147a0d5`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/147a0d57094330d4e13408ba6ae4e4162ced914d) adds `app/src/debug/**` to CI trigger paths. Intermediate run [#40202001975 (#155)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202001975) was cancelled by the subsequent push as configured by branch concurrency. The combined-state validation is [#40202008562 (#156)](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40202008562), currently running. **Do not mark draft restore passed until #156 succeeds.**

## Remaining real blockers

1. **Undo/Redo:** source history uses clip-only snapshots. Text and audio/music edits do not yet share a global history.
2. **Project round-trip:** local draft test above is pending CI; separately verify manual JSON Save As → close → Open with clips, trims, timeline position, text, music and sound settings restored.
3. **Text strips/export:** dragging text-strip edges has not been validated; output-vs-preview parity for text timing/placement/size/style is unverified.
4. **Audio/export:** original audio level, background music loop/volume, timed sound effect and final MP4 A/V sync lack end-to-end test evidence.
5. **Timeline edge cases:** split-at-playhead/export boundary, trim-versus-scroll conflicts and multi-clip editing at multiple zoom levels are still unverified. The previously passing single-clip start/end/zoom checks and two-clip export do not need to be rerun without a relevant code change.
6. **Physical phone:** emulator screenshots were reviewed, but thumbnail density, compact tap targets, keyboard/panel feel and overall layout still need a physical-device check.
7. **Release/install:** signed update/install verification remains last, after all previous items pass.

## Source-of-truth rule

The canonical checklist lives in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md). This file is only the short handoff: update both after meaningful progress, but do not maintain duplicate historical unchecked lists. Older entries saying keyboard accessibility or two-clip export are unverified are superseded by run #152 above.

No signed release APK was produced by run #152. Keep version metadata unchanged and do not release version 395.
