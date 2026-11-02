const fs = require('fs');
const path = require('path');
const root = __dirname;
const screens = JSON.parse(fs.readFileSync(path.join(root, 'screens.json')));
const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function frame(s, phase) {
    const src = `${phase}/${s.id}.png`;
    if (!fs.existsSync(path.join(root, src))) throw Error(`Missing ${src}`);
    const annotations = phase === 'after' ? s.annotations.map((a, i) => {
        if (!a.box) return '';
        const [x, y, w, h] = a.box;
        return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="6" fill="none" stroke="#ffbc72" stroke-width="3"/><rect x="${x}" y="${y-28}" width="32" height="28" rx="3" fill="#ffbc72"/><text x="${x+16}" y="${y-7}" text-anchor="middle" font-family="sans-serif" font-size="24" font-weight="700" fill="#33200b">${i+1}</text>`;
    }).join('') : '';
    return `<figure><figcaption>${phase === 'before' ? 'BEFORE · initial proposal' : 'AFTER · accessibility revision'}</figcaption><div class="shot"><img src="${src}" alt="${esc(s.title)}: ${phase}">${annotations ? `<svg viewBox="0 0 720 1280" aria-hidden="true">${annotations}</svg>` : ''}</div><a class="raw-link" href="${src}">Open raw screenshot</a></figure>`;
}
const nav = `<nav class="capture-index">${screens.map(s => `<a href="#${s.id}">${esc(s.title)}</a>`).join('')}</nav>`;
const content = nav + screens.map(s => `<article class="evidence-card" id="${s.id}"><h3>${esc(s.title)}</h3><p class="evidence-note">${esc(s.note)}</p><div class="evidence-pair ${s.before ? '' : 'single'}">${s.before ? frame(s, 'before') : ''}${frame(s, 'after')}</div><ol class="annotation-list">${s.annotations.map((a, i) => `<li><span class="badge">${i+1}</span><strong>${esc(a.label)}</strong>: ${esc(a.font)}${a.beforeFont ? `<br><span style="margin-left:32px;color:#4b6573">Before: ${esc(a.beforeFont)}</span>` : ""}</li>`).join('')}</ol></article>`).join('');
const file = path.join(root, 'index.html');
let html = fs.readFileSync(file, 'utf8');
html = html.replace(/<!-- CAPTURES_START -->[\s\S]*?<!-- CAPTURES_END -->/, `<!-- CAPTURES_START -->${content}<!-- CAPTURES_END -->`);
fs.writeFileSync(file, html);
console.log(JSON.stringify({screens:screens.length,pairs:screens.filter(s=>s.before).length}));
