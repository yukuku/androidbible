package yuku.alkitab.base;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.widget.Toast;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

import yuku.afw.D;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.devotion.Downloader;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.InternalDbHelper;
import yuku.alkitab.base.storage.InternalReader;
import yuku.alkitab.base.storage.OldVerseTextDecoder;
import yuku.alkitab.base.storage.SongDb;
import yuku.alkitab.base.storage.SongDbHelper;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.IntArrayList;


public class S {
	static final String TAG = S.class.getSimpleName();

	/**
	 * values applied from settings
	 */
	public static class applied {
		/** in dp */
		public static float fontSize2dp;
		
		public static Typeface fontFace;
		public static float lineSpacingMult;
		public static int fontBold;
		
		public static int fontColor; 
		public static int fontRedColor;
		public static int backgroundColor;
		public static int verseNumberColor;
		
		/** 0.f to 1.f */
		public static float backgroundBrightness;
		
		// semua di bawah dalam px
		public static int indentParagraphFirst;
		public static int indentParagraphRest;
		public static int indentSpacing1;
		public static int indentSpacing2;
		public static int indentSpacing3;
		public static int indentSpacing4;
		public static int indentSpacingExtra;
		public static int paragraphSpacingBefore;
		public static int pericopeSpacingTop;
		public static int pericopeSpacingBottom;
	}
	
	/**
	 * Settings that are still alive even when activities are destroyed.
	 * Ensure there is no references to any activity to prevent memory leak.
	 * 
	 * TODO this is not a good practice
	 */
	public static class temporary {
		public static String devotion_name = null;
		public static Date devotion_date = null;
		public static int devotion_scroll = 0;
	}
	
	//# 22nya harus siap di siapinKitab
	public static Version activeVersion;
	public static Book activeBook;
	public static String activeVersionId;

	public static Downloader downloader;
	
	static {
		int a = R.drawable.ambilwarna_cursor;
		if (a == 0) throw new RuntimeException(); // cuma mencegah project ambilwarna lupa dibuka
	}
	
	private static synchronized void prepareVersion() {
		if (activeVersion == null) {
			activeVersion = getInternalVersion();
		}
	}

	private static Version internalVersion;
	public static synchronized Version getInternalVersion() {
		if (internalVersion == null) {
			AppConfig c = AppConfig.get(App.context);
			internalVersion = new Version(new InternalReader(c.internalPrefix, c.internalShortName, c.internalLongName, new OldVerseTextDecoder.Utf8()));
		}
		return internalVersion;
	}

	public static synchronized void prepareBook() {
		prepareVersion();
		
		if (activeBook != null) return;
		activeBook = activeVersion.getFirstBook(); // nanti diset sama luar waktu init 
	}
	
