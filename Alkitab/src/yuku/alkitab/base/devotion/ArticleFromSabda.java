package yuku.alkitab.base.devotion;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ArticleFromSabda implements DevotionArticle {
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
	
	String title;
	String bodyHtml;
	String headerHtml;
	boolean readyToUse;
	String date;
	
	protected ArticleFromSabda(String date) {
		this.date = date;
	}
	
	/**
	 * Pulih dari db
	 * @param readyToUse
	 */
	public ArticleFromSabda(String date, String title, String headerHtml, String bodyHtml, boolean readyToUse) {
		this.date = date;
		this.title = title;
		this.headerHtml = headerHtml;
		this.bodyHtml = bodyHtml;
		this.readyToUse = readyToUse;
	}

	@Override
	public void fillIn(String raw) {
		int poin = 0;
		
		if (raw.startsWith("NG")) { //$NON-NLS-1$
			Log.d(TAG, "isikan tapi mentahnya " + raw); //$NON-NLS-1$
			return;
		}
		
		{
			Matcher matcher = pattern1.matcher(raw);
			if (matcher.find()) {
				title = matcher.group(1);
				poin += 5;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat judul"); //$NON-NLS-1$
			}
		}
		
		{
			Matcher matcher = pattern2.matcher(raw);
			if (matcher.find()) {
				bodyHtml = matcher.group(1);
				poin += 10;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat isi"); //$NON-NLS-1$
			}
		}
		
		if (this instanceof ArticleRenunganHarian) {
			Matcher matcher = pattern3.matcher(raw);
			if (matcher.find()) {
				headerHtml = matcher.group(1);
				poin += 5;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat header"); //$NON-NLS-1$
			}
		} else {
			Matcher matcher = pattern4.matcher(raw);
			if (matcher.find()) {
				headerHtml = matcher.group(1);
				poin += 5;
			} else {
				Log.w(TAG, "ArtikelDariSabda ga dapat header"); //$NON-NLS-1$
				headerHtml = ""; //$NON-NLS-1$
			}
		}
		
		if (poin >= 15) {
			readyToUse = true;
		}
	}
	
	@Override
	public boolean getReadyToUse() {
		return readyToUse;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
		if (! (o instanceof ArticleFromSabda)) return false;
		if (this == o) return true;
		
		ArticleFromSabda x = (ArticleFromSabda) o;
		
		if (x.date.equals(date) && x.getName().equals(getName())) {
			return true;
		}
		
		return false;
	}
	
	@Override
	public int hashCode() {
		return date.hashCode() * 31 + getName().hashCode();
	}
	
	@Override
	public String getRawEncoding() {
		return "utf-8"; // the proxy server outputs in utf-8, unlike the source where it is in latin-1
	}
	
	@Override
	public String getDate() {
		return date;
	}
	
	@Override
	public String getUrl() {
		return "https://alkitab-host.appspot.com/devotion/get?name=" + getName() + "&date=" + date; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	@Override public String[] getHeaderTitleBody() {
		return new String[] {headerHtml, title, bodyHtml};
	}
}
