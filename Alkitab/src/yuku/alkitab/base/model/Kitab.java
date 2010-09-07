package yuku.alkitab.base.model;

import java.io.IOException;

import yuku.bintex.BintexReader;

public class Kitab {
	public int[] nayat;
	public int npasal;
	public int[] pasal_offset;
	public String nama;
	public String judul;
	public String file;
	public int pos;
	/** Hanya dipake di YesPembaca */
	public int offset = -1;

	public static Kitab baca(BintexReader in, int pos) throws IOException {
		Kitab k = new Kitab();
		k.pos = pos;
		
		String awal = in.readShortString();

		if (awal.equals("Kitab")) { //$NON-NLS-1$
			while (true) {
				String key = in.readShortString();
				if (key.equals("nama")) { //$NON-NLS-1$
					k.nama = in.readShortString();
				} else if (key.equals("judul")) { //$NON-NLS-1$
					k.judul = in.readShortString();
					
					k.judul = bersihinJudul(k.judul);
				} else if (key.equals("file")) { //$NON-NLS-1$
					k.file = in.readShortString();
				} else if (key.equals("npasal")) { //$NON-NLS-1$
					k.npasal = in.readInt();
				} else if (key.equals("nayat")) { //$NON-NLS-1$
					k.nayat = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.nayat[i] = in.readUint8();
					}
				} else if (key.equals("pasal_offset")) { //$NON-NLS-1$
					k.pasal_offset = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.pasal_offset[i] = in.readInt();
					}
				} else if (key.equals("uda")) { //$NON-NLS-1$
					break;
				}
			}
			
			return k;
		} else {
			return null;
		}
	}
	
	private static String bersihinJudul(String judul) {
		return judul.replace('_', ' ');
	}

	@Override
	public String toString() {
		return String.format("%s (%d pasal)", judul, npasal); //$NON-NLS-1$
	}
}
