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

package yuku.alkitabfeedback;

import android.content.Context;

import com.example.android.wizardpager.wizard.model.AbstractWizardModel;
import com.example.android.wizardpager.wizard.model.CustomerInfoPage;
import com.example.android.wizardpager.wizard.model.PageList;
import com.example.android.wizardpager.wizard.model.TextareaPage;

public class AlkitabFeedbackModel extends AbstractWizardModel {
	public AlkitabFeedbackModel(Context context) {
		super(context);
	}

	@Override protected PageList onNewRootPageList() {
		return new PageList( 
			new TextareaPage("message", this, getContext().getString(R.string.alkitabfeedback_label_message))
			.setRequired(true),
	
			new CustomerInfoPage("contact", this, getContext().getString(R.string.alkitabfeedback_title_about_you))
			.setRequired(true)
		);
	}

	@Override public Context getContext() {
		return super.mContext;
	}
}
