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
package com.googlecode.androidannotations.validation;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import com.googlecode.androidannotations.annotations.TrackingTouchStart;
import com.googlecode.androidannotations.helper.IdAnnotationHelper;
import com.googlecode.androidannotations.helper.IdValidatorHelper;
import com.googlecode.androidannotations.helper.IdValidatorHelper.FallbackStrategy;
import com.googlecode.androidannotations.model.AnnotationElements;
import com.googlecode.androidannotations.rclass.IRClass;
import com.googlecode.androidannotations.rclass.IRClass.Res;

public class TrackingTouchStartValidator implements ElementValidator {

	private final IdValidatorHelper validatorHelper;

	private final IdAnnotationHelper annotationHelper;

	public TrackingTouchStartValidator(ProcessingEnvironment processingEnv, IRClass rClass) {
		annotationHelper = new IdAnnotationHelper(processingEnv, getTarget(), rClass);
		validatorHelper = new IdValidatorHelper(annotationHelper);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return TrackingTouchStart.class;
	}

	@Override
	public boolean validate(Element element, AnnotationElements validatedElements) {

		IsValid valid = new IsValid();

		validatorHelper.enclosingElementHasEnhancedViewSupportAnnotation(element, validatedElements, valid);

		validatorHelper.resIdsExist(element, Res.ID, FallbackStrategy.USE_ELEMENT_NAME, valid);

		validatorHelper.isNotPrivate(element, valid);

		validatorHelper.doesntThrowException(element, valid);

		ExecutableElement executableElement = (ExecutableElement) element;
		validatorHelper.returnTypeIsVoid(executableElement, valid);

		haveProgressChangeMethodParameters(executableElement, valid);

		return valid.isValid();
	}

	private void haveProgressChangeMethodParameters(ExecutableElement executableElement, IsValid valid) {
		List<? extends VariableElement> parameters = executableElement.getParameters();

		if (parameters.size() > 1) {
			annotationHelper.printAnnotationError(executableElement, "Unrecognized parameter declaration. You can only have one parameter of type SeekBar. Try by declaring " + executableElement.getSimpleName() + "(SeekBar seekBar);");
			valid.invalidate();
			return;
		}

		if (parameters.size() == 1) {
			String parameterType = parameters.get(0).asType().toString();
			if (!parameterType.equals("android.widget.SeekBar")) {
				annotationHelper.printAnnotationError(executableElement, "Unrecognized param	eter declaration. You can only have one parameter of type SeekBar. Try by declaring " + executableElement.getSimpleName() + "(SeekBar seekBar);");
				valid.invalidate();
			}
		}

	}

}
