package yuku.alkitab.renungan;

import java.util.regex.*;

import android.util.Log;

public abstract class ArtikelDariSabda implements IArtikel {
	private static Pattern pattern1;
	private static Pattern pattern2;
	private static Pattern pattern3;
	private static Pattern pattern4;
	
	static {
		pattern1 = Pattern.compile("<h1>\\s*<div\\s+align=\"center\">(.*?)</div>\\s*</h1>", Pattern.MULTILINE | Pattern.DOTALL);
		pattern2 = Pattern.compile("<td class=\"wj\">(.{100,}?)</td>", Pattern.MULTILINE | Pattern.DOTALL);
		pattern3 = Pattern.compile("<td class=\"wn\".*?>(.*?)</td>", Pattern.MULTILINE | Pattern.DOTALL);
		pattern4 = Pattern.compile("<td width=\"50%\" class=\"wn\" align=\"right\">.*?<b>(.*?)</b>", Pattern.MULTILINE | Pattern.DOTALL);
	}
	
	private String judul_;
	private String isiHtml_;
	private String headerHtml_;
	private boolean siapPakai_;
	protected String tgl_;
	
	protected ArtikelDariSabda(String tgl) {
		tgl_ = tgl;
	}
	
	/**
	 * Pulih dari db
	 * @param siapPakai 
	 */
	public ArtikelDariSabda(String tgl, String judul, String header, String isi, boolean siapPakai) {
		tgl_ = tgl;
		judul_ = judul;
		headerHtml_ = header;
		isiHtml_ = isi;
		siapPakai_ = siapPakai;
	}

	@Override
	public void isikan(String mentah) {
		int poin = 0;
		
		if (mentah.startsWith("NG")) {
			Log.d("alki", "isikan tapi mentahnya " + mentah);
			return;
		}
		
		{
			Matcher matcher = pattern1.matcher(mentah);
			if (matcher.find()) {
				judul_ = matcher.group(1);
				poin += 5;
			} else {
				Log.w("alki", "ArtikelDariSabda ga dapat judul");
			}
		}
		
		{
			Matcher matcher = pattern2.matcher(mentah);
			if (matcher.find()) {
				isiHtml_ = matcher.group(1);
				poin += 10;
			} else {
				Log.w("alki", "ArtikelDariSabda ga dapat isi");
			}
		}
		
		if (this instanceof ArtikelRenunganHarian) {
			Matcher matcher = pattern3.matcher(mentah);
			if (matcher.find()) {
				headerHtml_ = matcher.group(1);
				poin += 5;
			} else {
				Log.w("alki", "ArtikelDariSabda ga dapat header");
			}
		} else {
			Matcher matcher = pattern4.matcher(mentah);
			if (matcher.find()) {
				headerHtml_ = matcher.group(1);
				poin += 10;
			} else {
				Log.w("alki", "ArtikelDariSabda ga dapat header");
				headerHtml_ = "";
			}
		}
		
		if (poin >= 15) {
			siapPakai_ = true;
		}
	}
	

	@Override
	public String getIsiHtml() {
		return isiHtml_;
	}

	@Override
	public CharSequence getJudul() {
		return judul_;
	}

	@Override
	public String getHeaderHtml() {
		return headerHtml_;
	}
	
	@Override
	public boolean getSiapPakai() {
		return siapPakai_;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
		if (! (o instanceof ArtikelDariSabda)) return false;
		
		ArtikelDariSabda x = (ArtikelDariSabda) o;
		
		if (x.tgl_.equals(tgl_) && x.getNama().equals(getNama())) {
			return true;
		}
		
		return false;
	}
	
	@Override
	public String getMentahEncoding() {
		return "iso-8859-1";
	}
	
	@Override
	public String getTgl() {
		return tgl_;
	}
	
	@Override
	public String getUrl() {
		return "http://www.kejut.com/prog/android/alkitab/renungan-proxy.php?nama=" + getNama() + "&tgl=" + tgl_;
	}
}
