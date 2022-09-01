/**
 * Copyright (C) 2010-2011 Pierre-Yves Ricau (py.ricau at gmail.com)
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
package com.googlecode.androidannotations.processing;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JInvocation;

public class AfterViewsProcessor implements ElementProcessor {

	@Override
	public Class<? extends Annotation> getTarget() {
		return AfterViews.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, ActivitiesHolder activitiesHolder) {
		
		ActivityHolder holder = activitiesHolder.getEnclosingActivityHolder(element);
		
		String methodName = element.getSimpleName().toString();
		
		ExecutableElement executableElement = (ExecutableElement) element;
		List<? extends VariableElement> parameters = executableElement.getParameters();
		boolean hasBundleParameter = parameters.size() == 1;

		JInvocation methodCall = holder.afterSetContentView.body().invoke(methodName);
		
		if (hasBundleParameter) {
			methodCall.arg(holder.afterSetContentViewSavedInstanceStateParam);
		}
	}

}
