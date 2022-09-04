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
package org.androidannotations.rest.spring.handler;

import static org.androidannotations.rest.spring.helper.RestSpringClasses.HTTP_METHOD;
import static org.androidannotations.rest.spring.helper.RestSpringClasses.NESTED_RUNTIME_EXCEPTION;
import static org.androidannotations.rest.spring.helper.RestSpringClasses.RESPONSE_ENTITY;

import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.handler.BaseAnnotationHandler;
import org.androidannotations.process.ElementValidation;
import org.androidannotations.rest.spring.helper.RestAnnotationHelper;
import org.androidannotations.rest.spring.helper.RestSpringValidatorHelper;
import org.androidannotations.rest.spring.holder.RestHolder;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCatchBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public abstract class RestMethodHandler extends BaseAnnotationHandler<RestHolder> {

	protected final RestAnnotationHelper restAnnotationHelper;
	protected final RestSpringValidatorHelper restSpringValidatorHelper;

	public RestMethodHandler(Class<?> targetClass, AndroidAnnotationsEnvironment environment) {
		super(targetClass, environment);
		restAnnotationHelper = new RestAnnotationHelper(environment, getTarget());
		restSpringValidatorHelper = new RestSpringValidatorHelper(environment, getTarget());
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		validatorHelper.notAlreadyValidated(element, validation);

		restSpringValidatorHelper.enclosingElementHasRestAnnotation(element, validation);

		restSpringValidatorHelper.throwsOnlyRestClientException((ExecutableElement) element, validation);
	}

	@Override
	public void process(Element element, RestHolder holder) {
		ExecutableElement executableElement = (ExecutableElement) element;
		String methodName = element.getSimpleName().toString();
		JClass methodReturnClass = getMethodReturnClass(element, holder);
		boolean methodReturnVoid = executableElement.getReturnType().getKind() == TypeKind.VOID;

		// Creating method signature
		JMethod method = holder.getGeneratedClass().method(JMod.PUBLIC, methodReturnClass, methodName);
		method.annotate(Override.class);
		SortedMap<String, JVar> params = addMethodParams(executableElement, holder, method);
		JBlock methodBody = new JBlock(false, false);

		// RestTemplate exchange() method call
		JInvocation exchangeCall = JExpr.invoke(holder.getRestTemplateField(), "exchange");
		exchangeCall.arg(getUrl(element, holder));
		exchangeCall.arg(getHttpMethod());
		exchangeCall.arg(getRequestEntity(executableElement, holder, methodBody, params));
		exchangeCall.arg(getResponseClass(element, holder));
		JExpression urlVariables = getUrlVariables(element, holder, methodBody, params);
		if (urlVariables != null) {
			exchangeCall.arg(urlVariables);
		}

		JExpression response = setCookies(executableElement, holder, methodBody, exchangeCall);
		if (methodReturnVoid && response.equals(exchangeCall)) {
			methodBody.add(exchangeCall);
		} else if (!methodReturnVoid) {
			methodBody._return(addResultCallMethod(response, methodReturnClass));
		}
		methodBody = surroundWithRestTryCatch(holder, methodBody, methodReturnVoid);
		codeModelHelper.copy(methodBody, method.body());
	}

	protected JClass getMethodReturnClass(Element element, RestHolder holder) {
		ExecutableElement executableElement = (ExecutableElement) element;
		return codeModelHelper.typeMirrorToJClass(executableElement.getReturnType());
	}

	protected SortedMap<String, JVar> addMethodParams(ExecutableElement executableElement, RestHolder restHolder, JMethod method) {
		List<? extends VariableElement> params = executableElement.getParameters();
		SortedMap<String, JVar> methodParams = new TreeMap<>();
		for (VariableElement parameter : params) {
			String paramName = parameter.getSimpleName().toString();
			String paramType = parameter.asType().toString();

			JVar param;
			if (parameter.asType().getKind().isPrimitive()) {
				param = method.param(JType.parse(getCodeModel(), paramType), paramName);
			} else {
				JClass parameterClass = codeModelHelper.typeMirrorToJClass(parameter.asType());
				param = method.param(parameterClass, paramName);
			}
			methodParams.put(paramName, param);
		}
		return methodParams;
	}

	protected JExpression getUrl(Element element, RestHolder restHolder) {
		String urlSuffix = getUrlSuffix(element);
		JExpression url = JExpr.lit(getUrlSuffix(element));
		if (!(urlSuffix.startsWith("http://") || urlSuffix.startsWith("https://"))) {
			url = JExpr.invoke(restHolder.getRootUrlField(), "concat").arg(url);
		}
		return url;
	}

	protected abstract String getUrlSuffix(Element element);

	protected JExpression getHttpMethod() {
		JClass httpMethod = getJClass(HTTP_METHOD);
		String simpleName = getTarget().substring(getTarget().lastIndexOf('.') + 1);
		String restMethodInCapitalLetters = simpleName.toUpperCase(Locale.ENGLISH);
		return httpMethod.staticRef(restMethodInCapitalLetters);
	}

	protected JExpression getRequestEntity(ExecutableElement element, RestHolder holder, JBlock methodBody, SortedMap<String, JVar> params) {
		JVar httpHeaders = restAnnotationHelper.declareHttpHeaders(element, holder, methodBody);
		JVar entitySentToServer = restAnnotationHelper.getEntitySentToServer(element, params);
		return restAnnotationHelper.declareHttpEntity(methodBody, entitySentToServer, httpHeaders);
	}

	protected JExpression getResponseClass(Element element, RestHolder holder) {
		return restAnnotationHelper.getResponseClass(element, holder);
	}

	protected JExpression getUrlVariables(Element element, RestHolder holder, JBlock methodBody, SortedMap<String, JVar> params) {
		return restAnnotationHelper.declareUrlVariables((ExecutableElement) element, holder, methodBody, params);
	}

	protected JExpression addResultCallMethod(JExpression exchangeCall, JClass methodReturnClass) {
		if (methodReturnClass != null && !methodReturnClass.fullName().startsWith(RESPONSE_ENTITY)) {
			return JExpr.invoke(exchangeCall, "getBody");
		}
		return exchangeCall;
	}

	private JExpression setCookies(ExecutableElement executableElement, RestHolder restHolder, JBlock methodBody, JInvocation exchangeCall) {
		String[] settingCookies = restAnnotationHelper.settingCookies(executableElement);
		if (settingCookies != null) {
			boolean methodReturnVoid = executableElement.getReturnType().getKind() == TypeKind.VOID;

			JClass exchangeResponseClass = restAnnotationHelper.retrieveResponseClass(executableElement.getReturnType(), restHolder);
			JType narrowType = exchangeResponseClass == null || methodReturnVoid ? getCodeModel().VOID : exchangeResponseClass;
			JClass responseEntityClass = getJClass(RESPONSE_ENTITY).narrow(narrowType);
			JVar responseEntity = methodBody.decl(responseEntityClass, "response", exchangeCall);

			// set cookies
			JClass stringListClass = getClasses().LIST.narrow(getClasses().STRING);
			JClass stringArrayClass = getClasses().STRING.array();
			JArray cookiesArray = JExpr.newArray(getClasses().STRING);
			for (String cookie : settingCookies) {
				cookiesArray.add(JExpr.lit(cookie));
			}
			JVar requestedCookiesVar = methodBody.decl(stringArrayClass, "requestedCookies", cookiesArray);

			JInvocation setCookiesList = JExpr.invoke(responseEntity, "getHeaders").invoke("get").arg("Set-Cookie");
			JVar allCookiesList = methodBody.decl(stringListClass, "allCookies", setCookiesList);

			// for loop over list... add if in string array
			JForEach forEach = methodBody._if(allCookiesList.ne(JExpr._null()))._then() //
					.forEach(getClasses().STRING, "rawCookie", allCookiesList);
			JVar rawCookieVar = forEach.var();

			JBlock forLoopBody = forEach.body();

			JForEach innerForEach = forLoopBody.forEach(getClasses().STRING, "thisCookieName", requestedCookiesVar);
			JBlock innerBody = innerForEach.body();
			JBlock thenBlock = innerBody._if(JExpr.invoke(rawCookieVar, "startsWith").arg(innerForEach.var()))._then();

			// where does the cookie VALUE end?
			JInvocation valueEnd = rawCookieVar.invoke("indexOf").arg(JExpr.lit(';'));
			JVar valueEndVar = thenBlock.decl(getCodeModel().INT, "valueEnd", valueEnd);
			JBlock fixValueEndBlock = thenBlock._if(valueEndVar.eq(JExpr.lit(-1)))._then();
			fixValueEndBlock.assign(valueEndVar, rawCookieVar.invoke("length"));

			JExpression indexOfValue = rawCookieVar.invoke("indexOf").arg("=").plus(JExpr.lit(1));
			JInvocation cookieValue = rawCookieVar.invoke("substring").arg(indexOfValue).arg(valueEndVar);
			thenBlock.invoke(restHolder.getAvailableCookiesField(), "put").arg(innerForEach.var()).arg(cookieValue);
			thenBlock._break();

			return responseEntity;
		}
		return exchangeCall;
	}

	/**
	 * Adds the try/catch around the rest execution code.
	 *
	 * If an exception is caught, it will first check if the handler is set. If
	 * the handler is set, it will call the handler and return null (or nothing
	 * if void). If the handler isn't set, it will re-throw the exception so
	 * that it behaves as it did previous to this feature.
	 */
	private JBlock surroundWithRestTryCatch(RestHolder holder, JBlock block, boolean methodReturnVoid) {
		if (holder.getRestErrorHandlerField() != null) {
			JBlock newBlock = new JBlock(false, false);

			JTryBlock tryBlock = newBlock._try();
			codeModelHelper.copy(block, tryBlock.body());

			JCatchBlock jCatch = tryBlock._catch(getJClass(NESTED_RUNTIME_EXCEPTION));

			JBlock catchBlock = jCatch.body();
			JConditional conditional = catchBlock._if(JOp.ne(holder.getRestErrorHandlerField(), JExpr._null()));
			JVar exceptionParam = jCatch.param("e");

			JBlock thenBlock = conditional._then();

			// call the handler method if it was set.
			thenBlock.add(holder.getRestErrorHandlerField().invoke("onRestClientExceptionThrown").arg(exceptionParam));

			// return null if exception was caught and handled.
			if (!methodReturnVoid) {
				thenBlock._return(JExpr._null());
			}

			// re-throw the exception if handler wasn't set.
			conditional._else()._throw(exceptionParam);
			return newBlock;
		}
		return block;
	}
}
