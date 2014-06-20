package yuku.alkitab.base.widget;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.ac.AboutActivity;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.base.ac.SettingsActivity;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.debug.R;

public abstract class LeftDrawer extends ScrollView {

	// mandatory
	Button bBible;
	Button bDevotion;
	Button bReadingPlan;
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
		bDevotion = V.get(this, R.id.bDevotion);
		bReadingPlan = V.get(this, R.id.bReadingPlan);
		bSettings = V.get(this, R.id.bSettings);
		bHelp = V.get(this, R.id.bHelp);

		if (this instanceof Text) bBible.setTextColor(0xff33b5e5);
		if (this instanceof Devotion) bDevotion.setTextColor(0xff33b5e5);
		if (this instanceof ReadingPlan) bReadingPlan.setTextColor(0xff33b5e5);

		bBible.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				bBible_click();
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

	@Override
	public boolean onDragEvent(final DragEvent event) {
		if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
			if (event.getClipDescription().hasMimeType(VerseItem.PROGRESS_MARK_DRAG_MIME_TYPE)) {
				return true; // Just to that the progress pin is not dropped to the verses
			}
		}
		return false;
	}

	public void closeDrawer() {
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

	void bBible_click() {
		activity.startActivity(IsiActivity.createIntent());
	}

	public static class Text extends LeftDrawer {
		public interface Listener {
			void bMarkers_click();
			void bDisplay_click();
			void cFullScreen_checkedChange(boolean isChecked);
			void cNightMode_checkedChange(boolean isChecked);
			void cSplitVersion_checkedChange(final Switch cSplitVersion, boolean isChecked);
			void bProgress_click(int preset_id);
		}

		public interface Handle {
			void setFullScreen(boolean fullScreen);
			void setNightMode(boolean nightMode);
			void setSplitVersion(boolean splitVersion);
		}

		View bMarkers;
		View bDisplay;
		Switch cFullScreen;
		Switch cNightMode;
		Switch cSplitVersion;

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

			@Override
			public void setSplitVersion(final boolean splitVersion) {
				cSplitVersion.setOnCheckedChangeListener(null);
				cSplitVersion.setChecked(splitVersion);
				cSplitVersion.setOnCheckedChangeListener(cSplitVersion_checkedChange);
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

			bMarkers = V.get(this, R.id.bMarkers);
			bDisplay = V.get(this, R.id.bDisplay);
			cFullScreen = V.get(this, R.id.cFullScreen);
			cNightMode = V.get(this, R.id.cNightMode);
			cSplitVersion = V.get(this, R.id.cSplitVersion);

			bProgress1 = V.get(this, R.id.bProgress1);
			bProgress2 = V.get(this, R.id.bProgress2);
			bProgress3 = V.get(this, R.id.bProgress3);
			bProgress4 = V.get(this, R.id.bProgress4);
			bProgress5 = V.get(this, R.id.bProgress5);

			cNightMode.setChecked(Preferences.getBoolean(Prefkey.is_night_mode, false));

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
				b.setOnLongClickListener(new OnLongClickListener() {
					@Override
					public boolean onLongClick(final View v) {
						final ClipData dragData = new ClipData("progress_mark", new String[]{VerseItem.PROGRESS_MARK_DRAG_MIME_TYPE}, new ClipData.Item("" + preset_id));
						b.setPressed(false);
						final DragShadowBuilder dragShadowBuilder = new DragShadowBuilder(b);
						performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
						v.startDrag(dragData, dragShadowBuilder, null, 0);

						return true;
					}
				});
			}

			bMarkers.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bMarkers_click();
					closeDrawer();
				}
			});

			bDisplay.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bDisplay_click();
					closeDrawer();
				}
			});

			cFullScreen.setOnCheckedChangeListener(cFullScreen_checkedChange);

			cNightMode.setOnCheckedChangeListener(cNightMode_checkedChange);

			cSplitVersion.setOnCheckedChangeListener(cSplitVersion_checkedChange);
		}


		CompoundButton.OnCheckedChangeListener cFullScreen_checkedChange = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				listener.cFullScreen_checkedChange(isChecked);
			}
		};

		CompoundButton.OnCheckedChangeListener cNightMode_checkedChange = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				listener.cNightMode_checkedChange(isChecked);
			}
		};

		CompoundButton.OnCheckedChangeListener cSplitVersion_checkedChange = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				listener.cSplitVersion_checkedChange(cSplitVersion, isChecked);
				closeDrawer();
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
			void bReload_click();
			void cbKind_itemSelected(DevotionActivity.DevotionKind kind);
		}

		public interface Handle {
			void setDevotionDate(CharSequence date);
			void setDevotionKind(DevotionActivity.DevotionKind kind);
		}

		Spinner cbKind;
		TextView tCurrentDate;
		View bPrev;
		View bNext;
		View bReload;

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
			bReload = V.get(this, R.id.bReload);

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

			bReload.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bReload_click();
					closeDrawer();
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

	public static class ReadingPlan extends LeftDrawer {
		public interface Listener {
			void bCatchMeUp_click();
		}

		public interface Handle {
			void setDescription(CharSequence description);
		}

		ScrollView scrollDescription;
		TextView tDescription;
		View bCatchMeUp;

		Listener listener;
		Handle handle = new Handle() {
			@Override
			public void setDescription(final CharSequence description) {
				if (description == null) {
					bCatchMeUp.setVisibility(GONE);
					scrollDescription.setVisibility(GONE);
					tDescription.setText("");
				} else {
					bCatchMeUp.setVisibility(VISIBLE);
					scrollDescription.setVisibility(VISIBLE);
					tDescription.setText(description);
				}
			}
		};

		public ReadingPlan(final Context context, final AttributeSet attrs) {
			super(context, attrs);
		}

		public Handle getHandle() {
			return handle;
		}

		@Override
		protected void onFinishInflate() {
			super.onFinishInflate();

			scrollDescription = V.get(this, R.id.scrollDescription);
			tDescription = V.get(this, R.id.tDescription);
			bCatchMeUp = V.get(this, R.id.bCatchMeUp);

			bCatchMeUp.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(final View v) {
					listener.bCatchMeUp_click();
				}
			});
		}

		@Override
		void bReadingPlan_click() {
			closeDrawer();
		}

		public <T extends Activity & Listener> void configure(T listener, DrawerLayout drawerLayout) {
			this.activity = listener;
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}
	}
}
