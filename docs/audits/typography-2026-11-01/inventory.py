from collections import defaultdict
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[3]
entries = defaultdict(list)
for file in sorted((root / 'Alkitab/src/main').rglob('*')):
    if file.suffix not in ('.xml', '.kt', '.java'):
        continue
    for line in file.read_text().splitlines():
        for match in re.finditer(r'(?:android:textSize="(\d+(?:\.\d+)?)(sp|dp)"|fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp)', line):
            size = (match[1] or match[3]) + (match[2] or 'sp')
            entries[size].append({'file': str(file.relative_to(root)), 'declaration': line.strip()})
output = dict(sorted(entries.items(), key=lambda item: float(re.sub('[a-z]', '', item[0]))))
Path(__file__).with_name('explicit-sizes.json').write_text(json.dumps(output, indent=2) + '\n')
print(json.dumps({size: len(uses) for size, uses in output.items()}, indent=2))
