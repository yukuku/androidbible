import xml.etree.ElementTree as ET
import re

tree = ET.parse("/Users/yuku/Downloads/LEB.xml")
root = tree.getroot()

paratags = set()

awalp = 0
verse_1 = 0
current_book_1 = 0

s = None
fulltext = []

def proc(t, note = False, supplied = False):
	global s, verse_1, awalp

	if verse_1 != 0:
		exitafterthis = False

		print verse_1
		if str(verse_1).startswith(('Ob ', '2 Jn ', '3 Jn ', 'Jud ', 'Phm ')):
			m = re.match(r'.*\s+(\d+)', verse_1)
			if m is None:
				raise ValueError('cannot match ' + verse_1)
			s += '\nverse\t' + str(current_book_1) + '\t' + '1' + '\t' + m.group(1) + '\t'

		elif ':' in verse_1:
			m = re.match(r'.*\s+(\d+):(\d+|title)', verse_1)
			if m is None:
				raise ValueError('cannot match ' + verse_1)
			s += '\nverse\t' + str(current_book_1) + '\t' + m.group(1) + '\t' + m.group(2) + '\t'
		else:
			s += '\n// chapter: ' + verse_1
			exitafterthis = True
		verse_1 = 0

		if exitafterthis:
			return

	if awalp == -1:
		s += '@^'
		awalp = 0
	if awalp == 1:
		s += '@1'
		awalp = 0
	if awalp == 2:
		s += '@2'
		awalp = 0
	if awalp == 3:
		s += '@2'
		awalp = 0

	if note:
		if t is not None:
			s += '##note##' + t + '##/note##'
	elif supplied:
		if t is not None:
			s += '@9' + t + '@7'
	else:
		if t is not None:
			s = s + t

def prosesperikop(p):
	global s
	s += '\n##pericope:##' + p

def alltext(e):
	return ET.tostring(e).replace('<' + e.tag + '>', '').replace('</' + e.tag + '>', '')

for book in root.iter('book'):
	s = ''
	current_book_1 += 1

	def proseschapter(chapter):
		for para in chapter:

			def prosesbaris(baris):
				global verse_1

				if baris.tag == 'verse-number':
					verse_1 = baris.attrib['id']
				elif baris.tag == 'note':
					s2 = ''
					if baris.text is not None:
						s2 += baris.text
					for inside in baris:
						canhavemoreinside = False
						if inside.tag == 'supplied':
							canhavemoreinside = True
							s3 = '@9'

							if inside.text is not None:
								s3 += '@9' + inside.text + '@7'

							for moreinside in inside: # ignore more formatting
								if moreinside.text is not None: s3 += moreinside.text
								if moreinside.tail is not None: s3 += moreinside.tail

							s3 += '@7'

							if inside.tail is not None:
								s3 += inside.tail

							s2 += s3
						elif inside.tag == 'cite':
							if inside.text is not None:
								s2 += inside.text
							if inside.tail is not None:
								s2 += inside.tail
						elif inside.tag == 'i':
							if inside.text is not None:
								s2 += '@9' + inside.text + '@7'
							if inside.tail is not None:
								s2 += inside.tail
						elif inside.tag == 'sc':
							if inside.text is not None:
								s2 += inside.text #.upper()
							if inside.tail is not None:
								s2 += inside.tail
						elif inside.tag == 'b':
							if inside.text is not None:
								s2 += inside.text
							if inside.tail is not None:
								s2 += inside.tail
						else:
							raise ValueError('inside note tag: ' + inside.tag)

						if not canhavemoreinside:
							for moreinside in inside:
								raise ValueError('why is there more inside? inside.text: ' + inside.text)

					proc(s2, note = True)
				elif baris.tag == 'supplied':
					s2 = ''

					if baris.text is not None:
						s2 += baris.text

					for inside in baris: # ignore more formatting except notes
						if inside.tag == 'note':
							s2 += '##note##' + inside.text + '##/note##'

							if inside.tail:
								s2 += inside.tail
						else:
							raise ValueError('tag other than note inside supplied: ' + inside.tag)

					proc(s2, supplied = True)
				elif baris.tag == 'idiom-start':
					if baris.text is not None:
						raise ValueError('baris text inside idiom-start: ' + baris.text)
				elif baris.tag == 'idiom-end':
					if baris.text is not None:
						raise ValueError('baris text inside idiom-end: ' + baris.text)
				elif baris.tag == 'i':
					if baris.text is not None:
						proc('@9' + baris.text + '@7')
				else:
					raise ValueError('baris tag: ' + baris.tag)

				proc(baris.tail)


			# bisa ul, pericope, block, p
			if para.tag == 'p':
				awalp = -1

				if para.text is not None:
					proc(para.text)

				for baris in para:
					prosesbaris(baris)

			elif para.tag == 'ul': # need to process inner
				for li in para:
					if li.tag == 'li1':
						awalp = 1
					elif li.tag == 'li2':
						awalp = 2
					elif li.tag == 'li3':
						awalp = 3
					else:
						raise ValueError('li tag: ' + li.tag)

					if li.text is not None:
						proc(li.text)

					for baris in li:
						prosesbaris(baris)

			elif para.tag == 'block': # need to process inner
				if para.text:
					proc(para.text)

				for blockpara in para:
					if blockpara.tag == 'p':
						awalp = -1
					else:
						raise ValueError('blockpara tag: ' + blockpara.tag)

					if blockpara.text:
						proc(blockpara.text)

					for baris in blockpara:
						prosesbaris(baris)

			elif para.tag == 'pericope':
				prosesperikop(alltext(para))

				if para.tail is not None and len(para.tail.strip()) > 0:
					raise ValueError('after pericope has tail: ' + para.tail)
			else:
				raise ValueError('para tag: ' + para.tag)


	chapters = list(book.iter('chapter'))

	if len(chapters) == 0:
		proseschapter(book)
	else:
		for chapter in chapters:
			proseschapter(chapter)

	fulltext.append(s)


import codecs
f = codecs.open('/tmp/out.yet', mode='w', encoding='utf-8')

for ft in fulltext:
	print ft
	f.write(ft)

f.close()
