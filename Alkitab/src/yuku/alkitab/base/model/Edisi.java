package yuku.alkitab.base.model;

import java.io.IOException;

import yuku.alkitab.base.AddonManager;
import yuku.alkitab.base.storage.*;
import yuku.bintex.BintexReader;

public class Edisi {
	public String nama;
	public 	String judul;
	public int nkitab;
	public int perikopAda; // 0=gaada; 1=versi 1 (pake BintexReader dan utf16) 
	public Pembaca pembaca;
	public String url;// bisa null
	
	public Kitab[] volatile_xkitab;
	public IndexPerikop volatile_indexPerikop;
	
	public static Edisi baca(BintexReader in) throws IOException {
		Edisi e = new Edisi();

		String awal = in.readShortString();

		if (awal.equals("Edisi")) { //$NON-NLS-1$
			while (true) {
				String key = in.readShortString();
				if (key.equals("nama")) { //$NON-NLS-1$
					e.nama = in.readShortString();
				} else if (key.equals("judul")) { //$NON-NLS-1$
					e.judul = in.readShortString();
				} else if (key.equals("nkitab")) { //$NON-NLS-1$
					e.nkitab = in.readInt();
				} else if (key.equals("perikopAda")) { //$NON-NLS-1$
					e.perikopAda = in.readInt();
				} else if (key.equals("pembaca")) { //$NON-NLS-1$
					String v = in.readShortString();
					if ("internal".equals(v)) { //$NON-NLS-1$
						e.pembaca = new InternalPembaca(new PembacaDecoder.Ascii());
					} else if ("internal-utf8".equals(v)) { //$NON-NLS-1$
						e.pembaca = new InternalPembaca(new PembacaDecoder.Utf8());
					} else if ("yes".equals(v)) { //$NON-NLS-1$
						e.pembaca = new YesPembaca(AddonManager.getEdisiPath(e.nama));
					}
				} else if (key.equals("url")) { //$NON-NLS-1$
					e.url = in.readShortString();
				} else if (key.equals("end")) { //$NON-NLS-1$
					break;
				}
			}
			
			return e;
		} else {
			return null;
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s (%s)", judul, nama); //$NON-NLS-1$
	}
}
