# Implementation Plan — Create Video: hide Prompt/Duration/Ratio for GENERATE_STATIC_VIDEO

## Goal
On the Create Video screen, when the selected template is a static-video template
(`ArtTemplate.isStaticVideo == true`, i.e. `templateType == GENERATE_STATIC_VIDEO`), hide three
input sections: (3) AI Prompt, (4) Aspect Ratio, (5) Duration — each header + body. For normal
`GENERATE_VIDEO` templates all three stay visible. Visibility must update reactively every time the
selected template changes at runtime (horizontal template selector).

## Findings from exploration (ground truth, do not re-derive)

1. **Model** — `PhotoArtModels.kt` already exposes `ArtTemplate.isStaticVideo`
   (`templateType == TemplateType.GENERATE_STATIC_VIDEO || imageEditTemplateCode.startsWith("static_video_")`).
   Reuse this property. Do NOT add any new type logic.

2. **ViewModel observation pattern** — `CreateVideoViewModel` exposes each piece of UI state as a
   separate `StateFlow` (`selectedTemplate`, `selectedAspectRatio`, `selectedDuration`, …). The
   Activity observes each one in `observeViewModel()` via
   `lifecycleScope.launch { viewModel.X.collect { ... } }`. There is already a
   `selectedTemplate: StateFlow<ArtTemplate?>` observed in `observeViewModel()`. The reactive hook
   for static-video visibility belongs inside that EXISTING `selectedTemplate` collector — no new
   observing mechanism, no new StateFlow required.

3. **Create flow already branches correctly on `isStaticVideo` — NO create-flow change needed.**
   - `CreateVideoActivity.startVideoGenerationFlow(...)` always passes
     `isStaticVideo = template.isStaticVideo` to `AiProcessingDialogFragment.newInstance(...)`.
   - `AiProcessingDialogFragment` forwards `isStaticVideo` + `optionsJson` + `prompt` to
     `VideoGenerationWorker.enqueue(...)`.
   - `VideoGenerationWorker` branches: `if (isStaticVideo) artSdkManager.generateStaticVideo(uploadFile, templateCode, ...)`
     (ignores `optionsJson` and `prompt`) `else artSdkManager.processImageEditing(... options = optionsJson, prompt = ...)`.
   - Therefore, when the three sections are hidden for a static-video template, the ViewModel
     defaults (`selectedAspectRatio = "9:16"`, `selectedDuration = "5"`) and the empty `etPrompt`
     are never consumed by the static path. The GENERATE_VIDEO path is unaffected (sections stay
     visible, values come from the user). Leave `handleCreateVideoClick()` and
     `startVideoGenerationFlow()` exactly as they are.

4. **Layout structure** — In `activity_create_video.xml`, inside the `NestedScrollView > LinearLayout`,
   sections 3/4/5 are flat sibling pairs with NO wrapping container and NO ids on header/body:
   - Section 3 (AI Prompt): header `TextView` (`@string/create_video_ai_prompt`,
     `layout_marginTop="20dp"`) + body `FrameLayout` (bg `@drawable/bg_card_prompt_glow`,
     `layout_marginHorizontal="-6dp"`, `layout_marginTop="6dp"`) wrapping `etPrompt` + `btnMagicPrompt`.
   - Section 4 (Aspect Ratio): header `TextView` (`@string/create_video_ratio`,
     `layout_marginTop="20dp"`) + body `LinearLayout` (`layout_marginHorizontal="-6dp"`,
     `layout_marginTop="6dp"`) holding `cardRatio916` / `cardRatio169` / `cardRatio11`.
   - Section 5 (Duration): header `TextView` (`@string/create_video_duration`,
     `layout_marginTop="20dp"`) + body `LinearLayout` (`layout_marginHorizontal="-6dp"`,
     `layout_marginTop="6dp"`) holding `cardDuration5s` / `cardDuration8s` / `cardDuration10s`.

   **Chosen approach: add stable ids to the existing 6 views (3 headers + 3 bodies) and toggle them
   as pairs.** This is preferred over wrapping each section in a new container because wrapping
   would move the `layout_marginTop="20dp"` / `layout_marginHorizontal="-6dp"` margins and risk a
   visible spacing regression. Adding ids keeps the exact flat hierarchy, margins, and spacing
   tokens untouched (no new dimens, nothing restyled). Six ids, three visibility pairs.

## Steps

- [ ] 1. Add stable ids to the three section headers and three section bodies in the layout.
      In `activity_create_video.xml`:
      - Section 3 AI Prompt: add `android:id="@+id/tvPromptHeader"` to the prompt header `TextView`
        (text `@string/create_video_ai_prompt`); add `android:id="@+id/layoutPromptSection"` to the
        prompt body `FrameLayout` (bg `@drawable/bg_card_prompt_glow`, the one wrapping `etPrompt`).
      - Section 4 Aspect Ratio: add `android:id="@+id/tvRatioHeader"` to the ratio header `TextView`
        (text `@string/create_video_ratio`); add `android:id="@+id/layoutRatioSection"` to the ratio
        body `LinearLayout` (the one holding `cardRatio916` / `cardRatio169` / `cardRatio11`).
      - Section 5 Duration: add `android:id="@+id/tvDurationHeader"` to the duration header `TextView`
        (text `@string/create_video_duration`); add `android:id="@+id/layoutDurationSection"` to the
        duration body `LinearLayout` (the one holding `cardDuration5s` / `cardDuration8s` /
        `cardDuration10s`).
      Do NOT change any margins, padding, backgrounds, dimens, or child ids. Ids only.
      Files: `app/src/main/res/layout/activity_create_video.xml`
      Verify: `./gradlew assembleDebug` from the workspace root succeeds (View Binding regenerates
      `ActivityCreateVideoBinding` with the six new fields; a compile confirms the ids are valid).

