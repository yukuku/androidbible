package yuku.pdbtoyet.app;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import yuku.alkitab.yes2.io.MemoryRandomOutputStream;
import yuku.alkitabconverter.yet.YetFileOutput;
import yuku.pdbtoyet.core.PDBMemoryStream;
import yuku.pdbtoyet.core.PdbInput;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PdbToYet {
	@Parameter private List<String> params = new ArrayList<String>();
	@Parameter(names = "--help", help = true, description = "Show this help") private boolean help = false;
	@Parameter(names = "--encoding", description = "Encoding of PDB file") private String encoding = "utf-8";

	public static void main(String[] args) throws Exception {
		PdbToYet main = new PdbToYet();
		JCommander jc = new JCommander(main, args);

		if (main.help) {
			jc.setProgramName("java -jar PdbToYet.jar");
			jc.usage();
			System.exit(0);
		}
		
		int retval = main.main();
		System.exit(retval);
	}

	private int main() throws Exception {
		if (params.size() < 1) {
			System.err.println("Usage parameters: <pdb-file> [<yet-file>]");
			return 1;
		}
		
		final String pdbfile = params.get(0);
		final String yetfile;
		if (params.size() >= 2) {
			yetfile = params.get(1);
		} else {
			yetfile = pdbfile.endsWith(".pdb") ? (pdbfile.substring(0, pdbfile.length() - 3) + "yet"): pdbfile + ".yet";
		}
	
		System.err.println("input:  " + pdbfile);
		System.err.println("output: " + yetfile);

		final PdbInput.Result result;
		try (final FileInputStream input = new FileInputStream(pdbfile)) {
			final PDBMemoryStream pdbMemoryStream = new PDBMemoryStream(input, pdbfile);

			final PdbInput pdbInput = new PdbInput();
			final PdbInput.Params params = new PdbInput.Params();
			params.includeAddlTitle = true;
			params.inputEncoding = encoding;

			result = pdbInput.read(pdbMemoryStream, params);
			System.err.println("Result: " + result.textDb.getBookCount() + " books");
		}

		final MemoryRandomOutputStream memory = new MemoryRandomOutputStream();

		// convert to outputformat
		final YetFileOutput yet = new YetFileOutput(memory);
		yet.setVersionInfo(result.versionInfo);
		yet.setTextDb(result.textDb);
		yet.setPericopeData(result.pericopeData);
		yet.setXrefDb(null);
		yet.setFootnoteDb(null);
		yet.write();

		// write to output
		try (final FileOutputStream output = new FileOutputStream(yetfile)) {
			output.write(memory.getBuffer(), memory.getBufferOffset(), memory.getBufferLength());
		}

		return 0;
	}
}
