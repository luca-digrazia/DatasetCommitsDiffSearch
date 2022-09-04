package com.googlecode.androidannotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;

public class RInnerClass {

	public static final RInnerClass EMPTY_R_INNER_CLASS = new RInnerClass(null);

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

	public boolean containsIdValue(Integer idValue) {
		return idQualifiedNamesByIdValues.containsKey(idValue);
	}

	public String getIdQualifiedName(Integer idValue) {
		return idQualifiedNamesByIdValues.get(idValue);
	}
	
	public boolean containsField(String name) {
		return idQualifiedNamesByIdValues.containsValue(rInnerQualifiedName+"."+name);
	}

}
