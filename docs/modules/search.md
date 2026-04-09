# Search Module

## Overview

Full-text search across Bible verses with token-based intersection, book/testament filtering, and search history.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/SearchActivity.kt` — Search UI with history autocomplete
- `Alkitab/src/main/java/yuku/alkitab/base/util/SearchEngine.java` — Core search engine (grep-based, token intersection)
- `Alkitab/src/main/java/yuku/alkitab/base/util/QueryTokenizer.java` — Tokenizes queries with quote and plus-sign support

## Query Tokenization

`QueryTokenizer` supports:
- Space-separated terms — each term must match independently (AND logic)
- `"quoted phrases"` — treated as a single token (supports Unicode curly quotes)
- `+multi +word` — plus-prefixed words treated as a single phrase

## Search Algorithm

`SearchEngine` performs a grep-based search:
1. Tokenize query via `QueryTokenizer`
2. For each book (or filtered subset):
   - Load chapter text via `Version.loadChapterText()`
   - Strip formatting codes via `FormattedVerseText.removeSpecialCodes()`
   - Check each verse against all tokens (intersection — all must match)
3. Results collected with verse references and matching text

## Filtering

- **Testament**: Old Testament only, New Testament only, or all
- **Single book**: Restrict search to a specific Bible book

## Search History

Recent searches are stored in preferences and shown as autocomplete suggestions. History is managed as a bounded list.

## Result Display

Results show verse reference and text with keyword highlighting. `TextColorUtil` adapts highlight colors to the current theme's background brightness. Users can select results to navigate to that verse in `IsiActivity`.
