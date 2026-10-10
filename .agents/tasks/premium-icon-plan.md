# Implementation Plan — Remove video indicator & update premium icon

Goal: (1) Remove the video indicator badge (`ivVideoIndicator`) from the two template cards and all code that toggles it. (2) Replace the premium badge (`ivPremiumIndicator`) with a gold OUTLINED crown inside a translucent glass circle, per Figma node 4063:1907.

Constraints (AGENTS.md): XML + View Binding only, Material 3, no Compose, no new tests. Icons must be Vector Drawables. Any `<shape>` with a `<stroke>` must be wrapped in `<inset android:inset="@dimen/stroke_hairline">` (AGENTS.md 3.7). Reuse existing tokens; do not duplicate.

## Confirmed facts from exploration
- `ivVideoIndicator` references (exactly 5): `item_home_template_card.xml`, `item_discover_template_card.xml`, `DiscoverTemplateAdapter.kt:118`, `HomeTemplateAdapter.kt:85`, `HomeSectionAdapter.kt:169`. Plus one indirect: `tvTemplateName` in `item_home_template_card.xml` constrains `app:layout_constraintEnd_toStartOf="@id/ivVideoIndicator"`.
- `ic_pro_crown.xml` is also used by `item_home_header_container.xml` → KEEP it. Only stop referencing it from the two cards.
- `bg_badge_video_circle.xml` is only used by the two cards, but scope is to leave it untouched and introduce a new `bg_badge_premium_glass.xml`.
- `ic_play_mini` is used elsewhere → KEEP it.
- Tokens: `icon_size_m`=20dp, `spacing_sm`=6dp, `spacing_s`=8dp, `spacing_xs`=4dp, `stroke_hairline`=1dp, `radius_full`=999dp.
- `playerContainer` and all video playback logic must stay. Only the static indicator ImageView is removed.

---

- [ ] 1. Create the new gold outlined crown vector drawable.
      Convert the Figma stroked SVG (viewBox 0 0 19 18, node 4063:1907) to an Android Vector Drawable: stroke-only (no fill), gold `#E7C878`, stroke width 2, round caps/joins.
      Files: `app/src/main/res/drawable/ic_premium_crown.xml` (new)
      Contents:
      ```xml
      <?xml version="1.0" encoding="utf-8"?>
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
          android:width="19dp"
          android:height="18dp"
          android:viewportWidth="19"
          android:viewportHeight="18">
          <path
              android:strokeColor="#E7C878"
              android:strokeWidth="2"
              android:strokeLineCap="round"
              android:strokeLineJoin="round"
              android:fillColor="@android:color/transparent"
              android:pathData="M3.95899,15.75H15.0414M9.15383,2.44953C9.188,2.39074 9.2382,2.34171 9.29918,2.30756C9.36017,2.27341 9.42971,2.2554 9.50055,2.2554C9.5714,2.2554 9.64094,2.27341 9.70193,2.30756C9.76291,2.34171 9.81311,2.39074 9.84728,2.44953L12.1841,6.65244C12.2398,6.74976 12.3176,6.83419 12.4118,6.89965C12.5061,6.96512 12.6145,7.01 12.7292,7.03108C12.8439,7.05216 12.9621,7.04891 13.0754,7.02157C13.1886,6.99423 13.2941,6.94347 13.3842,6.87294L16.7699,4.125C16.8348,4.07491 16.9149,4.04566 16.9986,4.04145C17.0823,4.03724 17.1652,4.05828 17.2354,4.10156C17.3056,4.14483 17.3595,4.2081 17.3894,4.28226C17.4193,4.35642 17.4235,4.43764 17.4016,4.51424L15.1581,12.1986C15.1124,12.3558 15.0137,12.4946 14.8771,12.594C14.7406,12.6933 14.5735,12.7478 14.4014,12.7491H4.60053C4.42822,12.7479 4.26102,12.6936 4.12429,12.5942C3.98757,12.4949 3.88879,12.356 3.84296,12.1986L1.60035,4.51499C1.57838,4.43839 1.58263,4.35717 1.61249,4.28301C1.64235,4.20885 1.69628,4.14558 1.76651,4.10231C1.83674,4.05903 1.91965,4.03799 2.00331,4.0422C2.08697,4.04641 2.16706,4.07566 2.23205,4.12575L5.61695,6.87369C5.70703,6.94422 5.81248,6.99498 5.92572,7.02232C6.03897,7.04966 6.15719,7.05291 6.27192,7.03183C6.38664,7.01075 6.49502,6.96587 6.58927,6.9004C6.68351,6.83494 6.76129,6.75051 6.81702,6.65319L9.15383,2.44953Z" />
      </vector>
      ```
      Verify: covered by the build in step 7 (AAPT compiles the vector; invalid pathData fails the build).

- [ ] 2. Create the translucent glass-circle badge background.
      Oval, solid `#33FFFFFF`, 1dp white `#33FFFFFF` stroke, wrapped in `<inset android:inset="@dimen/stroke_hairline">` per AGENTS.md 3.7 to prevent hairline clipping.
      Files: `app/src/main/res/drawable/bg_badge_premium_glass.xml` (new)
      Contents:
      ```xml
      <?xml version="1.0" encoding="utf-8"?>
      <inset xmlns:android="http://schemas.android.com/apk/res/android"
          android:inset="@dimen/stroke_hairline">
          <shape android:shape="oval">
              <solid android:color="#33FFFFFF" />
              <stroke
                  android:width="@dimen/stroke_hairline"
                  android:color="#33FFFFFF" />
          </shape>
      </inset>
      ```
      Verify: covered by the build in step 7.

