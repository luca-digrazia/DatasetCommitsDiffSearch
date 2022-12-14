/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

// @formatter:off

/*
 @ApiInfo(
 group="Tutorial"
 )
 */
/**
 * <h1>Truffle Tutorial: Embedding Truffle Languages in Java</h1>
 *
 * This tutorial shows how to embed the Truffle language
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine execution environment} in a
 * Java application. Java can interoperate with Truffle-implemented
 * <em>guest languages</em> via <em>foreign objects</em> and <em>foreign functions</em>.
 * For example Java code
 * can directly access guest language methods, objects, classes,
 * and some complex data structures
 * with Java-typed accessors. In the reverse direction, guest language code can access Java objects,
 * classes, and constructors.
 *
 *
 * <h2>Contents</h2>
 *
 * <div id="toc"></div>
 * <div id="contents">
 *
 * <h2>Setup</h2>
 *
 * Download
 * <a href="http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/">GraalVM</a>,
 * which contains all the necessary pre-built components.
 * Truffle bits are <a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.oracle.truffle%22%20AND%20a%3A%22truffle-api%22">
 * uploaded to Maven central</a>. You can use them from your <em>pom.xml</em>
 * file as:
 * <pre>
&lt;dependency&gt;
    &lt;groupId&gt;<b>com.oracle.truffle</b>&lt;/groupId&gt;
    &lt;artifactId&gt;<b>truffle-api</b>&lt;/artifactId&gt;
    &lt;version&gt;0.23&lt;/version&gt; <em>&lt;!-- or any later version --&gt;</em>
&lt;/dependency&gt;
&lt;dependency&gt;
    &lt;groupId&gt;<b>com.oracle.truffle</b>&lt;/groupId&gt;
    &lt;artifactId&gt;<b>truffle-dsl-processor</b>&lt;/artifactId&gt;
    &lt;version&gt;0.23&lt;/version&gt; <em>&lt;!-- same version as above --&gt;</em>
    &lt;scope&gt;provided&lt;/scope&gt;
&lt;/dependency&gt;
 * </pre>
 *
 * <h2>Get started</h2>
 *
 * <h3>Guest language "Hello World!"</h3>
 *
 * Integrating Truffle into your Java application starts with building
 * an instance of {@link com.oracle.truffle.api.vm.PolyglotEngine}, the
 * execution environment for Truffle-implemented languages.
 * You can then use the engine to
 * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval evaluate}
 * guest language source code.
 * <p>
 * The following example creates the (literal) JavaScript
 * {@link com.oracle.truffle.api.source.Source} named <code>hello.js</code>,
 * evaluates it, and then "casts" the result to a Java string.
 * You can also create a {@link com.oracle.truffle.api.source.Source} that
 * wraps a file name or URL.
 *
 * {@codesnippet com.oracle.truffle.tutorial.HelloWorld#helloWorldInJavaScript}
 *
 * <h3>It's a polyglot world</h3>
 *
 * How to list all available languages?
 *
 * <h3>Add a language</h3>
 *
 * Put its JAR on classpath.
 *
 * <h3>Hello World in Ruby and JavaScript</h3>
 *
 * Mixing languages
 *
 * <h2>Call guest language functions from Java</h2>
 *
 * Tuffle interoperation lets Java call
 * <em>foreign functions</em> that guest language code <em>exports</em> (details vary across languages).
 * This section presents a few examples.
 *
 * <h3>Define and call a JavaScript function</h3>
 *
 * A function exported from a dynamic language becomes a callable <em>foreign function</em>
 * by giving it a Java type, for example the Java interface {@code Multiplier} in the following code.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionFromJava}
 *
 * Notes:
 * <ul>
 * <li>Evaluating the JS source returns an anonymous JS function of two arguments wrapped
 * in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value} that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * to a <em>foreign function</em> with a Java type.</li>
 * <li>Parentheses around the JS function definition keep it out of JavaScript's
 * global scope, so the Java object holds the only reference to it.</li>
 * </ul>
 *
 * <h3>Define and call a Ruby function</h3>
 * <h3>Define and call an R function</h3>
 *
 * <h2>Call multiple guest language functions with shared state from Java</h2>
 *
 * Often it is necessary to export multiple dynamic language functions that work
 * together, for example by sharing variables.  This can be done by giving
 * an exported group of functions a Java type with more than a single method,
 * for example the Java interface {@code Counter} in the following code.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptFunctionsWithSharedStateFromJava}
 *
 * Notes:
 * <ul>
 * <li>Evaluating the JS source returns an anonymous JS function of no arguments wrapped
 * in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value} (assigned
 * to {@code jsFunction}) that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#execute(Object...) executed}
 * directly, without giving it a Java type.</li>
 * <li>Executing {@code jsFunction} returns a JS dynamic object (containing two methods
 * and a shared variable) wrapped in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value}
 * that can be {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * to a <em>foreign object</em> with a Java type.</li>
 * <li>Parentheses around the JS function definition keep it out of JavaScript's
 * global scope, so the Java object holds the only reference to it.</li>
 * </ul>
 *
 * <h2>Access guest language classes from Java</h2>
 *
 * <h3>Access JavaScript classes</h3>
 *
 * The ECMAScript 6 specification adds the concept of typeless classes to JavaScript.
 * Truffle interoperation allows Java to access fields and functions of a JavaScript class,
 * for example the <em>foreign function</em> factory and class given the Java type
 * {@code Incrementor} in the following code.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#callJavaScriptClassFactoryFromJava}
 *
 * Notes:
 * <ul>
 * <li>Evaluating the JS source returns an anonymous JS function of no arguments wrapped
 * in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value} (assigned
 * to {@code jsFunction}) that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#execute(Object...) executed}
 * directly, without giving it a Java type.</li>
 * <li>Executing {@code jsFunction} returns a JS factory method for class
 * {@code JSIncrementor} that can also be executed directly.</li>
 * <li>Executing the JS factory returns a JS object
 * wrapped in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value}
 * that can be {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * to a <em>foreign object</em> with the Java type {@code Incrementor}.</li>
 * <li>Parentheses around the JS function definition keep it out of JavaScript's
 * global scope, so the Java object holds the only reference to it.</li>
 * </ul>
 *
 * <h2>Access guest language data structures from Java</h2>
 *
 * The method {@link com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) Value.as(Class)}
 * plays an essential role supporting interoperation between Java and guest language data
 * structures.
 * This section presents a few examples.
 *
 * <h3>Access a JavaScript Array</h3>
 *
 * The following example demonstrates type-safe Java foreign access
 * to members of a JavaScript array with members of a known type,
 * accessed as a Java {@link java.util.List} of objects with type given by interface {@code Point}.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptArrayWithTypedElementsFromJava}
 *
 * Notes:
 * <ul>
 * <li>Evaluating the JS source returns an anonymous JS function of no arguments wrapped
 * in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value} (assigned
 * to {@code jsFunction}) that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * to a <em>foreign function</em> with Java type {@code PointProvider}.</li>
 * <li>Invoking the foreign function (assigned to {@code pointProvider}) creates
 * a JS array, which is returned as a <em>foreign object</em>
 * with Java type {@code List<Point>}.</li>
 * <li>Parentheses around the JS function definition keep it out of JavaScript's
 * global scope, so the Java object holds the only reference to it.</li>
 * </ul>
 *
 * <h3>Access a JavaScript JSON structure</h3>
 *
 * This example demonstrates type-safe Java foreign access to a JavaScript JSON-like
 * structure, based on JSON data returned by a GitHub API.
 * The GitHub response contains a list of repository objects. Each repository has an id,
 * name, list of URLs, and a nested structure describing its owner. Java interfaces
 * {@code Repository} and {@code Owner} define the structure as Java types.
 * <p>
 * The following Java code is able to inspect a JavaScript JSON data structure
 * generated by "mock parser" in a type-safe way.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJavaScriptJSONObjectFromJava}
 *
 * Notes:
 * <ul>
 * <li>Evaluating the JS source returns an anonymous JS function of no arguments wrapped
 * in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value} (assigned
 * to {@code jsFunction}) that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#execute(Object...) executed}
 * directly, without giving it a Java type.</li>
 * <li>Executing {@code jsFunction} returns a JS mock JSON parser function
 * (assigned to {@code jsMockParser}) wrapped in a
 * {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value}, that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * to a <em>foreign function</em> with Java type {@code ParseJSON}.</li>
 * <li>Calling the Java-typed mock parser creates a JS data structure, which is
 * returned as a <em>foreign object</em> with Java type {@code List<Repository>}.
 * <li>Parentheses around the JS function definition keep it out of JavaScript's
 * global scope, so the Java object holds the only reference to it.</li>
 * </ul>
 *
 * <h2>Access Java from guest languages</h2>
 *
 * <h3>Access Java fields and methods from JavaScript</h3>
 *
 * <em>Public</em> members of Java objects to scripts can be exposed (as <em>foreign objects</em>)
 * to guest languages for example Java objects of type {@code Moment} in
 * the following example.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObject}
 *
 * Notes:
 * <ul>
 * <li>Evaluating the JS source returns an anonymous JS function of one argument wrapped
 * in a {@link com.oracle.truffle.api.vm.PolyglotEngine.Value Value} (assigned to
 * {@code jsFunction}) that can be executed directly with one argument.</li>
 * <li>When {@code jsFunction} is passed a Java argument
 * (of type {@code Moment} in the example) it is seen by JS as a
 * <em>foreign object</em> whose public fields are visible.</em>
 * <li>Executing {@code jsFunction} returns a JS number
 * that can be
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * to a Java {@link java.lang.Number}.</li>
 * <li>Parentheses around the JS function definition keep it out of JavaScript's
 * global scope, so the Java object holds the only reference to it.</li>
 * </ul>
 *
 * The multiple conversions needed to get the result in the above example
 * produce awkward code that can be
 * clarified by a different approach.  Instead of invoking the JS function
 * directly and dealing with a wrapped JS result, we can
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#as(Class) "cast"}
 * the JS function to a Java function of type {@code MomentConverter} that
 * returns the desired Java type directly, as shown in the following variation.
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConverter}
 *
 * <h3>Access Java static methods from JavaScript</h3>
 *
 * Dynamic languages can also access <b>public</b> static methods and <b>public</b>
 * constructors of Java classes, if they can get reference to them. Luckily
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#execute(java.lang.Object...) there is a support}
 * for wrapping instances of
 * {@link java.lang.Class} to appropriate objects:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createJavaScriptFactoryForJavaClass}
 *
 * In the above example the <code>Moment.class</code> is passed into the JavaScript function
 * as first argument and can be used by the dynamic language as a constructor in
 * <code>new Moment(h, m, s)</code> - that creates new instances of the Java class. Static
 * methods of the passed in <code>Moment</code> object could be invoked as well.
 *
 * <h2>Related information</h2>
 * </ul>
 * <li>{@link com.oracle.truffle.api.vm.PolyglotEngine}: execution environment for Truffle-implemented languages.</li>
 * <li><a href=
 * "{@docRoot}/com/oracle/truffle/tutorial/package-summary.html">Other Truffle Tutorials</a></li>
 * </ul>

 *
 * </div>
<script>

window.onload = function () {
    function hide(tagname, cnt, clazz) {
        var elems = document.getElementsByTagName(tagname)
        for (var i = 0; cnt > 0; i++) {
            var e = elems[i];
            if (!e) {
                break;
            }
            if (!clazz || e.getAttribute("class") === clazz) {
                e.style.display = 'none';
                cnt--;
            }
        }
    }
    hide("h1", 1);
    hide("h2", 1);
    hide("p", 1);
    hide("div", 1, "docSummary");

    var toc = "";
    var level = 0;

    document.getElementById("contents").innerHTML =
        document.getElementById("contents").innerHTML.replace(
            /<h([\d])>([^<]+)<\/h([\d])>/gi,
            function (str, openLevel, titleText, closeLevel) {
                if (openLevel != closeLevel) {
                    return str;
                }

                if (openLevel > level) {
                    toc += (new Array(openLevel - level + 1)).join("<ul>");
                } else if (openLevel < level) {
                    toc += (new Array(level - openLevel + 1)).join("</ul>");
                }

                level = parseInt(openLevel);

                var anchor = titleText.replace(/ /g, "_");
                toc += "<li><a href=\"#" + anchor + "\">" + titleText
                    + "</a></li>";

                return "<h" + openLevel + "><a name=\"" + anchor + "\">"
                    + titleText + "</a></h" + closeLevel + ">";
            }
        );

    if (level) {
        toc += (new Array(level + 1)).join("</ul>");
    }

    document.getElementById("toc").innerHTML += toc;
};
</script>
 *
 * @since 0.25
 */
package com.oracle.truffle.tutorial.embedding;
