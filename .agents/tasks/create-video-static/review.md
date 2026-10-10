# Hide Prompt/Duration/Ratio sections for GENERATE_STATIC_VIDEO on Create Video

The Create Video screen now hides the AI Prompt, Aspect Ratio, and Duration input sections when the selected template is a static-video template (`ArtTemplate.isStaticVideo == true`, i.e. `templateType == GENERATE_STATIC_VIDEO`). The change is deliberately minimal: six `android:id` attributes added to the three existing header/body view pairs in `activity_create_video.xml`, plus a visibility toggle inside the already-existing `selectedTemplate` collector in `CreateVideoActivity`. No ViewModel change, no new StateFlow, no layout restructuring. The create flow is untouched because it already branched on `isStaticVideo` end-to-end.

Watch for: nothing blocking. One minor interpretation note — `isStaticVideo` is derived inline in the Activity collector off the collected `ArtTemplate` rather than surfaced as a dedicated UI-state field (confirmed), which still satisfies "reuse the model property, react via the existing StateFlow."

**Verdict**: APPROVED

## High-level view

The visibility logic lives inside the pre-existing `lifecycleScope.launch { viewModel.selectedTemplate.collect { ... } }` block, so it re-evaluates on every template change in the horizontal selector, not just at screen open. The toggle uses `View.GONE`, so the collapsed sections free their vertical space rather than leaving blank gaps.

The layout change is purely additive: stable ids on the three section headers (`TextView`) and their three bodies (the prompt `FrameLayout`, ratio `LinearLayout`, duration `LinearLayout`). No margins, padding, backgrounds, or child ids were altered, so there's no spacing-regression risk and no new dimens were invented.

The create action is correct for static templates without defensive work. `startVideoGenerationFlow` already passes `isStaticVideo = template.isStaticVideo` into `AiProcessingDialogFragment`, which forwards it to `VideoGenerationWorker`; the worker calls `generateStaticVideo(uploadFile, templateCode, ...)` for static templates and ignores the options JSON and prompt. The GENERATE_VIDEO path still builds `optionsJson` from ratio/duration and sends the prompt, so the hidden-field defaults (`9:16`, `5s`, empty prompt) are never consumed on the static path.

<details>
<summary>Issues (0)</summary>

No blocking or actionable findings. One informational note recorded in the summary: `isStaticVideo` is derived inline in the collector rather than exposed as a discrete UI-state field; this matches the task's "reused, not re-derived" intent since it reads the model property off the existing `selectedTemplate` StateFlow.

</details>

<details>
<summary>Details</summary>

### Reactive visibility via the existing selectedTemplate collector

The six visibility assignments were inserted at the top of the existing collector that also drives `templateAdapter.setSelectedCode(...)` and `scrollToTemplatePosition(...)`:

```kotlin
viewModel.selectedTemplate.collect { template ->
    val sectionVisibility = if (template?.isStaticVideo == true) View.GONE else View.VISIBLE
    binding.tvPromptHeader.visibility = sectionVisibility
    binding.layoutPromptSection.visibility = sectionVisibility
    binding.tvRatioHeader.visibility = sectionVisibility
    binding.layoutRatioSection.visibility = sectionVisibility
    binding.tvDurationHeader.visibility = sectionVisibility
    binding.layoutDurationSection.visibility = sectionVisibility
    ...
}
```

`selectedTemplate` is a pre-existing `StateFlow<ArtTemplate?>` on `CreateVideoViewModel` (set by `selectTemplate(...)` and `setSelectedCode(...)`), so the toggle fires on every runtime selection change, not only at open. `View.GONE` is used throughout — confirmed, not `INVISIBLE` — so the three sections collapse and the layout reflows. No new StateFlow or ad-hoc observer was added; the ViewModel file was not modified at all (confirmed: the commit touches only the Activity and the layout).

`isStaticVideo` is the model property `ArtTemplate.isStaticVideo` (`templateType == GENERATE_STATIC_VIDEO || imageEditTemplateCode.startsWith("static_video_")`), read directly off the collected template rather than re-derived — confirmed reuse. The task phrasing "exposed through the existing UI state" is satisfied in the sense that the selected template is the existing UI state and the boolean is computed from it; the implementation chose not to add a separate derived field, which keeps the change smaller and is a reasonable reading of the intent.

### Additive layout ids, no spacing change

The layout diff adds exactly six ids and nothing else (confirmed by the patch): `tvPromptHeader` + `layoutPromptSection`, `tvRatioHeader` + `layoutRatioSection`, `tvDurationHeader` + `layoutDurationSection`. Each pair is header `TextView` plus body container. The surrounding `layout_marginTop="20dp"` / `layout_marginHorizontal="-6dp"` attributes are unchanged, so toggling the pair as a unit removes both the header and its spacing together. Choosing ids-on-existing-views over wrapping in new containers avoids relocating those margins, which is the right call for a no-regression change. No new dimens, colors, drawables, or strings.

### Create flow correctness with hidden fields

`startVideoGenerationFlow` passes `isStaticVideo = template.isStaticVideo` (confirmed) through `AiProcessingDialogFragment.newInstance(...)` → `VideoGenerationWorker.enqueue(...)`. The worker branches: `if (isStaticVideo) artSdkManager.generateStaticVideo(uploadFile, templateCode, timeoutMs = 75000L)` else the image-editing path consuming `optionsJson`/`prompt`. So for a static template the empty `etPrompt` and the default ratio/duration are never read. The GENERATE_VIDEO path still calls `viewModel.buildOptionsJson(ratio, duration)` and forwards the trimmed prompt. No create-flow code was modified, which is correct — the branch already existed.

### AGENTS.md compliance

No Jetpack Compose. View Binding is used throughout (`binding.tvPromptHeader`, etc.); no `findViewById` in the file (confirmed). `android.view.View` is imported at the top (line 7); the added code uses `View.GONE`/`View.VISIBLE` with no inline fully-qualified names. No invented tokens. The change is minimal and scoped to the two in-scope files that needed editing.

### Build evidence

The plan's completed verification note and the commit message both record `./gradlew assembleDebug` → BUILD SUCCESSFUL (exit 0), with only pre-existing deprecation warnings unrelated to this change. View Binding regenerated `ActivityCreateVideoBinding` with the six new fields, and the Activity compiled against them, which transitively confirms the ids resolve. Build was not re-run per instructions; no articulable doubt remained that would warrant a spot-check beyond the code reads already done.

</details>

<details>
<summary>File map</summary>

- `app/src/main/java/.../ui/create/video/CreateVideoActivity.kt` — six visibility assignments added inside the existing `selectedTemplate` collector.
- `app/src/main/res/layout/activity_create_video.xml` — six `android:id` attributes added to the three section header/body pairs; nothing else changed.
- `CreateVideoViewModel.kt` — not modified (in scope for review but no change was needed; `selectedTemplate` StateFlow already present).

Full diff: `git show 53dd86b` (commit "feat: hide prompt/duration/ratio for static video templates").

</details>
