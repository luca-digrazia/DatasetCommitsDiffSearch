/**
 * Copyright (C) 2010-2014 eBusiness Information, Excilys Group
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
package org.androidannotations.test15;

import java.util.ArrayList;

import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.Result;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

@EActivity(R.layout.views_injected)
public class AwaitingResultActivity extends Activity {

	static final int FIRST_REQUEST = 11;
	static final int SECOND_REQUEST = 22;
	static final int THIRD_REQUEST = 33;
	static final int FORTH_REQUEST = 44;
	boolean onResultCalled = false;
	boolean onResultWithDataCalled = false;
	boolean onActivityResultWithResultCodeAndDataCalled = false;
	boolean onActivityResultWithDataAndResultCodeCalled = false;
	boolean onResultWithIntResultCodeCalled = false;
	boolean onResultWithIntegerResultCodeCalled = false;
	boolean onResultWithResultExtraCodeCalled = false;

	@OnActivityResult(FIRST_REQUEST)
	void onResult() {
		onResultCalled = true;
	}

	@OnActivityResult(SECOND_REQUEST)
	void onResultWithData(Intent intentData) {
		onResultWithDataCalled = true;
	}

	@OnActivityResult(SECOND_REQUEST)
	void onActivityResultWithResultCodeAndData(int result, Intent intentData) {
		onActivityResultWithResultCodeAndDataCalled = true;
	}

	@OnActivityResult(SECOND_REQUEST)
	void onActivityResultWithDataAndResultCode(Intent intentData, int result) {
		onActivityResultWithDataAndResultCodeCalled = true;
	}

	@OnActivityResult(THIRD_REQUEST)
	void onResultWithIntResultCode(int resultCode) {
		onResultWithIntResultCodeCalled = true;
	}

	@OnActivityResult(THIRD_REQUEST)
	void onResultWithIntegerResultCode(Integer resultCodeInteger) {
		onResultWithIntegerResultCodeCalled = true;
	}

	@OnActivityResult(FORTH_REQUEST)
	void onResultWithResultExtra(int resultCode, @Result("value") int i, @Result String s, @Result Uri uri,
	        @Result ArrayList<Uri> uris, @Result String[] strings) {
		onResultWithResultExtraCodeCalled = true;
	}
}
