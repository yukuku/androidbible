package yuku.alkitab.yes2.section;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;

public class Text extends SectionContent implements SectionContent.Writer {
	private final Charset charset;
	private final List<String> verses;

	public Text(Charset charset, List<String> verses) {
		super("text________");
		this.charset = charset;
		this.verses = verses;
	}

	@Override public void write(BintexWriter writer) throws Exception {
		// int verse_count
		writer.writeInt(verses.size());
		
		for (String verse : verses) {
			ByteBuffer buf = charset.encode(verse);
			byte[] bytes = new byte[buf.limit()];
			buf.position(0);
			buf.get(bytes);
			writer.writeRaw(bytes);
			writer.writeUint8('\n');
		}
	}
}
