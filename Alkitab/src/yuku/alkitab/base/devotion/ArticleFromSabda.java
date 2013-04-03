package yuku.alkitab.base.devotion;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ArticleFromSabda implements IArticle {
	public static final String TAG = ArticleFromSabda.class.getSimpleName();
	
	private static Pattern pattern1;
	private static Pattern pattern2;
	private static Pattern pattern3;
	private static Pattern pattern4;
	
	static {
		pattern1 = Pattern.compile("<h1>\\s*<div\\s+align=\"center\">(.*?)</div>\\s*</h1>", Pattern.MULTILINE | Pattern.DOTALL); // judul //$NON-NLS-1$
		pattern2 = Pattern.compile("<td class=\"wj\">(.{100,}?)</td>", Pattern.MULTILINE | Pattern.DOTALL); // isi //$NON-NLS-1$
		pattern3 = Pattern.compile("<td class=\"wn\".*?>(.*?)</td>", Pattern.MULTILINE | Pattern.DOTALL); // header //$NON-NLS-1$
		pattern4 = Pattern.compile("<td width=\"50%\" class=\"wn\" align=\"right\">.*?<b>(.*?)</b>", Pattern.MULTILINE | Pattern.DOTALL); //header //$NON-NLS-1$
	}
	
	private String judul_;
	private String isiHtml_;
	private String headerHtml_;
	private boolean siapPakai_;
	protected String tgl_;
	
	protected ArticleFromSabda(String tgl) {
		tgl_ = tgl;
	}
	
	/**
	 * Pulih dari db
	 * @param siapPakai 
	 */
	public ArticleFromSabda(String tgl, String judul, String header, String isi, boolean siapPakai) {
		tgl_ = tgl;
		judul_ = judul;
		headerHtml_ = header;
		isiHtml_ = isi;
		siapPakai_ = siapPakai;
	}

	@Override
	public void fillIn(String mentah) {
		int poin = 0;
		
		if (mentah.startsWith("NG")) { //$NON-NLS-1$
			Log.d(TAG, "isikan tapi mentahnya " + mentah); //$NON-NLS-1$
			return;
		}
		
		{
			Matcher matcher = pattern1.matcher(mentah);
			if (matcher.find()) {
				judul_ = matcher.group(1);
				poin += 5;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat judul"); //$NON-NLS-1$
			}
		}
		
		{
			Matcher matcher = pattern2.matcher(mentah);
			if (matcher.find()) {
				isiHtml_ = matcher.group(1);
				poin += 10;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat isi"); //$NON-NLS-1$
			}
		}
		
		if (this instanceof ArticleRenunganHarian) {
			Matcher matcher = pattern3.matcher(mentah);
			if (matcher.find()) {
				headerHtml_ = matcher.group(1);
				poin += 5;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat header"); //$NON-NLS-1$
			}
		} else {
			Matcher matcher = pattern4.matcher(mentah);
			if (matcher.find()) {
				headerHtml_ = matcher.group(1);
				poin += 5;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat header"); //$NON-NLS-1$
				headerHtml_ = ""; //$NON-NLS-1$
			}
		}
		
		if (poin >= 15) {
			siapPakai_ = true;
		}
	}
	

	@Override
	public String getBodyHtml() {
		return isiHtml_;
	}

	@Override
	public CharSequence getTitle() {
		return judul_;
	}

	@Override
	public String getHeaderHtml() {
		return headerHtml_;
	}
	
	@Override
	public boolean getReadyToUse() {
		return siapPakai_;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
		if (! (o instanceof ArticleFromSabda)) return false;
		if (this == o) return true;
		
		ArticleFromSabda x = (ArticleFromSabda) o;
		
		if (x.tgl_.equals(tgl_) && x.getName().equals(getName())) {
			return true;
		}
		
		return false;
	}
	
	@Override
	public int hashCode() {
		return tgl_.hashCode() * 31 + getName().hashCode();
	}
	
	@Override
	public String getRawEncoding() {
		return "iso-8859-1"; //$NON-NLS-1$
	}
	
	@Override
	public String getDate() {
		return tgl_;
	}
	
	@Override
	public String getUrl() {
		return "http://www.kejut.com/prog/android/alkitab/renungan-proxy.php?nama=" + getName() + "&tgl=" + tgl_; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
