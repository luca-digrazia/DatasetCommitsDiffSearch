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
package org.androidannotations.rest.spring.helper;

import static java.util.Arrays.asList;

import static org.androidannotations.rest.spring.helper.RestSpringClasses.CLIENT_HTTP_REQUEST_FACTORY;
import static org.androidannotations.rest.spring.helper.RestSpringClasses.CLIENT_HTTP_REQUEST_INTERCEPTOR;
import static org.androidannotations.rest.spring.helper.RestSpringClasses.HTTP_MESSAGE_CONVERTER;
import static org.androidannotations.rest.spring.helper.RestSpringClasses.REST_TEMPLATE;
import static org.androidannotations.rest.spring.helper.RestSpringClasses.REST_CLIENT_EXCEPTION;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.annotations.EBean;
import org.androidannotations.helper.CanonicalNameConstants;
import org.androidannotations.helper.TargetAnnotationHelper;
import org.androidannotations.helper.ValidatorHelper;
import org.androidannotations.process.ElementValidation;
import org.androidannotations.rest.spring.annotations.Delete;
import org.androidannotations.rest.spring.annotations.Get;
import org.androidannotations.rest.spring.annotations.Head;
import org.androidannotations.rest.spring.annotations.Options;
import org.androidannotations.rest.spring.annotations.Post;
import org.androidannotations.rest.spring.annotations.Put;
import org.androidannotations.rest.spring.annotations.Rest;
import org.androidannotations.rest.spring.api.RestClientErrorHandling;
import org.androidannotations.rest.spring.api.RestClientHeaders;
import org.androidannotations.rest.spring.api.RestClientRootUrl;
import org.androidannotations.rest.spring.api.RestClientSupport;

public class RestSpringValidatorHelper extends ValidatorHelper {

	private static final List<String> VALID_REST_INTERFACES = asList(RestClientHeaders.class.getName(), RestClientErrorHandling.class.getName(),
			RestClientRootUrl.class.getName(), RestClientSupport.class.getName());
	private static final List<Class<? extends Annotation>> REST_ANNOTATION_CLASSES = Arrays.asList(Get.class, Head.class, Options.class, Post.class, Put.class, Delete.class);

	private static final String METHOD_NAME_SET_ROOT_URL = "setRootUrl";
	private static final String METHOD_NAME_SET_AUTHENTICATION = "setAuthentication";
	private static final String METHOD_NAME_SET_BEARER_AUTH = "setBearerAuth";
	private static final String METHOD_NAME_GET_COOKIE = "getCookie";
	private static final String METHOD_NAME_GET_HEADER = "getHeader";

	private static final String METHOD_NAME_GET_ROOT_URL = "getRootUrl";

	private final RestAnnotationHelper restAnnotationHelper;

	public RestSpringValidatorHelper(AndroidAnnotationsEnvironment environment, String target) {
		super(new TargetAnnotationHelper(environment, target));
		restAnnotationHelper = new RestAnnotationHelper(environment, target);
	}

	public void doesNotExtendInvalidInterfaces(TypeElement element, ElementValidation valid) {
		if (element.getInterfaces().size() > 0) {
			boolean isValid = true;

			for (TypeMirror iface : element.getInterfaces()) {
				if (!VALID_REST_INTERFACES.contains(iface.toString())) {
					isValid = false;
					break;
				}
			}

			if (!isValid) {
				valid.addError("%s interfaces can only extend the following interfaces: " + VALID_REST_INTERFACES);
			}
		}
	}

	public void enclosingElementHasRestAnnotation(Element element, ElementValidation valid) {
		String error = "can only be used in an interface annotated with";
		enclosingElementHasAnnotation(Rest.class, element, valid, error);
	}

