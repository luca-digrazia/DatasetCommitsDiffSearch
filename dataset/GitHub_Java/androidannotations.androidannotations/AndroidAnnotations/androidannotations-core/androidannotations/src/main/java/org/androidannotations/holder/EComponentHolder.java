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

import static com.sun.codemodel.JExpr.cast;
import static com.sun.codemodel.JMod.PRIVATE;
import static org.androidannotations.helper.ModelConstants.generationSuffix;

import javax.lang.model.element.TypeElement;

import org.androidannotations.AndroidAnnotationsEnvironment;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;

public abstract class EComponentHolder extends BaseGeneratedClassHolder {

	protected JExpression contextRef;
	protected JMethod init;
	private JVar resourcesRef;
	private JFieldVar powerManagerRef;

	public EComponentHolder(AndroidAnnotationsEnvironment environment, TypeElement annotatedElement) throws Exception {
		super(environment, annotatedElement);
	}

	public JExpression getContextRef() {
		if (contextRef == null) {
			setContextRef();
		}
		return contextRef;
	}

	protected abstract void setContextRef();

	public JMethod getInit() {
		if (init == null) {
			setInit();
		}
		return init;
	}

	protected abstract void setInit();

	public JBlock getInitBody() {
		return getInit().body();
	}

	public JVar getResourcesRef() {
		if (resourcesRef == null) {
			setResourcesRef();
		}
		return resourcesRef;
	}

	private void setResourcesRef() {
		resourcesRef = getInitBody().decl(classes().RESOURCES, "resources" + generationSuffix(), getContextRef().invoke("getResources"));
	}

	public JFieldVar getPowerManagerRef() {
		if (powerManagerRef == null) {
			setPowerManagerRef();
		}

		return powerManagerRef;
	}

	private void setPowerManagerRef() {
		JBlock methodBody = getInitBody();

		JFieldRef serviceRef = classes().CONTEXT.staticRef("POWER_SERVICE");
		powerManagerRef = getGeneratedClass().field(PRIVATE, classes().POWER_MANAGER, "powerManager" + generationSuffix());
		methodBody.assign(powerManagerRef, cast(classes().POWER_MANAGER, getContextRef().invoke("getSystemService").arg(serviceRef)));
	}
}
