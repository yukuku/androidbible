package yuku.alkitab.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.debug.R;

public class ReadingPlanFloatMenu extends LinearLayout {

	private int[] ariRanges;
	private boolean[] readReadings;
	private int sequence;

	private ReadingPlanFloatMenuNavigationClickListener leftNavigation;
	private ReadingPlanFloatMenuNavigationClickListener rightNavigation;
	private TextView tDescription;
	private ImageButton bLeft;
	private ImageButton bRight;

	public ReadingPlanFloatMenu(final Context context) {
		super(context);
		prepareLayout(context);
	}

	public ReadingPlanFloatMenu(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		prepareLayout(context);
	}

	public void load(int[] ariRanges, boolean[] readReadings, int sequence) {
		this.ariRanges = ariRanges;
		this.readReadings = readReadings;
		this.sequence = sequence;
		updateLayout();
	}

	private void prepareLayout(Context context) {
		View view = LayoutInflater.from(context).inflate(R.layout.float_menu_reading_plan, this, true);

		tDescription = V.get(view, R.id.tDescription);
		bLeft = V.get(view, R.id.bNavLeft);
		bRight = V.get(view, R.id.bNavRight);
	}

	private void updateLayout() {

		if (ariRanges == null || readReadings == null) {
			return;
		}

		bLeft.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (sequence != 0) {
					sequence += -2;
					leftNavigation.onClick(ariRanges[sequence]);
					updateLayout();
				}
			}
		});

		bRight.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (sequence != ariRanges.length - 2) {
					sequence += 2;
					rightNavigation.onClick(ariRanges[sequence]);
					updateLayout();
				}
			}
		});

		tDescription.setText(ReadingPlanActivity.getReference(S.activeVersion, new int[] {ariRanges[sequence], ariRanges[sequence + 1]}));
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

	public void setLeftNavigationClickListener(final ReadingPlanFloatMenuNavigationClickListener leftNavigation) {
		this.leftNavigation = leftNavigation;
	}

	public void setRightNavigationClickListener(final ReadingPlanFloatMenuNavigationClickListener rightNavigation) {
		this.rightNavigation = rightNavigation;
	}

	public interface ReadingPlanFloatMenuNavigationClickListener {
		public void onClick(int ari);
	}

}
