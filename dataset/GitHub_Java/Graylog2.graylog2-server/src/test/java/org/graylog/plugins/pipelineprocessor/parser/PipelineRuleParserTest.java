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
package org.graylog.plugins.pipelineprocessor.parser;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.Pipeline;
import org.graylog.plugins.pipelineprocessor.ast.Rule;
import org.graylog.plugins.pipelineprocessor.ast.Stage;
import org.graylog.plugins.pipelineprocessor.ast.functions.Function;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionDescriptor;
import org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor;
import org.graylog.plugins.pipelineprocessor.ast.statements.Statement;
import org.graylog.plugins.pipelineprocessor.functions.HasField;
import org.graylog.plugins.pipelineprocessor.functions.LongCoercion;
import org.graylog.plugins.pipelineprocessor.functions.SetField;
import org.graylog.plugins.pipelineprocessor.functions.StringCoercion;
import org.graylog.plugins.pipelineprocessor.parser.errors.IncompatibleArgumentType;
import org.graylog.plugins.pipelineprocessor.parser.errors.OptionalParametersMustBeNamed;
import org.graylog.plugins.pipelineprocessor.parser.errors.UndeclaredFunction;
import org.graylog.plugins.pipelineprocessor.parser.errors.UndeclaredVariable;
import org.graylog2.plugin.Message;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.collect.ImmutableList.of;
import static org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor.param;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PipelineRuleParserTest {

    @org.junit.Rule
    public TestName name = new TestName();

    private PipelineRuleParser parser;
    private static FunctionRegistry functionRegistry;

    private static final AtomicBoolean actionsTriggered = new AtomicBoolean(false);

    @BeforeClass
    public static void registerFunctions() {
        final Map<String, Function<?>> functions = Maps.newHashMap();
        functions.put("nein", new Function<Boolean>() {
            @Override
            public Boolean evaluate(FunctionArgs args, EvaluationContext context) {
                return false;
            }

            @Override
            public FunctionDescriptor<Boolean> descriptor() {
                return FunctionDescriptor.<Boolean>builder()
                        .name("nein")
                        .returnType(Boolean.class)
                        .params(of())
                        .build();
            }
        });
        functions.put("doch", new Function<Boolean>() {
            @Override
            public Boolean evaluate(FunctionArgs args, EvaluationContext context) {
                return true;
            }

            @Override
            public FunctionDescriptor<Boolean> descriptor() {
                return FunctionDescriptor.<Boolean>builder()
                        .name("doch")
                        .returnType(Boolean.class)
                        .params(of())
                        .build();
            }
        });
        functions.put("double_valued_func", new Function<Double>() {
            @Override
            public Double evaluate(FunctionArgs args, EvaluationContext context) {
                return 0d;
            }

            @Override
            public FunctionDescriptor<Double> descriptor() {
                return FunctionDescriptor.<Double>builder()
                        .name("double_valued_func")
                        .returnType(Double.class)
                        .params(of())
                        .build();
            }
        });
        functions.put("one_arg", new Function<String>() {
            @Override
            public String evaluate(FunctionArgs args, EvaluationContext context) {
                return args.evaluated("one", context, String.class).orElse("");
            }

            @Override
            public FunctionDescriptor<String> descriptor() {
                return FunctionDescriptor.<String>builder()
                        .name("one_arg")
                        .returnType(String.class)
                        .params(of(ParameterDescriptor.string("one")))
                        .build();
            }
        });
        functions.put("concat", new Function<String>() {
            @Override
            public String evaluate(FunctionArgs args, EvaluationContext context) {
                final Object one = args.evaluated("one", context, Object.class).orElse("");
                final Object two = args.evaluated("two", context, Object.class).orElse("");
                final Object three = args.evaluated("three", context, Object.class).orElse("");
                return one.toString() + two.toString() + three.toString();
            }

            @Override
            public FunctionDescriptor<String> descriptor() {
                return FunctionDescriptor.<String>builder()
                        .name("concat")
                        .returnType(String.class)
                        .params(of(
                                ParameterDescriptor.string("one"),
                                ParameterDescriptor.object("two"),
                                ParameterDescriptor.object("three")
                        ))
                        .build();
            }
        });
        functions.put("trigger_test", new Function<Void>() {
            @Override
            public Void evaluate(FunctionArgs args, EvaluationContext context) {
                actionsTriggered.set(true);
                return null;
            }

            @Override
            public FunctionDescriptor<Void> descriptor() {
                return FunctionDescriptor.<Void>builder()
                        .name("trigger_test")
                        .returnType(Void.class)
                        .params(of())
                        .build();
            }
        });
        functions.put("optional", new Function<Boolean>() {
            @Override
            public Boolean evaluate(FunctionArgs args, EvaluationContext context) {
                return true;
            }

            @Override
            public FunctionDescriptor<Boolean> descriptor() {
                return FunctionDescriptor.<Boolean>builder()
                        .name("optional")
                        .returnType(Boolean.class)
                        .params(of(
                                ParameterDescriptor.bool("a"),
                                ParameterDescriptor.string("b"),
                                param().floating("c").optional().build(),
                                ParameterDescriptor.integer("d")
                        ))
                        .build();
            }
        });
        functions.put("customObject", new Function<CustomObject>() {
            @Override
            public CustomObject evaluate(FunctionArgs args, EvaluationContext context) {
                return new CustomObject(args.evaluated("default", context, String.class).orElse(""));
            }

            @Override
            public FunctionDescriptor<CustomObject> descriptor() {
                return FunctionDescriptor.<CustomObject>builder()
                        .name("customObject")
                        .returnType(CustomObject.class)
                        .params(of(ParameterDescriptor.string("default")))
                        .build();
            }
        });
        functions.put("keys", new Function<List>() {
            @Override
            public List evaluate(FunctionArgs args, EvaluationContext context) {
                final Optional<Map> map = args.evaluated("map", context, Map.class);
                return Lists.newArrayList(map.orElse(Collections.emptyMap()).keySet());
            }

            @Override
            public FunctionDescriptor<List> descriptor() {
                return FunctionDescriptor.<List>builder()
                        .name("keys")
                        .returnType(List.class)
                        .params(of(param().name("map").type(Map.class).build()))
                        .build();
            }
        });
        functions.put("sort", new Function<Collection>() {
            @Override
            public Collection evaluate(FunctionArgs args, EvaluationContext context) {
                final Collection collection = args.evaluated("collection",
                                                             context,
                                                             Collection.class).orElse(Collections.emptyList());
                return Ordering.natural().sortedCopy(collection);
            }

            @Override
            public FunctionDescriptor<Collection> descriptor() {
                return FunctionDescriptor.<Collection>builder()
                        .name("sort")
                        .returnType(Collection.class)
                        .params(of(param().name("collection").type(Collection.class).build()))
                        .build();
            }
        });
        functions.put(LongCoercion.NAME, new LongCoercion());
        functions.put(StringCoercion.NAME, new StringCoercion());
        functions.put(SetField.NAME, new SetField());
        functions.put(HasField.NAME, new HasField());
        functionRegistry = new FunctionRegistry(functions);
    }

    @Before
    public void setup() {
        parser = new PipelineRuleParser(functionRegistry);
        // initialize before every test!
        actionsTriggered.set(false);
    }

    @After
    public void tearDown() {
        parser = null;
    }

    @Test
    public void basicRule() throws Exception {
        final Rule rule = parser.parseRule(ruleForTest());
        Assert.assertNotNull("rule should be successfully parsed", rule);
    }

    @Test
    public void undeclaredIdentifier() throws Exception {
        try {
            parser.parseRule(ruleForTest());
            fail("should throw error: undeclared variable x");
        } catch (ParseException e) {
            assertEquals(2,
                         e.getErrors().size()); // undeclared var and incompatible type, but we only care about the undeclared one here
            assertTrue("Should find error UndeclaredVariable",
                       e.getErrors().stream().anyMatch(error -> error instanceof UndeclaredVariable));
        }
    }

    @Test
    public void declaredFunction() throws Exception {
        try {
            parser.parseRule(ruleForTest());
        } catch (ParseException e) {
            fail("Should not fail to resolve function 'false'");
        }
    }

    @Test
    public void undeclaredFunction() throws Exception {
        try {
            parser.parseRule(ruleForTest());
            fail("should throw error: undeclared function 'unknown'");
        } catch (ParseException e) {
            assertTrue("Should find error UndeclaredFunction",
                       e.getErrors().stream().anyMatch(input -> input instanceof UndeclaredFunction));
        }
    }

    @Test
    public void singleArgFunction() throws Exception {
        try {
            final Rule rule = parser.parseRule(ruleForTest());
            final Message message = evaluateRule(rule);

            assertNotNull(message);
            assertTrue("actions should have triggered", actionsTriggered.get());
        } catch (ParseException e) {
            fail("Should not fail to parse");
        }
    }

    @Test
    public void positionalArguments() throws Exception {
        try {
            final Rule rule = parser.parseRule(ruleForTest());
            evaluateRule(rule);

            assertTrue(actionsTriggered.get());
        } catch (ParseException e) {
            fail("Should not fail to parse");
        }
    }

    @Test
    public void inferVariableType() throws Exception {
        try {
            final Rule rule = parser.parseRule(ruleForTest());

            evaluateRule(rule);
        } catch (ParseException e) {
            fail("Should not fail to parse");
        }
    }

    @Test
    public void invalidArgType() throws Exception {
        try {
            parser.parseRule(ruleForTest());
        } catch (ParseException e) {
            assertEquals(2, e.getErrors().size());
            assertTrue("Should only find IncompatibleArgumentType errors",
                       e.getErrors().stream().allMatch(input -> input instanceof IncompatibleArgumentType));
        }
    }

    @Test
    public void booleanValuedFunctionAsCondition() throws Exception {
        try {
            final Rule rule = parser.parseRule(ruleForTest());

            evaluateRule(rule);
            assertTrue("actions should have triggered", actionsTriggered.get());
        } catch (ParseException e) {
            fail("Should not fail to parse");
        }
    }

    @Test
    public void messageRef() throws Exception {
        final Rule rule = parser.parseRule(ruleForTest());
        Message message = new Message("hello test", "source", DateTime.now());
        message.addField("responseCode", 500);
        final Message processedMsg = evaluateRule(rule, message);

        assertNotNull(processedMsg);
        assertEquals("server_error", processedMsg.getField("response_category"));
    }

    @Test
    public void messageRefQuotedField() throws Exception {
        final Rule rule = parser.parseRule(ruleForTest());
        Message message = new Message("hello test", "source", DateTime.now());
        message.addField("@specialfieldname", "string");
        evaluateRule(rule, message);

        assertTrue(actionsTriggered.get());
    }

    @Test
    public void optionalArguments() throws Exception {
        final Rule rule = parser.parseRule(ruleForTest());

        Message message = new Message("hello test", "source", DateTime.now());
        evaluateRule(rule, message);
        assertTrue(actionsTriggered.get());
    }

    @Test
    public void optionalParamsMustBeNamed() throws Exception {
        try {
            parser.parseRule(ruleForTest());
        } catch (ParseException e) {
            assertEquals(1, e.getErrors().stream().count());
            assertTrue(e.getErrors().stream().allMatch(error -> error instanceof OptionalParametersMustBeNamed));
        }

    }

    @Test
    public void mapArrayLiteral() {
        final Rule rule = parser.parseRule(ruleForTest());
        Message message = new Message("hello test", "source", DateTime.now());
        evaluateRule(rule, message);
        assertTrue(actionsTriggered.get());
    }

    @Test
    public void typedFieldAccess() throws Exception {
        try {
            final Rule rule = parser.parseRule(ruleForTest());
            evaluateRule(rule, new Message("hallo", "test", DateTime.now()));
            assertTrue("condition should be true", actionsTriggered.get());
        } catch (ParseException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void pipelineDeclaration() throws Exception {
        final List<Pipeline> pipelines = parser.parsePipelines(ruleForTest());
        assertEquals(1, pipelines.size());
        final Pipeline pipeline = Iterables.getOnlyElement(pipelines);
        assertEquals("cisco", pipeline.name());
        assertEquals(2, pipeline.stages().size());
        final Stage stage1 = pipeline.stages().first();
        final Stage stage2 = pipeline.stages().last();

        assertEquals(true, stage1.matchAll());
        assertEquals(1, stage1.stage());
        assertArrayEquals(new Object[]{"check_ip_whitelist", "cisco_device"}, stage1.ruleReferences().toArray());

        assertEquals(false, stage2.matchAll());
        assertEquals(2, stage2.stage());
        assertArrayEquals(new Object[]{"parse_cisco_time", "extract_src_dest", "normalize_src_dest", "lookup_ips", "resolve_ips"},
                          stage2.ruleReferences().toArray());
    }

    private Message evaluateRule(Rule rule, Message message) {
        final EvaluationContext context = new EvaluationContext(message);
        if (rule.when().evaluateBool(context)) {

            for (Statement statement : rule.then()) {
                statement.evaluate(context);
            }
            return message;
        } else {
            return null;
        }
    }

    @Nullable
    private Message evaluateRule(Rule rule) {
        final Message message = new Message("hello test", "source", DateTime.now());
        return evaluateRule(rule, message);
    }

    private String ruleForTest() {
        try {
            final URL resource = this.getClass().getResource(name.getMethodName().concat(".txt"));
            final Path path = Paths.get(resource.toURI());
            final byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, Charsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static class CustomObject {
        private final String id;

        public CustomObject(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}