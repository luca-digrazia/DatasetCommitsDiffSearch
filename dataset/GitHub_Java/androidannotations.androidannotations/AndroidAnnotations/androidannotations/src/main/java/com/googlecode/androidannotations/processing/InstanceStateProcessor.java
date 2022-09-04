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

import static com.sun.codemodel.JMod.PUBLIC;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import android.os.Bundle;

import com.googlecode.androidannotations.annotations.InstanceState;
import com.googlecode.androidannotations.helper.APTCodeModelHelper;
import com.googlecode.androidannotations.helper.AnnotationHelper;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;

public class InstanceStateProcessor extends AnnotationHelper implements ElementProcessor {

	private static final String BUNDLE_PARAM_NAME = "bundle";

	public static final Map<String, String> methodSuffixNameByTypeName = new HashMap<String, String>();

	static {

		methodSuffixNameByTypeName.put("android.os.Bundle", "Bundle");

		methodSuffixNameByTypeName.put("boolean", "Boolean");
		methodSuffixNameByTypeName.put("boolean[]", "BooleanArray");

		methodSuffixNameByTypeName.put("byte", "Byte");
		methodSuffixNameByTypeName.put("byte[]", "ByteArray");

		methodSuffixNameByTypeName.put("char", "Char");
		methodSuffixNameByTypeName.put("char[]", "CharArray");

		methodSuffixNameByTypeName.put("java.lang.CharSequence", "CharSequence");

		methodSuffixNameByTypeName.put("double", "Double");
		methodSuffixNameByTypeName.put("double[]", "DoubleArray");

		methodSuffixNameByTypeName.put("float", "Float");
		methodSuffixNameByTypeName.put("float[]", "FloatArray");

		methodSuffixNameByTypeName.put("int", "Int");
		methodSuffixNameByTypeName.put("int[]", "IntArray");
		methodSuffixNameByTypeName.put("java.util.ArrayList<java.lang.Integer>", "IntegerArrayList");

		methodSuffixNameByTypeName.put("long", "Long");
		methodSuffixNameByTypeName.put("long[]", "LongArray");

		methodSuffixNameByTypeName.put("short", "Short");
		methodSuffixNameByTypeName.put("short[]", "ShortArray");

		methodSuffixNameByTypeName.put("java.lang.String", "String");
		methodSuffixNameByTypeName.put("java.lang.String[]", "StringArray");
		methodSuffixNameByTypeName.put("java.util.ArrayList<java.lang.String>", "StringArrayList");
	}

	private final APTCodeModelHelper helper = new APTCodeModelHelper();

	public InstanceStateProcessor(ProcessingEnvironment processingEnv) {
		super(processingEnv);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return InstanceState.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeansHolder activitiesHolder) {
		EBeanHolder holder = activitiesHolder.getEnclosingEBeanHolder(element);
		String fieldName = element.getSimpleName().toString();

		JBlock saveStateBody = getSaveStateMethodBody(codeModel, holder);
		JBlock restoreStateBody = getRestoreStateBody(holder);

		String typeString = element.asType().toString();
		TypeElement elementType = typeElementFromQualifiedName(typeString);

		String methodNameToSave;
		String methodNameToRestore;
		boolean restoreCallNeedCastStatement = false;

		if (methodSuffixNameByTypeName.containsKey(typeString)) {

			methodNameToSave = "put" + methodSuffixNameByTypeName.get(typeString);
			methodNameToRestore = "get" + methodSuffixNameByTypeName.get(typeString);

		} else if (element.asType().getKind() == TypeKind.ARRAY) {

			ArrayType arrayType = (ArrayType) element.asType();

			if (arrayType.getComponentType() instanceof DeclaredType) {
				DeclaredType declaredType = (DeclaredType) arrayType.getComponentType();
				typeString = declaredType.asElement().toString();
			} else {
				typeString = arrayType.getComponentType().toString();
			}

			elementType = typeElementFromQualifiedName(typeString);

			if (isTypeParcelable(elementType)) {
				methodNameToSave = "put" + "ParcelableArray";
				methodNameToRestore = "get" + "ParcelableArray";
				restoreCallNeedCastStatement = true;
			} else {
				methodNameToSave = "put" + "Serializable";
				methodNameToRestore = "get" + "Serializable";
				restoreCallNeedCastStatement = true;
			}
		} else {

			if (element.asType() instanceof DeclaredType) {
				DeclaredType declaredType = (DeclaredType) element.asType();
				typeString = declaredType.asElement().toString();
				elementType = typeElementFromQualifiedName(typeString);
			}

			if (isTypeParcelable(elementType)) {
				methodNameToSave = "put" + "Parcelable";
				methodNameToRestore = "get" + "Parcelable";
			} else {
				methodNameToSave = "put" + "Serializable";
				methodNameToRestore = "get" + "Serializable";
				restoreCallNeedCastStatement = true;
			}
		}

		JFieldRef ref = JExpr.ref(fieldName);
		saveStateBody.invoke(JExpr.ref(BUNDLE_PARAM_NAME), methodNameToSave).arg(fieldName).arg(ref);

		JInvocation restoreMethodCall = JExpr.invoke(JExpr.ref("savedInstanceState"), methodNameToRestore).arg(fieldName);
		if (restoreCallNeedCastStatement) {

			JClass jclass = helper.typeMirrorToJClass(element.asType(), holder);
			JExpression castStatement = JExpr.cast(jclass, restoreMethodCall);
			restoreStateBody.assign(ref, castStatement);

		} else {

			restoreStateBody.assign(ref, restoreMethodCall);

		}
	}

	private JBlock getRestoreStateBody(EBeanHolder holder) {

		if (holder.restoreInstanceStateBlock == null) {
			JExpression bundleNullTest = JExpr.ref("savedInstanceState").ne(JExpr._null());
			holder.restoreInstanceStateBlock = holder.initIfActivityBody.block()._if(bundleNullTest)._then();
		}

		return holder.restoreInstanceStateBlock;
	}

	private JBlock getSaveStateMethodBody(JCodeModel codeModel, EBeanHolder holder) {

		if (holder.saveInstanceStateBlock == null) {
			JMethod method = holder.eBean.method(PUBLIC, codeModel.VOID, "onSaveInstanceState");
			method.annotate(Override.class);
			method.param(Bundle.class, BUNDLE_PARAM_NAME);

			holder.saveInstanceStateBlock = method.body();

			holder.saveInstanceStateBlock.invoke(JExpr._super(), "onSaveInstanceState").arg(JExpr.ref(BUNDLE_PARAM_NAME));
		}

		return holder.saveInstanceStateBlock;
	}

	private boolean isTypeParcelable(TypeElement elementType) {

		TypeElement parcelableType = typeElementFromQualifiedName("android.os.Parcelable");

		return elementType != null && isSubtype(elementType, parcelableType);
	}

}