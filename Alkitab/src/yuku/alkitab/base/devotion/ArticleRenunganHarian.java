package yuku.alkitab.base.devotion;


public class ArticleRenunganHarian extends ArticleFromSabda {
	public ArticleRenunganHarian(String date) {
		super(date);
	}
	
	public ArticleRenunganHarian(String date, String title, String headerHtml, String bodyHtml, boolean readyToUse) {
		super(date, title, headerHtml, bodyHtml, readyToUse);
	}

	@Override public CharSequence getCopyrightHtml() {
		return "__________<br/>" +
				"<small>Renungan Harian / e-Renungan Harian<br/>" +
				"Bahan renungan yang diterbitkan secara teratur oleh Yayasan Gloria dan diterbitkan secara elektronik oleh Yayasan Lembaga SABDA (YLSA).<br/>" + 
				"© 1999-2012 Yayasan Lembaga SABDA (YLSA). <br/>" + 
				"Isi boleh disimpan untuk tujuan pribadi atau non-komersial. Atas setiap publikasi atau pencetakan wajib menyebutkan alamat situs SABDA.org sebagai sumber tulisan dan mengirim pemberitahuan ke webmaster@sabda.org</small>";
	}

	@Override
	public String getName() {
		return "rh"; //$NON-NLS-1$
	}
	
	@Override
	public String getDevotionTitle() {
		return "Renungan Harian"; //$NON-NLS-1$
	}
}


