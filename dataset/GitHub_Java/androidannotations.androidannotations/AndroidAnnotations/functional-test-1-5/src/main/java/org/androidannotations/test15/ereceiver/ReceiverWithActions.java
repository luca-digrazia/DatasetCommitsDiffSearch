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
package org.androidannotations.test15.ereceiver;

import org.androidannotations.annotations.EReceiver;
import org.androidannotations.annotations.ReceiverAction;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

@EReceiver
public class ReceiverWithActions extends BroadcastReceiver {

	public static final String ACTION_SIMPLE_TEST = "ACTION_SIMPLE_TEST";
	public static final String ACTION_SCHEME_TEST = "ACTION_SCHEME_TEST";
	public static final String ACTION_PARAMETER_TEST = "ACTION_PARAMETER_TEST";
	public static final String ACTION_MULTIPLE_TEST_1 = "ACTION_MULTIPLE_TEST_1";
	public static final String ACTION_MULTIPLE_TEST_2 = "ACTION_MULTIPLE_TEST_2";
	public static final String ACTION_EXTRA_PARAMETER_TEST = "ACTION_EXTRA_PARAMETER_TEST";
	public static final String EXTRA_ARG_NAME1 = "thisExtraHasAnotherName";
	public static final String EXTRA_ARG_NAME2 = "thisIsMyParameter";
	public static final String DATA_SCHEME = "http";

	public boolean simpleActionReceived = false;
	public boolean actionWithSchemeReceived = false;

	public boolean parameterActionReceived = false;
	public String parameterActionValue = null;

	public boolean extraParameterActionReceived = false;
	public String extraParameterActionValue = null;

	public int multipleActionCall = 0;

	@Override
	public void onReceive(Context context, Intent intent) {
	}

	@ReceiverAction(ACTION_SIMPLE_TEST)
	public void onSimpleAction() {
		simpleActionReceived = true;
	}

	@ReceiverAction(value = ACTION_SCHEME_TEST, dataSchemes = DATA_SCHEME)
	public void onActionWithReceiver() {
		actionWithSchemeReceived = true;
	}

	@ReceiverAction(ACTION_PARAMETER_TEST)
	public void onParameterAction(@ReceiverAction.Extra String thisIsMyParameter) {
		parameterActionReceived = true;
		parameterActionValue = thisIsMyParameter;
	}

	@ReceiverAction(ACTION_EXTRA_PARAMETER_TEST)
	public void onExtraParameterAction(@ReceiverAction.Extra(EXTRA_ARG_NAME1) String thisIsAParameter) {
		extraParameterActionReceived = true;
		extraParameterActionValue = thisIsAParameter;
	}

	@ReceiverAction({ ACTION_MULTIPLE_TEST_1, ACTION_MULTIPLE_TEST_2 })
	public void onMultipleActions() {
		multipleActionCall++;
	}
}
