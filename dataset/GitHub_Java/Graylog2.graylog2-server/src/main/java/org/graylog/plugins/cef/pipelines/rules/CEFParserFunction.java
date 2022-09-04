package org.graylog.plugins.cef.pipelines.rules;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import org.graylog.plugins.cef.parser.CEFMessage;
import org.graylog.plugins.cef.parser.CEFParser;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.functions.AbstractFunction;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionDescriptor;
import org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.codahale.metrics.MetricRegistry.name;

public class CEFParserFunction extends AbstractFunction<CEFParserResult> {
    private static final Logger LOG = LoggerFactory.getLogger(CEFParserFunction.class);

    public static final String NAME = "parse_cef";

    @VisibleForTesting
    static final String VALUE = "cef_string";
    static final String USE_FULL_NAMES = "use_full_names";

    private final ParameterDescriptor<String, String> valueParam = ParameterDescriptor.string(VALUE).description("The CEF string to parse").build();
    private final ParameterDescriptor<Boolean, Boolean> useFullNamesParam = ParameterDescriptor.bool(USE_FULL_NAMES).description("Use full field names for CEF extensions").build();

    private final Timer parseTime;

    @Inject
    public CEFParserFunction(final MetricRegistry metricRegistry) {
        this.parseTime = metricRegistry.timer(name(this.getClass(), "parseTime"));
    }

    @Override
    public CEFParserResult evaluate(FunctionArgs args, EvaluationContext context) {
        final String cef = valueParam.required(args, context);
        final boolean useFullNames = useFullNamesParam.optional(args, context).orElse(false);

        final CEFParser parser = new CEFParser(useFullNames);
        if (cef == null || cef.isEmpty()) {
            LOG.debug("NULL or empty parameter passed to CEF parser function. Not evaluating.");
            return null;
        }

        LOG.debug("Running CEF parser for [{}].", cef);

        final CEFMessage message;
        try (Timer.Context timer = parseTime.time()) {
            message = parser.parse(cef.trim()).build();
        } catch (Exception e) {
            LOG.error("Could not run CEF parser for [{}].", cef, e);
            return null;
        }

        final Map<String, Object> fields = new HashMap<>();

        /*
          * Add all CEF standard fields. We are prefixing with cef_ to avoid overwriting existing fields or to be
          * overwritten ourselves later in the processing. The user is encouraged to run another pipeline function
          * to clean up field names if desired.
         */
        fields.put("cef_version", message.version());
        fields.put("cef_device_vendor", message.deviceVendor());
        fields.put("cef_device_product", message.deviceProduct());
        fields.put("cef_device_version", message.deviceVersion());
        fields.put("cef_device_event_class_id", message.deviceEventClassId());
        fields.put("cef_name", message.name());
        fields.put("cef_severity", message.severity().text());
        fields.put("cef_severity_numeric", message.severity().numeric());
        if (message.message() != null) {
            fields.put("cef_message", message.message());
        }

        // Add all custom CEF fields.
        for (Map.Entry<String, Object> f : message.fields().entrySet()) {
            fields.put("cef_" + f.getKey(), f.getValue());
        }

        return new CEFParserResult(fields);
    }

    @Override
    public FunctionDescriptor<CEFParserResult> descriptor() {
        return FunctionDescriptor.<CEFParserResult>builder()
                .name(NAME)
                .description("Parse any CEF formatted string into it's fields. This is the CEF string (starting with \"CEF:\") without a syslog envelope.")
                .params(valueParam, useFullNamesParam)
                .returnType(CEFParserResult.class)
                .build();
    }
}
