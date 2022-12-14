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
package com.googlecode.androidannotations.helper;

import java.lang.annotation.Annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

public abstract class HasTargetAnnotationHelper extends AnnotationHelper implements HasTarget {

	public HasTargetAnnotationHelper(ProcessingEnvironment processingEnv) {
		super(processingEnv);
	}

	protected void printAnnotationError(Element annotatedElement, String message) {
		Class<? extends Annotation> annotationClass = getTarget();
		printAnnotationError(annotatedElement, annotationClass, message);
	}

	protected void printAnnotationWarning(Element annotatedElement, String message) {
		Class<? extends Annotation> annotationClass = getTarget();
		printAnnotationWarning(annotatedElement, annotationClass, message);
	}

}
