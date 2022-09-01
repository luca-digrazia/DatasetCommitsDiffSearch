/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.server;

import javax.annotation.concurrent.ThreadSafe;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@ThreadSafe
public class LeakyBufferedInputStream extends BufferedInputStream {
  private boolean mLeaked;
  private boolean mMarked;

  public LeakyBufferedInputStream(InputStream in, int bufSize) {
    super(in, bufSize);
  }

  @Override
  public synchronized void mark(int readlimit) {
    throwIfLeaked();
    mMarked = true;
    super.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    throwIfLeaked();
    mMarked = false;
    super.reset();
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  public synchronized InputStream leakBufferAndStream() {
    throwIfLeaked();
    throwIfMarked();
    mLeaked = true;
    return new CompositeInputStream(
        new InputStream[] {
            new ByteArrayInputStream(clearBufferLocked()),
            in
        });
  }

  private byte[] clearBufferLocked() {
    byte[] leaked = new byte[count - pos];
    System.arraycopy(buf, pos, leaked, 0, leaked.length);
    pos = 0;
    count = 0;
    return leaked;
  }

  private void throwIfLeaked() {
    if (mLeaked) {
      throw new IllegalStateException();
    }
  }

  private void throwIfMarked() {
    if (mMarked) {
      throw new IllegalStateException();
    }
  }
}
