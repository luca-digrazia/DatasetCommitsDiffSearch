/*
 * Copyright 2011 Pierre-Yves Ricau (py.ricau at gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.googlecode.androidannotations.api.sharedpreferences;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public abstract class AbstractPrefField {
	
	protected final SharedPreferences sharedPreferences;
	protected final String key;
	
	public AbstractPrefField(SharedPreferences sharedPreferences, String key) {
		this.sharedPreferences = sharedPreferences;
		this.key = key;
	}

	public final boolean exists() {
		return sharedPreferences.contains(key);
	}
	
	public final void remove() {
		apply(edit().remove(key));
	}
	
	protected Editor edit() {
		return sharedPreferences.edit();
	}
	
	protected final void apply(Editor editor) {
		SharedPreferencesCompat.apply(editor);
	}


}
