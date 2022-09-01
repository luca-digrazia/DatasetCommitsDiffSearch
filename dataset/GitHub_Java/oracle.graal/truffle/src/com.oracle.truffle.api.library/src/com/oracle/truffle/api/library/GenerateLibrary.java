/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.library;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an abstract class or interface that specifies an active library. An active library consists
 * of a set of messages that are specified using Java instance methods. Each method specifies one
 * message. The implementation of a message may remain abstract or a default implementation may be
 * provided. The first parameter of each message is the receiver type and must be of type
 * {@link Object}. At compile time, an annotation processor generates a library provider base class
 * using the class name of the library followed by the prefix <code>Abstract</code>. Instead of
 * extending the library interface or abstract class directly the generated abstract provider class
 * should be used as base class for implementations.
 * <p>
 * Minimal example of a library with one class that uses one implementation:
 *
 * <p>
 *
 * <h2>Reflection</h2>
 *
 * <h2>Sharing Caches</h2>
 *
 * <h2>Pre and Post Conditions</h2>
 *
 *
 *
 *
 * For each
 * <ol>
 * <li>The library specification (this class)
 * <li>An abstract library provider generated by this class called ${thisClass}Provider.
 * <li>An abstract library provider generated by this class ${thisClass}Provider.
 * </ol>
 *
 * If annotated the the annotation processor will generate class with the name ${class}Provider.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateLibrary {

    /**
     * Specifies a wrapper class that wraps all instances of that library only if Java assertions
     * are enabled.
     *
     * @return
     */
    Class<? extends Library> assertions() default Library.class;

    /**
     * Customize the receiver type for exports that implement this library. Default exports are not
     * affected by this restriction.
     *
     * @return
     */
    Class<?> receiverType() default Object.class;

    /**
     * Specifies active {@link GenerateLibrary library} implementations provided by default as a
     * fallback. May only be used on classes annotated with Library.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE})
    @Repeatable(DefaultExport.Repeat.class)
    public @interface DefaultExport {

        Class<?> value();

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface Repeat {

            DefaultExport[] value();

        }
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface Ignore {

    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface Abstract {

        String[] ifExported() default {};

    }

}
