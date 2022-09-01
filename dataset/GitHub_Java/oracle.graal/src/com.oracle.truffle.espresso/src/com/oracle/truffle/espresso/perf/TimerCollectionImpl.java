/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.perf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.TruffleLogger;

class TimerCollectionImpl extends TimerCollection {
    Map<String, DebugTimer.DebugTimerImpl> mapping = new ConcurrentHashMap<>();

    @Override
    DebugCloseable scope(DebugTimer timer) {
        String key = timer.name;
        DebugTimer.DebugTimerImpl impl = mapping.get(key);
        if (impl == null) {
            DebugTimer.DebugTimerImpl created = DebugTimer.spawn();
            impl = mapping.putIfAbsent(key, created);
            if (impl == null) {
                impl = created;
            }
        }
        return DebugTimer.AutoTimer.scope(impl);
    }

    @Override
    public void report(TruffleLogger logger) {
        for (Map.Entry<String, DebugTimer.DebugTimerImpl> entry : mapping.entrySet()) {
            entry.getValue().report(logger, entry.getKey());
        }
    }

    static final class NoTimer extends TimerCollection {
        @Override
        DebugCloseable scope(DebugTimer timer) {
            return DebugTimer.AutoTimer.NO_TIMER;
        }

        @Override
        public void report(TruffleLogger logger) {
        }
    }
}
