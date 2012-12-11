package yuku.alkitab.yes2.model;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import yuku.bintex.BintexWriter;

public class Text implements SectionContent {
	private final Charset charset;
	public String[] verses;

	public Text(Charset charset) {
		this.charset = charset;
	}

	@Override public void toBytes(BintexWriter writer) throws Exception {
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
