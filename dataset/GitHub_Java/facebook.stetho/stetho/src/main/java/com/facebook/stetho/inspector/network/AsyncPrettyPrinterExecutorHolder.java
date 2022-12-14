/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.network;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A holder class for the executor service used for pretty printing related tasks
 */
class AsyncPrettyPrinterExecutorHolder {
  public static final ExecutorService sExecutorService = Executors.newCachedThreadPool();
}
