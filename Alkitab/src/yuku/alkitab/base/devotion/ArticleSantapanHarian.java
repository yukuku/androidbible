package yuku.alkitab.base.devotion;


public class ArticleSantapanHarian extends ArticleFromSabda {
	public ArticleSantapanHarian(String date) {
		super(date);
	}

	public ArticleSantapanHarian(String date, String title, String headerHtml, String bodyHtml, boolean readyToUse) {
		super(date, title, headerHtml, bodyHtml, readyToUse);
	}

	@Override public CharSequence getCopyrightHtml() {
		return "__________<br/>" +
		"<small>Santapan Harian / e-Santapan Harian<br/>" + 
		"Bahan saat teduh yang diterbitkan secara teratur oleh Persekutuan Pembaca Alkitab (PPA) dan diterbitkan secara elektronik oleh Yayasan Lembaga SABDA (YLSA). <br/>" + 
		"© 1999-2012 Yayasan Lembaga SABDA (YLSA). <br/>" + 
		"Isi boleh disimpan untuk tujuan pribadi atau non-komersial. Atas setiap publikasi atau pencetakan wajib menyebutkan alamat situs SABDA.org sebagai sumber tulisan dan mengirim pemberitahuan ke webmaster@sabda.org</small>";
	}

	@Override
	public String getName() {
		return "sh"; //$NON-NLS-1$
	}
	
	@Override
	public String getDevotionTitle() {
		return "Santapan Harian"; //$NON-NLS-1$
	}
}
