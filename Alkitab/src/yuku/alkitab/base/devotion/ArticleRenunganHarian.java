package yuku.alkitab.base.devotion;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.alkitab.base.widget.CallbackSpan;


public class ArticleRenunganHarian extends ArticleFromSabda {
	public ArticleRenunganHarian(String date) {
		super(date);
	}
	
	public ArticleRenunganHarian(String date, String title, String headerHtml, String bodyHtml, boolean readyToUse) {
		super(date, title, headerHtml, bodyHtml, readyToUse);
	}

	@Override
	public String getName() {
		return "rh"; //$NON-NLS-1$
	}
	
	@Override
	public String getDevotionTitle() {
		return "Renungan Harian"; //$NON-NLS-1$
	}

	@Override public CharSequence getContent(CallbackSpan.OnClickListener verseClickListener) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		
		Spanned header = Html.fromHtml(headerHtml);
		sb.append(header);
		
		// cari "Bacaan Setahun : " dst
		{
			String s = header.toString();
			Matcher m = Pattern.compile("Bacaan\\s+Setahun\\s*:\\s*(.*?)\\s*$", Pattern.MULTILINE).matcher(s); //$NON-NLS-1$
			while (m.find()) {
				// di dalem daftar ayat, kita cari lagi, harusnya sih dipisahkan titik-koma.
				String t = m.group(1);
				Matcher n = Pattern.compile("\\s*(\\S.*?)\\s*(;|$)", Pattern.MULTILINE).matcher(t); //$NON-NLS-1$
				
				while (n.find()) {
					Log.d(TAG, "Ketemu salah satu bacaan setahun: #" + n.group(1) + "#"); //$NON-NLS-1$ //$NON-NLS-2$
					CallbackSpan span = new CallbackSpan(n.group(1), verseClickListener);
					sb.setSpan(span, m.start(1) + n.start(1), m.start(1) + n.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		}
		
		sb.append(Html.fromHtml("<br/><h3>" + title + "</h3><br/>"));  //$NON-NLS-1$//$NON-NLS-2$
		
		Spanned body = Html.fromHtml(bodyHtml);
		int sb_len = sb.length();
		sb.append(body);
		
		// cari "Bacaan : " dst dan pasang link
		{
			Matcher m = Pattern.compile("Bacaan\\s*:\\s*(.*?)\\s*$", Pattern.MULTILINE).matcher(body); //$NON-NLS-1$
			while (m.find()) {
				Log.d(TAG, "Ketemu \"Bacaan : \": #" + m.group(1) + "#"); //$NON-NLS-1$ //$NON-NLS-2$
				CallbackSpan span = new CallbackSpan(m.group(1), verseClickListener);
				sb.setSpan(span, sb_len + m.start(1), sb_len + m.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}

		String copyrightHtml = "__________<br/>" +
		"<small>Renungan Harian / e-Renungan Harian<br/>" +
		"Bahan renungan yang diterbitkan secara teratur oleh Yayasan Gloria dan diterbitkan secara elektronik oleh Yayasan Lembaga SABDA (YLSA).<br/>" +
		"© 1999-2012 Yayasan Lembaga SABDA (YLSA). <br/>" +
		"Isi boleh disimpan untuk tujuan pribadi atau non-komersial. Atas setiap publikasi atau pencetakan wajib menyebutkan alamat situs SABDA.org sebagai sumber tulisan dan mengirim pemberitahuan ke webmaster@sabda.org</small>";

		sb.append(Html.fromHtml(copyrightHtml));
		
		return sb;
	}
}


