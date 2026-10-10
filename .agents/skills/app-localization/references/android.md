# Android localization

- Follow the app's existing resource structure and locale mapping. Default `values/` is the fallback; inspect its language instead of assuming English.
- Keep resource names stable unless a semantic split is necessary. Preserve argument index, type and literal percent escapes (`%1$s`, `%2$d`, `%%`); positional arguments may be reordered. When introducing multiple arguments, use indexed placeholders and update callers consistently.
- Preserve XML/Android escaping, styling tags, annotations and XLIFF placeholders where present. Validate with the Android resource compiler when changing resources; XML parsing alone does not validate Android escapes or format strings.
- Respect `translatable="false"`. Do not translate identifiers, URLs, format syntax or API enum values as UI copy.
- Use complete formatted resources rather than concatenated sentence fragments. Use quantity resources when grammar depends on count; plural categories follow each locale, not the source language. Ensure `other` exists and callers use quantity-aware APIs.
- Format dates, numbers and currencies with locale-aware APIs. For real subscription prices and billing periods, use billing product data; do not convert or invent prices while translating sample strings.
- Avoid carrying English hard line breaks or manual hyphenation into translations. Check space in actual XML layouts, including accessibility descriptions and dynamic text.
- For RTL locales inspect start/end alignment, directional icons and mixed-direction numbers/text. Do not reverse strings manually or mirror nondirectional assets.
- Check key coverage within requested scope and distinguish intentional fallback from missing translations. Run relevant resource/build checks when resources change; follow project testing rules and do not generate UI tests just for a copy edit.
