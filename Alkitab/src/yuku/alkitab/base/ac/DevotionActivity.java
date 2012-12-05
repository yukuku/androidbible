package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.renungan.ArtikelRenunganHarian;
import yuku.alkitab.base.renungan.ArtikelSantapanHarian;
import yuku.alkitab.base.renungan.Downloader;
import yuku.alkitab.base.renungan.Downloader.OnStatusDonlotListener;
import yuku.alkitab.base.renungan.IArtikel;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.widget.CallbackSpan;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class DevotionActivity extends BaseActivity implements OnStatusDonlotListener {
	public static final String TAG = DevotionActivity.class.getSimpleName();

	public static final String EXTRA_alamat = "alamat"; //$NON-NLS-1$
	private static final int REQCODE_bagikan = 0;

	ThreadLocal<SimpleDateFormat> tgl_format = U.getThreadLocalSimpleDateFormat("yyyyMMdd"); //$NON-NLS-1$
	
	public static final String[] AVAILABLE_NAMES = {
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
	
	boolean renderBerhasilBaik = false;
	long terakhirCobaTampilLagi = 0;
	Animation memudar;
	
	// yang ditampilin saat ini
	String nama;
	Date tanggalan;

	Handler pengulangTampil = new Handler() {
		@Override public void handleMessage(Message msg) {
			{
				long kini = SystemClock.currentThreadTimeMillis();
				if (kini - terakhirCobaTampilLagi < 500) {
					return; // ANEH. Terlalu cepat.
				}
				
				terakhirCobaTampilLagi = kini;
			}
			
			tuju(true, 0);
			
			if (!renderBerhasilBaik) {
				pengulangTampil.sendEmptyMessageDelayed(0, 12000);
			}
		}
	};
	
	static class PenampilStatusDonlotHandler extends Handler {
		private WeakReference<DevotionActivity> ac;

		public PenampilStatusDonlotHandler(DevotionActivity ac) {
			this.ac = new WeakReference<DevotionActivity>(ac);
		}
		
		@Override public void handleMessage(Message msg) {
			DevotionActivity ac = this.ac.get();
			if (ac == null) return;
			
			String s = (String) msg.obj;
			if (s != null) {
				ac.lStatus.setText(s);
				ac.lStatus.setVisibility(View.VISIBLE);
				ac.lStatus.startAnimation(ac.memudar);
			}
		}
	}
	
	Handler penampilStatusDonlot = new PenampilStatusDonlotHandler(this);
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_devotion);
		
		memudar = AnimationUtils.loadAnimation(this, R.anim.fade_out);

		lHeader = (TextView) findViewById(R.id.lHeader);
		lIsi = (TextView) findViewById(R.id.lIsi);
		scrollIsi = (ScrollView) findViewById(R.id.scrollIsi);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		lStatus = (TextView) findViewById(R.id.lStatus);
		
		scrollIsi.setBackgroundColor(S.applied.backgroundColor);
		
		bKiri.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				tanggalan.setTime(tanggalan.getTime() - 3600*24*1000);
				tampilkan(0);
			}
		});
		
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		bKanan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				tanggalan.setTime(tanggalan.getTime() + 3600*24*1000);
				tampilkan(0);
			}
		});
		
		bGanti = (Button) findViewById(R.id.bGanti);
		bGanti.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int index = 0;
				
				for (int i = 0; i < AVAILABLE_NAMES.length; i++) {
					if (AVAILABLE_NAMES[i].equals(nama)) {
						index = i;
						break;
					}
				}
				
				index = (index + 1) % AVAILABLE_NAMES.length;
				nama = AVAILABLE_NAMES[index];
				tampilkan(0);
			}
		});
		
		//# atur difot! 
		if (S.temporary.devotion_date == null) S.temporary.devotion_date = new Date();
		if (S.temporary.devotion_name == null) S.temporary.devotion_name = DEFAULT;
		
		nama = S.temporary.devotion_name;
		tanggalan = S.temporary.devotion_date;
		
		// Workaround for crashes due to html tags in the title
		// We remove all rows that contain '<' in the judul
		if (Preferences.getBoolean(Prefkey.patch_devotionSlippedHtmlTags, false) == false) {
			int deleted = S.getDb().deleteDevotionsWithLessThanInTitle();
			Log.d(TAG, "patch_devotionSlippedHtmlTags: deleted " + deleted);
			Preferences.setBoolean(Prefkey.patch_devotionSlippedHtmlTags, true);
		}
		
		new PemintaMasaDepan().execute();
		
		{ // betulin ui update 
			Downloader td = S.downloader;
			if (td != null) {
				td.setListener(this);
			}
		}
		
		tampilkan(S.temporary.devotion_scroll);
	}
	
	@Override protected void onDestroy() {
		super.onDestroy();
		
		S.temporary.devotion_name = nama;
		S.temporary.devotion_date = tanggalan;
		S.temporary.devotion_scroll = scrollIsi.getScrollY();
		
		Log.d(TAG, "renungan_skrol = " + S.temporary.devotion_scroll); //$NON-NLS-1$
	}
		
	private void bikinMenu(Menu menu) {
		menu.clear();
		getSupportMenuInflater().inflate(R.menu.activity_devotion, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.menuCopy) {
			String salinan = lHeader.getText() + "\n" + lIsi.getText(); //$NON-NLS-1$
			U.copyToClipboard(salinan);
			
			Toast.makeText(this, R.string.renungan_sudah_disalin, Toast.LENGTH_SHORT).show();
			
			return true;
		} else if (itemId == R.id.menuShare) {
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain"); //$NON-NLS-1$
			intent.putExtra(Intent.EXTRA_SUBJECT, lHeader.getText());
			intent.putExtra(Intent.EXTRA_TEXT, lHeader.getText() + "\n" + lIsi.getText()); //$NON-NLS-1$
			startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_renungan)), REQCODE_bagikan);
			
			return true;
		} else if (itemId == R.id.menuRedownload) {
			akanPerlu(this.nama, this.tgl_format.get().format(tanggalan), true);
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	void tampilkan(int skrol) {
		pengulangTampil.removeMessages(0);
		
		tuju(true, skrol);
	}

	void tuju(boolean penting, int skrol) {
		String tgl = tgl_format.get().format(tanggalan);
		IArtikel artikel = S.getDb().cobaAmbilRenungan(nama, tgl);
		if (artikel == null || !artikel.getSiapPakai()) {
			akanPerlu(nama, tgl, penting);
			render(artikel, skrol);
			
			pengulangTampil.sendEmptyMessageDelayed(0, 3000);
		} else {
			Log.d(TAG, "sudah siap tampil, kita syuh yang tersisa dari pengulang tampil"); //$NON-NLS-1$
			pengulangTampil.removeMessages(0);
			
			render(artikel, skrol);
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

	private void render(IArtikel artikel, final int skrol) {
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
				judul.setSpan(new CallbackSpan(artikel.getJudul(), ayatKlikListener), 0, judul.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				
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
			
			// text formats
			lIsi.setTextColor(S.applied.fontColor);
			lIsi.setBackgroundColor(S.applied.backgroundColor);
			lIsi.setTypeface(S.applied.fontFace, S.applied.fontBold);
			lIsi.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
			lIsi.setLineSpacing(0, S.applied.lineSpacingMult);
			
			if (skrol != 0) {
				scrollIsi.post(new Runnable() {
					@Override public void run() {
						scrollIsi.scrollTo(0, skrol);
					}
				});
			}
		} else {
			renderBerhasilBaik  = false;
			
			if (artikel == null) {
				lIsi.setText(R.string.belum_tersedia_menunggu_pengambilan_data_lewat_internet_pastikan_ada);
			} else { // berarti belum siap pakai
				lIsi.setText(R.string.belum_tersedia_mungkin_tanggal_yang_diminta_belum_disiapkan);
			}
		}
		
		String judul = ""; //$NON-NLS-1$
		for (int i = 0; i < AVAILABLE_NAMES.length; i++) {
			if (AVAILABLE_NAMES[i].equals(nama)) {
				judul = ADA_JUDUL[i];
			}
		}
		
		lHeader.setText(judul + "\n" + namaHari(tanggalan) + ", " + DateFormat.getDateFormat(this).format(tanggalan));  //$NON-NLS-1$//$NON-NLS-2$
	}

	private static final int[] NAMA_HARI_RESID = {R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu};

	private String namaHari(Date date) {
		@SuppressWarnings("deprecation") int day = date.getDay();
		return getString(NAMA_HARI_RESID[day]);
	}

	synchronized void akanPerlu(String nama, String tgl, boolean penting) {
		if (S.downloader == null) {
			S.downloader = new Downloader(this, this);
			S.downloader.start();
		}

		IArtikel artikel = null;
		if (nama.equals("rh")) { //$NON-NLS-1$
			artikel = new ArtikelRenunganHarian(tgl);
		} else if (nama.equals("sh")) { //$NON-NLS-1$
			artikel = new ArtikelSantapanHarian(tgl);
		}

		if (artikel != null) {
			boolean tertambah = S.downloader.tambah(artikel, penting);
			if (tertambah) S.downloader.interruptKaloNganggur();
		}
	}
	
	static boolean pemintaMasaDepanLagiJalan = false;
	
	class PemintaMasaDepan extends AsyncTask<Void, Void, Void> {
		@Override protected Void doInBackground(Void... params) {
			if (pemintaMasaDepanLagiJalan) {
				Log.d(TAG, "peminta masa depan lagi jalan"); //$NON-NLS-1$
				return null;
			}
			
			// diem dulu 6 detik
			SystemClock.sleep(6000);
			
			Date hariIni = new Date();
			
			// hapus yang sudah lebih lama dari 3 bulan (90 hari)!
			int terhapus = S.getDb().hapusRenunganBerwaktuSentuhSebelum(new Date(hariIni.getTime() - 90 * 86400000L));
			if (terhapus > 0) {
				Log.d(TAG, "penghapusan renungan berhasil menghapus " + terhapus); //$NON-NLS-1$
			}
			
			pemintaMasaDepanLagiJalan = true;
			try {
				for (int i = 0; i < 31; i++) { // 31 hari ke depan
					String tgl = tgl_format.get().format(hariIni);
					if (S.getDb().cobaAmbilRenungan(nama, tgl) == null) {
						Log.d(TAG, "PemintaMasaDepan perlu minta " + tgl); //$NON-NLS-1$
						akanPerlu(nama, tgl, false);
						
						SystemClock.sleep(1000);
					} else {
						SystemClock.sleep(100); // biar ga berbeban aja
					}
					
					// maju ke besoknya
					hariIni.setTime(hariIni.getTime() + 3600*24*1000);
				}
			} finally {
				pemintaMasaDepanLagiJalan = false;
			}
			
			return null;
		}
	}

	@Override public void onStatusDonlot(final String s) {
		Message msg = Message.obtain(penampilStatusDonlot);
		msg.obj = s;
		msg.sendToTarget();
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_bagikan) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) { //$NON-NLS-1$
						if (U.equals(nama, "sh")) chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.sabda.org/publikasi/e-sh/print/?edisi=" + tgl_format.get().format(tanggalan)); // change text to url //$NON-NLS-1$ //$NON-NLS-2$
						if (U.equals(nama, "rh")) chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.sabda.org/publikasi/e-rh/print/?edisi=" + tgl_format.get().format(tanggalan)); // change text to url //$NON-NLS-1$ //$NON-NLS-2$
					}
					startActivity(chosenIntent);
				}
			}
		}
	}
}
