// Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.buildeventservice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.authandtls.AuthAndTLSOptions;
import com.google.devtools.build.lib.buildeventservice.client.BuildEventServiceClient;
import com.google.devtools.build.lib.buildeventstream.BuildEventArtifactUploader;
import com.google.devtools.build.lib.buildeventstream.BuildEventProtocolOptions;
import com.google.devtools.build.lib.buildeventstream.BuildEventServiceAbruptExitCallback;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.Aborted.AbortReason;
import com.google.devtools.build.lib.buildeventstream.BuildEventTransport;
import com.google.devtools.build.lib.buildeventstream.LocalFilesArtifactUploader;
import com.google.devtools.build.lib.buildeventstream.transports.BinaryFormatFileTransport;
import com.google.devtools.build.lib.buildeventstream.transports.BuildEventStreamOptions;
import com.google.devtools.build.lib.buildeventstream.transports.JsonFormatFileTransport;
import com.google.devtools.build.lib.buildeventstream.transports.TextFormatFileTransport;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.runtime.BlazeModule;
import com.google.devtools.build.lib.runtime.BuildEventStreamer;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.runtime.CountingArtifactGroupNamer;
import com.google.devtools.build.lib.runtime.SynchronizedOutputStream;
import com.google.devtools.build.lib.util.AbruptExitException;
import com.google.devtools.build.lib.util.ExitCode;
import com.google.devtools.build.lib.util.LoggingUtil;
import com.google.devtools.build.lib.util.io.OutErr;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParsingException;
import com.google.devtools.common.options.OptionsParsingResult;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Module responsible for the Build Event Transport (BEP) and Build Event Service (BES)
 * functionality.
 */
