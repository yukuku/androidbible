# Responsive Compose Goto controls

[Open the comparison report](index.html). It contains eight before/after screen or state pairs covering phone dialer input, a wide portrait dialer, landscape dialer, and Direct entry with and without suggestions.

The dialer uses a centered content column with a maximum width of 448 dp. The existing 16 dp margins sit outside that cap, so its maximum overall width is 480 dp. Existing inner book/keypad insets remain.

In portrait, actual digit buttons have a minimum height of five eighths of their width, with a 52 dp floor. Landscape keeps 52 dp buttons. Cells retain 2 dp padding per edge and a 4 dp gap. Backspace and Ok remain centered in the same rows. Digit typography stays 28sp Medium; chapter/verse fields retain their agreed typography and appearance.

Direct entry uses one vertical lazy list for the prompt, field and suggestions. The field remains focusable and scrollable above the landscape keyboard. Returning to Direct after scrolling its field away restores the field before requesting focus. Query text, reference parsing and submission behavior remain intact.

## Evidence

Captures use the dedicated Typography_Revision_API35 AVD, Indonesian UI and 100% system text scale. Portrait phone frames are 720 × 1280, wide portrait frames 1200 × 1920, and landscape frames 1280 × 720, all at 320 dpi. The three dialer phone baselines are retained from the preceding controls review. The wide baseline is freshly captured from that build. Landscape dialer uses an interim ratio-only build to isolate the subsequent width cap and landscape exception. Its Direct tab still has the preceding implementation. Direct phone baselines and all after states are fresh captures. Per-file hashes and APK provenance are in [manifest.json](manifest.json). Raw Android images are unchanged.

The existing [controls report](../controls-revision/README.md) retains the wider typography and native rollback evidence. Its original Goto captures predate this follow-up.

## Validation

App/test APK builds, Android lint, and nine focused Goto tests passed. Tests check digit target sizes at 320, 360, 600 and 840 dp, centered width limits, the landscape height exception, scrolling and typing above a landscape keyboard, focus restoration after scrolling away, tab changes, and chapter/verse replacement and append behavior. The 200% text-scale work remains outside scope.

Regenerate the report with `python3 docs/audits/typography-2026-11-01/keypad-sizing/build-report.py` after placing the captures. [Browser verification](validation.json) checks every rendered screenshot against its intrinsic aspect ratio and checks horizontal overflow at desktop and phone widths.
