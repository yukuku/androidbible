package yuku.alkitab.songs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import yuku.alkitab.songs.newdoc.Block;
import yuku.alkitab.songs.newdoc.LyricBlock;
import yuku.alkitab.songs.newdoc.PBlock;
import yuku.alkitab.songs.newdoc.RowBlock;
import yuku.alkitab.songs.newdoc.ScriptureBlock;
import yuku.alkitab.songs.newdoc.SongDocument;
import yuku.alkitab.songs.newdoc.SongDocumentJson;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;

/**
 * REM-21 replaced the gzipped Java-serialized {@code List<Song>} download wire format with a
 * gzipped JSON song-book wrapper (design §3.7). These tests build that wrapper via
 * {@link SongDocumentJson} (through {@link LegacySongConverter} fixtures for realism) and assert
 * {@link SongBookUtil#deserializeSongs} parses it back into {@link SongDocument}s.
 */
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
        s.scriptureReferences = "John.3.16";
        return s;
    }

    private static byte[] gzipSongBookJson(List<Song> songs) throws IOException {
        List<SongDocument> docs = new ArrayList<>(songs.size());
        for (Song s : songs) {
            docs.add(yuku.alkitab.songs.newdoc.LegacySongConverter.convert(s));
        }
        SongDocumentJson.SongBookWrapper wrapper = new SongDocumentJson.SongBookWrapper(SongDocumentJson.DATA_FORMAT_VERSION, docs);
        String json = SongDocumentJson.encodeSongBook(wrapper);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private static PBlock firstPBlockWithRole(SongDocument doc, String role) {
        for (Block b : doc.getBlocks()) {
            if (b instanceof PBlock && role.equals(((PBlock) b).getRole())) {
                return (PBlock) b;
            }
        }
        return null;
    }

    private static PBlock firstRowItemWithRole(SongDocument doc, String role) {
        for (Block b : doc.getBlocks()) {
            if (b instanceof RowBlock) {
                for (PBlock item : ((RowBlock) b).getItems()) {
                    if (role.equals(item.getRole())) return item;
                }
            }
        }
        return null;
    }

    private static String plain(yuku.alkitab.songs.newdoc.Line line) {
        return yuku.alkitab.songs.newdoc.SongDocumentKt.plainText(line);
    }

    @Test
    public void deserializeSingleSong() throws Exception {
        Verse v1 = createVerse(1, VerseKind.NORMAL, "Amazing grace, how sweet the sound", "That saved a wretch like me");
        Verse refrain = createVerse(2, VerseKind.REFRAIN, "Praise the Lord, praise the Lord");
        Lyric lyric = createLyric("Verse 1", v1, refrain);

        Song song = createSong("AG001", "Amazing Grace", "Amazing Grace (Original)", Collections.singletonList(lyric));

        byte[] gzipped = gzipSongBookJson(Collections.singletonList(song));
        List<SongDocument> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertNotNull(result);
        assertEquals(1, result.size());

        SongDocument decoded = result.get(0);
        assertEquals("AG001", decoded.getCode());
        assertEquals("Amazing Grace", decoded.getMeta().getTitle());
        assertEquals("Amazing Grace (Original)", decoded.getMeta().getTitle_original());
        assertEquals("Author A; Author B", plain(firstRowItemWithRole(decoded, "authors_lyric").getContent()));
        assertEquals("Composer X", plain(firstRowItemWithRole(decoded, "authors_music").getContent()));
        assertEquals("TUNE_NAME", plain(firstPBlockWithRole(decoded, "tune").getContent()));
        assertEquals("C 4/4", plain(firstPBlockWithRole(decoded, "musical").getContent()));

        ScriptureBlock scripture = null;
        for (Block b : decoded.getBlocks()) {
            if (b instanceof ScriptureBlock) scripture = (ScriptureBlock) b;
        }
        assertNotNull(scripture);
        assertEquals("John.3.16", scripture.getOsis());

        LyricBlock lyricBlock = null;
        for (Block b : decoded.getBlocks()) {
            if (b instanceof LyricBlock) lyricBlock = (LyricBlock) b;
        }
        assertNotNull(lyricBlock);
        assertEquals("Verse 1", plain(lyricBlock.getCaption()));
        assertEquals(2, lyricBlock.getVerses().size());
        assertEquals(yuku.alkitab.songs.newdoc.VerseKind.NORMAL, lyricBlock.getVerses().get(0).getKind());
        assertEquals(yuku.alkitab.songs.newdoc.VerseKind.REFRAIN, lyricBlock.getVerses().get(1).getKind());
    }

    @Test
    public void deserializeMultipleSongs() throws Exception {
        List<Song> songs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Verse v = createVerse(1, VerseKind.NORMAL, "Line " + i + " of song " + i);
            Lyric l = createLyric(null, v);
            songs.add(createSong("S" + i, "Song " + i, null, Collections.singletonList(l)));
        }

        byte[] gzipped = gzipSongBookJson(songs);
        List<SongDocument> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertEquals(5, result.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("S" + (i + 1), result.get(i).getCode());
            assertEquals("Song " + (i + 1), result.get(i).getMeta().getTitle());
        }
    }

    @Test
    public void deserializeEmptyList() throws Exception {
        byte[] gzipped = gzipSongBookJson(new ArrayList<>());
        List<SongDocument> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void deserializeSongWithUnicode() throws Exception {
        Verse v = createVerse(1, VerseKind.NORMAL, "Ku bersyukur pada-Mu", "À toi la gloire", "主的恩典");
        Lyric l = createLyric("Bait 1", v);
        Song song = createSong("UNI01", "Nyanyian Pujian — 赞美诗", null, Collections.singletonList(l));
        song.authors_lyric = Collections.singletonList("Péngarang");
        song.authors_music = Collections.singletonList("作曲家");

        byte[] gzipped = gzipSongBookJson(Collections.singletonList(song));
        List<SongDocument> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertEquals(1, result.size());
        SongDocument decoded = result.get(0);
        assertEquals("Nyanyian Pujian — 赞美诗", decoded.getMeta().getTitle());
        assertEquals("Péngarang", plain(firstRowItemWithRole(decoded, "authors_lyric").getContent()));

        LyricBlock lyricBlock = null;
        for (Block b : decoded.getBlocks()) {
            if (b instanceof LyricBlock) lyricBlock = (LyricBlock) b;
        }
        assertNotNull(lyricBlock);
        assertEquals(3, lyricBlock.getVerses().get(0).getLines().size());
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

        byte[] gzipped = gzipSongBookJson(Collections.singletonList(song));
        List<SongDocument> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertEquals(1, result.size());
        SongDocument decoded = result.get(0);
        List<LyricBlock> lyricBlocks = new ArrayList<>();
        for (Block b : decoded.getBlocks()) {
            if (b instanceof LyricBlock) lyricBlocks.add((LyricBlock) b);
        }
        assertEquals(3, lyricBlocks.size());
        assertEquals("Verse 1", plain(lyricBlocks.get(0).getCaption()));
        assertEquals("Verse 2", plain(lyricBlocks.get(1).getCaption()));
        assertEquals("Text", plain(lyricBlocks.get(2).getCaption()));
        assertEquals(yuku.alkitab.songs.newdoc.VerseKind.TEXT, lyricBlocks.get(2).getVerses().get(0).getKind());
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

        byte[] gzipped = gzipSongBookJson(Collections.singletonList(song));
        List<SongDocument> result = SongBookUtil.deserializeSongs(new ByteArrayInputStream(gzipped));

        assertEquals(1, result.size());
        SongDocument decoded = result.get(0);
        assertEquals("N001", decoded.getCode());
        assertEquals(0, decoded.getBlocks().size());
    }

    @Test
    public void isSupportedDataFormatVersionAcceptsOnlyTheJsonVersion() {
        assertEquals(false, SongBookUtil.isSupportedDataFormatVersion(3));
        assertEquals(false, SongBookUtil.isSupportedDataFormatVersion(4));
        assertTrue(SongBookUtil.isSupportedDataFormatVersion(5));
    }

    @Test
    public void deserializeRejectsTheOldJavaSerializedFormat() throws Exception {
        Song song = createSong("OLD1", "Old Format", null, Collections.singletonList(createLyric(null, createVerse(1, VerseKind.NORMAL, "line"))));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(Collections.singletonList(song));
        }
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(gz)) {
            gzos.write(baos.toByteArray());
        }

        assertThrows(Exception.class, () -> SongBookUtil.deserializeSongs(new ByteArrayInputStream(gz.toByteArray())));
    }
}
