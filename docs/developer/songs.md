**File format**

Untuk menuliskan lagu-lagu, format yang dipakai adalah plain text file (.txt). Gunakan utf-8 dengan UNIX line ending. (Kalau kurang paham, kami akan membantu membetulkannya.)

*For song books, please use/save the text file to a plain text file format (.txt). Encoding must be utf-8, UNIX line endings.*

**Software**

Anda bisa menggunakan berbagai macam software text editor seperti Microsoft Word/TextEdit/Notepad/Sublime Text.

*You can use any text editor program such as Microsoft Word/TextEdit/Notepad/Sublime Text, etc.*

**Format penulisan**

Berikut adalah format penulisan yang lengkap untuk satu lagu:

*Here is the* ***complete*** *writing format for one song:*

```text
code ← nomor lagu song number
title ← judul lagu song title
title_original ← judul lagu asli original song title (if 'title' is translated from the original language)
1= ← nada dasar, mis. 1=C (artinya Do=C) key signature, e.g. 1=D means the tonic is in D
4/4 ← ketukan time signature (in case of multiple time signatures, separate with comma. e.g. "2/4, 3/4")
tune ← jenis melodi the "tune" of the song (some songs can share the same tune)
authors_lyric ← pengarang lirik/syair the authors of lyrics (separate with ';' if multiple)
authors_music ← pengarang musik the authors of the music (separate with ';' if multiple)
scriptureReferences ← acuan ayat where the lyrics are taken from, in OSIS format

*1
(bait 1 first stanza)

*ref
(kalau ada ref, harap ditulis satu kali saja, tidak perlu berulang-ulang setelah tiap bait)
if there is a refrain, just type it once instead of copy/pasting it after every stanza

*2
(bait 2 kalau ada second stanza (if any))

*3
(bait 3 kalau ada third stanza (if any))

(dst)
(and so on)

=== ← pemisah antar lagu divider between songs
```

Sebagai contoh:

*So for example, you could type something like this for a song:*

```text
code 3
title Zakeus
1=C
4/4
authors_lyric Mary Nelson Keithahn
authors_music John D Horman

*1
Orang banyak di Yerikho sedang tunggu Yesus,
Di mana-mana terdengar, tentang mujizat-Nya,
Seorang pemungut cukai panjat pohon ara,
Yesus pun melihat dia, dan panggil namanya.

*ref
"Zakeus, Zakeus, segera turunlah!
Zakeus, Zakeus, mari, percayalah."

*2
Zakeus turun dan heran Yesus menyapanya.
Dia sadar tiada haknya menjamu Sang Kudus,
Pada Yesus <u>dia</u> berkata: "Ku orang berdosa,
namun saat Kau panggilku, ku janji berubah."

===
```

Dan beginilah lagu tersebut ditampilkan di layar:

*And this is how the song will be displayed on the screen:*

![Screenshot from the original document](images/songs.png)

**Special notes**

- \<u\>\</u\> adalah simbol untuk **underline** untuk kata yang dinyanyikan 1 ketuk. Misalnya, "semua" yang dinyanyikan dalam 2 ketuk sebagai "se-mua", dituliskan "se\<u\>mua\</u\>", nantinya akan ditampilkan sebagai "semua". Harap diperhatikan bahwa diftong seperti "bagai" memang sudah dibaca "ba-gai" bukan "ba-ga-i", maka tidak perlu diberi tanda \<u\>\</u\> lagi.

*Use \<u\>\</u\> to underline a word or a part of a word that is sung in a syllable*

- Untuk setiap lagu, yang **harus ada** adalah **code** (nomor lagu), **title** (judul lagu), dan isi (lirik) lagu tersebut. Keterangan lainnya seperti nada dasar/ketukan/pengarang boleh ada, boleh juga tidak ada.

*Each song* ***must have*** *the* ***code*** *(song number),* ***title*** *(song title), and the* ***content*** *(lyrics). Other information such as the key signature, time signature, and author(s) are optional.*

- Jika satu lagu hanya terdiri dari satu bait, tetap harus ada tanda "\*1" sebelum bait pertama.

*If a song has only one verse, you still need to put "\*1" before the first line of the verse.*

Jika anda memiliki pertanyaan, silakan hubungi kami di [help@bibleforandroid.com](mailto:help@bibleforandroid.com) atau [yukuku@gmail.com](mailto:yukuku@gmail.com)

*If you have any questions, contact us at* [*help@bibleforandroid.com*](mailto:help@bibleforandroid.com) *or* [*yukuku@gmail.com*](mailto:yukuku@gmail.com)

**Examples from the "English Hymns" book**

```text
code 54
title Welcome for Me
scriptureReferences Gen.8.10

*1
Like a bird on the deep, far away from its nest,
I had wandered, my Savior, from Thee,
But Thy dear loving voice called me home to Thy breast,
And I knew there was welcome for me.

*ref
Welcome for me, Savior, from Thee;
A smile and a welcome for me;
Now, like a dove, I rest in Thy love,
And find a sweet refuge in Thee.

*2
I am safe in the ark; I have folded my wings
On the bosom of mercy divine;
I am filled with the light, of Thy presence so bright,
And the joy that will ever be mine.

*3
I am safe in the ark, and I dread not the storm,
Though around me the surges may roll;
I will look to the skies, where the day never dies,
I will sing of the joy in my soul.

===
```

```text
code 1990
title Count Your Blessings
scriptureReferences Prov.10.6

*1
When upon life’s billows you are tempest tossed,
When you are discouraged, thinking all is lost,
Count your many blessings, name them one by one,
And it will surprise you what the Lord hath done.

*ref
Count your blessings, name them one by one,
Count your blessings, see what God hath done!
Count your blessings, name them one by one,
And it will surprise you what the Lord hath done.

*2
Are you ever burdened with a load of care?
Does the cross seem heavy you are called to bear?
Count your many blessings, every doubt will fly,
And you will keep singing as the days go by.

*3
When you look at others with their lands and gold,
Think that Christ has promised you His wealth untold;
Count your many blessings. Wealth can never buy
Your reward in heaven, nor your home on high.

*4
So, amid the conflict whether great or small,
Do not be disheartened, God is over all;
Count your many blessings, angels will attend,
Help and comfort give you to your journey’s end.
```
