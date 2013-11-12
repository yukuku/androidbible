package yuku.alkitab.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.IntArrayList;

public class ReadingPlanFloatMenu extends LinearLayout {

	public boolean isActive;
	public boolean isAnimating;
	private long id;
	private int dayNumber;
	private int[] ariRanges;
	private boolean[] readMarks;
	private int sequence;

	private ReadingPlanFloatMenuClickListener leftNavigationListener;
	private ReadingPlanFloatMenuClickListener rightNavigationListener;
	private ReadingPlanFloatMenuClickListener descriptionListener;
	private ReadingPlanFloatMenuClickListener closeReadingModeListener;

	private Button bDescription;
	private ImageButton bLeft;
	private ImageButton bRight;
	private CheckBox cbTick;

	private Toast tooltip;

	@Override
	public boolean onInterceptTouchEvent(final MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			clearAnimation();
			fadeoutAnimation(5000);
		}

		return super.onInterceptTouchEvent(ev);
	}

	public ReadingPlanFloatMenu(final Context context) {
		super(context);
		prepareLayout();
	}

	public ReadingPlanFloatMenu(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		prepareLayout();
	}

	public void load(long readingPlanId, int dayNumber, int[] ariRanges, int sequence) {
		this.id = readingPlanId;
		this.dayNumber = dayNumber;
		this.ariRanges = ariRanges;
		this.sequence = sequence;
		this.readMarks = new boolean[ariRanges.length];

		updateProgress();
		updateLayout();
	}

	public void updateProgress() {
		IntArrayList readingCodes = S.getDb().getAllReadingCodesByReadingPlanId(id);
		readMarks = new boolean[readMarks.length];
		ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, dayNumber);
	}

	private void prepareLayout() {
		View view = LayoutInflater.from(new ContextThemeWrapper(getContext(), R.style.Theme_AppCompat)).inflate(R.layout.float_menu_reading_plan, this, true);

		bDescription = V.get(view, R.id.bDescription);
		bLeft = V.get(view, R.id.bNavLeft);
		bRight = V.get(view, R.id.bNavRight);
		cbTick = V.get(view, R.id.cbTick);
		ImageButton bClose = V.get(view, R.id.bClose);

		bLeft.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (sequence != 0) {
					sequence += -2;
					leftNavigationListener.onClick(ariRanges[sequence], ariRanges[sequence + 1]);
					updateLayout();
				}
			}
		});

		bRight.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (sequence != ariRanges.length - 2) {
					sequence += 2;
					rightNavigationListener.onClick(ariRanges[sequence], ariRanges[sequence + 1]);
					updateLayout();
				}
			}
		});

		cbTick.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				readMarks[sequence] = isChecked;
				readMarks[sequence + 1] = isChecked;

				ReadingPlanManager.updateReadingPlanProgress(id, dayNumber, sequence / 2, isChecked);
				updateLayout();
			}
		});

		bDescription.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				descriptionListener.onClick(ariRanges[sequence], ariRanges[sequence + 1]);
			}
		});

		bClose.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				closeReadingModeListener.onClick(ariRanges[sequence], ariRanges[sequence + 1]);
			}
		});

		//tooltip
		bLeft.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				showTooltip(R.string.rp_floatPreviousReading);
				return true;
			}
		});

		bRight.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				showTooltip(R.string.rp_floatNextReading);
				return true;
			}
		});

		cbTick.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				showTooltip(R.string.rp_floatCheckMark);
				return true;
			}
		});

		bDescription.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				showTooltip(R.string.rp_floatDescription);
				return true;
			}
		});

		bClose.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				showTooltip(R.string.rp_floatClose);
				return true;
			}
		});

	}

	private void showTooltip(final int resId) {
		if (tooltip == null) {
			tooltip = Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT);
		} else {
			tooltip.setText(resId);
		}
		tooltip.show();
	}

	public void updateLayout() {

		if (ariRanges == null || readMarks == null) {
			return;
		}

		cbTick.setChecked(readMarks[sequence]);

		final StringBuilder reference = ReadingPlanActivity.getReference(S.activeVersion, ariRanges[sequence], ariRanges[sequence + 1]);
		reference.append("\n");
		reference.append(sequence / 2 + 1);
		reference.append("/").append(ariRanges.length / 2);
		bDescription.setText(reference);

		bLeft.setEnabled(sequence != 0);
		bRight.setEnabled(sequence != ariRanges.length - 2);
	}

	public void fadeoutAnimation(long startOffset) {
		AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
		alphaAnimation.setStartOffset(startOffset);
		alphaAnimation.setDuration(1000);
		alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(final Animation animation) {
				ReadingPlanFloatMenu.this.isAnimating = true;
			}

			@Override
			public void onAnimationEnd(final Animation animation) {
				ReadingPlanFloatMenu.this.isAnimating = false;
				ReadingPlanFloatMenu.this.setVisibility(View.GONE);
			}

			@Override
			public void onAnimationRepeat(final Animation animation) {}
		});
		this.startAnimation(alphaAnimation);
	}

	public long getReadingPlanId() {
		return id;
	}

	public void setLeftNavigationClickListener(final ReadingPlanFloatMenuClickListener leftNavigationClickListener) {
		this.leftNavigationListener = leftNavigationClickListener;
	}

	public void setRightNavigationClickListener(final ReadingPlanFloatMenuClickListener rightNavigationClickListener) {
		this.rightNavigationListener = rightNavigationClickListener;
	}

	public void setDescriptionListener(final ReadingPlanFloatMenuClickListener descriptionListener) {
		this.descriptionListener = descriptionListener;
	}

	public void setCloseReadingModeClickListener(final ReadingPlanFloatMenuClickListener closeReadingModeClickListener) {
		this.closeReadingModeListener = closeReadingModeClickListener;
	}


	public interface ReadingPlanFloatMenuClickListener {
		public void onClick(int ari_start, int ari_end);
	}

}
