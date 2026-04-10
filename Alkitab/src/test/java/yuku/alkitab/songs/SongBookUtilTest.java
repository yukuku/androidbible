package yuku.alkitab.songs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;

public class SongBookUtilTest {

    private static Verse createVerse(int ordering, VerseKind kind, String... lines) {
        Verse v = new Verse();
        v.ordering = ordering;
        v.kind = kind;
        v.lines = Arrays.asList(lines);
        return v;
    }

    private static Lyric createLyric(String caption, Verse... verses) {
        Lyric l = new Lyric();
        l.caption = caption;
        l.verses = Arrays.asList(verses);
        return l;
    }

    private static Song createSong(String code, String title, String titleOriginal, List<Lyric> lyrics) {
        Song s = new Song();
        s.code = code;
        s.title = title;
        s.title_original = titleOriginal;
        s.authors_lyric = Arrays.asList("Author A", "Author B");
        s.authors_music = Collections.singletonList("Composer X");
        s.tune = "TUNE_NAME";
        s.keySignature = "C";
        s.timeSignature = "4/4";
        s.lyrics = lyrics;
        s.scriptureReferences = "John 3:16";
        return s;
    }

    private static byte[] serializeSongs(List<Song> songs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(songs);
        }
        return baos.toByteArray();
    }

    private static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    @Test
    public void deserializeSingleSong() throws Exception {
        Verse v1 = createVerse(1, VerseKind.NORMAL, "Amazing grace, how sweet the sound", "That saved a wretch like me");
        Verse refrain = createVerse(2, VerseKind.REFRAIN, "Praise the Lord, praise the Lord");
        Lyric lyric = createLyric("Verse 1", v1, refrain);

        Song song = createSong("AG001", "Amazing Grace", "Amazing Grace (Original)", Collections.singletonList(lyric));

        byte[] serialized = serializeSongs(Collections.singletonList(song));
        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(serialized));

        assertNotNull(result);
        assertEquals(1, result.size());

        Song decoded = result.get(0);
        assertEquals("AG001", decoded.code);
        assertEquals("Amazing Grace", decoded.title);
        assertEquals("Amazing Grace (Original)", decoded.title_original);
        assertEquals(Arrays.asList("Author A", "Author B"), decoded.authors_lyric);
        assertEquals(Collections.singletonList("Composer X"), decoded.authors_music);
        assertEquals("TUNE_NAME", decoded.tune);
        assertEquals("C", decoded.keySignature);
        assertEquals("4/4", decoded.timeSignature);
        assertEquals("John 3:16", decoded.scriptureReferences);

        assertEquals(1, decoded.lyrics.size());
        Lyric decodedLyric = decoded.lyrics.get(0);
        assertEquals("Verse 1", decodedLyric.caption);
        assertEquals(2, decodedLyric.verses.size());

        Verse decodedV1 = decodedLyric.verses.get(0);
        assertEquals(1, decodedV1.ordering);
        assertEquals(VerseKind.NORMAL, decodedV1.kind);
        assertEquals(Arrays.asList("Amazing grace, how sweet the sound", "That saved a wretch like me"), decodedV1.lines);

        Verse decodedRefrain = decodedLyric.verses.get(1);
        assertEquals(2, decodedRefrain.ordering);
        assertEquals(VerseKind.REFRAIN, decodedRefrain.kind);
        assertEquals(Collections.singletonList("Praise the Lord, praise the Lord"), decodedRefrain.lines);
    }

    @Test
    public void deserializeMultipleSongs() throws Exception {
        List<Song> songs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Verse v = createVerse(1, VerseKind.NORMAL, "Line " + i + " of song " + i);
            Lyric l = createLyric(null, v);
            songs.add(createSong("S" + i, "Song " + i, null, Collections.singletonList(l)));
        }

        byte[] serialized = serializeSongs(songs);
        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(serialized));

        assertEquals(5, result.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("S" + (i + 1), result.get(i).code);
            assertEquals("Song " + (i + 1), result.get(i).title);
        }
    }

    @Test
    public void deserializeEmptyList() throws Exception {
        byte[] serialized = serializeSongs(new ArrayList<>());
        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(serialized));

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void deserializeGzipCompressed() throws Exception {
        Verse v = createVerse(1, VerseKind.NORMAL, "Compressed verse line");
        Lyric l = createLyric("Verse 1", v);
        Song song = createSong("GZ001", "Gzipped Song", null, Collections.singletonList(l));

        byte[] serialized = serializeSongs(Collections.singletonList(song));
        byte[] gzipped = gzipCompress(serialized);

        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("GZ001", result.get(0).code);
        assertEquals("Gzipped Song", result.get(0).title);
        assertEquals(1, result.get(0).lyrics.size());
        assertEquals("Compressed verse line", result.get(0).lyrics.get(0).verses.get(0).lines.get(0));
    }

    @Test
    public void deserializeSongWithUnicode() throws Exception {
        Verse v = createVerse(1, VerseKind.NORMAL, "Ku bersyukur pada-Mu", "\u00c0 toi la gloire", "\u4e3b\u7684\u6069\u5178");
        Lyric l = createLyric("Bait 1", v);
        Song song = createSong("UNI01", "Nyanyian Pujian \u2014 \u8d5e\u7f8e\u8bd7", null, Collections.singletonList(l));
        song.authors_lyric = Collections.singletonList("P\u00e9ngarang");
        song.authors_music = Collections.singletonList("\u4f5c\u66f2\u5bb6");

        byte[] serialized = serializeSongs(Collections.singletonList(song));
        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(serialized));

        assertEquals(1, result.size());
        Song decoded = result.get(0);
        assertEquals("Nyanyian Pujian \u2014 \u8d5e\u7f8e\u8bd7", decoded.title);
        assertEquals(Collections.singletonList("P\u00e9ngarang"), decoded.authors_lyric);
        assertEquals(3, decoded.lyrics.get(0).verses.get(0).lines.size());
        assertEquals("\u4e3b\u7684\u6069\u5178", decoded.lyrics.get(0).verses.get(0).lines.get(2));
    }

    @Test
    public void deserializeSongWithMultipleLyrics() throws Exception {
        Lyric l1 = createLyric("Verse 1",
            createVerse(1, VerseKind.NORMAL, "First verse line 1", "First verse line 2"));
        Lyric l2 = createLyric("Verse 2",
            createVerse(1, VerseKind.NORMAL, "Second verse line 1"),
            createVerse(2, VerseKind.REFRAIN, "Refrain line"));
        Lyric l3 = createLyric("Text",
            createVerse(1, VerseKind.TEXT, "Some explanatory text"));

        Song song = createSong("ML01", "Multi-Lyric Song", null, Arrays.asList(l1, l2, l3));

        byte[] serialized = serializeSongs(Collections.singletonList(song));
        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(serialized));

        assertEquals(1, result.size());
        Song decoded = result.get(0);
        assertEquals(3, decoded.lyrics.size());
        assertEquals("Verse 1", decoded.lyrics.get(0).caption);
        assertEquals("Verse 2", decoded.lyrics.get(1).caption);
        assertEquals("Text", decoded.lyrics.get(2).caption);
        assertEquals(VerseKind.TEXT, decoded.lyrics.get(2).verses.get(0).kind);
    }

    @Test
    public void deserializeSongWithNullFields() throws Exception {
        Song song = new Song();
        song.code = "N001";
        song.title = null;
        song.title_original = null;
        song.authors_lyric = null;
        song.authors_music = null;
        song.tune = null;
        song.keySignature = null;
        song.timeSignature = null;
        song.lyrics = null;
        song.scriptureReferences = null;

        byte[] serialized = serializeSongs(Collections.singletonList(song));
        List<Song> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(serialized));

        assertEquals(1, result.size());
        Song decoded = result.get(0);
        assertEquals("N001", decoded.code);
        assertNull(decoded.title);
        assertNull(decoded.authors_lyric);
        assertNull(decoded.lyrics);
        assertNull(decoded.scriptureReferences);
    }
}
