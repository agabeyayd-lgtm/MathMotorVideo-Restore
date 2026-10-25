# Restoration Status — 2026-10-25

## Resume here

**Do not start over.** The canonical completed-test record and remaining-work checklist are in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md).

- Repo: `agabeyayd-lgtm/MathMotorVideo-Restore`
- Branch: `fix/editor-395-gate`
- PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed baseline: versionCode **394** / build **61** (`app-v8.3-61`)
- Latest commit: [`3d910eb`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/3d910eb5466ae05aca2e4fa5f17088dfa9602577)
- Latest validation: [run #161 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107). Unit tests, debug build, screenshot capture and **7/7 emulator interaction tests passed**.

## Latest confirmed progress

The local saved-draft restore test now passes. It checks restored clip trim duration (7.02 s), preview text overlay, editable text (`ROUND TRIP TEXT`) and saved size (32 sp). The test order was corrected so the editable field is checked while visible, before scrolling down to the size control. Do not revisit this fixed selector/order issue unless a related code change regresses it.

Previously verified and not to repeat unnecessarily: timeline playhead/zoom/scroll, left/right clip trimming/source-duration restore, distinct filmstrip frames, text editing/timing controls, keyboard-resize text input, sequential two-clip MP4 export with originals unchanged, and emulator home/editor screenshots.

## Next steps — do these in order

1. **Manual JSON Save As → reopen — in progress.** Added an Android instrumentation test in commit [`9b41603`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/9b416035b5bca7ccb60090b7b3a857896f6a7e9b). It uses Android's Create Document picker to save JSON, closes the editor, opens a fresh editor from home, then uses Open Document to reload the file and checks clip trim, text content/size, music/effect titles and audio levels. [Run #163](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40214305429) is currently queued; do not mark this gate complete until it passes.
2. **Unified Undo / Redo** — implementation is currently clip-only. Extend it to clip, text and audio/music edits, and verify timeline plus preview restore together.
3. **Text-strip interaction and text export parity** — drag text strip start/end handles; export timed/styled text and compare with preview.
4. **Audio export verification** — original audio level/mute, background music loop/volume, timed sound effect and A/V sync.
5. **Timeline edge cases** — split at playhead/export boundary, trim versus scroll conflicts, multi-clip trim/reorder/seek at multiple zooms.
6. **Physical-phone review** — check compact layout, thumbnails, tap targets and keyboard/panel behavior on the user's actual device.
7. **Release/install gate** — signed update/package install verification after blockers above pass.

## Release rule

Version 395 is **not released**. Keep version metadata unchanged at the installed baseline; do not sign/publish an APK or enable in-app update until the remaining gates pass and the user explicitly authorizes release.

After each meaningful change, update README and this short handoff with commit/run links and evidence. Do not keep duplicating old failure logs here; README owns the canonical checklist.
