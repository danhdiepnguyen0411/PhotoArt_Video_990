# Premium gold-crown badge replaces the video indicator on template cards

The video indicator badge is gone from the Home and Discover template cards and the premium badge now shows a gold outlined crown inside a translucent glass circle, per Figma node 4063:1907. Rather than deleting the `ivVideoIndicator` ImageView and editing a separate premium view, the coder repurposed the same badge slot: the ImageView keeps its position and size but was renamed to `ivPremiumIndicator` and re-pointed at the two new drawables (`ic_premium_crown`, `bg_badge_premium_glass`). The adapters stop toggling the view on `item.isVideo` and instead toggle it on `item.premium`. Both new drawables match the spec precisely, grep for `ivVideoIndicator` is clean, `playerContainer` and playback logic are untouched, and the working-tree build is reported `BUILD SUCCESSFUL`.

Watch for: a DiffUtil-payload + model-type refactor (`ArtTemplate` → `HomeSectionUi`/`HomeTemplateCardUi`/`DiscoverTemplateCardUi`) is bundled into the same commit and reaches well beyond the stated plan (confirmed). The committed snapshot references those model types, but they exist only as untracked/uncommitted files in the working tree, so the commit is not self-contained — the recorded build passed against the working tree, not against `cd0ce5b` in isolation (confirmed).

**Verdict**: APPROVED

## High-level view

The premium/video change itself is correct and faithful to the spec. The video indicator no longer exists as a video affordance: there is no `ic_play_mini` source, no `isVideo` toggle, and a repo-wide grep for `ivVideoIndicator` returns nothing. The same ImageView was reused as the premium badge, keeping the top-left position and the 20dp (home) / 22dp (discover) footprint, now pointing at the new gold-crown vector and glass-oval background with a proper content description.

Both new drawables are exact. `ic_premium_crown.xml` is a stroke-only Vector Drawable (`#E7C878`, width 2, round caps/joins, transparent fill, 19×18 viewport) with no bitmap. `bg_badge_premium_glass.xml` is an oval with solid `#33FFFFFF`, a 1dp `#33FFFFFF` stroke, wrapped in `<inset android:inset="@dimen/stroke_hairline">` as AGENTS.md 3.7 requires. All dimension tokens used (`icon_size_m`, `spacing_sm`, `spacing_s`, `spacing_xs`, `stroke_hairline`) already exist; none were duplicated. The content description string exists. No Compose, View Binding only, imports at the top.

Two things diverge from the plan. First, the plan said to delete the `ivVideoIndicator` ImageView block and separately edit an existing `ivPremiumIndicator`; in reality the base layouts had no premium view, so the coder renamed the one ImageView. This satisfies the intent (video indicator gone, premium crown top-left) and is arguably cleaner, so it is not blocking. Second, and more notable, the commit also migrates the three adapters from `ArtTemplate` to new `*CardUi` model types and introduces DiffUtil change-payloads for badge-only rebinds. That is a real refactor the plan never mentioned, and it depends on model files that are not part of this commit and not yet committed anywhere.

<details>
<summary>Issues (3)</summary>

1. **ImageView repurposed, not removed** — spec item 1 reads literally as "the ImageView block is gone from both layouts," but the block was renamed `ivVideoIndicator`→`ivPremiumIndicator` and kept. Functionally the video indicator is gone (no play src, no `isVideo` toggle, grep clean) and the premium crown sits top-left as required; accept as meeting intent, but note the literal wording mismatch.
2. **Unrelated refactor bundled in** — the commit migrates adapters to `HomeSectionUi`/`HomeTemplateCardUi`/`DiscoverTemplateCardUi` and adds DiffUtil payloads, well beyond the premium-icon scope. Consider splitting into its own commit so the UI change is reviewable in isolation; not blocking since the working-tree build passes.
3. **Commit not self-contained** — `cd0ce5b` references model types that exist only as untracked/uncommitted working-tree files, so that commit alone would not compile. Commit the model files (and the ViewModel wiring that produces them) together with the adapters so the history builds at every point.

</details>

<details>
<summary>Details</summary>

### Video indicator removal: repurposed, not deleted

The base layouts (`HEAD~1`) contained only `ivVideoIndicator` and `playerContainer` — there was no separate `ivPremiumIndicator`. The spec and plan assumed two distinct views (delete one, edit the other), but the coder renamed the single badge ImageView to `ivPremiumIndicator`, swapped its `src` from `ic_play_mini` to `ic_premium_crown`, its `background` from `bg_badge_video_circle` to `bg_badge_premium_glass`, and the content description from `@null` to `@string/template_premium_content_description`. The three adapter lines `binding.ivVideoIndicator.isVisible = item.isVideo` became `binding.ivPremiumIndicator.isVisible = item.premium` (Discover, HomeSection) or were dropped entirely (HomeTemplate, which had no premium toggle to keep).

