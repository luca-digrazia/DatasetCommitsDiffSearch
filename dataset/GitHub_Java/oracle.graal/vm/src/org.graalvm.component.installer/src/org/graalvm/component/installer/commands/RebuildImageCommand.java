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
package org.graalvm.component.installer.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import static org.graalvm.component.installer.Commands.DO_NOT_PROCESS_OPTIONS;

public class RebuildImageCommand implements InstallerCommand {
    private static final Map<String, String> OPTIONS = new HashMap<>();

    private Feedback feedback;
    private CommandInput input;

    static {
        OPTIONS.put(DO_NOT_PROCESS_OPTIONS, "");
    }

    @Override
    public Map<String, String> supportedOptions() {
        return OPTIONS;
    }

    @Override
    public void init(CommandInput input, Feedback feedback) {
        this.feedback = feedback;
        this.input = input;
    }

    final class OutputRewriter implements Runnable {
        private final String processName;
        private final String substProcessName;
        private final InputStream output;
        private volatile IOException terminated;

        OutputRewriter(InputStream output, String processName, String substProcessName) {
            this.output = output;
            this.processName = processName;
            this.substProcessName = substProcessName;
        }

        @Override
        public void run() {
            try (BufferedReader bre = new BufferedReader(new InputStreamReader(output))) {
                String line;
                while ((line = bre.readLine()) != null) {
                    int i = line.indexOf(processName);
                    if (i != -1) {
                        line = line.substring(0, i) + substProcessName +
                                        line.substring(i + processName.length());
                    }
                    System.out.println(line);
                    System.out.flush();
                }
            } catch (IOException ex) {
                terminated = ex;
            }
        }
    }

    @Override
    public int execute() throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> commandLine = new ArrayList<>();
        Path toolPath = input.getGraalHomePath().resolve(feedback.l10n("REBUILD_ToolRelativePath"));
        String procName = toolPath.toAbsolutePath().toString();
        commandLine.add(procName);
        while (input.hasParameter()) {
            commandLine.add(input.nextParameter());
        }

        pb.command(commandLine);
        pb.directory(input.getGraalHomePath().toFile());
        pb.redirectInput(Redirect.INHERIT);
        pb.redirectError(Redirect.INHERIT);
        try {
            int exitCode;
            Process p = pb.start();

            OutputRewriter rw = new OutputRewriter(p.getInputStream(), procName,
                            feedback.l10n("REBUILD_RewriteRebuildToolName")); // NOI18N
            Thread rwThread = new Thread(rw);
            rwThread.start();
            exitCode = p.waitFor();
            rwThread.join(1000);
            if (rw.terminated != null) {
                feedback.error("REBUILD_ImageToolInterrupted", rw.terminated);
            }
            return exitCode;
        } catch (InterruptedException ex) {
            feedback.error("REBUILD_ImageToolInterrupted", ex);
            return 1;
        }
    }

}
