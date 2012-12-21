package yuku.alkitab.base.renungan;


public class ArtikelRenunganHarian extends ArtikelDariSabda {
	public ArtikelRenunganHarian(String tgl) {
		super(tgl);
	}
	
	public ArtikelRenunganHarian(String tgl, String judul, String headerHtml, String isiHtml, boolean siapPakai) {
		super(tgl, judul, headerHtml, isiHtml, siapPakai);
	}

	@Override public CharSequence getKopiraitHtml() {
		return "__________<br/>" +
				"<small>Renungan Harian / e-Renungan Harian<br/>" +
				"Bahan renungan yang diterbitkan secara teratur oleh Yayasan Gloria dan diterbitkan secara elektronik oleh Yayasan Lembaga SABDA (YLSA).<br/>" + 
				"© 1999-2012 Yayasan Lembaga SABDA (YLSA). <br/>" + 
				"Isi boleh disimpan untuk tujuan pribadi atau non-komersial. Atas setiap publikasi atau pencetakan wajib menyebutkan alamat situs SABDA.org sebagai sumber tulisan dan mengirim pemberitahuan ke webmaster@sabda.org</small>";
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


