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
package org.androidannotations.internal.core.handler;

import javax.lang.model.element.Element;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.annotations.WindowFeature;
import org.androidannotations.handler.BaseAnnotationHandler;
import org.androidannotations.holder.EActivityHolder;

import com.sun.codemodel.JExpr;

public class WindowFeatureHandler extends BaseAnnotationHandler<EActivityHolder> {

	public WindowFeatureHandler(AndroidAnnotationsEnvironment environment) {
		super(WindowFeature.class, environment);
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		validatorHelper.hasEActivity(element, validation);
	}

	@Override
	public void process(Element element, EActivityHolder holder) throws Exception {
		WindowFeature annotation = element.getAnnotation(WindowFeature.class);
		int[] features = annotation.value();

		for (int feature : features) {
			holder.getInitBody().invoke("requestWindowFeature").arg(JExpr.lit(feature));
		}
	}
}
