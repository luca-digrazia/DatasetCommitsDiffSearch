/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 * Copyright (C) 2016-2020 the AndroidAnnotations project
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
package org.androidannotations.menu;

import java.io.IOException;

import org.androidannotations.internal.AndroidAnnotationProcessor;
import org.androidannotations.testutils.AAProcessorTestHelper;
import org.junit.Before;
import org.junit.Test;

public class InjectMenuTest extends AAProcessorTestHelper {

	@Before
	public void setUp() {
		addManifestProcessorParameter(InjectMenuActivity.class);
		addProcessor(AndroidAnnotationProcessor.class);
	}

	@Test
	public void testCompilation() throws IOException {
		CompileResult result = compileFiles(InjectMenuActivity.class);
		assertCompilationSuccessful(result);
		assertGeneratedClassMatches(toGeneratedFile(InjectMenuActivity.class), "        this.menu = menu;");
	}

}
