/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

import static sun.security.tools.PathList.appendPath;

public class EspressoLauncher extends AbstractLanguageLauncher {
    public static void main(String[] args) {
        new EspressoLauncher().launch(args);
    }

    private String classpath = null;
    private final ArrayList<String> mainClassArgs = new ArrayList<>();
    private String mainClassName = null;
    private VersionAction versionAction = VersionAction.None;

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        String jarFileName = null;
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            switch (arg) {
                case "-cp":
                case "-classpath":
                    i += 1;
                    if (i < arguments.size()) {
                        classpath = arguments.get(i);
                    } else {
                        throw abort("Error: " + arg + " requires class path specification");
                    }
                    break;
                case "-jar":
                    i += 1;
                    if (i < arguments.size()) {
                        jarFileName = arguments.get(i);
                    } else {
                        throw abort("Error: " + arg + " requires jar file specification");
                    }
                    break;
                case "-version":
                    versionAction = VersionAction.PrintAndExit;
                    break;
                case "-showversion":
                    versionAction = VersionAction.PrintAndContinue;
                    break;

                case "-ea":
                case "-enableassertions":
                    polyglotOptions.put("java.EnableAssertions", "true");
                    break;

                case "-esa":
                case "-enablesystemassertions":
                    polyglotOptions.put("java.EnableSystemAssertions", "true");
                    break;

                case "-?":
                case "-help":
                    unrecognized.add("--help");
                    break;
                default:
                    if (arg.startsWith("-Xbootclasspath:")) {
                        polyglotOptions.remove("java.BootClasspathPrepend");
                        polyglotOptions.remove("java.BootClasspathAppend");
                        polyglotOptions.put("java.BootClasspath", arg.substring("-Xbootclasspath:".length()));
                    } else if (arg.startsWith("-Xbootclasspath/a:")) {

                        polyglotOptions.put("java.BootClasspathAppend", appendPath(polyglotOptions.get("java.BootClasspathAppend"), arg.substring("-Xbootclasspath/a:".length())));
                    } else if (arg.startsWith("-Xbootclasspath/p:")) {
                        polyglotOptions.put("java.BootClasspathPrepend", prependPath(arg.substring("-Xbootclasspath/p:".length()), polyglotOptions.get("java.BootClasspathPrepend")));
                    } else if (arg.startsWith("-Xverify:")) {
                        String mode = arg.substring("-Xverify:".length());
                        polyglotOptions.put("java.Verify", mode);
                    } else
                    // -Dsystem.property=value
                    if (arg.startsWith("-D")) {
                        String key = arg.substring("-D".length());
                        int splitAt = key.indexOf("=");
                        String value = "";
                        if (splitAt >= 0) {
                            value = key.substring(splitAt + 1);
                            key = key.substring(0, splitAt);
                        }

                        switch (key) {
                            case "java.library.path":
                                polyglotOptions.put("java.JavaLibraryPath", value);
                                break;
                            case "java.class.path":
                                classpath = value;
                                break;
                            case "java.ext.dirs":
                                polyglotOptions.put("java.ExtDirs", value);
                                break;
                            case "sun.boot.class.path":
                                polyglotOptions.put("java.BootClasspath", value);
                                break;
                            case "sun.boot.library.path":
                                polyglotOptions.put("java.BootLibraryPath", value);
                                break;
                        }

                        polyglotOptions.put("java.Properties." + key, value);
                    } else if (!arg.startsWith("-")) {
                        mainClassName = arg;
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
            }
            if (mainClassName != null || jarFileName != null) {
                if (jarFileName != null) {
                    // Overwrite class path. For compatibility with the standard java launcher,
                    // this is done silently.
                    classpath = jarFileName;

                    mainClassName = getMainClassName(jarFileName);
                }
                i += 1;
                if (i < arguments.size()) {
                    mainClassArgs.addAll(arguments.subList(i, arguments.size()));
                }
                break;
            }
        }

        // classpath provenance order:
        // (1) the -cp/-classpath command line option
        if (classpath == null) {
            // (2) the property java.class.path
            classpath = polyglotOptions.get("java.Properties.java.class.path");
            if (classpath == null) {
                // (3) the environment variable CLASSPATH
                classpath = System.getenv("CLASSPATH");
                if (classpath == null) {
                    // (4) the current working directory only
                    classpath = ".";
                }
            }
        }

