package yuku.alkitab.base.fr;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.fr.base.BaseFragment;

public class GotoDirectFragment extends BaseFragment {
	public static final String TAG = GotoDirectFragment.class.getSimpleName();
	
	TextView lContohLoncat;
	EditText tAlamatLoncat;
	
	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_direct, container, false);
		lContohLoncat = V.get(res, R.id.lContohLoncat);
		tAlamatLoncat = V.get(res, R.id.tAlamatLoncat);
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		{
			String alamatContoh = "*Contoh alamat"; // TODO S.alamat(S.kitabAktif, IsiActivity.this.pasal_1, getAyatBerdasarSkrol());
			String text = getString(R.string.loncat_ke_alamat_titikdua);
			int pos = text.indexOf("%s"); //$NON-NLS-1$
			if (pos >= 0) {
				SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append(text.substring(0, pos));
				sb.append(alamatContoh);
				sb.append(text.substring(pos + 2));
				sb.setSpan(new StyleSpan(Typeface.BOLD), pos, pos + alamatContoh.length(), 0);
				lContohLoncat.setText(sb, BufferType.SPANNABLE);
			}
		}
		
//		TODO final DialogInterface.OnClickListener loncat_click = new DialogInterface.OnClickListener() {
//			@Override public void onClick(DialogInterface dialog, int which) {
//				int ari = loncatKe(tAlamatLoncat.getText().toString());
//				if (ari != 0) {
//					sejarah.tambah(ari);
//				}
//			}
//		};
		
		tAlamatLoncat.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
//				loncat_click.onClick(dialog, 0);
//				dialog.dismiss();
				
				return true;
			}
		});
		
//		TODO dialog.setOnDismissListener(new OnDismissListener() {
//			@Override public void onDismiss(DialogInterface _) {
//				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
//			}
//		});
		
		// TODO dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}
}
