package org.graylog.plugins.cef.pipelines.rules;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.CommonToken;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.expressions.BooleanExpression;
import org.graylog.plugins.pipelineprocessor.ast.expressions.Expression;
import org.graylog.plugins.pipelineprocessor.ast.expressions.StringExpression;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog2.plugin.Message;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class CEFParserFunctionTest {
    private CEFParserFunction function;

    @Before
    public void setUp() {
        function = new CEFParserFunction(new MetricRegistry());
    }

    @Test
    public void evaluate_returns_null_for_missing_CEF_string() throws Exception {
        final FunctionArgs functionArgs = new FunctionArgs(function, Collections.emptyMap());
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNull(result);
    }

    @Test
    public void evaluate_returns_null_for_empty_CEF_string() throws Exception {
        final Map<String, Expression> arguments = Collections.singletonMap(
                CEFParserFunction.VALUE, new StringExpression(new CommonToken(0), "")
        );
        final FunctionArgs functionArgs = new FunctionArgs(function, arguments);
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNull(result);
    }

    @Test
    public void evaluate_returns_null_for_invalid_CEF_string() throws Exception {
        final Map<String, Expression> arguments = ImmutableMap.of(
                CEFParserFunction.VALUE, new StringExpression(new CommonToken(0), "CEF:0|Foobar"),
                CEFParserFunction.USE_FULL_NAMES, new BooleanExpression(new CommonToken(0), false)
        );
        final FunctionArgs functionArgs = new FunctionArgs(function, arguments);
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNull(result);
    }

    @Test
    public void evaluate_returns_result_for_valid_CEF_string() throws Exception {
        final Map<String, Expression> arguments = ImmutableMap.of(
                CEFParserFunction.VALUE, new StringExpression(new CommonToken(0), "CEF:0|vendor|product|1.0|id|name|low|dvc=example.com msg=Foobar"),
                CEFParserFunction.USE_FULL_NAMES, new BooleanExpression(new CommonToken(0), false)
        );
        final FunctionArgs functionArgs = new FunctionArgs(function, arguments);
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNotNull(result);
        assertEquals(0, result.get("cef_version"));
        assertEquals("vendor", result.get("cef_device_vendor"));
        assertEquals("product", result.get("cef_device_product"));
        assertEquals("1.0", result.get("cef_device_version"));
        assertEquals("id", result.get("cef_device_event_class_id"));
        assertEquals("Low", result.get("cef_severity"));
        assertEquals(0, result.get("cef_severity_numeric"));
        assertEquals("example.com", result.get("cef_dvc"));
        assertEquals("Foobar", result.get("cef_message"));
    }

    @Test
    public void evaluate_returns_result_for_valid_CEF_string_with_short_names_if_parameter_is_missing() throws Exception {
        final Map<String, Expression> arguments = Collections.singletonMap(
                CEFParserFunction.VALUE, new StringExpression(new CommonToken(0), "CEF:0|vendor|product|1.0|id|name|low|dvc=example.com msg=Foobar")
        );
        final FunctionArgs functionArgs = new FunctionArgs(function, arguments);
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNotNull(result);
        assertEquals(0, result.get("cef_version"));
        assertEquals("vendor", result.get("cef_device_vendor"));
        assertEquals("product", result.get("cef_device_product"));
        assertEquals("1.0", result.get("cef_device_version"));
        assertEquals("id", result.get("cef_device_event_class_id"));
        assertEquals("Low", result.get("cef_severity"));
        assertEquals(0, result.get("cef_severity_numeric"));
        assertEquals("example.com", result.get("cef_dvc"));
        assertEquals("Foobar", result.get("cef_message"));
    }

    @Test
    public void evaluate_returns_result_for_valid_CEF_string_with_full_names() throws Exception {
        final CEFParserFunction function = new CEFParserFunction(new MetricRegistry());
        final Map<String, Expression> arguments = ImmutableMap.of(
                CEFParserFunction.VALUE, new StringExpression(new CommonToken(0), "CEF:0|vendor|product|1.0|id|name|low|dvc=example.com msg=Foobar"),
                CEFParserFunction.USE_FULL_NAMES, new BooleanExpression(new CommonToken(0), true)
        );
        final FunctionArgs functionArgs = new FunctionArgs(function, arguments);
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNotNull(result);
        assertEquals(0, result.get("cef_version"));
        assertEquals("vendor", result.get("cef_device_vendor"));
        assertEquals("product", result.get("cef_device_product"));
        assertEquals("1.0", result.get("cef_device_version"));
        assertEquals("id", result.get("cef_device_event_class_id"));
        assertEquals("Low", result.get("cef_severity"));
        assertEquals(0, result.get("cef_severity_numeric"));
        assertEquals("example.com", result.get("cef_deviceAddress"));
        assertEquals("Foobar", result.get("cef_message"));
    }

    @Test
    public void evaluate_returns_result_without_message_field() throws Exception {
        final Map<String, Expression> arguments = ImmutableMap.of(
                CEFParserFunction.VALUE, new StringExpression(new CommonToken(0), "CEF:0|vendor|product|1.0|id|name|low|dvc=example.com"),
                CEFParserFunction.USE_FULL_NAMES, new BooleanExpression(new CommonToken(0), false)
        );
        final FunctionArgs functionArgs = new FunctionArgs(function, arguments);
        final Message message = new Message("__dummy", "__dummy", DateTime.parse("2010-07-30T16:03:25Z"));
        final EvaluationContext evaluationContext = new EvaluationContext(message);

        final CEFParserResult result = function.evaluate(functionArgs, evaluationContext);
        assertNotNull(result);
        assertEquals(0, result.get("cef_version"));
        assertEquals("vendor", result.get("cef_device_vendor"));
        assertEquals("product", result.get("cef_device_product"));
        assertEquals("1.0", result.get("cef_device_version"));
        assertEquals("id", result.get("cef_device_event_class_id"));
        assertEquals("Low", result.get("cef_severity"));
        assertEquals(0, result.get("cef_severity_numeric"));
        assertEquals("example.com", result.get("cef_dvc"));
        assertFalse(result.containsKey("cef_message"));
    }
}