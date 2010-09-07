package yuku.alkitab.base.renungan;


public class ArtikelSantapanHarian extends ArtikelDariSabda {
	public ArtikelSantapanHarian(String tgl) {
		super(tgl);
	}

	public ArtikelSantapanHarian(String tgl, String judul, String headerHtml, String isiHtml, boolean siapPakai) {
		super(tgl, judul, headerHtml, isiHtml, siapPakai);
	}

	@Override
	public CharSequence getKopiraitHtml() {
		return "Santapan Harian.<br/>diambil dari <b>sabda.org</b>"; //$NON-NLS-1$
	}

	@Override
	public String getNama() {
		return "sh"; //$NON-NLS-1$
	}
	
	@Override
	public String getNamaUmum() {
		return "Santapan Harian"; //$NON-NLS-1$
	}
}
