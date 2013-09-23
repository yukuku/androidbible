package yuku.alkitab.base.cp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Provider to support easy sharing of files (public and private) between apps.
 *
 * <p>AUTHORITY/external/path means external storage path
 * <p>AUTHORITY/cache/path means internal cache path
 */
public class FileProvider extends ContentProvider {
    private static final String[] COLUMNS = { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };

    @Override
    public boolean onCreate() {
	    return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
	    final File file = getFileForUri(uri);

	    if (file == null) {
		    throw new IllegalArgumentException("Unknown path in uri: " + uri);
	    }

	    if (!file.isFile() || !file.canRead()) {
		    throw new IllegalArgumentException("Can't read file in uri: " + uri);
	    }

	    if (projection == null) {
            projection = COLUMNS;
        }

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = file.getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = file.length();
            }
        }

        cols = copyOf(cols, i);
        values = copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

	private File getFileForUri(final Uri uri) {
		final String externalPrefix = "/external/";
		final String cachePrefix = "/cache/";
		final String uriPath = uri.getPath();

		if (uriPath.startsWith(externalPrefix)) {
			return new File(Environment.getExternalStorageDirectory().getAbsolutePath(), uriPath.substring(externalPrefix.length()));
		} else if (uriPath.startsWith(cachePrefix)) {
			return new File(getContext().getCacheDir().getAbsolutePath(), uriPath.substring(cachePrefix.length()));
		}
		return null;
	}

	@Override
    public String getType(Uri uri) {
		final File file = getFileForUri(uri);

		final int lastDot = file.getName().lastIndexOf('.');
		if (lastDot >= 0) {
			final String extension = file.getName().substring(lastDot + 1);
			final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
			if (mime != null) {
				return mime;
			}
		}

		return "application/octet-stream";
	}

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
	    throw new UnsupportedOperationException("No external deletes");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
	    final File file = getFileForUri(uri);
	    final int fileMode = modeToMode(mode);
	    return ParcelFileDescriptor.open(file, fileMode);
    }

	private static String[] copyOf(String[] original, int newLength) {
		final String[] result = new String[newLength];
		System.arraycopy(original, 0, result, 0, newLength);
		return result;
	}

	private static Object[] copyOf(Object[] original, int newLength) {
		final Object[] result = new Object[newLength];
		System.arraycopy(original, 0, result, 0, newLength);
		return result;
	}

	/**
	 * Copied from ContentResolver.java
	 */
	private static int modeToMode(String mode) {
		int modeBits;
		if ("r".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
		} else if ("w".equals(mode) || "wt".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
			| ParcelFileDescriptor.MODE_CREATE
			| ParcelFileDescriptor.MODE_TRUNCATE;
		} else if ("wa".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
			| ParcelFileDescriptor.MODE_CREATE
			| ParcelFileDescriptor.MODE_APPEND;
		} else if ("rw".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_READ_WRITE
			| ParcelFileDescriptor.MODE_CREATE;
		} else if ("rwt".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_READ_WRITE
			| ParcelFileDescriptor.MODE_CREATE
			| ParcelFileDescriptor.MODE_TRUNCATE;
		} else {
			throw new IllegalArgumentException("Invalid mode: " + mode);
		}
		return modeBits;
	}
}
