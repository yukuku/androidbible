package yuku.alkitab.base.ac;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.sv.DownloadService;
import yuku.alkitab.base.sv.DownloadService.DownloadBinder;
import yuku.alkitab.base.sv.DownloadService.DownloadEntry;
import yuku.alkitab.base.sv.DownloadService.DownloadListener;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.debug.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FontManagerActivity extends BaseActivity implements DownloadListener {
	public static final String TAG = FontManagerActivity.class.getSimpleName();
	
	private static final String URL_fontList = "https://alkitab-host.appspot.com/addon/fonts/v1/list-v2.txt";
	private static final String URL_fontData = "https://alkitab-host.appspot.com/addon/fonts/v1/data/%s.zip";
	private static final String URL_fontPreview = "https://alkitab-host.appspot.com/addon/fonts/v1/preview/%s-384x84.png";

	public static Intent createIntent() {
		return new Intent(App.context, FontManagerActivity.class);
	}

	ListView lsFont;
	FontAdapter adapter;
	TextView lEmptyError;
	DownloadService dls;
	
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override public void onServiceDisconnected(ComponentName name) {
			dls = null;
		}
		
		@Override public void onServiceConnected(ComponentName name, IBinder service) {
			dls = ((DownloadBinder) service).getService();
			dls.setDownloadListener(FontManagerActivity.this);
			runOnUiThread(new Runnable() {
				@Override public void run() {
					loadFontList();
				}
			});
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreateWithNonToolbarUpButton(savedInstanceState);

		setContentView(R.layout.activity_font_manager);
		setTitle(R.string.fm_activity_title);

		lsFont = V.get(this, R.id.lsFont);
		lEmptyError = V.get(this, R.id.lEmptyError);
		
		lsFont.setAdapter(adapter = new FontAdapter());
		lsFont.setEmptyView(lEmptyError);
		
		bindService(new Intent(App.context, DownloadService.class), serviceConnection, BIND_AUTO_CREATE);
	}
	
	@Override protected void onDestroy() {
		super.onDestroy();
		
		if (dls != null) {
			unbindService(serviceConnection);
		}
	}

	void loadFontList() {
		new AsyncTask<Void, Void, List<FontItem>>() {
			String errorMsg;
			
			@Override protected List<FontItem> doInBackground(Void... params) {
				try {
					final String listString = App.downloadString(URL_fontList);
					final List<FontItem> list = new ArrayList<>();
					final Scanner sc = new Scanner(listString);
					if (sc.hasNextLine() && sc.nextLine().startsWith("OK")) {
						while (sc.hasNextLine()) {
							String line = sc.nextLine().trim();
							if (line.length() > 0) {
								FontItem item = new FontItem();
								item.name = line.split(" ")[0];
								list.add(item);
							}
						}
					}
					return list;
				} catch (IOException e) {
					errorMsg = e.getMessage();
					return null;
				}
			}
			
			@Override protected void onPostExecute(List<FontItem> result) {
				if (result != null) {
					lEmptyError.setText(null);
					adapter.setData(result);
				} else {
					lEmptyError.setText(errorMsg);
				}
			}
		}.execute();
	}
	
	String getFontDownloadKey(String name) {
		return "FontManager/" + name; //$NON-NLS-1$
	}
	
	private String getFontNameFromDownloadKey(String key) {
		if (!key.startsWith("FontManager/")) return null; //$NON-NLS-1$
		return key.substring("FontManager/".length()); //$NON-NLS-1$
	}

	String getFontDownloadDestination(String name) {
		return new File(FontManager.getFontsPath(), "download-" + name + ".zip").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	public static class FontItem {
		public String name;
	}
	
	public class FontAdapter extends BaseAdapter {
		List<FontItem> list;
		
		public void setData(List<FontItem> list) {
			this.list = list;
			notifyDataSetChanged();
		}

		@Override public int getCount() {
			return list == null? 0: list.size();
		}

		@Override public FontItem getItem(int position) {
			return list.get(position);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null ? convertView : getLayoutInflater().inflate(R.layout.item_font_download, parent, false);

			final ImageView imgPreview = V.get(res, R.id.imgPreview);
			final TextView lFontName = V.get(res, R.id.lFontName);
			final View bDownload = V.get(res, R.id.bDownload);
			final View bDelete = V.get(res, R.id.bDelete);
			final ProgressBar progressbar = V.get(res, R.id.progressbar);
			final TextView lErrorMsg = V.get(res, R.id.lErrorMsg);
			
			final FontItem item = getItem(position);
			final String dlkey = getFontDownloadKey(item.name);
			
			lFontName.setText(item.name);
			lFontName.setVisibility(View.VISIBLE);
			Picasso.with(FontManagerActivity.this).load(String.format(URL_fontPreview, item.name)).into(imgPreview, new Callback.EmptyCallback() {
				@Override
				public void onSuccess() {
					lFontName.setVisibility(View.GONE);
				}
			});
			imgPreview.setContentDescription(item.name);

			bDownload.setTag(R.id.TAG_fontItem, item);
			bDownload.setOnClickListener(bDownload_click);
			bDelete.setTag(R.id.TAG_fontItem, item);
			bDelete.setOnClickListener(bDelete_click);
			
			if (FontManager.isInstalled(item.name)) {
				progressbar.setIndeterminate(false);
				progressbar.setMax(100);
				progressbar.setProgress(100);
				bDownload.setVisibility(View.GONE);
				bDelete.setVisibility(View.VISIBLE);
				lErrorMsg.setVisibility(View.GONE);
			} else {
				DownloadEntry entry = dls.getEntry(dlkey);
				if (entry == null) {
					progressbar.setIndeterminate(false);
					progressbar.setMax(100);
					progressbar.setProgress(0);
					bDownload.setVisibility(View.VISIBLE);
					bDownload.setEnabled(true);
					bDelete.setVisibility(View.GONE);
					lErrorMsg.setVisibility(View.GONE);
				} else {
					if (entry.state == DownloadService.State.created) {
						progressbar.setIndeterminate(true);
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(false);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.downloading) {
						if (entry.length == -1) {
							progressbar.setIndeterminate(true);
						} else {
							progressbar.setIndeterminate(false);
							progressbar.setMax((int) entry.length);
							progressbar.setProgress((int) entry.progress);
						}
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(false);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.finished) {
						progressbar.setIndeterminate(false);
						progressbar.setMax(100); // consider full
						progressbar.setProgress(100); // consider full
						bDownload.setVisibility(View.GONE);
						bDelete.setVisibility(View.VISIBLE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.failed) {
						progressbar.setIndeterminate(false);
						progressbar.setMax(100);
						progressbar.setProgress(0);
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(true);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.VISIBLE);
						lErrorMsg.setText(entry.errorMsg);
					}
				}
			}
			
			return res;
		}
		
		private OnClickListener bDownload_click = new OnClickListener() {
			@Override public void onClick(View v) {
				FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);
				
				String dlkey = getFontDownloadKey(item.name);
				dls.removeEntry(dlkey);
				
				if (dls.getEntry(dlkey) == null) {
					new File(FontManager.getFontsPath()).mkdirs();
					dls.startDownload(
						dlkey,
						String.format(URL_fontData, item.name),
						getFontDownloadDestination(item.name)
					);
				}
				
				notifyDataSetChanged();
			}
		};
		
		private OnClickListener bDelete_click = new OnClickListener() {
			@Override public void onClick(View v) {
				final FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);

				new AlertDialogWrapper.Builder(FontManagerActivity.this)
					.setMessage(getString(R.string.fm_do_you_want_to_delete, item.name))
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							File fontDir = FontManager.getFontDir(item.name);
							File[] listFiles = fontDir.listFiles();
							if (listFiles != null) {
								for (File file : listFiles) {
									file.delete();
								}
							}
							fontDir.delete();

							dls.removeEntry(getFontDownloadKey(item.name));

							notifyDataSetChanged();
						}
					})
					.setNegativeButton(R.string.no, null)
					.show();
			}
		};
	}

	@Override public void onStateChanged(DownloadEntry entry, DownloadService.State originalState) {
		adapter.notifyDataSetChanged();
		
		if (originalState == DownloadService.State.finished) {
			String fontName = getFontNameFromDownloadKey(entry.key);
			if (fontName == null) { // this download doesn't belong to font manager.
				return;
			}
			
			try {
				String downloadedZip = getFontDownloadDestination(fontName);
				File fontDir = FontManager.getFontDir(fontName);
				fontDir.mkdirs();
				
				Log.d(TAG, "Going to unzip " + downloadedZip, new Throwable().fillInStackTrace()); //$NON-NLS-1$
				
				ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(downloadedZip)));
				try {
					ZipEntry ze;
					while ((ze = zis.getNextEntry()) != null) {
						String zname = ze.getName();
						Log.d(TAG, "Extracting from zip: " + zname); //$NON-NLS-1$
						File extractFile = new File(fontDir, zname);
						FileOutputStream fos = new FileOutputStream(extractFile);
						try {
							byte[] buf = new byte[4096];
							int count;
							while ((count = zis.read(buf)) != -1) {
								fos.write(buf, 0, count);
							}
						} finally {
							fos.close();
						}
					}
				} finally {
					zis.close();
				}
				
				new File(downloadedZip).delete();
			} catch (Exception e) {
				new AlertDialogWrapper.Builder(FontManagerActivity.this)
					.setMessage(getString(R.string.fm_error_when_extracting_font, fontName, e.getClass().getSimpleName() + ' ' + e.getMessage()))
					.setPositiveButton(R.string.ok, null)
					.show();
			}
		}
	}

	@Override public void onProgress(DownloadEntry entry, DownloadService.State originalState) {
		adapter.notifyDataSetChanged();
	}
}
