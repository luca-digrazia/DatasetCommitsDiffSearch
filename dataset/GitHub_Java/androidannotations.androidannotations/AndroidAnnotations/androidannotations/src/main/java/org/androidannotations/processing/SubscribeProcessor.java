/**
 * Copyright (C) 2010-2013 eBusiness Information, Excilys Group
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
package org.androidannotations.processing;

import static org.androidannotations.helper.CanonicalNameConstants.SUBSCRIBE;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

import org.androidannotations.helper.APTCodeModelHelper;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JMethod;

public class SubscribeProcessor implements DecoratingElementProcessor {

	private final APTCodeModelHelper helper = new APTCodeModelHelper();

	@Override
	public String getTarget() {
		return SUBSCRIBE;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeanHolder holder) throws JClassAlreadyExistsException {

		ExecutableElement executableElement = (ExecutableElement) element;

		JMethod delegatingMethod = helper.overrideAnnotatedMethod(executableElement, holder);

		delegatingMethod.annotate(holder.refClass(SUBSCRIBE));
	}

}
