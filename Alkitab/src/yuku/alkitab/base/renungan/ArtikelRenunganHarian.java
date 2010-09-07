package yuku.alkitab.base.renungan;


public class ArtikelRenunganHarian extends ArtikelDariSabda {
	public ArtikelRenunganHarian(String tgl) {
		super(tgl);
	}
	
	public ArtikelRenunganHarian(String tgl, String judul, String headerHtml, String isiHtml, boolean siapPakai) {
		super(tgl, judul, headerHtml, isiHtml, siapPakai);
	}

	@Override
	public CharSequence getKopiraitHtml() {
		return "Renungan Harian.<br/>diambil dari <b>sabda.org</b>"; //$NON-NLS-1$
	}

	@Override
	public String getNama() {
		return "rh"; //$NON-NLS-1$
	}
	
	@Override
	public String getNamaUmum() {
		return "Renungan Harian"; //$NON-NLS-1$
	}
}


