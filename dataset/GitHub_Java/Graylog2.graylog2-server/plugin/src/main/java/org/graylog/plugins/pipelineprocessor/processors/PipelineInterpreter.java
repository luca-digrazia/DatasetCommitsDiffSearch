/**
 * This file is part of Graylog Pipeline Processor.
 *
 * Graylog Pipeline Processor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog Pipeline Processor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog Pipeline Processor.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.pipelineprocessor.processors;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.Pipeline;
import org.graylog.plugins.pipelineprocessor.ast.Rule;
import org.graylog.plugins.pipelineprocessor.ast.Stage;
import org.graylog.plugins.pipelineprocessor.ast.statements.Statement;
import org.graylog.plugins.pipelineprocessor.processors.listeners.InterpreterListener;
import org.graylog.plugins.pipelineprocessor.processors.listeners.NoopInterpreterListener;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.MessageCollection;
import org.graylog2.plugin.Messages;
import org.graylog2.plugin.messageprocessors.MessageProcessor;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.shared.buffers.processors.ProcessBufferProcessor;
import org.graylog2.shared.journal.Journal;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.codahale.metrics.MetricRegistry.name;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class PipelineInterpreter implements MessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(PipelineInterpreter.class);

    public static final String GL2_PROCESSING_ERROR = "gl2_processing_error";

    private final Journal journal;
    private final Meter filteredOutMessages;
    private EventBus serverEventBus;

    /**
     * The current pipeline/stage/rule configuration of the system, including the stream-pipeline connections
     */
    private final AtomicReference<State> state = new AtomicReference<>(null);

    @Inject
    public PipelineInterpreter(Journal journal,
                               MetricRegistry metricRegistry,
                               EventBus serverEventBus,
                               ConfigurationStateUpdater stateUpdater) {

        this.journal = journal;
        this.filteredOutMessages = metricRegistry.meter(name(ProcessBufferProcessor.class, "filteredOutMessages"));
        this.serverEventBus = serverEventBus;

        /*
         * get around the initialization race between state updater and the interpreter instances:
         * the updater loads the config and posts an event on the bus, but the interpreters haven't registered yet.
         * once the updater is constructed, it has loaded a state so we can get it once it has been injected
         */
        state.set(stateUpdater.getLatestState());

        // listens to state changes
        serverEventBus.register(this);
    }

    @Subscribe
    public void handleStateUpdate(State newState) {
        log.debug("Updated pipeline state to {}", newState);
        state.set(newState);
    }

    /*
     * Allow to unregister PipelineInterpreter from the event bus, allowing the object to be garbage collected.
     * This is needed in some classes, when new PipelineInterpreter instances are created per request.
     */
    public void stop() {
        serverEventBus.unregister(this);
    }

    /**
     * @param messages messages to process
     * @return messages to pass on to the next stage
     */
    @Override
    public Messages process(Messages messages) {
        return process(messages, new NoopInterpreterListener(), this.state.get());
    }

    /**
     * Evaluates all pipelines that apply to the given messages, based on the current stream routing of the messages.
     *
     * The processing loops on each single message (passed in or created by pipelines) until the set of streams does
     * not change anymore.
     * No cycle detection is performed.
     *
     * @param messages the messages to process through the pipelines
     * @param interpreterListener a listener which gets called for each processing stage (e.g. to trace execution)
     * @param state the pipeline/stage/rule/stream connection state to use during processing
     * @return the processed messages
     */
    public Messages process(Messages messages, InterpreterListener interpreterListener, State state) {
        interpreterListener.startProcessing();
        // message id + stream id
        final Set<Tuple2<String, String>> processingBlacklist = Sets.newHashSet();

        final List<Message> fullyProcessed = Lists.newArrayList();
        List<Message> toProcess = Lists.newArrayList(messages);

        while (!toProcess.isEmpty()) {
            final MessageCollection currentSet = new MessageCollection(toProcess);
            // we'll add them back below
            toProcess.clear();

            for (Message message : currentSet) {
                final String msgId = message.getId();

                // 1. for each message, determine which pipelines are supposed to be executed, based on their streams
                //    null is the default stream, the other streams are identified by their id
                final ImmutableSet<Pipeline> pipelinesToRun;

                // this makes a copy of the list!
                final Set<String> initialStreamIds = message.getStreams().stream().map(Stream::getId).collect(Collectors.toSet());

                final ImmutableSetMultimap<String, Pipeline> streamConnection = state.getStreamPipelineConnections();

                if (initialStreamIds.isEmpty()) {
                    if (processingBlacklist.contains(tuple(msgId, "default"))) {
                        // already processed default pipeline for this message
                        pipelinesToRun = ImmutableSet.of();
                        log.debug("[{}] already processed default stream, skipping", msgId);
                    } else {
                        // get the default stream pipeline connections for this message
                        pipelinesToRun = streamConnection.get("default");
                        interpreterListener.processDefaultStream(message, pipelinesToRun);
                        if (log.isDebugEnabled()) {
                            log.debug("[{}] running default stream pipelines: [{}]",
                                      msgId,
                                      pipelinesToRun.stream().map(Pipeline::name).toArray());
                        }
                    }
                } else {
                    // 2. if a message-stream combination has already been processed (is in the set), skip that execution
                    final Set<String> streamsIds = initialStreamIds.stream()
                            .filter(streamId -> !processingBlacklist.contains(tuple(msgId, streamId)))
                            .filter(streamConnection::containsKey)
                            .collect(Collectors.toSet());
                    pipelinesToRun = ImmutableSet.copyOf(streamsIds.stream()
                            .flatMap(streamId -> streamConnection.get(streamId).stream())
                            .collect(Collectors.toSet()));
                    interpreterListener.processStreams(message, pipelinesToRun, streamsIds);
                    log.debug("[{}] running pipelines {} for streams {}", msgId, pipelinesToRun, streamsIds);
                }

                toProcess.addAll(processForResolvedPipelines(message, msgId, pipelinesToRun, interpreterListener));

                boolean addedStreams = false;
                // 5. add each message-stream combination to the blacklist set
                for (Stream stream : message.getStreams()) {
                    if (!initialStreamIds.remove(stream.getId())) {
                        addedStreams = true;
                    } else {
                        // only add pre-existing streams to blacklist, this has the effect of only adding already processed streams,
                        // not newly added ones.
                        processingBlacklist.add(tuple(msgId, stream.getId()));
                    }
                }
                if (message.getFilterOut()) {
                    log.debug("[{}] marked message to be discarded. Dropping message.",
                              msgId);
                    filteredOutMessages.mark();
                    journal.markJournalOffsetCommitted(message.getJournalOffset());
                }
                // 6. go to 1 and iterate over all messages again until no more streams are being assigned
                if (!addedStreams || message.getFilterOut()) {
                    log.debug("[{}] no new streams matches or dropped message, not running again", msgId);
                    fullyProcessed.add(message);
                } else {
                    // process again, we've added a stream
                    log.debug("[{}] new streams assigned, running again for those streams", msgId);
                    toProcess.add(message);
                }
            }
        }

        interpreterListener.finishProcessing();
        // 7. return the processed messages
        return new MessageCollection(fullyProcessed);
    }

    public List<Message> processForPipelines(Message message,
                                             String msgId,
                                             Set<String> pipelines,
                                             InterpreterListener interpreterListener,
                                             State state) {
        final ImmutableSet<Pipeline> pipelinesToRun = ImmutableSet.copyOf(pipelines
                .stream()
                .map(pipelineId -> state.getCurrentPipelines().get(pipelineId))
                .filter(pipeline -> pipeline != null)
                .collect(Collectors.toSet()));

        return processForResolvedPipelines(message, msgId, pipelinesToRun, interpreterListener);
    }

    private List<Message> processForResolvedPipelines(Message message,
                                                      String msgId,
                                                      Set<Pipeline> pipelines,
                                                      InterpreterListener interpreterListener) {
        final List<Message> result = new ArrayList<>();
        // record execution of pipeline in metrics
        pipelines.forEach(Pipeline::markExecution);

        final StageIterator stages = new StageIterator(pipelines);
        final Set<Pipeline> pipelinesToSkip = Sets.newHashSet();

        // iterate through all stages for all matching pipelines, per "stage slice" instead of per pipeline.
        // pipeline execution ordering is not guaranteed
        while (stages.hasNext()) {
            final List<Stage> stageSet = stages.next();
            for (final Stage stage : stageSet) {
                final Pipeline pipeline = stage.getPipeline();
                if (pipelinesToSkip.contains(pipeline)) {
                    log.debug("[{}] previous stage result prevents further processing of pipeline `{}`",
                             msgId,
                             pipeline.name());
                    continue;
                }
                stage.markExecution();
                interpreterListener.enterStage(stage);
                log.debug("[{}] evaluating rule conditions in stage {}: match {}",
                         msgId,
                         stage.stage(),
                         stage.matchAll() ? "all" : "either");

                // TODO the message should be decorated to allow layering changes and isolate stages
                final EvaluationContext context = new EvaluationContext(message);

                // 3. iterate over all the stages in these pipelines and execute them in order
                final ArrayList<Rule> rulesToRun = Lists.newArrayListWithCapacity(stage.getRules().size());
                boolean anyRulesMatched = false;
                for (Rule rule : stage.getRules()) {
                    interpreterListener.evaluateRule(rule, pipeline);
                    if (rule.when().evaluateBool(context)) {
                        anyRulesMatched = true;
                        rule.markMatch();

                        if (context.hasEvaluationErrors()) {
                            final EvaluationContext.EvalError lastError = Iterables.getLast(context.evaluationErrors());
                            appendProcessingError(rule, message, lastError.toString());
                            interpreterListener.failEvaluateRule(rule, pipeline);
                            log.debug("Encountered evaluation error during condition, skipping rule actions: {}",
                                      lastError);
                            continue;
                        }
                        interpreterListener.satisfyRule(rule, pipeline);
                        log.debug("[{}] rule `{}` matches, scheduling to run", msgId, rule.name());
                        rulesToRun.add(rule);
                    } else {
                        rule.markNonMatch();
                        interpreterListener.dissatisfyRule(rule, pipeline);
                        log.debug("[{}] rule `{}` does not match", msgId, rule.name());
                    }
                }
                RULES:
                for (Rule rule : rulesToRun) {
                    rule.markExecution();
                    interpreterListener.executeRule(rule, pipeline);
                    log.debug("[{}] rule `{}` matched running actions", msgId, rule.name());
                    for (Statement statement : rule.then()) {
                        statement.evaluate(context);
                        if (context.hasEvaluationErrors()) {
                            // if the last statement resulted in an error, do not continue to execute this rules
                            final EvaluationContext.EvalError lastError = Iterables.getLast(context.evaluationErrors());
                            appendProcessingError(rule, message, lastError.toString());
                            interpreterListener.failExecuteRule(rule, pipeline);
                            log.debug("Encountered evaluation error, skipping rest of the rule: {}",
                                      lastError);
                            rule.markFailure();
                            break RULES;
                        }
                    }
                }
                // stage needed to match all rule conditions to enable the next stage,
                // record that it is ok to proceed with this pipeline
                // OR
                // any rule could match, but at least one had to,
                // record that it is ok to proceed with the pipeline
                if ((stage.matchAll() && (rulesToRun.size() == stage.getRules().size()))
                        || (rulesToRun.size() > 0 && anyRulesMatched)) {
                    interpreterListener.continuePipelineExecution(pipeline, stage);
                    log.debug("[{}] stage {} for pipeline `{}` required match: {}, ok to proceed with next stage",
                             msgId, stage.stage(), pipeline.name(), stage.matchAll() ? "all" : "either");
                } else {
                    // no longer execute stages from this pipeline, the guard prevents it
                    interpreterListener.stopPipelineExecution(pipeline, stage);
                    log.debug("[{}] stage {} for pipeline `{}` required match: {}, NOT ok to proceed with next stage",
                              msgId, stage.stage(), pipeline.name(), stage.matchAll() ? "all" : "either");
                    pipelinesToSkip.add(pipeline);
                }

                // 4. after each complete stage run, merge the processing changes, stages are isolated from each other
                // TODO message changes become visible immediately for now

                // 4a. also add all new messages from the context to the toProcess work list
                Iterables.addAll(result, context.createdMessages());
                context.clearCreatedMessages();
                interpreterListener.exitStage(stage);
            }
        }

        // 7. return the processed messages
        return result;
    }

    private void appendProcessingError(Rule rule, Message message, String errorString) {
        final String msg = "For rule '" + rule.name() + "': " + errorString;
        if (message.hasField(GL2_PROCESSING_ERROR)) {
            message.addField(GL2_PROCESSING_ERROR, message.getFieldAs(String.class, GL2_PROCESSING_ERROR) + "," + msg);
        } else {
            message.addField(GL2_PROCESSING_ERROR, msg);
        }
    }

    public static class Descriptor implements MessageProcessor.Descriptor {
        @Override
        public String name() {
            return "Pipeline Processor";
        }

        @Override
        public String className() {
            return PipelineInterpreter.class.getCanonicalName();
        }
    }

    public static class State {
        private final ImmutableMap<String, Pipeline> currentPipelines;
        private final ImmutableSetMultimap<String, Pipeline> streamPipelineConnections;

        public State(ImmutableMap<String, Pipeline> currentPipelines,
                     ImmutableSetMultimap<String, Pipeline> streamPipelineConnections) {
            this.currentPipelines = currentPipelines;
            this.streamPipelineConnections = streamPipelineConnections;
        }

        public ImmutableMap<String, Pipeline> getCurrentPipelines() {
            return currentPipelines;
        }

        public ImmutableSetMultimap<String, Pipeline> getStreamPipelineConnections() {
            return streamPipelineConnections;
        }
    }
}
