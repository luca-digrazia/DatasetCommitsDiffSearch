/*
 * Copyright (C)  Tony Green, Litepal Framework Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litepal.tablemanager.typechange;

/**
 * This class deals with text type.
 * 
 * @author Tony Green
 * @since 1.0
 */
public class TextOrm extends OrmChange {

	/**
	 * If the field type passed in is char or String, it will change it into
	 * text as column type. Column name will be same as field name. Then return
	 * an array of the combination.
	 */
	@Override
	public String[] object2Relation(String className, String fieldName, String fieldType) {
		if (fieldName != null && fieldType != null) {
			String[] relations = new String[2];
			relations[0] = fieldName;
			if (fieldType.equals("char") || fieldType.equals("java.lang.Character")) {
				relations[1] = "TEXT";
				return relations;
			}
			if (fieldType.equals("java.lang.String")) {
				relations[1] = "TEXT";
				return relations;
			}
		}
		return null;
	}

}
