package yuku.alkitab.base.ac;

import android.app.AlertDialog;
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.afw.D;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.sv.DownloadService;
import yuku.alkitab.base.sv.DownloadService.DownloadBinder;
import yuku.alkitab.base.sv.DownloadService.DownloadEntry;
import yuku.alkitab.base.sv.DownloadService.DownloadListener;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.widget.UrlImageView;
import yuku.alkitab.base.widget.UrlImageView.OnStateChangeListener;

public class FontManagerActivity extends BaseActivity implements DownloadListener {
	public static final String TAG = FontManagerActivity.class.getSimpleName();

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
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_font_manager);
		setTitle(R.string.fm_activity_title);

		lsFont = U.getView(this, R.id.lsFont);
		lEmptyError = U.getView(this, R.id.lEmptyError);
		
		lsFont.setAdapter(adapter = new FontAdapter());
		lsFont.setOnItemClickListener(lsFont_itemClick);
		lsFont.setOnItemLongClickListener(lsFont_itemLongClick);
		lsFont.setEmptyView(lEmptyError);
		
		bindService(new Intent(App.context, DownloadService.class), serviceConnection, BIND_AUTO_CREATE);
	}
	
	@Override protected void onDestroy() {
		super.onDestroy();
		
		if (dls != null) {
			unbindService(serviceConnection);
		}
	}

	private void loadFontList() {
		new AsyncTask<Void, Void, List<FontItem>>() {
			String error;
			
			@Override protected List<FontItem> doInBackground(Void... params) {
				{
					List<FontItem> list = new ArrayList<FontItem>();
					for (String name: "Bitter Cardo EBGaramond Kreon Lora".split(" ")) {
						FontItem item = new FontItem();
						item.name = name;
						list.add(item);
					}
					if (D.EBUG) return list;
				}
				
				
				HttpConnection conn = new HttpConnection("http://alkitab-host.appspot.com/addon/fonts/v1/list.txt");
				try {
					InputStream in = conn.load();
					if (in != null) {
						if (conn.isSameHost()) {
							List<FontItem> list = new ArrayList<FontItem>();
							Scanner sc = new Scanner(in);
							while (sc.hasNextLine()) {
								String line = sc.nextLine().trim();
								if (line.length() > 0) {
									FontItem item = new FontItem();
									item.name = line.split(" ")[0];
									list.add(item);
								}
							}
							return list;
						} else {
							error = "You need to log-in to your (Wi-Fi) network first.";
						}
					} else {
						error = conn.getException().getMessage();
					}
					return null;
				} finally {
					conn.close();
				}
			}
			
			@Override protected void onPostExecute(List<FontItem> result) {
				if (result != null) {
					lEmptyError.setText(null);
					adapter.setData(result);
				} else {
					lEmptyError.setText(error);
				}
			};
		}.execute();
	}
	
	public static class HttpConnection {
		final URL url;
		HttpURLConnection conn = null;
		InputStream in = null;
		IOException ex = null;
		
		public HttpConnection(String url) {
			try {
				this.url = new URL(url);
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
		
		public InputStream load() {
			try {
				conn = (HttpURLConnection) url.openConnection();
				in = new BufferedInputStream(conn.getInputStream());
				return in;
			} catch (IOException e) {
				this.ex = e;
				return null;
			}
		}
		
		public boolean isSameHost() {
			return url.getHost().equals(conn.getURL().getHost());
		}
		
		public Exception getException() {
			return ex;
		}
		
		public void close() {
			try {
				if (in != null) in.close();
				if (conn != null) conn.disconnect();
			} catch (IOException e) {
				Log.d(TAG, "close", e);
			}
		}
	}

	private OnItemClickListener lsFont_itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			FontItem item = adapter.getItem(position);

			adapter.notifyDataSetChanged();
		}
	};

	private OnItemLongClickListener lsFont_itemLongClick = new OnItemLongClickListener() {
		@Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			// TODO Auto-generated method stub
			return false;
		}
	};
	
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
			View res = convertView != null ? convertView : getLayoutInflater().inflate(R.layout.item_font_download, null);

			UrlImageView imgPreview = U.getView(res, R.id.imgPreview);
			TextView lFontName = U.getView(res, R.id.lFontName);
			View bDownload = U.getView(res, R.id.bDownload);
			View bDelete = U.getView(res, R.id.bDelete);
			ProgressBar progressbar = U.getView(res, R.id.progressbar);
			TextView lErrorMsg = U.getView(res, R.id.lErrorMsg);
			
			FontItem item = getItem(position);
			String dlkey = getDownloadKey(item.name);
			boolean installed = FontManager.isInstalled(item.name);
			
			lFontName.setText(item.name);
			lFontName.setVisibility(View.VISIBLE);
			imgPreview.setTag(R.id.TAG_fontName, lFontName);
			imgPreview.setOnStateChangeListener(imgPreview_stateChange);
			imgPreview.setUrl("http://alkitab-host.appspot.com/addon/fonts/v1/preview/" + item.name + "-384x84.png");
			bDownload.setTag(R.id.TAG_fontItem, item);
			bDownload.setOnClickListener(bDownload_click);
			bDelete.setTag(R.id.TAG_fontItem, item);
			bDelete.setOnClickListener(bDelete_click);
			
			if (installed) {
				progressbar.setIndeterminate(false);
				progressbar.setProgress(100);
				progressbar.setMax(100);
				bDownload.setVisibility(View.GONE);
				bDelete.setVisibility(View.VISIBLE);
				lErrorMsg.setVisibility(View.GONE);
			} else {
				DownloadEntry entry = dls.getEntry(dlkey);
				if (entry == null) {
					progressbar.setIndeterminate(false);
					progressbar.setProgress(0);
					progressbar.setMax(100);
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
							progressbar.setProgress((int) entry.progress);
							progressbar.setMax((int) entry.length);
						}
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(false);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.finished) {
						progressbar.setIndeterminate(false);
						progressbar.setProgress((int) entry.progress);
						progressbar.setMax((int) entry.progress); // same as progress
						bDownload.setVisibility(View.GONE);
						bDelete.setVisibility(View.VISIBLE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.failed) {
						progressbar.setIndeterminate(false);
						progressbar.setProgress(0);
						progressbar.setMax((int) (entry.length == -1? 100: entry.length));
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

		private String getDownloadKey(String name) {
			return "FontManager/name/" + name;
		}
		
		private OnClickListener bDownload_click = new OnClickListener() {
			@Override public void onClick(View v) {
				FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);
				
				String dlkey = getDownloadKey(item.name);
				dls.removeEntry(dlkey);
				
				if (dls.getEntry(dlkey) == null) {
					dls.startDownload(
						dlkey, 
						"http://alkitab-host.appspot.com/addon/fonts/v1/data/" + item.name + ".zip",
						new File(FontManager.getFontsPath(), "download-" + item.name + ".zip").getAbsolutePath()
					);
				}
				
				notifyDataSetChanged();
			}
		};
		
		private OnClickListener bDelete_click = new OnClickListener() {
			@Override public void onClick(View v) {
				final FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);
				
				new AlertDialog.Builder(FontManagerActivity.this)
				.setMessage("Do you want to delete " + item.name + "?")
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						File fontDir = FontManager.getFontDir(item.name);
						for (File file: fontDir.listFiles()) {
							file.delete();
						}
						fontDir.delete();
						
						notifyDataSetChanged();
					}
				})
				.setNegativeButton(R.string.no, null)
				.show();
			}
		};
		
		private OnStateChangeListener imgPreview_stateChange = new OnStateChangeListener() {
			@Override public void onStateChange(UrlImageView v, UrlImageView.State newState, String url) {
				if (newState.isLoaded()) {
					TextView lFontName = (TextView) v.getTag(R.id.TAG_fontName);
					lFontName.setVisibility(View.GONE);
				}
			}
		};
	}

	@Override public void onStateChanged(DownloadEntry entry) {
		adapter.notifyDataSetChanged();
		// TODO optimize
		// TODO unzip
	}

	@Override public void onProgress(DownloadEntry entry) {
		adapter.notifyDataSetChanged();
		// TODO optimize
	}
}
