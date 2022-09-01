/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
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

import com.helger.jcodemodel.JBlock;

public interface HasLifecycleMethods extends GeneratedClassHolder {

	JBlock getOnCreateAfterSuperBlock();
	JBlock getOnDestroyBeforeSuperBlock();

	JBlock getOnStartAfterSuperBlock();
	JBlock getOnStopBeforeSuperBlock();

	JBlock getOnResumeAfterSuperBlock();
	JBlock getOnPauseBeforeSuperBlock();

	JBlock getOnAttachAfterSuperBlock();
	JBlock getOnDetachBeforeSuperBlock();
}
