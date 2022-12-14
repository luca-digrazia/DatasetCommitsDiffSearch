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

import static com.sun.codemodel.JExpr._this;
import static com.sun.codemodel.JMod.FINAL;
import static com.sun.codemodel.JMod.PRIVATE;
import static com.sun.codemodel.JMod.PUBLIC;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import org.androidannotations.annotations.EIntentService;
import org.androidannotations.helper.APTCodeModelHelper;
import org.androidannotations.helper.ModelConstants;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;

public class EIntentServiceProcessor extends EServiceProcessor {

	private final APTCodeModelHelper aptCodeModelHelper;

	public EIntentServiceProcessor() {
		aptCodeModelHelper = new APTCodeModelHelper();
	}

	@Override
	public String getTarget() {
		return EIntentService.class.getName();
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeansHolder eBeansHolder) throws Exception {

		TypeElement typeElement = (TypeElement) element;

		String annotatedComponentQualifiedName = typeElement.getQualifiedName().toString();

		String generatedComponentQualifiedName = annotatedComponentQualifiedName + ModelConstants.GENERATION_SUFFIX;

		JDefinedClass generatedClass = codeModel._class(PUBLIC | FINAL, generatedComponentQualifiedName, ClassType.CLASS);

		EBeanHolder holder = eBeansHolder.create(element, EIntentService.class, generatedClass);

		JClass annotatedComponent = codeModel.directClass(annotatedComponentQualifiedName);

		holder.generatedClass._extends(annotatedComponent);

		holder.contextRef = _this();

		JMethod init = holder.generatedClass.method(PRIVATE, codeModel.VOID, "init_");
		holder.initBody = init.body();
		{
			// onCreate
			JMethod onCreate = holder.generatedClass.method(PUBLIC, codeModel.VOID, "onCreate");
			onCreate.annotate(Override.class);
			JBlock onCreateBody = onCreate.body();
			onCreateBody.invoke(init);
			onCreateBody.invoke(JExpr._super(), onCreate);
		}

		{
			// onHandleIntent
			JMethod onHandleIntent = holder.generatedClass.method(PUBLIC, codeModel.VOID, "onHandleIntent");
			JVar intent = onHandleIntent.param(eBeansHolder.classes().INTENT, "intent");
			onHandleIntent.annotate(Override.class);
			JBlock onHandleIntentBody = onHandleIntent.body();
			aptCodeModelHelper.callSuperMethod(onHandleIntent, holder, onHandleIntentBody);

			holder.onHandleIntentIntent = intent;
			holder.onHandleIntentBody = onHandleIntentBody;
		}

		{
			/*
			 * Setting to null shouldn't be a problem as long as we don't allow
			 * 
			 * @App and @Extra on this component
			 */
			holder.initIfActivityBody = null;
			holder.initActivityRef = null;
		}

		aptCodeModelHelper.addServiceIntentBuilder(codeModel, holder);

	}
}
