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

package com.example.android.wizardpager.wizard.model;

import android.support.v4.app.Fragment;
import android.text.TextUtils;

import java.util.ArrayList;

import yuku.alkitabfeedback.R;

import com.example.android.wizardpager.wizard.ui.TextareaFragment;

/**
 * A page asking for a name and an email.
 */
public class TextareaPage extends Page {
    private final String key;

	public TextareaPage(String key, ModelCallbacks callbacks, String title) {
        super(callbacks, title);
		this.key = key;
    }
	
	@Override public String getKey() {
		return key;
	}
    
    @Override
    public Fragment createFragment() {
        return TextareaFragment.create(getKey());
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
        dest.add(new ReviewItem(getContext().getString(R.string.alkitabfeedback_label_message), mData.getString(Page.SIMPLE_DATA_KEY), getKey()));
    }

    @Override
    public boolean isCompleted() {
        return !TextUtils.isEmpty(mData.getString(Page.SIMPLE_DATA_KEY));
    }
}
