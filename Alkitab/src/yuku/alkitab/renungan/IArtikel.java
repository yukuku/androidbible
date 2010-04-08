package yuku.alkitab.renungan;


public interface IArtikel {
	CharSequence getJudul();
	String getHeaderHtml();
	String getIsiHtml();
	String getTgl();
	boolean getSiapPakai();
	
	CharSequence getKopiraitHtml();
	
	//# dipake buat orang luar 
	String getNama();
	String getUrl();
	String getMentahEncoding();
	void isikan(String mentah);
}
