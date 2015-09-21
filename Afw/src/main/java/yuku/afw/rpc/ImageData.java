package yuku.afw.rpc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import yuku.afw.D;

public abstract class ImageData extends BaseData {
	public static final String TAG = ImageData.class.getSimpleName();

	public Bitmap bitmap;
	public Options opts;
	
	public class ImageProcessor implements ResponseProcessor {
		private int maxPixels = 0;
		
		public void setMaxPixels(int maxPixels) {
			this.maxPixels = maxPixels;
		}
		
		@Override public void process(byte[] raw) throws Exception {
			opts = new Options();
			
			if (maxPixels != 0) {
				opts.inJustDecodeBounds = true;
				BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
				
				if (opts.outHeight == -1 || opts.outWidth == -1) {
					return;
				}
				
				int pixels = opts.outHeight * opts.outWidth;
				int downscale = 1;
				while (true) {
					if (D.EBUG) Log.d(TAG, "maxpixels: " + maxPixels + " pixels: " + pixels + " downscale: " + downscale + " pixels/downscale/downscale: " + (pixels / downscale / downscale));
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
				
				bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
			} else {
				bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
			}
		}
	}
		
	@Override public ResponseProcessor getResponseProcessor(Response response) {
		return new ImageProcessor();
	}
}
