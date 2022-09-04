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
package com.googlecode.androidannotations.validation;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.ItemLongClick;
import com.googlecode.androidannotations.helper.ValidatorHelper;
import com.googlecode.androidannotations.model.AnnotationElements;
import com.googlecode.androidannotations.rclass.RClass;
import com.googlecode.androidannotations.rclass.RInnerClass;
import com.googlecode.androidannotations.rclass.RClass.Res;

/**
 * @author Benjamin Fellous
 */
public class ItemLongClickValidator extends ValidatorHelper implements ElementValidator {

	private final RClass rClass;

	public ItemLongClickValidator(ProcessingEnvironment processingEnv, RClass rClass) {
		super(processingEnv);
		this.rClass = rClass;
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return ItemLongClick.class;
	}

	@Override
	public boolean validate(Element element, AnnotationElements validatedElements) {

		IsValid valid = new IsValid();

		validateHasLayout(element, validatedElements, valid);

		ExecutableElement executableElement = (ExecutableElement) element;

		warnVoidReturnType(element, executableElement);

		validateRFieldName(element, valid);

		validateParameters(element, valid, executableElement);

		validateIsNotPrivate(element, valid);

		return valid.isValid();
	}

	private void validateParameters(Element element, IsValid valid, ExecutableElement executableElement) {
		List<? extends VariableElement> parameters = executableElement.getParameters();

		if (parameters.size() != 4) {
			valid.invalidate();
			printAnnotationError(element, annotationName() + " should only be used on a method with 4 parameters, instead of " + parameters.size() + ". " +
					"Parameters should be AdapterView<?>, View, int, long and you must respect the order.");
		}

		if (parameters.size() == 4) {
			if(!parameters.get(0).asType().toString().equals("android.widget.AdapterView<?>")){
				valid.invalidate();
				printAnnotationError(element, annotationName() + " - First parameter must be of type android.widget.AdapterView<?>");
			}
			if(!parameters.get(1).asType().toString().equals("android.view.View")){
				valid.invalidate();
				printAnnotationError(element, annotationName() + " - Second parameter must be of type android.view.View");
			}
			if(!parameters.get(2).asType().getKind().equals(TypeKind.INT)){
				valid.invalidate();
				printAnnotationError(element, annotationName() + " - Third parameter must be of type int");
			}
			if(!parameters.get(3).asType().getKind().equals(TypeKind.LONG)){
				valid.invalidate();
				printAnnotationError(element, annotationName() + " - Fourth parameter must be of type long");
			}
		}
	}

	private void validateRFieldName(Element element, IsValid valid) {
		ItemLongClick annotation = element.getAnnotation(ItemLongClick.class);
		int idValue = annotation.value();

		RInnerClass rInnerClass = rClass.get(Res.ID);
		if (idValue == Click.DEFAULT_VALUE) {
			String methodName = element.getSimpleName().toString();
			if (!rInnerClass.containsField(methodName)) {
				valid.invalidate();
				printAnnotationError(element, "Id not found: R.id." + methodName);
			}
		} else {
			if (!rInnerClass.containsIdValue(idValue)) {
				valid.invalidate();
				printAnnotationError(element, "Id not found: R.id." + idValue);
			}
		}
	}

	private void warnVoidReturnType(Element element, ExecutableElement executableElement) {
		TypeMirror returnType = executableElement.getReturnType();

		if (returnType.getKind() != TypeKind.BOOLEAN) {
			printAnnotationWarning(element, annotationName() + " should only be used on a method with a boolean return type ");
		}
	}
}
