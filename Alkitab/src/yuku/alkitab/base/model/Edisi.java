package yuku.alkitab.base.model;

import yuku.alkitab.base.storage.Pembaca;

public class Edisi {
	public Pembaca pembaca;
	
	public Edisi(Pembaca pembaca) {
		this.pembaca = pembaca;
	}
	
	private Kitab[] volatile_xkitab;
	private IndexPerikop volatile_indexPerikop;
	private boolean volatile_indexPerikopSudahCobaBaca = false;
	
	public synchronized Kitab[] getXkitab() {
		if (volatile_xkitab == null) {
			volatile_xkitab = this.pembaca.bacaInfoKitab();
		}
		return volatile_xkitab;
	}
	
	public synchronized IndexPerikop getIndexPerikop() {
		if (!volatile_indexPerikopSudahCobaBaca) {
			volatile_indexPerikop = this.pembaca.bacaIndexPerikop();
			volatile_indexPerikopSudahCobaBaca = true;
		}
		return volatile_indexPerikop;
	}
}
