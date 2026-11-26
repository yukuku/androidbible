# Bilingual design mockups

Created: 2026-09-28. These are proposed layouts with an expandable reader control, not screenshots of a
shipped feature or a functional app prototype. They illustrate the backlog;
they do not change any entry's Proposed status.

## Open the mockups

- [English HTML](mockups/english.html)
- [Indonesian HTML](mockups/indonesian.html)

Open either HTML file by itself in a browser. Each embeds its own CSS and icon
SVG and needs no adjacent files, images, scripts, fonts, or network requests. GitHub displays HTML
source rather than running it; the rendered overview images below are provided
for PR review. The reader plan icon opens and closes a native HTML disclosure; the other
phone controls remain static illustrations. In-page navigation also works.
The PNG overviews are for Markdown review only; neither HTML file loads them.

| English overview | Indonesian overview |
| --- | --- |
| ![Six proposed English screens](mockups/english-overview.png) | ![Six proposed Indonesian screens](mockups/indonesian-overview.png) |

## Familiarity is a design constraint

Existing users should recognize this as the same reading-plan feature. Preserve
the dark plan surface, blue accents, app drawer entry, plan selector, day arrows,
clickable date, passage-first list, and right-side completion checkboxes. Keep
the reader's usual chapter navigation, version/audio controls, and typography.
The schematic icons here do not prescribe replacements for existing app icons.

Change hierarchy and behavior where the audit found a problem; do not introduce
bottom tabs, a new home dashboard, streaks, decorative cards for every passage,
or gesture-only controls merely to make the screen look new.

| Existing landmark | Proposed treatment | Familiarity safeguard |
| --- | --- | --- |
| Toolbar plan dropdown | Full title and dropdown immediately below the toolbar | Keep the selector near the top, show the same plan titles, and preserve current selection. Do not rename downloaded plans. |
| Previous/date/next row | Retain arrows and date; add day total and flexible height | Keep order and meanings. The date still opens date navigation. |
| Reference plus right checkbox | Keep the daily row arrangement everywhere passages appear | References open reading; separate checkboxes toggle completion. Do not make the whole row a completion toggle. |
| Show details / Tampilkan daftar baca | Keep a visible reading-list action on the daily screen; open a dedicated day list | Retain the Indonesian label exactly. Clarify the English label to Show reading list. Back returns to the same day and scroll position. |
| Plus/download and delete icons | Move management into a labeled overflow menu | Include Download a reading plan / Unduh jadwal baca and Remove downloaded plan / Hapus jadwal yang diunduh. Keep the familiar download wording. |
| Date-menu recovery and drawer Restart | Consolidate management without abruptly removing old routes | During the transition, keep the old date-menu and drawer actions as aliases to the same new dialogs. Decide their later removal only after usability validation. |
| Ordinary Bible reader | Add a small contextual plan icon to the existing toolbar | No persistent bottom strip or new toolbar row. Reveal a temporary panel only on request. Back still returns to the plan. |
| Checkmarks after finishing | Keep the completed list visible | Add calm completion feedback and an optional next-day action; never auto-advance. |

Keep familiar English and Indonesian feature names, Reading Plan and Jadwal
baca. The catalogue's original `Bible Plan Gospels` title is deliberately
unchanged in both mockups. The Indonesian description is proposed localized
curated copy, not evidence that arbitrary catalogue content is translated.

## Screen and behavior mapping

| Screen | Backlog | Intended behavior |
| --- | --- | --- |
| Daily reading | RP2-001, RP2-003, RP2-005 | Show daily counts and a named Continue action. Preserve arrows, date, switcher, reference rows, and explicit checks. Today remains directly available. |
| Reader session | RP2-002 | Keep the plan icon collapsed while reading. Tap to reveal assigned passage, day, sequence, and explicit completion. Closing the panel does not complete anything. |
| All days | RP2-003, RP2-004, RP2-007 | Show a compact date/status list. Selecting a day opens the same daily passage layout; return preserves position. The mockup shows a sample, not the entire plan. |
| Returning after a break | RP2-001, RP2-008 | Explain earlier unfinished readings and distinguish next unread from today's schedule. The lower panel represents the confirmation opened by Adjust schedule, not a permanent second dashboard. |
| Finished day | RP2-006 | Keep checked rows, confirm completion, and offer optional navigation. The whole-plan completion state remains specified in the backlog and is not illustrated here. |
| Plan preview | RP2-009 | Keep the existing catalogue route and light catalogue surface. Preview the sample day and start date before Start plan. This does not require a native catalogue rewrite. |

