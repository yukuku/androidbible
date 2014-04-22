# coding=utf-8
import re

fns = []

out = open('/tmp/out3.yet', 'w')

for line in open('/tmp/out2b.yet').readlines():
	line = line.strip()
	m = re.match(r'^verse\t(\d+)\t(\d+)\t(\d+)\t(.*)$', line)
	if m:
		text = m.group(4)

		field = 0

		def f(n):
			global field
			field += 1

			fns.append((int(m.group(1)), int(m.group(2)), int(m.group(3)), field, n.group(1)))

			return '@<f' + str(field) + '@>@/'

		text = re.sub(r'##note##(.*?)##/note##', f, text)

		line = 'verse\t%s\t%s\t%s\t%s\n' % (m.group(1), m.group(2), m.group(3), text)
	else:
		line += '\n'

	line = re.sub(r'&#8217;', '’', line, 0, re.M)
	line = re.sub(r'&#8211;', '–', line, 0, re.M)
	line = re.sub(r'&#700;', 'ʼ', line, 0, re.M)
	line = re.sub(r'&#701;', 'ʽ', line, 0, re.M)
	line = re.sub(r'&#8212;', '—', line, 0, re.M)
	line = re.sub(r'&#8220;', '“', line, 0, re.M)
	line = re.sub(r'&#8221;', '”', line, 0, re.M)

	out.write(line)

for fn in fns:
	out.write('footnote\t%s\t%s\t%s\t%s\t%s\n' % fn)

out.close()
