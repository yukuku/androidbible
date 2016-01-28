package yuku.pdbtoyet.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This buffers an input stream wholly and then provide seeking etc.
 */
public class MemoryStream {
    final byte[] buf;

	// reads everything until end of stream
    public MemoryStream(final InputStream input) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] buf2 = new byte[4096];
        while (true) {
            final int read = input.read(buf2);
            if (read < 0) break;
            baos.write(buf2, 0, read);
        }
        buf = baos.toByteArray();
    }

	public InputStream asInputStream() {
		return new ByteArrayInputStream(buf);
	}
}
