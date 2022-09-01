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
package org.androidannotations.otto;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.handler.AnnotationHandlers;
import org.androidannotations.otto.handler.ProduceHandler;
import org.androidannotations.otto.handler.SubscribeHandler;
import org.androidannotations.plugin.AndroidAnnotationsPlugin;

public class OttoPlugin extends AndroidAnnotationsPlugin {

	private static final String NAME = "Otto";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean shouldCheckApiAndProcessorVersions() {
		return false;
	}

	@Override
	public void addHandlers(AnnotationHandlers annotationHandlers, AndroidAnnotationsEnvironment androidAnnotationEnv) {
		annotationHandlers.add(new SubscribeHandler(androidAnnotationEnv));
		annotationHandlers.add(new ProduceHandler(androidAnnotationEnv));
	}
}
