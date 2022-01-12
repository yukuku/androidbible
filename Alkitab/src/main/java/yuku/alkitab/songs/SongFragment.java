package yuku.alkitab.songs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import yuku.alkitab.base.fr.base.BaseFragment;
import yuku.alkitab.debug.R;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;

public class SongFragment extends BaseFragment {
	private static final String ARG_song = "song";
	private static final String ARG_templateFile = "templateFile";
	private static final String ARG_customVars = "customVars";

	private WebView webview;

	private Song song;
	private String templateFile;
	private Bundle customVars;

	public interface ShouldOverrideUrlLoadingHandler {
		boolean shouldOverrideUrlLoading(WebViewClient client, WebView view, String url);
	}

	public static SongFragment create(Song song, String templateFile, Bundle optionalCustomVars) {
		final SongFragment res = new SongFragment();
		final Bundle args = new Bundle();
		args.putParcelable(ARG_song, song);
		args.putString(ARG_templateFile, templateFile);
		if (optionalCustomVars != null) {
			args.putBundle(ARG_customVars, optionalCustomVars);
		}
		res.setArguments(args);
		return res;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Bundle args = getArguments();
		assert args != null;
		song = args.getParcelable(ARG_song);
		templateFile = args.getString(ARG_templateFile);
		customVars = args.getBundle(ARG_customVars);
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View res = inflater.inflate(R.layout.fragment_song, container, false);
		webview = res.findViewById(R.id.webview);
		webview.setBackgroundColor(0x00000000);
		webview.setWebViewClient(webViewClient);

		final WebSettings settings = webview.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setSupportZoom(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);

		// prevent user system-wide display settings (sp scaling) from changing the actual text size inside webview.
		settings.setTextZoom(100);

		return res;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		renderLagu(song);
	}

	final WebViewClient webViewClient = new WebViewClient() {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			final Activity activity = getActivity();
			return activity instanceof ShouldOverrideUrlLoadingHandler && ((ShouldOverrideUrlLoadingHandler) activity).shouldOverrideUrlLoading(this, view, url) || super.shouldOverrideUrlLoading(view, url);
		}

		boolean pendingResize = false;

		@Override
		public void onScaleChanged(final WebView view, final float oldScale, final float newScale) {
			super.onScaleChanged(view, oldScale, newScale);

			// "restore" auto text-wrapping behavior from before KitKat
			if (pendingResize) return;

			pendingResize = true;
			view.postDelayed(() -> {
				final String script = "document.getElementsByTagName('body')[0].style.width = window.innerWidth + 'px';";
				view.evaluateJavascript(script, null);

				pendingResize = false;
			}, 100);
		}
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
				for (String key : customVars.keySet()) {
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

			webview.loadDataWithBaseURL("file:///android_asset/" + templateFile, template, "text/html", "utf-8", null);
		} catch (Exception e) {
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			webview.loadDataWithBaseURL(null, sw.toString(), "text/plain", "utf-8", null);
		}
	}

	public static String songToHtml(final Song song, final boolean forPatchText) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < song.lyrics.size(); i++) {
			final Lyric lyric = song.lyrics.get(i);

			sb.append("<div class='lyric'>");

			if (song.lyrics.size() > 1 || lyric.caption != null) { // otherwise, only lyric and has no name
				if (lyric.caption != null) {
					sb.append("<div class='lyric_caption'>").append(lyric.caption).append("</div>");
				} else {
					sb.append("<div class='lyric_caption'>Versi ").append(i + 1).append("</div>");
				}
			}

			int bait_normal_no = 0;
			int bait_reff_no = 0;
			for (Verse verse : lyric.verses) {
				sb.append("<div class='verse").append(verse.kind == VerseKind.REFRAIN ? " refrain" : "").append("'>");

				{
					switch (verse.kind) {
						case REFRAIN:
							bait_reff_no++;
							break;
						case NORMAL:
							bait_normal_no++;
							break;
					}

					if (forPatchText) {
						switch (verse.kind) {
							case REFRAIN:
								sb.append("reff ").append(bait_reff_no);
								break;
							case NORMAL:
								sb.append(bait_normal_no);
								break;
						}
					} else {
						switch (verse.kind) {
							case REFRAIN:
								sb.append("<div class='verse_ordering'>").append(bait_reff_no).append("</div>");
								break;
							case NORMAL:
								sb.append("<div class='verse_ordering'>").append(bait_normal_no).append("</div>");
								break;
						}
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
		return template.replace("{{div:" + name + "}}", value == null ? "" : ("<div class='" + name + "'>" + value + "</div>"));
	}

	private String templateDivReplace(String template, String name, List<String> value) {
		return templateDivReplace(template, name, value == null ? null : TextUtils.join("; ", value.toArray(new String[value.size()])));
	}

	private String templateVarReplace(String template, String name, Object value) {
		return template.replace("{{$" + name + "}}", value == null ? "" : value.toString());
	}

	public int getWebViewTextZoom() {
		if (webview == null) return 0; // not ready
		return webview.getSettings().getTextZoom();
	}

	public void setWebViewTextZoom(final int percent) {
		if (webview == null) return;
		webview.getSettings().setTextZoom(percent);
	}
}
