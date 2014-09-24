package yuku.kpriviewer.fr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import yuku.afw.V;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;
import yuku.kpriviewer.R;
import yuku.kpriviewer.fr.base.BaseFragment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class SongFragment extends BaseFragment {
	public static final String TAG = SongFragment.class.getSimpleName();
	
	private static final String ARG_song = "song";
	private static final String ARG_templateFile = "templateFile";
	private static final String ARG_customVars = "customVars";

	private WebView webView;

	private Song song;
	private String templateFile;
	private Bundle customVars;
	
	public interface ShouldOverrideUrlLoadingHandler {
		boolean shouldOverrideUrlLoading(WebViewClient client, WebView view, String url);
	}
	
	public static SongFragment create(Song song, String templateFile, Bundle optionalCustomVars) {
		SongFragment res = new SongFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_song, song);
		args.putString(ARG_templateFile, templateFile);
		if (optionalCustomVars != null) args.putBundle(ARG_customVars, optionalCustomVars);
		res.setArguments(args);
		return res;
	}
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		song = getArguments().getParcelable(ARG_song);
		templateFile = getArguments().getString(ARG_templateFile);
		customVars = getArguments().getBundle(ARG_customVars);
	}
	
	@SuppressLint("NewApi") @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_song, container, false);
		webView = V.get(res, R.id.webView);
		webView.setBackgroundColor(0x00000000);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setWebViewClient(webViewClient);
		
		if (Build.VERSION.SDK_INT >= 11) {
			webView.getSettings().setSupportZoom(true);
			webView.getSettings().setBuiltInZoomControls(true);
			webView.getSettings().setDisplayZoomControls(false);
		} else {
			webView.getSettings().setSupportZoom(true);
			webView.getSettings().setBuiltInZoomControls(true);
			// TODO do not show zoom buttons on devices with pinch.
			// possible solution: http://stackoverflow.com/questions/5125851/enable-disable-zoom-in-android-webview
		}
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		renderLagu(song);
	}
	
	WebViewClient webViewClient = new WebViewClient() {
		@Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Activity activity = getActivity();
			if (activity instanceof ShouldOverrideUrlLoadingHandler) {
				if (((ShouldOverrideUrlLoadingHandler)activity).shouldOverrideUrlLoading(this, view, url)) {
					return true;
				} else {
					return super.shouldOverrideUrlLoading(view, url);
				}
			} else {
				return super.shouldOverrideUrlLoading(view, url);
			}
		};
	};

	private void renderLagu(Song song) {
		try {
			InputStream is = getResources().getAssets().open(templateFile);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			while (true) {
				int read = is.read(buf);
				if (read < 0) break;
				baos.write(buf, 0, read);
			}
			String template = new String(baos.toByteArray(), "utf-8");
			
			if (customVars != null) {
				for (String key: customVars.keySet()) {
					template = templateVarReplace(template, key, customVars.get(key));
				}
			}
			
			template = templateDivReplace(template, "code", song.code);
			template = templateDivReplace(template, "title", song.title);
			template = templateDivReplace(template, "title_original", song.title_original);
			template = templateDivReplace(template, "tune", song.tune);
			template = templateDivReplace(template, "keySignature", song.keySignature);
			template = templateDivReplace(template, "timeSignature", song.timeSignature);
			template = templateDivReplace(template, "authors_lyric", song.authors_lyric);
			template = templateDivReplace(template, "authors_music", song.authors_music);

			template = templateDivReplace(template, "lyrics", songToHtml(song, false));

			webView.loadDataWithBaseURL("file:///android_asset/" + templateFile, template, "text/html", "utf-8", null);
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			webView.loadDataWithBaseURL(null, sw.toString(), "text/plain", "utf-8", null);
		}
	}

	public static String songToHtml(final Song song, final boolean forPatchText) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < song.lyrics.size(); i++) {
			Lyric lyric = song.lyrics.get(i);

			sb.append("<div class='lyric'>");

			if (song.lyrics.size() > 1 || lyric.caption != null) { // otherwise, only lyric and has no name
				if (lyric.caption != null) {
					sb.append("<div class='lyric_caption'>" + lyric.caption + "</div>");
				} else {
					sb.append("<div class='lyric_caption'>Versi " + (i+1) + "</div>");
				}
			}

			int bait_normal_no = 0;
			int bait_reff_no = 0;
			for (Verse verse: lyric.verses) {
				sb.append("<div class='verse" + (verse.kind == VerseKind.REFRAIN? " refrain": "") + "'>");
				{
					if (verse.kind == VerseKind.REFRAIN) {
						bait_reff_no++;
					} else {
						bait_normal_no++;
					}

					if (forPatchText) {
						sb.append(verse.kind == VerseKind.REFRAIN ? ("reff " + bait_reff_no) : bait_normal_no);
					} else {
						sb.append("<div class='verse_ordering'>" + (verse.kind == VerseKind.REFRAIN? bait_reff_no: bait_normal_no) + "</div>");
					}

					sb.append("<div class='verse_content'>");

					for (String line : verse.lines) {
						if (forPatchText) {
							sb.append(line).append("<br/>");
						} else {
							sb.append("<p class='line'>").append(line).append("</p>");
						}
					}

					sb.append("</div>");
				}
				sb.append("</div>");
			}

			sb.append("</div>");
		}
		return sb.toString();
	}
	
	private String templateDivReplace(String template, String name, String value) {
		return template.replace("{{div:" + name + "}}", value == null? "": ("<div class='" + name + "'>" + value + "</div>"));
	}

	private String templateDivReplace(String template, String name, List<String> value) {
		return templateDivReplace(template, name, value == null? null: TextUtils.join("; ", value.toArray(new String[0])));
	}

	private String templateVarReplace(String template, String name, Object value) {
		return template.replace("{{$" + name + "}}", value == null? "": value.toString());
	}
}