The daily state uses September 27, 2026, with Matthew 1 checked and Matthew 2
and 3 unread. The finished state has all three checked. Counts use 89 assigned
readings in the example plan; the UI must calculate actual counts from each
plan, never hardcode these examples.

The recovery example advances today to September 29 while only Matthew 1 is
complete. The two remaining readings from Day 1 plus three from Day 2 make
five earlier unfinished readings. Shifting Day 1 to September 29 moves the
30-day plan's finish from October 26 to October 28, preserving the checkmark.
It does not mark the missed passages read or change their sequence.

## RP2-002: Protect the reading area

The default reader has only a small checklist icon in the existing toolbar,
with a 48 px illustrative hit area and an accessible label. It adds no permanent
height below the toolbar and no persistent panel over verses. Tap the icon to
open the temporary context/completion panel; tap again to close it. The native
disclosure supports keyboard activation. Completion remains an explicit action
inside the panel, never an effect of opening or dismissing it.

The app implementation should close the panel on Back or outside tap, return
focus to the icon, and close it after completing and advancing. These behaviors
are requirements, not simulated by the static completion labels in this HTML.
Preserve chapter navigation, version, audio, and search controls. If the icon
cannot fit at a narrow width or large font size without squeezing the reference
or touch targets, expose the same labeled action through the reader navigation
drawer instead of adding a second toolbar row or reducing the reading area.
The icon is contextual to an active plan session; ordinary reading does not
need an extra control. Validate discoverability with current users.

| English: panel opened on request | Indonesian: panel opened on request |
| --- | --- |
| ![English plan controls revealed by the icon](mockups/english-reader-expanded.png) | ![Indonesian plan controls revealed by the icon](mockups/indonesian-reader-expanded.png) |

## English and Indonesian copy

| English | Indonesian | Meaning |
| --- | --- | --- |
| Continue · Matthew 2 | Lanjutkan · Matius 2 | Open the named unfinished passage, without changing completion. |
| Show reading list | Tampilkan daftar baca | Open the All days destination. |
| First unread | Ke yang belum dibaca | Navigate to the first unfinished assignment. |
| Mark complete & next | Tandai selesai & lanjut | Complete only the active assignment and continue within the same day. |
| Adjust schedule | Atur jadwal | Preview date changes while retaining progress. |
| Move schedule | Geser jadwal | Apply the previewed date change. |
| Today's readings are complete | Bacaan hari ini selesai | Confirm daily completion without navigating away. |
| Start plan | Mulai jadwal baca | Start the selected plan on the chosen date. |

Keep these translations semantically aligned. Date examples deliberately use
English month-first formatting and Indonesian day-first formatting. Reference
names come from the active Bible version in the real app. Reader text is an
explicit placeholder, not a fabricated quotation or a proposed Bible version.

## Review and implementation limits

These layouts are illustrative and responsive, with flexible heights. They do
not prove native Android touch-target dimensions, TalkBack traversal, rotation
restoration, sync correctness, or behavior at the system's largest text size.
Those acceptance checks remain in RP2-004, RP2-010, and RP2-011.

Before accepting the navigation changes, ask existing users in both languages
to switch a plan, open a passage, mark it complete, find another day, and adjust
a schedule. Compare task completion and wrong-action taps with the existing
screen; record where old navigation aliases are used. Preserve progress and
selection through any transition. Keep the existing audit screenshots as the
historical baseline rather than replacing them with these proposals.
