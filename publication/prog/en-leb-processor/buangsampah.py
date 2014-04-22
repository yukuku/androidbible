# coding=utf-8
s = open('/tmp/out.yet').read()

import re

s = re.sub(r'^// chapter:.*\n', '', s, 0, re.M)
s = re.sub(r'@.', '', s, 0, re.M)
s = re.sub(r'##/?note##', '', s, 0, re.M)
s = re.sub(r'##pericope:##', '', s, 0, re.M)
s = re.sub(r'^verse.*\t', '', s, 0, re.M)
s = re.sub(r'&#8217;', '’', s, 0, re.M)
s = re.sub(r'&#8211;', '–', s, 0, re.M)
s = re.sub(r'&#700;', 'ʼ', s, 0, re.M)

t = open('/tmp/p5.txt', 'w')
t.write(s)
t.close()