The net behavior is what the spec wants: no video affordance remains, the premium crown occupies the top-left slot, and `playerContainer` plus all playback paths (`loadPoster`, `playerManager`, `clear`, autoplay) are untouched. A repo-wide `grep ivVideoIndicator app/src/main` returns zero matches (confirmed). The only caveat is that spec item 1's phrase "the ImageView block is gone" is not literally true; the view was reused. Given the base layout never had a dedicated premium view, reuse is the sensible path and preserves the exact footprint, so this is accepted as meeting intent (confirmed).

### Home card title constraint re-point

In `item_home_template_card.xml`, `tvTemplateName`'s end constraint moved from `app:layout_constraintEnd_toEndOf="parent"` to `app:layout_constraintEnd_toEndOf="@id/ivTemplateImage"` (confirmed). The plan said the base was `toStartOf="@id/ivVideoIndicator"`; the actual base was `toEndOf="parent"`. Either way the dangling reference to the removed/renamed view id is resolved and the constraint graph is valid. The badge TextView was also retargeted from PRO to NEW (`bg_badge_pro`→`bg_badge_new`, text `home_badge_new`), consistent with premium now being conveyed by the crown icon.

### The two new drawables match the spec exactly

`ic_premium_crown.xml` is a Vector Drawable with a single stroked path: `strokeColor="#E7C878"`, `strokeWidth="2"`, round line cap and join, `fillColor="@android:color/transparent"`, viewport 19×18. No bitmap, no fill — exactly spec item 4.

`bg_badge_premium_glass.xml` is `<inset android:inset="@dimen/stroke_hairline">` wrapping an `oval` shape with `solid #33FFFFFF` and a `stroke` of `@dimen/stroke_hairline` (1dp) `#33FFFFFF` — exactly spec item 5 and compliant with AGENTS.md 3.7's hairline-clipping rule.

Both cards keep `android:visibility="gone"` with `tools:visibility="visible"`, top-left placement, and existing footprints (20dp home via `icon_size_m`, 22dp discover). Padding (`spacing_xs`=4dp home, 5dp discover) keeps the 19×18 crown legible inside the circle. Sizing is unchanged from the prior badge, satisfying spec item 7.

### Bundled refactor and a non-self-contained commit

Beyond the badge swap, commit `cd0ce5b` rewrites the DiffUtil callbacks and bind paths of `DiscoverTemplateAdapter`, `HomeSectionAdapter`, and (lightly) `HomeTemplateAdapter`. The adapters now consume `DiscoverTemplateCardUi` and `HomeSectionUi`/`HomeTemplateCardUi` instead of `ArtTemplate`/`HomeSection`, precompute the NEW badge off the bind path, and add a `getChangePayload` + `onBindViewHolder(..., payloads)` fast path (`PAYLOAD_BADGE` / `PAYLOAD_BADGES`) so a badge-only change avoids a poster reload or autoplay restart. This is coherent, well-commented work, but it is unrelated to "remove the video indicator and update the premium icon" and inflates the diff the reviewer must reason about.

More concretely, those model types are not created by this commit and do not exist at `HEAD~1`. `HomeSectionUi.kt` is untracked on disk and `DiscoverTemplateCardUi` lives in a modified-but-uncommitted `DiscoverUiState.kt`. So the committed tree at `cd0ce5b` references symbols that are absent from that commit; it would not compile in isolation. The recorded `BUILD SUCCESSFUL` is valid for the current working tree (which has these files), and the premium/video behavior under review does compile and run there — hence not a blocker for this UI gate — but the git history is not buildable at this commit. Committing the model files and their producing ViewModel changes alongside the adapters (ideally as a separate refactor commit) would fix both the scope bloat and the build-at-every-commit property.

</details>

<details>
<summary>File map</summary>

- `app/src/main/res/drawable/ic_premium_crown.xml` (new) — gold stroke-only crown vector, 19×18.
- `app/src/main/res/drawable/bg_badge_premium_glass.xml` (new) — translucent oval, hairline stroke, inset-wrapped.
- `app/src/main/res/layout/item_home_template_card.xml` — badge ImageView renamed to `ivPremiumIndicator` + new drawables; title end constraint re-pointed to `ivTemplateImage`; badge TextView → NEW.
- `app/src/main/res/layout/item_discover_template_card.xml` — badge ImageView renamed to `ivPremiumIndicator` + new drawables; badge TextView → NEW.
- `DiscoverTemplateAdapter.kt` — `ivVideoIndicator`→`ivPremiumIndicator` toggle; migrated to `DiscoverTemplateCardUi` + badge payload.
- `HomeSectionAdapter.kt` — `ivVideoIndicator`→`ivPremiumIndicator` toggle; migrated to `HomeSectionUi`/`HomeTemplateCardUi` + badge payloads.
- `HomeTemplateAdapter.kt` — dropped the `ivVideoIndicator` toggle lines.

Full diff: `git show cd0ce5b`. Note: the `*CardUi` model files consumed by the adapters are present in the working tree but outside this commit.

</details>
