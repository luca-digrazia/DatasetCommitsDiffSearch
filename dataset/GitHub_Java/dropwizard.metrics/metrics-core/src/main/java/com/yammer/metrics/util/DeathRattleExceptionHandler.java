package com.yammer.metrics.util;

import com.yammer.metrics.core.CounterMetric;

/**
 * When a thread throws an Exception that was not caught, a DeathRattleExceptionHandler will
 * increment a counter signalling a thread has died and print out the name and stack trace of the
 * thread.
 *
 * This makes it easy to build alerts on unexpected Thread deaths and fine grained used quickens
 * debugging in production.
 *
 * You can also set a DeathRattleExceptionHandler as the default exception handler on all threads,
 * allowing you to get information on Threads you do not have direct control over.
 *
 * Usage is straightforward:
 *
 * <pre>
 * CounterMetric c = Metrics.newCounter(MyRunnable.class, "thread-deaths")
 * Thread.UncaughtExceptionHandler exHandler = new DeathRattleExceptionHandler(c)
 * Thread myThread = new Thread(myRunnable, "MyRunnable")
 * myThread.setExceptionHandler(exHandler)
 * </pre>
 *
 * Setting the global default exception handler should be done first, like so:
 *
 * <pre>
 * CounterMetric c = Metrics.newCounter(MyMainClass.class, "unhandled-thread-deaths")
 * Thread.UncaughtExceptionHandler ohNoIDidntKnowAboutThis = new DeathRattleExceptionHandler(c)
 * Thread.setDefaultExceptionHandler(ohNoIDidntKnowAboutThis)
 * </pre>
 */
public class DeathRattleExceptionHandler implements Thread.UncaughtExceptionHandler {
  final private CounterMetric deathRattle;
  public DeathRattleExceptionHandler(CounterMetric deathRattle) {
    this.deathRattle = deathRattle;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    deathRattle.inc();
    System.err.println("Uncaught exception on thread " + t);
    e.printStackTrace();
  }
}
