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
import com.googlecode.androidannotations.helper.ValidatorHelper;
import com.googlecode.androidannotations.model.AnnotationElements;
import com.googlecode.androidannotations.rclass.IRClass;
import com.googlecode.androidannotations.rclass.IRInnerClass;
import com.googlecode.androidannotations.rclass.RClass.Res;

public class ClickValidator extends ValidatorHelper implements ElementValidator {

	private static final String ANDROID_VIEW_QUALIFIED_NAME = "android.view.View";
	private final IRClass rClass;

	public ClickValidator(ProcessingEnvironment processingEnv, IRClass rClass) {
		super(processingEnv);
		this.rClass = rClass;
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return Click.class;
	}

	@Override
	public boolean validate(Element element, AnnotationElements validatedElements) {

		IsValid valid = new IsValid();

		validateHasLayout(element, validatedElements, valid);

		ExecutableElement executableElement = (ExecutableElement) element;

		warnNotVoidReturnType(element, executableElement);

		validateRFieldName(element, valid);

		validateParameters(element, valid, executableElement);

		validateIsNotPrivate(element, valid);
		
		validateDoesntThrowException(element, valid);

		return valid.isValid();
	}

	private void validateParameters(Element element, IsValid valid, ExecutableElement executableElement) {
		List<? extends VariableElement> parameters = executableElement.getParameters();

		if (parameters.size() != 0 && parameters.size() != 1) {
			valid.invalidate();
			printAnnotationError(element, annotationName() + " should only be used on a method with zero or one parameter, instead of " + parameters.size());
		}

		if (parameters.size() == 1) {
			VariableElement parameter = parameters.get(0);
			TypeMirror parameterType = parameter.asType();
			if (!parameterType.toString().equals(ANDROID_VIEW_QUALIFIED_NAME)) {
				valid.invalidate();
				printAnnotationError(element, annotationName() + " should only be used on a method with no parameter or a parameter of type android.view.View, not " + parameterType);
			}
		}
	}

	private void validateRFieldName(Element element, IsValid valid) {
		Click annotation = element.getAnnotation(Click.class);
		int idValue = annotation.value();

		IRInnerClass rInnerClass = rClass.get(Res.ID);
		if (idValue == Click.DEFAULT_VALUE) {
			String methodName = element.getSimpleName().toString();
			int lastIndex = methodName.lastIndexOf(actionName());
			if (lastIndex != -1) {
				methodName = methodName.substring(0, lastIndex);
			}
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

	private void warnNotVoidReturnType(Element element, ExecutableElement executableElement) {
		TypeMirror returnType = executableElement.getReturnType();

		if (returnType.getKind() != TypeKind.VOID) {
			printAnnotationWarning(element, annotationName() + " should only be used on a method with a void return type ");
		}
	}
}
