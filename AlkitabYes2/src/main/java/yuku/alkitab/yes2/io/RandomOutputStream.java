package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A class that implements OutputStream but supports random access by calling {@link #seek(long)} and
 * {@link #getFilePointer()}.
 */
public abstract class RandomOutputStream extends OutputStream {
    public abstract void seek(long n) throws IOException;

    public abstract long getFilePointer() throws IOException;
}