        polyglotOptions.put("java.Classpath", classpath);

        return unrecognized;
    }

    private static String usage() {
        String nl = System.lineSeparator();
        // @formatter:off
        return "Usage: java [-options] <mainclass> [args...]" + nl +
               "           (to execute a class)" + nl +
               "   or  java [-options] -jar jarfile [args...]" + nl +
               "           (to execute a jar file)" + nl +
               "where options include:" + nl +
               "    -cp <class search path of directories and zip/jar files>" + nl +
               "    -classpath <class search path of directories and zip/jar files>" + nl +
               "                  A " + File.pathSeparator + " separated list of directories, JAR archives," + nl +
               "                  and ZIP archives to search for class files." + nl +
               "    -D<name>=<value>" + nl +
               "                  set a system property" + nl +
               "    -version      print product version and exit" + nl +
               "    -showversion  print product version and continue" + nl +
               "    -ea | -enableassertions" + nl +
               "                  enable assertions" + nl +
               "    -esa | -enablesystemassertions" + nl +
               "                  enable system assertions" + nl +
               "    -? -help      print this help message";
        // @formatter:on
    }

    private static void print(String string) {
        System.out.println(string);
    }

    /**
     * Gets the values of the attribute {@code name} from the manifest in {@code jarFile}.
     *
     * @return the value of the attribute of null if not found
     * @throws IOException if error reading jar file
     */
    public static String getManifestValue(JarFile jarFile, String name) throws IOException {
        final Manifest manifest = jarFile.getManifest();
        if (manifest == null) {
            return null;
        }
        return manifest.getMainAttributes().getValue(name);
    }

    /**
     * Finds the main class name from the command line either explicitly or via the jar file.
     */
    private String getMainClassName(String jarFileName) {
        try {
            final JarFile jarFile = new JarFile(jarFileName);
            String value = getManifestValue(jarFile, "Main-Class");
            if (value == null) {
                throw abort("Could not find main class in jarfile: " + jarFileName);
            }
            return value;
        } catch (IOException e) {
            throw abort("Could not find main class in jarfile: " + jarFileName + " [cause: " + e + "]");
        }
    }

    @Override
    protected void launch(Builder contextBuilder) {
        contextBuilder.arguments(getLanguageId(), mainClassArgs.toArray(new String[0])).in(System.in).out(System.out).err(System.err);

        if (classpath != null) {
            contextBuilder.option("java.Classpath", classpath);
        }

        contextBuilder.allowCreateThread(true);

        int rc = 1;
        try (Context context = contextBuilder.build()) {

            runVersionAction(versionAction, context.getEngine());

            if (mainClassName == null) {
                throw abort(usage());
            }

            try {
                eval(context);
                rc = 0;
            } catch (PolyglotException e) {
                if (!e.isExit()) {
                    e.printStackTrace();
                } else {
                    rc = e.getExitStatus();
                }
            } catch (NoSuchFileException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            rc = 1;
            e.printStackTrace();
        }
        throw exit(rc);
    }

    private void eval(Context context) throws IOException {
        Source src = Source.newBuilder(getLanguageId(), "", mainClassName).build();
        context.eval(src);
    }

    @Override
    protected String getLanguageId() {
        return "java";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        print(usage());
    }

    @Override
    protected void collectArguments(Set<String> options) {
        // This list of arguments is used when we are launched through the Polyglot
        // launcher
        options.add("-cp");
        options.add("-classpath");
        options.add("-version");
        options.add("-showversion");
        options.add("-ea");
        options.add("-enableassertions");
        options.add("-esa");
        options.add("-enablesystemassertions");
        options.add("-?");
        options.add("-help");
    }

    private static String appendPanth(String paths, String toAppend) {
        if (paths != null && paths.length() != 0) {
            return toAppend != null && toAppend.length() != 0 ? paths + File.pathSeparator + toAppend : paths;
        } else {
            return toAppend;
        }
    }

    private static String prependPath(String toPrepend, String paths) {
        if (paths != null && paths.length() != 0) {
            return toPrepend != null && toPrepend.length() != 0 ? toPrepend + File.pathSeparator + paths : paths;
        } else {
            return toPrepend;
        }
    }
}
