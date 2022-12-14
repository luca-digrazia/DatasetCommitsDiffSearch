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
package com.googlecode.androidannotations.processing;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

import com.googlecode.androidannotations.annotations.TrackingTouchStart;
import com.googlecode.androidannotations.helper.APTCodeModelHelper;
import com.googlecode.androidannotations.helper.OnSeekBarChangeListenerHelper;
import com.googlecode.androidannotations.rclass.IRClass;
import com.googlecode.androidannotations.rclass.IRClass.Res;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;

/**
 * @author Mathieu Boniface
 */
public class TrackingTouchStartProcessor implements DecoratingElementProcessor {

	private final OnSeekBarChangeListenerHelper helper;

	private final APTCodeModelHelper codeModelHelper;

	public TrackingTouchStartProcessor(ProcessingEnvironment processingEnv, IRClass rClass) {
		codeModelHelper = new APTCodeModelHelper();
		helper = new OnSeekBarChangeListenerHelper(processingEnv, getTarget(), rClass, codeModelHelper);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return TrackingTouchStart.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeanHolder holder) {

		String methodName = element.getSimpleName().toString();

		List<JFieldRef> idsRefs = helper.extractAnnotationFieldRefs(holder, element, Res.ID, true);

		for (JFieldRef idRef : idsRefs) {
			OnSeekBarChangeListenerHolder onSeekBarChangeListenerHolder = helper.getOrCreateListener(codeModel, holder, idRef);

			JInvocation textChangeCall;
			JMethod methodToCall = getMethodToCall(onSeekBarChangeListenerHolder);

			JBlock previousBody = codeModelHelper.removeBody(methodToCall);
			JBlock methodBody = methodToCall.body();

			methodBody.add(previousBody);
			JExpression activityRef = holder.eBean.staticRef("this");
			textChangeCall = methodBody.invoke(activityRef, methodName);

			JVar progressParameter = codeModelHelper.findParameterByName(methodToCall, "seekBar");
			textChangeCall.arg(progressParameter);
		}

	}

	protected JMethod getMethodToCall(OnSeekBarChangeListenerHolder onSeekBarChangeListenerHolder) {
		JMethod methodToCall = onSeekBarChangeListenerHolder.onStartTrackingTouchMethod;
		return methodToCall;
	}
}