- [ ] 3. Edit `item_home_template_card.xml`: remove the video indicator, repoint the title, and update the premium badge.
      - Delete the entire `<ImageView android:id="@+id/ivVideoIndicator" .../>` block (the "Bottom-right Video Indicator" ImageView).
      - On `tvTemplateName`, change `app:layout_constraintEnd_toStartOf="@id/ivVideoIndicator"` to `app:layout_constraintEnd_toEndOf="@id/ivTemplateImage"` so the constraint graph stays valid (name may now span the full width; existing `android:padding="8dp"` keeps trailing space sensible).
      - On `ivPremiumIndicator`: change `android:background="@drawable/bg_badge_video_circle"` → `@drawable/bg_badge_premium_glass`, and `android:src="@drawable/ic_pro_crown"` → `@drawable/ic_premium_crown`. Keep size `@dimen/icon_size_m` (20dp), keep top-left constraints, keep `android:padding="@dimen/spacing_xs"` (4dp) so the 19x18 crown reads clearly inside the 20dp glass circle. Keep `android:visibility="gone"` + `tools:visibility="visible"`.
      Files: `app/src/main/res/layout/item_home_template_card.xml`
      Verify: covered by the build in step 7 (ConstraintLayout with a dangling reference would fail at inflation, but the compile confirms the resource refs resolve); visually confirm no play badge and the title no longer collides with a right-side badge.

- [ ] 4. Edit `item_discover_template_card.xml`: remove the video indicator and update the premium badge.
      - Delete the entire `<ImageView android:id="@+id/ivVideoIndicator" .../>` block (the "Bottom-right Video Indicator" ImageView inside the FrameLayout; `gravity="bottom|end"`, no constraint fixups needed).
      - On `ivPremiumIndicator`: change `android:background="@drawable/bg_badge_video_circle"` → `@drawable/bg_badge_premium_glass`, and `android:src="@drawable/ic_pro_crown"` → `@drawable/ic_premium_crown`. Keep size 22dp, keep `android:padding="5dp"` (crown reads clearly in the 22dp circle), keep top|start gravity, keep `android:visibility="gone"` + `tools:visibility="visible"`.
      Files: `app/src/main/res/layout/item_discover_template_card.xml`
      Verify: covered by the build in step 7.

- [ ] 5. Remove the `ivVideoIndicator` toggle from the three adapters (same change pattern across files). Keep every `ivPremiumIndicator` line and all playerContainer logic.
      - `DiscoverTemplateAdapter.kt`: delete line `binding.ivVideoIndicator.isVisible = item.isVideo` (keep `binding.ivPremiumIndicator.isVisible = item.premium`).
      - `HomeTemplateAdapter.kt`: delete line `binding.ivVideoIndicator.isVisible = item.isVideo` and its "Video indicator badge" comment.
      - `HomeSectionAdapter.kt`: delete line `cardBinding.ivVideoIndicator.isVisible = template.isVideo` (keep the `ivPremiumIndicator` line); trim the comment to reference only the premium icon.
      Files: `app/src/main/java/com/aiart/photo/video/generator/ui/discover/DiscoverTemplateAdapter.kt`, `app/src/main/java/com/aiart/photo/video/generator/ui/home/adapter/HomeTemplateAdapter.kt`, `app/src/main/java/com/aiart/photo/video/generator/ui/home/adapter/HomeSectionAdapter.kt`
      Verify: covered by the build in step 7 (generated View Binding classes no longer expose `ivVideoIndicator`; any remaining reference fails Kotlin compilation).

- [ ] 6. Grep sweep to confirm cleanup.
      Run from repo root:
      - `grep -rn "ivVideoIndicator" app/src/main` → expect ZERO matches.
      - `grep -rn "ic_pro_crown\|bg_badge_video_circle" app/src/main/res/layout/item_home_template_card.xml app/src/main/res/layout/item_discover_template_card.xml` → expect ZERO matches (both cards now reference the new drawables only).
      - `grep -rn "ic_premium_crown\|bg_badge_premium_glass" app/src/main/res/layout` → expect matches in both card layouts.
      Verify: match counts as stated above. (`ic_pro_crown` still present in `item_home_header_container.xml` is expected and correct.)

- [ ] 7. Build the app.
      Files: none (verification build).
      Verify: `./gradlew assembleDebug` from `/Volumes/ExternalSSD/android_apps/893-PHOTO-ART` completes with BUILD SUCCESSFUL. This confirms the new vector + drawable compile, the layouts inflate (constraint refs resolve), and the adapters compile without `ivVideoIndicator`.

## Notes / assumptions
- Kept the existing badge footprint (20dp home / 22dp discover) rather than Figma's 34dp container, per the sizing note — 34dp is oversized on a 109dp-wide card. Inner padding (4dp / 5dp) retained so the outlined crown reads clearly inside the glass circle.
- Backdrop blur approximated with translucent fill + hairline white border; no BlurView added for this small badge (per spec).
- Old `ic_pro_crown.xml` and `bg_badge_video_circle.xml` are left in place (still referenced / out of scope).
