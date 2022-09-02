/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

// Checkstyle: stop

import java.awt.event.WindowEvent;

import javax.swing.*;

public class FallbackSwingTest {
    private static JFrame frame = new JFrame();

    private static void showFrame() {
        frame.getContentPane().add(new JButton(FallbackSwingTest.class.getSimpleName()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void closeFrame() {
        WindowEvent windowCloseEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
        frame.dispatchEvent(windowCloseEvent);
    }

    public static void main(String[] args) throws InterruptedException {
        javax.swing.SwingUtilities.invokeLater(FallbackSwingTest::showFrame);
        Thread.sleep(3_000);
        javax.swing.SwingUtilities.invokeLater(FallbackSwingTest::closeFrame);
    }
}
