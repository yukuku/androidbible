package yuku.alkitab.base.ac;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.picasso.Callback;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.sv.DownloadService;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.Foreground;
import yuku.alkitab.debug.BuildConfig;
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

public class FontManagerActivity extends BaseActivity implements DownloadService.DownloadListener {
	public static final String TAG = FontManagerActivity.class.getSimpleName();

	private static final String URL_fontList = BuildConfig.SERVER_HOST + "addon/fonts/v1/list-v2.txt";
	private static final String URL_fontData = BuildConfig.SERVER_HOST + "addon/fonts/v1/data/%s.zip";
	private static final String URL_fontPreview = BuildConfig.SERVER_HOST + "addon/fonts/v1/preview/%s-384x84.png";

	public static Intent createIntent() {
		return new Intent(App.context, FontManagerActivity.class);
	}

	ListView lsFont;
	FontAdapter adapter;
	View progress;
	TextView lEmptyError;
	DownloadService dls;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			dls = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			dls = ((DownloadService.DownloadBinder) service).getService();
			dls.setDownloadListener(FontManagerActivity.this);
			runOnUiThread(() -> loadFontList());
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.willNeedStoragePermission();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_font_manager);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		lsFont = findViewById(R.id.lsFont);
		progress = findViewById(R.id.progress);
		lEmptyError = findViewById(R.id.lEmptyError);

		lsFont.setAdapter(adapter = new FontAdapter());

		bindService(new Intent(App.context, DownloadService.class), serviceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onNeededPermissionsGranted(final boolean immediatelyGranted) {
		super.onNeededPermissionsGranted(immediatelyGranted);

		if (!immediatelyGranted && dls != null) {
			loadFontList();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (dls != null) {
			unbindService(serviceConnection);
		}
	}

	void loadFontList() {
		Background.run(() -> {
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

				Foreground.run(() -> {
					adapter.setData(list);
					lEmptyError.setVisibility(View.GONE);
					progress.setVisibility(View.GONE);
				});
			} catch (IOException e) {
				final String errorMsg = e.getMessage();

				Foreground.run(() -> {
					lEmptyError.setVisibility(View.VISIBLE);
					lEmptyError.setText(errorMsg);
					progress.setVisibility(View.GONE);
				});
			}
		});
	}

	String getFontDownloadKey(String name) {
		return "FontManager/" + name;
	}

	private String getFontNameFromDownloadKey(String key) {
		if (!key.startsWith("FontManager/")) return null;
		return key.substring("FontManager/".length());
	}

	String getFontDownloadDestination(String name) {
		return new File(getCacheDir(), "download-" + name + ".zip").getAbsolutePath();
	}

	public static class FontItem {
		public String name;
	}

	public class FontAdapter extends EasyAdapter {
		final List<FontItem> list = new ArrayList<>();

		public void setData(List<FontItem> list) {
			this.list.clear();
			this.list.addAll(list);
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public FontItem getItem(int position) {
			return list.get(position);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_font_download, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final ImageView imgPreview = view.findViewById(R.id.imgPreview);
			final TextView lFontName = view.findViewById(R.id.lFontName);
			final View bDownload = view.findViewById(R.id.bDownload);
			final View bDelete = view.findViewById(R.id.bDelete);
			final ProgressBar progressbar = view.findViewById(R.id.progressbar);
			final TextView lErrorMsg = view.findViewById(R.id.lErrorMsg);

			final FontItem item = getItem(position);
			final String dlkey = getFontDownloadKey(item.name);

			lFontName.setText(item.name);
			lFontName.setVisibility(View.VISIBLE);
			App.picasso().load(String.format(URL_fontPreview, item.name)).into(imgPreview, new Callback.EmptyCallback() {
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
				DownloadService.DownloadEntry entry = dls.getEntry(dlkey);
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
		}

		private View.OnClickListener bDownload_click = v -> {
			FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);

			String dlkey = getFontDownloadKey(item.name);
			dls.removeEntry(dlkey);

			if (dls.getEntry(dlkey) == null) {
				dls.startDownload(
					dlkey,
					String.format(URL_fontData, item.name),
					getFontDownloadDestination(item.name)
				);
			}

			notifyDataSetChanged();
		};

		private View.OnClickListener bDelete_click = v -> {
			final FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);

			new MaterialDialog.Builder(FontManagerActivity.this)
				.content(getString(R.string.fm_do_you_want_to_delete, item.name))
				.positiveText(R.string.delete)
				.onPositive((dialog, which) -> {
					File fontDir = FontManager.getFontDir(item.name);
					File[] listFiles = fontDir.listFiles();
					if (listFiles != null) {
						for (File file : listFiles) {
							//noinspection ResultOfMethodCallIgnored
							file.delete();
						}
					}
					//noinspection ResultOfMethodCallIgnored
					fontDir.delete();

					dls.removeEntry(getFontDownloadKey(item.name));

					notifyDataSetChanged();
				})
				.negativeText(R.string.cancel)
				.show();
		};
	}

	@Override
	public void onStateChanged(DownloadService.DownloadEntry entry, DownloadService.State originalState) {
		adapter.notifyDataSetChanged();

		if (originalState == DownloadService.State.finished) {
			String fontName = getFontNameFromDownloadKey(entry.key);
			if (fontName == null) { // this download doesn't belong to font manager.
				return;
			}

			try {
				final String downloadedZip = getFontDownloadDestination(fontName);
				final File fontDir = FontManager.getFontDir(fontName);
				//noinspection ResultOfMethodCallIgnored
				fontDir.mkdirs();

				AppLog.d(TAG, "Going to unzip " + downloadedZip, new Throwable().fillInStackTrace());

				try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(downloadedZip)))) {
					ZipEntry ze;
					while ((ze = zis.getNextEntry()) != null) {
						final String zname = ze.getName();
						AppLog.d(TAG, "Extracting from zip: " + zname);
						final File extractFile = new File(fontDir, zname);

						// https://support.google.com/faqs/answer/9294009
						final String fontDirPath = fontDir.getCanonicalPath();
						final String extractFilePath = extractFile.getCanonicalPath();
						if (!extractFilePath.startsWith(fontDirPath)) {
							throw new SecurityException("Zip path traversal attack: " + fontDirPath + ", " + zname);
						}

						try (FileOutputStream fos = new FileOutputStream(extractFile)) {
							byte[] buf = new byte[4096];
							int count;
							while ((count = zis.read(buf)) != -1) {
								fos.write(buf, 0, count);
							}
						}
					}
				}

				//noinspection ResultOfMethodCallIgnored
				new File(downloadedZip).delete();
			} catch (Exception e) {
				new MaterialDialog.Builder(FontManagerActivity.this)
					.content(getString(R.string.fm_error_when_extracting_font, fontName, e.getClass().getSimpleName() + ' ' + e.getMessage()))
					.positiveText(R.string.ok)
					.show();
			}
		}
	}

	@Override
	public void onProgress(DownloadService.DownloadEntry entry, DownloadService.State originalState) {
		adapter.notifyDataSetChanged();
	}
}
