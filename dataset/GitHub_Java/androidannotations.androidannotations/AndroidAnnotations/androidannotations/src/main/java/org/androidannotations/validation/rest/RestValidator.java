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
package org.androidannotations.validation.rest;

import java.lang.annotation.Annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import org.androidannotations.annotations.rest.Rest;
import org.androidannotations.helper.TargetAnnotationHelper;
import org.androidannotations.helper.ValidatorHelper;
import org.androidannotations.model.AnnotationElements;
import org.androidannotations.validation.ElementValidator;
import org.androidannotations.validation.IsValid;

public class RestValidator implements ElementValidator {

	private final ValidatorHelper validatorHelper;

	public RestValidator(ProcessingEnvironment processingEnv) {
		TargetAnnotationHelper annotationHelper = new TargetAnnotationHelper(processingEnv, getTarget());
		validatorHelper = new ValidatorHelper(annotationHelper);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return Rest.class;
	}

	@Override
	public boolean validate(Element element, AnnotationElements validatedElements) {
		IsValid valid = new IsValid();

		TypeElement typeElement = (TypeElement) element;

		validatorHelper.notAlreadyValidated(element, validatedElements, valid);

		validatorHelper.hasSpringAndroidJars(element, valid);

		validatorHelper.isInterface(typeElement, valid);

		validatorHelper.isTopLevel(typeElement, valid);

		validatorHelper.doesNotExtendOtherInterfaces(typeElement, valid);

		validatorHelper.unannotatedMethodReturnsRestTemplate(typeElement, valid);

		validatorHelper.validateConverters(element, valid);

		validatorHelper.validateInterceptors(element, valid);

		return valid.isValid();
	}

}
