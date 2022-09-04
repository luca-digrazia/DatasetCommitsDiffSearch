/**
 * Copyright (C) 2010-2012 eBusiness Information, Excilys Group
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
package com.googlecode.androidannotations.processing.rest;

import java.lang.annotation.Annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.googlecode.androidannotations.annotations.rest.Post;
import com.googlecode.androidannotations.helper.CanonicalNameConstants;
import com.googlecode.androidannotations.processing.EBeanHolder;
import com.sun.codemodel.JClass;

public class PostProcessor extends GetPostProcessor {

	public PostProcessor(ProcessingEnvironment processingEnv, RestImplementationsHolder restImplementationHolder) {
		super(processingEnv, restImplementationHolder);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return Post.class;
	}

	@Override
	public void retrieveReturnClass(EBeanHolder holder, TypeMirror returnType, MethodProcessorHolder processorHolder) {
		String returnTypeString = returnType.toString();
		JClass expectedClass = null;
		JClass generatedReturnClass = null;

		if (returnTypeString.startsWith(CanonicalNameConstants.URI)) {
			DeclaredType declaredReturnType = (DeclaredType) returnType;
			TypeMirror typeParameter = declaredReturnType.getTypeArguments().get(0);
			expectedClass = holder.refClass(typeParameter.toString());
			generatedReturnClass = holder.refClass(CanonicalNameConstants.URI);

		} else if (returnTypeString.startsWith(CanonicalNameConstants.RESPONSE_ENTITY)) {
			DeclaredType declaredReturnType = (DeclaredType) returnType;
			TypeMirror typeParameter = declaredReturnType.getTypeArguments().get(0);
			expectedClass = holder.refClass(typeParameter.toString());
			generatedReturnClass = holder.refClass(CanonicalNameConstants.RESPONSE_ENTITY).narrow(expectedClass);

		} else if (returnType.getKind() == TypeKind.DECLARED) {
			DeclaredType declaredReturnType = (DeclaredType) returnType;
			TypeMirror enclosingType = declaredReturnType.getEnclosingType();
			if (enclosingType instanceof NoType) {
				expectedClass = holder.parseClass(declaredReturnType.toString());
			} else {
				expectedClass = holder.parseClass(enclosingType.toString());
			}
			generatedReturnClass = holder.parseClass(declaredReturnType.toString());

		} else {
			generatedReturnClass = holder.refClass(returnTypeString);
			expectedClass = holder.refClass(returnTypeString);
		}

		processorHolder.setExpectedClass(expectedClass);
		processorHolder.setGeneratedReturnType(generatedReturnClass);
	}

	@Override
	public String retrieveUrlSuffix(Element element) {
		Post getAnnotation = element.getAnnotation(Post.class);
		return getAnnotation.value();
	}

}
