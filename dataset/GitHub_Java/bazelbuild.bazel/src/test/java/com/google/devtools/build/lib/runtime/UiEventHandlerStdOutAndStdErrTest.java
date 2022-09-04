// Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.runtime;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.buildtool.BuildRequest;
import com.google.devtools.build.lib.buildtool.BuildResult;
import com.google.devtools.build.lib.buildtool.buildevent.BuildCompleteEvent;
import com.google.devtools.build.lib.buildtool.buildevent.BuildStartingEvent;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventKind;
import com.google.devtools.build.lib.testutil.ManualClock;
import com.google.devtools.build.lib.util.io.OutErr;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for output generated by {@link UiEventHandler}. */
@RunWith(Parameterized.class)
public class UiEventHandlerStdOutAndStdErrTest {

  private static final BuildCompleteEvent BUILD_COMPETE =
      new BuildCompleteEvent(new BuildResult(/*startTimeMillis=*/ 0));

  @Parameter public TestedOutput testedOutput;

  private UiEventHandler uiEventHandler;
  private FlushCollectingOutputStream output;
  private EventKind eventKind;

  @Parameters(name = "Output: {0}")
  public static TestedOutput[] testedOutputs() {
    return TestedOutput.values();
  }

  enum TestedOutput {
    STDOUT,
    STDERR;
  }

  @Before
  public void createUiEventHandler() {
    output = new FlushCollectingOutputStream();

    OutErr outErr = null;
    switch (testedOutput) {
      case STDOUT:
        outErr = OutErr.create(/*out=*/ output, /*err=*/ mock(OutputStream.class));
        eventKind = EventKind.STDOUT;
        break;
      case STDERR:
        outErr = OutErr.create(/*out=*/ mock(OutputStream.class), /*err=*/ output);
        eventKind = EventKind.STDERR;
        break;
    }

    UiOptions uiOptions = new UiOptions();
    uiOptions.eventFilters = ImmutableList.of();
    uiEventHandler =
        new UiEventHandler(outErr, uiOptions, new ManualClock(), /*workspacePathFragment=*/ null);
    uiEventHandler.buildStarted(new BuildStartingEvent(/*env=*/ null, mock(BuildRequest.class)));
  }

  @Test
  public void buildComplete_outputsNothing() {
    uiEventHandler.buildComplete(BUILD_COMPETE);
    output.assertFlushed();
  }

  @Test
  public void buildComplete_flushesBufferedMessage() {
    uiEventHandler.handle(output("hello"));
    uiEventHandler.buildComplete(BUILD_COMPETE);

    output.assertFlushed("hello");
  }

  @Test
  public void buildComplete_emptyBuffer_outputsNothing() {
    uiEventHandler.handle(output(""));
    uiEventHandler.buildComplete(BUILD_COMPETE);

    output.assertFlushed();
  }

  @Test
  public void handleOutputEvent_buffersWithoutNewline() {
    uiEventHandler.handle(output("hello"));
    output.assertFlushed();
  }

  @Test
  public void handleOutputEvent_concatenatesInBuffer() {
    uiEventHandler.handle(output("hello "));
    uiEventHandler.handle(output("there"));
    uiEventHandler.buildComplete(BUILD_COMPETE);

    output.assertFlushed("hello there");
  }

  @Test
  public void handleOutputEvent_flushesOnNewline() {
    uiEventHandler.handle(output("hello\n"));
    output.assertFlushed("hello\n");
  }

  @Test
  public void handleOutputEvent_flushesOnlyUntilNewline() {
    uiEventHandler.handle(output("hello\nworld"));
    output.assertFlushed("hello\n");
  }

  @Test
  public void handleOutputEvent_flushesUntilLastNewline() {
    uiEventHandler.handle(output("hello\nto\neveryone"));
    output.assertFlushed("hello\nto\n");
  }

  @Test
  public void handleOutputEvent_flushesMultiLineMessageAtOnce() {
    uiEventHandler.handle(output("hello\neveryone\n"));
    output.assertFlushed("hello\neveryone\n");
  }

  @Test
  public void handleOutputEvent_concatenatesBufferBeforeFlushingOnNewline() {
    uiEventHandler.handle(output("hello"));
    uiEventHandler.handle(output(" there!\nmore text"));

    output.assertFlushed("hello there!\n");
  }

  private Event output(String message) {
    return Event.of(eventKind, message);
  }

  private static class FlushCollectingOutputStream extends OutputStream {
    private final List<String> flushed = new ArrayList<>();
    private String writtenSinceFlush = "";

    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) b});
    }

    @Override
    public void write(byte[] bytes, int offset, int len) {
      writtenSinceFlush += new String(Arrays.copyOfRange(bytes, offset, offset + len), UTF_8);
    }

    @Override
    public void flush() {
      // Ignore inconsequential extra flushes.
      if (!writtenSinceFlush.isEmpty()) {
        flushed.add(writtenSinceFlush);
      }
      writtenSinceFlush = "";
    }

    private void assertFlushed(String... messages) {
      assertThat(writtenSinceFlush).isEmpty();
      assertThat(flushed).containsExactlyElementsIn(messages);
    }
  }
}
