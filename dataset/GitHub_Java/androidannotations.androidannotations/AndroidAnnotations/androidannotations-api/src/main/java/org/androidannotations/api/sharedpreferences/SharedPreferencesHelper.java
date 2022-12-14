/**
 * Copyright (C) 2010-2012 eBusiness Information, Excilys Group
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
package org.androidannotations.api.sharedpreferences;

import android.content.SharedPreferences;

public abstract class SharedPreferencesHelper {

	private final SharedPreferences sharedPreferences;

	public SharedPreferencesHelper(SharedPreferences sharedPreferences) {
		this.sharedPreferences = sharedPreferences;
	}

	public final SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}

	public final void clear() {
		SharedPreferencesCompat.apply(sharedPreferences.edit().clear());
	}

	protected IntPrefField intField(String key, int defaultValue) {
		return new IntPrefField(sharedPreferences, key, defaultValue);
	}

	protected StringPrefField stringField(String key, String defaultValue) {
		return new StringPrefField(sharedPreferences, key, defaultValue);
	}

	protected StringSetPrefField stringSetField(String key) {
		return new StringSetPrefField(sharedPreferences, key);
	}

	protected BooleanPrefField booleanField(String key, boolean defaultValue) {
		return new BooleanPrefField(sharedPreferences, key, defaultValue);
	}

	protected FloatPrefField floatField(String key, float defaultValue) {
		return new FloatPrefField(sharedPreferences, key, defaultValue);
	}

	protected LongPrefField longField(String key, long defaultValue) {
		return new LongPrefField(sharedPreferences, key, defaultValue);
	}
}
