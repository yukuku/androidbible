package yuku.alkitab.base.renungan;


public class ArtikelSantapanHarian extends ArtikelDariSabda {
	public ArtikelSantapanHarian(String tgl) {
		super(tgl);
	}

	public ArtikelSantapanHarian(String tgl, String judul, String headerHtml, String isiHtml, boolean siapPakai) {
		super(tgl, judul, headerHtml, isiHtml, siapPakai);
	}

	@Override public CharSequence getKopiraitHtml() {
		return "__________<br/>" +
		"Santapan Harian / e-Santapan Harian<br/>" + 
		"Bahan saat teduh yang diterbitkan secara teratur oleh Persekutuan Pembaca Alkitab (PPA) dan diterbitkan secara elektronik oleh Yayasan Lembaga SABDA (YLSA). <br/>" + 
		"© 1999-2012 Yayasan Lembaga SABDA (YLSA). <br/>" + 
		"Isi boleh disimpan untuk tujuan pribadi atau non-komersial. Atas setiap publikasi atau pencetakan wajib menyebutkan alamat situs SABDA.org sebagai sumber tulisan dan mengirim pemberitahuan ke webmaster@sabda.org";
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
