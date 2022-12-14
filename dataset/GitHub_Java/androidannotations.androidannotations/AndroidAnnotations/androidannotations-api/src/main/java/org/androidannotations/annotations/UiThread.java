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
package org.androidannotations.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Should be used on method that must be run in the Ui thread
 *
 * The annotation parameter delay is the delay (in milliseconds) until the
 * method will be executed. The default is 0 (no delay).
 *
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface UiThread {
	long delay() default 0;

	/**
	 * If useDirect = true, the method will check first if it is inside the UI
	 * thread already. If so, it will directly call the method instead of using
	 * the handler.
	 *
	 * @return
	 */
	boolean useDirect() default false;
}
