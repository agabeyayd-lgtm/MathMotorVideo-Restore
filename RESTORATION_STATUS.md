# Restoration Status — 2026-10-25

## Resume here

**Do not start over.** The canonical completed-test record and remaining-work checklist are in [README.md](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/blob/fix/editor-395-gate/README.md).

- Repo: `agabeyayd-lgtm/MathMotorVideo-Restore`
- Branch: `fix/editor-395-gate`
- PR: [#8](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/pull/8)
- Installed baseline: versionCode **394** / build **61** (`app-v8.3-61`)
- Latest tested decoder fix: [`52755a6`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/commit/52755a6889f44e2c6936cf34dd0102743c0b875e) switches scaled filmstrip decoding to `OPTION_CLOSEST`.
- [Run #172 — SUCCESS](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375): unit tests, debug APK, offline fixture preparation, **8/8 Android instrumentation tests**, emulator screenshot capture, and diagnostic-artifact upload all passed.
- Verified in #172: distinct filmstrip timestamps now yield usable thumbnails; manual JSON Save As → close → Open Document restores the currently asserted trim, text content/size, music/effect names, and audio levels.
- The broader persistence gate remains open for untested clip order, playhead/zoom, and text position/colour/style.
- Global Undo/Redo: [run #173](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221794555) failed because (1) the feature branch still used the old thumbnail decoder and (2) the test searched for Undo without scrolling the compact tool panel. The decoder fix and test scroll-into-view correction are now on [`fix/global-editor-undo`](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/tree/fix/global-editor-undo); [run #175](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40223890916) is validating them.
- Installed baseline remains versionCode **394** / build **61**. Version 395 is still blocked.

The local saved-draft restore test now passes. It checks restored clip trim duration (7.02 s), preview text overlay, editable text (`ROUND TRIP TEXT`) and saved size (32 sp). The test order was corrected so the editable field is checked while visible, before scrolling down to the size control. Do not revisit this fixed selector/order issue unless a related code change regresses it.

Previously verified and not to repeat unnecessarily: timeline playhead/zoom/scroll, left/right clip trimming/source-duration restore, distinct filmstrip frames, text editing/timing controls, keyboard-resize text input, sequential two-clip MP4 export with originals unchanged, and emulator home/editor screenshots.

## Next steps — do these in order

1. **Broader manual JSON persistence coverage — running.** Basic Save As → close editor → Open Document passed in [run #172](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40221395375). Expanded assertions now check the 2.00 s playhead, 200% zoom and text timing/position/colour/bold style; [run #177](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40224656700) is testing them.
2. **Unified Undo / Redo — selector correction in progress.** Run #175 validated the decoder fix (filmstrip test no longer failed) but the new test could not scroll to Undo because the ambiguous tab selector did not reliably select the clip tools panel. The app now provides stable accessibility labels for tabs and Undo/Redo; [run #178](https://github.com/agabeyayd-lgtm/MathMotorVideo-Restore/actions/runs/40224804039) validates the correction.
3. **Text-strip interaction and export parity** — drag text-strip start/end handles; export timed/styled text and compare with preview.
4. **Audio export verification** — source audio level/mute, background music loop/volume, timed sound-effect start/volume and audio/video sync.
5. **Timeline edge cases** — split at playhead/export boundary; trim-versus-scroll conflicts and multi-clip trim/reorder/seek at several zoom levels.
6. **Physical-phone review** — compact layout, thumbnails, tap targets and keyboard/panel behavior on the actual device.
7. **Release/install gate** — verify signed update/package installation after blockers above pass. Version 395 remains blocked.

## Release rule

Version 395 is **not released**. Keep version metadata unchanged at the installed baseline; do not sign/publish an APK or enable in-app update until the remaining gates pass and the user explicitly authorizes release.

After each meaningful change, update README and this short handoff with commit/run links and evidence. Do not keep duplicating old failure logs here; README owns the canonical checklist.
