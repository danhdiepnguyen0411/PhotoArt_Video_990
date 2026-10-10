# Implementation Plan: Update Notification-Permission popup and Exit popup to match Figma

Project: 893-PHOTO-ART (App ID CODE12_893). No Compose. XML + View Binding + Material 3.
Figma file key `LpZ7zpoel2cIq3vER2RDTY` ("[CODE12] 762Y VIDEO GENERATOR (Copy)").

## 0. Status of Figma data (READ FIRST)

The planning agent had NO Figma tool available (the `kiro_powers` tool was not exposed, and an anonymous `web_fetch` of the Figma URL returns nothing readable; `~/.kiro/settings/mcp.json` does register a `figma` server, so the implementing agent may have it). Therefore:

- **Node to popup mapping is UNVERIFIED.** Neither `214:3189` nor `235:774` has been read. Do not assume which is which.
- **No Figma design values (sizes, radii, colors, typography, copy) are confirmed.** Section 3 lists what is in the code TODAY and the token each Figma value must map to. Fill the "Figma" column in Step 1 before touching code.
- Deterministic mapping rule for Step 1 (rule, not a guess):
  - The frame with a notification/bell style illustration, "allow/settings" style primary CTA and a "don't allow/later" style secondary CTA is the **Notification-permission popup**.
  - The frame with an exit/door/goodbye illustration and Exit / Stay style CTAs is the **Exit popup**.
  - If both frames contain the same kind of content, or neither matches, STOP and `send_message` severity `warning` to the user. Do not continue by guessing.
- If the Figma tool still cannot be reached in Step 1, STOP with `send_message` severity `warning` ("Figma not reachable; need exported screenshots or specs of nodes 214:3189 and 235:774"). Do not implement from the existing code alone, because that would not be "giống thiết kế figma".
- Final report must say whether on-device visual verification was done (see Step 7).

## 1. Facts verified in the codebase

