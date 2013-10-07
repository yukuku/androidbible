package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.InputStream;

public abstract class RandomInputStream extends InputStream {
	public abstract void seek(long n) throws IOException;

	public abstract long getFilePointer() throws IOException;
}
