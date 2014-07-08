package yuku.alkitabintegration.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import yuku.alkitabintegration.AlkitabIntegrationUtil;
import yuku.alkitabintegration.ConnectionResult;
import yuku.alkitabintegration.display.Launcher;
import yuku.alkitabintegration.provider.VerseProvider;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
	EditText tBookIdFrom;
	EditText tChapterFrom;
	EditText tVerseFrom;
	EditText tBookIdTo;
	EditText tChapterTo;
	EditText tVerseTo;
	CheckBox cFormatting;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		tBookIdFrom = (EditText) findViewById(R.id.tBookIdFrom);
		tChapterFrom = (EditText) findViewById(R.id.tChapterFrom);
		tVerseFrom = (EditText) findViewById(R.id.tVerseFrom);
		tBookIdTo = (EditText) findViewById(R.id.tBookIdTo);
		tChapterTo = (EditText) findViewById(R.id.tChapterTo);
		tVerseTo = (EditText) findViewById(R.id.tVerseTo);
		cFormatting = (CheckBox) findViewById(R.id.cFormatting);

		findViewById(R.id.bCheck).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final int result = AlkitabIntegrationUtil.isIntegrationAvailable(MainActivity.this);

				new AlertDialog.Builder(MainActivity.this)
					.setMessage("Check integration available result: " + result + "\n\n(" + ConnectionResult.SUCCESS + " means success)")
					.setPositiveButton("OK", null)
					.show();
			}
		});

		findViewById(R.id.bLaunch).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final Intent intent = Launcher.openAppAtBibleLocation(getAriFrom());
				startActivity(intent);
			}
		});

		findViewById(R.id.bProvideOne).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final VerseProvider vp = new VerseProvider(getContentResolver());
				final VerseProvider.Verse verse = vp.getVerse(getAriFrom());
				showProviderVerses(Arrays.asList(verse));
			}
		});

		findViewById(R.id.bProvideRange).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final VerseProvider vp = new VerseProvider(getContentResolver());
				final VerseProvider.VerseRanges ranges = new VerseProvider.VerseRanges();
				ranges.addRange(getAriFrom(), getAriTo());
				final List<VerseProvider.Verse> verses = vp.getVerses(ranges);
				showProviderVerses(verses);
			}
		});

		findViewById(R.id.bVersesDialog).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final Intent intent = Launcher.openVersesDialogByTarget("a:" + getAriFrom() + "-" + getAriTo());
				startActivity(intent);
			}
		});
	}

	void showProviderVerses(final List<VerseProvider.Verse> verses) {
		StringBuilder sb = new StringBuilder();
		for (VerseProvider.Verse verse : verses) {
			sb.append(verse.bookName).append("(").append(verse.getBookId()).append(") ").append(verse.getChapter()).append(":").append(verse.getVerse());
			sb.append(": ").append(verse.text);
			sb.append("\n");
		}

		new AlertDialog.Builder(this)
			.setMessage(sb)
			.setPositiveButton("OK", null)
			.show();
	}

	int getAriFrom() {
		int bookId = Integer.parseInt(tBookIdFrom.getText().toString());
		int chapter_1 = Integer.parseInt(tChapterFrom.getText().toString());
		int verse_1 = Integer.parseInt(tVerseFrom.getText().toString());
		return (bookId << 16) | (chapter_1 << 8) | verse_1;
	}

	int getAriTo() {
		int bookId = Integer.parseInt(tBookIdTo.getText().toString());
		int chapter_1 = Integer.parseInt(tChapterTo.getText().toString());
		int verse_1 = Integer.parseInt(tVerseTo.getText().toString());
		return (bookId << 16) | (chapter_1 << 8) | verse_1;
	}

	boolean getFormatting() {
		return cFormatting.isChecked();
	}

}
