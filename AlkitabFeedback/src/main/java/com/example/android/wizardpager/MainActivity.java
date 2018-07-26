/*
 * Copyright 2012 Roman Nurik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wizardpager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.TextViewCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.example.android.wizardpager.wizard.model.AbstractWizardModel;
import com.example.android.wizardpager.wizard.model.CustomerInfoPage;
import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.TextareaPage;
import com.example.android.wizardpager.wizard.ui.PageFragmentCallbacks;
import com.example.android.wizardpager.wizard.ui.ReviewFragment;
import com.example.android.wizardpager.wizard.ui.StepPagerStrip;
import yuku.alkitabfeedback.AlkitabFeedbackModel;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.alkitabfeedback.R;

import java.util.List;

public class MainActivity extends FragmentActivity implements
	PageFragmentCallbacks,
	ReviewFragment.Callbacks,
	ModelCallbacks {
	private ViewPager mPager;
	private MyPagerAdapter mPagerAdapter;

	private boolean mEditingAfterReview;

	private AbstractWizardModel mWizardModel;

	private boolean mConsumePageSelectedEvent;

	private Button mNextButton;
	private Button mPrevButton;

	private List<Page> mCurrentPageSequence;
	private StepPagerStrip mStepPagerStrip;

	public static Intent createIntent(Context context) {
		return new Intent(context, MainActivity.class);
	}

	public static Intent createIntent(Context context, String patchTextMessage) {
		return new Intent(context, MainActivity.class).putExtra("patchTextMessage", patchTextMessage);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.alkitabfeedback_activity_main);

		mWizardModel = new AlkitabFeedbackModel(this);

		if (savedInstanceState != null) {
			mWizardModel.load(savedInstanceState.getBundle("model"));
		} else {
			final String patchTextMessage = getIntent().getStringExtra("patchTextMessage");
			if (patchTextMessage != null) {
				final Page message = mWizardModel.findByKey("message");
				final Bundle bundle = new Bundle();
				bundle.putString(TextareaPage.SIMPLE_DATA_KEY, patchTextMessage);
				bundle.putBoolean(TextareaPage.DISABLE_EDITING, true);
				message.resetData(bundle);
			}
		}

		mWizardModel.registerListener(this);

		mPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
		mPager = findViewById(R.id.pager);

		mCurrentPageSequence = mWizardModel.getCurrentPageSequence();

		mPager.setAdapter(mPagerAdapter);
		mStepPagerStrip = findViewById(R.id.strip);
		mStepPagerStrip.setOnPageSelectedListener(new StepPagerStrip.OnPageSelectedListener() {
			@Override
			public void onPageStripSelected(int position) {
				position = Math.min(mPagerAdapter.getCount() - 1, position);
				if (mPager.getCurrentItem() != position) {
					mPager.setCurrentItem(position);
				}
			}
		});

		mNextButton = findViewById(R.id.next_button);
		mPrevButton = findViewById(R.id.prev_button);

		mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				mStepPagerStrip.setCurrentPage(position);

				if (mConsumePageSelectedEvent) {
					mConsumePageSelectedEvent = false;
					return;
				}

				mEditingAfterReview = false;
				updateBottomBar();
			}
		});

		mNextButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (mPager.getCurrentItem() == mCurrentPageSequence.size()) {
					new MaterialDialog.Builder(MainActivity.this)
						.content(R.string.alkitabfeedback_submit_confirm_message)
						.positiveText(R.string.alkitabfeedback_submit_confirm_button)
						.onPositive(new MaterialDialog.SingleButtonCallback() {
							@Override
							public void onClick(@NonNull final MaterialDialog dialog, @NonNull final DialogAction which) {
								String feedback_from_name = null;
								String feedback_from_email = null;
								String feedback_body = null;

								Bundle saved = mWizardModel.save();
								Bundle contact = saved.getBundle("contact");
								if (contact != null) {
									feedback_from_name = contact.getString(CustomerInfoPage.NAME_DATA_KEY);
									feedback_from_email = contact.getString(CustomerInfoPage.EMAIL_DATA_KEY);
								}
								Bundle message = saved.getBundle("message");
								if (message != null) {
									feedback_body = message.getString(TextareaPage.SIMPLE_DATA_KEY);
								}

								if (feedback_from_name != null && feedback_from_email != null && feedback_body != null) {
									FeedbackSender sender = FeedbackSender.getInstance(getApplicationContext());
									sender.addEntry(feedback_from_name, feedback_from_email, feedback_body);
									sender.trySend();
								}

								Toast.makeText(MainActivity.this, R.string.alkitabfeedback_submit_thanks, Toast.LENGTH_LONG).show();
								setResult(RESULT_OK);
								finish();
							}
						})
						.negativeText(android.R.string.cancel)
						.show();
				} else {
					if (mEditingAfterReview) {
						mPager.setCurrentItem(mPagerAdapter.getCount() - 1);
					} else {
						mPager.setCurrentItem(mPager.getCurrentItem() + 1);
					}
				}

			}
		});

		mPrevButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				mPager.setCurrentItem(mPager.getCurrentItem() - 1);
			}
		});

		onPageTreeChanged();
		updateBottomBar();
	}

	@Override
	public void onPageTreeChanged() {
		mCurrentPageSequence = mWizardModel.getCurrentPageSequence();
		recalculateCutOffPage();
		mStepPagerStrip.setPageCount(mCurrentPageSequence.size() + 1); // + 1 = review step
		mPagerAdapter.notifyDataSetChanged();
		updateBottomBar();
	}

	private void updateBottomBar() {
		int position = mPager.getCurrentItem();
		if (position == mCurrentPageSequence.size()) {
			mNextButton.setText(R.string.alkitabfeedback_finish);

			mNextButton.setBackgroundResource(R.drawable.alkitabfeedback_finish_background);
			TextViewCompat.setTextAppearance(mNextButton, R.style.alkitabfeedback_TextAppearanceFinish);
		} else {
			mNextButton.setText(mEditingAfterReview
				? R.string.alkitabfeedback_review
				: R.string.alkitabfeedback_next);

			mNextButton.setTextColor(ResourcesCompat.getColorStateList(getResources(), R.color.alkitabfeedback_button_textcolor, getTheme()));
			mNextButton.setBackgroundResource(R.drawable.alkitabfeedback_selectable_item_background);

			mNextButton.setEnabled(position != mPagerAdapter.getCutOffPage());
		}

		mPrevButton.setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mWizardModel.unregisterListener(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBundle("model", mWizardModel.save());
	}

	@Override
	public AbstractWizardModel onGetModel() {
		return mWizardModel;
	}

	@Override
	public void onEditScreenAfterReview(String key) {
		for (int i = mCurrentPageSequence.size() - 1; i >= 0; i--) {
			if (mCurrentPageSequence.get(i).getKey().equals(key)) {
				mConsumePageSelectedEvent = true;
				mEditingAfterReview = true;
				mPager.setCurrentItem(i);
				updateBottomBar();
				break;
			}
		}
	}

	@Override
	public void onPageDataChanged(Page page) {
		if (page.isRequired()) {
			if (recalculateCutOffPage()) {
				mPagerAdapter.notifyDataSetChanged();
				updateBottomBar();
			}
		}
	}

	@Override
	public Page onGetPage(String key) {
		return mWizardModel.findByKey(key);
	}

	private boolean recalculateCutOffPage() {
		// Cut off the pager adapter at first required page that isn't completed
		int cutOffPage = mCurrentPageSequence.size() + 1;
		for (int i = 0; i < mCurrentPageSequence.size(); i++) {
			Page page = mCurrentPageSequence.get(i);
			if (page.isRequired() && !page.isCompleted()) {
				cutOffPage = i;
				break;
			}
		}

		if (mPagerAdapter.getCutOffPage() != cutOffPage) {
			mPagerAdapter.setCutOffPage(cutOffPage);
			return true;
		}

		return false;
	}

	public class MyPagerAdapter extends FragmentStatePagerAdapter {
		private int mCutOffPage;
		private Fragment mPrimaryItem;

		public MyPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int i) {
			if (i >= mCurrentPageSequence.size()) {
				return new ReviewFragment();
			}

			return mCurrentPageSequence.get(i).createFragment();
		}

		@Override
		public int getItemPosition(Object object) {
			// TODO: be smarter about this
			if (object == mPrimaryItem) {
				// Re-use the current fragment (its position never changes)
				return POSITION_UNCHANGED;
			}

			return POSITION_NONE;
		}

		@Override
		public void setPrimaryItem(ViewGroup container, int position, Object object) {
			super.setPrimaryItem(container, position, object);
			mPrimaryItem = (Fragment) object;
		}

		@Override
		public int getCount() {
			return Math.min(mCutOffPage + 1, mCurrentPageSequence.size() + 1);
		}

		public void setCutOffPage(int cutOffPage) {
			if (cutOffPage < 0) {
				cutOffPage = Integer.MAX_VALUE;
			}
			mCutOffPage = cutOffPage;
		}

		public int getCutOffPage() {
			return mCutOffPage;
		}
	}

	@Override
	public Context getContext() {
		return this;
	}
}