| Item | Finding (file) |
| :-- | :-- |
| Shared popup system | `app/src/main/java/com/aiart/photo/video/generator/common/dialog/AppDialog.kt` (694 lines) inflating `app/src/main/res/layout/dialog_app_modal.xml` via `DialogAppModalBinding`. `setupVisuals`, `setupButtons`, `bindButton` are shared by ~10 callers (IntroActivity, HistoryFragment, SettingFragment, MainActivity, PaywallActivity, LoginActivity, RedeemCodeDialog, BaseActivity, AppDialogRateExtensions, AppDialogCinemaExtensions). **Any edit to shared layout/bind code changes all of them.** |
| Exit popup | `AppDialog.showExitConfirmation(activity, onStay, onExit)` at `AppDialog.kt` ~line 600. Banner `R.drawable.img_popup_sign_out`, title `exit_dialog_title`, message `exit_dialog_message`, `CtaOrientation.VERTICAL`, primary `exit_dialog_btn_exit` style `NINE_PATCH_GLOW` (calls `onExit`), secondary `exit_dialog_btn_stay` style `TRANSLUCENT_OUTLINE` (calls `onStay`), `setOnCancelListener { onStay }`. Only real caller: `ui/main/MainActivity.kt` `showExitDialog()` (line ~215: `finishAffinity(); exitProcess(0)`). Wrapper `AppDialog.showExitApp` in `ui/common/dialog/AppDialogCinemaExtensions.kt` ~line 269 is currently unused by callers. KDoc of `showExitConfirmation` cites old Figma ids `#2005:917 / #281:477` (stale, refresh after this task). |
| Notification popup | `AppDialog.showNotificationPermission(activity, onGoToSettings, onDontAllow)` in `ui/common/dialog/AppDialogCinemaExtensions.kt` ~line 202. Banner `img_popup_notification`, strings `popup_notification_title/message/btn_primary/btn_secondary`, VERTICAL CTA, primary `NINE_PATCH_GLOW`, secondary `TRANSLUCENT_OUTLINE`. Wrapped by `common/notification/CustomNotificationDialog.kt`, shown from `MainActivity.onResume()` through `CustomNotificationDialog.showIfAppropriate(this)` after `NotificationHelper.shouldShowCustomHomePrompt`. |
| Behaviour correction to the brief | The custom popup does NOT itself launch the runtime `POST_NOTIFICATIONS` request. Primary CTA calls `NotificationHelper.openNotificationSettings(context, autoBackOnGranted = true, returnTabId)`. The runtime prompt lives in `ui/splash/SplashActivity.kt` (line ~170, `notificationPermissionLauncher`). "Behaviour unchanged" therefore means: keep `onGoToSettings`/`onDontAllow` wiring, `isCustomDialogDismissedThisSession`, cancel listener and `showIfAppropriate` exactly as they are. |
| Copy is leftover from a drama app | `popup_notification_title` = "Never Miss a New Episode", message mentions "new episodes". Photo Art is an AI art app (`docs/localization/product-context.md`). Figma copy may differ. Compare in Step 1. |
| Existing dimens | `dialog_width_max` 360dp, `dialog_cinema_width` 310dp, `dialog_icon_size` 72dp, `dialog_banner_width` 190dp, `dialog_banner_height` 124dp, `radius_3xl` 32dp, `radius_xl` 20dp, `radius_full`, `btn_height_large` 56dp, `spacing_s/m/l/xl/xxl/3xl` = 8/12/16/20/24/32dp, `stroke_hairline` 1dp, `min_touch_target` 48dp (`app/src/main/res/values/dimens.xml`). |
| Current container (dialog_app_modal.xml) | `MaterialCardView` bg `?attr/colorSurfaceVariant`, corner `@dimen/radius_3xl`, elevation 8dp, inner padding `@dimen/spacing_xxl` all sides, banner `dialog_banner_width x dialog_banner_height`, title `?attr/textAppearanceTitleMedium` bold `?attr/colorOnSurface`, message `?attr/textAppearanceBodyLarge` `lineSpacingExtra=4dp`, top margins `@dimen/spacing_l`, CTA container top margin `@dimen/spacing_xxl`, button text 15sp bold (in XML and in `bindButton`), vertical gap `@dimen/spacing_m`. |
| Hard-coded leftovers in touched files | `dialog_app_modal.xml`: `cardElevation 8dp`, `indicatorSize 44dp`, `trackThickness 3.5dp`, `lineSpacingExtra 4dp`, `textSize 15sp` (also duplicated in `AppDialog.bindButton`). `bg_btn_dialog_translucent.xml`: `#33FFFFFF` / `#99FFFFFF` literals (equivalents exist: `@color/cinema_btn_back_bg` = #33FFFFFF, `@color/cinema_stroke_line` = #99FFFFFF) and 4dp insets instead of `stroke_hairline`. Clean these ONLY if the file is edited for this task. |
| Existing assets | `drawable-nodpi/img_popup_notification.webp` (51 KB), `drawable-nodpi/img_popup_sign_out.webp` (35 KB), `drawable-xxhdpi/bg_btn_primary_glow.9.png`. `bg_btn_translucent_pill.xml` also exists (same look as `bg_btn_dialog_translucent`). |
| Locale folders | `values`, `values-ar`, `-bn`, `-de`, `-es`, `-fr`, `-hi`, `-in`, `-ja`, `-ko`, `-pt`, `-pt-rPT`, `-ru`, `-tr`, `-vi`, `-zh-rCN`, `-zh-rTW` (16 translations + default; `values-sw600dp` is dimens only). Each has `exit_dialog_*` and `popup_notification_*` keys (verified via grep count = 2 per file for title keys). |
| Tooling | `cwebp` at `/opt/homebrew/bin/cwebp`, `adb` at `~/Library/Android/sdk/platform-tools/adb`. Working tree already has unrelated uncommitted changes (ArtTemplateRepository, AiError/AiProcessing dialogs, CreatePhoto*, tests). Do not touch or stage them. |

## 2. Design decisions

1. **Reuse `AppDialog` + `dialog_app_modal.xml`; no new dialog class.** Both popups already go through it. Only the Figma delta is applied.
2. **Scope control (shared layout risk).** In Step 1 open 1 or 2 other popups in the same Figma file (e.g. the Sign Out / Not Enough Coins popups) and compare container specs with the two target frames.
   - If the container (bg, radius, stroke, padding, title/message typography, button sizes) is identical across popups: apply changes once in `dialog_app_modal.xml` / `bindButton` (clean, benefits all callers).
   - If the two target frames differ from the other popups: add `enum class DialogAppearance { DEFAULT, FIGMA_V2 }` to `AppDialog` + `DialogConfig.appearance` + `Builder.setAppearance(...)`, applied in one private `applyAppearance(binding)` function, and opt in only from `showExitConfirmation` and `showNotificationPermission`. Default appearance must leave every other popup pixel-identical.
   - Do not guess: record which branch was taken in the final report.
3. **Tokens first.** Snap each Figma number within 2dp to an existing token (`spacing_*`, `radius_*`, `dialog_*`, `btn_height_large`). Add a token to `dimens.xml` only when no token is within 2dp and the value is not on the 8dp grid; name per AGENTS.md 3.5: `dialog_<view>_<property>` for component tokens (e.g. `dialog_banner_width` style). Never add `[screen]_[view]_padding` duplicates of existing scale values.
4. **Colors.** Use M3 attrs (`?attr/colorSurface`, `?attr/colorSurfaceVariant`, `?attr/colorOnSurface`, `?attr/colorOnSurfaceVariant`, `?attr/colorPrimary`) or existing `cinema_*` colors first. Add a color only if it is a brand-exclusive value absent from `colors.xml` (check `cinema_*`, `md_theme_*`). Note the dark glass dialog surface used by `bg_dialog_ai_processing.xml` (`#F2141418`, stroke `#4DFFFFFF`, radius 16dp) is a precedent from this same Figma file, but it is hard-coded there. Do not copy that style unless Figma says the popups use it; if used, create ONE shared drawable with the required `<inset android:inset="@dimen/stroke_hairline">` wrapper.
5. **Buttons.** Keep `ButtonStyle.NINE_PATCH_GLOW` and `TRANSLUCENT_OUTLINE` if Figma CTAs are still glow pill + glass pill. Regenerate the `.9.png` only if the glow or gradient differs, following `.agents/skills/android-nine-patch/SKILL.md` and AGENTS.md 3.8 (view height = solid height + 2 x glow radius, visual margin compensation, `clipChildren=false`, pill stretch mark only in the flat middle, 1px border pure black/transparent). If Figma changes the arrangement (horizontal vs vertical) switch `setCtaOrientation` only.
6. **Illustrations.** Download from Figma only if they differ from the current `img_popup_notification` / `img_popup_sign_out`. Use new file names (`img_popup_notification.webp` can be overwritten in place since it is used only by `showNotificationPermission` and line ~512 of `AppDialogCinemaExtensions.kt`; for `img_popup_sign_out` it is ALSO used by `showSignOut`, so a different Exit illustration needs a NEW file `img_popup_exit.webp`).
7. **Copy.** Change strings only if Figma copy differs. Keep the existing keys (no rename, avoids touching Kotlin and orphaning), update the value in all 17 files.

## 3. Design value map (fill the "Figma" column in Step 1)

Current values are from code. "Target token" is the mapping rule, not a confirmed value.

| Property | Current (code) | Figma 214:3189 | Figma 235:774 | Target token / rule |
| :-- | :-- | :-- | :-- | :-- |
| Dialog width | `min(90% screen, dialog_width_max 360dp)` set in `AppDialog.show()` | TBD | TBD | `@dimen/dialog_width_max` (360) or `dialog_cinema_width` (310) if <= 2dp off; else new `dialog_*_width` |
| Container bg / stroke | `?attr/colorSurfaceVariant`, no stroke, elevation 8dp | TBD | TBD | M3 attr or `cinema_*`; stroke only with `<inset>` |
| Corner radius | `radius_3xl` 32dp | TBD | TBD | `radius_xl` 20 / `radius_l` 16 / `radius_xxl` 24 / `radius_3xl` 32 |
| Inner padding | `spacing_xxl` 24dp | TBD | TBD | `spacing_*` scale |
| Illustration size | 190x124dp | TBD | TBD | `dialog_banner_width/height` or new `dialog_banner_*` token |
| Illustration to title gap | `spacing_l` 16dp | TBD | TBD | `spacing_*` |
| Title typography | TitleMedium, bold, `colorOnSurface` | TBD (size sp, weight, color, Poppins) | TBD | `?attr/textAppearance*`; if size/weight differ apply via `android:textSize` sp + `android:textFontWeight` (precedent in `dialog_ai_error.xml`) |
| Message typography | BodyLarge, line extra 4dp | TBD | TBD | same rule; `lineSpacingExtra` token-free is acceptable if sp/dp from Figma line height |
| Title to message gap | `spacing_l` | TBD | TBD | `spacing_s` / `spacing_m` / `spacing_l` |
| Message to CTA gap | `spacing_xxl` | TBD | TBD | `spacing_*` |
| CTA size | `btn_height_large` 56dp, full width in VERTICAL | TBD | TBD | `btn_height_large`, radius `radius_full` |
| CTA gap | `spacing_m` 12dp | TBD | TBD | `spacing_*` |
| CTA text | 15sp bold | TBD | TBD | replace magic `15sp` (XML and `bindButton`) with ONE source of truth (`@dimen/dialog_btn_text_size` sp token or `textAppearance`) if touched |
| Secondary button fill / stroke | `#33FFFFFF` / `#99FFFFFF` stroke 1dp, insets 4dp | TBD | TBD | `cinema_btn_back_bg` / `cinema_stroke_line`, `<inset stroke_hairline>` |
| Close "X" button | none for these popups | TBD (present?) | TBD | if present: `setShowCloseButton(true)`, keep `min_touch_target` 48dp |
| Copy (title/message/CTA) | see strings below | TBD | TBD | Section 5 |

Accessibility guard (AGENTS 3.5.5): if Figma gives a touch target < 48dp, keep `min_touch_target` and explain in the report. Content is short, so do not add a scroll container unless the measured height exceeds ~ 360dp screens with the largest font scale; if it can overflow, wrap the inner `ConstraintLayout` in `NestedScrollView` (not in the shared layout unless needed by all).

## 4. Ordered implementation steps

- [ ] 1. **Read both Figma nodes and freeze the spec.** Activate the figma power (`kiro_powers` action=activate powerName=figma, then action=use). For EACH of `214:3189` and `235:774` call `get_metadata`, `get_design_context` and `get_screenshot`. Decide the node to popup mapping with the rule in Section 0, fill the Section 3 table (exact dp, radii, hex/alpha, font size/weight/line height, gaps, copy text, button orientation, close button, outer glow radius) and list image assets to export (illustration layers). Also open one or two sibling popups to settle the scope decision (Design decision 2). Write the filled table and the mapping to the top of `.agents/tasks/popup-ui-plan.md` under a new "Resolved Figma spec" heading (keep the plan otherwise unchanged).
      Files: `.agents/tasks/popup-ui-plan.md`
      Verify: the table has zero "TBD" cells and the mapping line states "214:3189 = <popup>, 235:774 = <popup>". If Figma is unreachable, send `warning` and stop (Section 0).

- [ ] 2. **Export and convert assets (only those that differ).** Export illustration(s) from Figma at 3x PNG, then convert and remove the source:
      `cwebp -q 88 <input>.png -o app/src/main/res/drawable-nodpi/<name>.webp && rm -f <input>.png`
      Targets: `img_popup_notification.webp` (overwrite), and `img_popup_exit.webp` (NEW) when the Exit illustration differs, so `showSignOut` keeps `img_popup_sign_out`. Each file must be < 150 KB. Icons (for example a close icon or CTA icon) go in as Vector Drawable XML under `app/src/main/res/drawable/`. Only if the glow button differs: rebuild `drawable-xxhdpi/bg_btn_primary_glow.9.png` per the nine-patch skill; otherwise leave it.
      Files: `app/src/main/res/drawable-nodpi/img_popup_notification.webp`, `app/src/main/res/drawable-nodpi/img_popup_exit.webp` (if needed)
      Verify: `./gradlew assembleDebug` succeeds (AAPT2 accepts assets) and `git status` shows no leftover .png/.jpg sources.

- [ ] 3. **Tokens (only what step 1 proves is missing).** Add missing dimens to `app/src/main/res/values/dimens.xml` next to the existing `dialog_*` block (line ~100), mirror in `values-sw600dp/dimens.xml` only if that file overrides the `dialog_*` tokens (check with grep first). Add a color to `values/colors.xml` only if brand-exclusive. Reuse everything else.
      Files: `app/src/main/res/values/dimens.xml` (and optionally `values/colors.xml`)
      Verify: `./gradlew assembleDebug` succeeds; no new token duplicates an existing value (compare against the Section 1 table).

- [ ] 4. **Update layout/drawables to match the spec.** Edit `app/src/main/res/layout/dialog_app_modal.xml` (container bg/radius/padding, banner size, title/message typography and margins, CTA container margins, CTA text size via a single source) and, where the secondary CTA differs, `app/src/main/res/drawable/bg_btn_dialog_translucent.xml` (replace literals with `@color/cinema_btn_back_bg` / `@color/cinema_stroke_line`, use `<inset android:inset="@dimen/stroke_hairline">` if the spec allows, keep the `<stroke>` wrapped in an inset). If Design decision 2 chose the opt-in appearance, add `DialogAppearance` to `AppDialog.kt` and apply it in a single `applyAppearance(binding)` called from `setupVisuals`; keep DEFAULT behaviour identical to today. Keep hierarchy flat, no `findViewById`, imports at top only (the existing `androidx.appcompat.R.attr.colorPrimary` and `android.widget.ProgressBar` FQNs in `AppDialog.kt` are pre-existing; convert to imports only if that line is touched).
      Files: `app/src/main/res/layout/dialog_app_modal.xml`, `app/src/main/res/drawable/bg_btn_dialog_translucent.xml` (if needed), `app/src/main/java/com/aiart/photo/video/generator/common/dialog/AppDialog.kt` (only if needed)
      Verify: `./gradlew assembleDebug` succeeds. Spot-check that other popups are unaffected: view-binding regenerates without errors and `grep` shows `DialogAppModalBinding` ids used by `AppDialog.kt` still exist.

- [ ] 5. **Update the two call sites.**
      - Exit: `AppDialog.showExitConfirmation` in `AppDialog.kt` (banner resource, orientation, button styles, optional close button as per Figma; refresh the KDoc to cite the new node id).
      - Notification: `AppDialog.showNotificationPermission` in `ui/common/dialog/AppDialogCinemaExtensions.kt` (same items; refresh the KDoc). Update the class KDoc of `common/notification/CustomNotificationDialog.kt` only if it stops being accurate.
      Keep callbacks identical: exit `dialog.dismiss(); onExit()` / stay `dialog.dismiss(); onStay?.invoke()` / cancel = stay; notification `onGoToSettings` / `onDontAllow` / cancel = `onDontAllow`. Do not change `MainActivity.kt` or `CustomNotificationDialog.show()` logic.
      Files: `.../common/dialog/AppDialog.kt`, `.../ui/common/dialog/AppDialogCinemaExtensions.kt`
      Verify: `./gradlew assembleDebug` succeeds; diff of these two functions shows no change to lambdas other than style/resource arguments.

- [ ] 6. **Copy and localization (only if Figma copy differs from current).** Update values for the existing keys `exit_dialog_title`, `exit_dialog_message`, `exit_dialog_btn_stay`, `exit_dialog_btn_exit` and `popup_notification_title`, `popup_notification_message`, `popup_notification_btn_primary`, `popup_notification_btn_secondary` in `values/strings.xml` (lines ~446 and ~658) and in all 16 locale files: ar, bn, de, es, fr, hi, in, ja, ko, pt, pt-rPT, ru, tr, vi, zh-rCN, zh-rTW. Before writing, read `.agents/skills/app-localization/SKILL.md`, `docs/localization/product-context.md` (Photo Art = AI art app, no coin, no drama/episode wording) and `docs/localization/glossary.md`. Translate by meaning (CTA = the actual action), preserve `\n`, apostrophes/escapes and the typographic apostrophe already used ("Don’t allow"). Keep key names and formatting arguments unchanged. Note any key that Figma makes obsolete; remove only after `grep -rn "<key>" app/src/main` shows no usage.
      Files: `app/src/main/res/values*/strings.xml` (17 files)
      Verify: `./gradlew assembleDebug` succeeds (malformed XML/escapes fail resource merge); `./gradlew lintDebug` shows no new MissingTranslation/ExtraTranslation/StringFormat errors for these keys; eyeball the longest locales (de, ru, vi, ar RTL) for CTA length (CTA has marquee fallback).

- [ ] 7. **Visual and behaviour verification.**
      a. Re-fetch `get_screenshot` for both nodes and re-check the final XML/dimens against `get_metadata` (every row of the Section 3 table).
      b. `./gradlew assembleDebug` then `./gradlew lintDebug`.
      c. If a device/emulator exists (`adb devices` lists one): `adb install -r app/build/outputs/apk/debug/app-debug.apk`; open the app, press back on Home to show the Exit popup, screenshot with `adb exec-out screencap -p > /tmp/exit.png`; for the notification popup, on Android 13+ deny notifications so `NotificationHelper.shouldShowCustomHomePrompt` is true (3rd+ launch after 2 splash denials per the comment in `MainActivity.onResume`), then screenshot. Compare with the Figma screenshots, also at 320dp-wide/large font scale, dark theme, and `ar` (RTL). Delete the temp screenshots afterwards.
      d. Behaviour: Exit button closes the app; Stay/back/outside-tap keeps the app; notification primary opens the notification settings (`openNotificationSettings`), secondary/cancel sets `isCustomDialogDismissedThisSession`. Also open one other popup (Sign Out via `AppDialog.showSignOut`, Delete Account, Rate) to confirm no regression if the shared layout was changed.
      e. If no device is available, state explicitly in the final report: "on-device visual verification was not performed; verified by build + lint + Figma metadata comparison only".
      No unit or UI tests are to be added (AGENTS.md section 6).
      Files: none (temp files only, removed afterwards)
      Verify: all commands above exit 0 and the checklist in 7a is fully ticked.

- [ ] 8. **Cleanup and diff review.** Confirm with `grep -rn` that any replaced drawable/string/dimen is unreferenced before deleting it (`img_popup_sign_out` is still used by `showSignOut`; `ic_exit_dialog`, `bg_dialog_rounded`, `bg_dialog_rate` were not verified as used, leave them unless the grep proves they are orphaned AND they were made obsolete by this task). Check `git diff --stat` only contains files from this plan; do not stage the unrelated pre-existing modifications. Do not commit unless asked.
      Files: as above
      Verify: `./gradlew assembleDebug` succeeds after removals; `git status` shows only intended files.

## 5. Copy / locale summary

- Keys (existing, values only): `exit_dialog_title`, `exit_dialog_message`, `exit_dialog_btn_stay`, `exit_dialog_btn_exit`, `popup_notification_title`, `popup_notification_message`, `popup_notification_btn_primary`, `popup_notification_btn_secondary`.
- Current English: "Exit App?", "Are you sure you want to exit? Unsaved changes may be lost.", "Stay", "Exit"; "Never Miss a New Episode", "Turn on notifications to get updates\nwhen new episodes arrive.", "Go to settings", "Don’t allow". The notification copy references episodes (leftover from the drama template) and likely needs Photo Art wording if Figma uses it; the Figma text is the source of truth. If Figma text is absent/placeholder, keep the current copy and flag it in the report instead of inventing features.
- Locale files to update when copy changes: `values/` + `values-ar`, `-bn`, `-de`, `-es`, `-fr`, `-hi`, `-in`, `-ja`, `-ko`, `-pt`, `-pt-rPT`, `-ru`, `-tr`, `-vi`, `-zh-rCN`, `-zh-rTW`.
- If copy is unchanged, Step 6 is skipped entirely.

## 6. Constraints checklist for the implementer

- No Compose, no `findViewById`, View Binding only, M3 widgets.
- No hard-coded user-visible strings; no new static colors unless brand-exclusive; no duplicate tokens; snap <= 2dp to tokens.
- Images: WebP q88 in `drawable-nodpi` (large) or vector XML (icons); delete sources; nine-patch only as `.9.png` in `drawable-xxhdpi`.
- Every `<stroke>` drawable wrapped by `<inset>`.
- No FQN or inline imports in Kotlin; imports at top.
- No new tests. Behaviour of both popups unchanged.
- Report at the end: node to popup mapping, scope decision, tokens added, assets added, strings changed, and what was and was not verified.

## Resolved Figma spec and verification (implementation report)
Figma read through the Figma REST API (read-only, `FIGMA_ACCESS_TOKEN` from the environment) because the `figma` power's remote MCP was not callable from this agent; node tree, fills, radii, auto-layout, text styles, screenshots and SVG icons were taken from the real file.
- Mapping: **214:3189 = Notification-permission popup ("Noti")**, **235:774 = Exit popup**.
- Both frames are the same glass popup (350dp wide, radius 16, padding 32, 80dp icon, 24dp gaps, title Poppins Bold 24/32 #E5E1E4, message Poppins Regular 14/22 #ACAAB1, 56dp pill CTAs with 16dp gap: glow gradient pill + glass pill). This differs from all other AppDialog popups, so scope branch = opt-in `AppDialog.DialogAppearance.GLASS` (DEFAULT untouched). Reused: `bg_dialog_rate`, `bg_btn_primary_pill_glow.9.png` (12dp glow, 80dp view), `bg_btn_not_now`, `dialog_rate_figma_width/icon_*`, `accent_lavender`.
- Exit: Figma puts "Stay" as primary (glow) and "Exit" as secondary; callbacks stay bound to the same actions (Stay -> onStay, Exit -> onExit, cancel -> onStay).
- Notification: primary "Go to settings" -> onGoToSettings, secondary "Don’t allow" -> onDontAllow, cancel -> onDontAllow (unchanged). Figma literal "Go to setting" treated as typo of existing "Go to settings".
- New tokens: `line_height_ms` 22sp, `line_height_3xl` 32sp; colors `dialog_glass_title/message/btn_secondary_text` (Figma hex values not in M3 theme); 4 `TextAppearance.FilmAI.Dialog.Glass.*` styles; vectors `ic_dialog_notification`, `ic_dialog_exit` (exported from Figma SVG).
- Copy: `exit_dialog_title` ("Exit"), `exit_dialog_message` (line break after the question, per Figma), `popup_notification_title` ("Notification"), `popup_notification_message` updated in all 17 values files.
- Verification run: `./gradlew assembleDebug` OK; `./gradlew lintDebug` OK (no issues on changed files); emulator-5554 install + screenshots of both popups compared to Figma screenshots (layout, icon, type, button proportions match). Not verified: RTL/`ar`, large font scale, small (320dp) screens, light theme, physical device.
## Review iteration 2 (fixes for popup-ui-review.json)
- Surface: new `drawable/bg_dialog_glass.xml` (solid `dialog_glass_surface` #383838, 1dp `dialog_glass_stroke`, shape in `<inset stroke_hairline>`); `bg_dialog_rate` untouched (Rate dialog). Figma render sampled as flat #383838 (the gradient is barely visible in the export), so no gradient.
- Secondary pill: new `drawable/bg_btn_dialog_glass_pill.xml` (solid `dialog_glass_pill` #5E5E5E, same stroke, inset); `bg_btn_not_now` untouched (Rate dialog). New colors are Figma-measured values, not in M3 theme/cinema_*.
- Rhythm: `includeFontPadding=false` on title/message and an exact line height (32sp / 22sp) via `setExactLineHeight`. The `lineHeight` item in a TextAppearance is NOT applied by `setTextAppearance`, and TextViewCompat.setLineHeight read metrics before Poppins was applied (pitch came out 25.7dp), so the extra is computed after the font is applied (`post`). Padding gets +2 hairlines per side because Figma measures 32dp from the outer border edge while the drawable is inset 1dp.
- NOT done, reviewer finding 4 is wrong: `img_popup_notification.webp` is still used by `showDeleteAccountConfirmation` (`AppDialogCinemaExtensions.kt`, called from `SettingFragment`). Deleting it would break that popup/build, so it is kept.
- Verification (emulator-5554, 1280x2856 @480dpi; main working tree currently cannot compile because of another task's uncommitted edits [AiProcessingDialogFragment / dialog_ai_processing.xml / KSP Room], so the build was run in a clean `git worktree` of HEAD + these files): `./gradlew assembleDebug` OK, `./gradlew lintDebug` OK (only a pre-existing UseKtx warning at AppDialog.kt:251).
- Pixel comparison vs Figma PNG export (dp from card top, Exit 235:774 / Noti 214:3189): card height 406.3 / 428.0 (Figma 406 / 428); icon 38.7 (39); title 142.7 (141.7); message lines 182.3, 204.7 (182.3, 204.7; Noti 3rd line 226.3 = 226.3); Stay/primary glow 241 to 303 (241.7 to 304.7); secondary 317 to 371 (317 to 373, 2dp is the mandated hairline inset); surface #383838 = Figma; pill #5E5E5E (Figma ~#5E) in touch mode. Both popups confirmed on device, Noti message wraps to 3 lines like Figma. In non-touch (keyboard) mode the focused secondary pill shows its ripple focus highlight (lighter); not changed.
- Not verified: RTL, large font scale, 320dp screens, light theme, physical device.
