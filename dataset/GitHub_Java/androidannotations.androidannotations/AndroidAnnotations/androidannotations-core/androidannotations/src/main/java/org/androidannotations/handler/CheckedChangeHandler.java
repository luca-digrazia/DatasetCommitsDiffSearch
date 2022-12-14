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
package org.androidannotations.handler;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.annotations.CheckedChange;
import org.androidannotations.helper.CanonicalNameConstants;
import org.androidannotations.holder.EComponentWithViewSupportHolder;
import org.androidannotations.process.ElementValidation;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

public class CheckedChangeHandler extends AbstractViewListenerHandler {

	public CheckedChangeHandler(AndroidAnnotationsEnvironment environment) {
		super(CheckedChange.class, environment);
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		super.validate(element, validation);

		ExecutableElement executableElement = (ExecutableElement) element;
		validatorHelper.returnTypeIsVoid(executableElement, validation);

		validatorHelper.param.anyOrder() //
				.extendsType(CanonicalNameConstants.COMPOUND_BUTTON).optional() //
				.primitiveOrWrapper(TypeKind.BOOLEAN).optional() //
				.validate(executableElement, validation);
	}

	@Override
	protected void makeCall(JBlock listenerMethodBody, JInvocation call, TypeMirror returnType) {
		listenerMethodBody.add(call);
	}

	@Override
	protected void processParameters(EComponentWithViewSupportHolder holder, JMethod listenerMethod, JInvocation call, List<? extends VariableElement> parameters) {
		JVar btnParam = listenerMethod.param(getClasses().COMPOUND_BUTTON, "buttonView");
		JVar isCheckedParam = listenerMethod.param(codeModel().BOOLEAN, "isChecked");

		for (VariableElement parameter : parameters) {
			String parameterType = parameter.asType().toString();
			if (isTypeOrSubclass(CanonicalNameConstants.COMPOUND_BUTTON, parameter)) {
				call.arg(castArgumentIfNecessary(holder, CanonicalNameConstants.COMPOUND_BUTTON, btnParam, parameter));
			} else if (parameterType.equals(CanonicalNameConstants.BOOLEAN) || parameter.asType().getKind() == TypeKind.BOOLEAN) {
				call.arg(isCheckedParam);
			}
		}
	}

	@Override
	protected JMethod createListenerMethod(JDefinedClass listenerAnonymousClass) {
		return listenerAnonymousClass.method(JMod.PUBLIC, codeModel().VOID, "onCheckedChanged");
	}

	@Override
	protected String getSetterName() {
		return "setOnCheckedChangeListener";
	}

	@Override
	protected JClass getListenerClass() {
		return getClasses().COMPOUND_BUTTON_ON_CHECKED_CHANGE_LISTENER;
	}

	@Override
	protected JClass getListenerTargetClass() {
		return getClasses().COMPOUND_BUTTON;
	}
}
