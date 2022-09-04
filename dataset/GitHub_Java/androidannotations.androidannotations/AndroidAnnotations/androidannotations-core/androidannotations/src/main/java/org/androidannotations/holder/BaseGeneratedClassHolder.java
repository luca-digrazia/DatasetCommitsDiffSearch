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
package org.androidannotations.holder;

import static com.sun.codemodel.JMod.FINAL;
import static com.sun.codemodel.JMod.PUBLIC;
import static com.sun.codemodel.JMod.STATIC;
import static org.androidannotations.helper.ModelConstants.classSuffix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.helper.APTCodeModelHelper;
import org.androidannotations.process.ProcessHolder;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JTypeVar;

public abstract class BaseGeneratedClassHolder implements GeneratedClassHolder {

	protected final AndroidAnnotationsEnvironment environment;
	protected JDefinedClass generatedClass;
	protected JClass annotatedClass;
	protected final TypeElement annotatedElement;
	protected final APTCodeModelHelper codeModelHelper;

	private Map<Class<?>, Object> pluginHolders = new HashMap<>();

	public BaseGeneratedClassHolder(AndroidAnnotationsEnvironment environment, TypeElement annotatedElement) throws Exception {
		this.environment = environment;
		this.annotatedElement = annotatedElement;
		codeModelHelper = new APTCodeModelHelper(environment);
		setGeneratedClass();
	}

	protected void setGeneratedClass() throws Exception {
		String annotatedComponentQualifiedName = annotatedElement.getQualifiedName().toString();
		annotatedClass = getCodeModel().directClass(annotatedElement.asType().toString());

		if (annotatedElement.getNestingKind().isNested()) {
			Element enclosingElement = annotatedElement.getEnclosingElement();
			GeneratedClassHolder enclosingHolder = processHolder().getGeneratedClassHolder(enclosingElement);
			String generatedBeanSimpleName = annotatedElement.getSimpleName().toString() + classSuffix();
			generatedClass = enclosingHolder.getGeneratedClass()._class(PUBLIC | FINAL | STATIC, generatedBeanSimpleName, ClassType.CLASS);
		} else {
			String generatedClassQualifiedName = annotatedComponentQualifiedName + classSuffix();
			generatedClass = getCodeModel()._class(PUBLIC | FINAL, generatedClassQualifiedName, ClassType.CLASS);
		}
		for (TypeParameterElement typeParam : annotatedElement.getTypeParameters()) {
			JClass bound = codeModelHelper.typeBoundsToJClass(typeParam.getBounds());
			generatedClass.generify(typeParam.getSimpleName().toString(), bound);
		}
		setExtends();
		codeModelHelper.copyNonAAAnnotations(generatedClass, annotatedElement.getAnnotationMirrors());
	}

	public JClass getAnnotatedClass() {
		return annotatedClass;
	}

	protected void setExtends() {
		JClass annotatedComponent = getCodeModel().directClass(annotatedElement.asType().toString());
		generatedClass._extends(annotatedComponent);
	}

	@Override
	public JDefinedClass getGeneratedClass() {
		return generatedClass;
	}

	@Override
	public TypeElement getAnnotatedElement() {
		return annotatedElement;
	}

	public AndroidAnnotationsEnvironment getEnvironment() {
		return environment;
	}

	public ProcessHolder processHolder() {
		return environment.getProcessHolder();
	}

	protected ProcessHolder.Classes getClasses() {
		return environment.getClasses();
	}

	protected JCodeModel getCodeModel() {
		return environment().getCodeModel();
	}

	protected JClass getJClass(String fullyQualifiedClassName) {
		return environment().getJClass(fullyQualifiedClassName);
	}

	protected JClass getJClass(Class<?> clazz) {
		return environment().getJClass(clazz);
	}

	@Override
	public AndroidAnnotationsEnvironment environment() {
		return environment;
	}

	public JClass narrow(JClass toNarrow) {
		List<JClass> classes = new ArrayList<>();
		for (JTypeVar type : generatedClass.typeParams()) {
			classes.add(getCodeModel().directClass(type.name()));
		}
		if (classes.isEmpty()) {
			return toNarrow;
		}
		return toNarrow.narrow(classes);
	}

	@SuppressWarnings("unchecked")
	public <T> T getPluginHolder(T pluginHolder) {
		T currentPluginHolder = (T) pluginHolders.get(pluginHolder.getClass());
		if (currentPluginHolder == null) {
			currentPluginHolder = pluginHolder;
			pluginHolders.put(pluginHolder.getClass(), pluginHolder);
		}
		return currentPluginHolder;
	}
}
