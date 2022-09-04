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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import org.apache.http.HttpEntity;

import com.googlecode.androidannotations.annotations.rest.Accept;
import com.googlecode.androidannotations.helper.CanonicalNameConstants;
import com.googlecode.androidannotations.helper.RestAnnotationHelper;
import com.googlecode.androidannotations.processing.DecoratingElementProcessor;
import com.googlecode.androidannotations.processing.EBeanHolder;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public abstract class MethodProcessor implements DecoratingElementProcessor {

	protected final RestImplementationsHolder restImplementationsHolder;
	protected final RestAnnotationHelper restAnnotationHelper;

	public MethodProcessor(ProcessingEnvironment processingEnv, RestImplementationsHolder restImplementationsHolder) {
		this.restImplementationsHolder = restImplementationsHolder;
		restAnnotationHelper = new RestAnnotationHelper(processingEnv, getTarget());
	}

	protected void generateRestTemplateCallBlock(MethodProcessorHolder methodHolder) {
		RestImplementationHolder holder = restImplementationsHolder.getEnclosingHolder(methodHolder.getElement());
		ExecutableElement executableElement = (ExecutableElement) methodHolder.getElement();
		EBeanHolder eBeanHolder = methodHolder.getHolder();
		JClass expectedClass = methodHolder.getExpectedClass();
		JClass generatedReturnType = methodHolder.getGeneratedReturnType();

		// Creating method signature
		JMethod method;
		String methodName = executableElement.getSimpleName().toString();
		boolean methodReturnVoid = generatedReturnType == null && expectedClass == null;
		if (methodReturnVoid) {
			method = holder.restImplementationClass.method(JMod.PUBLIC, void.class, methodName);
		} else {
			method = holder.restImplementationClass.method(JMod.PUBLIC, methodHolder.getGeneratedReturnType(), methodName);
		}
		method.annotate(Override.class);
		if (expectedClass != generatedReturnType && !generatedReturnType.fullName().startsWith(CanonicalNameConstants.RESPONSE_ENTITY)) {
			method.annotate(SuppressWarnings.class).param("value", "unchecked");
		}

		// Keep a reference on method's body
		JBlock body = method.body();
		methodHolder.setBody(body);

		// Keep a reference on method's parameters
		TreeMap<String, JVar> methodParams = extractMethodParamsVar(eBeanHolder, method, executableElement, holder);
		methodHolder.setMethodParams(methodParams);

		// RestTemplate exchange() method call
		JInvocation restCall = JExpr.invoke(holder.restTemplateField, "exchange");

		// RestTemplate exchange() 1st arg : concat root url + suffix
		JInvocation concatCall = JExpr.invoke(holder.rootUrlField, "concat");

		// RestTemplate exchange() 2nd arg : add url param
		restCall.arg(concatCall.arg(JExpr.lit(methodHolder.getUrlSuffix())));

		// RestTemplate exchange() 3rd arg : add HttpMethod type param
		JClass httpMethod = eBeanHolder.refClass(CanonicalNameConstants.HTTP_METHOD);
		String restMethodInCapitalLetters = getTarget().getSimpleName().toUpperCase();
		restCall.arg(httpMethod.staticRef(restMethodInCapitalLetters));

		restCall = addHttpEntityVar(restCall, methodHolder);
		restCall = addResponseEntityArg(restCall, methodHolder);

		JVar hashMapVar = generateHashMapVar(methodHolder);
		if (hashMapVar != null) {
			restCall.arg(hashMapVar);
		}

		insertRestCallInBody(body, restCall, methodHolder, methodReturnVoid);
	}

	/**
	 * Add the {@link HttpEntity} attribute to restTemplate.exchange() method.
	 * By default, the value will be <code>null</code> (for DELETE, HEAD and
	 * OPTIONS method type)
	 */
	protected JInvocation addHttpEntityVar(JInvocation restCall, MethodProcessorHolder methodHolder) {
		return restCall.arg(JExpr._null());
	}

	/**
	 * Add the response type to restTemplate.exchange() method. This is used to
	 * bind the response into a specific Java object.
	 */
	protected JInvocation addResponseEntityArg(JInvocation restCall, MethodProcessorHolder methodHolder) {
		return restCall.arg(JExpr._null());
	}

	/**
	 * Add an extra method calls on the result of restTemplate.exchange(). By
	 * default, just return the result
	 */
	protected JInvocation addResultCallMethod(JInvocation restCall, MethodProcessorHolder methodHolder) {
		return restCall;
	}

	private void insertRestCallInBody(JBlock body, JInvocation restCall, MethodProcessorHolder methodHolder, boolean methodReturnVoid) {
		if (methodReturnVoid) {
			body.add(restCall);
		} else {
			restCall = addResultCallMethod(restCall, methodHolder);
			body._return(restCall);
		}
	}

	private JVar generateHashMapVar(MethodProcessorHolder methodHolder) {
		ExecutableElement element = (ExecutableElement) methodHolder.getElement();
		JCodeModel codeModel = methodHolder.getCodeModel();
		JBlock body = methodHolder.getBody();
		Map<String, JVar> methodParams = methodHolder.getMethodParams();
		JVar hashMapVar = null;

		List<String> urlVariables = restAnnotationHelper.extractUrlVariableNames(element);
		JClass hashMapClass = codeModel.ref(HashMap.class).narrow(String.class, Object.class);
		if (!urlVariables.isEmpty()) {
			hashMapVar = body.decl(hashMapClass, "urlVariables", JExpr._new(hashMapClass));

			for (String urlVariable : urlVariables) {
				body.invoke(hashMapVar, "put").arg(urlVariable).arg(methodParams.get(urlVariable));
				methodParams.remove(urlVariable);
			}
		}
		return hashMapVar;
	}

	protected JVar generateHttpEntityVar(MethodProcessorHolder methodHolder) {
		ExecutableElement executableElement = (ExecutableElement) methodHolder.getElement();
		EBeanHolder holder = methodHolder.getHolder();
		JClass httpEntity = holder.refClass(CanonicalNameConstants.HTTP_ENTITY);
		JInvocation newHttpEntityVarCall;

		TreeMap<String, JVar> methodParams = methodHolder.getMethodParams();
		JVar entitySentToServer = null;
		JType entityType = null;
		if (!methodParams.isEmpty()) {
			entitySentToServer = methodParams.firstEntry().getValue();
			entityType = entitySentToServer.type();
		}

		if (entitySentToServer != null) {
			newHttpEntityVarCall = JExpr._new(httpEntity.narrow(entityType));
		} else {
			newHttpEntityVarCall = JExpr._new(httpEntity.narrow(Object.class));
		}

		JBlock body = methodHolder.getBody();
		JVar httpHeadersVar = generateHttpHeadersVar(holder, body, executableElement);

		boolean hasHeaders = httpHeadersVar != null;

		if (entitySentToServer != null) {
			newHttpEntityVarCall.arg(entitySentToServer);
		}

		if (hasHeaders) {
			newHttpEntityVarCall.arg(httpHeadersVar);
		} else if (methodParams.isEmpty()) {
			newHttpEntityVarCall.arg(JExpr._null());
		}

		JVar httpEntityVar;
		String httpEntityVarName = "requestEntity";
		if (entitySentToServer != null) {
			httpEntityVar = body.decl(httpEntity.narrow(entityType), httpEntityVarName, newHttpEntityVarCall);
		} else {
			httpEntityVar = body.decl(httpEntity.narrow(Object.class), httpEntityVarName, newHttpEntityVarCall);
		}

		return httpEntityVar;
	}

	protected JVar generateHttpHeadersVar(EBeanHolder holder, JBlock body, ExecutableElement executableElement) {
		JVar httpHeadersVar = null;

		JClass httpHeadersClass = holder.refClass(CanonicalNameConstants.HTTP_HEADERS);
		httpHeadersVar = body.decl(httpHeadersClass, "httpHeaders", JExpr._new(httpHeadersClass));

		String mediaType = retrieveAcceptAnnotationValue(executableElement);
		boolean hasMediaTypeDefined = mediaType != null;
		if (hasMediaTypeDefined) {
			JClass collectionsClass = holder.refClass(CanonicalNameConstants.COLLECTIONS);
			JClass mediaTypeClass = holder.refClass(CanonicalNameConstants.MEDIA_TYPE);

			JInvocation mediaTypeListParam = collectionsClass.staticInvoke("singletonList").arg(mediaTypeClass.staticInvoke("parseMediaType").arg(mediaType));
			body.add(JExpr.invoke(httpHeadersVar, "setAccept").arg(mediaTypeListParam));
		}

		return httpHeadersVar;
	}

	private String retrieveAcceptAnnotationValue(ExecutableElement executableElement) {
		Accept acceptAnnotation = executableElement.getAnnotation(Accept.class);
		if (acceptAnnotation == null) {
			acceptAnnotation = executableElement.getEnclosingElement().getAnnotation(Accept.class);
		}
		if (acceptAnnotation != null) {
			return acceptAnnotation.value();
		} else {
			return null;
		}
	}

	private TreeMap<String, JVar> extractMethodParamsVar(EBeanHolder eBeanHolder, JMethod method, ExecutableElement executableElement, RestImplementationHolder holder) {
		List<? extends VariableElement> params = executableElement.getParameters();
		TreeMap<String, JVar> methodParams = new TreeMap<String, JVar>();
		for (VariableElement parameter : params) {
			String paramName = parameter.getSimpleName().toString();
			String paramType = parameter.asType().toString();

			// TODO check in validator that params are not generic. Or create a
			// helper to fix that case and generate the right code.
			JVar param = method.param(eBeanHolder.refClass(paramType), paramName);
			methodParams.put(paramName, param);
		}

		return methodParams;
	}

}
