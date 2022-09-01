package com.facebook.stetho.inspector.network;

import java.io.IOException;

/**
 * Custom hook to intercept read events delivered by
 * {@link NetworkEventReporter#interpretResponseStream}.
 */
public interface ResponseHandler {
  /**
   * Signal that data has been read from the response stream.
   *
   * @param numBytes Bytes read from the network stack's stream as established by
   *     {@link NetworkEventReporter#interpretResponseStream}.
   */
  public void onRead(int numBytes);

  /**
   * Signal that data has been decoded (reversing the response's {@code Content-Encoding}) while
   * reading a raw stream.  This method is only called when the stream is known to have
   * a supported encoding.  Note that for HTTP, content encoding almost always is used for
   * some form of response compression.
   *
   * @param numBytes Bytes yielded after decoding bytes received from the network stack's
   *     stream.
   */
  public void onReadDecoded(int numBytes);

  /**
   * Signals that EOF has been reached reading the response stream from the network
   * stack.
   */
  public void onEOF();

  /**
   * Signals that an error occurred while reading the response stream.
   */
  public void onError(IOException e);
}
