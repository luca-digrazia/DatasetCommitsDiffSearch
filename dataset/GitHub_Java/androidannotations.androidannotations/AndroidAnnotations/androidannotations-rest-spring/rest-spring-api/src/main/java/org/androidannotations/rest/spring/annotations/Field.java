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
package org.androidannotations.rest.spring.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used to add a form-encoded parameter to the request
 * from a method parameter. The annotation value should be the name of the
 * form-encoded parameter, if not specified, the method parameter name will be
 * used as the name of the form-encoded parameter. This annotation only can be
 * used with POST requests, hence the method must be annotated with {@link Post}
 * . To use this annotation, you must add <code>FormHttpMessageConverter</code>
 * to the list of converters.
 *
 * <blockquote>
 *
 * <b>Example :</b>
 *
 * <pre>
 * &#064;Rest(rootUrl = &quot;http://myserver&quot;, converters = FormHttpMessageConverter.class)
 * public interface RestClient {
 *
 * 	&#064;Post(&quot;/events/{id}&quot;)
 * 	EventList addEvent(String id, <b>&#064;Field</b> String eventName);
 * }
 * </pre>
 *
 * </blockquote>
 *
 * @see Rest
 * @see Post
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Field {

	/**
	 * Name of the form-encoded parameter.
	 *
	 * @return name of the parameter
	 */
	String value() default "";
}
