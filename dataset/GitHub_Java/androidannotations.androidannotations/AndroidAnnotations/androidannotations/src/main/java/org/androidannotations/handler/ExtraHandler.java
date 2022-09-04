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
package org.androidannotations.handler;

import static com.sun.codemodel.JExpr._this;
import static com.sun.codemodel.JExpr.cast;
import static com.sun.codemodel.JExpr.invoke;
import static com.sun.codemodel.JExpr.lit;
import static com.sun.codemodel.JMod.FINAL;
import static com.sun.codemodel.JMod.PUBLIC;
import static com.sun.codemodel.JMod.STATIC;
import static org.androidannotations.helper.CanonicalNameConstants.PARCELABLE;
import static org.androidannotations.helper.CanonicalNameConstants.SERIALIZABLE;
import static org.androidannotations.helper.CanonicalNameConstants.STRING;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.androidannotations.annotations.Extra;
import org.androidannotations.helper.APTCodeModelHelper;
import org.androidannotations.helper.AnnotationHelper;
import org.androidannotations.helper.BundleHelper;
import org.androidannotations.helper.CaseHelper;
import org.androidannotations.holder.HasExtras;
import org.androidannotations.holder.HasIntentBuilder;
import org.androidannotations.model.AnnotationElements;
import org.androidannotations.process.IsValid;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;

public class ExtraHandler extends BaseAnnotationHandler<HasExtras> {

	private final AnnotationHelper annotationHelper;
	private final APTCodeModelHelper codeModelHelper = new APTCodeModelHelper();

	public ExtraHandler(ProcessingEnvironment processingEnvironment) {
		super(Extra.class, processingEnvironment);
		annotationHelper = new AnnotationHelper(processingEnv);
	}

	@Override
	public void validate(Element element, AnnotationElements validatedElements, IsValid valid) {
		/*
		 * TODO since we override setIntent(), we should check that the
		 * setIntent() method can be overridden
		 */

		validatorHelper.enclosingElementHasEActivity(element, validatedElements, valid);

		validatorHelper.isNotPrivate(element, valid);
	}

	@Override
	public void process(Element element, HasExtras holder) {
		Extra annotation = element.getAnnotation(Extra.class);
		String extraKey = annotation.value();
		String fieldName = element.getSimpleName().toString();
		if (extraKey.isEmpty()) {
			extraKey = fieldName;
		}

		JFieldVar extraKeyStaticField = createStaticExtraField(holder, extraKey, fieldName);
		injectExtraInComponent(element, holder, extraKeyStaticField, fieldName);

		if (holder instanceof HasIntentBuilder)
			createIntentInjectionMethod(element, (HasIntentBuilder) holder, extraKeyStaticField, fieldName);
	}

	private JFieldVar createStaticExtraField(HasExtras holder, String extraKey, String fieldName) {
		String staticFieldName;
		if (fieldName.endsWith("Extra")) {
			staticFieldName = CaseHelper.camelCaseToUpperSnakeCase(fieldName);
		} else {
			staticFieldName = CaseHelper.camelCaseToUpperSnakeCase(fieldName + "Extra");
		}
		return holder.getGeneratedClass().field(PUBLIC | STATIC | FINAL, holder.classes().STRING, staticFieldName, lit(extraKey));
	}

	private void injectExtraInComponent(Element element, HasExtras hasExtras, JFieldVar extraKeyStaticField, String fieldName) {
		JVar extras = hasExtras.getInjectExtras();
		JBlock injectExtrasBlock = hasExtras.getInjectExtrasBlock();

		BundleHelper bundleHelper = new BundleHelper(annotationHelper, element);

		JFieldRef extraField = JExpr.ref(fieldName);
		JBlock ifContainsKey = injectExtrasBlock._if(JExpr.invoke(extras, "containsKey").arg(extraKeyStaticField))._then();

		JExpression restoreMethodCall = JExpr.invoke(extras, bundleHelper.getMethodNameToRestore()).arg(extraKeyStaticField);
		if (bundleHelper.restoreCallNeedCastStatement()) {

			JClass jclass = codeModelHelper.typeMirrorToJClass(element.asType(), hasExtras);
			restoreMethodCall = JExpr.cast(jclass, restoreMethodCall);

			if (bundleHelper.restoreCallNeedsSuppressWarning()) {
				JMethod injectExtrasMethod = hasExtras.getInjectExtrasMethod();
				if (injectExtrasMethod.annotations().size() == 0) {
					injectExtrasMethod.annotate(SuppressWarnings.class).param("value", "unchecked");
				}
			}

		}

		/*
		 * Handle the android bug : getIntent().getExtras().getByteArray()
		 * always returns null;
		 */
		restoreMethodCall = handleByteArrayExtraBug(element, extraKeyStaticField, restoreMethodCall);

		ifContainsKey.assign(extraField, restoreMethodCall);
	}

	private JExpression handleByteArrayExtraBug(Element element, JFieldVar extraKeyStaticField, JExpression restoreMethodCall) {
		if ("byte[]".equals(element.asType().toString())) {
			restoreMethodCall = invoke("getIntent").invoke("getByteArrayExtra").arg(extraKeyStaticField);
		}
		return restoreMethodCall;
	}

	private void createIntentInjectionMethod(Element element, HasIntentBuilder holder, JFieldVar extraKeyStaticField, String fieldName) {
		JDefinedClass intentBuilderClass = holder.getIntentBuilderClass();
		JMethod method = intentBuilderClass.method(PUBLIC, intentBuilderClass, fieldName);

		boolean castToSerializable = false;
		boolean castToParcelable = false;
		TypeMirror extraType = element.asType();
		if (extraType.getKind() == TypeKind.DECLARED) {
			Elements elementUtils = processingEnv.getElementUtils();
			Types typeUtils = processingEnv.getTypeUtils();
			TypeMirror parcelableType = elementUtils.getTypeElement(PARCELABLE).asType();
			if (!typeUtils.isSubtype(extraType, parcelableType)) {
				TypeMirror stringType = elementUtils.getTypeElement(STRING).asType();
				if (!typeUtils.isSubtype(extraType, stringType)) {
					castToSerializable = true;
				}
			} else {
				TypeMirror serializableType = elementUtils.getTypeElement(SERIALIZABLE).asType();
				if (typeUtils.isSubtype(extraType, serializableType)) {
					castToParcelable = true;
				}
			}
		}
		JClass paramClass = codeModelHelper.typeMirrorToJClass(extraType, holder);
		JVar extraParam = method.param(paramClass, fieldName);
		JBlock body = method.body();
		JInvocation invocation = body.invoke(holder.getIntentField(), "putExtra").arg(extraKeyStaticField);
		if (castToSerializable) {
			invocation.arg(cast(holder.classes().SERIALIZABLE, extraParam));
		} else if (castToParcelable) {
			invocation.arg(cast(holder.classes().PARCELABLE, extraParam));
		} else {
			invocation.arg(extraParam);
		}
		body._return(_this());
	}
}
