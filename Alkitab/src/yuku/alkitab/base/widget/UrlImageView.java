package yuku.alkitab.base.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import yuku.afw.App;
import yuku.afw.D;
import yuku.afw.rpc.BaseData;
import yuku.afw.rpc.ImageData;
import yuku.afw.rpc.Response;
import yuku.afw.rpc.Response.Validity;
import yuku.afw.rpc.UrlLoader;
import yuku.alkitab.R;

public class UrlImageView extends ImageView {
	public static final String TAG = UrlImageView.class.getSimpleName();
	public static final String DISKCACHE_PREFIX = "UrlImageView/"; //$NON-NLS-1$
	
	public enum State {
		none,
		loading,
		loaded_from_memory,
		loaded_from_disk,
		loaded_from_url,
		/** This may happen if setUrl(null) is called */
		loaded_from_default,
		;
		public boolean isLoaded() {
			return this == loaded_from_disk || this == loaded_from_memory || this == loaded_from_url || this == loaded_from_default;
		}
	}

	public interface OnStateChangeListener {
		void onStateChange(UrlImageView v, State newState, String url);
	}
	
	public interface DiskCache {
		byte[] retrieve(String key);
		void store(String key, byte[] data);
	}
	
	public static class DefaultDiskCache implements DiskCache {
		private String getPathcode(final String path, final int version) {
			StringBuilder res = new StringBuilder();
			for (int i = path.length() - 1; i >= 0; i--) {
				char c = path.charAt(i);
				if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')) {
					res.append(c);
				} else {
					res.append(Integer.toHexString(c));
				}
				if (res.length() >= 50) {
					res.setLength(50);
					break;
				}
			}
			res.reverse();
			res.append('-');
			res.append(Integer.toHexString(path.hashCode()));
			res.append("-v"); //$NON-NLS-1$
			res.append(version);
			res.append(".cache"); //$NON-NLS-1$
			return res.toString();
		}
		
