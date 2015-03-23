package yuku.alkitab.base.devotion;

public abstract class ArticleFromSabda extends DevotionArticle {
	// json model class for structure from server
	protected static class BodyJson {
		public String tanggal;
		public String ayat;
		public String ayat_setahun;
		public Nats nats;
		public String judul;
		public String isi;
		public String catatan_kaki;

		static class Nats {
			public String nats_isi;
			public String nats_ayat;
		}
	}

	String body;
	boolean readyToUse;
	String date;

	protected ArticleFromSabda(String date) {
		this.date = date;
	}

	public ArticleFromSabda(String date, String body, boolean readyToUse) {
		this.date = date;
		this.body = body;
		this.readyToUse = readyToUse;
	}

	@Override
	public void fillIn(String raw) {
		body = raw;
		readyToUse = !raw.startsWith("NG");
	}

	@Override
	public boolean getReadyToUse() {
		return readyToUse;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
		if (!(o instanceof ArticleFromSabda)) return false;
		if (this == o) return true;

		ArticleFromSabda x = (ArticleFromSabda) o;

		return x.date.equals(date) && x.getKind().equals(getKind());
	}

	@Override
	public int hashCode() {
		return date.hashCode() * 31 + getKind().name.hashCode();
	}

	@Override
	public String getDate() {
		return date;
	}

	@Override
	public String[] getHeaderTitleBody() {
		return new String[]{null, null, body};
	}
}
