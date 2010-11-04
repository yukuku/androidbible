package yuku.alkitab.base.config;

public class Config {
	public boolean menuRenungan = true;
	public boolean menuGebug = false;
	public boolean menuEdisi = true;
	public boolean menuBantuan = true;
	public boolean menuDonasi = false;

	public Config(boolean menuRenungan, boolean menuGebug, boolean menuEdisi, boolean menuBantuan, boolean menuDonasi) {
		this.menuRenungan = menuRenungan;
		this.menuGebug = menuGebug;
		this.menuEdisi = menuEdisi;
		this.menuBantuan = menuBantuan;
		this.menuDonasi = menuDonasi;
	}
}
