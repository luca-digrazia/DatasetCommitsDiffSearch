/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************/
package com.oracle.truffle.espresso;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.MainLauncherRootNode;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.classfile.SymbolTable;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.Utils;
import com.oracle.truffle.espresso.types.SignatureDescriptors;
import com.oracle.truffle.espresso.types.TypeDescriptors;

@TruffleLanguage.Registration(name = "Java", version = "1.8", mimeType = EspressoLanguage.MIME_TYPE)
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> {

    public static final OptionKey<String> CLASSPATH = new OptionKey<>("");
    public static final String CLASSPATH_NAME = "java.classpath";
    public static final String CLASSPATH_HELP = "A " + File.pathSeparator + " separated list of directories, JAR archives, and ZIP archives to search for class files.";

    public static final String BOOT_CLASSPATH_NAME = "boot.classpath";
    public static final OptionKey<String> BOOT_CLASSPATH = new OptionKey<>("");

    public static final String MIME_TYPE = "application/x-java";
    public static final String FILE_EXTENSION = ".class";

    public static final String ESPRESSO_SOURCE_FILE_KEY = "EspressoSourceFile";

    private final SymbolTable symbols = new SymbolTable();

    private final TypeDescriptors typeDescriptors = new TypeDescriptors();
    private final SignatureDescriptors signatureDescriptors = new SignatureDescriptors(typeDescriptors);

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        ArrayList<OptionDescriptor> options = new ArrayList<>();
        options.add(OptionDescriptor.newBuilder(CLASSPATH, CLASSPATH_NAME).help(CLASSPATH_HELP).category(OptionCategory.USER).build());
        // options.add(OptionDescriptor.newBuilder(BOOT_CLASSPATH, BOOT_CLASSPATH_NAME).category(OptionCategory.EXPERT).build());
        return OptionDescriptors.create(options);
    }

    static enum LaunchMode {               // cf. sun.launcher.LauncherHelper
        LM_UNKNOWN,
        LM_CLASS,
        LM_JAR,
        // LM_MODULE,
        // LM_SOURCE
    }

    @Override
    protected EspressoContext createContext(final TruffleLanguage.Env env) {
        OptionValues options = env.getOptions();
        InputStream in = env.in();
        OutputStream out = env.out();
        EspressoContext context = new EspressoContext(env, this);

        context.setMainArguments(env.getApplicationArguments());

        String classpathValue = options.get(CLASSPATH);

        // classpath provenance order:
        // (1) the -cp command line option
        if (classpathValue == null) {
            // (2) the property java.class.path
            classpathValue = System.getProperty("java.class.path");
            if (classpathValue == null) {
                // (3) the environment variable CLASSPATH
                classpathValue = System.getenv("CLASSPATH");
                if (classpathValue == null) {
                    // (4) the current working directory only
                    classpathValue = ".";
                }
            }
        }

        context.setClasspath(new Classpath(classpathValue));

        // TODO(peterssen): Investigate boot classpath whereabouts/sources.
        String bootClasspathValue = System.getProperty("sun.boot.class.path");
        assert bootClasspathValue != null;

        context.setBootClasspath(new Classpath(bootClasspathValue));

        Object sourceFile = env.getConfig().get(EspressoLanguage.ESPRESSO_SOURCE_FILE_KEY);
        if (sourceFile != null) {
            context.setMainSourceFile((Source) sourceFile);
        }

        return context;
    }

    @Override
    protected void initializeContext(final EspressoContext context) throws Exception {
        context.initializeContext();
    }

    @Override
    protected void disposeContext(final EspressoContext context) {
        // TODO: call destructors on disposeContext?
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        final Source source = request.getSource();
        final EspressoContext context = findContext();

        assert context.isInitialized();

        Object classLoader = null;
        String className = source.getName();

        String classDescriptor = MetaUtil.toInternalName(className);

        // TODO(peterssen): Do not use system BCL.

        assert context.getAppClassLoader() != null && context.getAppClassLoader() != StaticObject.NULL;

        Klass mainClass = loadMainClass(context, LaunchMode.LM_CLASS, className).getMirror();

        EspressoError.guarantee(mainClass != null, "Error: Could not find or load main class %s", className);

        MethodInfo mainMethod = mainClass.findDeclaredMethod("main", void.class, String[].class);

        EspressoError.guarantee(mainMethod != null,
                        "Error: Main method not found in class %s, please define the main method as:\n" +
                                        "            public static void main(String[] args)\n",
                        className);

        assert mainMethod.isPublic() && mainMethod.isStatic();
        return Truffle.getRuntime().createCallTarget(new MainLauncherRootNode(this, mainMethod));
    }

    /*
     * Loads a class and verifies that the main class is present and it is ok to call it for more
     * details refer to the java implementation.
     */
    private StaticObjectClass loadMainClass(EspressoContext context, LaunchMode mode, String name) {
        Klass launcherHelperKlass = context.getRegistries().resolve(context.getTypeDescriptors().make("Lsun/launcher/LauncherHelper;"), null);
        MethodInfo checkAndLoadMain = launcherHelperKlass.findMethod("checkAndLoadMain",
                        context.getSignatureDescriptors().make("(ZILjava/lang/String;)Ljava/lang/Class;"));
        StaticObjectClass mainClass = (StaticObjectClass) checkAndLoadMain.getCallTarget().call(true, mode.ordinal(), context.getMeta().toGuest(name));
        return mainClass;
    }

    @Override
    protected boolean isObjectOfLanguage(final Object object) {
        return false;
    }

    public EspressoContext findContext() {
        CompilerAsserts.neverPartOfCompilation();
        return super.getContextReference().get();
    }

    @SuppressWarnings("unused")
    private static String getClasspathString() {
        final ClassLoader cl = ClassLoader.getSystemClassLoader();
        final URL[] urls = ((URLClassLoader) cl).getURLs();
        final StringBuilder b = new StringBuilder();

        for (final URL url : urls) {
            b.append(url.getFile()).append('\n');
        }

        return b.toString();
    }

    public SymbolTable getSymbolTable() {
        return symbols;
    }

    public TypeDescriptors getTypeDescriptors() {
        return typeDescriptors;
    }

    public SignatureDescriptors getSignatureDescriptors() {
        return signatureDescriptors;
    }
}
