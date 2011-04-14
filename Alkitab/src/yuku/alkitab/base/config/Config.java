package yuku.alkitab.base.config;

public class Config {
	public final String edisiPrefix;
	public final String edisiJudul;
	public final boolean menuRenungan;
	public final boolean menuGebug;
	public final boolean menuEdisi;
	public final boolean menuBantuan;
	public final boolean menuDonasi;

	public Config(String edisiPrefix, String edisiJudul, boolean menuRenungan, boolean menuGebug, boolean menuEdisi, boolean menuBantuan, boolean menuDonasi) {
		this.edisiPrefix = edisiPrefix;
		this.edisiJudul = edisiJudul;
		this.menuRenungan = menuRenungan;
		this.menuGebug = menuGebug;
		this.menuEdisi = menuEdisi;
		this.menuBantuan = menuBantuan;
		this.menuDonasi = menuDonasi;
	}
}
