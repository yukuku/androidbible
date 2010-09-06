package yuku.alkitab.base.renungan;


public interface IArtikel {
	CharSequence getJudul();
	String getHeaderHtml();
	String getIsiHtml();
	String getTgl();
	boolean getSiapPakai();
	
	CharSequence getKopiraitHtml();
	
	//# dipake buat orang luar 
	String getNama();
	String getNamaUmum();
	String getUrl();
	String getMentahEncoding();
	void isikan(String mentah);
}
