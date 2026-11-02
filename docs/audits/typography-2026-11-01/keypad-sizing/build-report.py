from pathlib import Path
import html
import json

root = Path(__file__).resolve().parent
screens = [
    ('goto-dialer', '360 dp phone: prefilled chapter'),
    ('goto-typed', '360 dp phone: entered chapter'),
    ('goto-verse-typed', '360 dp phone: entered verse'),
    ('goto-wide', '600 dp portrait window: capped dialer'),
    ('goto-dialer-landscape', '640 × 360 dp landscape: capped dialer'),
    ('goto-direct', '360 dp phone: Direct tab'),
    ('goto-direct-candidates', '360 dp phone: Direct suggestions'),
    ('goto-direct-landscape', '640 × 360 dp landscape: Direct tab and keyboard'),
]
style = '''
*{box-sizing:border-box}body{margin:0;background:#edf2f5;color:#213747;font:16px/1.5 system-ui,sans-serif}
main{max-width:1150px;margin:auto;padding:28px}header,article,section{padding:24px;margin-bottom:24px;background:#fff;border:1px solid #d4dfe5;border-radius:14px}
h1{font-size:32px;line-height:1.2}h2{font-size:23px}a{color:#006e8b}nav{display:flex;flex-wrap:wrap;gap:10px 18px}
.shots{display:grid;grid-template-columns:1fr 1fr;gap:20px;max-width:800px;margin:20px auto}figure{margin:0;min-width:0}
img{display:block;width:100%;height:auto;max-width:360px;margin:auto}figcaption{text-align:center;font-size:14px;font-weight:600;margin-bottom:8px}
.landscape .shots{grid-template-columns:1fr;max-width:900px}.landscape img{max-width:900px}.raw{display:block;text-align:center;font-size:12px;margin-top:8px}.note{padding:12px;background:#edf5f8;border-left:3px solid #1587a1;font-size:14px}
.table-wrap{overflow-x:auto}table{min-width:480px;width:100%;border-collapse:collapse;font-size:14px}th,td{text-align:left;padding:10px;border-bottom:1px solid #d4dfe5}.tag{font-size:13px;color:#276578;font-weight:600}
@media(max-width:650px){main{padding:12px}header,article,section{padding:16px}.shots{gap:10px}h1{font-size:27px}}
'''
parts = ['<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Responsive Goto controls</title><style>' + style + '</style></head><body><main><header><span class="tag">Goto layout follow-up · 2 November 2026</span><h1>Responsive Goto controls</h1><p>The Compose dialer is centered within a maximum 448 dp content width, plus 16 dp outer margins on each side. Portrait digit buttons are at least five eighths as tall as they are wide, with a 52 dp floor. Landscape buttons retain 52 dp height. The Direct tab scrolls as one vertical list so the reference field stays reachable with the keyboard open.</p><nav><a href="https://github.com/yukuku/androidbible/pull/375">Review PR #375</a><a href="https://androidbible-typography-review.itikku.chatgpt.site/controls/">Full controls and typography review</a></nav></header><section><h2>Sizing rule</h2><p><strong>Portrait button height = max(52 dp, button width × 5/8). Landscape button height = 52 dp.</strong> The 448 dp cap covers the book selector, chapter/verse controls, divider and keypad as one centered column. Its complete width including the 16 dp side margins is at most 480 dp. Existing inner insets remain. The portrait height rule applies to the actual digit button inside its 2 dp padding. Three equal columns and 4 dp gaps remain. Backspace and Ok stay centered in the aligned bottom row.</p><p class="note">Digits: system sans, 28sp Medium (500). Chapter/verse fields: 64 × 64 dp, with 24sp Medium values. Typography, solid active borders, quieter replacement-ready values and keypad input behavior remain as agreed. These layout refinements apply to Compose Goto. Direct prompt and input use 16sp Regular; suggestions use 16sp Medium. The Direct tab keeps its reference parsing, suggestions, submit action and automatic keyboard focus.</p><div class="table-wrap"><table><thead><tr><th>Window width</th><th>Digit width, approximately</th><th>Original fixed height</th><th>Portrait height, approximately</th><th>Landscape height</th></tr></thead><tbody>']
for width in [320, 360, 600, 840]:
    button_width = (min(width, 480) - 80) / 3 - 4
    button_height = max(52, button_width * 5 / 8)
    parts.append(f'<tr><td>{width} dp</td><td>{button_width:.1f} dp</td><td>52 dp</td><td>{button_height:.1f} dp</td><td>52 dp</td></tr>')
parts.append('</tbody></table></div><p>Values are rounded for the table. Android lays out the final dimensions in whole pixels. On wider, shorter windows the taller keypad can require scrolling; the existing vertical scroll container keeps its bottom row reachable.</p></section>')
for screen_id, title in screens:
    parts.append(f'<article id="{screen_id}" class="{"landscape" if screen_id.endswith("landscape") else "portrait"}"><h2>{html.escape(title)}</h2><div class="shots">')
    for phase, label in [('before', 'Before · preceding layout'), ('after', 'After · responsive Goto layout')]:
        src = f'{phase}/{screen_id}.png'
        if not (root / src).exists():
            raise RuntimeError(f'Missing {src}')
        parts.append(f'<figure><figcaption>{label}</figcaption><img src="{src}" alt="{html.escape(title)}: {phase}"><a class="raw" href="{src}">Open raw screenshot</a></figure>')
    parts.append('</div><p class="note">Actual Android captures at 320 dpi and 100% text scale. Phone states use a 720 × 1280 display, wide portrait uses 1200 × 1920, and landscape uses 1280 × 720. Source captures keep their original aspect ratio.</p></article>')
parts.append('<section class="landscape"><h2>Landscape interaction checks</h2><p>The lower keypad rows remain reachable by scrolling. The Direct field accepts a verse reference with the keyboard open; the sample below is entered text, without submitting navigation.</p><div class="shots"><figure><figcaption>Scrolled dialer: all digit rows and actions</figcaption><img src="after/goto-dialer-landscape-bottom.png" alt="Landscape keypad scrolled to the bottom"></figure><figure><figcaption>Direct: typed verse reference above the keyboard</figcaption><img src="after/goto-direct-landscape-typed.png" alt="Typed reference in landscape with keyboard open"></figure></div></section>')
parts.append('<section><h2>Verification and provenance</h2><p>App and test APK builds, focused Goto tests, and Android lint passed. Rendered digit touch targets were checked at 320, 360, 600 and 840 dp widths. The tests also cover the centered width cap, the landscape height exception, entering a reference above a landscape keyboard, chapter/verse replacement and append behavior, tab selection and keyboard transitions.</p><p>The three phone dialer baselines are retained captures of the preceding controls revision. The portrait wide baseline is freshly captured from that build. Landscape dialer uses an interim ratio-only build to show the requested width cap and landscape exception; its Direct tab is still the preceding implementation. The Direct phone baselines and all after states are freshly captured on the same dedicated API 35 QA emulator. Status-bar time can differ independently of the layout. The 200% layout work remains outside scope.</p><nav><a href="manifest.json">Capture provenance</a><a href="unit-tests.json">Focused test results</a><a href="validation.json">Browser checks</a></nav></section></main></body></html>')
(root / 'index.html').write_text(''.join(parts))
(root / 'screens.json').write_text(json.dumps([dict(id=i, title=t) for i, t in screens], indent=2) + '\n')
print('Built', len(screens), 'keypad comparison pairs')