	public void unannotatedMethodReturnsRestTemplate(TypeElement typeElement, ElementValidation valid) {
		List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
		boolean foundGetRestTemplateMethod = false;
		boolean foundSetRestTemplateMethod = false;
		boolean foundSetAuthenticationMethod = false;
		boolean foundSetBearerAuthMethod = false;
		boolean foundSetRootUrlMethod = false;
		boolean foundGetCookieMethod = false;
		boolean foundGetHeaderMethod = false;
		boolean foundGetRootUrlMethod = false;

		for (Element enclosedElement : enclosedElements) {
			if (enclosedElement.getKind() != ElementKind.METHOD) {
				valid.addError(enclosedElement, "Only methods are allowed in a %s annotated interface");
			} else {

				boolean hasRestAnnotation = false;
				for (Class<? extends Annotation> annotationClass : REST_ANNOTATION_CLASSES) {
					if (enclosedElement.getAnnotation(annotationClass) != null) {
						hasRestAnnotation = true;
						break;
					}
				}

				if (!hasRestAnnotation) {

					ExecutableElement executableElement = (ExecutableElement) enclosedElement;
					TypeMirror returnType = executableElement.getReturnType();
					String simpleName = executableElement.getSimpleName().toString();

					if (returnType.toString().equals(REST_TEMPLATE)) {
						if (executableElement.getParameters().size() > 0) {
							valid.addError(enclosedElement,
									"The method returning a RestTemplate should not declare any parameter in a " + TargetAnnotationHelper.annotationName(Rest.class) + " annotated interface");
						} else {
							if (foundGetRestTemplateMethod) {
								valid.addError(enclosedElement, "Only one method should declare returning a RestTemplate in a " + TargetAnnotationHelper.annotationName(Rest.class)
										+ " annotated interface");
							} else {
								foundGetRestTemplateMethod = true;
							}
						}
					} else if (simpleName.equals(METHOD_NAME_GET_ROOT_URL)) {
						if (!returnType.toString().equals(CanonicalNameConstants.STRING)) {
							valid.addError(enclosedElement, "The method getRootUrl must return String on a %s annotated interface");
						}

						if (executableElement.getParameters().size() != 0) {
							valid.addError(enclosedElement, "The method getRootUrl cannot have parameters on a " + TargetAnnotationHelper.annotationName(Rest.class)
									+ " annotated interface");
						}

						if (!foundGetRootUrlMethod) {
							foundGetRootUrlMethod = true;
						} else {
							valid.addError(enclosedElement, "The can be only one getRootUrl method on a %s annotated interface");
						}
					} else if (returnType.getKind() == TypeKind.VOID) {
						List<? extends VariableElement> parameters = executableElement.getParameters();
						if (parameters.size() == 1) {
							VariableElement firstParameter = parameters.get(0);
							if (firstParameter.asType().toString().equals(REST_TEMPLATE)) {
								if (!foundSetRestTemplateMethod) {
									foundSetRestTemplateMethod = true;
								} else {
									valid.addError(enclosedElement, "You can only have oneRestTemplate setter method on a " + TargetAnnotationHelper.annotationName(Rest.class)
											+ " annotated interface");
								}
							} else if (executableElement.getSimpleName().toString().equals(METHOD_NAME_SET_ROOT_URL) && !foundSetRootUrlMethod) {
								foundSetRootUrlMethod = true;
							} else if (executableElement.getSimpleName().toString().equals(METHOD_NAME_SET_AUTHENTICATION) && !foundSetAuthenticationMethod) {
								foundSetAuthenticationMethod = true;
							} else if (executableElement.getSimpleName().toString().equals(METHOD_NAME_SET_BEARER_AUTH) && !foundSetBearerAuthMethod) {
								foundSetBearerAuthMethod = true;
							} else {
								valid.addError(enclosedElement,
										"The method to set a RestTemplate should have only one RestTemplate parameter on a " + TargetAnnotationHelper.annotationName(Rest.class)
												+ " annotated interface");

							}
						} else if (parameters.size() == 2) {
							VariableElement firstParameter = parameters.get(0);
							VariableElement secondParameter = parameters.get(1);
							if (!(firstParameter.asType().toString().equals(CanonicalNameConstants.STRING) && secondParameter.asType().toString().equals(CanonicalNameConstants.STRING))) {
								valid.addError(enclosedElement,
										"The method to set headers, cookies, or HTTP Basic Auth should have only String parameters on a " + TargetAnnotationHelper.annotationName(Rest.class)
												+ " annotated interface");
							}
						} else {
							valid.addError(enclosedElement,
									"The method to set a RestTemplate should have only one RestTemplate parameter on a " + TargetAnnotationHelper.annotationName(Rest.class) + " annotated interface");
						}
					} else if (returnType.toString().equals(CanonicalNameConstants.STRING)) {
						List<? extends VariableElement> parameters = executableElement.getParameters();
						if (parameters.size() == 1) {
							VariableElement firstParameter = parameters.get(0);
							if (firstParameter.asType().toString().equals(CanonicalNameConstants.STRING)) {
								if (executableElement.getSimpleName().toString().equals(METHOD_NAME_GET_COOKIE) && !foundGetCookieMethod) {
									foundGetCookieMethod = true;
								} else if (executableElement.getSimpleName().toString().equals(METHOD_NAME_GET_HEADER) && !foundGetHeaderMethod) {
									foundGetHeaderMethod = true;
								} else {
									valid.addError(enclosedElement,
											"Only one getCookie(String) and one getHeader(String) method are allowed on a " + TargetAnnotationHelper.annotationName(Rest.class)
													+ " annotated interface");
								}
							} else {
								valid.addError(enclosedElement,
										"Only getCookie(String) and getHeader(String) can return a String on a " + TargetAnnotationHelper.annotationName(Rest.class) + " annotated interface");
							}

						} else {
							valid.addError(enclosedElement, "The only methods that can return a String on a " + TargetAnnotationHelper.annotationName(Rest.class)
									+ " annotated interface are getCookie(String) and getHeader(String)");
						}
					} else {
						valid.addError(enclosedElement, "All methods should be annotated in a " + TargetAnnotationHelper.annotationName(Rest.class)
								+ " annotated interface, except the ones that returns or set a RestTemplate");
					}
				}
			}
		}
	}

