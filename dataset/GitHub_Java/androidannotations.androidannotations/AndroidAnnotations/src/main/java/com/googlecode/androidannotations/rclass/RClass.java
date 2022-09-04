/*
 * Copyright 2010-2011 Pierre-Yves Ricau (py.ricau at gmail.com)
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
package com.googlecode.androidannotations.rclass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

public class RClass {

	public enum Res {
		LAYOUT, ID, STRING, ARRAY, COLOR;
		public String rName() {
			return toString().toLowerCase();
		}
	}

	private final Map<String, RInnerClass> rClass = new HashMap<String, RInnerClass>();

	public RClass(TypeElement rClassElement) {
		List<TypeElement> rInnerTypeElements = extractRInnerTypeElements(rClassElement);

		for (TypeElement rInnerTypeElement : rInnerTypeElements) {
			RInnerClass rInnerClass = new RInnerClass(rInnerTypeElement);
			rClass.put(rInnerTypeElement.getSimpleName().toString(), rInnerClass);
		}
	}

	private List<TypeElement> extractRInnerTypeElements(TypeElement rClassElement) {
		List<? extends Element> rEnclosedElements = rClassElement.getEnclosedElements();
		return ElementFilter.typesIn(rEnclosedElements);
	}

	public RInnerClass get(Res res) {

		String id = res.rName();

		RInnerClass rInnerClass = rClass.get(id);
		if (rInnerClass != null) {
			return rInnerClass;
		} else {
			return RInnerClass.EMPTY_R_INNER_CLASS;
		}
	}
}
