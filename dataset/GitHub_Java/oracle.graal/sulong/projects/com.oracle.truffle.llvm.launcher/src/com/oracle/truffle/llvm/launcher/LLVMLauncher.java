/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class LLVMLauncher extends AbstractLanguageLauncher {

    public static void main(String[] args) {
        new LLVMLauncher().launch(args);
    }

    String[] programArgs;
    File file;
    private VersionAction versionAction = VersionAction.None;

    @Override
    protected void launch(Context.Builder contextBuilder) {
        System.exit(execute(contextBuilder));
    }

    @Override
    protected String getLanguageId() {
        return "llvm";
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        final List<String> unrecognizedOptions = new ArrayList<>();
        List<String> path = new ArrayList<>();
        List<String> libs = new ArrayList<>();

        ListIterator<String> iterator = arguments.listIterator();
        while (iterator.hasNext()) {
            String option = iterator.next();
            if (option.length() < 2 || !option.startsWith("-")) {
                iterator.previous();
                break;
            }
            // Ignore fall through
            switch (option) {
                case "--": // --
                    break;
                case "--show-version":
                    versionAction = VersionAction.PrintAndContinue;
                    break;
                case "--version":
                    versionAction = VersionAction.PrintAndExit;
                    break;
                default:
                    // options with argument
                    String optionName = option;
                    String argument;
                    int equalsIndex = option.indexOf('=');
                    if (equalsIndex > 0) {
                        argument = option.substring(equalsIndex + 1);
                        optionName = option.substring(0, equalsIndex);
                    } else if (iterator.hasNext()) {
                        argument = iterator.next();
                    } else {
                        argument = null;
                    }
                    switch (optionName) {
                        case "-L":
                            if (argument == null) {
                                throw abort("Missing argument for " + optionName);
                            }
                            path.add(argument);
                            iterator.remove();
                            if (equalsIndex < 0) {
                                iterator.previous();
                                iterator.remove();
                            }
                            break;
                        case "--lib":
                            if (argument == null) {
                                throw abort("Missing argument for " + optionName);
                            }
                            libs.add(argument);
                            iterator.remove();
                            if (equalsIndex < 0) {
                                iterator.previous();
                                iterator.remove();
                            }
                            break;
                        default:
                            // ignore unknown options
                            unrecognizedOptions.add(option);
                            if (equalsIndex < 0 && argument != null) {
                                iterator.previous();
                            }
                            break;
                    }
                    break;
            }
        }

        if (!path.isEmpty()) {
            polyglotOptions.put("llvm.libraryPath", path.stream().collect(Collectors.joining(":")));
        }
        if (!libs.isEmpty()) {
            polyglotOptions.put("llvm.libraries", libs.stream().collect(Collectors.joining(":")));
        }

        // collect the file:
        if (file == null && iterator.hasNext()) {
            file = Paths.get(iterator.next()).toFile();
        }

        // collect the program args:
        List<String> programArgumentsList = arguments.subList(iterator.nextIndex(), arguments.size());
        programArgs = programArgumentsList.toArray(new String[programArgumentsList.size()]);

        return unrecognizedOptions;
    }

    @Override
    protected void validateArguments(Map<String, String> polyglotOptions) {
        if (file == null && versionAction != VersionAction.PrintAndExit) {
            throw abort("No bitcode file provided.", 6);
        }
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        // @formatter:off
        System.out.println();
        System.out.println("Usage: lli [OPTION]... [FILE] [PROGRAM ARGS]");
        System.out.println("Run LLVM bitcode files on the GraalVM's lli.\n");
        System.out.println("Mandatory arguments to long options are mandatory for short options too.\n");
        System.out.println("Options:");
        printOption("-L <path>",         "set path where lli searches for libraries");
        printOption("--lib <libraries>", "add library (*.bc or precompiled library *.so/*.dylib)");
        printOption("--version",         "print the version and exit");
        printOption("--show-version",    "print the version and continue");
    }

    @Override
    protected void collectArguments(Set<String> args) {
        args.addAll(Arrays.asList(
                        "-L", "--lib",
                        "--version",
                        "--show-version"));
    }

    protected static void printOption(String option, String description) {
        String opt;
        if (option.length() >= 22) {
            System.out.println(String.format("%s%s", "  ", option));
            opt = "";
        } else {
            opt = option;
        }
        System.out.println(String.format("  %-22s%s", opt, description));
    }

    protected int execute(Context.Builder contextBuilder) {
        contextBuilder.arguments(getLanguageId(), programArgs);
        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());
            Value library = context.eval(Source.newBuilder(getLanguageId(), file).build());
            if (!library.canExecute()) {
                throw abort("no main function found");
            }
            return library.execute().asInt();
        } catch (PolyglotException e) {
            if (e.isExit()) {
                throw e;
            } else if (!e.isInternalError()) {
                printStackTraceSkipTrailingHost(e);
                return -1;
            } else {
                throw e;
            }
        } catch (IOException e) {
            throw abort(String.format("Error loading file '%s' (%s)", file, e.getMessage()));
        }
    }

    private static void printStackTraceSkipTrailingHost(PolyglotException e) {
        List<PolyglotException.StackFrame> stackTrace = new ArrayList<>();
        for (PolyglotException.StackFrame s : e.getPolyglotStackTrace()) {
            stackTrace.add(s);
        }
        // remove trailing host frames
        for (ListIterator<PolyglotException.StackFrame> iterator = stackTrace.listIterator(stackTrace.size()); iterator.hasPrevious();) {
            PolyglotException.StackFrame s = iterator.previous();
            if (s.isHostFrame()) {
                iterator.remove();
            } else {
                break;
            }
        }
        System.err.println(e.isHostException() ? e.asHostException().toString() : e.getMessage());
        for (PolyglotException.StackFrame s : stackTrace) {
           System.err.println("\tat " + s);
        }
    }
}
