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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JFieldRef;

public class RInnerClass implements IRInnerClass {

	private final Map<Integer, String> idQualifiedNamesByIdValues = new HashMap<Integer, String>();

	private final String rInnerQualifiedName;

	public RInnerClass(TypeElement rInnerTypeElement) {
		if (rInnerTypeElement != null) {

			rInnerQualifiedName = rInnerTypeElement.getQualifiedName().toString();

			List<? extends Element> idEnclosedElements = rInnerTypeElement.getEnclosedElements();

			List<VariableElement> idFields = ElementFilter.fieldsIn(idEnclosedElements);

			for (VariableElement idField : idFields) {
				TypeKind fieldType = idField.asType().getKind();
				if (fieldType.isPrimitive() && fieldType.equals(TypeKind.INT)) {
					Integer idFieldId = (Integer) idField.getConstantValue();
					idQualifiedNamesByIdValues.put(idFieldId, rInnerQualifiedName + "." + idField.getSimpleName());
				}
			}
		} else {
			rInnerQualifiedName = "";
		}
	}

	@Override
	public boolean containsIdValue(Integer idValue) {
		return idQualifiedNamesByIdValues.containsKey(idValue);
	}

	@Override
	public String getIdQualifiedName(Integer idValue) {
		return idQualifiedNamesByIdValues.get(idValue);
	}

	@Override
	public boolean containsField(String name) {
		return idQualifiedNamesByIdValues.containsValue(rInnerQualifiedName + "." + name);
	}

	@Override
	public String getIdQualifiedName(String name) {
		String idQualifiedName = rInnerQualifiedName + "." + name;

		if (idQualifiedNamesByIdValues.containsValue(idQualifiedName)) {
			return idQualifiedName;
		} else {
			return null;
		}

	}

	@Override
	public JFieldRef getIdStaticRef(Integer idValue, JCodeModel codeModel) {
		String layoutFieldQualifiedName = getIdQualifiedName(idValue);
		return extractIdStaticRef(codeModel, layoutFieldQualifiedName);
	}

	@Override
	public JFieldRef getIdStaticRef(String name, JCodeModel codeModel) {
		String layoutFieldQualifiedName = getIdQualifiedName(name);
		return extractIdStaticRef(codeModel, layoutFieldQualifiedName);
	}

	private JFieldRef extractIdStaticRef(JCodeModel codeModel, String layoutFieldQualifiedName) {
		if (layoutFieldQualifiedName != null) {
			int fieldSuffix = layoutFieldQualifiedName.lastIndexOf('.');
			String fieldName = layoutFieldQualifiedName.substring(fieldSuffix + 1);
			String rInnerClassName = layoutFieldQualifiedName.substring(0, fieldSuffix);

			return codeModel.ref(rInnerClassName).staticRef(fieldName);
		} else {
			return null;
		}
	}

}
