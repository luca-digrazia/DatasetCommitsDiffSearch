// Copyright 2004-present Facebook. All Rights Reserved.

package com.facebook.stetho.sample;

import java.io.PrintStream;
import java.util.Iterator;

import android.text.TextUtils;

import com.facebook.stetho.dumpapp.DumpException;
import com.facebook.stetho.dumpapp.DumpUsageException;
import com.facebook.stetho.dumpapp.DumperContext;
import com.facebook.stetho.dumpapp.DumperPlugin;

public class HelloWorldDumperPlugin implements DumperPlugin {
  private static final String NAME = "hello";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void dump(DumperContext dumpContext) throws DumpException {
    PrintStream writer = dumpContext.getStdout();
    Iterator<String> args = dumpContext.getArgsAsList().iterator();

    String helloToWhom = args.hasNext() ? args.next() : null;
    if (helloToWhom != null) {
      doHello(writer, helloToWhom);
    } else {
      doUsage(writer);
    }
  }

  private void doHello(PrintStream writer, String name) throws DumpUsageException {
    if (TextUtils.isEmpty(name)) {
      // This will print an error to the dumpapp user and cause a non-zero exit of the
      // script.
      throw new DumpUsageException("Name is empty");
    }

    writer.println("Hello " + name + "!");
  }

  private void doUsage(PrintStream writer) {
    writer.println("Usage: dumpapp " + NAME + " <name>");
  }
}
