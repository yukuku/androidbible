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
import java.util.regex.Pattern;

import yuku.alkitabfeedback.R;

import com.example.android.wizardpager.wizard.ui.CustomerInfoFragment;

/**
 * A page asking for a name and an email.
 */
public class CustomerInfoPage extends Page {
    public static final String NAME_DATA_KEY = "name";
    public static final String EMAIL_DATA_KEY = "email";
	private final String key;

    public CustomerInfoPage(String key, ModelCallbacks callbacks, String title) {
        super(callbacks, title);
		this.key = key;
    }
    
    @Override public String getKey() {
    	return key;
    }

    @Override
    public Fragment createFragment() {
        return CustomerInfoFragment.create(getKey());
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
        dest.add(new ReviewItem(getContext().getString(R.string.alkitabfeedback_label_your_name), mData.getString(NAME_DATA_KEY), getKey(), -1));
        dest.add(new ReviewItem(getContext().getString(R.string.alkitabfeedback_label_your_email), mData.getString(EMAIL_DATA_KEY), getKey(), -1));
    }

    // copied from Android SDK 8
    private static final Pattern EMAIL_ADDRESS
    = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
        "\\@" +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
        ")+"
    );

    @Override
    public boolean isCompleted() {
        boolean name_ok = !TextUtils.isEmpty(mData.getString(NAME_DATA_KEY));
        String email = mData.getString(EMAIL_DATA_KEY);
		boolean email_ok = email != null && EMAIL_ADDRESS.matcher(email).matches();
        return name_ok && email_ok;
    }
}
