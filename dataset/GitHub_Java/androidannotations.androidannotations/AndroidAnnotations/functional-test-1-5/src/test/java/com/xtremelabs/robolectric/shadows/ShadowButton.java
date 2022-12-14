/**
 * Copyright (C) 2010-2011 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.xtremelabs.robolectric.shadows;

import android.view.View;
import android.widget.Button;

import com.xtremelabs.robolectric.internal.Implementation;
import com.xtremelabs.robolectric.internal.Implements;

@Implements(Button.class)
public class ShadowButton extends ShadowTextView {

	private View.OnLongClickListener onLongClickListener;

	public ShadowButton() {
		System.out.println();
	}

	@Implementation
	public boolean performLongClick() {
		if (onLongClickListener != null) {
			onLongClickListener.onLongClick(realView);
			return true;
		}
		return false;
	}

	@Implementation
	public void setOnLongClickListener(View.OnLongClickListener onLongClickListener) {
		this.onLongClickListener = onLongClickListener;
	}

}
