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

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;

import com.googlecode.androidannotations.annotations.Extra;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCatchBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JType;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;

public class ExtraProcessor implements ElementProcessor {

	@Override
	public Class<? extends Annotation> getTarget() {
		return Extra.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeansHolder activitiesHolder) {
		Extra annotation = element.getAnnotation(Extra.class);
		String extraKey = annotation.value();
		String fieldName = element.getSimpleName().toString();
		EBeanHolder holder = activitiesHolder.getEnclosingEBeanHolder(element);

		if (holder.cast == null) {
			JType objectType = codeModel._ref(Object.class);
			JMethod method = holder.eBean.method(JMod.PRIVATE, objectType, "cast_");
			JTypeVar genericType = method.generify("T");
			method.type(genericType);
			JVar objectParam = method.param(objectType, "object");
			method.annotate(SuppressWarnings.class).param("value", "unchecked");
			method.body()._return(JExpr.cast(genericType, objectParam));

			holder.cast = method;
		}

		if (holder.extras == null) {
			JClass bundleClass = holder.refClass("android.os.Bundle");
			holder.extras = holder.initIfActivityBody.decl(bundleClass, "extras_");
			holder.extras.init(holder.initActivityRef.invoke("getIntent").invoke("getExtras"));

			holder.extrasNotNullBlock = holder.initIfActivityBody._if(holder.extras.ne(JExpr._null()))._then();
		}

		JBlock ifContainsKey = holder.extrasNotNullBlock._if(JExpr.invoke(holder.extras, "containsKey").arg(extraKey))._then();

		JTryBlock containsKeyTry = ifContainsKey._try();

		JFieldRef extraField = JExpr.ref(fieldName);

		containsKeyTry.body().assign(extraField, JExpr.invoke(holder.cast).arg(holder.extras.invoke("get").arg(extraKey)));

		JCatchBlock containsKeyCatch = containsKeyTry._catch(holder.refClass(ClassCastException.class));
		JVar exceptionParam = containsKeyCatch.param("e");

		JInvocation errorInvoke = holder.refClass("android.util.Log").staticInvoke("e");

		errorInvoke.arg(holder.eBean.name());
		errorInvoke.arg("Could not cast extra to expected type, the field is left to its default value");
		errorInvoke.arg(exceptionParam);

		containsKeyCatch.body().add(errorInvoke);
	}

}
