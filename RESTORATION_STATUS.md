# Restoration Status — 2026-10-25

## Resume here

**Do not start over.** The canonical completed-test record and remaining-work checklist are in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md).

- Repo: `agabeyayd-lgtm/MathMotorVideo-Restore`
- Branch: `fix/editor-395-gate`
- PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed baseline: versionCode **394** / build **61** (`app-v8.3-61`)
- Latest editor-test fix: [`c584c0d`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/c584c0db3068a1fd7c09c40ed372d95ace94f89f) — navigates DocumentsUI into Downloads before selecting saved JSON and performs cleanup after failures.
- Latest CI infrastructure fix: [`9c3c1f2`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/9c3c1f2228ee04c5e7d68fce96dfe9606c1e19b9) uses checked-in offline MP4 fixtures. In [run #169 — FAILED](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40219003521), fixture prep, unit tests, debug build, and sequential two-clip MP4 export passed. The JSON picker flow now reached restoration checks; the remaining failure was the loaded music-volume label text mismatch. A second failure was the debug thumbnail fingerprint sampler (6×4 points) missing differences in the compact moving fixture.
- Latest code fix: [`bf1a6e9`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/bf1a6e9dc11ecbd709fabbb4c514be14b0b84b9c) aligns project-loaded music/effect labels with the normal editor labels and increases the debug-only thumbnail fingerprint sample grid to 20×16.
- Latest green baseline: [run #161 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40212230107). Unit tests, debug build, screenshot capture and **7/7 emulator interaction tests passed**.
- Current validation: [run #171](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40220299247) is queued to validate the label and dense-sampler fixes; run #170 was superseded by the newer source change. Do not mark manual project-file restoration complete until all editor interaction tests and the following home screenshot pass.
## Latest confirmed progress

The local saved-draft restore test now passes. It checks restored clip trim duration (7.02 s), preview text overlay, editable text (`ROUND TRIP TEXT`) and saved size (32 sp). The test order was corrected so the editable field is checked while visible, before scrolling down to the size control. Do not revisit this fixed selector/order issue unless a related code change regresses it.

Previously verified and not to repeat unnecessarily: timeline playhead/zoom/scroll, left/right clip trimming/source-duration restore, distinct filmstrip frames, text editing/timing controls, keyboard-resize text input, sequential two-clip MP4 export with originals unchanged, and emulator home/editor screenshots.

## Next steps — do these in order

1. **Manual JSON Save As → reopen — in progress.** The E2E test uses Android's real Create Document picker, closes the editor, opens a fresh editor from home, then uses Open Document to reload and check clip trim, text content/size, music/effect titles and audio levels. [Run #163](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40214305429) saved JSON but hit a stale `Downloads` node; [run #164](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40215482800) found the file but the test called `UiObject2.click()` on the non-clickable filename TextView, so the JSON was not opened and restoration status timed out. The extra screenshot failure was a test cleanup cascade. Test-only fix [`de8948e`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/de8948e649431fd7e2b6aa32210b70d519a5222e) taps the visible filename coordinates, emits UI diagnostics when open fails, and returns home before throwing. [Run #165](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40216650783) validates this specific fix. Do not mark the gate complete until the full round trip passes.
2. **Unified Undo / Redo** — implementation is currently clip-only. Extend it to clip, text and audio/music edits, and verify timeline plus preview restore together.
3. **Text-strip interaction and text export parity** — drag text strip start/end handles; export timed/styled text and compare with preview.
4. **Audio export verification** — original audio level/mute, background music loop/volume, timed sound effect and A/V sync.
5. **Timeline edge cases** — split at playhead/export boundary, trim versus scroll conflicts, multi-clip trim/reorder/seek at multiple zooms.
6. **Physical-phone review** — check compact layout, thumbnails, tap targets and keyboard/panel behavior on the user's actual device.
7. **Release/install gate** — signed update/package install verification after blockers above pass.

## Release rule

Version 395 is **not released**. Keep version metadata unchanged at the installed baseline; do not sign/publish an APK or enable in-app update until the remaining gates pass and the user explicitly authorizes release.

After each meaningful change, update README and this short handoff with commit/run links and evidence. Do not keep duplicating old failure logs here; README owns the canonical checklist.
