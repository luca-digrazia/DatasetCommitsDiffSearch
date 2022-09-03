/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.dsl.processor.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;

import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class CacheExpression extends MessageContainer {

    private final DSLExpression expression;
    private final Parameter sourceParameter;
    private final AnnotationMirror sourceAnnotationMirror;
    private int dimensions = -1;

    public CacheExpression(Parameter sourceParameter, AnnotationMirror sourceAnnotationMirror, DSLExpression expression) {
        this.sourceParameter = sourceParameter;
        this.expression = expression;
        this.sourceAnnotationMirror = sourceAnnotationMirror;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getDimensions() {
        return dimensions;
    }

    public Parameter getParameter() {
        return sourceParameter;
    }

    @Override
    public Element getMessageElement() {
        return sourceParameter.getVariableElement();
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return sourceAnnotationMirror;
    }

    @Override
    public AnnotationValue getMessageAnnotationValue() {
        return ElementUtils.getAnnotationValue(getMessageAnnotation(), "value");
    }

    public DSLExpression getExpression() {
        return expression;
    }

}
