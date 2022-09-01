// Copyright 2004-present Facebook. All Rights Reserved.

package com.facebook.stetho.dumpapp;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class GlobalOptions {
  public final Option optionHelp = new Option("h", "help", false, "Print this help");
  public final Option optionListPlugins = new Option("l", "list", false, "List available plugins");

  /**
   * Special option used to inject it into help but is otherwise not processed within the app.
   * Instead, the dumpapp shell script interprets this and figures out which port to send the
   * request to.
   */
  public final Option optionProcess = new Option("p", "process", true, "Specify target process");

  public final Options options;

  public GlobalOptions() {
    options = new Options();
    options.addOption(optionHelp);
    options.addOption(optionListPlugins);
    options.addOption(optionProcess);
  }
}
