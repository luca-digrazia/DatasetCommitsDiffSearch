/**
 * Copyright (C) 2010-2015 eBusiness Information, Excilys Group
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
package org.androidannotations.otto.handler;

import org.androidannotations.handler.BaseAnnotationHandler;
import org.androidannotations.helper.TargetAnnotationHelper;
import org.androidannotations.helper.ValidatorParameterHelper;
import org.androidannotations.holder.EComponentHolder;
import org.androidannotations.model.AnnotationElements;
import org.androidannotations.process.ElementValidation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

public abstract class AbstractOttoHandler extends BaseAnnotationHandler<EComponentHolder> {

	protected final TargetAnnotationHelper annotationHelper;

	public AbstractOttoHandler(String target, ProcessingEnvironment processingEnvironment) {
		super(target, processingEnvironment);
		annotationHelper = new TargetAnnotationHelper(processingEnv, getTarget());
	}

	@Override
	public void validate(Element element, AnnotationElements validatedElements, ElementValidation valid) {
		if (!annotationHelper.enclosingElementHasEnhancedComponentAnnotation(element)) {
			valid.invalidate();
			return;
		}

		ExecutableElement executableElement = (ExecutableElement) element;

		/*
		 * We check that twice to skip invalid annotated elements
		 */
		validatorHelper.enclosingElementHasEnhancedComponentAnnotation(executableElement, valid);

		validateReturnType(executableElement, valid);

		validatorHelper.isPublic(element, valid);

		validatorHelper.doesntThrowException(executableElement, valid);

		validatorHelper.isNotFinal(element, valid);

		getParamValidator().validate(executableElement, valid);
	}

	protected abstract ValidatorParameterHelper.Validator getParamValidator();

	protected abstract void validateReturnType(ExecutableElement executableElement, ElementValidation validation);

	@Override
	public void process(Element element, EComponentHolder holder) throws Exception {
		ExecutableElement executableElement = (ExecutableElement) element;

		codeModelHelper.overrideAnnotatedMethod(executableElement, holder);
	}

}
