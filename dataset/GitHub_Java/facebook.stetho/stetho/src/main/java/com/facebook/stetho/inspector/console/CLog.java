package com.facebook.stetho.inspector.console;

import com.facebook.stetho.common.LogRedirector;
import com.facebook.stetho.inspector.helper.ChromePeerManager;
import com.facebook.stetho.inspector.protocol.module.Console;

/**
 * Utility for reporting an event to the console
 */
public class CLog {
  private static final String TAG = "CLog";

  // @VisibleForTest
  public static void writeToConsole(
      ChromePeerManager chromePeerManager,
      Console.MessageLevel logLevel,
      Console.MessageSource messageSource,
      String messageText) {
    // Send to logcat to increase the chances that a developer will notice :)
    LogRedirector.d(TAG, messageText);

    Console.ConsoleMessage message = new Console.ConsoleMessage();
    message.source = messageSource;
    message.level = logLevel;
    message.text = messageText;
    Console.MessageAddedRequest messageAddedRequest = new Console.MessageAddedRequest();
    messageAddedRequest.message = message;
    chromePeerManager.sendNotificationToPeers("Console.messageAdded", messageAddedRequest);
  }
}
