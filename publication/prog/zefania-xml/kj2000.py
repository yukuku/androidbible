import xml.etree.ElementTree as ET
import re

tree = ET.parse("/Users/yuku/Downloads/SF_2009-01-20_ENG_KJ2000_(KING JAMES 2000).xml")
root = tree.getroot()

def processverse(book_1, chapter_1, verse_1, verse):
	text = ''
	for x in verse.iter():
		text += x.text

		if x.tail:
			text += x.tail

	text = re.sub(r'\s+', ' ', text)
	text = text.strip()
	print "verse\t%s\t%s\t%s\t%s" % (book_1, chapter_1, verse_1, text)


info = root.find('INFORMATION')
print "info\tshortName\t%s" % (info.find('identifier').text,)
print "info\tlongName\t%s" % (info.find('title').text,)
print "info\tdescription\t%s" % (info.find('description').text,)

for book in root.iter('BIBLEBOOK'):
	book_1 = int(book.attrib['bnumber'])
	for chapter in book.iter('CHAPTER'):
		chapter_1 = int(chapter.attrib['cnumber'])
		for verse in chapter.iter('VERS'):
			verse_1 = int(verse.attrib['vnumber'])
			processverse(book_1, chapter_1, verse_1, verse)
