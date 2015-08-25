package yuku.alkitab.imagesharer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ShareVerseActivity extends AppCompatActivity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get the selected verse and its text
		Intent intent = getIntent();
		int[] aris = intent.getIntArrayExtra("aris");
		String[] verseTexts = intent.getStringArrayExtra("verseTexts");

		// safety check
		if (aris == null || aris.length < 1 || verseTexts == null || verseTexts.length < 1) {
			finish();
			return;
		}

		// this example does not support multiple verses
		// so use the first one only
		int book = (aris[0] & 0xff0000) >> 16;
		int chapter = (aris[0] & 0xff00) >> 8;
		int verse = aris[0] & 0xff;

		// get the name of book
		String bookName = "unknown";
		if (book < 66) {
			bookName = new String[] {"Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"}[book];
		}

		// construct a reference string: Bookname chapter:verse
		String reference = bookName + " " + chapter + ":" + verse;

		// draw on a bitmap
		Bitmap b = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		Paint p = new Paint();
		p.setColor(0xff0000ff); // blue

		c.drawText(reference, 40, 200, p);
		c.drawText(verseTexts[0], 40, 400, p);

		// save as image and share
		try {
			FileOutputStream f = new FileOutputStream("/sdcard/tmp.png");
			b.compress(Bitmap.CompressFormat.PNG, 100, f);
			f.close();

			ShareCompat.IntentBuilder.from(this)
				.addStream(Uri.fromFile(new File("/sdcard/tmp.png")))
				.startChooser();
		} catch (IOException e) {
			Log.e("Error", "error saving image", e); // do something
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_share_verse, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}
}