		@Override public byte[] retrieve(String key) {
			long startTime = 0;
			if (D.EBUG) startTime = SystemClock.uptimeMillis();
			
			final String pathcode = getPathcode(key, App.getVersionCode());
			
			File cacheDir = new File(App.context.getCacheDir(), "UrlImageView-diskcache"); //$NON-NLS-1$
			File cacheFile = new File(cacheDir, pathcode);
			
			byte[] buf = null;
			try {
				if (cacheFile.exists()) {
					long length = cacheFile.length();
					buf = new byte[(int) length];
					FileInputStream fis = new FileInputStream(cacheFile);
					int read = fis.read(buf, 0, buf.length);
					if (read == length) {
						return buf;
					} else {
						return null;
					}
				} else {
					return null;
				}
			} catch (Exception e) {
				Log.d(TAG, "Error when reading disk cache: ", e); //$NON-NLS-1$
				return null;
			} finally {
				if (D.EBUG) Log.d(TAG, "retrieveFromDiskCache (" + (buf == null? "null": buf.length + " bytes") + ") took " + (SystemClock.uptimeMillis() - startTime) + " ms. From '" + key + "', thread=" + Thread.currentThread().getId()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			}
		}

		@Override public void store(final String key, final byte[] data) {
			final String pathcode = getPathcode(key, App.getVersionCode());
			
			final long callTime;
			if (D.EBUG) {
				callTime = SystemClock.uptimeMillis();
			} else {
				callTime = 0;
			}
			
			new AsyncTask<Void, Void, Void>() {
				@Override protected Void doInBackground(Void... params) {
					long startTime = 0;  
					if (D.EBUG) {
						startTime = SystemClock.uptimeMillis();
					}
					
					File cacheDir = new File(App.context.getCacheDir(), "UrlImageView-diskcache"); //$NON-NLS-1$
					cacheDir.mkdirs();
					File cacheFile = new File(cacheDir, pathcode);
					if (data == null) {
						cacheFile.delete();
					} else {
						File cacheFileTmp = new File(cacheDir, pathcode + ".tmp"); //$NON-NLS-1$
						
						try {
							FileOutputStream os = new FileOutputStream(cacheFileTmp);
							os.write(data);
							os.close();
							
							cacheFile.delete();
							cacheFileTmp.renameTo(cacheFile);
						} catch (Exception e) {
							Log.w(TAG, "exception when writing cache: ", e); //$NON-NLS-1$
						}
					}
					
					if (D.EBUG) Log.d(TAG, "storeToDiskCache " + (data == null? "null": data.length + " bytes") + " took " + (SystemClock.uptimeMillis() - startTime) + " ms (delayed " + (startTime - callTime) + " ms). To '" + key + "', thread=" + Thread.currentThread().getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
					return null;
				}
			}.execute();
		}
	}
	
	public class AlwaysSuccessImageData extends ImageData {
		@Override public boolean isSuccessResponse(Response response) {
			return response.validity == Validity.Ok;
		}
	}
	
	String urlToDisplay = null;
	private Drawable defaultImage;
	int maxPixels;
	
	private static Map<String, Bitmap> memoryCache = Collections.synchronizedMap(new LinkedHashMap<String, Bitmap>(200, 0.75f, true) {
		static final int maxTotalSize = 4000000;
		
		int totalSize = 0;
		
		@Override public Bitmap put(String key, Bitmap value) {
			Bitmap prev = super.put(key, value);
			
			if (prev != null) {
				int size = calcSize(prev);
				totalSize -= size;
				if (D.EBUG) Log.d(TAG, "cache put() remove prev: size " + size + " total " + totalSize); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			if (value != null) {
				int size = calcSize(value);
				totalSize += size;
				if (D.EBUG) Log.d(TAG, "cache put() add new: size " + size + " total " + totalSize); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			return prev;
		}

		@Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> eldest) {
			if (totalSize >= maxTotalSize) { 
				for (Iterator<Entry<String, Bitmap>> iter = this.entrySet().iterator(); iter.hasNext();) {
					Map.Entry<String, Bitmap> e = iter.next();
					if (e == null) {
						break; // no more items to remove. Should not happen.
					}

					iter.remove();
					
					if (totalSize < maxTotalSize) {
						break;
					} else {
						if (D.EBUG) Log.d(TAG, "cache removeEldestEntry() still needs to remove items"); //$NON-NLS-1$
					}
				}
			}
			
			// always false, because we're removing manually.
			return false;
		}
		
		@Override public Bitmap remove(Object key) {
			Bitmap prev = super.remove(key);
			
			if (prev != null) {
				int size = calcSize(prev);
				totalSize -= size;
				if (D.EBUG) Log.d(TAG, "cache remove(): size " + size + " total " + totalSize); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			return prev;
		};
		
		@Override public void clear() {
			totalSize = 0;
			if (D.EBUG) Log.d(TAG, "cache clear()"); //$NON-NLS-1$
			
			super.clear();
		};
		
		private int calcSize(Bitmap b) {
			int n = 3;
			Config config = b.getConfig();
			if (config == Config.RGB_565) n = 2;
			else if (config == Config.ARGB_8888) n = 4;
			else if (config == Config.ALPHA_8) n = 1;
			else if (config == Config.ARGB_4444) n = 2;
			int size = b.getWidth() * b.getHeight() * n;
			return size;
		}
	});
	
	static DiskCache diskCache;
	
	private State state;
	private OnStateChangeListener onStateChangeListener;
	
	private int lastWidthMeasureSpec = -1;
	private int lastHeightMeasureSpec = -1;
	
	private static UrlLoader urlLoader = new UrlLoader(); 
	
	public UrlImageView(Context context) {
		super(context);
		init(null);
	}

	public UrlImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs);
	}

	public UrlImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs);
	}

	private void init(AttributeSet attrs) {
		if (attrs != null) {
			TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.UrlImageView);
			defaultImage = a.getDrawable(R.styleable.UrlImageView_defaultImage);
			setMaxPixels(a.getInt(R.styleable.UrlImageView_maxPixels, 0));
			a.recycle();
		}
		
		setState(State.none, null);
		
		if (diskCache == null) {
			diskCache = new DefaultDiskCache();
		}
	}
	
	public void setDefaultImage(Drawable defaultImage) {
		this.defaultImage = defaultImage;
	}
	
	public Drawable getDefaultImage() {
		return defaultImage;
	}

	public static void clearMemoryCache() {
		memoryCache.clear();
	}
	
	public static void setDiskCache(DiskCache diskCache) {
		UrlImageView.diskCache = diskCache;
	}

	public void setUrl(final String url) {
		setUrl(url, false);
	}

	public void setUrl(final String url, boolean forceRefresh) {
		urlToDisplay = url;
		setState(State.loading, url);

		if (url == null) {
			Log.d(TAG, "class: " + defaultImage.getClass().getName()); //$NON-NLS-1$
			
			if (defaultImage != null) {
				this.setImageDrawable(defaultImage);
				if (defaultImage instanceof AnimationDrawable) {
					((AnimationDrawable) defaultImage).stop();
					((AnimationDrawable) defaultImage).start();
				}
				setState(State.loaded_from_default, url);
			} else {
				this.setImageDrawable(null);
			}
			return;
		}

		Bitmap bitmapFromMemory = retrieveFromMemoryCache(url);
		if (bitmapFromMemory != null) {
			// sync
			this.setImageBitmap(bitmapFromMemory);
			setState(State.loaded_from_memory, url);

			if (forceRefresh) {
				loadFromServer(url);
			}
		} else { // bitmapFromMemory == null
			if (defaultImage != null) {
				this.setImageDrawable(defaultImage);
				if (defaultImage instanceof AnimationDrawable) {
					((AnimationDrawable) defaultImage).stop();
					((AnimationDrawable) defaultImage).start();
				}
				setState(State.loaded_from_default, url);
			} else {
				this.setImageDrawable(null);
			}

			new AsyncTask<Void, Bitmap, Void>() {
				@Override protected Void doInBackground(Void... params) {
					byte[] rawFromDisk = diskCache.retrieve(DISKCACHE_PREFIX + url);
					
					if (rawFromDisk != null) {
						Options opts = new Options();
						Bitmap bitmapFromDisk = null;
						
						if (maxPixels != 0) {
							opts.inJustDecodeBounds = true;
							BitmapFactory.decodeByteArray(rawFromDisk, 0, rawFromDisk.length, opts);
							
							if (opts.outHeight != -1 && opts.outWidth != -1) {
								int pixels = opts.outHeight * opts.outWidth;
								int downscale = 1;
								while (true) {
									if (D.EBUG) Log.d(TAG, "maxpixels: " + maxPixels + " pixels: " + pixels + " downscale: " + downscale + " pixels/downscale/downscale: " + (pixels / downscale / downscale)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
									if (pixels / downscale / downscale > maxPixels) {
										downscale++;
									} else {
										break;
									}
									if (downscale >= 10) {
										break;
									}
								}
								
								opts.inJustDecodeBounds = false;
								opts.inSampleSize = downscale;
								opts.outHeight = -1;
								opts.outWidth = -1;
								
								bitmapFromDisk = BitmapFactory.decodeByteArray(rawFromDisk, 0, rawFromDisk.length, opts);
							}
						} else {
							bitmapFromDisk = BitmapFactory.decodeByteArray(rawFromDisk, 0, rawFromDisk.length, opts);
						}
						
						if (bitmapFromDisk != null) {
							storeToMemoryCache(url, bitmapFromDisk);
							// back to ui thread to update bitmap!
							publishProgress(bitmapFromDisk);
						}
					}
					return null;
				}
				
				@Override protected void onProgressUpdate(Bitmap... values) {
					if (url.equals(UrlImageView.this.urlToDisplay)) {
						UrlImageView.this.setImageBitmap(values[0]);
						setState(State.loaded_from_disk, url);
					}
				};
				
				@Override protected void onPostExecute(Void result) {
					// this must be called from ui thread
					loadFromServer(url);
				};
			}.execute();
		}
	}

	void loadFromServer(final String url) {
		// the below is executed in async
		urlLoader.load(getContext(), url, new AlwaysSuccessImageData(), new UrlLoader.Listener() {
			@Override public void onResponse(String url, Response response, BaseData data_, boolean firstTime) {
				AlwaysSuccessImageData data = (AlwaysSuccessImageData) data_;
				
				if (data != null && data.bitmap != null) {
					if (firstTime) {
						storeToMemoryCache(url, data.bitmap);
						diskCache.store(DISKCACHE_PREFIX + url, response.data);
					}
					
					if (url.equals(UrlImageView.this.urlToDisplay)) {
						UrlImageView.this.setImageBitmap(data.bitmap);
						UrlImageView.this.setState(State.loaded_from_url, url);
					}
				}
			}
		});
	}

	void storeToMemoryCache(String url, Bitmap b) {
		if (b == null) {
			memoryCache.remove(url);
		} else {
			if (D.EBUG) Log.d(TAG, "storeToMemoryCache " + b.getWidth() + "*" + b.getHeight() + " " + b.getConfig() + " for " + url); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			memoryCache.put(url, b);
		}
	}

	Bitmap retrieveFromMemoryCache(String url) {
		return memoryCache.get(url);
	}

	public String getUrlToDisplay() {
		return urlToDisplay;
	}
	
	@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		this.lastWidthMeasureSpec = widthMeasureSpec;
		this.lastHeightMeasureSpec = heightMeasureSpec;
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	public int getLastWidthMeasureSpec() {
		return lastWidthMeasureSpec;
	}

	public int getLastHeightMeasureSpec() {
		return lastHeightMeasureSpec;
	}

	public State getState() {
		return state;
	}

	public void setState(State state, String url) {
		this.state = state;
		if (onStateChangeListener != null) {
			onStateChangeListener.onStateChange(this, state, url);
		}
	}

	public OnStateChangeListener getOnStateChangeListener() {
		return onStateChangeListener;
	}

	public void setOnStateChangeListener(OnStateChangeListener onStateChangeListener) {
		this.onStateChangeListener = onStateChangeListener;
	}

	public int getMaxPixels() {
		return maxPixels;
	}

	public void setMaxPixels(int maxPixels) {
		this.maxPixels = maxPixels;
	}
}
