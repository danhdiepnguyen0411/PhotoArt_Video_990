# Home NEW badge (pass 3): client first-seen rule still in place of server createdAt

This pass re-reviews the badge work. Commit `4dbb346` (made by another session) now holds all of it, so the review reads HEAD instead of the working tree. The uncommitted diff now has only unrelated photo-guide work, plus a restyle of the Discover NEW pill in `item_discover_template_card.xml`. Two of the earlier non-blocking findings are fixed. The Discover and SelectImageTemplate New tabs now filter by `isNewCandidate`, so opened templates stay in the tab, and SelectImageTemplate collects badge state. The report now attributes the parallel changes, and the `Log.d` in `syncCatalog` is gone. The two blocking findings are unchanged. NEW is still driven by `template_badge_state.first_seen_at` with a BASELINE first sync, and `createdAt` parsing is still `optLong`. The coder says this follows a "Quyết định user (vòng 3)", meaning a third-round user decision. No such decision appears in the user's messages, which remain authoritative: "ai có trả ra createdAt" and "icon video đưa xuống góc dưới bên phải".

Watch for:
- (confirmed, blocking) The first-seen/baseline mechanism the brief requires to be DROPPED is still shipped.
- (confirmed, blocking) `createdAt` is never parsed beyond `optLong`.
- (confirmed, needs user decision) The video icon is still removed instead of moved to bottom-right.

**Verdict**: NEEDS_CHANGES

## High-level view

The coder's evidence is plausible. 242 cached items carry no date key, and the disk cache stores the raw SDK `JsonElement`, so nothing in the app strips the field. Under the brief, that outcome means reporting a warning and not inventing a fallback. The code instead keeps the rejected client mechanism and attributes it to a user decision that is not in the transcript. Going back to the server rule means narrowing the table to opened codes and making `NewBadgePolicy` read `template.createdAt`. With today's data that shows no NEW at all, and the user has to be told so.

The surrounding plumbing is still good and does not need rework. That covers the singleton `StateFlow` repo, `markOpened` from Home and Discover, the `ListAdapter` with `PAYLOAD_BADGES`, max-1-per-section in backend order, EXCLUSIVE gated on premium, the crown icon toggled both ways, and the localized `home_badge_new`. The only parts that change are the policy's candidate rule, the entity and DAO shape, and the migration SQL (8→9 is unshipped, so it can be redefined).

The video icon conflict is unchanged. The stale header comment is fixed and now says "no video indicator". That records the deviation but does not resolve it: user message 2, item 3 explicitly asks for the video icon at bottom-right.

<details>
<summary>Issues (4)</summary>

1. **First-seen fallback still shipped** (blocking): `NewBadgePolicy.isNewCandidate` reads `state.firstSeen` against `BASELINE`, and `syncCatalog` writes `first_seen_at`. Fix:
   - Drop `syncCatalog`, `firstSeen`, and `BASELINE`.
   - Use `createdAt > 0 && (now - createdAt).coerceAtLeast(0) <= 7d`.
   - Shrink the entity to `template_code PK, opened_at NOT NULL` with a matching `migration8To9`, and expose `StateFlow<Set<String>>`.
   - Escalate the missing field to the user instead of citing an unrecorded "round 3" decision.
2. **createdAt parsing not hardened** (blocking): `ArtTemplateRepositoryImpl.kt:567` is still `obj.optLong("createdAt", 0L)`, so an ISO-8601 string becomes 0. Parse number, numeric string, and ISO (`SimpleDateFormat`, UTC, `Locale.US`, created once per catalog parse; no `java.time`) under `createdAt` and `created_at`.
3. **Video icon removed, not moved** (needs user decision): the user asked for it at bottom-right. `ivVideoIndicator` does not exist in either card layout. Ask the user whether commit `cd0ce5b` overrides that decision, or restore the icon at bottom-right with its existing visibility rule.
4. **Hardcoded spacing in the Discover NEW pill restyle** (non-blocking): the uncommitted `item_discover_template_card.xml` diff replaces `@dimen/template_badge_height` with literal `6dp`/`2dp` padding and a `6dp` margin. Use the same tokens as the Home card, or snap to `@dimen/spacing_*`.

</details>

<details><summary>Details</summary>

### Server-driven rule versus the shipped first-seen rule

```kotlin
// domain/policy/NewBadgePolicy.kt
fun isNewCandidate(templateCode: String, state: TemplateBadgeState, nowMillis: Long): Boolean {
    val firstSeen = state.firstSeen[templateCode] ?: return false
    if (firstSeen <= BASELINE) return false
```

`TemplateBadgeRepositoryImpl.syncCatalog` writes BASELINE (0) on the first sync and `now` afterwards. It is called from `HomeViewModel` and `BaseDiscoverViewModel.loadData`. `ArtTemplate.createdAt` is never read for NEW anywhere (confirmed). The report's device test proves the mechanism works, with seeded deletes re-inserted at `now`. That shows the mechanism is internally consistent, but it is the mechanism the user replaced. Its user-visible failure modes are still present: a fresh install shows zero NEW, a re-added template never shows NEW again, and NEW differs per device. The parts that already fit the target design can stay: `resolveNewFlags`, the opened-set merge with `pendingOpened`, and the payload pipeline. Only the candidate predicate and the storage shape change.

`migration8To9` currently matches its entity character-for-character (confirmed against the report's `AppDatabase_Impl` check). Once the entity is narrowed, the CREATE string has to be regenerated. The v8→v9 seeded-favorites device check also has to be repeated, because `fallbackToDestructiveMigration()` is on.

### createdAt parsing

`parseTemplatesFromElement` at line 567 is unchanged from before the task. If the backend adds `createdAt` as a Jackson-style ISO string, `optLong` returns 0 and NEW never appears (confirmed for the code path; the string format is likely). The sorted use of `createdAt` in `buildHeroBanners` (line 389) predates this task (`619bfd1`), so it is out of scope for the "no client sort" check.

### Discover

The `rank` and `categoryCode == "NEW"` hacks are gone. The New tab filters by candidacy in backend order without sorting, and both ViewModels now re-filter when badge state loads. These were findings 4 and 5 last pass, and both are now fixed. When finding 1 is fixed, these filters will call the `createdAt`-based `isNewCandidate` automatically.

</details>

<details>
<summary>File map</summary>

- `domain/policy/NewBadgePolicy.kt`: first-seen candidate rule, max-1 in backend order (HEAD)
- `data/repository/TemplateBadgeRepositoryImpl.kt`, `data/local/entity/TemplateBadgeStateEntity.kt`, `data/local/dao/TemplateBadgeStateDao.kt`, `di/DatabaseModule.kt`: `template_badge_state` table and v9 migration (HEAD)
- `data/repository/ArtTemplateRepositoryImpl.kt`: `createdAt` still `optLong`; unrelated `AI_TOOL` / `isFeaturedCategory` changes (HEAD)
- `ui/discover/BaseDiscoverViewModel.kt`, `SelectImageTemplateViewModel.kt`: New tab via `isNewCandidate` and a state collector (HEAD)
- `res/layout/item_home_template_card.xml`: header comment updated (HEAD)
- `res/layout/item_discover_template_card.xml`: uncommitted NEW pill restyle with literal dp values
- Uncommitted, unrelated: `PhotoGuideBottomSheet`, `bottom_sheet_photo_guide.xml`, guide webp assets, `themes.xml`, and four activities

Full diff: `git show 4dbb346` plus `git diff`.

</details>
