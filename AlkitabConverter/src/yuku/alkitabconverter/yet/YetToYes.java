package yuku.alkitabconverter.yet;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.PerikopBlok;
import yuku.alkitab.yes.YesFile.PerikopIndex;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.yes_common.YesCommon;
import yuku.alkitabconverter.yet.YetFileInput.YetFileInputResult;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class YetToYes {
	@Parameter private List<String> params = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		YetToYes main = new YetToYes();
		new JCommander(main, args);

		int retval = main.main();
		System.exit(retval);
	}

	private int main() throws Exception {
		if (params.size() < 1) {
			System.err.println("Usage parameters: <yet-file> [<yes-file>]");
			return 1;
		}
		
		String yetfile = params.get(0);
		String yesfile;
		if (params.size() >= 2) {
			yesfile = params.get(1);
		} else {
			yesfile = yetfile.endsWith(".yet") ? (yetfile.substring(0, yetfile.length() - 1) + "s"): yetfile + ".yes";
		}
	
		System.err.println("input:  " + yetfile);
		System.err.println("output: " + yesfile);
		
		YetFileInputResult result = new YetFileInput().parse(yetfile);
		if (result.recs == null) {
			System.err.println("yet file doesn't contain any verses");
			return 1;
		} else if (result.infos == null) {
			System.err.println("yet file doesn't contain any info");
			return 1;
		} else if (result.infos.get("longName") == null) {
			System.err.println("yet file doesn't contain info 'longName' which is required");
			return 1;
		} else if (result.bookNames == null) {
			System.err.println("yet file doesn't contain any book names");
			return 1;
		}
		
		{ // check if all book names are available
			Set<Integer> books_1 = new HashSet<Integer>();
			for (Rec rec: result.recs) {
				books_1.add(rec.book_1);
			}
			
			for (Integer book_1: books_1) {
				String bookName = result.bookNames.get(book_1 - 1);
				if (bookName == null) {
					System.err.println("yet file doesn't contain book name for book " + book_1);
					return 1;
				}
			}
		}
		
		InfoEdisi infoEdisi = YesCommon.infoEdisi(null, result.infos.get("shortName"), result.infos.get("longName"), result.numberOfBooks, result.pericopeData == null? 0: 1, result.infos.get("description"), 2 /* utf-8 */);
		InfoKitab infoKitab = YesCommon.infoKitab(result.recs, "utf-8", result.bookNames);
		Teks teks = YesCommon.teks(result.recs, "utf-8");
		YesFile out;
		if (result.pericopeData != null) {
			out = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks, new PerikopBlok(result.pericopeData), new PerikopIndex(result.pericopeData));
		} else {
			out = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks);
		}
		
		out.output(new RandomAccessFile(yesfile, "rw"));
		return 0;
	}
}
