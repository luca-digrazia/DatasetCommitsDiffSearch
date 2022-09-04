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
package org.androidannotations.generation;

import java.io.File;
import java.io.IOException;

import org.androidannotations.AndroidAnnotationProcessor;
import org.androidannotations.utils.AAProcessorTestHelper;
import org.junit.Before;
import org.junit.Test;

public class ChangedGenerationSuffixTest extends AAProcessorTestHelper {

	@Before
	public void setUp() {
		addManifestProcessorParameter(ActivityInManifest.class);
		addProcessor(AndroidAnnotationProcessor.class);
		ensureOutputDirectoryIsEmpty();
	}

	@Test
	public void generateWithDefaultClassSuffix() throws IOException {
		CompileResult result = compileFiles(ActivityInManifest.class);
		File generatedFile = toGeneratedFile(ActivityInManifest.class);

		assertCompilationSuccessful(result);

		assertGeneratedClassMatches(generatedFile, "public final class ActivityInManifest_");
	}

	@Test
	public void generateWithChangedClassSuffix() throws IOException {
		addProcessorParameter("classSuffix", "Impl");

		CompileResult result = compileFiles(ActivityInManifest.class);
		File generatedFile = toGeneratedFile(ActivityInManifest.class);

		assertCompilationSuccessful(result);

		assertGeneratedClassMatches(generatedFile, "public final class ActivityInManifestImpl");
	}
}
