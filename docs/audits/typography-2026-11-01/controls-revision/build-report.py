from pathlib import Path
import html
import json

root = Path(__file__).resolve().parent
original = {s['id']: s for s in json.loads((root.parent / 'comparison/screens.json').read_text())}
core = [
    ('color-sliders', 'Hex color input', 'Compose', 'Warna Hex replaces the longer label. A separate # sits immediately before the centered editable value. Label, prefix and input: 14sp Regular (400).'),
    ('version-details', 'Version details bottom sheet', 'Compose migration', 'Title: 20sp Medium (500). Version code: 16sp Medium. Version name and metadata values: 16sp Regular. Metadata labels and actions: 14sp Medium. Description: 14sp Regular. Remove is a text button with a trash icon at start; Share is the primary button with a share icon at end.'),
    ('goto-dialer', 'Bible keypad: prefilled chapter', 'Compose', 'Book: 16sp Medium (500). Number fields: 24sp Medium. Keypad: 28sp Medium. Active means a solid accent border, even when the next key will replace the value. Inactive fields are dashed. Replacement-ready values remain quieter.'),
    ('goto-typed', 'Bible keypad: entered chapter', 'Compose', 'Number fields: 24sp Medium (500). The synthetic cursor is removed. The active field stays solid; input replacement and appending behavior are preserved.'),
    ('goto-verse-typed', 'Bible keypad: entered verse', 'Compose', 'Number fields: 24sp Medium (500). The solid border follows the active verse field. The entered but inactive chapter is dashed. No synthetic cursor is drawn.'),
    ('legacy-goto-dialer', 'Native Bible keypad: initial', 'Native rollback', 'The custom number boxes and programmatic typography are removed. The original native layout and behavior are restored. Shared theme and keypad style inheritance remains. Actual native sizes and weights are listed below.'),
    ('legacy-goto-typed', 'Native Bible keypad: entered chapter', 'Native rollback', 'The native number display uses its original layout and focus behavior. The added box and cursor are removed. Shared typography styles continue to apply through existing style references.'),
    ('song-selector', 'Song keypad: prefilled number', 'Compose', 'Book: 16sp Medium (500). Value: 24sp Medium. Digits: 28sp Medium. The active value has a solid border while its replacement-ready digits remain quieter.'),
    ('song-input', 'Song keypad: entered number', 'Compose', 'Value: 24sp Medium (500). The solid active border remains; the synthetic cursor is removed. Entered digits retain the brighter foreground.'),
    ('song-four-digits', 'Song keypad: four-digit replacement state', 'Compose', 'Value: 24sp Medium (500). Four digits are replacement-ready because the next keypad digit starts a new code. The field remains solid because it is active; the foreground signals replacement.'),
    ('song-drawer', 'Native song drawer: initial', 'Native rollback', 'The custom number field is removed from the native drawer. Its original XML and View behavior are restored. Existing keypad controls still inherit the shared keypad style.'),
    ('song-drawer-input', 'Native song drawer: entered number', 'Native rollback', 'The native drawer retains its original number presentation. The added synthetic cursor, box and replacement styling are removed. Shared styles remain inherited.'),
]
extra_ids = 'versions,versions-downloaded,legacy-goto-grid,legacy-goto-book-menu,legacy-reader,drawer,bookmarks,notes,highlights,note-editor,search-results,search-filter,plan,plan-list,plan-drawer,about,fonts,settings,settings-display,settings-usage,settings-copy,settings-transfer,legacy-sign-in,bookmark-editor,label-chooser,progress,verse-dialog,xref,report-verse,pdb-options,debug-sync,song-search,devotion'.split(',')
specs = [dict(id=i, title=t, category=c, note=n) for i, t, c, n in core]
for i in extra_ids:
    source = original.get(i, {})
    if i == 'bookmarks': source = dict(source, title='Bookmarks')
    if i == 'note-editor': source = dict(source, title='New note editor (empty)')
    specs.append(dict(id=i, title=source.get('title', i.replace('-', ' ').capitalize()), category='Native rollback', note='Direct XML/View typography changes are reverted to develop. Existing shared style and theme inheritance remains. Before/after native measurements below record the actual visible TextViews. Reading-content typography retains its own appearance settings.'))