	public void validateConverters(Element element, ElementValidation valid) {
		TypeMirror httpMessageConverterType = annotationHelper.typeElementFromQualifiedName(HTTP_MESSAGE_CONVERTER).asType();
		TypeMirror httpMessageConverterTypeErased = annotationHelper.getTypeUtils().erasure(httpMessageConverterType);
		List<DeclaredType> converters = annotationHelper.extractAnnotationClassArrayParameter(element, annotationHelper.getTarget(), "converters");

		if (converters == null || converters.isEmpty()) {
			valid.addError(element, "At least one converter is required");
			return;
		}

		for (DeclaredType converterType : converters) {
			TypeMirror erasedConverterType = annotationHelper.getTypeUtils().erasure(converterType);
			if (annotationHelper.isSubtype(erasedConverterType, httpMessageConverterTypeErased)) {
				Element converterElement = converterType.asElement();
				if (converterElement.getKind().isClass()) {
					if (!annotationHelper.isAbstract(converterElement)) {
						if (converterElement.getAnnotation(EBean.class) == null) {
							List<ExecutableElement> constructors = ElementFilter.constructorsIn(converterElement.getEnclosedElements());
							boolean hasPublicWithNoArgumentConstructor = false;
							for (ExecutableElement constructor : constructors) {
								if (annotationHelper.isPublic(constructor) && constructor.getParameters().isEmpty()) {
									hasPublicWithNoArgumentConstructor = true;
								}
							}
							if (!hasPublicWithNoArgumentConstructor) {
								valid.addError("The converter class must have a public no argument constructor");
							}
						}
					} else {
						valid.addError("The converter class must not be abstract");
					}
				} else {
					valid.addError("The converter class must be a class");
				}
			} else {
				valid.addError("The converter class must be a subtype of " + HTTP_MESSAGE_CONVERTER);
			}
		}
	}

	public void validateInterceptors(Element element, ElementValidation valid) {
		TypeMirror clientHttpRequestInterceptorType = annotationHelper.typeElementFromQualifiedName(CLIENT_HTTP_REQUEST_INTERCEPTOR).asType();
		TypeMirror clientHttpRequestInterceptorTypeErased = annotationHelper.getTypeUtils().erasure(clientHttpRequestInterceptorType);
		List<DeclaredType> interceptors = annotationHelper.extractAnnotationClassArrayParameter(element, annotationHelper.getTarget(), "interceptors");
		if (interceptors == null) {
			return;
		}
		for (DeclaredType interceptorType : interceptors) {
			TypeMirror erasedInterceptorType = annotationHelper.getTypeUtils().erasure(interceptorType);
			if (annotationHelper.isSubtype(erasedInterceptorType, clientHttpRequestInterceptorTypeErased)) {
				Element interceptorElement = interceptorType.asElement();
				if (interceptorElement.getKind().isClass()) {
					if (!annotationHelper.isAbstract(interceptorElement)) {
						if (interceptorElement.getAnnotation(EBean.class) == null) {
							List<ExecutableElement> constructors = ElementFilter.constructorsIn(interceptorElement.getEnclosedElements());
							boolean hasPublicWithNoArgumentConstructor = false;
							for (ExecutableElement constructor : constructors) {
								if (annotationHelper.isPublic(constructor) && constructor.getParameters().isEmpty()) {
									hasPublicWithNoArgumentConstructor = true;
								}
							}
							if (!hasPublicWithNoArgumentConstructor) {
								valid.addError("The interceptor class must have a public no argument constructor or be annotated with @EBean");
							}
						}
					} else {
						valid.addError("The interceptor class must not be abstract");
					}
				} else {
					valid.addError("The interceptor class must be a class");
				}
			} else {
				valid.addError("The interceptor class must be a subtype of " + CLIENT_HTTP_REQUEST_INTERCEPTOR);
			}
		}
	}