public abstract class BuildEventServiceModule<BESOptionsT extends BuildEventServiceOptions>
    extends BlazeModule {

  private static final Logger logger = Logger.getLogger(BuildEventServiceModule.class.getName());

  private OutErr outErr;
  private BuildEventStreamer streamer;
  private BuildEventProtocolOptions bepOptions;
  private AuthAndTLSOptions authTlsOptions;
  private BuildEventStreamOptions besStreamOptions;
  private ImmutableSet<BuildEventTransport> bepTransports;
  private String invocationId;
  private EventHandler cmdLineReporter;

  protected BESOptionsT besOptions;

  /** Callback used by the transports to report errors and possible exit abruptly. */
  protected BuildEventServiceAbruptExitCallback getAbruptExitCallback(
      ModuleEnvironment moduleEnvironment) {
    return moduleEnvironment::exit;
  }

  /** Report errors in the command line and possibly fail the build. */
  protected void reportError(
      EventHandler commandLineReporter,
      ModuleEnvironment moduleEnvironment,
      String msg,
      Exception exception,
      ExitCode exitCode) {
    // Don't hide unchecked exceptions as part of the error reporting.
    Throwables.throwIfUnchecked(exception);

    logger.log(Level.SEVERE, msg, exception);
    AbruptExitException abruptException = new AbruptExitException(msg, exitCode, exception);
    commandLineReporter.handle(Event.error(exception.getMessage()));
    moduleEnvironment.exit(abruptException);
  }

  @Override
  public Iterable<Class<? extends OptionsBase>> getCommonCommandOptions() {
    return ImmutableList.of(
        optionsClass(),
        AuthAndTLSOptions.class,
        BuildEventStreamOptions.class,
        BuildEventProtocolOptions.class);
  }

  @Override
  public void beforeCommand(CommandEnvironment cmdEnv) {
    this.invocationId = cmdEnv.getCommandId().toString();
    this.cmdLineReporter = cmdEnv.getReporter();
    // Reset to null in case afterCommand was not called.
    // TODO(lpino): Remove this statement once {@link BlazeModule#afterCommmand()} is guaranteed
    // to be executed for every invocation.
    this.outErr = null;

    OptionsParsingResult parsingResult = cmdEnv.getOptions();
    this.besOptions = Preconditions.checkNotNull(parsingResult.getOptions(optionsClass()));
    this.bepOptions =
        Preconditions.checkNotNull(parsingResult.getOptions(BuildEventProtocolOptions.class));
    this.authTlsOptions =
        Preconditions.checkNotNull(parsingResult.getOptions(AuthAndTLSOptions.class));
    this.besStreamOptions =
        Preconditions.checkNotNull(parsingResult.getOptions(BuildEventStreamOptions.class));

    CountingArtifactGroupNamer artifactGroupNamer = new CountingArtifactGroupNamer();
    Supplier<BuildEventArtifactUploader> uploaderSupplier =
        Suppliers.memoize(
            () ->
                cmdEnv
                    .getRuntime()
                    .getBuildEventArtifactUploaderFactoryMap()
                    .select(bepOptions.buildEventUploadStrategy)
                    .create(cmdEnv));

    if (!whitelistedCommands(besOptions).contains(cmdEnv.getCommandName())) {
      // Exit early if the running command isn't supported.
      return;
    }

    bepTransports = createBepTransports(cmdEnv, uploaderSupplier, artifactGroupNamer);
    if (bepTransports.isEmpty()) {
      // Exit early if there are no transports to stream to.
      return;
    }

    streamer =
        new BuildEventStreamer.Builder()
            .buildEventTransports(bepTransports)
            .cmdLineReporter(cmdEnv.getReporter())
            .besStreamOptions(besStreamOptions)
            .artifactGroupNamer(artifactGroupNamer)
            .build();

    cmdEnv.getReporter().addHandler(streamer);
    cmdEnv.getEventBus().register(streamer);
    registerOutAndErrOutputStreams();
  }

  private void registerOutAndErrOutputStreams() {
    int bufferSize = besOptions.besOuterrBufferSize;
    int chunkSize = besOptions.besOuterrChunkSize;
    final SynchronizedOutputStream out = new SynchronizedOutputStream(bufferSize, chunkSize);
    final SynchronizedOutputStream err = new SynchronizedOutputStream(bufferSize, chunkSize);

    this.outErr = OutErr.create(out, err);
    streamer.registerOutErrProvider(
        new BuildEventStreamer.OutErrProvider() {
          @Override
          public Iterable<String> getOut() {
            return out.readAndReset();
          }

          @Override
          public Iterable<String> getErr() {
            return err.readAndReset();
          }
        });
    err.registerStreamer(streamer);
    out.registerStreamer(streamer);
  }

  @Override
  public OutErr getOutputListener() {
    return outErr;
  }

  @Override
  public void blazeShutdownOnCrash() {
    if (streamer != null) {
      logger.warning("Attempting to close BES streamer on crash");
      streamer.close(AbortReason.INTERNAL);
    }
  }

  @Override
  public void afterCommand() {
    if (streamer != null) {
      if (!Strings.isNullOrEmpty(besOptions.besBackend)) {
        constructAndMaybeReportInvocationIdUrl();
      }

      if (!streamer.isClosed()) {
        // This should not occur, but close with an internal error if a {@link BuildEventStreamer}
        // bug manifests as an unclosed streamer.
        logger.warning("Attempting to close BES streamer after command");
        String msg = "BES was not properly closed";
        LoggingUtil.logToRemote(Level.WARNING, msg, new IllegalStateException(msg));
        streamer.close(AbortReason.INTERNAL);
      }
      this.streamer = null;
    }

    if (!besStreamOptions.keepBackendConnections) {
      clearBesClient();
    }
    this.outErr = null;
    this.bepTransports = null;
    this.invocationId = null;
    this.cmdLineReporter = null;
  }

  private void constructAndMaybeReportInvocationIdUrl() {
    if (!getInvocationIdPrefix().isEmpty()) {
      cmdLineReporter.handle(
          Event.info("Streaming build results to: " + getInvocationIdPrefix() + invocationId));
    }
  }

  private void constructAndReportIds(String buildRequestId) {
    cmdLineReporter.handle(
        Event.info(
            String.format(
                "Streaming Build Event Protocol to '%s' with build_request_id: '%s'"
                    + " and invocation_id: '%s'",
                besOptions.besBackend, buildRequestId, invocationId)));
  }

  @Nullable
  private BuildEventServiceTransport createBesTransport(
      CommandEnvironment cmdEnv,
      Supplier<BuildEventArtifactUploader> uploaderSupplier,
      CountingArtifactGroupNamer artifactGroupNamer) {
    if (Strings.isNullOrEmpty(besOptions.besBackend)) {
      clearBesClient();
      return null;
    }

    constructAndReportIds(cmdEnv.getBuildRequestId());

    final BuildEventServiceClient besClient;
    try {
      besClient = getBesClient(besOptions, authTlsOptions);
    } catch (IOException | OptionsParsingException e) {
      reportError(
          cmdEnv.getReporter(),
          cmdEnv.getBlazeModuleEnvironment(),
          e.getMessage(),
          e,
          ExitCode.LOCAL_ENVIRONMENTAL_ERROR);
      return null;
    }

    BuildEventServiceProtoUtil besProtoUtil =
        new BuildEventServiceProtoUtil.Builder()
            .buildRequestId(cmdEnv.getBuildRequestId())
            .invocationId(cmdEnv.getCommandId().toString())
            .projectId(besOptions.projectId)
            .commandName(cmdEnv.getCommandName())
            .keywords(getBesKeywords(besOptions, cmdEnv.getRuntime().getStartupOptionsProvider()))
            .build();

    return new BuildEventServiceTransport.Builder()
        .localFileUploader(uploaderSupplier.get())
        .besClient(besClient)
        .besOptions(besOptions)
        .besProtoUtil(besProtoUtil)
        .artifactGroupNamer(artifactGroupNamer)
        .bepOptions(bepOptions)
        .clock(cmdEnv.getRuntime().getClock())
        .abruptExitCallback(getAbruptExitCallback(cmdEnv.getBlazeModuleEnvironment()))
        .eventBus(cmdEnv.getEventBus())
        .build();
  }

  private ImmutableSet<BuildEventTransport> createBepTransports(
      CommandEnvironment cmdEnv,
      Supplier<BuildEventArtifactUploader> uploaderSupplier,
      CountingArtifactGroupNamer artifactGroupNamer) {
    ImmutableSet.Builder<BuildEventTransport> bepTransportsBuilder = new ImmutableSet.Builder<>();

    if (!Strings.isNullOrEmpty(besStreamOptions.buildEventTextFile)) {
      try {
        BufferedOutputStream bepTextOutputStream =
            new BufferedOutputStream(
                Files.newOutputStream(Paths.get(besStreamOptions.buildEventTextFile)));

        BuildEventArtifactUploader localFileUploader =
            besStreamOptions.buildEventTextFilePathConversion
                ? uploaderSupplier.get()
                : new LocalFilesArtifactUploader();
        bepTransportsBuilder.add(
            new TextFormatFileTransport(
                bepTextOutputStream,
                bepOptions,
                localFileUploader,
                getAbruptExitCallback(cmdEnv.getBlazeModuleEnvironment()),
                artifactGroupNamer));
      } catch (IOException exception) {
        // TODO(b/125216340): Consider making this a warning instead of an error once the
        //  associated bug has been resolved.
        reportError(
            cmdEnv.getReporter(),
            cmdEnv.getBlazeModuleEnvironment(),
            "Unable to write to '"
                + besStreamOptions.buildEventTextFile
                + "'. Omitting --build_event_text_file.",
            exception,
            ExitCode.LOCAL_ENVIRONMENTAL_ERROR);
      }
    }

    if (!Strings.isNullOrEmpty(besStreamOptions.buildEventBinaryFile)) {
      try {
        BufferedOutputStream bepBinaryOutputStream =
            new BufferedOutputStream(
                Files.newOutputStream(Paths.get(besStreamOptions.buildEventBinaryFile)));

        BuildEventArtifactUploader localFileUploader =
            besStreamOptions.buildEventBinaryFilePathConversion
                ? uploaderSupplier.get()
                : new LocalFilesArtifactUploader();
        bepTransportsBuilder.add(
            new BinaryFormatFileTransport(
                bepBinaryOutputStream,
                bepOptions,
                localFileUploader,
                getAbruptExitCallback(cmdEnv.getBlazeModuleEnvironment()),
                artifactGroupNamer));
      } catch (IOException exception) {
        // TODO(b/125216340): Consider making this a warning instead of an error once the
        //  associated bug has been resolved.
        reportError(
            cmdEnv.getReporter(),
            cmdEnv.getBlazeModuleEnvironment(),
            "Unable to write to '"
                + besStreamOptions.buildEventBinaryFile
                + "'. Omitting --build_event_binary_file.",
            exception,
            ExitCode.LOCAL_ENVIRONMENTAL_ERROR);
      }
    }

    if (!Strings.isNullOrEmpty(besStreamOptions.buildEventJsonFile)) {
      try {
        BufferedOutputStream bepJsonOutputStream =
            new BufferedOutputStream(
                Files.newOutputStream(Paths.get(besStreamOptions.buildEventJsonFile)));
        BuildEventArtifactUploader localFileUploader =
            besStreamOptions.buildEventJsonFilePathConversion
                ? uploaderSupplier.get()
                : new LocalFilesArtifactUploader();
        bepTransportsBuilder.add(
            new JsonFormatFileTransport(
                bepJsonOutputStream,
                bepOptions,
                localFileUploader,
                getAbruptExitCallback(cmdEnv.getBlazeModuleEnvironment()),
                artifactGroupNamer));
      } catch (IOException exception) {
        // TODO(b/125216340): Consider making this a warning instead of an error once the
        //  associated bug has been resolved.
        reportError(
            cmdEnv.getReporter(),
            cmdEnv.getBlazeModuleEnvironment(),
            "Unable to write to '"
                + besStreamOptions.buildEventJsonFile
                + "'. Omitting --build_event_json_file.",
            exception,
            ExitCode.LOCAL_ENVIRONMENTAL_ERROR);
      }
    }

    BuildEventServiceTransport besTransport =
        createBesTransport(cmdEnv, uploaderSupplier, artifactGroupNamer);
    if (besTransport != null) {
      constructAndMaybeReportInvocationIdUrl();
      bepTransportsBuilder.add(besTransport);
    }

    return bepTransportsBuilder.build();
  }

  protected abstract Class<BESOptionsT> optionsClass();

  protected abstract BuildEventServiceClient getBesClient(
      BESOptionsT besOptions, AuthAndTLSOptions authAndTLSOptions)
      throws IOException, OptionsParsingException;

  protected abstract void clearBesClient();

  protected abstract Set<String> whitelistedCommands(BESOptionsT besOptions);

  protected Set<String> getBesKeywords(
      BESOptionsT besOptions, @Nullable OptionsParsingResult startupOptionsProvider) {
    return besOptions.besKeywords.stream()
        .map(keyword -> "user_keyword=" + keyword)
        .collect(ImmutableSet.toImmutableSet());
  }

  /** A prefix used when printing the invocation ID in the command line */
  protected abstract String getInvocationIdPrefix();

  // TODO(b/115961387): This method shouldn't exist. It only does because some tests are relying on
  //  the transport creation logic of this module directly.
  @VisibleForTesting
  ImmutableSet<BuildEventTransport> getBepTransports() {
    return bepTransports;
  }
}
