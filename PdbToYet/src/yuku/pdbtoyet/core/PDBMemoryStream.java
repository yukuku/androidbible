package yuku.pdbtoyet.core;

import com.compactbyte.bibleplus.reader.PDBDataStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This buffers an input stream wholly and then provide seeking etc.
 */
public class PDBMemoryStream extends PDBDataStream {
    final byte[] buf;
    final String filename;
    int pos;

    public PDBMemoryStream(final InputStream input, final String filename) throws IOException {
        this.filename = filename;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] buf2 = new byte[4096];
        while (true) {
            final int read = input.read(buf2);
            if (read < 0) break;
            baos.write(buf2, 0, read);
        }
        buf = baos.toByteArray();
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }

    @Override
    public int getCurrentPosition() {
        return pos;
    }

    @Override
    public String getPathName() {
        return filename;
    }

    @Override
    public long getSize() throws IOException {
        return buf.length;
    }

    @Override
    public void read(byte[] data) throws IOException {
        final int len = data.length;
        System.arraycopy(buf, pos, data, 0, len);
        pos += len;
    }

    @Override
    public void skip(int nbytes) throws IOException {
        pos += nbytes;
    }

    @Override
    public void seek(int position) throws IOException {
        pos = position;
    }
}
