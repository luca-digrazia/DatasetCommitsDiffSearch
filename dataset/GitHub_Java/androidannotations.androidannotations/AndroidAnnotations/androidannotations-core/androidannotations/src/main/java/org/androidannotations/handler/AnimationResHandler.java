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
package org.androidannotations.handler;

import static com.sun.codemodel.JExpr.ref;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.holder.EComponentHolder;
import org.androidannotations.model.AndroidRes;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JFieldRef;

public class AnimationResHandler extends AbstractResHandler {

	public AnimationResHandler(AndroidAnnotationsEnvironment environmentEnvironment) {
		super(AndroidRes.ANIMATION, environmentEnvironment);
	}

	@Override
	protected void makeCall(String fieldName, EComponentHolder holder, JBlock methodBody, JFieldRef idRef) {
		methodBody.assign(ref(fieldName), getClasses().ANIMATION_UTILS.staticInvoke("loadAnimation").arg(holder.getContextRef()).arg(idRef));
	}
}
