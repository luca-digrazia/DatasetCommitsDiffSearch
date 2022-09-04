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
package org.androidannotations.rest.spring.handler;

import java.util.Collections;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.handler.AnnotationHandler;
import org.androidannotations.handler.BaseAnnotationHandler;
import org.androidannotations.handler.HasParameterHandlers;
import org.androidannotations.holder.GeneratedClassHolder;
import org.androidannotations.rest.spring.annotations.Field;
import org.androidannotations.rest.spring.annotations.Post;
import org.androidannotations.rest.spring.holder.RestHolder;

public class PostHandler extends RestMethodHandler implements HasParameterHandlers<RestHolder> {

	private FieldHandler fieldHandler;

	public PostHandler(AndroidAnnotationsEnvironment environment) {
		super(Post.class, environment);
		fieldHandler = new FieldHandler(environment);
	}

	@Override
	public Iterable<AnnotationHandler> getParameterHandlers() {
		return Collections.<AnnotationHandler> singleton(fieldHandler);
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		super.validate(element, validation);

		validatorHelper.doesNotReturnPrimitive((ExecutableElement) element, validation);

		restSpringValidatorHelper.urlVariableNamesExistInParametersAndHasOnlyOneMoreParameter((ExecutableElement) element, validation);
	}

	@Override
	protected String getUrlSuffix(Element element) {
		Post annotation = element.getAnnotation(Post.class);
		return annotation.value();
	}

	public class FieldHandler extends BaseAnnotationHandler<GeneratedClassHolder> {

		public FieldHandler(AndroidAnnotationsEnvironment environment) {
			super(Field.class, environment);
		}

		@Override
		protected void validate(Element element, ElementValidation validation) {
			validatorHelper.enclosingElementHasAnnotation(Post.class, element, validation);
		}

		@Override
		public void process(Element element, GeneratedClassHolder holder) throws Exception {
			// Don't do anything here.
		}
	}
}
