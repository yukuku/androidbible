package yuku.alkitab.imagesharer;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;

import java.util.Arrays;

public class MultipleVersesPlainActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multiple_verses);

		// get the selected verses and their texts
		Intent intent = getIntent();
		int[] aris = intent.getIntArrayExtra("aris");
		String[] verseTexts = intent.getStringArrayExtra("verseTexts");

		StringBuilder sb = new StringBuilder();
		sb.append("Aris received: ");
		sb.append(Arrays.toString(aris));
		sb.append("\n\n");
		sb.append("Verse texts received: ");
		for (final String verseText : verseTexts) {
			sb.append("\n\n");
			sb.append(verseText);
		}

		((TextView) findViewById(R.id.tData)).setText(sb);
	}

}
