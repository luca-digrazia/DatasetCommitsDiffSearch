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
import org.androidannotations.annotations.Touch;
import org.androidannotations.helper.CanonicalNameConstants;
import org.androidannotations.holder.EComponentWithViewSupportHolder;
import org.androidannotations.process.ElementValidation;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

public class TouchHandler extends AbstractViewListenerHandler {

	public TouchHandler(AndroidAnnotationsEnvironment environment) {
		super(Touch.class, environment);
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		super.validate(element, validation);

		ExecutableElement executableElement = (ExecutableElement) element;

		validatorHelper.returnTypeIsVoidOrBoolean(executableElement, validation);

		validatorHelper.param.anyOrder() //
				.type(CanonicalNameConstants.MOTION_EVENT).optional() //
				.extendsType(CanonicalNameConstants.VIEW).optional() //
				.validate(executableElement, validation);
	}

	@Override
	protected void makeCall(JBlock listenerMethodBody, JInvocation call, TypeMirror returnType) {
		boolean returnMethodResult = returnType.getKind() != TypeKind.VOID;

		if (returnMethodResult) {
			listenerMethodBody._return(call);
		} else {
			listenerMethodBody.add(call);
			listenerMethodBody._return(JExpr.TRUE);
		}
	}

	@Override
	protected void processParameters(EComponentWithViewSupportHolder holder, JMethod listenerMethod, JInvocation call, List<? extends VariableElement> parameters) {
		JVar viewParam = listenerMethod.param(getClasses().VIEW, "view");
		JVar eventParam = listenerMethod.param(getClasses().MOTION_EVENT, "event");

		for (VariableElement parameter : parameters) {
			String parameterType = parameter.asType().toString();

			if (parameterType.equals(CanonicalNameConstants.MOTION_EVENT)) {
				call.arg(eventParam);
			} else if (isTypeOrSubclass(CanonicalNameConstants.VIEW, parameter)) {
				call.arg(castArgumentIfNecessary(holder, CanonicalNameConstants.VIEW, viewParam, parameter));
			}
		}
	}

	@Override
	protected JMethod createListenerMethod(JDefinedClass listenerAnonymousClass) {
		return listenerAnonymousClass.method(JMod.PUBLIC, codeModel().BOOLEAN, "onTouch");
	}

	@Override
	protected String getSetterName() {
		return "setOnTouchListener";
	}

	@Override
	protected JClass getListenerClass() {
		return getClasses().VIEW_ON_TOUCH_LISTENER;
	}
}
