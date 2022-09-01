/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.dumpapp;

class UnexpectedFrameException extends DumpappFramingException {
  public UnexpectedFrameException(byte expected, byte got) {
    super("Expected '" + expected + "', got: '" + got + "'");
  }
}
