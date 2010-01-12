package yuku.kbbiandroid;

import android.app.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.TextView.*;

public class LayarPilih extends ListActivity {
	private String cari_;

	public LayarPilih() {
		S.layarPilih = this;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pilih);

		S.kamus = new KamusLuringAndroid(getResources());

		cari_ = getIntent().getStringExtra("cari");
		TextView lEmpty = (TextView) findViewById(android.R.id.empty);
		lEmpty.setText(getResources().getString(R.string.kataYangDicariGaKetemu_s, cari_));
		
		setTitle(getResources().getString(R.string.hasilPencarian_s, cari_));

		tampilkanKandidat();
	}

	private void tampilkanKandidat() {
		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.satukata, R.id.lKata);

		String[] kandidat = S.kamus.kandidat(cari_);

		for (int i = 0; i < 100; i++) {
			if (i >= kandidat.length) break;

			adapter.add(kandidat[i]);
		}

		setListAdapter(adapter);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		TextView lKata = (TextView) ((LinearLayout) v).findViewById(R.id.lKata);
		String kata = lKata.getText().toString();

		{
			final Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.arti);
			dialog.setTitle(kata);
			dialog.setCanceledOnTouchOutside(true);

			TextView lArti = (TextView) dialog.findViewById(R.id.lArti);
			lArti.setText(Html.fromHtml(S.kamus.arti(kata)), BufferType.SPANNABLE);

			Button bTutup = (Button) dialog.findViewById(R.id.bTutup);
			bTutup.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});

			dialog.show();
		}
	}
}
