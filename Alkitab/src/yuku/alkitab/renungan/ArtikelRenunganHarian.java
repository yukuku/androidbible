package yuku.alkitab.renungan;


public class ArtikelRenunganHarian extends ArtikelDariSabda {
	public ArtikelRenunganHarian(String tgl) {
		super(tgl);
	}
	
	public ArtikelRenunganHarian(String tgl, String judul, String headerHtml, String isiHtml, boolean siapPakai) {
		super(tgl, judul, headerHtml, isiHtml, siapPakai);
	}

	@Override
	public CharSequence getKopiraitHtml() {
		return "Renungan Harian.<br/>diambil dari <b>sabda.org</b>";
	}

	@Override
	public String getNama() {
		return "rh";
	}
	
	@Override
	public String getNamaUmum() {
		return "Renungan Harian";
	}
}