	public static void calculateAppliedValuesBasedOnPreferences() {
		//# atur ukuran huruf isi berdasarkan pengaturan
		{
			applied.fontSize2dp = Preferences.getFloat(App.context.getString(R.string.pref_ukuranHuruf2_key), 17.f);
		}
		
		//# atur jenis huruf, termasuk boldnya
		{
			applied.fontFace = FontManager.typeface(Preferences.getString(App.context.getString(R.string.pref_jenisHuruf_key), null));
			applied.lineSpacingMult = Preferences.getFloat(App.context.getString(R.string.pref_lineSpacingMult_key), 1.f);
			applied.fontBold = Preferences.getBoolean(App.context.getString(R.string.pref_boldHuruf_key), false)? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur warna teks, latar, dan nomer ayat
		{
			applied.fontColor = Preferences.getInt(App.context.getString(R.string.pref_warnaHuruf_int_key), App.context.getResources().getInteger(R.integer.pref_warnaHuruf_int_default));
			applied.backgroundColor = Preferences.getInt(App.context.getString(R.string.pref_warnaLatar_int_key), App.context.getResources().getInteger(R.integer.pref_warnaLatar_int_default)); 
			applied.verseNumberColor = Preferences.getInt(App.context.getString(R.string.pref_warnaNomerAyat_int_key), App.context.getResources().getInteger(R.integer.pref_warnaNomerAyat_int_default));
			applied.fontRedColor = Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default));
			
			// calculation of backgroundColor brightness. Used somewhere else.
			{
				int c = applied.backgroundColor;
				applied.backgroundBrightness = (0.30f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)) * 0.003921568627f;
			}
		}
		
		Resources res = App.context.getResources();
		
		float skalaBerdasarUkuranHuruf = applied.fontSize2dp / 17.f;
		applied.indentParagraphFirst = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indentParagraphFirst) + 0.5f);
		applied.indentParagraphRest = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indentParagraphRest) + 0.5f);
		applied.indentSpacing1 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_1) + 0.5f);
		applied.indentSpacing2 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_2) + 0.5f);
		applied.indentSpacing3 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_3) + 0.5f);
		applied.indentSpacing4 = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indent_4) + 0.5f);
		applied.indentSpacingExtra = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indentExtra) + 0.5f);
		applied.paragraphSpacingBefore = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.paragraphSpacingBefore) + 0.5f);
		applied.pericopeSpacingTop = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.pericopeSpacingTop) + 0.5f);
		applied.pericopeSpacingBottom = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.pericopeSpacingBottom) + 0.5f);
		
		if (D.EBUG) {
			Toast.makeText(App.context, String.format(" text 0x%08x %n bg 0x%08x %n versenumber 0x%08x %n redtext 0x%08x", 
				applied.fontColor,
				applied.backgroundColor,
				applied.verseNumberColor,
				applied.fontRedColor), Toast.LENGTH_LONG).show();
			applied.fontColor = Preferences.getInt(App.context.getString(R.string.pref_warnaHuruf_int_key), App.context.getResources().getInteger(R.integer.pref_warnaHuruf_int_default));
			applied.backgroundColor = Preferences.getInt(App.context.getString(R.string.pref_warnaLatar_int_key), App.context.getResources().getInteger(R.integer.pref_warnaLatar_int_default)); 
			applied.verseNumberColor = Preferences.getInt(App.context.getString(R.string.pref_warnaNomerAyat_int_key), App.context.getResources().getInteger(R.integer.pref_warnaNomerAyat_int_default));
			applied.fontRedColor = Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default));

		}
	}
	
	private static final String notAvailableText = "[?]"; //$NON-NLS-1$
	
	private static final String[] notAvailableTextArray = {
		notAvailableText,
	};

	public static synchronized String loadVerseText(Version version, Book book, int pasal_1, int ayat_1) {
		if (book == null) {
			return notAvailableText;
		}
		SingleChapterVerses verses = loadChapterText(version, book, pasal_1, false, false);
		
		if (verses == null) {
			return notAvailableText;
		}
		
		int verse_0 = ayat_1 - 1;
		if (verse_0 >= verses.getVerseCount()) {
			return notAvailableText;
		}
		return verses.getVerse(verse_0);
	}
	
	public static synchronized String loadVerseText(Version version, int ari) {
		return loadVerseText(version, version.getBook(Ari.toBook(ari)), Ari.toChapter(ari), Ari.toVerse(ari));
	}

	public static synchronized SingleChapterVerses loadChapterText(Version version, Book book, int pasal_1) {
		if (book == null) {
			return null;
		}
		
		return loadChapterText(version, book, pasal_1, false, false);
	}

	public static synchronized SingleChapterVerses loadChapterTextLowercased(Version version, Book book, int pasal_1) {
		if (book == null) {
			return null;
		}
		return loadChapterText(version, book, pasal_1, false, true);
	}
	
	public static synchronized String loadChapterTextLowercasedWithoutSplit(Version version, Book book, int pasal_1) {
		if (book == null) {
			return notAvailableText;
		}
		
		SingleChapterVerses singleVerse = version.bibleReader.loadVerseText(book, pasal_1, true, true);
		
		if (singleVerse == null) {
			return notAvailableText;
		}
		
		return singleVerse.getVerse(0);
	}
	
	private static SingleChapterVerses loadChapterText(Version version, Book book, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		return version.bibleReader.loadVerseText(book, pasal_1, janganPisahAyat, hurufKecil);
	}

	public static String reference(Version version, int ari) {
		int kitabPos = Ari.toBook(ari);
		int pasal_1 = Ari.toChapter(ari);
		int ayat_1 = Ari.toVerse(ari);
		
		StringBuilder hasil = new StringBuilder(40);
		Book k = version.getBook(kitabPos);
		if (k == null) {
			hasil.append('[').append(kitabPos).append("] "); //$NON-NLS-1$
		} else {
			hasil.append(k.shortName).append(' ');
		}
		
		hasil.append(pasal_1);
		if (ayat_1 != 0) {
			hasil.append(':').append(ayat_1);
		}
		return hasil.toString();
	}

	public static String reference(Book book, int pasal_1) {
		return (book == null? "[?]": book.shortName) + " " + pasal_1; //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static String reference(Book book, int pasal_1, int ayat_1) {
		return (book == null? "[?]": book.shortName) + " " + pasal_1 + ":" + ayat_1;  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
	}
	
	public static CharSequence reference(Book book, int pasal_1, IntArrayList xayat_1) {
		StringBuilder sb = new StringBuilder(book == null? "[?]": book.shortName); //$NON-NLS-1$
		sb.append(' ').append(pasal_1);
		if (xayat_1 == null || xayat_1.size() == 0) {
			return sb;
		}
		sb.append(':');
		writeVerseRange(xayat_1, sb);
		return sb;
	}
	
	public static void writeVerseRange(IntArrayList xayat_1, StringBuilder sb) {
		int origLen = sb.length();
		int lastAyat_1 = 0;
		int awalAyat_1 = 0;
		
		for (int i = 0; i < xayat_1.size(); i++) {
			int ayat_1 = xayat_1.get(i);
			
			if (lastAyat_1 == 0) {
				// blum ada, diam dulu aja
			} else if (lastAyat_1 == ayat_1 - 1) {
				// masih terusan, simpen awalnya
				if (awalAyat_1 == 0) awalAyat_1 = lastAyat_1;
			} else {
				// abis loncat
				if (awalAyat_1 != 0) {
					sb.append(origLen == sb.length()? "": ", ").append(awalAyat_1).append('-').append(lastAyat_1); //$NON-NLS-1$ //$NON-NLS-2$
					awalAyat_1 = 0;
				} else {
					sb.append(origLen == sb.length()? "": ", ").append(lastAyat_1); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			
			lastAyat_1 = xayat_1.get(i);
		}
		
		// penghabisan
		if (awalAyat_1 != 0) {
			sb.append(origLen == sb.length()? "": ", ").append(awalAyat_1).append('-').append(lastAyat_1);  //$NON-NLS-1$//$NON-NLS-2$
			awalAyat_1 = 0; // ga perlu, tapi biar konsisten aja dengan atas
		} else {
			sb.append(origLen == sb.length()? "": ", ").append(lastAyat_1); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	/**
	 * @param handler Jangan null kalo mau dicek ulang 200ms kemudian. Harus null kalo jangan ulang lagi. 20110620 Uda ga dipake lagi.
	 */
	public static void applyLanguagePreference(final Handler handler, final int cobaLagi) {
		String bahasa = Preferences.getString(App.context.getString(R.string.pref_bahasa_key), App.context.getString(R.string.pref_bahasa_default));

		Locale locale;
		if ("DEFAULT".equals(bahasa)) { //$NON-NLS-1$
			locale = Locale.getDefault();
		} else {
			locale = new Locale(bahasa);
		}
		
		App.updateConfigurationWithLocale(App.context.getResources().getConfiguration(), locale);
	}

	private static TObjectIntHashMap<String> cache_rawResId = new TObjectIntHashMap<String>(32);
	
	public static InputStream openRaw(String name) {
		Resources resources = App.context.getResources();
		
		int resId = cache_rawResId.get(name);
		if (resId != 0) {
			return resources.openRawResource(resId);
		}
		
		resId = resources.getIdentifier(name, "raw", App.context.getPackageName()); //$NON-NLS-1$
		if (resId == 0) {
			return null;
		}
		
		cache_rawResId.put(name, resId);
		return resources.openRawResource(resId);
	}
	
	private static InternalDb db;
	public static synchronized InternalDb getDb() {
		if (db == null) {
			db = new InternalDb(new InternalDbHelper(App.context));
		}
		
		return db;
	}
	
	private static SongDb songDb;
	public static synchronized SongDb getSongDb() {
		if (songDb == null) {
			songDb = new SongDb(new SongDbHelper());
		}
		
		return songDb;
	}
	
	/**
	 * Jika ayat_1_range adalah null, ayat akan diabaikan (jadi cuma kitab dan pasal).
	 */
	public static String createVerseUrl(Book book, int pasal_1, String ayat_1_range) {
		AppConfig c = AppConfig.get(App.context);
		if (book.bookId >= c.url_standardBookNames.length) {
			return null;
		}
		String calonKitab = c.url_standardBookNames[book.bookId], calonPasal = String.valueOf(pasal_1), calonAyat = ayat_1_range;
		for (String format: c.url_format.split(" ")) { //$NON-NLS-1$
			if ("slash1".equals(format)) calonPasal = "/" + calonPasal; //$NON-NLS-1$ //$NON-NLS-2$
			if ("slash2".equals(format)) calonAyat = "/" + calonAyat; //$NON-NLS-1$ //$NON-NLS-2$
			if ("dot1".equals(format)) calonPasal = "." + calonPasal; //$NON-NLS-1$ //$NON-NLS-2$
			if ("dot2".equals(format)) calonAyat = "." + calonAyat; //$NON-NLS-1$ //$NON-NLS-2$
			if ("nospace0".equals(format)) calonKitab = calonKitab.replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return c.url_prefix + calonKitab + calonPasal + (ayat_1_range == null? "": calonAyat); //$NON-NLS-1$
	}
}
