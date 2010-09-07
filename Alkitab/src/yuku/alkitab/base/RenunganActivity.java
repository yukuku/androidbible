package yuku.alkitab.base;

import static yuku.alkitab.base.model.AlkitabDb.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.*;

import yuku.alkitab.R;
import yuku.alkitab.base.model.AlkitabDb;
import yuku.alkitab.base.renungan.*;
import yuku.alkitab.base.renungan.TukangDonlot.OnStatusDonlotListener;
import yuku.andoutil.ThreadSleep;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.text.*;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.widget.TextView.BufferType;

public class RenunganActivity extends Activity implements OnStatusDonlotListener {
	public static final String TAG = RenunganActivity.class.getSimpleName();
	public static final String EXTRA_alamat = "alamat"; //$NON-NLS-1$
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); //$NON-NLS-1$

	public static final String[] ADA_NAMA = {
		"sh", "rh",  //$NON-NLS-1$//$NON-NLS-2$
	};
	public static final String DEFAULT = "sh"; //$NON-NLS-1$
	private static final String[] ADA_JUDUL = {
		"Santapan Harian", "Renungan Harian", //$NON-NLS-1$ //$NON-NLS-2$
	};

	TextView lIsi;
	ScrollView scrollIsi;
	TextView lHeader;
	ImageButton bKiri;
	ImageButton bKanan;
	Button bGanti;
	TextView lStatus;
	
	private boolean renderBerhasilBaik = false;
	private long terakhirCobaTampilLagi = 0;
	private Animation memudar;

	Handler pengulangTampil = new Handler();
	Handler penampilStatusDonlot = new Handler();
	
	Runnable cobaTampilLagi = new Runnable() {
		@Override
		public void run() {
			{
				long kini = SystemClock.currentThreadTimeMillis();
				if (kini - terakhirCobaTampilLagi < 500) {
					return; // ANEH. Terlalu cepat.
				}
				
				terakhirCobaTampilLagi = kini;
			}
			
			
			tuju(S.penampungan.renungan_nama, S.penampungan.renungan_tanggalan, true);
			
			if (!renderBerhasilBaik) {
				pengulangTampil.postDelayed(cobaTampilLagi, 12000);
			}
		}
	};

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.renungan);

		S.siapinEdisi(getApplicationContext());
		S.siapinKitab(getApplicationContext());
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		
		memudar = AnimationUtils.loadAnimation(this, R.anim.memudar);

		lHeader = (TextView) findViewById(R.id.lHeader);
		lIsi = (TextView) findViewById(R.id.lIsi);
		scrollIsi = (ScrollView) findViewById(R.id.scrollIsi);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		lStatus = (TextView) findViewById(R.id.lStatus);
		
		scrollIsi.setBackgroundColor(S.penerapan.warnaLatar);
		
		bKiri.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				S.penampungan.renungan_tanggalan.setTime(S.penampungan.renungan_tanggalan.getTime() - 3600*24*1000);
				tampilkan();
			}
		});
		
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		bKanan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				S.penampungan.renungan_tanggalan.setTime(S.penampungan.renungan_tanggalan.getTime() + 3600*24*1000);
				tampilkan();
			}
		});
		
		bGanti = (Button) findViewById(R.id.bGanti);
		bGanti.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int index = 0;
				
				for (int i = 0; i < ADA_NAMA.length; i++) {
					if (ADA_NAMA[i].equals(S.penampungan.renungan_nama)) {
						index = i;
						break;
					}
				}
				
				index = (index + 1) % ADA_NAMA.length;
				S.penampungan.renungan_nama = ADA_NAMA[index];
				tampilkan();
			}
		});
		
		//# atur difot! 
		if (S.penampungan.renungan_tanggalan == null) S.penampungan.renungan_tanggalan = new Date();
		if (S.penampungan.renungan_nama == null) S.penampungan.renungan_nama = DEFAULT;
		
		new PemintaMasaDepan().start();
		
		tampilkan();
	}
		
	private void tampilkan() {
		pengulangTampil.removeCallbacks(cobaTampilLagi);
		
		tuju(S.penampungan.renungan_nama, S.penampungan.renungan_tanggalan, true);
	}

	private void tuju(String nama, Date date, boolean penting) {
		tuju(nama, sdf.format(date), penting);
	}

	private void tuju(String nama, String tgl, boolean penting) {
		IArtikel artikel = cobaAmbilLokal(nama, tgl);
		if (artikel == null || !artikel.getSiapPakai()) {
			akanPerlu(nama, tgl, penting);
			render(artikel);
			
			pengulangTampil.postDelayed(cobaTampilLagi, 3000);
		} else {
			Log.d(TAG, "sudah siap tampil, kita syuh yang tersisa dari pengulang tampil"); //$NON-NLS-1$
			pengulangTampil.removeCallbacks(cobaTampilLagi);
			
			render(artikel);
		}
	}
	
	CallbackSpan.OnClickListener ayatKlikListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object data) {
			Log.d(TAG, "Dalam renungan, ada yang diklik: " + data); //$NON-NLS-1$

			Intent res = new Intent();
			res.putExtra(EXTRA_alamat, (String)data);
			
			setResult(RESULT_OK, res);
			finish();
		}
	};

	private void render(IArtikel artikel) {
		if (artikel == null) {
			Log.d(TAG, "merender artikel null"); //$NON-NLS-1$
		} else {
			Log.d(TAG, "merender artikel nama=" + artikel.getNama() + " tgl=" + artikel.getTgl() + " siapPakai=" + artikel.getSiapPakai()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		if (artikel != null && artikel.getSiapPakai()) {
			renderBerhasilBaik = true;
			
			Spanned header = Html.fromHtml(artikel.getHeaderHtml());
			SpannableStringBuilder ss = new SpannableStringBuilder(header);
			
			if (artikel.getNama().equals("sh")) { //$NON-NLS-1$
				SpannableStringBuilder judul = new SpannableStringBuilder(Html.fromHtml("<h3>" + artikel.getJudul() + "</h3>")); //$NON-NLS-1$ //$NON-NLS-2$
				judul.setSpan(new CallbackSpan(artikel.getJudul(), ayatKlikListener), 0, artikel.getJudul().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				
				ss.append(judul);
			} else if (artikel.getNama().equals("rh")) { //$NON-NLS-1$
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
							CallbackSpan span = new CallbackSpan(n.group(1), ayatKlikListener);
							ss.setSpan(span, m.start(1) + n.start(1), m.start(1) + n.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						}
					}
				}
				
				ss.append(Html.fromHtml("<br/><h3>" + artikel.getJudul() + "</h3><br/>"));  //$NON-NLS-1$//$NON-NLS-2$
			}
			
			int ofsetSebelumIsi = ss.length();
			
			Spanned isiDanKopirait = Html.fromHtml(artikel.getIsiHtml() + "<br/><br/>" + artikel.getKopiraitHtml()); //$NON-NLS-1$
			ss.append(isiDanKopirait);
			
			// cari "Bacaan : " dst dan pasang link
			{
				String s = isiDanKopirait.toString();
				Matcher m = Pattern.compile("Bacaan\\s*:\\s*(.*?)\\s*$", Pattern.MULTILINE).matcher(s); //$NON-NLS-1$
				while (m.find()) {
					Log.d(TAG, "Ketemu \"Bacaan : \": #" + m.group(1) + "#"); //$NON-NLS-1$ //$NON-NLS-2$
					CallbackSpan span = new CallbackSpan(m.group(1), ayatKlikListener);
					ss.setSpan(span, ofsetSebelumIsi + m.start(1), ofsetSebelumIsi + m.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			
			lIsi.setText(ss, BufferType.SPANNABLE);
			lIsi.setLinksClickable(true);
			lIsi.setMovementMethod(LinkMovementMethod.getInstance());
			lIsi.setTextColor(S.penerapan.warnaHuruf);
			lIsi.setBackgroundColor(S.penerapan.warnaLatar);
			lIsi.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
			lIsi.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp);
		} else {
			renderBerhasilBaik  = false;
			
			if (artikel == null) {
				lIsi.setText(R.string.belum_tersedia_menunggu_pengambilan_data_lewat_internet_pastikan_ada);
			} else { // berarti belum siap pakai
				lIsi.setText(R.string.belum_tersedia_mungkin_tanggal_yang_diminta_belum_disiapkan);
			}
		}
		
		String judul = S.penampungan.renungan_nama;
		for (int i = 0; i < ADA_NAMA.length; i++) {
			if (ADA_NAMA[i].equals(S.penampungan.renungan_nama)) {
				judul = ADA_JUDUL[i];
			}
		}
		
		lHeader.setText(judul + "\n" + namaHari(S.penampungan.renungan_tanggalan) + ", " + DateFormat.getDateFormat(this).format(S.penampungan.renungan_tanggalan));  //$NON-NLS-1$//$NON-NLS-2$
	}

	private static final int[] NAMA_HARI_RESID = {R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu};

	private String namaHari(Date date) {
		int day = date.getDay();
		return getString(NAMA_HARI_RESID[day]);
	}

	private synchronized void akanPerlu(String nama, String tgl, boolean penting) {
		if (S.tukangDonlot == null) {
			S.tukangDonlot = new TukangDonlot(this, this);
			S.tukangDonlot.start();
		}

		IArtikel artikel = null;
		if (nama.equals("rh")) { //$NON-NLS-1$
			artikel = new ArtikelRenunganHarian(tgl);
		} else if (nama.equals("sh")) { //$NON-NLS-1$
			artikel = new ArtikelSantapanHarian(tgl);
		}

		if (artikel != null) {
			boolean tertambah = S.tukangDonlot.tambah(artikel, penting);
			if (tertambah) S.tukangDonlot.interruptKaloNganggur();
		}
	}

	/**
	 * Coba ambil artikel dari db lokal. Artikel ga siap pakai pun akan direturn.
	 */
	private IArtikel cobaAmbilLokal(String nama, String tgl) {
		SQLiteDatabase db = AlkitabDb.getInstance(getApplicationContext()).getDatabase();

		Cursor c = db.query(TABEL_Renungan, null, KOLOM_Renungan_nama + "=? and " + KOLOM_Renungan_tgl + "=?", new String[] { nama, tgl }, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			if (c.moveToNext()) {
				IArtikel res = null;
				if (nama.equals("rh")) { //$NON-NLS-1$
					res = new ArtikelRenunganHarian(
						tgl,
						c.getString(c.getColumnIndexOrThrow(KOLOM_Renungan_judul)),
						c.getString(c.getColumnIndexOrThrow(KOLOM_Renungan_header)),
						c.getString(c.getColumnIndexOrThrow(KOLOM_Renungan_isi)),
						c.getInt(c.getColumnIndexOrThrow(KOLOM_Renungan_siapPakai)) > 0
					);
				} else if (nama.equals("sh")) { //$NON-NLS-1$
					res = new ArtikelSantapanHarian(
						tgl,
						c.getString(c.getColumnIndexOrThrow(KOLOM_Renungan_judul)),
						c.getString(c.getColumnIndexOrThrow(KOLOM_Renungan_header)),
						c.getString(c.getColumnIndexOrThrow(KOLOM_Renungan_isi)),
						c.getInt(c.getColumnIndexOrThrow(KOLOM_Renungan_siapPakai)) > 0
					);
				}

				return res;
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}
	
	private static boolean pemintaMasaDepanLagiJalan = false;
	
	private class PemintaMasaDepan extends Thread {
		@Override
		public void run() {
			if (pemintaMasaDepanLagiJalan) {
				Log.d(TAG, "peminta masa depan lagi jalan"); //$NON-NLS-1$
				return;
			}
			
			// diem dulu 6 detik
			ThreadSleep.ignoreInterrupt(6000);
			
			pemintaMasaDepanLagiJalan = true;
			try {
				Date hariIni = new Date();
				
				int hariDepan = 15;
				
				for (int i = 0; i < hariDepan; i++) {
					String tgl = sdf.format(hariIni);
					if (cobaAmbilLokal(S.penampungan.renungan_nama, tgl) == null) {
						Log.d(TAG, "PemintaMasaDepan perlu minta " + tgl); //$NON-NLS-1$
						akanPerlu(S.penampungan.renungan_nama, tgl, false);
						
						ThreadSleep.ignoreInterrupt(1000);
					} else {
						ThreadSleep.ignoreInterrupt(100); // biar ga berbeban aja
					}
					
					// maju ke besoknya
					hariIni.setTime(hariIni.getTime() + 3600*24*1000);
				}
			} finally {
				pemintaMasaDepanLagiJalan = false;
			}
		}
		
	}

	@Override
	public void onStatusDonlot(final String s) {
		penampilStatusDonlot.post(new Runnable() {
			@Override
			public void run() {
				if (s != null) {
					lStatus.setText(s);
					lStatus.setVisibility(View.VISIBLE);
					lStatus.startAnimation(memudar);
				}
			}
		});
	}
}
