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
package com.googlecode.androidannotations.test15;

import static org.fest.assertions.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;

import android.os.Bundle;

import com.xtremelabs.robolectric.Robolectric;
import com.xtremelabs.robolectric.shadows.CustomShadowBundle;

@RunWith(AndroidAnnotationsParameterized.class)
public class SaveInstanceStateActivityParameterizedTest {

	@Parameters
	public static Collection<Object[]> generateTestCases() {

		ArrayList<Integer> myIntegerArrayList = new ArrayList<Integer>();
		myIntegerArrayList.add(5);
		myIntegerArrayList.add(8);

		ArrayList<String> myStringList = new ArrayList<String>();
		myStringList.add("S1");
		myStringList.add("S2");

		Object[][] testCases = { //
		//
				{ "myBoolean", true }, //
				{ "myBooleanArray", new boolean[] { true, false, true } }, //
				{ "myBooleanObject", Boolean.TRUE }, //
				{ "myByte", (byte) 8 }, //
				{ "myByteArray", new byte[] { 3, 8, 6 } }, //
				{ "myByteObject", Byte.MAX_VALUE }, //
				{ "myByteObjectArray", new Byte[] { Byte.MAX_VALUE, Byte.MIN_VALUE, 9 } }, //
				{ "myChar", 'c' }, //
				{ "myCharacterArray", new char[] { 'a', 'b', 'c' } }, //
				{ "myCharacterObject", Character.MIN_SURROGATE }, //
				{ "myCharacterObjectArray", new Character[] { 'a', 'c', 'b' } }, //
				{ "myCharSequence", "S5" }, //
				{ "myDouble", 1.05d }, //
				{ "myDoubleArray", new double[] { 1.05d, 2.03d } }, //
				{ "myDoubleObject", 1.08d }, //
				{ "myDoubleObjectArray", new Double[] { 1.05d, 2.03d } }, //
				{ "myFloat", 3.7f }, //
				{ "myFloatArray", new float[] { 3.7f, 3.8f } }, //
				{ "myFloatObject", 3.4f }, //
				{ "myFloatObjectArray", new Float[] { 3.6f, 4.6f } }, //
				{ "myInt", 12 }, //
				{ "myIntegerArray", new int[] { 3, 5, 9 } }, //
				{ "myIntegerObject", 64 }, //
				{ "myIntegerObjectArray", new Integer[] { 7, 45, 14 } }, //
				{ "myIntegerArrayList", myIntegerArrayList }, //
				{ "myLong", 5l }, //
				{ "myLongArray", new long[] { 3, 6, 9 } }, //
				{ "myLongObject", 8l }, //
				{ "myLongObjectArray", new Long[] { 3l, 6l, 9l } }, //
				{ "myShort", (short) 124 }, //
				{ "myShortArray", new short[] { 3, 6, 18 } }, //
				{ "myShortObject", (short) 9 }, //
				{ "myShortObjectArray", new Short[] { 3, 6, 18 } }, //
				{ "myString", "S4" }, //
				{ "myStringArray", new String[] { "S1", "S3" } }, //
				{ "myStringList", myStringList }, //
				// TODO : not working yet
//				{ "mySerializableBean", new MySerializableBean(4) }, //
//				{ "mySerializableBeanArray", new MySerializableBean[] { new MySerializableBean(5), new MySerializableBean(6) } }, //
//				{ "myParcelableBean", new MyParcelableBean(9) }, //
//				{ "myParcelableBeanArray", new MyParcelableBean[] { new MyParcelableBean(3), new MyParcelableBean(9) } }, //
		//
		};
		return Arrays.asList(testCases);
	}
	
	private Object value;
	private String fieldName;

	/**
	 * @see AndroidAnnotationsParameterized
	 */
	public void init(String fieldName, Object value) {
		this.fieldName = fieldName;
		this.value = value;
	}

	private Field field;

	@Before
	public void setup() throws Exception {
		Robolectric.bindShadowClass(CustomShadowBundle.class);
		field = SaveInstanceStateActivity.class.getDeclaredField(fieldName);
		field.setAccessible(true);
	}

	@Test
	public void can_save_field() throws Exception {
		SaveInstanceStateActivity_ savedActivity = new SaveInstanceStateActivity_();

		Bundle bundle = saveField(savedActivity);

		assertThat(bundle.get(fieldName)).isEqualTo(value);
	}

	@Test
	public void can_load_field() throws Exception {
		SaveInstanceStateActivity_ savedActivity = new SaveInstanceStateActivity_();

		Bundle bundle = saveField(savedActivity);

		SaveInstanceStateActivity_ recreatedActivity = new SaveInstanceStateActivity_();

		Object initialFieldValue = field.get(recreatedActivity);

		assertThat(initialFieldValue).isNotEqualTo(value);

		recreatedActivity.onCreate(bundle);

		Object loadedFieldValue = field.get(recreatedActivity);

		assertThat(loadedFieldValue).isEqualTo(value);
	}
	
	private Bundle saveField(SaveInstanceStateActivity_ savedActivity) throws NoSuchFieldException, IllegalAccessException {
		field.set(savedActivity, value);
		Bundle bundle = new Bundle();
		savedActivity.onSaveInstanceState(bundle);
		return bundle;
	}

}
