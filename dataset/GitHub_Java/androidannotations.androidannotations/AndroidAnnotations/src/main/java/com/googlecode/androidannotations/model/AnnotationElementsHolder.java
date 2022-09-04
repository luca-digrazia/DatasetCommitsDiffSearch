/*
 * Copyright 2010-2011 Pierre-Yves Ricau (py.ricau at gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.googlecode.androidannotations.model;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public class AnnotationElementsHolder implements AnnotationElements {

	Map<TypeElement, Set<? extends Element>> annotatedElementsByAnnotation = new HashMap<TypeElement, Set<? extends Element>>();

	public void put(TypeElement annotation, Set<? extends Element> annotatedElements) {
		annotatedElementsByAnnotation.put(annotation, annotatedElements);
	}

	@Override
	public Set<? extends Element> getAnnotatedElements(Class<? extends Annotation> annotationClass) {

		TypeElement annotationElement = annotationElementfromAnnotationClass(annotationClass);
		if (annotationElement != null) {
			return new HashSet<Element>(annotatedElementsByAnnotation.get(annotationElement));
		} else {
			return new HashSet<Element>();
		}
	}

	public TypeElement annotationElementfromAnnotationClass(Class<? extends Annotation> annotationClass) {
		for (Entry<TypeElement, Set<? extends Element>> annotatedElements : annotatedElementsByAnnotation.entrySet()) {
			TypeElement elementAnnotation = annotatedElements.getKey();
			String elementAnnotationQualifiedName = elementAnnotation.getQualifiedName().toString();
			String annotationClassName = annotationClass.getName();
			if (elementAnnotationQualifiedName.equals(annotationClassName)) {
				return elementAnnotation;
			}
		}
		return null;
	}

}
