# Search Module

## Overview

Full-text search across Bible verses with token-based intersection, book/testament filtering, and search history.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/SearchActivity.kt` — Search UI with history autocomplete
- `Alkitab/src/main/java/yuku/alkitab/base/util/SearchEngine.kt` — Core search engine (grep-based, token intersection)
- `Alkitab/src/main/java/yuku/alkitab/base/util/QueryTokenizer.kt` — Tokenizes queries with quote and plus-sign support
- `Alkitab/src/main/java/yuku/alkitab/base/util/SearchEngineQuery.kt` — Data class holding query string and optional book filter (`bookIds: SparseBooleanArray?`)

## Query Tokenization

`QueryTokenizer.tokenize(query)` splits a query string into tokens using these rules:

1. **Space-separated terms** — each term becomes a separate token. All tokens must match (AND logic).
   - Example: `love grace` → tokens `["love", "grace"]`

2. **Quoted phrases** — text inside quotes becomes a single token with a `+` prefix (triggers whole-word matching). Supports ASCII `"..."` and Unicode curly quotes `\u201c...\u201d` (and reversed).
   - Example: `"in the beginning"` → token `["+in the beginning"]`

3. **Plus-prefixed words** — a `+` before a word triggers whole-word matching for that word. Multiple consecutive `+word` tokens are joined into a single multi-word token.
   - Example: `+love` → token `["+love"]` (whole-word: won't match "beloved")
   - Example: `+in +the` → token `["+in the"]` (multi-word phrase)

### Matching Modes

The `+` prefix on a token determines the matching mode:

| Token | Mode | Behavior |
|-------|------|----------|
| `love` | Substring | Matches "love", "beloved", "loves", "gloved" |
| `+love` | Whole-word | Matches only "love" surrounded by non-letter/non-digit chars or string boundaries |
| `+"in the beginning"` | Multi-word phrase | Whole-word match of each word in sequence, tolerant of formatting tags and punctuation between words |

### Multi-word Token Processing

When a `+`-prefixed token contains multiple words, `QueryTokenizer.tokenizeMultiwordToken()` splits it on word boundaries (`[\p{javaLetterOrDigit}'-]+`). The search then uses `indexOfWholeMultiword()` which:
- Finds each word as a whole-word match
- Strips inline formatting tags (`@<...@>...@/`) between words
- Skips punctuation between words
- Requires all words to appear in sequence within the same verse

## Search Algorithm

`SearchEngine.searchByGrep()` performs a grep-based search across all verses:

1. **Tokenize** the query via `QueryTokenizer.tokenize()`
2. **Sort tokens** by length (longest first), then alphabetically — this is an optimization so the most selective token is searched first
3. **Remove duplicate tokens**
4. **For each token sequentially:**
   - Iterate over all books (or the filtered subset from `SearchEngineQuery.bookIds`)
   - For each book, iterate over all chapters
   - Load chapter text (lowercased, formatting codes intact) via `version.loadChapterTextLowercasedWithoutSplit(book, chapter_1)`
   - For each verse in the chapter, check if the token matches:
     - Substring tokens: `indexOf()` on lowercased text
     - Whole-word tokens: `indexOfWholeWord()` — checks that match boundaries are non-letter/non-digit
     - Multi-word tokens: `indexOfWholeMultiword()` — sequential word matching with tag/punctuation tolerance
   - Collect matching verse ARIs into an `IntArrayList`
   - **If this is not the first token**, intersect the new results with the previous token's results (only verses matching ALL tokens survive)
5. Return final intersection as the result set

Key detail: the algorithm searches **one token at a time across all verses**, then intersects results — it does NOT check all tokens per verse. This means the first (longest) token prunes the result set early, making subsequent token searches faster.

## Filtering

- **Testament**: Old Testament only, New Testament only, or all
- **Single book**: Restrict search to a specific Bible book
- Filters are passed via `SearchEngineQuery.bookIds` (`SparseBooleanArray` mapping bookId → included)

## Search History

Recent searches are stored as JSON in preferences (`Prefkey.searchHistory`), bounded to a maximum of 20 entries. New searches are added to the front; duplicates are removed before re-adding. History entries appear as autocomplete suggestions in `SearchActivity`.

## Result Display

Results show verse reference and text with keyword highlighting. `TextColorUtil` adapts highlight colors to the current theme's background brightness. Users can select results to navigate to that verse in `IsiActivity`.
