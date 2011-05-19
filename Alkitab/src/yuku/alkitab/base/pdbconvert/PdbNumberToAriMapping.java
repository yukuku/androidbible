package yuku.alkitab.base.pdbconvert;

public class PdbNumberToAriMapping {
	public static final String TAG = PdbNumberToAriMapping.class.getSimpleName();
	
	// http://sourceforge.net/projects/palmbibleplus/files/zDocumentation/1.0/
	public static final int[] bookNumber = {
		10,
		20,
		30,
		40,
		50,
		60,
		70,
		80,
		90,
		100, // 2 sam
		110,
		120,
		130,
		140, // 2 taw
		150, // ezra
		160, // nehemia
		190, // ester
		220, // job/ayub
		230, // ps
		240, // amsal
		250, // pengkotbah/ecc
		260, // kidung
		290, // yesaya
		300,
		310, // ratapan
		330, // ezekiel
		340,
		350,
		360,
		370,
		380,
		390,
		400,
		410,
		420,
		430,
		440,
		450,
		460, // mal
		470, // matius
		480,
		490,
		500,
		510,
		520,
		530,
		540,
		550,
		560,
		570,
		580, 
		590,
		600,
		610,
		620,
		630,
		640,
		650,
		660,
		670,
		680,
		690,
		700,
		710,
		720,
		730, // nomer kitab Ari: 65
		///////////
		
		145, // 1 esdras // nomer kitab Ari: 66
		146, // 2 esdras
		170, // tobit #katolik
		180, // judit #katolik
		200, // 1 makabe  #katolik // nomer kitab Ari: 70 
		210, // 2 makabe  #katolik
		215, // 3 makabe
		216, // 4 makabe
		231, // Psalms (from Heb.) Ps (H) Vulg.: Jerome's translation from the Hebrew
		235, // Odes // nomer kitab Ari: 75
		270, // wisdom of solomon  #katolik
		280, // sirach / Ecclesiasticus #katolik
		285, // Psalms of Solomon
		315, // Letter of Jeremiah 
		320, // baruk  #katolik // nomer kitab Ari: 80
		335, // susanna
		345, // Prayer of Azariah and the Song of the Three Jews
		346, // Bel and the Dragon
		790, // Prayer of Manasseh
		980, // Additions to Esther // nomer kitab Ari: 85
		991, // maxmur 151
		1802, // Epistle to the Laodicaeans // nomer kitab Ari: 87
	};
	
	public static int pdbNumberToAriKitab(int pdbNumber) {
		for (int i = 0; i < bookNumber.length; i++) {
			if (bookNumber[i] == pdbNumber) {
				return i;
			}
		}
		return -1;
	}
	
	public static int ariKitabToPdbNumber(int ari_kitab) {
		if (ari_kitab < 0 || ari_kitab >= bookNumber.length) return -1;
		return bookNumber[ari_kitab];
	}
}
