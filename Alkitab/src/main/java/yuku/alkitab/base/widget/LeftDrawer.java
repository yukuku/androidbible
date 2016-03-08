package yuku.alkitab.base.widget;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.SwitchCompat;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.AboutActivity;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.base.ac.SettingsActivity;
import yuku.alkitab.base.ac.SongViewActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.CurrentReading;
import yuku.alkitab.base.util.SongBookUtil;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.List;

public abstract class LeftDrawer extends NestedScrollView {

	// mandatory
	TextView bBible;
	TextView bDevotion;
	TextView bReadingPlan;
	TextView bSongs;
	View bSettings;
	View bHelp;

	// for launching other activities
	final Activity activity;
	// for closing drawer
	DrawerLayout drawerLayout;

	public LeftDrawer(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		activity = isInEditMode()? null: (Activity) context;
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
		bSongs = V.get(this, R.id.bSongs);
		bSettings = V.get(this, R.id.bSettings);
		bHelp = V.get(this, R.id.bHelp);

		final int selectedTextColor = getResources().getColor(R.color.accent);
		if (this instanceof Text) bBible.setTextColor(selectedTextColor);
		if (this instanceof Devotion) bDevotion.setTextColor(selectedTextColor);
		if (this instanceof ReadingPlan) bReadingPlan.setTextColor(selectedTextColor);
		if (this instanceof Songs) bSongs.setTextColor(selectedTextColor);

		// hide and show according to app config
		if (!isInEditMode()) {
			bSongs.setVisibility(AppConfig.get().menuSongs? VISIBLE: GONE);
			bDevotion.setVisibility(AppConfig.get().menuDevotion? VISIBLE: GONE);
			bReadingPlan.setVisibility(AppConfig.get().menuReadingPlan? VISIBLE: GONE);
		}

		bBible.setOnClickListener(v -> {
			bBible_click();
			closeDrawer();
		});

		bDevotion.setOnClickListener(v -> {
			bDevotion_click();
			closeDrawer();
		});

		bReadingPlan.setOnClickListener(v -> {
			bReadingPlan_click();
			closeDrawer();
		});

		bSongs.setOnClickListener(v -> {
			bSongs_click();
			closeDrawer();
		});

		bSettings.setOnClickListener(v -> {
			bSettings_click();
			closeDrawer();
		});

		bHelp.setOnClickListener(v -> {
			bHelp_click();
			closeDrawer();
		});
	}

