/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.dumpapp.plugins;

import com.facebook.stetho.common.ExceptionUtil;
import com.facebook.stetho.common.Util;
import com.facebook.stetho.dumpapp.ArgsHelper;
import com.facebook.stetho.dumpapp.DumpException;
import com.facebook.stetho.dumpapp.DumpUsageException;
import com.facebook.stetho.dumpapp.DumperContext;
import com.facebook.stetho.dumpapp.DumperPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nullable;

/**
 * Yes, this intentionally crashes the app.  Useful for testing crash recovery and crash reporter
 * work flows.  Three separate exit strategies are supported; see help output for more details.
 */
public class CrashDumperPlugin implements DumperPlugin {
  private static final String NAME = "crash";

  private static final String OPTION_THROW_DEFAULT = "java.lang.Error";
  private static final String OPTION_KILL_DEFAULT = "9"; // SIGKILL
  private static final String OPTION_EXIT_DEFAULT = "0"; // EXIT_SUCCESS in C

  public CrashDumperPlugin() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void dump(DumperContext dumpContext) throws DumpException {
    Iterator<String> argsIter = dumpContext.getArgsAsList().iterator();

    String command = ArgsHelper.nextOptionalArg(argsIter, null);
    if ("throw".equals(command)) {
      doUncaughtException(argsIter);
    } else if ("kill".equals(command)) {
      doKill(dumpContext, argsIter);
    } else if ("exit".equals(command)) {
      doSystemExit(argsIter);
    } else {
      doUsage(dumpContext.getStdout());
      if (command != null) {
        throw new DumpUsageException("Unsupported command: " + command);
      }
    }
  }

  private void doUsage(PrintStream out) {
    final String cmdName = "dumpapp " + NAME;

    String usagePrefix = "Usage: " + cmdName + " ";
    String blankPrefix = "       " + cmdName + " ";
    out.println(usagePrefix + "<command> [command-options]");
    out.println(usagePrefix + "throw");
    out.println(blankPrefix + "kill");
    out.println(blankPrefix + "exit");
    out.println();
    out.println(cmdName + " throw: Throw an uncaught exception (simulates a program crash)");
    out.println("    <Throwable>: Throwable class to use (default: " + OPTION_THROW_DEFAULT + ")");
    out.println();
    out.println(cmdName + " kill: Send a signal to this process (simulates the low memory killer)");
    out.println("    <SIGNAL>: Either signal name or number to send (default: " + OPTION_KILL_DEFAULT + ")");
    out.println("              See `adb shell kill -l` for more information");
    out.println();
    out.println(cmdName + " exit: Invoke System.exit (simulates an abnormal Android exit strategy)");
    out.println("    <code>: Exit code (default: " + OPTION_EXIT_DEFAULT + ")");
  }

  private void doSystemExit(Iterator<String> argsIter) {
    String exitCodeStr = ArgsHelper.nextOptionalArg(argsIter, OPTION_EXIT_DEFAULT);
    System.exit(Integer.parseInt(exitCodeStr));
  }

  private void doKill(DumperContext dumpContext, Iterator<String> argsIter) throws DumpException {
    String signal = ArgsHelper.nextOptionalArg(argsIter, OPTION_KILL_DEFAULT);
    try {
      Process kill = new ProcessBuilder()
          .command("/system/bin/kill", "-" + signal, String.valueOf(android.os.Process.myPid()))
          .redirectErrorStream(true)
          .start();

      // Handle kill command output gracefully in the event that the signal delivered didn't
      // actually take out our process...
      try {
        InputStream in = kill.getInputStream();
        Util.copy(in, dumpContext.getStdout(), new byte[1024]);
      } finally {
        kill.destroy();
      }
    } catch (IOException e) {
      throw new DumpException("Failed to invoke kill: " + e);
    }
  }

  private void doUncaughtException(Iterator<String> argsIter) throws DumpException {
    String throwableClass = ArgsHelper.nextOptionalArg(argsIter, OPTION_THROW_DEFAULT);
    try {
      Thread crashThread = new Thread(
          new ThrowRunnable(
              (Class<? extends Throwable>)Class.forName(throwableClass)));
      crashThread.start();

      CountDownLatch impossibleLatch = new CountDownLatch(1);
      Util.awaitUninterruptibly(impossibleLatch);
    } catch (NoSuchMethodException | ClassNotFoundException | ClassCastException e) {
      throw new DumpException("Invalid supplied Throwable class: " + e);
    }
  }

  private static class ThrowRunnable implements Runnable {
    @Nullable
    private final Constructor<? extends Throwable> mDetailMessageConstructor;

    private final Constructor<? extends Throwable> mParameterlessConstructor;

    public ThrowRunnable(Class<? extends Throwable> throwableClass) throws NoSuchMethodException {
      mDetailMessageConstructor = tryGetDeclaredConstructor(throwableClass, String.class);
      mParameterlessConstructor = throwableClass.getDeclaredConstructor();
    }

    @Nullable
    private static <T> Constructor<? extends T> tryGetDeclaredConstructor(
        Class<T> clazz,
        Class<?>... parameterTypes) {
      try {
        return clazz.getDeclaredConstructor(parameterTypes);
      } catch (NoSuchMethodException e) {
        return null;
      }
    }

    @Override
    public void run() {
      try {
        Throwable t = createThrowable();
        ExceptionUtil.<Error>sneakyThrow(t);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        // Hehe, ok whatever :)
        ExceptionUtil.<Error>sneakyThrow(e);
      }
    }

    private Throwable createThrowable()
        throws IllegalAccessException, InvocationTargetException, InstantiationException {
      if (mDetailMessageConstructor != null) {
        return mDetailMessageConstructor.newInstance(
            "Uncaught exception triggered by Stetho");
      } else {
        return mParameterlessConstructor.newInstance();
      }
    }
  }
}