style = '*{box-sizing:border-box}body{margin:0;background:#edf2f5;color:#213747;font:16px/1.5 system-ui,sans-serif}main{max-width:1150px;margin:auto;padding:28px}header,article,.panel{padding:24px;margin-bottom:24px;background:white;border:1px solid #d4dfe5;border-radius:14px}h1{font-size:32px;line-height:1.2}h2{font-size:23px}a{color:#006e8b}nav{display:flex;flex-wrap:wrap;gap:10px 18px}.shots{display:grid;grid-template-columns:1fr 1fr;gap:20px;max-width:800px;margin:20px auto}figure{margin:0;min-width:0}img{display:block;width:100%;height:auto;max-width:360px;margin:auto}figcaption{text-align:center;font-size:14px;font-weight:600;margin-bottom:8px}.raw{display:block;text-align:center;font-size:12px;margin-top:8px}.font{padding:12px;background:#edf5f8;border-left:3px solid #1587a1;font-size:14px}.tag{font-size:13px;color:#276578;font-weight:600}table{width:100%;border-collapse:collapse;font-size:13px;table-layout:fixed}th,td{text-align:left;padding:8px;border-bottom:1px solid #d4dfe5;overflow-wrap:anywhere}details{margin-top:16px}summary{cursor:pointer}.small{font-size:14px}@media(max-width:650px){main{padding:12px}header,article,.panel{padding:16px}.shots{gap:10px}h1{font-size:27px}}'
def esc(value):
    return html.escape(' '.join(value.split()))
parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>AndroidBible controls and native rollback</title><style>' + style + '</style></head><body><main><header><span class="tag">Latest review • 2 November 2026</span><h1>Compose controls, version details, and native rollback</h1><p>The latest scope keeps the shared typography styles and Compose refinements. Direct native XML/View changes have been reverted. Older reports remain historical snapshots.</p><nav><a href="https://github.com/yukuku/androidbible/pull/375">Review PR #375</a><a href="https://androidbible-typography-review.itikku.chatgpt.site/sentence-case/">Earlier sentence-case snapshot</a><a href="https://androidbible-typography-review.itikku.chatgpt.site/accessibility/">Earlier Medium-weight snapshot</a><a href="https://androidbible-typography-review.itikku.chatgpt.site/">Initial proposal snapshot</a></nav><p class="font"><strong>Goto layout follow-up:</strong> <a href="https://androidbible-typography-review.itikku.chatgpt.site/keypad/">Review the latest portrait and landscape captures</a>. The dialer is capped at 448 dp content width, uses taller portrait digits and compact landscape digits, and Direct entry scrolls above the keyboard. The original Goto captures below predate that follow-up.</p></header><section class="panel"><h2>Current behavior</h2><ul><li>The hex label is “Warna Hex”; # is adjacent to the left of the centered input. The label is simplified consistently in all 31 UI locales.</li><li>Version details use a Compose bottom sheet with scrollable information and a fixed action row. Installed removable versions offer Remove at start and primary Share at end, both with icons. Download and update actions remain available where applicable. Built-in versions retain their protection from removal and unsupported file sharing.</li><li>Compose Bible/song number fields use a solid border when active, a dashed border when inactive, and quieter replacement-ready digits. The synthetic cursor is removed.</li><li>55 native files match develop exactly. Two native helpers are removed. Existing shared themes, text appearances and keypad styles remain, so native controls can still inherit their effects. Explicit original native sizes and casing remain where the original layout defines them.</li></ul><p>All comparison images are untouched Android captures at 720 × 1280, 320dpi and 100% system font size. The dedicated AVD uses Indonesian UI, public BIMK/KJ content, and local marker fixtures. No delete, share, sign-in, feedback or report action was submitted. The PDB conversion screen is a component fixture.</p><p>Native font tables record each TextView’s base font; inline spans can override individual runs. They list visible TextViews across the app’s windows; underlying activity text can also be present behind a modal sheet. Compose font annotations describe the named component. Keyboard, status bar and network content can differ independently of typography. The 200% layout work remains outside scope.</p><nav>']
parts += [f'<a href="#{s["id"]}">{esc(s["title"])}</a>' for s in specs]
parts.append('</nav></section>')
for s in specs:
    i = s['id']
    caveats = {'legacy-reader': 'The baseline contains a debug-only leak-analysis overlay. Reading content retains its own font settings; the native rollback is also verified by exact source comparison.', 'settings-display': 'A debug-only leak-analysis toast overlays part of the baseline. The current view is recaptured without that transient overlay.', 'verse-dialog': 'This is the external floating verse activity. The baseline has an unrelated system permission prompt behind it. The foreground verse dialog is the component being compared; the current frame is recaptured without that prompt.', 'note-editor': 'This is an empty new-note editor. It verifies layout, not the appearance of entered note text.'}
    if i in caveats: s['note'] += ' ' + caveats[i]
    for phase in ['before', 'after']:
        if not (root / phase / (i + '.png')).exists():
            raise RuntimeError(f'Missing {phase}/{i}.png')
    parts.append(f'<article id="{i}"><span class="tag">{esc(s["category"])}</span><h2>{esc(s["title"])}</h2><p class="font">{esc(s["note"])}</p><div class="shots">')
    for phase, label in [('before', 'Before · preceding review build'), ('after', 'After · current scope')]:
        src = f'{phase}/{i}.png'
        parts.append(f'<figure><figcaption>{label}</figcaption><img src="{src}" alt="{esc(s["title"])}: {phase}" width="720" height="1280"><a class="raw" href="{src}">Open raw screenshot</a></figure>')
    parts.append('</div>')
    if s['category'] == 'Native rollback':
        measured = {}
        for phase in ['before', 'after']:
            file = root / phase / (i + '-native-fonts.json')
            values = json.loads(file.read_text()) if file.exists() else []
            fields = {}
            for value in values:
                if 'LeakCanary' in value['text'] or 'Leak detected' in value['text']: continue
                key = value['id'] or value['text'][:35]
                if key not in fields:
                    fields[key] = value
            measured[phase] = fields
        rows = []
        for key in list(dict.fromkeys(list(measured['after']) + list(measured['before'])))[:12]:
            value = measured['after'].get(key) or measured['before'][key]
            label = value['id'].split(':id/')[-1] + ': ' + value['text'][:45]
            cells = []
            for phase in ['before', 'after']:
                font = measured[phase].get(key)
                cells.append(f"{font['sizeSp']:g}sp · {font['weight']}" if font else 'Not recorded')
            rows.append('<tr><td>' + esc(label) + '</td><td>' + cells[0] + '</td><td>' + cells[1] + '</td></tr>')
        parts.append('<table><thead><tr><th>Native usage</th><th>Before: size · weight</th><th>After: size · weight</th></tr></thead><tbody>' + ''.join(rows) + '</tbody></table>')
    for phase in ['before', 'after']:
        fontfile = root / phase / (i + '-native-fonts.json')
        if fontfile.exists():
            values = json.loads(fontfile.read_text())
            rows = []
            seen = set()
            for v in values:
                if 'LeakCanary' in v['text'] or 'Leak detected' in v['text']: continue
                key = (v['id'], v['text'], round(v['sizeSp'], 2), v['weight'], v['italic'])
                if key in seen:
                    continue
                seen.add(key)
                rows.append(f'<tr><td>{esc(v["text"])}</td><td>{esc(v["id"].split(":id/")[-1])}</td><td>{v["sizeSp"]:g}sp · {v["weight"]}' + (' · Italic' if v['italic'] else '') + '</td></tr>')
            parts.append(f'<details><summary>{phase.capitalize()} · measured native font usages ({len(rows)})</summary><p class="small">Font weight 400 = Regular, 500 = Medium, 700 = Bold. Family: system/UI fonts, except explicit reading fonts and font previews.</p><table><thead><tr><th>Visible text</th><th>View</th><th>Size and weight</th></tr></thead><tbody>' + ''.join(rows) + f'</tbody></table><a href="{phase}/{i}-native-fonts.json">Raw measurements</a></details>')
    parts.append('</article>')
parts.append('<section class="panel"><h2>Evidence and validation</h2><p><a href="native-rollback.json">Exact native rollback inventory</a> · <a href="manifest.json">Capture provenance</a> · <a href="unit-tests.json">Focused tests</a> · <a href="lint.json">Lint</a> · <a href="validation.json">Browser checks</a></p><p>The sheet tests cover independent action callbacks, action eligibility, start/end placement, and footer visibility with long descriptions in a constrained viewport. Device screenshots verify the actual sheet and keypad appearance. These checks do not replace human low-vision usability review.</p></section></main></body></html>')
(root / 'screens.json').write_text(json.dumps(specs, indent=2) + '\n')
(root / 'index.html').write_text(''.join(parts))
print('Built', len(specs), 'pairs')
