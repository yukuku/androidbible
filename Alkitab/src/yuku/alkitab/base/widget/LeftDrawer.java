package yuku.alkitab.base.widget;

import android.app.Activity;
import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.ac.AboutActivity;
import yuku.alkitab.base.ac.BookmarkActivity;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.base.ac.SettingsActivity;
import yuku.alkitab.debug.R;

public abstract class LeftDrawer extends ScrollView {

	// mandatory
	View bBible;
	View bMarkers;
	View bDevotion;
	View bReadingPlan;
	View bSettings;
	View bHelp;

	// for launching other activities
	Activity activity;
	// for closing drawer
	DrawerLayout drawerLayout;

	public LeftDrawer(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		setClickable(true);
		for (int i = 0, len = getChildCount(); i < len; i++) {
			getChildAt(i).setDuplicateParentStateEnabled(false);
		}

		bBible = V.get(this, R.id.bBible);
		bMarkers = V.get(this, R.id.bMarkers);
		bDevotion = V.get(this, R.id.bDevotion);
		bReadingPlan = V.get(this, R.id.bReadingPlan);
		bSettings = V.get(this, R.id.bSettings);
		bHelp = V.get(this, R.id.bHelp);

		bBible.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bBible_click();
				closeDrawer();
			}
		});

		bMarkers.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bMarkers_click();
				closeDrawer();
			}
		});

		bDevotion.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bDevotion_click();
				closeDrawer();
			}
		});

		bReadingPlan.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bReadingPlan_click();
				closeDrawer();
			}
		});

		bSettings.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bSettings_click();
				closeDrawer();
			}
		});

		bHelp.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bHelp_click();
				closeDrawer();
			}
		});
	}

	void closeDrawer() {
		drawerLayout.closeDrawer(Gravity.LEFT);
	}

	void bHelp_click() {
		activity.startActivity(AboutActivity.createIntent());
	}

	void bSettings_click() {
		activity.startActivity(SettingsActivity.createIntent());
	}

	void bReadingPlan_click() {
		activity.startActivity(ReadingPlanActivity.createIntent());
	}

	void bDevotion_click() {
		activity.startActivity(DevotionActivity.createIntent());
	}

	void bMarkers_click() {
		activity.startActivity(BookmarkActivity.createIntent());
	}

	void bBible_click() {
		activity.startActivity(IsiActivity.createIntent());
	}

	public static class Text extends LeftDrawer {
		public interface Listener {
			void bDisplay_click();
			void cFullScreen_onCheckedChanged(boolean isChecked);
			void cNightMode_onCheckedChanged(boolean isChecked);
			void bProgress_click(int preset_id);
		}

		public interface Handle {
			void setFullScreen(boolean fullScreen);
			void setNightMode(boolean nightMode);
		}

		View bDisplay;
		Switch cFullScreen;
		Switch cNightMode;

		View bProgress1;
		View bProgress2;
		View bProgress3;
		View bProgress4;
		View bProgress5;

		Listener listener;
		Handle handle = new Handle() {
			@Override
			public void setFullScreen(final boolean fullScreen) {
				cFullScreen.setOnCheckedChangeListener(null);
				cFullScreen.setChecked(fullScreen);
				cFullScreen.setOnCheckedChangeListener(cFullScreen_checkedChange);
			}

			@Override
			public void setNightMode(final boolean nightMode) {
				cNightMode.setOnCheckedChangeListener(null);
				cNightMode.setChecked(nightMode);
				cNightMode.setOnCheckedChangeListener(cNightMode_checkedChange);
			}
		};

		public Text(final Context context, final AttributeSet attrs) {
			super(context, attrs);
		}

		public Handle getHandle() {
			return handle;
		}

		@Override
		protected void onFinishInflate() {
			super.onFinishInflate();

			bDisplay = V.get(this, R.id.bDisplay);
			cFullScreen = V.get(this, R.id.cFullScreen);
			cNightMode = V.get(this, R.id.cNightMode);

			bProgress1 = V.get(this, R.id.bProgress1);
			bProgress2 = V.get(this, R.id.bProgress2);
			bProgress3 = V.get(this, R.id.bProgress3);
			bProgress4 = V.get(this, R.id.bProgress4);
			bProgress5 = V.get(this, R.id.bProgress5);

			final View[] views = new View[]{bProgress1, bProgress2, bProgress3, bProgress4, bProgress5};
			for (int i = 0; i < views.length; i++) {
				final View b = views[i];
				final int preset_id = i;
				b.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(final View v) {
						listener.bProgress_click(preset_id);
						closeDrawer();
					}
				});
			}

			bDisplay.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bDisplay_click();
					closeDrawer();
				}
			});

			cFullScreen.setOnCheckedChangeListener(cFullScreen_checkedChange);

			cNightMode.setOnCheckedChangeListener(cNightMode_checkedChange);
		}


		CompoundButton.OnCheckedChangeListener cFullScreen_checkedChange = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				listener.cFullScreen_onCheckedChanged(isChecked);
			}
		};

		CompoundButton.OnCheckedChangeListener cNightMode_checkedChange = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				listener.cNightMode_onCheckedChanged(isChecked);
			}
		};

		public <T extends Activity & Listener> void configure(T listener, DrawerLayout drawerLayout) {
			this.activity = listener;
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}

		@Override
		void bBible_click() {
			closeDrawer();
		}
	}

	public static class Devotion extends LeftDrawer {
		public interface Listener {
			void bPrev_click(TextView tCurrentDate);
			void bNext_click(TextView tCurrentDate);
			void cbKind_itemSelected(DevotionActivity.DevotionKind kind);
		}

		public interface Handle {
			void setDevotionDate(CharSequence date);
			void setDevotionKind(final DevotionActivity.DevotionKind kind);
		}

		Spinner cbKind;
		TextView tCurrentDate;
		View bPrev;
		View bNext;

		Listener listener;
		Handle handle = new Handle() {
			@Override
			public void setDevotionDate(final CharSequence date) {
				tCurrentDate.setText(date);
			}

			@Override
			public void setDevotionKind(final DevotionActivity.DevotionKind kind) {
				final AdapterView.OnItemSelectedListener backup = cbKind.getOnItemSelectedListener();
				cbKind.setOnItemSelectedListener(null);
				cbKind.setSelection(kind.ordinal());
				cbKind.setOnItemSelectedListener(backup);
			}
		};

		public Devotion(final Context context, final AttributeSet attrs) {
			super(context, attrs);
		}

		public Handle getHandle() {
			return handle;
		}

		@Override
		protected void onFinishInflate() {
			super.onFinishInflate();

			cbKind = V.get(this, R.id.cbKind);
			tCurrentDate = V.get(this, R.id.tCurrentDate);
			bPrev = V.get(this, R.id.bPrev);
			bNext = V.get(this, R.id.bNext);

			cbKind.setAdapter(new EasyAdapter() {
				@Override
				public View newView(final int position, final ViewGroup parent) {
					return LayoutInflater.from(getContext()).inflate(android.R.layout.simple_spinner_item, parent, false);
				}

				@Override
				public void bindView(final View view, final int position, final ViewGroup parent) {
					final DevotionActivity.DevotionKind kind = DevotionActivity.DevotionKind.values()[position];
					final SpannableStringBuilder sb = new SpannableStringBuilder();
					sb.append(kind.title);
					sb.append("\n");
					final int sb_len = sb.length();
					sb.append(kind.subtitle);
					sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
					((TextView) view).setText(sb);
				}

				@Override
				public View newDropDownView(final int position, final ViewGroup parent) {
					final TextView res = (TextView) LayoutInflater.from(getContext()).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
					res.setSingleLine(false);
					return res;
				}

				@Override
				public int getCount() {
					return DevotionActivity.DevotionKind.values().length;
				}
			});
			cbKind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
					listener.cbKind_itemSelected(DevotionActivity.DevotionKind.values()[position]);
				}

				@Override
				public void onNothingSelected(final AdapterView<?> parent) {}
			});


			bPrev.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bPrev_click(tCurrentDate);
				}
			});

			bNext.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bNext_click(tCurrentDate);
				}
			});
		}

		public <T extends Activity & Listener> void configure(T listener, DrawerLayout drawerLayout) {
			this.activity = listener;
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}

		@Override
		void bDevotion_click() {
			closeDrawer();
		}
	}
}
