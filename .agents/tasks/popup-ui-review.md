# Notification-permission and Exit popups on the Figma glass surface (review pass 2)

Commits 388313f and f22d15d add the opt-in `AppDialog.DialogAppearance.GLASS` (plus `GLOW_PILL` / `GLASS_PILL` button styles) and move both popups onto it. Pass-1 findings were addressed: dedicated `bg_dialog_glass` / `bg_btn_dialog_glass_pill` drawables (inset-wrapped strokes), exact 32/22sp line heights, no font padding, border-aware padding. The card now measures 406dp / 428dp like Figma, the surface is #383838 like Figma, and the border matches. One visual mismatch remains on device: the secondary "Exit" / "Don't allow" pill renders #727272 instead of Figma's #5D5D5D to #565656.

Watch for: secondary glass pill is ~21-28 levels lighter than Figma on the running app, in touch mode as well (confirmed, likely cause: focus highlight ripple on a `focusableInTouchMode` button).

**Verdict**: NEEDS_CHANGES

## High-level view

Both popups reuse the shared `AppDialog` modal and opt into `GLASS`, so every other popup stays pixel-identical (DEFAULT path untouched). Geometry, typography, icons, copy and callbacks match Figma. The remaining gap is colour of the secondary pill in its resting state on device, which is the only blocking item. Everything else is housekeeping.

<details>
<summary>Issues (4)</summary>

1. **Secondary pill too light (blocking, confirmed)** — on emulator the pill samples (114,114,114); Figma samples 93 (left) to 86 (right) and the drawable's own solid is #5E5E5E (94). Remove the persistent focus/ripple highlight for the glass pills (e.g. make `btnSecondary` / `btnPrimary` not `focusableInTouchMode` when `isGlass`, or give the ripple no focus tint) and re-measure in touch mode.
2. **Unrelated rename inside f22d15d (non-blocking, confirmed)** — `SelectTemplateAdapter.kt` is moved `ui/create/photo/adapter` to `ui/create/adapter` (100% similarity) in the fix commit. It is an artefact of another task's staged index. Do not revert it (that task's working tree depends on it) but keep it out of any future squash/PR of this work.
3. **Near-duplicate stroke colour (non-blocking, likely)** — `dialog_glass_stroke` (#36FFFFFF) is 3 alpha levels off `cinema_btn_back_bg` (#33FFFFFF); on-device border is 97 either way. Reuse the existing token to honour "no duplicate tokens", or keep and justify as Figma-measured.
4. **Not verified by anyone (non-blocking, possible)** — Notification popup was not opened by this reviewer (shares the GLASS path); RTL, large font scale, 320dp width, light theme were not verified.

</details>

<details>
<summary>Details</summary>

## Exit popup (Figma 235:774, 350x406) property-by-property

Container 284dp content, 33dp inset from frame (32 + 1 border), icon 80dp at top, Prompt block (title 32 line, 8 gap, message 2 lines of 22), 24 gaps, CTAs 56dp with 16 gap. Implemented: width `dialog_rate_figma_width` 350dp, radius `radius_l` 16dp, padding `spacing_xl`/`spacing_3xl` + 2 hairlines, icon `dialog_rate_icon_*` 80dp, title `text_size_3xl` 24sp Poppins Bold `#E5E1E4` with 32sp exact line, message 14sp Poppins Regular `#ACAAB1` with 22sp exact line, `spacing_s` title/message gap, `spacing_xxl` block gaps, glow pill 56dp (view 80dp with 12dp glow compensation) then glass pill 56dp with `spacing_l - glow` margin. Device vs Figma crop: card height matches, title/message/icon positions visually identical, card fill (56,56,56) both, border ~97 both, primary gradient pill with glow matches, label weights/widths match ("Stay" and "Exit" spans equal). Copy: title "Exit", message with line break after the question, "Stay" primary, "Exit" secondary, same as Figma.

Mismatch: secondary pill measured (114,114,114) at both ends on device vs Figma (93..86). The drawable solid is #5E5E5E, so the extra ~20 levels come from an overlay: `btnSecondary` is `focusableInTouchMode="true"` in `dialog_app_modal.xml` and the drawable's ripple colour is `@color/cinema_btn_back_bg` (20% white); a focused RippleDrawable paints that colour over the fill. UI Automator reports the button focused after showing the dialog and after a touch tap, i.e. this is the normal state users see, not just a keyboard-mode artefact as the plan notes claim. Re-tested in touch mode after a tap inside the dialog: same 114.

## Notification popup (Figma 214:3189, 350x428)

Same code path with bell icon `ic_dialog_notification`, 3-line message (Figma message frame is 300dp wide inside 284; implemented text margins of -0 plus `glow` margin give the same 3-line wrap per the coder's measurement; not independently reproduced). Copy "Notification" / "Don't miss out on important updates, photo generation status, and new features." matches; primary "Go to settings" is the existing string (Figma "Go to setting" is a typo), secondary "Don’t allow". Same secondary-pill colour issue applies.

## Rule compliance

Compose: none. XML + ViewBinding + Material: yes; new code uses `updateLayoutParams` with top-of-file imports, no FQN added, no `findViewById`. Strings: 8 copy keys changed/kept in `values` and all 16 locale folders; `\n` and typographic apostrophes preserved, no placeholders involved; no new keys so nothing orphaned. Spot-read ar, de, ja, ru, vi, zh-rCN, pt-rPT: meaning correct (pt-rPT uses Brazilian "Tem certeza... não salvas", minor). Tokens: `line_height_ms`/`line_height_3xl` are new scale steps (justified, no duplicates); colours `dialog_glass_title/message/btn_secondary_text/surface/pill` are Figma-measured values with no match in `colors.xml` (grep for the hex values returned only the new entries); `dialog_glass_stroke` is the near-duplicate noted above. Strokes: both new drawables wrap the shape in `<inset stroke_hairline>`. Glow: `bg_btn_primary_pill_glow.9.png` reused unchanged with view = 56 + 2x12, visual margin compensation applied, container clipping already handled by the shared layout. Assets: two vector icons, no bitmaps added; `img_popup_notification.webp` is still used by `showDeleteAccountConfirmation` and `img_popup_sign_out.webp` by `showSignOut`, so pass-1 finding 4 (orphan) is withdrawn. Touch targets: 56dp pills. Behaviour: Stay -> `onStay`, Exit -> `onExit`, cancel -> `onStay`; notification primary -> `onGoToSettings`, secondary/cancel -> `onDontAllow`; `MainActivity` and `CustomNotificationDialog` logic untouched. Verification evidence: plan file and commit message record `assembleDebug`, `lintDebug` (clean worktree) and emulator comparison. Build and tests were not re-run here.

## Files

Kotlin: `AppDialog.kt`, `AppDialogCinemaExtensions.kt`, `CustomNotificationDialog.kt` (KDoc). Resources: `bg_dialog_glass.xml`, `bg_btn_dialog_glass_pill.xml`, `ic_dialog_exit.xml`, `ic_dialog_notification.xml`, `colors.xml`, `dimens.xml`, `styles.xml`, `strings.xml` x17.

</details>
