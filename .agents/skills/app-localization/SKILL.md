---
name: app-localization
description: Write, translate, or review app UI copy using product context and locale-specific terminology. Use when adding or changing user-facing strings, translations, localization guidelines, or app glossaries.
---

# App localization

## Load the app context

- Read the project's AGENTS.md and `docs/localization/product-context.md` and `docs/localization/glossary.md`, or their project-designated equivalents. Keep product facts and vocabulary in the project, not in this reusable skill.
- If context is missing, inspect resources, layouts and action handlers; record supported facts separately from assumptions. Ask only about ambiguity that changes meaning or promises. Continue unambiguous work.
- Determine requested locales and scope. Do not translate every locale or rewrite unrelated copy merely because this skill is active.
- For Android resources, read [references/android.md](references/android.md).

## Translate the experience

- For each string, establish the screen, UI role, action/result, audience, and available space. Read callers rather than inferring intent from the resource key alone.
- Translate intent into natural target-language copy. Preserve meaning, conditions and commercial terms; do not preserve English word order, idioms or capitalization mechanically.
- Use a concrete verb for an action, a noun for a destination, and a completed-state phrase for feedback. Distinguish saving settings, saving content, downloading and resuming playback.
- Follow the app's voice and locale conventions. Prefer concise wording without removing information needed for a decision. Do not invent arbitrary character limits or shrink text to force a translation to fit.
- Error copy should describe the issue and an available next step; do not promise recovery, offline access, refunds, free trials, or capabilities that the flow does not support.
- Localize whole sentences. If a heading is split for visual emphasis, read and translate all pieces together; word order and highlighted spans may differ by language. Propose resource/layout restructuring only when needed and within scope.
- Use glossary entries by concept and context, not blind search-and-replace. Add new recurring terms with locale, meaning, usage and evidence/review status. Existing translations are evidence, not automatically approved terminology.
- Preserve brand names designated as non-translatable. Distinguish language from region; do not silently choose a regional variant for an unspecified locale.

## Verify and hand off

- Check changed keys, placeholders/types, markup, escaping, plurals and non-translatable entries against source resources. Keep variable values intact while allowing grammatical reordering.
- Review related labels, buttons, errors and accessibility states together for consistent terminology and correct meaning.
- When runtime UI is available, inspect the affected screens for wrapping, truncation, font scaling and RTL where applicable. Otherwise report that visual verification was not performed.
- Report changed locales, checks actually run and material unresolved context. Do not claim native-speaker review or runtime validation without evidence.
