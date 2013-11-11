package yuku.alkitab.base.widget;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.debug.R;

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
	private ReadingPlanFloatMenuClickListener readMarkListener;
	private ReadingPlanFloatMenuClickListener descriptionListener;
	private ReadingPlanFloatMenuClickListener closeReadingModeListener;

	private Button bDescription;
	private ImageButton bLeft;
	private ImageButton bRight;
	private CheckBox cbTick;

	public ReadingPlanFloatMenu(final Context context) {
		super(context);
		prepareLayout(context);
	}

	public ReadingPlanFloatMenu(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		prepareLayout(context);
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

	private void prepareLayout(Context context) {
		View view = LayoutInflater.from(context).inflate(R.layout.float_menu_reading_plan, this, true);

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
				readMarkListener.onClick(ariRanges[sequence], ariRanges[sequence + 1]);
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

	}

	public void updateLayout() {

		if (ariRanges == null || readMarks == null) {
			return;
		}

		cbTick.setChecked(readMarks[sequence]);

		SpannableStringBuilder reference = ReadingPlanActivity.getReference(S.activeVersion, new int[] {ariRanges[sequence], ariRanges[sequence + 1]});
		reference.append("\n");
		reference.append("" + (sequence / 2 + 1));
		reference.append("/" + (ariRanges.length / 2));
		bDescription.setText(reference);
		if (sequence == 0) {
			bLeft.setEnabled(false);
			bRight.setEnabled(true);
		} else if (sequence == ariRanges.length - 2) {
			bLeft.setEnabled(true);
			bRight.setEnabled(false);
		} else {
			bLeft.setEnabled(true);
			bRight.setEnabled(true);
		}
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

	public int[] getAriRanges() {
		return ariRanges;
	}

	public int getSequence() { return sequence; }

	public void setLeftNavigationClickListener(final ReadingPlanFloatMenuClickListener leftNavigationClickListener) {
		this.leftNavigationListener = leftNavigationClickListener;
	}

	public void setRightNavigationClickListener(final ReadingPlanFloatMenuClickListener rightNavigationClickListener) {
		this.rightNavigationListener = rightNavigationClickListener;
	}

	public void setReadMarkClickListener(final ReadingPlanFloatMenuClickListener readMarkClickListener) {
		this.readMarkListener = readMarkClickListener;
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
