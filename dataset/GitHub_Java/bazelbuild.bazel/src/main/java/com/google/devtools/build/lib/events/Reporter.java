// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.events;

import com.google.devtools.build.lib.util.io.OutErr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The reporter is the primary means of reporting events such as errors,
 * warnings, progress information and diagnostic information to the user.  It
 * is not intended as a logging mechanism for developer-only messages; use a
 * Logger for that.
 *
 * <p>The reporter instance is consumed by the build system, and passes events to
 * {@link EventHandler} instances. These handlers are registered via {@link
 * #addHandler(EventHandler)}.
 *
 * <p>Thread-safe: calls to {@code #report} may be made on any thread.
 * Handlers may be run in an arbitary thread (but right now, they will not be
 * run concurrently).
 */
public final class Reporter implements EventHandler, ExceptionListener {

  private final List<EventHandler> handlers = new ArrayList<>();

  /** An OutErr that sends all of its output to this Reporter.
   * Each write will (when flushed) get mapped to an EventKind.STDOUT or EventKind.STDERR event.
   */
  private final OutErr outErrToReporter = outErrForReporter(this);
  private volatile OutputFilter outputFilter = OutputFilter.OUTPUT_EVERYTHING;
  private EventHandler ansiAllowingHandler;
  private EventHandler ansiStrippingHandler;
  private boolean ansiAllowingHandlerRegistered;

  public Reporter() {}

  public static OutErr outErrForReporter(EventHandler rep) {
    return OutErr.create(
        // We don't use BufferedOutputStream here, because in general the Blaze
        // code base assumes that the output streams are not buffered.
        new ReporterStream(rep, EventKind.STDOUT),
        new ReporterStream(rep, EventKind.STDERR));
  }

  /**
   * A copy constructor, to make it convenient to replicate a reporter
   * config for temporary configuration changes.
   */
  public Reporter(Reporter template) {
    handlers.addAll(template.handlers);
  }

  /**
   * Constructor which configures a reporter with the specified handlers.
   */
  public Reporter(EventHandler... handlers) {
    for (EventHandler handler: handlers) {
      addHandler(handler);
    }
  }

  /**
   * Returns an OutErr that sends all of its output to this Reporter.
   * Each write to the OutErr will cause an EventKind.STDOUT or EventKind.STDERR event.
   */
  public OutErr getOutErr() {
    return outErrToReporter;
  }

  /**
   * Adds a handler to this reporter.
   */
  public synchronized void addHandler(EventHandler handler) {
    handlers.add(handler);
  }

  /**
   * Removes handler from this reporter.
   */
  public synchronized void removeHandler(EventHandler handler) {
     handlers.remove(handler);
  }

  /**
   * This method is called by the build system to report an event.
   */
  @Override
  public synchronized void handle(Event e) {
    if (e.getKind() != EventKind.ERROR && e.getTag() != null && !showOutput(e.getTag())) {
      return;
    }
    for (EventHandler handler : handlers) {
      handler.handle(e);
    }
  }

  /**
   * Reports the start of a particular task.
   * Is a wrapper around report() with event kind START.
   * Should always be matched by a corresponding call to finishTask()
   * with the same message, except that the leading percentage
   * progress indicator (if any) in the message may differ.
   */
  public void startTask(Location location, String message) {
    handle(new Event(EventKind.START, location, message));
  }

  /**
   * Reports the start of a particular task.
   * Is a wrapper around report() with event kind FINISH.
   * Should always be matched by a corresponding call to startTask()
   * with the same message, except that the leading percentage
   * progress indicator (if any) in the message may differ.
   */
  public void finishTask(Location location, String message) {
    handle(new Event(EventKind.FINISH, location, message));
  }

  @Override
  public void error(Location location, String message, Throwable error) {
    handle(new Event(EventKind.ERROR, location, message));
    error.printStackTrace(new PrintStream(getOutErr().getErrorStream()));
  }

  /**
   * Returns true iff the given tag matches the output filter.
   */
  public boolean showOutput(String tag) {
    return outputFilter.showOutput(tag);
  }

  public void setOutputFilter(OutputFilter outputFilter) {
    this.outputFilter = outputFilter;
  }

  /**
   * Registers an ANSI-control-code-allowing EventHandler with an ANSI-stripping EventHandler
   * that is already registered with the reporter.  The ANSI-stripping handler can then be replaced
   * with the ANSI-allowing handler by calling {@code #switchToAnsiAllowingHandler} which
   * calls {@code removeHandler} for the ANSI-stripping handler and then {@code addHandler} for the
   * ANSI-allowing handler.
   */
  public synchronized void registerAnsiAllowingHandler(
      EventHandler ansiStrippingHandler,
      EventHandler ansiAllowingHandler) {
    this.ansiAllowingHandler = ansiAllowingHandler;
    this.ansiStrippingHandler = ansiStrippingHandler;
    ansiAllowingHandlerRegistered = true;
  }

  /**
   * Restores the ANSI-allowing EventHandler registered using
   * {@code #registerAnsiAllowingHandler(...)}.
   */
  public synchronized void switchToAnsiAllowingHandler() {
    if (ansiAllowingHandlerRegistered) {
      removeHandler(ansiStrippingHandler);
      addHandler(ansiAllowingHandler);
      ansiStrippingHandler = null;
      ansiAllowingHandler = null;
      ansiAllowingHandlerRegistered = false;
    }
  }

}
