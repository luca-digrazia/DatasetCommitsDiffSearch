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

import static com.sun.codemodel.JExpr.cast;
import static com.sun.codemodel.JExpr.invoke;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.holder.EComponentWithViewSupportHolder;
import org.androidannotations.process.ElementValidation;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

public class ItemClickHandler extends AbstractViewListenerHandler {

	public ItemClickHandler(AndroidAnnotationsEnvironment environment) {
		super(ItemClick.class, environment);
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		super.validate(element, validation);

		ExecutableElement executableElement = (ExecutableElement) element;

		validatorHelper.returnTypeIsVoid(executableElement, validation);

		validatorHelper.param.anyType().optional().validate(executableElement, validation);
	}

	@Override
	protected void makeCall(JBlock listenerMethodBody, JInvocation call, TypeMirror returnType) {
		listenerMethodBody.add(call);
	}

	@Override
	protected void processParameters(EComponentWithViewSupportHolder holder, JMethod listenerMethod, JInvocation call, List<? extends VariableElement> parameters) {
		boolean hasItemParameter = parameters.size() == 1;

		JClass narrowAdapterViewClass = getClasses().ADAPTER_VIEW.narrow(codeModel().wildcard());
		JVar onItemClickParentParam = listenerMethod.param(narrowAdapterViewClass, "parent");
		listenerMethod.param(getClasses().VIEW, "view");
		JVar onItemClickPositionParam = listenerMethod.param(codeModel().INT, "position");
		listenerMethod.param(codeModel().LONG, "id");

		if (hasItemParameter) {
			VariableElement parameter = parameters.get(0);

			TypeMirror parameterType = parameter.asType();
			if (parameterType.getKind() == TypeKind.INT) {
				call.arg(onItemClickPositionParam);
			} else {
				JClass parameterClass = codeModelHelper.typeMirrorToJClass(parameterType);
				call.arg(cast(parameterClass, invoke(onItemClickParentParam, "getAdapter").invoke("getItem").arg(onItemClickPositionParam)));

				if (parameterClass.isParameterized()) {
					codeModelHelper.addSuppressWarnings(listenerMethod, "unchecked");
				}
			}
		}
	}

	@Override
	protected JMethod createListenerMethod(JDefinedClass listenerAnonymousClass) {
		return listenerAnonymousClass.method(JMod.PUBLIC, codeModel().VOID, "onItemClick");
	}

	@Override
	protected String getSetterName() {
		return "setOnItemClickListener";
	}

	@Override
	protected JClass getListenerClass() {
		return getClasses().ON_ITEM_CLICK_LISTENER;
	}

	@Override
	protected JClass getListenerTargetClass() {
		return getClasses().ADAPTER_VIEW.narrow(codeModel().wildcard());
	}
}