	@Override
	public boolean onDragEvent(final DragEvent event) {
		if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
			App.trackEvent("pin_drag_started");
			if (event.getClipDescription().hasMimeType(VerseItem.PROGRESS_MARK_DRAG_MIME_TYPE)) {
				return true; // Just to that the progress pin is not dropped to the verses
			}
		}
		return false;
	}

	public void toggleDrawer() {
		if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
			drawerLayout.closeDrawer(GravityCompat.START);
		} else {
			drawerLayout.openDrawer(GravityCompat.START);
		}
	}

	public void closeDrawer() {
		drawerLayout.closeDrawer(GravityCompat.START);
	}

	void bHelp_click() {
		activity.startActivity(AboutActivity.createIntent());
	}

	void bSettings_click() {
		activity.startActivity(SettingsActivity.createIntent());
	}

	/**
	 * When the current activity is not {@link yuku.alkitab.base.IsiActivity},
	 * this clears all activity on this stack,
	 * starts {@link yuku.alkitab.base.IsiActivity} on the background,
	 * and then starts {@link yuku.alkitab.base.ac.ReadingPlanActivity}.
	 */
	void bReadingPlan_click() {
		if (getContext() instanceof IsiActivity) {
			activity.startActivity(ReadingPlanActivity.createIntent());
		} else {
			final Intent baseIntent = IsiActivity.createIntent();
			baseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			final Intent intent = ReadingPlanActivity.createIntent();
			activity.startActivities(new Intent[]{baseIntent, intent});
		}
	}

	/**
	 * When the current activity is not {@link yuku.alkitab.base.IsiActivity},
	 * this clears all activity on this stack,
	 * starts {@link yuku.alkitab.base.IsiActivity} on the background,
	 * and then starts {@link yuku.alkitab.base.ac.SongViewActivity}.
	 */
	void bSongs_click() {
		if (getContext() instanceof IsiActivity) {
			activity.startActivity(SongViewActivity.createIntent());
		} else {
			final Intent baseIntent = IsiActivity.createIntent();
			baseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			final Intent intent = SongViewActivity.createIntent();
			activity.startActivities(new Intent[]{baseIntent, intent});
		}
	}

	/**
	 * When the current activity is not {@link yuku.alkitab.base.IsiActivity},
	 * this clears all activity on this stack,
	 * starts {@link yuku.alkitab.base.IsiActivity} on the background,
	 * and then starts {@link yuku.alkitab.base.ac.DevotionActivity}.
	 */
	void bDevotion_click() {
		if (getContext() instanceof IsiActivity) {
			activity.startActivity(DevotionActivity.createIntent());
		} else {
			final Intent baseIntent = IsiActivity.createIntent();
			baseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			final Intent intent = DevotionActivity.createIntent();
			activity.startActivities(new Intent[]{baseIntent, intent});
		}	}

	/** This clears all activity on this stack and starts {@link yuku.alkitab.base.IsiActivity}. */
	void bBible_click() {
		final Intent intent = IsiActivity.createIntent();
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		activity.startActivity(intent);
	}

	public static class Text extends LeftDrawer {
		public interface Listener {
			void bMarkers_click();
			void bDisplay_click();
			void cFullScreen_checkedChange(boolean isChecked);
			void cNightMode_checkedChange(boolean isChecked);
			void cSplitVersion_checkedChange(final SwitchCompat cSplitVersion, boolean isChecked);
			void bProgressMarkList_click();
			void bProgress_click(int preset_id);
			void bCurrentReadingClose_click();
			void bCurrentReadingReference_click();
		}

		public interface Handle {
			void setFullScreen(boolean fullScreen);
			void setSplitVersion(boolean splitVersion);
		}

		View bMarkers;
		View bDisplay;
		SwitchCompat cFullScreen;
		SwitchCompat cNightMode;
		SwitchCompat cSplitVersion;

		View bProgressMarkList;
		View bProgress1;
		View bProgress2;
		View bProgress3;
		View bProgress4;
		View bProgress5;

		View panelCurrentReadingHeader;
		View bCurrentReadingClose;
		TextView bCurrentReadingReference;

		Listener listener;
		Handle handle = new Handle() {
			@Override
			public void setFullScreen(final boolean fullScreen) {
				cFullScreen.setOnCheckedChangeListener(null);
				cFullScreen.setChecked(fullScreen);
				cFullScreen.setOnCheckedChangeListener(cFullScreen_checkedChange);
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

			bProgressMarkList = V.get(this, R.id.bProgressMarkList);
			bProgress1 = V.get(this, R.id.bProgress1);
			bProgress2 = V.get(this, R.id.bProgress2);
			bProgress3 = V.get(this, R.id.bProgress3);
			bProgress4 = V.get(this, R.id.bProgress4);
			bProgress5 = V.get(this, R.id.bProgress5);

			panelCurrentReadingHeader = V.get(this, R.id.panelCurrentReadingHeader);
			bCurrentReadingClose = V.get(this, R.id.bCurrentReadingClose);
			bCurrentReadingReference = V.get(this, R.id.bCurrentReadingReference);

			cNightMode.setChecked(!isInEditMode() && Preferences.getBoolean(Prefkey.is_night_mode, false));

			bProgressMarkList.setOnClickListener(v -> listener.bProgressMarkList_click());

			final View[] views = new View[]{bProgress1, bProgress2, bProgress3, bProgress4, bProgress5};
			for (int i = 0; i < views.length; i++) {
				final View b = views[i];
				final int preset_id = i;
				b.setOnClickListener(v -> {
					listener.bProgress_click(preset_id);
					closeDrawer();
				});
				b.setOnLongClickListener(v -> {
					final ClipData dragData = new ClipData("progress_mark", new String[]{VerseItem.PROGRESS_MARK_DRAG_MIME_TYPE}, new ClipData.Item("" + preset_id));
					b.setPressed(false);
					final DragShadowBuilder dragShadowBuilder = new DragShadowBuilder(b);
					performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
					closeDrawer();
					v.startDrag(dragData, dragShadowBuilder, null, 0);

					return true;
				});
			}

			bMarkers.setOnClickListener(v -> {
				listener.bMarkers_click();
				closeDrawer();
			});

			bDisplay.setOnClickListener(v -> {
				listener.bDisplay_click();
				closeDrawer();
			});

			cFullScreen.setOnCheckedChangeListener(cFullScreen_checkedChange);

			cNightMode.setOnCheckedChangeListener(cNightMode_checkedChange);

			cSplitVersion.setOnCheckedChangeListener(cSplitVersion_checkedChange);

			bCurrentReadingClose.setOnClickListener(v -> listener.bCurrentReadingClose_click());
			bCurrentReadingReference.setOnClickListener(v -> listener.bCurrentReadingReference_click());

			displayCurrentReading();

			// The following is not in onAttachedFromWindow, because we need to listen to
			// ACTION_ACTIVE_VERSION_CHANGED as early as possible, so we do not end up with
			// a verse reference from a version that was not actually selected during app startup.
			final IntentFilter filter = new IntentFilter();
			filter.addAction(CurrentReading.ACTION_CURRENT_READING_CHANGED);
			filter.addAction(IsiActivity.ACTION_ACTIVE_VERSION_CHANGED);
			App.getLbm().registerReceiver(currentReadingChangeReceiver, filter);
		}

		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();

			App.getLbm().unregisterReceiver(currentReadingChangeReceiver);
		}

		final BroadcastReceiver currentReadingChangeReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				displayCurrentReading();
			}
		};

		void displayCurrentReading() {
			if (isInEditMode()) return;

			final int[] aris = CurrentReading.get();
			if (aris == null) {
				panelCurrentReadingHeader.setVisibility(GONE);
				bCurrentReadingReference.setVisibility(GONE);
			} else {
				panelCurrentReadingHeader.setVisibility(VISIBLE);
				bCurrentReadingReference.setVisibility(VISIBLE);

				bCurrentReadingReference.setText(S.activeVersion.referenceRange(aris[0], aris[1]));
			}
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
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}

		@Override
		void bBible_click() {
			closeDrawer();
		}
	}

	public static class Devotion extends LeftDrawer {

		DevotionKindAdapter adapter;

		public interface Listener {
			void bPrev_click();
			void bNext_click();
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
				cbKind.setSelection(adapter.getPositionForKind(kind));
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


			cbKind.setAdapter(adapter = new DevotionKindAdapter());
			cbKind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
					listener.cbKind_itemSelected(adapter.getItem(position));
				}

				@Override
				public void onNothingSelected(final AdapterView<?> parent) {}
			});


			bPrev.setOnClickListener(v -> listener.bPrev_click());

			bNext.setOnClickListener(v -> listener.bNext_click());

			bReload.setOnClickListener(v -> {
				listener.bReload_click();
				closeDrawer();
			});
		}

		public <T extends Activity & Listener> void configure(T listener, DrawerLayout drawerLayout) {
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}

		@Override
		void bDevotion_click() {
			closeDrawer();
		}

		class DevotionKindAdapter extends EasyAdapter {
			final List<DevotionActivity.DevotionKind> kinds = new ArrayList<>();

			{
				// gather available devotions based on app config
				for (String devotionName : AppConfig.get().devotionNames) {
					kinds.add(DevotionActivity.DevotionKind.getByName(devotionName));
				}
			}

			@Override
			public View newView(final int position, final ViewGroup parent) {
				return LayoutInflater.from(getContext()).inflate(android.R.layout.simple_spinner_item, parent, false);
			}

			@Override
			public DevotionActivity.DevotionKind getItem(final int position) {
				return kinds.get(position);
			}

			@Override
			public void bindView(final View view, final int position, final ViewGroup parent) {
				final DevotionActivity.DevotionKind kind = getItem(position);
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
				return kinds.size();
			}

			public int getPositionForKind(final DevotionActivity.DevotionKind kind) {
				return kinds.indexOf(kind);
			}
		}
	}

	public static class ReadingPlan extends LeftDrawer {
		public interface Listener {
			void bRestart_click();
		}

		public interface Handle {
			void setDescription(CharSequence description);
		}

		NestedScrollView scrollDescription;
		TextView tDescription;
		View bRestart;

		Listener listener;
		Handle handle = new Handle() {
			@Override
			public void setDescription(final CharSequence description) {
				if (description == null) {
					bRestart.setVisibility(GONE);
					scrollDescription.setVisibility(GONE);
					tDescription.setText("");
				} else {
					bRestart.setVisibility(VISIBLE);
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
			bRestart = V.get(this, R.id.bRestart);

			bRestart.setOnClickListener(v -> listener.bRestart_click());
		}

		@Override
		void bReadingPlan_click() {
			closeDrawer();
		}

		public <T extends Activity & Listener> void configure(T listener, DrawerLayout drawerLayout) {
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}
	}

	public static class Songs extends LeftDrawer {
		public interface Listener {
			void songKeypadButton_click(View v);
			void songBookSelected(String name);
			void moreSelected();
		}

		public interface Handle {
			void setOkButtonEnabled(boolean enabled);
			void setAButtonEnabled(boolean enabled);
			void setBButtonEnabled(boolean enabled);
			void setCButtonEnabled(boolean enabled);
			void setBookName(CharSequence bookName);
			void setCode(String code);
		}

		Listener listener;
		Handle handle = new Handle() {
			@Override
			public void setOkButtonEnabled(boolean enabled) {
				bOk.setEnabled(enabled);
			}

			@Override
			public void setAButtonEnabled(boolean enabled) {
				bDigitA.setEnabled(enabled);
			}

			@Override
			public void setBButtonEnabled(boolean enabled) {
				bDigitB.setEnabled(enabled);
			}

			@Override
			public void setCButtonEnabled(boolean enabled) {
				bDigitC.setEnabled(enabled);
			}

			@Override
			public void setBookName(final CharSequence bookName) {
				bChangeBook.setText(bookName);
			}

			@Override
			public void setCode(final String code) {
				bChangeCode.setText(code);
			}

		};

		public Songs(final Context context, final AttributeSet attrs) {
			super(context, attrs);
		}

		TextView bChangeBook;
		TextView bChangeCode;

		Button bOk;
		Button bDigitA;
		Button bDigitB;
		Button bDigitC;

		public Handle getHandle() {
			return handle;
		}

		@Override
		protected void onFinishInflate() {
			super.onFinishInflate();

			bChangeBook = V.get(this, R.id.bChangeBook);
			bChangeCode = V.get(this, R.id.bChangeCode);

			bOk = V.get(this, R.id.bOk);
			bDigitA = V.get(this, R.id.bDigitA);
			bDigitB = V.get(this, R.id.bDigitB);
			bDigitC = V.get(this, R.id.bDigitC);

			bChangeBook.setOnClickListener(v -> {
				final PopupMenu popupChangeBook = SongBookUtil.getSongBookPopupMenu(activity, false, true, bChangeBook);
				popupChangeBook.setOnMenuItemClickListener(SongBookUtil.getSongBookOnMenuItemClickListener(new SongBookUtil.DefaultOnSongBookSelectedListener() {
					@Override
					public void onSongBookSelected(final String name) {
						listener.songBookSelected(name);
					}

					@Override
					public void onMoreSelected() {
						listener.moreSelected();
					}
				}));

				popupChangeBook.show();
			});

			// all buttons
			for (int buttonId: new int[] {
				R.id.bDigit0,
				R.id.bDigit1,
				R.id.bDigit2,
				R.id.bDigit3,
				R.id.bDigit4,
				R.id.bDigit5,
				R.id.bDigit6,
				R.id.bDigit7,
				R.id.bDigit8,
				R.id.bDigit9,
				R.id.bDigitA,
				R.id.bDigitB,
				R.id.bDigitC,
				R.id.bOk,
				R.id.bBackspace,
			}) {
				V.get(this, buttonId).setOnClickListener(button_click);
			}
		}

		OnClickListener button_click = new OnClickListener() {
			@Override public void onClick(View v) {
				if (listener != null) {
					listener.songKeypadButton_click(v);
				}
			}
		};

		@Override
		void bSongs_click() {
			closeDrawer();
		}

		public <T extends Activity & Listener> void configure(T listener, DrawerLayout drawerLayout) {
			this.listener = listener;
			this.drawerLayout = drawerLayout;
		}
	}
}
