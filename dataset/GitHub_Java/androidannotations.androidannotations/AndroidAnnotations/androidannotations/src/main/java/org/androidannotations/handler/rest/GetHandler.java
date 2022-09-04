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
package org.androidannotations.handler.rest;

import com.sun.codemodel.*;
import org.androidannotations.annotations.rest.Get;
import org.androidannotations.helper.CanonicalNameConstants;
import org.androidannotations.holder.RestHolder;
import org.androidannotations.model.AnnotationElements;
import org.androidannotations.process.IsValid;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.TreeMap;

public class GetHandler extends RestMethodHandler {

	public GetHandler(ProcessingEnvironment processingEnvironment) {
		super(Get.class, processingEnvironment);
	}

	@Override
	public void validate(Element element, AnnotationElements validatedElements, IsValid valid) {
		super.validate(element, validatedElements, valid);

		validatorHelper.doesNotReturnPrimitive((ExecutableElement) element, valid);

		restAnnotationHelper.urlVariableNamesExistInParametersAndHasNoOneMoreParameter((ExecutableElement) element, valid);
	}

	@Override
	protected String getUrlSuffix(Element element) {
		Get annotation = element.getAnnotation(Get.class);
		return annotation.value();
	}

	@Override
	protected JExpression getRequestEntity(Element element, RestHolder holder, JBlock methodBody, TreeMap<String, JVar> methodParams) {
		ExecutableElement executableElement = (ExecutableElement) element;
		String mediaType = restAnnotationHelper.acceptedHeaders(executableElement);
		if (mediaType != null) {
			JClass httpEntity = holder.classes().HTTP_ENTITY;
			JInvocation newHttpEntityVarCall = JExpr._new(httpEntity.narrow(Object.class));
			JVar httpHeaders = restAnnotationHelper.declareAcceptedHttpHeaders(holder, methodBody, mediaType);
			newHttpEntityVarCall.arg(httpHeaders);
			return methodBody.decl(httpEntity.narrow(Object.class), "requestEntity", newHttpEntityVarCall);
		}
		return JExpr._null();
	}

	protected JExpression getResponseClass(Element element, RestHolder holder) {
		return restAnnotationHelper.getResponseClass(element, holder);
	}

	protected JInvocation addResultCallMethod(JInvocation exchangeCall, JClass methodReturnClass) {
		if (methodReturnClass != null && !methodReturnClass.fullName().startsWith(CanonicalNameConstants.RESPONSE_ENTITY)) {
			return JExpr.invoke(exchangeCall, "getBody");
		}
		return exchangeCall;
	}
}
