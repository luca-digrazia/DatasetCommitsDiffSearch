/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import static org.graalvm.component.installer.Commands.DO_NOT_PROCESS_OPTIONS;

public class SimpleGetopt {
    private LinkedList<String> parameters;
    private final Map<String, String> globalOptions;
    private final Map<String, Map<String, String>> commandOptions = new HashMap<>();

    private final Map<String, String> optValues = new HashMap<>();
    private final LinkedList<String> positionalParameters = new LinkedList<>();

    private String command;

    public SimpleGetopt(Map<String, String> globalOptions) {
        this.globalOptions = globalOptions;
    }

    public void setParameters(LinkedList<String> parameters) {
        this.parameters = parameters;
    }

    // overridable by tests
    void err(String messageKey, Object... args) {
        ComponentInstaller.err(messageKey, args);
    }

    private String findCommand(String cmdString) {
        String cmd = cmdString;
        if (cmd.isEmpty()) {
            err("ERROR_MissingCommand"); // NOI18N
        }
        String selCommand = null;
        for (String s : commandOptions.keySet()) {
            if (s.startsWith(cmdString)) {
                if (selCommand != null) {
                    err("ERROR_AmbiguousCommand", cmdString, selCommand, s);
                }
                selCommand = s;
                if (s.length() == cmdString.length()) {
                    break;
                }
            }
        }
        if (selCommand == null) {
            err("ERROR_UnknownCommand", cmdString); // NOI18N
        }
        command = selCommand;
        return command;
    }

    public void process() {
        while (true) {
            String p = parameters.peek();
            if (p == null) {
                break;
            }
            if (!p.startsWith("-")) { // NOI18N
                if (command == null) {
                    findCommand(parameters.poll());
                    Map<String, String> cOpts = commandOptions.get(command);
                    for (String s : optValues.keySet()) {
                        if ("X".equals(cOpts.get(s))) {
                            err("ERROR_UnsupportedOption", s, command); // NOI18N
                        }
                    }
                    if (cOpts.containsKey(DO_NOT_PROCESS_OPTIONS)) { // NOI18N
                        // terminate all processing, the rest are positional params
                        positionalParameters.addAll(parameters);
                        break;
                    }
                } else {
                    positionalParameters.add(parameters.poll());
                }
                continue;
            } else if (p.length() == 1 || "--".equals(p)) {
                // dash alone, or double-dash terminates option search.
                parameters.poll();
                positionalParameters.addAll(parameters);
                break;
            }
            String param = parameters.poll();
            boolean nextParam = p.startsWith("--"); // NOI18N
            String optName;
            int optCharIndex = 1;
            while (optCharIndex < param.length()) {
                if (nextParam) {
                    optName = param.substring(2);
                    param = processOptSpec(optName, optCharIndex, param, nextParam);
                    break;
                } else {
                    optName = param.substring(optCharIndex, optCharIndex + 1);
                }
                optCharIndex += optName.length();
                for (int i = 0; i < optName.length(); i++) {
                    String o = String.valueOf(optName.charAt(i));
                    param = processOptSpec(o, optCharIndex, param, nextParam);
                }
            }
        }
    }

    private String processOptSpec(String o, int optCharIndex, String inParam, boolean nextParam) {
        String param = inParam;
        String optSpec = null;
        if (command != null) {
            Map<String, String> cmdSpec = commandOptions.get(command);
            optSpec = cmdSpec.get(o);
        }
        if (optSpec == null) {
            optSpec = globalOptions.get(o);
        }
        if (optSpec == null) {
            if (command == null) {
                err("ERROR_UnsupportedGlobalOption", o); // NOI18N
            }
            Map<String, String> cmdSpec = commandOptions.get(command);
            if (cmdSpec.isEmpty()) {
                err("ERROR_CommandWithNoOptions", command); // NOI18N
            }
            err("ERROR_UnsupportedOption", o, command); // NOI18N
        }
        // no support for parametrized options now
        String optVal = "";
        switch (optSpec) {
            case "s":
                if (nextParam) {
                    optVal = parameters.poll();
                    if (optVal == null) {
                        err("ERROR_OptionNeedsParameter", o, command); // NOI18N
                    }
                } else {
                    if (optCharIndex < param.length()) {
                        optVal = param.substring(optCharIndex);
                        param = "";
                    } else if (parameters.isEmpty()) {
                        err("ERROR_OptionNeedsParameter", o, command); // NOI18N
                    } else {
                        optVal = parameters.poll();
                    }
                }
                break;
            case "X":
                err("ERROR_UnsupportedOption", o, command); // NOI18N
                break;
            case "":
                break;
        }
        optValues.put(o, optVal); // NOI18N
        return param;
    }

    public String getCommand() {
        return command;
    }

    public void addCommandOptions(String commandName, Map<String, String> optSpec) {
        commandOptions.put(commandName, optSpec);
    }

    public Map<String, String> getOptValues() {
        return optValues;
    }

    public LinkedList<String> getPositionalParameters() {
        return positionalParameters;
    }
}
