package yuku.alkitab.base.ac;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
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

import greendroid.widget.AsyncImageView;
import greendroid.widget.AsyncImageView.OnImageViewLoadListener;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;

public class FontManagerActivity extends BaseActivity {
	public static final String TAG = FontManagerActivity.class.getSimpleName();

	public static Intent createIntent() {
		return new Intent(App.context, FontManagerActivity.class);
	}

	ListView lsFont;
	FontAdapter adapter;
	TextView lEmptyError;

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

		loadFontList();
	}

	private void loadFontList() {
		new AsyncTask<Void, Void, List<FontItem>>() {
			String error;
			
			@Override protected List<FontItem> doInBackground(Void... params) {
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

			AsyncImageView imgPreview = U.getView(res, R.id.imgPreview);
			TextView lFontName = U.getView(res, R.id.lFontName);
			View bDownload = U.getView(res, R.id.bDownload);
			ProgressBar progressbar = U.getView(res, R.id.progressbar);
			
			FontItem item = getItem(position);
			
			lFontName.setText(item.name);
			lFontName.setVisibility(View.VISIBLE);
			imgPreview.setTag(R.id.TAG_fontName, lFontName);
			imgPreview.setOnImageViewLoadListener(imgPreview_imageViewLoad);
			imgPreview.setUrl("http://alkitab-host.appspot.com/addon/fonts/v1/preview/" + item.name + "-384x84.png");
			bDownload.setTag(R.id.TAG_fontItem, item);
			bDownload.setOnClickListener(bDownload_click);
			progressbar.setProgress(0);
			
			return res;
		}
		
		private OnClickListener bDownload_click = new OnClickListener() {
			@Override public void onClick(View v) {
				FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);
				
			}
		};
		
		private OnImageViewLoadListener imgPreview_imageViewLoad = new OnImageViewLoadListener() {
			@Override public void onLoadingStarted(AsyncImageView imageView) {}
			
			@Override public void onLoadingFailed(AsyncImageView imageView, Throwable throwable) {
				TextView lFontName = (TextView) imageView.getTag(R.id.TAG_fontName);
				lFontName.setVisibility(View.VISIBLE);
			}
			
			@Override public void onLoadingEnded(AsyncImageView imageView, Bitmap image) {
				TextView lFontName = (TextView) imageView.getTag(R.id.TAG_fontName);
				lFontName.setVisibility(View.GONE);
			}
		};
	}
}
