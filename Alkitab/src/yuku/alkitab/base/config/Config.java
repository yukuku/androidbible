package yuku.alkitab.base.config;

public class Config {
	public boolean menuRenungan = true;
	public boolean menuGebug = false;
	public boolean menuEdisi = true;
	public boolean menuBantuan = true;

	public Config(boolean menuRenungan, boolean menuGebug, boolean menuEdisi, boolean menuBantuan) {
		this.menuRenungan = menuRenungan;
		this.menuGebug = menuGebug;
		this.menuEdisi = menuEdisi;
		this.menuBantuan = menuBantuan;
	}
}
