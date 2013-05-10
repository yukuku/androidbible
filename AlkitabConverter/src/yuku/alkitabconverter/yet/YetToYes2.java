package yuku.alkitabconverter.yet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.yes_common.Yes2Common;
import yuku.alkitabconverter.yet.YetFileInput.YetFileInputResult;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class YetToYes2 {
	@Parameter private List<String> params = new ArrayList<String>();
	@Parameter(names = "--help", help = true, description = "Show this help") private boolean help = false;
	@Parameter(names = "--no-compress", description = "Disable compression on the resultant yes file") private boolean nocompress = false;
	
	public static void main(String[] args) throws Exception {
		YetToYes2 main = new YetToYes2();
		JCommander jc = new JCommander(main, args);

		if (main.help) {
			jc.setProgramName("java -jar YetToYes2.jar");
			jc.usage();
			System.exit(0);
		}
		
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
		if (result == null) {
			// error message given by parse above
			return 1;
		}
		
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
		
		Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();
		versionInfo.locale = result.infos.get("locale");
		versionInfo.shortName = result.infos.get("shortName");
		versionInfo.longName = result.infos.get("longName");
		versionInfo.description = result.infos.get("description");
		versionInfo.setBookNames(result.bookNames);
		
		// convert recs to textdb
		TextDb textDb = new TextDb();
		for (Rec rec: result.recs) {
			textDb.append(rec.book_1 - 1, rec.chapter_1, rec.verse_1, rec.text, -1);
		}
		
		boolean compressed = !nocompress;
		Yes2Common.createYesFile(new File(yesfile), versionInfo, textDb, result.pericopeData, compressed); 
		
		return 0;
	}
}
