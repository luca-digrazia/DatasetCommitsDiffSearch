/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.wasm.constants.Debug;

public interface WasmTracing {
    TruffleLogger logger = TruffleLogger.getLogger("wasm");

    default void trace(String message) {
        if (Debug.TRACING) {
            logger.finest(message);
        }
    }

    default void trace(String message, int value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    default void trace(String message, long value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    default void trace(String message, float value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    default void trace(String message, double value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    default void trace(String message, Object value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    default void trace(String message, int v0, int v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, long v0, long v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, float v0, float v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, double v0, double v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, int v0, float v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, int v0, double v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, Object v0, int v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, Object v0, Object v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    default void trace(String message, int v0, int v1, int v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, int v0, long v1, long v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, long v0, long v1, long v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, float v0, float v1, float v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, long v0, int v1, float v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, long v0, int v1, int v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, double v0, double v1, double v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, long v0, long v1, double v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    default void trace(String message, int v0, int v1, int v2, int v3) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2, v3));
        }
    }

    default void trace(String message, int v0, long v1, long v2, long v3) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2, v3));
        }
    }

    default void trace(String message, long v0, long v1, long v2, long v3) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2, v3));
        }
    }
}
