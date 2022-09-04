/**
 * Copyright (C) 2010-2011 eBusiness Information, Excilys Group
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

import static com.sun.codemodel.JExpr.FALSE;
import static com.sun.codemodel.JExpr.TRUE;
import static com.sun.codemodel.JExpr._super;
import static com.sun.codemodel.JExpr.invoke;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.googlecode.androidannotations.annotations.OptionsItem;
import com.googlecode.androidannotations.helper.IdAnnotationHelper;
import com.googlecode.androidannotations.helper.SherlockHelper;
import com.googlecode.androidannotations.processing.EBeansHolder.Classes;
import com.googlecode.androidannotations.rclass.IRClass;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCase;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

/**
 * @author Pierre-Yves Ricau
 */
public class OptionsItemProcessor implements ElementProcessor {

	private final IdAnnotationHelper helper;

	private final SherlockHelper sherlockHelper;

	public OptionsItemProcessor(ProcessingEnvironment processingEnv, IRClass rClass) {
		helper = new IdAnnotationHelper(processingEnv, getTarget(), rClass);
		sherlockHelper = new SherlockHelper(helper);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return OptionsItem.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeansHolder activitiesHolder) {
		EBeanHolder holder = activitiesHolder.getEnclosingEBeanHolder(element);
		Classes classes = holder.classes();

		String methodName = element.getSimpleName().toString();

		JClass menuItemClass;
		if (sherlockHelper.usesSherlock(holder)) {
			menuItemClass = classes.SHERLOCK_MENU_ITEM;
		} else {
			menuItemClass = classes.MENU_ITEM;
		}

		ExecutableElement executableElement = (ExecutableElement) element;
		List<? extends VariableElement> parameters = executableElement.getParameters();
		TypeMirror returnType = executableElement.getReturnType();
		boolean returnMethodResult = returnType.getKind() != TypeKind.VOID;

		boolean hasItemParameter = parameters.size() == 1;

		OptionsItem annotation = element.getAnnotation(OptionsItem.class);
		List<JFieldRef> idsRefs = helper.extractFieldRefsFromAnnotationValues(element, annotation.value(), "Selected", holder);

		if (holder.onOptionsItemSelectedSwitch == null) {
			JMethod method = holder.eBean.method(JMod.PUBLIC, codeModel.BOOLEAN, "onOptionsItemSelected");
			method.annotate(Override.class);
			holder.onOptionsItemSelectedItem = method.param(menuItemClass, "item");

			JBlock body = method.body();
			JVar handled = body.decl(codeModel.BOOLEAN, "handled", invoke(_super(), method).arg(holder.onOptionsItemSelectedItem));

			body._if(handled)._then()._return(TRUE);

			holder.onOptionsItemSelectedSwitch = body._switch(holder.onOptionsItemSelectedItem.invoke("getItemId"));

			JBlock defaultBody = holder.onOptionsItemSelectedSwitch._default().body();
			defaultBody._return(FALSE);
		}

		JCase itemCase = null;
		for (JFieldRef idRef : idsRefs) {
			itemCase = holder.onOptionsItemSelectedSwitch._case(idRef);
		}

		JBlock itemCaseBody = itemCase.body();
		JInvocation methodCall = invoke(methodName);

		if (returnMethodResult) {
			itemCaseBody._return(methodCall);
		} else {
			itemCaseBody.add(methodCall);
			itemCaseBody._return(TRUE);
		}

		if (hasItemParameter) {
			methodCall.arg(holder.onOptionsItemSelectedItem);
		}

	}

}
