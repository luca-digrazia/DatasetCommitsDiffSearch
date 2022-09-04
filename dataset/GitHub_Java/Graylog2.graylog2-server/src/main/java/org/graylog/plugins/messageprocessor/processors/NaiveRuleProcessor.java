package org.graylog.plugins.messageprocessor.processors;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.graylog.plugins.messageprocessor.EvaluationContext;
import org.graylog.plugins.messageprocessor.ast.Rule;
import org.graylog.plugins.messageprocessor.ast.statements.Statement;
import org.graylog.plugins.messageprocessor.db.RuleSourceService;
import org.graylog.plugins.messageprocessor.parser.FunctionRegistry;
import org.graylog.plugins.messageprocessor.parser.ParseException;
import org.graylog.plugins.messageprocessor.parser.RuleParser;
import org.graylog.plugins.messageprocessor.rest.RuleSource;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.Messages;
import org.graylog2.plugin.messageprocessors.MessageProcessor;
import org.graylog2.shared.buffers.processors.ProcessBufferProcessor;
import org.graylog2.shared.journal.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static com.codahale.metrics.MetricRegistry.name;

public class NaiveRuleProcessor implements MessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(NaiveRuleProcessor.class);

    private final RuleSourceService ruleSourceService;
    private final RuleParser ruleParser;
    private final FunctionRegistry functionRegistry;
    private final Journal journal;
    private final Meter filteredOutMessages;

    @Inject
    public NaiveRuleProcessor(RuleSourceService ruleSourceService,
                              RuleParser ruleParser,
                              FunctionRegistry functionRegistry,
                              Journal journal,
                              MetricRegistry metricRegistry) {
        this.ruleSourceService = ruleSourceService;
        this.ruleParser = ruleParser;
        this.functionRegistry = functionRegistry;
        this.journal = journal;
        this.filteredOutMessages = metricRegistry.meter(name(ProcessBufferProcessor.class, "filteredOutMessages"));
    }

    @Override
    public Messages process(Messages messages) {
        for (RuleSource ruleSource : ruleSourceService.loadAll()) {
            final Rule rule;
            try {
                rule = ruleParser.parseRule(ruleSource.source());
            } catch (ParseException parseException) {
                log.error("Unable to parse rule: " + parseException.getMessage());
                continue;
            }
            log.info("Evaluation rule {}", rule.name());

            for (Message message : messages) {
                try {
                    final EvaluationContext context = new EvaluationContext(functionRegistry);
                    if (rule.when().evaluateBool(context, message)) {
                        log.info("[✓] Message {} matches condition", message.getId());

                        for (Statement statement : rule.then()) {
                            statement.evaluate(context, message);
                        }

                    } else {
                        log.info("[✕] Message {} does not match condition", message.getId());
                    }
                } catch (Exception e) {
                    log.error("Unable to process message", e);
                }
                if (message.getFilterOut()) {
                    log.info("[✝] Message {} was filtered out", message.getId());

                    filteredOutMessages.mark();
                    journal.markJournalOffsetCommitted(message.getJournalOffset());
                }
            }
        }
        return messages;
    }
}
