package yuku.alkitab.base.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.widget.TextView;

import yuku.alkitab.base.S;

public class PengaturTampilan {
	public static final String TAG = PengaturTampilan.class.getSimpleName();

	public static void aturIsiDanTampilanCuplikanBukmak(TextView t, String alamat, CharSequence isi) {
		SpannableStringBuilder sb = new SpannableStringBuilder(alamat);
		sb.setSpan(new UnderlineSpan(), 0, alamat.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		sb.append(' ').append(isi);
		t.setText(sb);
		aturTampilanTeksIsi(t);
	}

	public static void aturTampilanTeksIsi(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.penerapan.warnaHuruf);
	}

	public static void aturTampilanTeksAlamatHasilCari(TextView t, SpannableStringBuilder sb) {
		aturTampilanTeksJudulBukmak(t);
		sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		t.setText(sb);
	}

	public static void aturTampilanTeksJudulBukmak(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp * 1.2f);
		t.setTextColor(S.penerapan.warnaHuruf);
	}

	public static void aturTampilanTeksTanggalBukmak(TextView t) {
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp * 0.8f);
		t.setTextColor(S.penerapan.warnaHuruf);
	}

	public static void aturTampilanTeksNomerAyat(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.penerapan.warnaNomerAyat);
	}
}
