# Bible content licenses and provenance

Status date: 2026-09-05. This is an engineering release gate and provenance record, not a substitute for legal advice.

## Decision summary

The Yuku/Quick Bible application code can be modified, redistributed, and monetized under Apache License 2.0, provided its license and required notices are preserved. Bible text and audio are separate works: the app's open-source license does not grant rights to TB, TB2, or any recording.

KJV, WEB, and ASV may be used commercially under the qualifications below. TB may only ship within the exact scope of the Indonesian-content permission. The project owner stated on 2026-09-04 that permission for the Indonesian edition already exists; before a public or monetized release, archive the signed grant or licensor correspondence in the private legal repository and verify that it covers application bundling, offline storage, redistribution, updates, territories, branding, attribution, text modifications, and monetization. TB2 is explicitly excluded from the current implementation phase.

| Asset | Current source | Rights status | Release gate |
|---|---|---|---|
| Yuku/Quick Bible code | Git commit `c2f24b5fef5221e3520bfd1d38503dc38d9f8b8d` | Apache-2.0; commercial modification and distribution allowed with license/notice obligations | Preserve Apache license, notices, and attribution; rename/rebrand so users are not misled about upstream authorship. |
| TB | Yuku API preset `in-tb`; a separate local JSON corpus also exists | Copyrighted LAI text. Permission asserted by project owner; grant text not present in this workspace | Do not publish or monetize until the grant's scope and required attribution are recorded. The test module is acceptable for this private device validation. |
| TB2 | Excluded from this implementation phase | Copyrighted LAI text; no licensed master file or approved delivery endpoint is in this workspace | Do not import, reconstruct, bundle, or test TB2 in this phase. |
| KJV | Yuku preset `en-kjv`; official eBible USFM archive `engkjvcpb_usfm.zip` retained | Public domain outside the UK. Printing/import restrictions under Crown letters patent apply in the UK | Commercial use is acceptable for Indonesia and most territories; obtain UK-specific advice/permission before UK distribution. Filter official source to the desired 66-book canon. |
| WEB | Yuku preset `en-web`; official eBible 66-book Protestant USFM archive retained | Public domain. “World English Bible” is a trademark | Commercial use is acceptable. If the actual text is changed, do not call the result “World English Bible.” |
| ASV | Yuku preset `en-asv`; official eBible USFM archive retained | Public domain due to copyright expiration | Commercial use and modification are acceptable; retain truthful edition/source attribution. |
| WEB audio | AudioTreasure WEB recording narrated by David Williams | Publisher states that the recording is public domain and released without restriction | Keep the title `WEB — David Williams (public domain)`, source URL, and this provenance record. Do not relabel it as another translation. |
| Granite Embedding model | IBM `granite-embedding-97m-multilingual-r2`, pinned revision `835ad140…` | Apache-2.0 model license | Preserve the model license and attribution with any downloaded offline-search pack. |
| ONNX Runtime Android | Microsoft ONNX Runtime 1.24.3 | MIT License | Preserve the MIT license notice in distributed binary notices. |

## AudioTreasure WEB audio manifest

- Publisher page: `https://audiotreasure.com/webindex.htm`
- Source manifest SHA-256: `90d9847c22acc226eb2655198ab97dd027b69ab40aaa551ad313e810958f7dc1`
- Source manifest records: 1,189 chapters across 66 books
- Bundled manifest: `Alkitab/src/main/assets/audio/audiotreasure_web_manifest.json`
- Narrator: David Williams
- Translation: World English Bible
- License statement recorded from publisher: public domain; released without restriction
- Verified filename exceptions: Ratapan/Lamentations 5 uses `25_Lam5.mp3`; Zakharia/Zechariah 14 uses `38_Zechariah_14.mp3`.

The bundled JSON removes repeated per-row attribution fields but retains top-level source and license metadata. It stores the publisher's validated URL for every chapter rather than reconstructing filenames at runtime.

## Downloaded module manifest

The installable test modules are in the workspace's `artifacts/modules/`. The API responses are gzip streams even though their filenames end in `.yes`; Yuku transparently decompresses them during import. `artifacts/modules/SHA256SUMS` records both downloaded-stream hashes and decompressed hashes. The latter match byte-for-byte with the files installed under the debug app's private `files/bible/yes/` directory on the S21+.

| Module | Download URL | Downloaded bytes | Installed bytes |
|---|---|---:|---:|
| TB | `https://api.alkitab.app/versions/get_yes?preset_name=in-tb` | 1,910,147 | 2,190,420 |
| KJV | `https://api.alkitab.app/versions/get_yes?preset_name=en-kjv` | 1,701,243 | 1,941,000 |
| WEB | `https://api.alkitab.app/versions/get_yes?preset_name=en-web` | 2,141,193 | 2,460,041 |
| ASV | `https://api.alkitab.app/versions/get_yes?preset_name=en-asv` | 1,649,623 | 1,880,663 |

Yuku-hosted modules are useful for compatibility testing, but production content should be generated from licensor-provided or independently verified upstream masters. The official public-domain English USFM packages and their hashes are recorded in `content-sources/english/README.md` in the source workspace.

## Device verification baseline

Device: Samsung Galaxy S21+ (`SM-G996B`), Android 15, serial `RRCR100881M`. App baseline: `yuku.alkitab.debug` version `5.0.0-b0` (`23549140`).

- TB opened at Kejadian 1:1 with the expected Indonesian text.
- KJV, WEB, and ASV each opened at Genesis 1:1 with their expected edition wording.
- ASV search for `beginning` returned Genesis 1:1 and further results.
- Android crash buffer was empty after import, switching, reading, and search.
- Screenshots and UI hierarchy dumps are stored under the source workspace's `artifacts/yuku-s21plus-{tb,kjv,web,asv}-reader.*` and `artifacts/yuku-s21plus-asv-search.*`.

The new audio, fallback TTS, accessibility, and offline theme-search flows require a fresh S21+ acceptance run before release.