- [ ] 2. React to the selected template inside the EXISTING `selectedTemplate` collector in the Activity.
      In `CreateVideoActivity.observeViewModel()`, locate the existing
      `lifecycleScope.launch { viewModel.selectedTemplate.collect { template -> ... } }` block (the
      one that currently calls `templateAdapter.setSelectedCode(...)` / `scrollToTemplatePosition(...)`).
      Add visibility handling in that SAME collector so it runs on every template change: compute
      `val isStatic = template?.isStaticVideo == true` and set the six views to
      `View.GONE` when `isStatic` is true, else `View.VISIBLE`:
      ```kotlin
      lifecycleScope.launch {
          viewModel.selectedTemplate.collect { template ->
              val sectionVisibility = if (template?.isStaticVideo == true) View.GONE else View.VISIBLE
              binding.tvPromptHeader.visibility = sectionVisibility
              binding.layoutPromptSection.visibility = sectionVisibility
              binding.tvRatioHeader.visibility = sectionVisibility
              binding.layoutRatioSection.visibility = sectionVisibility
              binding.tvDurationHeader.visibility = sectionVisibility
              binding.layoutDurationSection.visibility = sectionVisibility

              if (template != null) {
                  val pos = templateAdapter.setSelectedCode(template.imageEditTemplateCode)
                  if (pos >= 0) {
                      scrollToTemplatePosition(pos)
                  }
              }
          }
      }
      ```
      Use `View.GONE` (not `INVISIBLE`) so layout collapses. `View` is already imported
      (`import android.view.View`); `isStaticVideo` is a property on the already-imported
      `ArtTemplate`. Do NOT add a new collector or a new StateFlow — reuse this existing one.
      Files: `app/src/main/java/com/aiart/photo/video/generator/ui/create/video/CreateVideoActivity.kt`
      Verify: `./gradlew assembleDebug` from the workspace root succeeds. Manual check on device/emulator:
      selecting a `GENERATE_STATIC_VIDEO` template hides the Prompt, Aspect Ratio, and Duration
      sections (headers + bodies, layout collapses); selecting a `GENERATE_VIDEO` template shows all
      three again; switching back and forth updates immediately.

- [ ] 3. Confirm (do NOT modify) that the create flow is correct when sections are hidden.
      Re-read `handleCreateVideoClick()` and `startVideoGenerationFlow()` in `CreateVideoActivity.kt`
      and confirm they already pass `isStaticVideo = template.isStaticVideo` through to
      `AiProcessingDialogFragment` → `VideoGenerationWorker`, which calls `generateStaticVideo(...)`
      for static templates (ignoring `optionsJson`/`prompt`) and `processImageEditing(... options =
      optionsJson ...)` for `GENERATE_VIDEO`. No change required; the hidden-field defaults are never
      consumed by the static path. If and only if a future reading shows the static path actually
      reads prompt/ratio/duration, raise it before editing — otherwise leave this code untouched.
      Files: none (verification only).
      Verify: `./gradlew assembleDebug` from the workspace root still succeeds; no code change in this step.

## Notes / assumptions
- No new strings, dimens, colors, or drawables are introduced. Only six `android:id` attributes and
  one block of visibility toggles reusing an existing collector.
- No tests are added (per AGENTS.md §6; not requested).
- Build command for all verification: `./gradlew assembleDebug` run from
  `/Volumes/ExternalSSD/android_apps/893-PHOTO-ART`.

## Verification note (completed)
- Ran `./gradlew assembleDebug` from `/Volumes/ExternalSSD/android_apps/893-PHOTO-ART`: **BUILD SUCCESSFUL in 15s**, exit code 0. Only pre-existing deprecation warnings (RedeemCodeDialog.kt, CinemaTopAppBar.kt), unrelated to this change.
- Verified via build + code inspection:
  - Six new ids added to `activity_create_video.xml` (`tvPromptHeader`, `layoutPromptSection`, `tvRatioHeader`, `layoutRatioSection`, `tvDurationHeader`, `layoutDurationSection`); no margins/padding/backgrounds/child ids changed. View Binding regenerated and compiled, confirming the ids resolve.
  - `CreateVideoActivity.observeViewModel()` toggles all six views to `View.GONE`/`View.VISIBLE` inside the EXISTING `selectedTemplate` collector, so visibility updates reactively on every template change. No new StateFlow or collector added.
  - Create flow left untouched: `startVideoGenerationFlow()` passes `isStaticVideo = template.isStaticVideo` through to `AiProcessingDialogFragment`; static path uses `generateStaticVideo(...)` ignoring prompt/options, so hidden-field defaults are never consumed. GENERATE_VIDEO path unchanged.
- Needs a device/emulator for full runtime visual verification: confirm sections actually collapse (GONE) when a GENERATE_STATIC_VIDEO template is selected, reappear for GENERATE_VIDEO, and update immediately when switching between templates in the horizontal selector.
