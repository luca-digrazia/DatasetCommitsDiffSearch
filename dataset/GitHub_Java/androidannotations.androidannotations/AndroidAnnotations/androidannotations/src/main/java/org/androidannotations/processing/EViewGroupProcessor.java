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

import static com.sun.codemodel.JExpr.invoke;
import static com.sun.codemodel.JMod.PRIVATE;
import static com.sun.codemodel.JMod.PUBLIC;

import java.lang.annotation.Annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import org.androidannotations.annotations.EViewGroup;
import org.androidannotations.helper.APTCodeModelHelper;
import org.androidannotations.helper.IdAnnotationHelper;
import org.androidannotations.helper.ModelConstants;
import org.androidannotations.processing.EBeansHolder.Classes;
import org.androidannotations.rclass.IRClass;
import org.androidannotations.rclass.IRClass.Res;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;

public class EViewGroupProcessor implements GeneratingElementProcessor {

	private static final String ALREADY_INFLATED_COMMENT = "" // +
			+ "The mAlreadyInflated_ hack is needed because of an Android bug\n" // +
			+ "which leads to infinite calls of onFinishInflate()\n" //
			+ "when inflating a layout with a parent and using\n" //
			+ "the <merge /> tag." //
	;

	private static final String SUPPRESS_WARNING_COMMENT = "" //
			+ "We use @SuppressWarning here because our java code\n" //
			+ "generator doesn't know that there is no need\n" //
			+ "to import OnXXXListeners from View as we already\n" //
			+ "are in a View." //
	;

	private final APTCodeModelHelper codeModelHelper;

	private final IdAnnotationHelper helper;

	public EViewGroupProcessor(ProcessingEnvironment processingEnv, IRClass rClass) {
		codeModelHelper = new APTCodeModelHelper();
		helper = new IdAnnotationHelper(processingEnv, getTarget(), rClass);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return EViewGroup.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeansHolder eBeansHolder) throws Exception {

		Classes classes = eBeansHolder.classes();

		TypeElement typeElement = (TypeElement) element;

		String eBeanQualifiedName = typeElement.getQualifiedName().toString();

		String generatedBeanQualifiedName = eBeanQualifiedName + ModelConstants.GENERATION_SUFFIX;

		int modifiers;
		if (element.getModifiers().contains(Modifier.ABSTRACT)) {
			modifiers = JMod.PUBLIC | JMod.ABSTRACT;
		} else {
			modifiers = JMod.PUBLIC | JMod.FINAL;
		}

		JDefinedClass generatedClass = codeModel._class(modifiers, generatedBeanQualifiedName, ClassType.CLASS);

		EBeanHolder holder = eBeansHolder.create(element, getTarget(), generatedClass);

		JClass eBeanClass = codeModel.directClass(eBeanQualifiedName);

		holder.generatedClass._extends(eBeanClass);

		holder.generatedClass.annotate(SuppressWarnings.class).param("value", "unused");
		holder.generatedClass.javadoc().append(SUPPRESS_WARNING_COMMENT);

		{
			holder.contextRef = holder.generatedClass.field(PRIVATE, classes.CONTEXT, "context_");
		}

		JMethod init;
		{
			// init
			init = holder.generatedClass.method(PRIVATE, codeModel.VOID, "init_");
			holder.initBody = init.body();
			holder.wrapInitWithNotifier();
			holder.initBody.assign((JFieldVar) holder.contextRef, JExpr.invoke("getContext"));
		}

		JFieldVar mAlreadyInflated_ = holder.generatedClass.field(PRIVATE, JType.parse(codeModel, "boolean"), "mAlreadyInflated_", JExpr.FALSE);

		// onFinishInflate
		JMethod onFinishInflate = holder.generatedClass.method(PUBLIC, codeModel.VOID, "onFinishInflate");
		onFinishInflate.annotate(Override.class);
		onFinishInflate.javadoc().append(ALREADY_INFLATED_COMMENT);

		JBlock ifNotInflated = onFinishInflate.body()._if(JExpr.ref("mAlreadyInflated_").not())._then();
		ifNotInflated.assign(mAlreadyInflated_, JExpr.TRUE);

		JFieldRef contentViewId = helper.extractOneAnnotationFieldRef(holder, element, Res.LAYOUT, false);

		if (contentViewId != null) {
			ifNotInflated.invoke("inflate").arg(invoke("getContext")).arg(contentViewId).arg(JExpr._this());
		}
		holder.invokeViewChanged(ifNotInflated);

		// finally
		onFinishInflate.body().invoke(JExpr._super(), "onFinishInflate");

		codeModelHelper.copyConstructorsAndAddStaticEViewBuilders(element, codeModel, eBeanClass, holder, onFinishInflate, init);

		{
			// init if activity
			APTCodeModelHelper helper = new APTCodeModelHelper();
			holder.initIfActivityBody = helper.ifContextInstanceOfActivity(holder, holder.initBody);
			holder.initActivityRef = helper.castContextToActivity(holder, holder.initIfActivityBody);
		}

	}

}
