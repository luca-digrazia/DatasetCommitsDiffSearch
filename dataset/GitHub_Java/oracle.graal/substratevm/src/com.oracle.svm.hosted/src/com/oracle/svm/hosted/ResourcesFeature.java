/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.svm.hosted;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;

@AutomaticFeature
public final class ResourcesFeature implements Feature {

    public static class Options {
        @Option(help = "Regexp to match names of resources to be included in the image.", type = OptionType.User)//
        public static final HostedOptionKey<String[]> IncludeResources = new HostedOptionKey<String[]>(new String[0]) {
            @Override
            public String getDelimiterRegex() {
                /*
                 * Since IncludeResources takes regular expressions it's safer to disallow passing
                 * more than one regex with a single IncludeResources option. Note that it's still
                 * possible pass multiple IncludeResources regular expressions by passing each as
                 * its own IncludeResources option. E.g.
                 * @formatter:off
                 * -H:IncludeResources=/nobel/prizes.json -H:IncludeResources=/fields/prizes.json
                 * @formatter:on
                 */
                return null;
            }
        };
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (String regExp : Options.IncludeResources.getValue()) {
            if (regExp.length() == 0) {
                return;
            }

            Pattern pattern = Pattern.compile(regExp);

            final Set<File> todo = new HashSet<>();
            // Checkstyle: stop
            final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) contextClassLoader).getURLs()) {
                    try {
                        final File file = new File(url.toURI());
                        todo.add(file);
                    } catch (URISyntaxException | IllegalArgumentException e) {
                        throw UserError.abort("Unable to handle imagecp element '" + url.toExternalForm() + "'. Make sure that all imagecp entries are either directories or valid jar files.");
                    }
                }
            }
            // Checkstyle: resume
            for (File element : todo) {
                try {
                    DebugContext debugContext = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getDebugContext();
                    if (element.isDirectory()) {
                        scanDirectory(debugContext, element, "", pattern);
                    } else {
                        scanJar(debugContext, element, pattern);
                    }
                } catch (IOException ex) {
                    throw UserError.abort("Unable to handle classpath element '" + element + "'. Make sure that all classpath entries are either directories or valid jar files.");
                }
            }
        }
    }

    @SuppressWarnings("try")
    private void scanDirectory(DebugContext debugContext, File f, String relativePath, Pattern... patterns) throws IOException {
        if (f.isDirectory()) {
            for (File ch : f.listFiles()) {
                scanDirectory(debugContext, ch, relativePath + "/" + ch.getName(), patterns);
            }
        } else {
            if (matches(patterns, relativePath)) {
                try (FileInputStream is = new FileInputStream(f)) {
                    try (DebugContext.Scope s = debugContext.scope("registerResource")) {
                        debugContext.log("ResourcesFeature: registerResource: " + relativePath.substring(1));
                    }
                    Resources.registerResource(relativePath.substring(1), is);
                }
            }
        }
    }

    @SuppressWarnings("try")
    private static void scanJar(DebugContext debugContext, File element, Pattern... patterns) throws IOException {
        JarFile jf = new JarFile(element);
        Enumeration<JarEntry> en = jf.entries();
        while (en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            if (e.getName().endsWith("/")) {
                continue;
            }
            if (matches(patterns, e.getName())) {
                try (InputStream is = jf.getInputStream(e)) {
                    try (DebugContext.Scope s = debugContext.scope("registerResource")) {
                        debugContext.log("ResourcesFeature: registerResource: " + e.getName());
                    }
                    Resources.registerResource(e.getName(), is);
                }
            }
        }
    }

    private static boolean matches(Pattern[] patterns, String relativePath) {
        for (Pattern p : patterns) {
            if (p.matcher(relativePath).matches()) {
                return true;
            }
        }
        return false;
    }
}
