s = open('/tmp/p5.txt').read()
t = open('/tmp/p5-a.txt', 'w')

ft = ''
for c in s:
	if ord('a') <= ord(c) <= ord('z') or ord('A') <= ord(c) <= ord('Z'):
		ft += c

t.write(ft)
t.close()
