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
package org.androidannotations.validation;

import javax.lang.model.element.Element;

import org.androidannotations.helper.HasTarget;
import org.androidannotations.model.AnnotationElements;

public interface ElementValidator extends HasTarget {

	/**
	 * Method used to filter validated elements from annotated elements
	 * 
	 * @param element
	 *            the element to validate
	 * @param validatedElements
	 *            to already validated elements
	 * @return true if the element should be added to the validatedElements,
	 *         false otherwise
	 */
	boolean validate(Element element, AnnotationElements validatedElements);

}
