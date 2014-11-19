package yuku.alkitab.base.sync;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

public class SyncLoginActivity extends BaseActivity {

	public static class Result {
		public String accountName;
		public String simpleToken;
	}

	public static Intent createIntent() {
		return new Intent(App.context, SyncLoginActivity.class);
	}

	public static Result obtainResult(final Intent data) {
		if (data == null) return null;
		final Result res = new Result();
		res.accountName = data.getStringExtra("accountName");
		res.simpleToken = data.getStringExtra("simpleToken");
		return res;
	}

	TextView tIntro;
	EditText tEmail;
	EditText tPassword;
	EditText tPasswordNew;
	Button bForgot;
	View panelRegister;
	EditText tChurch;
	EditText tCity;
	Spinner cbReligion;
	TextView tPrivacy;
	Button bRegister;
	Button bLogin;
	Button bChangePassword;

	ReligionAdapter religionAdapter;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, true);

		setContentView(R.layout.activity_sync_login);

		tIntro = V.get(this, R.id.tIntro);
		tEmail = V.get(this, R.id.tEmail);
		tPassword = V.get(this, R.id.tPassword);
		tPasswordNew = V.get(this, R.id.tPasswordNew);
		bForgot = V.get(this, R.id.bForgot);
		panelRegister = V.get(this, R.id.panelRegister);
		tChurch = V.get(this, R.id.tChurch);
		tCity = V.get(this, R.id.tCity);
		cbReligion = V.get(this, R.id.cbReligion);
		tPrivacy = V.get(this, R.id.tPrivacy);
		bRegister = V.get(this, R.id.bRegister);
		bLogin = V.get(this, R.id.bLogin);
		bChangePassword = V.get(this, R.id.bChangePassword);

		cbReligion.setAdapter(religionAdapter = new ReligionAdapter());

		panelRegister.setVisibility(View.GONE);
		panelRegister.setTag(/* opened: */ false);

		bRegister.setOnClickListener(v -> {
			if (Boolean.FALSE.equals(panelRegister.getTag())) {
				panelRegister.setVisibility(View.VISIBLE);
				panelRegister.setTag(/* opened: */ true);
				bLogin.setVisibility(View.GONE);
			} else {
				if (tEmail.length() == 0) {
					tEmail.setError(getString(R.string.sync_login_error_required));
				} else {
					tEmail.setError(null);
				}

				if (tPassword.length() == 0) {
					tPassword.setError(getString(R.string.sync_login_error_required));
				} else {
					tPassword.setError(null);
				}

				// TODO really register
			}
		});

		tPassword.setOnFocusChangeListener((v,hasFocus) -> bForgot.setVisibility(tPassword.length() > 0 || hasFocus ? View.GONE : View.VISIBLE));

		bForgot.setOnClickListener(v -> {
			if (tEmail.length() == 0) {
				tEmail.setError(getString(R.string.sync_login_error_required));
			} else {
				tEmail.setError(null);
			}
		});

		tIntro.setMovementMethod(LinkMovementMethod.getInstance());
		tPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sync_login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.menuChangePassword) {
			bLogin.setVisibility(View.GONE);
			bRegister.setVisibility(View.GONE);
			bChangePassword.setVisibility(View.VISIBLE);

			tPasswordNew.setVisibility(View.VISIBLE);
			panelRegister.setVisibility(View.GONE);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	class ReligionAdapter extends EasyAdapter {
		Object[][] choices = {
			{null, R.string.sync_login_survey_religion_select_one},
			{"christianity", R.string.sync_login_survey_religion_christianity},
			{"christianity.protestant", R.string.sync_login_survey_religion_christianity_protestant},
			{"christianity.lutheran", R.string.sync_login_survey_religion_christianity_lutheran},
			{"christianity.methodist", R.string.sync_login_survey_religion_christianity_methodist},
			{"christianity.anglican", R.string.sync_login_survey_religion_christianity_anglican},
			{"christianity.pentecostal", R.string.sync_login_survey_religion_christianity_pentecostal},
			{"christianity.roman", R.string.sync_login_survey_religion_christianity_roman},
			{"christianity.other", R.string.sync_login_survey_religion_christianity_other},
			{"islam", R.string.sync_login_survey_religion_islam},
			{"buddha", R.string.sync_login_survey_religion_buddha},
			{"hindu", R.string.sync_login_survey_religion_hindu},
			{"other", R.string.sync_login_survey_religion_other},
			{"atheist", R.string.sync_login_survey_religion_atheist},
			{"agnostic", R.string.sync_login_survey_religion_agnostic},
		};

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_spinner_item, parent, false);
		}

		@Override
		public View newDropDownView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final TextView text = (TextView) view;
			text.setText(getString((int) choices[position][1]));
		}

		@Override
		public int getCount() {
			return choices.length;
		}
	}
}