	public void validateRequestFactory(Element element, ElementValidation valid) {
		TypeMirror clientHttpRequestFactoryType = annotationHelper.typeElementFromQualifiedName(CLIENT_HTTP_REQUEST_FACTORY).asType();
		DeclaredType requestFactory = annotationHelper.extractAnnotationClassParameter(element, annotationHelper.getTarget(), "requestFactory");
		if (requestFactory != null) {
			if (annotationHelper.isSubtype(requestFactory, clientHttpRequestFactoryType)) {
				Element requestFactoryElement = requestFactory.asElement();
				if (requestFactoryElement.getKind().isClass()) {
					if (!annotationHelper.isAbstract(requestFactoryElement)) {
						if (requestFactoryElement.getAnnotation(EBean.class) != null) {
							return;
						}
						List<ExecutableElement> constructors = ElementFilter.constructorsIn(requestFactoryElement.getEnclosedElements());
						for (ExecutableElement constructor : constructors) {
							if (annotationHelper.isPublic(constructor) && constructor.getParameters().isEmpty()) {
								return;
							}
						}
						valid.addError("The requestFactory class must have a public no argument constructor or must be annotated with @EBean");
					} else {
						valid.addError("The requestFactory class must not be abstract");
					}
				} else {
					valid.addError("The requestFactory class must be a class");
				}
			} else {
				valid.addError("The requestFactory class must be a subtype of " + CLIENT_HTTP_REQUEST_FACTORY);
			}
		}
	}

	public void throwsOnlyRestClientException(ExecutableElement element, ElementValidation valid) {
		List<? extends TypeMirror> thrownTypes = element.getThrownTypes();
		if (thrownTypes.size() > 0) {
			if (thrownTypes.size() > 1 || !thrownTypes.get(0).toString().equals(REST_CLIENT_EXCEPTION)) {
				valid.addError("%s annotated methods can only declare throwing a RestClientException");
			}
		}
	}

	public void urlVariableNamesExistInParameters(ExecutableElement element, Set<String> variableNames, ElementValidation valid) {

		List<? extends VariableElement> parameters = element.getParameters();

		List<String> parametersName = new ArrayList<>();
		for (VariableElement parameter : parameters) {
			parametersName.add(parameter.getSimpleName().toString());
		}

		String[] cookiesToUrl = restAnnotationHelper.requiredUrlCookies(element);
		if (cookiesToUrl != null) {
			Collections.addAll(parametersName, cookiesToUrl);
		}

		for (String variableName : variableNames) {
			if (!parametersName.contains(variableName)) {
				valid.addError("%s annotated method has an url variable which name could not be found in the method parameters: " + variableName);
				return;
			}
		}
	}

	public void urlVariableNamesExistInParametersAndHasNoOneMoreParameter(ExecutableElement element, ElementValidation valid) {
		if (valid.isValid()) {
			Set<String> variableNames = restAnnotationHelper.extractUrlVariableNames(element);
			urlVariableNamesExistInParameters(element, variableNames, valid);
			if (valid.isValid()) {
				List<? extends VariableElement> parameters = element.getParameters();

				if (parameters.size() > variableNames.size()) {
					valid.addError("%s annotated method has only url variables in the method parameters");
				}
			}
		}
	}

	public void urlVariableNamesExistInParametersAndHasOnlyOneMoreParameter(ExecutableElement element, ElementValidation valid) {
		if (valid.isValid()) {
			Set<String> variableNames = restAnnotationHelper.extractUrlVariableNames(element);
			urlVariableNamesExistInParameters(element, variableNames, valid);
			if (valid.isValid()) {
				List<? extends VariableElement> parameters = element.getParameters();

				if (parameters.size() > variableNames.size() + 1) {
					valid.addError("%s annotated method has more than one entity parameter");
				}
			}
		}
	}

	public void hasSpringAndroidJars(ElementValidation valid) {
		Elements elementUtils = annotationHelper.getElementUtils();
		if (elementUtils.getTypeElement(REST_TEMPLATE) == null) {
			valid.addError("Could not find the SpringAndroid framework in the classpath, the following class is missing: " + REST_TEMPLATE);
		}
	}

	public void hasHttpHeadersReturnType(ExecutableElement element, ElementValidation valid) {
		String returnType = element.getReturnType().toString();
		if (!returnType.equals("org.springframework.http.HttpHeaders")) {
			valid.addError("%s annotated methods can only return a HttpHeaders, not " + returnType);
		}
	}

	public void hasSetOfHttpMethodReturnType(ExecutableElement element, ElementValidation valid) {
		TypeMirror returnType = element.getReturnType();
		String returnTypeString = returnType.toString();
		if (!returnTypeString.equals("java.util.Set<org.springframework.http.HttpMethod>")) {
			valid.addError("%s annotated methods can only return a Set of HttpMethod, not " + returnTypeString);
		} else {
			DeclaredType declaredType = (DeclaredType) returnType;
			List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
			if (typeArguments.size() != 1) {
				valid.addError("%s annotated methods can only return a parameterized Set (with HttpMethod)");
			} else {
				TypeMirror typeArgument = typeArguments.get(0);
				if (!typeArgument.toString().equals("org.springframework.http.HttpMethod")) {
					valid.addError("%s annotated methods can only return a parameterized Set of HttpMethod, not " + typeArgument.toString());
				}
			}
		}
	}
}
